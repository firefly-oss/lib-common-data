/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.data.service;

import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for synchronous DataJobService implementations that provides
 * built-in observability, resiliency, and persistence features.
 *
 * <p>This class is designed for jobs that execute synchronously and return results
 * immediately, as opposed to {@link AbstractResilientDataJobService} which handles
 * asynchronous jobs with multiple stages (START, CHECK, COLLECT, RESULT, STOP).</p>
 *
 * <p><b>Automatic Features:</b></p>
 * <ul>
 *   <li>Distributed tracing via Micrometer</li>
 *   <li>Metrics collection (execution time, success/failure counts, error types)</li>
 *   <li>Circuit breaker, retry, rate limiting, and bulkhead patterns</li>
 *   <li>Audit trail persistence</li>
 *   <li>Execution result persistence</li>
 *   <li>Event publishing (job started, completed, failed)</li>
 *   <li>Comprehensive logging with execution context</li>
 *   <li>Automatic error handling and recovery</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Service
 * public class CustomerValidationService extends AbstractResilientSyncDataJobService {
 *     
 *     private final CustomerRepository customerRepository;
 *     
 *     public CustomerValidationService(
 *             JobTracingService tracingService,
 *             JobMetricsService metricsService,
 *             ResiliencyDecoratorService resiliencyService,
 *             JobEventPublisher eventPublisher,
 *             JobAuditService auditService,
 *             JobExecutionResultService resultService,
 *             CustomerRepository customerRepository) {
 *         super(tracingService, metricsService, resiliencyService, 
 *               eventPublisher, auditService, resultService);
 *         this.customerRepository = customerRepository;
 *     }
 *     
 *     @Override
 *     protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
 *         String customerId = (String) request.getParameters().get("customerId");
 *         
 *         return customerRepository.findById(customerId)
 *             .flatMap(customer -> validateCustomer(customer))
 *             .map(validationResult -> JobStageResponse.builder()
 *                 .success(validationResult.isValid())
 *                 .executionId(request.getExecutionId())
 *                 .data(Map.of("validationResult", validationResult))
 *                 .message(validationResult.getMessage())
 *                 .build());
 *     }
 *     
 *     @Override
 *     protected String getJobName() {
 *         return "CustomerValidation";
 *     }
 *     
 *     @Override
 *     protected String getJobDescription() {
 *         return "Validates customer data against business rules";
 *     }
 * }
 * }</pre>
 *
 * <p><b>Subclass Requirements:</b></p>
 * <ul>
 *   <li>Implement {@link #doExecute(JobStageRequest)} with your business logic</li>
 *   <li>Override {@link #getJobName()} to provide a meaningful job name</li>
 *   <li>Override {@link #getJobDescription()} to describe what the job does</li>
 *   <li>Optionally override {@link #getOrchestratorType()} if using an orchestrator</li>
 * </ul>
 *
 * @see SyncDataJobService
 * @see AbstractResilientDataJobService
 */
@Slf4j
public abstract class AbstractResilientSyncDataJobService implements SyncDataJobService {

    private final JobTracingService tracingService;
    private final JobMetricsService metricsService;
    private final ResiliencyDecoratorService resiliencyService;
    private final JobEventPublisher eventPublisher;
    private final JobAuditService auditService;
    private final JobExecutionResultService resultService;

    /**
     * Full constructor with all dependencies.
     *
     * @param tracingService service for distributed tracing
     * @param metricsService service for metrics collection
     * @param resiliencyService service for resiliency patterns
     * @param eventPublisher publisher for job events
     * @param auditService service for audit trail persistence
     * @param resultService service for execution result persistence
     */
    protected AbstractResilientSyncDataJobService(JobTracingService tracingService,
                                                  JobMetricsService metricsService,
                                                  ResiliencyDecoratorService resiliencyService,
                                                  JobEventPublisher eventPublisher,
                                                  JobAuditService auditService,
                                                  JobExecutionResultService resultService) {
        this.tracingService = tracingService;
        this.metricsService = metricsService;
        this.resiliencyService = resiliencyService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.resultService = resultService;
    }

    /**
     * Constructor without persistence services for backward compatibility.
     */
    protected AbstractResilientSyncDataJobService(JobTracingService tracingService,
                                                  JobMetricsService metricsService,
                                                  ResiliencyDecoratorService resiliencyService,
                                                  JobEventPublisher eventPublisher) {
        this(tracingService, metricsService, resiliencyService, eventPublisher, null, null);
    }

    /**
     * Constructor without JobEventPublisher for backward compatibility.
     */
    protected AbstractResilientSyncDataJobService(JobTracingService tracingService,
                                                  JobMetricsService metricsService,
                                                  ResiliencyDecoratorService resiliencyService) {
        this(tracingService, metricsService, resiliencyService, null, null, null);
    }

    @Override
    public final Mono<JobStageResponse> execute(JobStageRequest request) {
        // Generate execution ID if not provided
        String executionId = request.getExecutionId();
        if (executionId == null || executionId.isEmpty()) {
            executionId = generateExecutionId();
            request.setExecutionId(executionId);
        }

        // Publish job started event before execution
        if (eventPublisher != null) {
            eventPublisher.publishJobStarted(request);
        }

        return executeWithObservabilityAndResiliency(request);
    }

    /**
     * Executes the synchronous job with full observability, resiliency, and persistence.
     */
    private Mono<JobStageResponse> executeWithObservabilityAndResiliency(JobStageRequest request) {
        Instant startTime = Instant.now();
        String executionId = request.getExecutionId();
        String orchestratorType = getOrchestratorType();
        String jobName = getJobName();

        // Log execution start with request details
        log.info("Starting synchronous job '{}' - executionId: {}, parameters: {}",
                jobName, executionId, request.getParameters() != null ? request.getParameters().keySet() : "none");
        log.debug("Full request details for job '{}' - executionId: {}, request: {}",
                jobName, executionId, request);

        // Record audit entry for operation started
        if (auditService != null) {
            auditService.recordOperationStarted(request, orchestratorType)
                    .subscribe(); // Fire and forget
        }

        // Wrap with tracing - use a synthetic stage for sync jobs
        Mono<JobStageResponse> tracedOperation = tracingService.traceJobOperation(
                JobStage.ALL, // Sync jobs don't have stages, use ALL as a marker
                executionId,
                doExecute(request)
        );

        // Wrap with resiliency patterns
        Mono<JobStageResponse> resilientOperation = resiliencyService.decorate(tracedOperation);

        // Add metrics, logging, and persistence
        return resilientOperation
                .doOnSubscribe(subscription -> {
                    log.debug("Subscribed to synchronous job '{}' execution {}", jobName, executionId);
                })
                .doOnSuccess(response -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metricsService.recordJobStageExecution(JobStage.ALL, "success", duration);
                    metricsService.incrementJobStageCounter(JobStage.ALL, "success");

                    // Record audit entry for operation completed
                    if (auditService != null) {
                        auditService.recordOperationCompleted(request, response, duration.toMillis(), orchestratorType)
                                .subscribe(); // Fire and forget
                    }

                    // Persist execution result
                    if (resultService != null && response.isSuccess()) {
                        resultService.saveSuccessResult(
                                request,
                                response,
                                startTime,
                                null, // No separate collect data for sync jobs
                                response.getData(), // Result data
                                orchestratorType,
                                getJobDefinition()
                        ).subscribe(); // Fire and forget
                    }

                    // Publish job completed event
                    if (eventPublisher != null) {
                        eventPublisher.publishJobStageCompleted(JobStage.ALL, response);
                    }

                    if (response.isSuccess()) {
                        log.info("Successfully completed synchronous job '{}' - executionId: {}, duration: {}ms",
                                jobName, executionId, duration.toMillis());
                        log.debug("Response details for job '{}' - executionId: {}, data keys: {}, message: {}",
                                jobName, executionId,
                                response.getData() != null ? response.getData().keySet() : "none",
                                response.getMessage());
                    } else {
                        log.warn("Completed synchronous job '{}' with failure - executionId: {}, duration: {}ms, message: {}",
                                jobName, executionId, duration.toMillis(), response.getMessage());
                    }
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metricsService.recordJobStageExecution(JobStage.ALL, "failure", duration);
                    metricsService.incrementJobStageCounter(JobStage.ALL, "failure");
                    metricsService.recordJobError(JobStage.ALL, error.getClass().getSimpleName());

                    // Record audit entry for operation failed
                    if (auditService != null) {
                        auditService.recordOperationFailed(request, error, duration.toMillis(), orchestratorType)
                                .subscribe(); // Fire and forget
                    }

                    // Persist failure result
                    if (resultService != null) {
                        resultService.saveFailureResult(
                                request,
                                startTime,
                                error.getMessage(),
                                error.getClass().getSimpleName(),
                                orchestratorType,
                                getJobDefinition()
                        ).subscribe(); // Fire and forget
                    }

                    // Publish job failed event
                    if (eventPublisher != null) {
                        eventPublisher.publishJobFailed(executionId, request.getJobType(), error.getMessage(), error);
                    }

                    log.error("Failed synchronous job '{}' - executionId: {}, duration: {}ms, errorType: {}, errorMessage: {}",
                            jobName, executionId, duration.toMillis(), error.getClass().getSimpleName(), error.getMessage());
                    log.debug("Full error details for job '{}' - executionId: {}", jobName, executionId, error);
                })
                .onErrorResume(error -> {
                    log.warn("Returning failure response for synchronous job '{}' - executionId: {}, error: {}",
                            jobName, executionId, error.getMessage());
                    // Return a failure response instead of propagating the error
                    return Mono.just(JobStageResponse.builder()
                            .executionId(executionId)
                            .success(false)
                            .error("Error executing synchronous job: " + error.getMessage())
                            .message("Job execution failed")
                            .timestamp(Instant.now())
                            .build());
                });
    }

    /**
     * Implements the actual business logic for the synchronous job.
     * Subclasses must implement this method.
     *
     * <p>This method should:</p>
     * <ul>
     *   <li>Validate input parameters</li>
     *   <li>Execute the data processing logic</li>
     *   <li>Return results in the response</li>
     *   <li>Complete within a reasonable time (typically < 30 seconds)</li>
     * </ul>
     *
     * @param request the job execution request containing input parameters
     * @return a Mono emitting the response with execution results
     */
    protected abstract Mono<JobStageResponse> doExecute(JobStageRequest request);

    /**
     * Gets the tracing service for custom tracing operations.
     */
    protected JobTracingService getTracingService() {
        return tracingService;
    }

    /**
     * Gets the metrics service for custom metrics.
     */
    protected JobMetricsService getMetricsService() {
        return metricsService;
    }

    /**
     * Gets the resiliency service for custom resiliency patterns.
     */
    protected ResiliencyDecoratorService getResiliencyService() {
        return resiliencyService;
    }

    /**
     * Gets the audit service for custom audit operations.
     */
    protected JobAuditService getAuditService() {
        return auditService;
    }

    /**
     * Gets the result service for custom result operations.
     */
    protected JobExecutionResultService getResultService() {
        return resultService;
    }

    /**
     * Gets the orchestrator type. Subclasses should override this method.
     * Default implementation returns "SYNC" to indicate synchronous execution.
     */
    protected String getOrchestratorType() {
        return "SYNC";
    }

    /**
     * Gets the job definition identifier. Subclasses should override this method.
     * Default implementation returns null.
     */
    protected String getJobDefinition() {
        return null;
    }

    /**
     * Generates a unique execution ID for the job.
     * Subclasses can override this to customize the ID format.
     *
     * @return a unique execution ID
     */
    protected String generateExecutionId() {
        return "sync-" + UUID.randomUUID().toString();
    }
}

