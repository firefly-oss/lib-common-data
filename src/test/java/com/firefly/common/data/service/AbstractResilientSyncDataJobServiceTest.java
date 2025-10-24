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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbstractResilientSyncDataJobService.
 */
@ExtendWith(MockitoExtension.class)
class AbstractResilientSyncDataJobServiceTest {

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

    private TestSyncDataJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new TestSyncDataJobService(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                auditService,
                resultService
        );

        // Setup default mock behaviors with lenient() to avoid unnecessary stubbing errors
        lenient().when(tracingService.traceJobOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(auditService.recordOperationStarted(any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationCompleted(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationFailed(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveSuccessResult(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveFailureResult(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void execute_shouldReturnSuccessResponse_whenJobSucceeds() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .executionId("test-exec-123")
                .jobType("test-job")
                .parameters(Map.of("key", "value"))
                .requestId("req-001")
                .initiator("test-user")
                .build();

        // When
        Mono<JobStageResponse> result = jobService.execute(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo("test-exec-123");
                    assertThat(response.getMessage()).isEqualTo("Test job completed successfully");
                    assertThat(response.getData()).containsEntry("result", "test-data");
                })
                .verifyComplete();

        // Verify observability and persistence
        verify(tracingService).traceJobOperation(eq(JobStage.ALL), eq("test-exec-123"), any());
        verify(resiliencyService).decorate(any());
        verify(metricsService).recordJobStageExecution(eq(JobStage.ALL), eq("success"), any(Duration.class));
        verify(metricsService).incrementJobStageCounter(JobStage.ALL, "success");
        verify(eventPublisher).publishJobStarted(request);
        verify(eventPublisher).publishJobStageCompleted(eq(JobStage.ALL), any());
        verify(auditService).recordOperationStarted(request, "SYNC");
        verify(auditService).recordOperationCompleted(eq(request), any(), anyLong(), eq("SYNC"));
        verify(resultService).saveSuccessResult(eq(request), any(), any(), isNull(), any(), eq("SYNC"), isNull());
    }

    @Test
    void execute_shouldGenerateExecutionId_whenNotProvided() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .jobType("test-job")
                .parameters(Map.of("key", "value"))
                .build();

        // When
        Mono<JobStageResponse> result = jobService.execute(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isNotNull();
                    assertThat(response.getExecutionId()).startsWith("sync-");
                })
                .verifyComplete();
    }

    @Test
    void execute_shouldReturnFailureResponse_whenJobFails() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .executionId("test-exec-456")
                .jobType("failing-job")
                .parameters(Map.of("shouldFail", true))
                .build();

        TestSyncDataJobService failingService = new TestSyncDataJobService(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                auditService,
                resultService
        ) {
            @Override
            protected Mono<JobStageResponse> doExecute(JobStageRequest req) {
                return Mono.error(new RuntimeException("Test failure"));
            }
        };

        // When
        Mono<JobStageResponse> result = failingService.execute(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getExecutionId()).isEqualTo("test-exec-456");
                    assertThat(response.getError()).contains("Error executing synchronous job");
                })
                .verifyComplete();

        // Verify error handling
        verify(metricsService).recordJobStageExecution(eq(JobStage.ALL), eq("failure"), any(Duration.class));
        verify(metricsService).incrementJobStageCounter(JobStage.ALL, "failure");
        verify(metricsService).recordJobError(JobStage.ALL, "RuntimeException");
        verify(eventPublisher).publishJobFailed(eq("test-exec-456"), eq("failing-job"), anyString(), any());
        verify(auditService).recordOperationFailed(eq(request), any(), anyLong(), eq("SYNC"));
        verify(resultService).saveFailureResult(eq(request), any(), anyString(), eq("RuntimeException"), eq("SYNC"), isNull());
    }

    @Test
    void execute_shouldHandleNullEventPublisher() {
        // Given
        TestSyncDataJobService serviceWithoutPublisher = new TestSyncDataJobService(
                tracingService,
                metricsService,
                resiliencyService,
                null, // No event publisher
                auditService,
                resultService
        );

        JobStageRequest request = JobStageRequest.builder()
                .executionId("test-exec-789")
                .build();

        // When
        Mono<JobStageResponse> result = serviceWithoutPublisher.execute(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                })
                .verifyComplete();

        // Verify no event publisher calls
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void execute_shouldHandleNullPersistenceServices() {
        // Given
        TestSyncDataJobService serviceWithoutPersistence = new TestSyncDataJobService(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                null, // No audit service
                null  // No result service
        );

        JobStageRequest request = JobStageRequest.builder()
                .executionId("test-exec-999")
                .build();

        // When
        Mono<JobStageResponse> result = serviceWithoutPersistence.execute(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                })
                .verifyComplete();

        // Verify no persistence calls
        verifyNoInteractions(auditService);
        verifyNoInteractions(resultService);
    }

    @Test
    void getJobName_shouldReturnCustomName() {
        // When
        String jobName = jobService.getJobName();

        // Then
        assertThat(jobName).isEqualTo("TestSyncJob");
    }

    @Test
    void getJobDescription_shouldReturnCustomDescription() {
        // When
        String description = jobService.getJobDescription();

        // Then
        assertThat(description).isEqualTo("Test synchronous job for unit tests");
    }

    @Test
    void getOrchestratorType_shouldReturnSync() {
        // When
        String orchestratorType = jobService.getOrchestratorType();

        // Then
        assertThat(orchestratorType).isEqualTo("SYNC");
    }

    /**
     * Test implementation of AbstractResilientSyncDataJobService.
     */
    static class TestSyncDataJobService extends AbstractResilientSyncDataJobService {

        public TestSyncDataJobService(JobTracingService tracingService,
                                     JobMetricsService metricsService,
                                     ResiliencyDecoratorService resiliencyService,
                                     JobEventPublisher eventPublisher,
                                     JobAuditService auditService,
                                     JobExecutionResultService resultService) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, auditService, resultService);
        }

        @Override
        protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
            return Mono.just(JobStageResponse.builder()
                    .success(true)
                    .executionId(request.getExecutionId())
                    .message("Test job completed successfully")
                    .data(Map.of("result", "test-data"))
                    .build());
        }

        @Override
        public String getJobName() {
            return "TestSyncJob";
        }

        @Override
        public String getJobDescription() {
            return "Test synchronous job for unit tests";
        }
    }
}

