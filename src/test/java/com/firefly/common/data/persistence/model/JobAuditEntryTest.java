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

import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobAuditEntry.
 */
class JobAuditEntryTest {

    @Test
    void shouldBuildJobAuditEntryWithAllFields() {
        // Given
        Instant timestamp = Instant.now();
        Map<String, Object> inputParams = Map.of("customerId", "12345");
        Map<String, Object> outputData = Map.of("result", "success");
        Map<String, String> metadata = Map.of("env", "prod");

        // When
        JobAuditEntry entry = JobAuditEntry.builder()
                .auditId("audit-123")
                .executionId("exec-456")
                .requestId("req-789")
                .stage(JobStage.START)
                .eventType(JobAuditEntry.AuditEventType.OPERATION_STARTED)
                .status(JobExecutionStatus.RUNNING)
                .timestamp(timestamp)
                .initiator("user@example.com")
                .jobType("customer-data-extraction")
                .inputParameters(inputParams)
                .outputData(outputData)
                .durationMs(1500L)
                .orchestratorType("AWS_STEP_FUNCTIONS")
                .metadata(metadata)
                .traceId("trace-001")
                .spanId("span-001")
                .serviceName("customer-service")
                .environment("prod")
                .build();

        // Then
        assertThat(entry.getAuditId()).isEqualTo("audit-123");
        assertThat(entry.getExecutionId()).isEqualTo("exec-456");
        assertThat(entry.getRequestId()).isEqualTo("req-789");
        assertThat(entry.getStage()).isEqualTo(JobStage.START);
        assertThat(entry.getEventType()).isEqualTo(JobAuditEntry.AuditEventType.OPERATION_STARTED);
        assertThat(entry.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
        assertThat(entry.getTimestamp()).isEqualTo(timestamp);
        assertThat(entry.getInitiator()).isEqualTo("user@example.com");
        assertThat(entry.getJobType()).isEqualTo("customer-data-extraction");
        assertThat(entry.getInputParameters()).isEqualTo(inputParams);
        assertThat(entry.getOutputData()).isEqualTo(outputData);
        assertThat(entry.getDurationMs()).isEqualTo(1500L);
        assertThat(entry.getOrchestratorType()).isEqualTo("AWS_STEP_FUNCTIONS");
        assertThat(entry.getMetadata()).isEqualTo(metadata);
        assertThat(entry.getTraceId()).isEqualTo("trace-001");
        assertThat(entry.getSpanId()).isEqualTo("span-001");
        assertThat(entry.getServiceName()).isEqualTo("customer-service");
        assertThat(entry.getEnvironment()).isEqualTo("prod");
    }

    @Test
    void shouldCreateAuditEntryForOperationStarted() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";
        JobStage stage = JobStage.START;
        String initiator = "user@example.com";
        String jobType = "data-extraction";
        Map<String, Object> inputParams = Map.of("param1", "value1");

        // When
        JobAuditEntry entry = JobAuditEntry.forOperationStarted(
                executionId, requestId, stage, initiator, jobType, inputParams);

        // Then
        assertThat(entry.getAuditId()).startsWith("audit-");
        assertThat(entry.getExecutionId()).isEqualTo(executionId);
        assertThat(entry.getRequestId()).isEqualTo(requestId);
        assertThat(entry.getStage()).isEqualTo(stage);
        assertThat(entry.getEventType()).isEqualTo(JobAuditEntry.AuditEventType.OPERATION_STARTED);
        assertThat(entry.getStatus()).isEqualTo(JobExecutionStatus.RUNNING);
        assertThat(entry.getInitiator()).isEqualTo(initiator);
        assertThat(entry.getJobType()).isEqualTo(jobType);
        assertThat(entry.getInputParameters()).isEqualTo(inputParams);
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    void shouldCreateAuditEntryForOperationCompleted() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";
        JobStage stage = JobStage.COLLECT;
        JobExecutionStatus status = JobExecutionStatus.SUCCEEDED;
        Map<String, Object> outputData = Map.of("result", "success");
        Long durationMs = 2000L;

        // When
        JobAuditEntry entry = JobAuditEntry.forOperationCompleted(
                executionId, requestId, stage, status, outputData, durationMs);

        // Then
        assertThat(entry.getAuditId()).startsWith("audit-");
        assertThat(entry.getExecutionId()).isEqualTo(executionId);
        assertThat(entry.getRequestId()).isEqualTo(requestId);
        assertThat(entry.getStage()).isEqualTo(stage);
        assertThat(entry.getEventType()).isEqualTo(JobAuditEntry.AuditEventType.OPERATION_COMPLETED);
        assertThat(entry.getStatus()).isEqualTo(status);
        assertThat(entry.getOutputData()).isEqualTo(outputData);
        assertThat(entry.getDurationMs()).isEqualTo(durationMs);
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    void shouldCreateAuditEntryForOperationFailed() {
        // Given
        String executionId = "exec-123";
        String requestId = "req-456";
        JobStage stage = JobStage.CHECK;
        String errorMessage = "Connection timeout";
        String errorStackTrace = "java.net.SocketTimeoutException...";
        Long durationMs = 5000L;

        // When
        JobAuditEntry entry = JobAuditEntry.forOperationFailed(
                executionId, requestId, stage, errorMessage, errorStackTrace, durationMs);

        // Then
        assertThat(entry.getAuditId()).startsWith("audit-");
        assertThat(entry.getExecutionId()).isEqualTo(executionId);
        assertThat(entry.getRequestId()).isEqualTo(requestId);
        assertThat(entry.getStage()).isEqualTo(stage);
        assertThat(entry.getEventType()).isEqualTo(JobAuditEntry.AuditEventType.OPERATION_FAILED);
        assertThat(entry.getStatus()).isEqualTo(JobExecutionStatus.FAILED);
        assertThat(entry.getErrorMessage()).isEqualTo(errorMessage);
        assertThat(entry.getErrorStackTrace()).isEqualTo(errorStackTrace);
        assertThat(entry.getDurationMs()).isEqualTo(durationMs);
        assertThat(entry.getTimestamp()).isNotNull();
    }

    @Test
    void shouldSupportAllAuditEventTypes() {
        // Verify all enum values exist
        assertThat(JobAuditEntry.AuditEventType.values()).containsExactlyInAnyOrder(
                JobAuditEntry.AuditEventType.OPERATION_STARTED,
                JobAuditEntry.AuditEventType.OPERATION_COMPLETED,
                JobAuditEntry.AuditEventType.OPERATION_FAILED,
                JobAuditEntry.AuditEventType.OPERATION_RETRIED,
                JobAuditEntry.AuditEventType.CIRCUIT_BREAKER_OPENED,
                JobAuditEntry.AuditEventType.CIRCUIT_BREAKER_CLOSED,
                JobAuditEntry.AuditEventType.RATE_LIMIT_EXCEEDED,
                JobAuditEntry.AuditEventType.STATUS_CHANGED,
                JobAuditEntry.AuditEventType.CUSTOM
        );
    }

    @Test
    void shouldGenerateUniqueAuditIds() {
        // When
        JobAuditEntry entry1 = JobAuditEntry.forOperationStarted(
                "exec-1", "req-1", JobStage.START, "user", "job", Map.of());
        JobAuditEntry entry2 = JobAuditEntry.forOperationStarted(
                "exec-2", "req-2", JobStage.START, "user", "job", Map.of());

        // Then
        assertThat(entry1.getAuditId()).isNotEqualTo(entry2.getAuditId());
    }
}

