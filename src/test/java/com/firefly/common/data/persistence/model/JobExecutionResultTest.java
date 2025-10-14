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

package com.firefly.common.data.persistence.model;

import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobExecutionResult.
 */
class JobExecutionResultTest {

    @Test
    void shouldBuildJobExecutionResultWithAllFields() {
        // Given
        Instant startTime = Instant.now().minusSeconds(60);
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        Map<String, Object> rawOutput = Map.of("raw", "data");
        Map<String, Object> transformedOutput = Map.of("transformed", "data");
        Map<String, String> metadata = Map.of("key", "value");
        Map<String, String> tags = Map.of("tag1", "value1");

        // When
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .executionId("exec-456")
                .requestId("req-789")
                .jobType("customer-data-extraction")
                .status(JobExecutionStatus.SUCCEEDED)
                .startTime(startTime)
                .endTime(endTime)
                .duration(duration)
                .initiator("user@example.com")
                .rawOutput(rawOutput)
                .transformedOutput(transformedOutput)
                .targetDtoClass("com.example.CustomerDTO")
                .mapperName("CustomerMapper")
                .orchestratorType("AWS_STEP_FUNCTIONS")
                .jobDefinition("arn:aws:states:us-east-1:123456789012:stateMachine:customer-data")
                .progressPercentage(100)
                .cacheable(true)
                .ttlSeconds(3600L)
                .metadata(metadata)
                .traceId("trace-001")
                .spanId("span-001")
                .serviceName("customer-service")
                .environment("prod")
                .schemaVersion("1.0")
                .dataSizeBytes(1024L)
                .tags(tags)
                .build();

        // Then
        assertThat(result.getResultId()).isEqualTo("result-123");
        assertThat(result.getExecutionId()).isEqualTo("exec-456");
        assertThat(result.getRequestId()).isEqualTo("req-789");
        assertThat(result.getJobType()).isEqualTo("customer-data-extraction");
        assertThat(result.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);
        assertThat(result.getDuration()).isEqualTo(duration);
        assertThat(result.getInitiator()).isEqualTo("user@example.com");
        assertThat(result.getRawOutput()).isEqualTo(rawOutput);
        assertThat(result.getTransformedOutput()).isEqualTo(transformedOutput);
        assertThat(result.getTargetDtoClass()).isEqualTo("com.example.CustomerDTO");
        assertThat(result.getMapperName()).isEqualTo("CustomerMapper");
        assertThat(result.getOrchestratorType()).isEqualTo("AWS_STEP_FUNCTIONS");
        assertThat(result.getJobDefinition()).isEqualTo("arn:aws:states:us-east-1:123456789012:stateMachine:customer-data");
        assertThat(result.getProgressPercentage()).isEqualTo(100);
        assertThat(result.getCacheable()).isTrue();
        assertThat(result.getTtlSeconds()).isEqualTo(3600L);
        assertThat(result.getMetadata()).isEqualTo(metadata);
        assertThat(result.getTraceId()).isEqualTo("trace-001");
        assertThat(result.getSpanId()).isEqualTo("span-001");
        assertThat(result.getServiceName()).isEqualTo("customer-service");
        assertThat(result.getEnvironment()).isEqualTo("prod");
        assertThat(result.getSchemaVersion()).isEqualTo("1.0");
        assertThat(result.getDataSizeBytes()).isEqualTo(1024L);
        assertThat(result.getTags()).isEqualTo(tags);
    }

    @Test
    void shouldCreateResultForSuccess() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";
        String jobType = "data-extraction";
        Instant startTime = Instant.now().minusSeconds(30);
        Instant endTime = Instant.now();
        Map<String, Object> rawOutput = Map.of("raw", "data");
        Map<String, Object> transformedOutput = Map.of("transformed", "data");

        // When
        JobExecutionResult result = JobExecutionResult.forSuccess(
                executionId, requestId, jobType, startTime, endTime, rawOutput, transformedOutput);

        // Then
        assertThat(result.getResultId()).startsWith("result-");
        assertThat(result.getExecutionId()).isEqualTo(executionId);
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getJobType()).isEqualTo(jobType);
        assertThat(result.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);
        assertThat(result.getDuration()).isEqualTo(Duration.between(startTime, endTime));
        assertThat(result.getRawOutput()).isEqualTo(rawOutput);
        assertThat(result.getTransformedOutput()).isEqualTo(transformedOutput);
        assertThat(result.getProgressPercentage()).isEqualTo(100);
    }

    @Test
    void shouldCreateResultForFailure() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";
        String jobType = "data-extraction";
        Instant startTime = Instant.now().minusSeconds(30);
        Instant endTime = Instant.now();
        String errorMessage = "Connection timeout";
        String errorCause = "SocketTimeoutException";

        // When
        JobExecutionResult result = JobExecutionResult.forFailure(
                executionId, requestId, jobType, startTime, endTime, errorMessage, errorCause);

        // Then
        assertThat(result.getResultId()).startsWith("result-");
        assertThat(result.getExecutionId()).isEqualTo(executionId);
        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getJobType()).isEqualTo(jobType);
        assertThat(result.getStatus()).isEqualTo(JobExecutionStatus.FAILED);
        assertThat(result.getStartTime()).isEqualTo(startTime);
        assertThat(result.getEndTime()).isEqualTo(endTime);
        assertThat(result.getDuration()).isEqualTo(Duration.between(startTime, endTime));
        assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(result.getErrorCause()).isEqualTo(errorCause);
    }

    @Test
    void shouldValidateNonExpiredResult() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // When/Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldValidateExpiredResult() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        // When/Then
        assertThat(result.isValid()).isFalse();
    }

    @Test
    void shouldValidateResultWithNoExpiration() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .expiresAt(null)
                .build();

        // When/Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldValidateCacheableAndValidResult() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .cacheable(true)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // When/Then
        assertThat(result.isCacheableAndValid()).isTrue();
    }

    @Test
    void shouldInvalidateNonCacheableResult() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .cacheable(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // When/Then
        assertThat(result.isCacheableAndValid()).isFalse();
    }

    @Test
    void shouldInvalidateExpiredCacheableResult() {
        // Given
        JobExecutionResult result = JobExecutionResult.builder()
                .resultId("result-123")
                .cacheable(true)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        // When/Then
        assertThat(result.isCacheableAndValid()).isFalse();
    }

    @Test
    void shouldGenerateUniqueResultIds() {
        // When
        JobExecutionResult result1 = JobExecutionResult.forSuccess(
                "exec-1", "req-1", "job", Instant.now(), Instant.now(), Map.of(), Map.of());
        JobExecutionResult result2 = JobExecutionResult.forSuccess(
                "exec-2", "req-2", "job", Instant.now(), Instant.now(), Map.of(), Map.of());

        // Then
        assertThat(result1.getResultId()).isNotEqualTo(result2.getResultId());
    }
}

