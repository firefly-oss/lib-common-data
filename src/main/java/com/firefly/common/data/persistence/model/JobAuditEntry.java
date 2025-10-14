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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Domain model representing an audit trail entry for job operations.
 * 
 * This entity captures all relevant information about job lifecycle events
 * for compliance, debugging, and monitoring purposes.
 * 
 * Each microservice implementing this library can persist these entries
 * using their preferred persistence mechanism (JPA, MongoDB, DynamoDB, etc.)
 * by implementing the JobAuditRepository port.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobAuditEntry {

    /**
     * Unique identifier for this audit entry.
     */
    private String auditId;

    /**
     * The job execution ID being audited.
     */
    private String executionId;

    /**
     * The request ID for correlation.
     */
    private String requestId;

    /**
     * The job stage being executed.
     */
    private JobStage stage;

    /**
     * The type of audit event.
     */
    private AuditEventType eventType;

    /**
     * The status of the job at the time of this audit entry.
     */
    private JobExecutionStatus status;

    /**
     * Timestamp when this audit entry was created.
     */
    private Instant timestamp;

    /**
     * The user or system that initiated the operation.
     */
    private String initiator;

    /**
     * The job type or category.
     */
    private String jobType;

    /**
     * Input parameters for the operation (sanitized for security).
     */
    private Map<String, Object> inputParameters;

    /**
     * Output data from the operation (may be truncated for large results).
     */
    private Map<String, Object> outputData;

    /**
     * Error message if the operation failed.
     */
    private String errorMessage;

    /**
     * Error stack trace if the operation failed (optional).
     */
    private String errorStackTrace;

    /**
     * Duration of the operation in milliseconds.
     */
    private Long durationMs;

    /**
     * The orchestrator type used (e.g., AWS_STEP_FUNCTIONS, APACHE_AIRFLOW).
     */
    private String orchestratorType;

    /**
     * Additional metadata for the audit entry.
     */
    private Map<String, String> metadata;

    /**
     * Trace ID for distributed tracing correlation.
     */
    private String traceId;

    /**
     * Span ID for distributed tracing correlation.
     */
    private String spanId;

    /**
     * Whether resiliency patterns were applied (circuit breaker, retry, etc.).
     */
    private Boolean resiliencyApplied;

    /**
     * Number of retry attempts if applicable.
     */
    private Integer retryAttempts;

    /**
     * The microservice name that generated this audit entry.
     */
    private String serviceName;

    /**
     * The environment (dev, staging, prod).
     */
    private String environment;

    /**
     * Enum defining types of audit events.
     */
    public enum AuditEventType {
        /**
         * Job operation started.
         */
        OPERATION_STARTED,

        /**
         * Job operation completed successfully.
         */
        OPERATION_COMPLETED,

        /**
         * Job operation failed.
         */
        OPERATION_FAILED,

        /**
         * Job operation was retried.
         */
        OPERATION_RETRIED,

        /**
         * Circuit breaker opened.
         */
        CIRCUIT_BREAKER_OPENED,

        /**
         * Circuit breaker closed.
         */
        CIRCUIT_BREAKER_CLOSED,

        /**
         * Rate limit exceeded.
         */
        RATE_LIMIT_EXCEEDED,

        /**
         * Job execution status changed.
         */
        STATUS_CHANGED,

        /**
         * Custom audit event.
         */
        CUSTOM
    }

    /**
     * Creates an audit entry for a started operation.
     */
    public static JobAuditEntry forOperationStarted(
            String executionId,
            String requestId,
            JobStage stage,
            String initiator,
            String jobType,
            Map<String, Object> inputParameters) {
        return JobAuditEntry.builder()
                .auditId(generateAuditId())
                .executionId(executionId)
                .requestId(requestId)
                .stage(stage)
                .eventType(AuditEventType.OPERATION_STARTED)
                .status(JobExecutionStatus.RUNNING)
                .timestamp(Instant.now())
                .initiator(initiator)
                .jobType(jobType)
                .inputParameters(inputParameters)
                .build();
    }

    /**
     * Creates an audit entry for a completed operation.
     */
    public static JobAuditEntry forOperationCompleted(
            String executionId,
            String requestId,
            JobStage stage,
            JobExecutionStatus status,
            Map<String, Object> outputData,
            Long durationMs) {
        return JobAuditEntry.builder()
                .auditId(generateAuditId())
                .executionId(executionId)
                .requestId(requestId)
                .stage(stage)
                .eventType(AuditEventType.OPERATION_COMPLETED)
                .status(status)
                .timestamp(Instant.now())
                .outputData(outputData)
                .durationMs(durationMs)
                .build();
    }

    /**
     * Creates an audit entry for a failed operation.
     */
    public static JobAuditEntry forOperationFailed(
            String executionId,
            String requestId,
            JobStage stage,
            String errorMessage,
            String errorStackTrace,
            Long durationMs) {
        return JobAuditEntry.builder()
                .auditId(generateAuditId())
                .executionId(executionId)
                .requestId(requestId)
                .stage(stage)
                .eventType(AuditEventType.OPERATION_FAILED)
                .status(JobExecutionStatus.FAILED)
                .timestamp(Instant.now())
                .errorMessage(errorMessage)
                .errorStackTrace(errorStackTrace)
                .durationMs(durationMs)
                .build();
    }

    /**
     * Generates a unique audit ID.
     */
    private static String generateAuditId() {
        return "audit-" + java.util.UUID.randomUUID().toString();
    }
}

