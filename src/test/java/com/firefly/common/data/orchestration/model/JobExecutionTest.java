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

package com.firefly.common.data.orchestration.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobExecution.
 */
class JobExecutionTest {

    @Test
    void shouldBuildJobExecutionWithAllFields() {
        // Given
        Instant startTime = Instant.now().minusSeconds(60);
        Instant stopTime = Instant.now();
        
        // When
        JobExecution execution = JobExecution.builder()
                .executionId("exec-123")
                .executionName("customer-data-extraction-exec")
                .jobDefinition("customer-data-extraction")
                .status(JobExecutionStatus.SUCCEEDED)
                .startTime(startTime)
                .stopTime(stopTime)
                .input(Map.of("customerId", "12345"))
                .output(Map.of("result", "success"))
                .error(null)
                .cause(null)
                .metadata(Map.of("region", "us-east-1"))
                .build();

        // Then
        assertThat(execution.getExecutionId()).isEqualTo("exec-123");
        assertThat(execution.getExecutionName()).isEqualTo("customer-data-extraction-exec");
        assertThat(execution.getJobDefinition()).isEqualTo("customer-data-extraction");
        assertThat(execution.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
        assertThat(execution.getStartTime()).isEqualTo(startTime);
        assertThat(execution.getStopTime()).isEqualTo(stopTime);
        assertThat(execution.getInput()).containsEntry("customerId", "12345");
        assertThat(execution.getOutput()).containsEntry("result", "success");
        assertThat(execution.getError()).isNull();
        assertThat(execution.getCause()).isNull();
        assertThat(execution.getMetadata()).containsEntry("region", "us-east-1");
    }

    @Test
    void shouldBuildFailedJobExecution() {
        // Given/When
        JobExecution execution = JobExecution.builder()
                .executionId("exec-456")
                .jobDefinition("order-processing")
                .status(JobExecutionStatus.FAILED)
                .error("Connection timeout")
                .cause("Network error")
                .build();

        // Then
        assertThat(execution.getExecutionId()).isEqualTo("exec-456");
        assertThat(execution.getStatus()).isEqualTo(JobExecutionStatus.FAILED);
        assertThat(execution.getError()).isEqualTo("Connection timeout");
        assertThat(execution.getCause()).isEqualTo("Network error");
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // Given/When
        JobExecution execution = new JobExecution();
        execution.setExecutionId("exec-789");
        execution.setStatus(JobExecutionStatus.RUNNING);

        // Then
        assertThat(execution.getExecutionId()).isEqualTo("exec-789");
        assertThat(execution.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        JobExecution execution1 = JobExecution.builder()
                .executionId("exec-123")
                .jobDefinition("test-job")
                .status(JobExecutionStatus.RUNNING)
                .build();

        JobExecution execution2 = JobExecution.builder()
                .executionId("exec-123")
                .jobDefinition("test-job")
                .status(JobExecutionStatus.RUNNING)
                .build();

        JobExecution execution3 = JobExecution.builder()
                .executionId("exec-456")
                .jobDefinition("other-job")
                .status(JobExecutionStatus.SUCCEEDED)
                .build();

        // Then
        assertThat(execution1).isEqualTo(execution2);
        assertThat(execution1).hasSameHashCodeAs(execution2);
        assertThat(execution1).isNotEqualTo(execution3);
    }
}

