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

/**
 * Abstract base class for DataJobService implementations that provides
 * built-in observability, resiliency, and persistence features.
 *
 * This class automatically wraps all job operations with:
 * - Distributed tracing via Micrometer
 * - Metrics collection
 * - Circuit breaker, retry, rate limiting, and bulkhead patterns
 * - Audit trail persistence
 * - Execution result persistence
 *
 * Subclasses should implement the protected abstract methods to provide
 * the actual business logic.
 */
@Slf4j
public abstract class AbstractResilientDataJobService implements DataJobService {

    private final JobTracingService tracingService;
    private final JobMetricsService metricsService;
    private final ResiliencyDecoratorService resiliencyService;
    private final JobEventPublisher eventPublisher;
    private final JobAuditService auditService;
    private final JobExecutionResultService resultService;

    protected AbstractResilientDataJobService(JobTracingService tracingService,
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
    protected AbstractResilientDataJobService(JobTracingService tracingService,
                                              JobMetricsService metricsService,
                                              ResiliencyDecoratorService resiliencyService,
                                              JobEventPublisher eventPublisher) {
        this(tracingService, metricsService, resiliencyService, eventPublisher, null, null);
    }

    /**
     * Constructor without JobEventPublisher for backward compatibility.
     */
    protected AbstractResilientDataJobService(JobTracingService tracingService,
                                              JobMetricsService metricsService,
                                              ResiliencyDecoratorService resiliencyService) {
        this(tracingService, metricsService, resiliencyService, null, null, null);
    }

    @Override
    public final Mono<JobStageResponse> startJob(JobStageRequest request) {
        // Publish job started event before execution
        if (eventPublisher != null) {
            eventPublisher.publishJobStarted(request);
        }
        
        return executeWithObservabilityAndResiliency(
                JobStage.START,
                request,
                () -> doStartJob(request)
        );
    }

    @Override
    public final Mono<JobStageResponse> checkJob(JobStageRequest request) {
        return executeWithObservabilityAndResiliency(
                JobStage.CHECK,
                request,
                () -> doCheckJob(request)
        );
    }

    @Override
    public final Mono<JobStageResponse> collectJobResults(JobStageRequest request) {
        return executeWithObservabilityAndResiliency(
                JobStage.COLLECT,
                request,
                () -> doCollectJobResults(request)
        );
    }

    @Override
    public final Mono<JobStageResponse> getJobResult(JobStageRequest request) {
        return executeWithObservabilityAndResiliency(
                JobStage.RESULT,
                request,
                () -> doGetJobResult(request)
        );
    }

    /**
     * Executes an operation with full observability, resiliency, and persistence.
     */
    private Mono<JobStageResponse> executeWithObservabilityAndResiliency(
            JobStage stage,
            JobStageRequest request,
            java.util.function.Supplier<Mono<JobStageResponse>> operation) {

        Instant startTime = Instant.now();
        String executionId = request.getExecutionId();
        String orchestratorType = getOrchestratorType();

        // Log stage start with request details
        log.info("Starting {} stage - executionId: {}, parameters: {}",
                stage, executionId, request.getParameters() != null ? request.getParameters().keySet() : "none");
        log.debug("Full request details for {} stage - executionId: {}, request: {}",
                stage, executionId, request);

        // Record audit entry for operation started
        if (auditService != null) {
            auditService.recordOperationStarted(request, orchestratorType)
                    .subscribe(); // Fire and forget
        }

        // Wrap with tracing
        Mono<JobStageResponse> tracedOperation = tracingService.traceJobOperation(
                stage,
                executionId,
                operation.get()
        );

        // Wrap with resiliency patterns
        Mono<JobStageResponse> resilientOperation = resiliencyService.decorate(tracedOperation);

        // Add metrics, logging, and persistence
        return resilientOperation
                .doOnSubscribe(subscription -> {
                    log.debug("Subscribed to {} stage operation for execution {}", stage, executionId);
                })
                .doOnSuccess(response -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metricsService.recordJobStageExecution(stage, "success", duration);
                    metricsService.incrementJobStageCounter(stage, "success");

                    // Record audit entry for operation completed
                    if (auditService != null) {
                        auditService.recordOperationCompleted(request, response, duration.toMillis(), orchestratorType)
                                .subscribe(); // Fire and forget
                    }

                    // Persist execution result for COLLECT and RESULT stages
                    if (resultService != null && (stage == JobStage.COLLECT || stage == JobStage.RESULT)) {
                        if (response.isSuccess()) {
                            resultService.saveSuccessResult(
                                    request,
                                    response,
                                    startTime,
                                    stage == JobStage.COLLECT ? response.getData() : null,
                                    stage == JobStage.RESULT ? response.getData() : null,
                                    orchestratorType,
                                    getJobDefinition()
                            ).subscribe(); // Fire and forget
                        }
                    }

                    // Publish stage completed event
                    if (eventPublisher != null) {
                        eventPublisher.publishJobStageCompleted(stage, response);
                    }

                    if (response.isSuccess()) {
                        log.info("Successfully completed {} stage - executionId: {}, duration: {}ms, status: {}",
                                stage, executionId, duration.toMillis(), response.getStatus());
                        log.debug("Response details for {} stage - executionId: {}, data keys: {}, message: {}",
                                stage, executionId,
                                response.getData() != null ? response.getData().keySet() : "none",
                                response.getMessage());
                    } else {
                        log.warn("Completed {} stage with failure - executionId: {}, duration: {}ms, status: {}, message: {}",
                                stage, executionId, duration.toMillis(), response.getStatus(), response.getMessage());
                    }
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    metricsService.recordJobStageExecution(stage, "failure", duration);
                    metricsService.incrementJobStageCounter(stage, "failure");
                    metricsService.recordJobError(stage, error.getClass().getSimpleName());

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

                    log.error("Failed {} stage - executionId: {}, duration: {}ms, errorType: {}, errorMessage: {}",
                            stage, executionId, duration.toMillis(), error.getClass().getSimpleName(), error.getMessage());
                    log.debug("Full error details for {} stage - executionId: {}", stage, executionId, error);
                })
                .onErrorResume(error -> {
                    log.warn("Returning failure response for {} stage - executionId: {}, error: {}",
                            stage, executionId, error.getMessage());
                    // Return a failure response instead of propagating the error
                    return Mono.just(JobStageResponse.failure(
                            stage,
                            executionId,
                            "Error executing " + stage + " stage: " + error.getMessage()
                    ));
                });
    }

    /**
     * Implements the actual business logic for starting a job.
     * Subclasses must implement this method.
     */
    protected abstract Mono<JobStageResponse> doStartJob(JobStageRequest request);

    /**
     * Implements the actual business logic for checking a job.
     * Subclasses must implement this method.
     */
    protected abstract Mono<JobStageResponse> doCheckJob(JobStageRequest request);

    /**
     * Implements the actual business logic for collecting job results.
     * Subclasses must implement this method.
     */
    protected abstract Mono<JobStageResponse> doCollectJobResults(JobStageRequest request);

    /**
     * Implements the actual business logic for getting final job results.
     * Subclasses must implement this method.
     */
    protected abstract Mono<JobStageResponse> doGetJobResult(JobStageRequest request);

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
     * Default implementation returns "UNKNOWN".
     */
    protected String getOrchestratorType() {
        return "UNKNOWN";
    }

    /**
     * Gets the job definition identifier. Subclasses should override this method.
     * Default implementation returns null.
     */
    protected String getJobDefinition() {
        return null;
    }
}

