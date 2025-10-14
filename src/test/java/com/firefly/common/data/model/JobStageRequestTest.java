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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobStageRequest.
 */
class JobStageRequestTest {

    @Test
    void shouldBuildJobStageRequestWithAllFields() {
        // Given/When
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .jobType("customer-data-extraction")
                .parameters(Map.of("customerId", "12345"))
                .requestId("req-001")
                .initiator("user@example.com")
                .metadata(Map.of("source", "api"))
                .targetDtoClass("com.example.CustomerDTO")
                .mapperName("CustomerMapper")
                .build();

        // Then
        assertThat(request.getStage()).isEqualTo(JobStage.START);
        assertThat(request.getExecutionId()).isEqualTo("exec-123");
        assertThat(request.getJobType()).isEqualTo("customer-data-extraction");
        assertThat(request.getParameters()).containsEntry("customerId", "12345");
        assertThat(request.getRequestId()).isEqualTo("req-001");
        assertThat(request.getInitiator()).isEqualTo("user@example.com");
        assertThat(request.getMetadata()).containsEntry("source", "api");
        assertThat(request.getTargetDtoClass()).isEqualTo("com.example.CustomerDTO");
        assertThat(request.getMapperName()).isEqualTo("CustomerMapper");
    }

    @Test
    void shouldBuildMinimalJobStageRequest() {
        // Given/When
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-123")
                .build();

        // Then
        assertThat(request.getStage()).isEqualTo(JobStage.CHECK);
        assertThat(request.getExecutionId()).isEqualTo("exec-123");
        assertThat(request.getJobType()).isNull();
        assertThat(request.getParameters()).isNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // Given/When
        JobStageRequest request = new JobStageRequest();
        request.setStage(JobStage.COLLECT);
        request.setExecutionId("exec-456");

        // Then
        assertThat(request.getStage()).isEqualTo(JobStage.COLLECT);
        assertThat(request.getExecutionId()).isEqualTo("exec-456");
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // Given/When
        JobStageRequest request = new JobStageRequest(
                JobStage.RESULT,
                "exec-789",
                "order-processing",
                Map.of("orderId", "order-123"),
                "req-002",
                "system",
                Map.of("env", "prod"),
                "com.example.OrderDTO",
                "OrderMapper"
        );

        // Then
        assertThat(request.getStage()).isEqualTo(JobStage.RESULT);
        assertThat(request.getExecutionId()).isEqualTo("exec-789");
        assertThat(request.getJobType()).isEqualTo("order-processing");
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        JobStageRequest request1 = JobStageRequest.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .build();

        JobStageRequest request2 = JobStageRequest.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .build();

        JobStageRequest request3 = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-456")
                .build();

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).hasSameHashCodeAs(request2);
        assertThat(request1).isNotEqualTo(request3);
    }

    @Test
    void shouldSupportToString() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.START)
                .executionId("exec-123")
                .jobType("test-job")
                .build();

        // When
        String toString = request.toString();

        // Then
        assertThat(toString).contains("START");
        assertThat(toString).contains("exec-123");
        assertThat(toString).contains("test-job");
    }
}

