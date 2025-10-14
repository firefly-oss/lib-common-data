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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Domain model representing the complete result of a job execution.
 * 
 * This entity stores the final state and output of a job execution,
 * allowing microservices to retrieve historical results without
 * re-executing the job.
 * 
 * Each microservice implementing this library can persist these results
 * using their preferred persistence mechanism (JPA, MongoDB, DynamoDB, etc.)
 * by implementing the JobExecutionResultRepository port.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionResult {

    /**
     * Unique identifier for this result entry.
     */
    private String resultId;

    /**
     * The job execution ID.
     */
    private String executionId;

    /**
     * The request ID for correlation.
     */
    private String requestId;

    /**
     * The job type or category.
     */
    private String jobType;

    /**
     * The final status of the job execution.
     */
    private JobExecutionStatus status;

    /**
     * When the job execution started.
     */
    private Instant startTime;

    /**
     * When the job execution completed.
     */
    private Instant endTime;

    /**
     * Total duration of the job execution.
     */
    private Duration duration;

    /**
     * The user or system that initiated the job.
     */
    private String initiator;

    /**
     * Input parameters provided to the job.
     */
    private Map<String, Object> inputParameters;

    /**
     * Raw output data from the job (COLLECT stage).
     */
    private Map<String, Object> rawOutput;

    /**
     * Transformed output data (RESULT stage).
     */
    private Map<String, Object> transformedOutput;

    /**
     * The target DTO class used for transformation.
     */
    private String targetDtoClass;

    /**
     * The mapper name used for transformation.
     */
    private String mapperName;

    /**
     * Error message if the job failed.
     */
    private String errorMessage;

    /**
     * Error cause if the job failed.
     */
    private String errorCause;

    /**
     * Error stack trace if the job failed (optional).
     */
    private String errorStackTrace;

    /**
     * The orchestrator type used.
     */
    private String orchestratorType;

    /**
     * The job definition identifier (e.g., state machine ARN, DAG ID).
     */
    private String jobDefinition;

    /**
     * Progress percentage at completion (0-100).
     */
    private Integer progressPercentage;

    /**
     * Number of retry attempts made.
     */
    private Integer retryAttempts;

    /**
     * Whether the result is cached and can be reused.
     */
    private Boolean cacheable;

    /**
     * Time-to-live for cached results (in seconds).
     */
    private Long ttlSeconds;

    /**
     * When this result expires (for cached results).
     */
    private Instant expiresAt;

    /**
     * Additional metadata for the result.
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
     * The microservice name that generated this result.
     */
    private String serviceName;

    /**
     * The environment (dev, staging, prod).
     */
    private String environment;

    /**
     * Version of the result schema (for backward compatibility).
     */
    private String schemaVersion;

    /**
     * Size of the result data in bytes (for monitoring).
     */
    private Long dataSizeBytes;

    /**
     * Tags for categorization and search.
     */
    private Map<String, String> tags;

    /**
     * Creates a result entry for a successful job execution.
     */
    public static JobExecutionResult forSuccess(
            String executionId,
            String requestId,
            String jobType,
            Instant startTime,
            Instant endTime,
            Map<String, Object> rawOutput,
            Map<String, Object> transformedOutput) {
        return JobExecutionResult.builder()
                .resultId(generateResultId())
                .executionId(executionId)
                .requestId(requestId)
                .jobType(jobType)
                .status(JobExecutionStatus.SUCCEEDED)
                .startTime(startTime)
                .endTime(endTime)
                .duration(Duration.between(startTime, endTime))
                .rawOutput(rawOutput)
                .transformedOutput(transformedOutput)
                .progressPercentage(100)
                .build();
    }

    /**
     * Creates a result entry for a failed job execution.
     */
    public static JobExecutionResult forFailure(
            String executionId,
            String requestId,
            String jobType,
            Instant startTime,
            Instant endTime,
            String errorMessage,
            String errorCause) {
        return JobExecutionResult.builder()
                .resultId(generateResultId())
                .executionId(executionId)
                .requestId(requestId)
                .jobType(jobType)
                .status(JobExecutionStatus.FAILED)
                .startTime(startTime)
                .endTime(endTime)
                .duration(Duration.between(startTime, endTime))
                .errorMessage(errorMessage)
                .errorCause(errorCause)
                .build();
    }

    /**
     * Checks if this result is still valid (not expired).
     */
    public boolean isValid() {
        if (expiresAt == null) {
            return true;
        }
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if this result is cacheable and valid.
     */
    public boolean isCacheableAndValid() {
        return Boolean.TRUE.equals(cacheable) && isValid();
    }

    /**
     * Generates a unique result ID.
     */
    private static String generateResultId() {
        return "result-" + java.util.UUID.randomUUID().toString();
    }
}

