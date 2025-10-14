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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobExecutionRequest.
 */
class JobExecutionRequestTest {

    @Test
    void shouldBuildJobExecutionRequestWithAllFields() {
        // Given/When
        JobExecutionRequest request = JobExecutionRequest.builder()
                .jobDefinition("customer-data-extraction")
                .executionName("customer-extraction-001")
                .input(Map.of("customerId", "12345", "includeHistory", true))
                .metadata(Map.of("source", "api", "region", "us-east-1"))
                .build();

        // Then
        assertThat(request.getJobDefinition()).isEqualTo("customer-data-extraction");
        assertThat(request.getExecutionName()).isEqualTo("customer-extraction-001");
        assertThat(request.getInput()).containsEntry("customerId", "12345");
        assertThat(request.getInput()).containsEntry("includeHistory", true);
        assertThat(request.getMetadata()).containsEntry("source", "api");
        assertThat(request.getMetadata()).containsEntry("region", "us-east-1");
    }

    @Test
    void shouldBuildMinimalJobExecutionRequest() {
        // Given/When
        JobExecutionRequest request = JobExecutionRequest.builder()
                .jobDefinition("simple-job")
                .build();

        // Then
        assertThat(request.getJobDefinition()).isEqualTo("simple-job");
        assertThat(request.getExecutionName()).isNull();
        assertThat(request.getInput()).isNull();
        assertThat(request.getMetadata()).isNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        // Given/When
        JobExecutionRequest request = new JobExecutionRequest();
        request.setJobDefinition("test-job");
        request.setInput(Map.of("param1", "value1"));

        // Then
        assertThat(request.getJobDefinition()).isEqualTo("test-job");
        assertThat(request.getInput()).containsEntry("param1", "value1");
    }

    @Test
    void shouldSupportEqualsAndHashCode() {
        // Given
        JobExecutionRequest request1 = JobExecutionRequest.builder()
                .jobDefinition("test-job")
                .input(Map.of("key", "value"))
                .build();

        JobExecutionRequest request2 = JobExecutionRequest.builder()
                .jobDefinition("test-job")
                .input(Map.of("key", "value"))
                .build();

        JobExecutionRequest request3 = JobExecutionRequest.builder()
                .jobDefinition("other-job")
                .build();

        // Then
        assertThat(request1).isEqualTo(request2);
        assertThat(request1).hasSameHashCodeAs(request2);
        assertThat(request1).isNotEqualTo(request3);
    }
}

