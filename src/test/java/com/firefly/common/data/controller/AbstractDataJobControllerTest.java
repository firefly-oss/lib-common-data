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

package com.firefly.common.data.controller;

import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.service.DataJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for AbstractDataJobController.
 */
@ExtendWith(MockitoExtension.class)
class AbstractDataJobControllerTest {

    @Mock
    private DataJobService dataJobService;

    private TestDataJobController controller;

    @BeforeEach
    void setUp() {
        controller = new TestDataJobController(dataJobService);
    }

    @Test
    void shouldStartJobSuccessfully() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.START)
                .parameters(Map.of("key", "value"))
                .build();

        JobStageResponse expectedResponse = JobStageResponse.success(
                JobStage.START,
                "exec-123",
                "Job started successfully"
        );

        when(dataJobService.startJob(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(controller.startJob(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo("exec-123");
                    assertThat(response.getStage()).isEqualTo(JobStage.START);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckJobSuccessfully() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";

        JobStageResponse expectedResponse = JobStageResponse.success(
                JobStage.CHECK,
                executionId,
                "Job is running"
        );

        when(dataJobService.checkJob(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(controller.checkJob(executionId, requestId))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo(executionId);
                    assertThat(response.getStage()).isEqualTo(JobStage.CHECK);
                })
                .verifyComplete();
    }

    @Test
    void shouldCollectJobResultsSuccessfully() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";

        JobStageResponse expectedResponse = JobStageResponse.builder()
                .stage(JobStage.COLLECT)
                .executionId(executionId)
                .success(true)
                .message("Results collected")
                .data(Map.of("results", Map.of("count", 100)))
                .build();

        when(dataJobService.collectJobResults(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(controller.collectJobResults(executionId, requestId))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo(executionId);
                    assertThat(response.getStage()).isEqualTo(JobStage.COLLECT);
                    assertThat(response.getData()).containsKey("results");
                })
                .verifyComplete();
    }

    @Test
    void shouldGetJobResultSuccessfully() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";

        JobStageResponse expectedResponse = JobStageResponse.builder()
                .stage(JobStage.RESULT)
                .executionId(executionId)
                .success(true)
                .message("Final results retrieved")
                .data(Map.of("finalResult", Map.of("total", 1000)))
                .build();

        when(dataJobService.getJobResult(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(controller.getJobResult(executionId, requestId, null))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo(executionId);
                    assertThat(response.getStage()).isEqualTo(JobStage.RESULT);
                    assertThat(response.getData()).containsKey("finalResult");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleStartJobFailure() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.START)
                .build();

        JobStageResponse expectedResponse = JobStageResponse.failure(
                JobStage.START,
                "exec-123",
                "Failed to start job"
        );

        when(dataJobService.startJob(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));

        // When & Then
        StepVerifier.create(controller.startJob(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getError()).isNotNull();
                    assertThat(response.getError()).contains("Failed to start job");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleServiceError() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.START)
                .build();

        when(dataJobService.startJob(any(JobStageRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        // When & Then
        StepVerifier.create(controller.startJob(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    /**
     * Test implementation of AbstractDataJobController.
     */
    static class TestDataJobController extends AbstractDataJobController {
        TestDataJobController(DataJobService dataJobService) {
            super(dataJobService);
        }
    }
}

