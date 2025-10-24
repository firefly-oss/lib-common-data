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

package com.firefly.common.data.integration;

import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * End-to-end test for async job orchestration.
 * 
 * This test validates the complete async job lifecycle:
 * 1. START - Initiates a long-running job
 * 2. CHECK - Polls job status until completion
 * 3. COLLECT - Retrieves job results
 * 4. RESULT - Maps results to target DTO
 * 5. STOP - Cancels a running job
 * 
 * This validates the core value proposition of the library:
 * Abstract away the complexity of different orchestrators (Step Functions, Airflow, etc.)
 * and provide a unified interface for async job management.
 */
@ExtendWith(MockitoExtension.class)
class AsyncJobOrchestratorE2ETest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private JobEventPublisher eventPublisher;

    @Mock
    private JobAuditService auditService;

    @Mock
    private JobExecutionResultService resultService;

    private TestAsyncJobService jobService;

    @BeforeEach
    void setUp() {
        // Setup mock behaviors
        lenient().when(tracingService.traceJobOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditService.recordOperationStarted(any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationCompleted(any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationFailed(any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveSuccessResult(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveFailureResult(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        // Create test job service
        jobService = new TestAsyncJobService(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                auditService,
                resultService
        );
    }

    @Test
    void shouldCompleteFullAsyncJobLifecycle_withPolling() {
        // Given - Configure job to simulate async execution
        // Job will be RUNNING for first 2 checks, then SUCCEEDED
        AtomicInteger checkCount = new AtomicInteger(0);
        jobService.setCheckBehavior(() -> {
            int count = checkCount.incrementAndGet();
            if (count <= 2) {
                return JobExecutionStatus.RUNNING;
            }
            return JobExecutionStatus.SUCCEEDED;
        });

        // STEP 1: START - Initiate async job
        JobStageRequest startRequest = JobStageRequest.builder()
                .stage(JobStage.START)
                .jobType("data-processing")
                .parameters(Map.of(
                        "dataSource", "customer-database",
                        "batchSize", 1000
                ))
                .requestId("req-async-001")
                .initiator("test-user")
                .build();

        StepVerifier.create(jobService.startJob(startRequest))
                .assertNext(response -> {
                    // Then - Job started successfully
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStage()).isEqualTo(JobStage.START);
                    assertThat(response.getExecutionId()).isNotNull();
                    assertThat(response.getExecutionId()).startsWith("exec-");
                    assertThat(response.getMessage()).contains("started");
                })
                .verifyComplete();

        String executionId = "exec-async-001";

        // STEP 2: CHECK - Poll job status (simulating multiple checks)
        JobStageRequest checkRequest = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId(executionId)
                .build();

        // First check - should be RUNNING
        StepVerifier.create(jobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStage()).isEqualTo(JobStage.CHECK);
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
                    assertThat(response.getProgressPercentage()).isGreaterThan(0);
                    assertThat(response.getMessage()).contains("running");
                })
                .verifyComplete();

        // Second check - still RUNNING
        StepVerifier.create(jobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
                    assertThat(response.getProgressPercentage()).isGreaterThan(0);
                })
                .verifyComplete();

        // Third check - now SUCCEEDED
        StepVerifier.create(jobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
                    assertThat(response.getProgressPercentage()).isEqualTo(100);
                    assertThat(response.getMessage()).contains("completed");
                })
                .verifyComplete();

        // STEP 3: COLLECT - Retrieve job results
        JobStageRequest collectRequest = JobStageRequest.builder()
                .stage(JobStage.COLLECT)
                .executionId(executionId)
                .build();

        StepVerifier.create(jobService.collectJobResults(collectRequest))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStage()).isEqualTo(JobStage.COLLECT);
                    assertThat(response.getData()).isNotNull();
                    
                    // Verify collected data
                    assertThat(response.getData()).containsKey("recordsProcessed");
                    assertThat(response.getData()).containsKey("processingTime");
                    assertThat(response.getData().get("recordsProcessed")).isEqualTo(1000);
                })
                .verifyComplete();

        // STEP 4: RESULT - Get final mapped result
        JobStageRequest resultRequest = JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId(executionId)
                .targetDtoClass(ProcessingResultDTO.class.getName())
                .build();

        StepVerifier.create(jobService.getJobResult(resultRequest))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStage()).isEqualTo(JobStage.RESULT);
                    assertThat(response.getData()).containsKey("result");
                    
                    // Verify mapped result
                    Object result = response.getData().get("result");
                    assertThat(result).isInstanceOf(ProcessingResultDTO.class);
                    
                    ProcessingResultDTO dto = (ProcessingResultDTO) result;
                    assertThat(dto.getRecordsProcessed()).isEqualTo(1000);
                    assertThat(dto.getStatus()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleJobFailure_duringExecution() {
        // Given - Configure job to fail
        jobService.setCheckBehavior(() -> JobExecutionStatus.FAILED);

        // When - Start job
        JobStageRequest startRequest = JobStageRequest.builder()
                .stage(JobStage.START)
                .jobType("failing-job")
                .parameters(Map.of("willFail", true))
                .build();

        StepVerifier.create(jobService.startJob(startRequest))
                .assertNext(response -> assertThat(response.isSuccess()).isTrue())
                .verifyComplete();

        // Then - Check should show FAILED status
        JobStageRequest checkRequest = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-fail-001")
                .build();

        StepVerifier.create(jobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.FAILED);
                    assertThat(response.getError()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldCancelRunningJob_whenStopRequested() {
        // Given - Job is running
        jobService.setCheckBehavior(() -> JobExecutionStatus.RUNNING);

        // When - Stop job
        JobStageRequest stopRequest = JobStageRequest.builder()
                .stage(JobStage.STOP)
                .executionId("exec-cancel-001")
                .build();

        StepVerifier.create(jobService.stopJob(stopRequest, "Test cancellation"))
                .assertNext(response -> {
                    // Then - Job stopped successfully
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStage()).isEqualTo(JobStage.STOP);
                    assertThat(response.getMessage()).contains("stopped");
                })
                .verifyComplete();

        // Verify job is now aborted
        JobStageRequest checkRequest = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-cancel-001")
                .build();

        StepVerifier.create(jobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.ABORTED);
                })
                .verifyComplete();
    }

    /**
     * Test DTO for processing results.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class ProcessingResultDTO {
        private Integer recordsProcessed;
        private String status;
        private Duration processingTime;
    }

    /**
     * Test async job service implementation.
     */
    static class TestAsyncJobService extends AbstractResilientDataJobService {

        @Setter
        private java.util.function.Supplier<JobExecutionStatus> checkBehavior = () -> JobExecutionStatus.SUCCEEDED;

        private boolean jobStopped = false;

        public TestAsyncJobService(JobTracingService tracingService,
                                  JobMetricsService metricsService,
                                  ResiliencyDecoratorService resiliencyService,
                                  JobEventPublisher eventPublisher,
                                  JobAuditService auditService,
                                  JobExecutionResultService resultService) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, auditService, resultService);
        }

        @Override
        protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
            return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.START)
                    .executionId("exec-async-001")
                    .success(true)
                    .status(JobExecutionStatus.RUNNING)
                    .message("Job started successfully")
                    .build());
        }

        @Override
        protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
            JobExecutionStatus status = jobStopped ? JobExecutionStatus.ABORTED : checkBehavior.get();
            boolean isSuccess = status == JobExecutionStatus.SUCCEEDED || status == JobExecutionStatus.RUNNING;
            int progress = status == JobExecutionStatus.SUCCEEDED ? 100 : 50;
            String message = status == JobExecutionStatus.SUCCEEDED ? "Job completed successfully" :
                           status == JobExecutionStatus.RUNNING ? "Job is running" : "Job failed";

            return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.CHECK)
                    .executionId(request.getExecutionId())
                    .success(isSuccess)
                    .status(status)
                    .progressPercentage(progress)
                    .message(message)
                    .error(status == JobExecutionStatus.FAILED ? "Job execution failed" : null)
                    .build());
        }

        @Override
        protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
            Map<String, Object> data = Map.of(
                    "recordsProcessed", 1000,
                    "processingTime", "PT5M",
                    "status", "SUCCESS"
            );

            return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.COLLECT)
                    .executionId(request.getExecutionId())
                    .success(true)
                    .status(JobExecutionStatus.SUCCEEDED)
                    .data(data)
                    .message("Results collected successfully")
                    .build());
        }

        @Override
        protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
            // Map raw data to target DTO
            ProcessingResultDTO result = ProcessingResultDTO.builder()
                    .recordsProcessed(1000)
                    .status("SUCCESS")
                    .processingTime(Duration.parse("PT5M"))
                    .build();

            return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.RESULT)
                    .executionId(request.getExecutionId())
                    .success(true)
                    .status(JobExecutionStatus.SUCCEEDED)
                    .data(Map.of("result", result))
                    .message("Result retrieved successfully")
                    .build());
        }

        @Override
        protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
            jobStopped = true;
            return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.STOP)
                    .executionId(request.getExecutionId())
                    .success(true)
                    .message("Job stopped successfully")
                    .build());
        }

        @Override
        public String getOrchestratorType() {
            return "TEST_ASYNC";
        }

        @Override
        public String getJobDefinition() {
            return "test-async-job";
        }
    }
}

