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

package com.firefly.common.data.model;

import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobStageResponse.
 */
class JobStageResponseTest {

    @Test
    void shouldBuildJobStageResponseWithAllFields() {
        // Given
        Instant now = Instant.now();
        
        // When
        JobStageResponse response = JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .status(JobExecutionStatus.RUNNING)
                .success(true)
                .message("Job started successfully")
                .progressPercentage(0)
                .data(Map.of("jobId", "job-123"))
                .error(null)
                .timestamp(now)
                .metadata(Map.of("source", "api"))
                .build();

        // Then
        assertThat(response.getStage()).isEqualTo(JobStage.START);
        assertThat(response.getExecutionId()).isEqualTo("exec-123");
        assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Job started successfully");
        assertThat(response.getProgressPercentage()).isEqualTo(0);
        assertThat(response.getData()).containsEntry("jobId", "job-123");
        assertThat(response.getError()).isNull();
        assertThat(response.getTimestamp()).isEqualTo(now);
        assertThat(response.getMetadata()).containsEntry("source", "api");
    }

    @Test
    void shouldCreateSuccessResponse() {
        // When
        JobStageResponse response = JobStageResponse.success(
                JobStage.START,
                "exec-123",
                "Job started successfully"
        );

        // Then
        assertThat(response.getStage()).isEqualTo(JobStage.START);
        assertThat(response.getExecutionId()).isEqualTo("exec-123");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Job started successfully");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    void shouldCreateFailureResponse() {
        // When
        JobStageResponse response = JobStageResponse.failure(
                JobStage.CHECK,
                "exec-456",
                "Job execution failed"
        );

        // Then
        assertThat(response.getStage()).isEqualTo(JobStage.CHECK);
        assertThat(response.getExecutionId()).isEqualTo("exec-456");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getError()).isEqualTo("Job execution failed");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // Given/When
        JobStageResponse response = new JobStageResponse();
        response.setStage(JobStage.COLLECT);
        response.setExecutionId("exec-789");
        response.setSuccess(true);

        // Then
        assertThat(response.getStage()).isEqualTo(JobStage.COLLECT);
        assertThat(response.getExecutionId()).isEqualTo("exec-789");
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // Given
        Instant now = Instant.now();
        
        // When
        JobStageResponse response = new JobStageResponse(
                JobStage.RESULT,
                "exec-999",
                JobExecutionStatus.SUCCEEDED,
                true,
                "Results retrieved",
                100,
                Map.of("result", "data"),
                null,
                now,
                Map.of("env", "prod")
        );

        // Then
        assertThat(response.getStage()).isEqualTo(JobStage.RESULT);
        assertThat(response.getExecutionId()).isEqualTo("exec-999");
        assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getProgressPercentage()).isEqualTo(100);
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        Instant now = Instant.now();
        
        JobStageResponse response1 = JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .success(true)
                .timestamp(now)
                .build();

        JobStageResponse response2 = JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .success(true)
                .timestamp(now)
                .build();

        JobStageResponse response3 = JobStageResponse.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-456")
                .success(false)
                .timestamp(now)
                .build();

        // Then
        assertThat(response1).isEqualTo(response2);
        assertThat(response1).hasSameHashCodeAs(response2);
        assertThat(response1).isNotEqualTo(response3);
    }

    @Test
    void shouldSupportToString() {
        // Given
        JobStageResponse response = JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .success(true)
                .message("Test message")
                .build();

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("START");
        assertThat(toString).contains("exec-123");
        assertThat(toString).contains("true");
        assertThat(toString).contains("Test message");
    }
}

