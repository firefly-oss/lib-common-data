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

package com.firefly.common.data.persistence.service;

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.persistence.model.JobAuditEntry;
import com.firefly.common.data.persistence.port.JobAuditRepository;
import com.firefly.common.data.util.TracingContextExtractor;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing job audit trail entries.
 * 
 * This service provides high-level operations for creating and querying
 * audit trail entries. It uses the JobAuditRepository port for persistence.
 */
@Slf4j
public class JobAuditService {

    private final Optional<JobAuditRepository> auditRepository;
    private final JobOrchestrationProperties properties;
    private final ObservationRegistry observationRegistry;
    private final String serviceName;
    private final String environment;

    public JobAuditService(
            Optional<JobAuditRepository> auditRepository,
            JobOrchestrationProperties properties,
            ObservationRegistry observationRegistry) {
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.observationRegistry = observationRegistry;
        this.serviceName = System.getProperty("spring.application.name", "unknown-service");
        this.environment = System.getProperty("spring.profiles.active", "default");
    }

    /**
     * Records the start of a job operation.
     */
    public Mono<JobAuditEntry> recordOperationStarted(
            JobStageRequest request,
            String orchestratorType) {
        
        if (auditRepository.isEmpty() || !properties.getPersistence().isAuditEnabled()) {
            log.debug("Audit repository not configured or audit disabled, skipping audit entry");
            return Mono.empty();
        }

        JobAuditEntry entry = JobAuditEntry.forOperationStarted(
                request.getExecutionId(),
                request.getRequestId(),
                request.getStage(),
                request.getInitiator(),
                request.getJobType(),
                request.getParameters()
        );

        enrichWithContext(entry, orchestratorType);

        return auditRepository.get().save(entry)
                .doOnSuccess(saved -> log.debug("Recorded operation started audit entry: {}", saved.getAuditId()))
                .doOnError(error -> log.error("Failed to save audit entry: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty()); // Don't fail the operation if audit fails
    }

    /**
     * Records the completion of a job operation.
     */
    public Mono<JobAuditEntry> recordOperationCompleted(
            JobStageRequest request,
            JobStageResponse response,
            Long durationMs,
            String orchestratorType) {
        
        if (auditRepository.isEmpty() || !properties.getPersistence().isAuditEnabled()) {
            return Mono.empty();
        }

        JobAuditEntry entry = JobAuditEntry.forOperationCompleted(
                request.getExecutionId(),
                request.getRequestId(),
                request.getStage(),
                response.getStatus(),
                response.getData(),
                durationMs
        );

        enrichWithContext(entry, orchestratorType);
        entry.setInitiator(request.getInitiator());
        entry.setJobType(request.getJobType());

        return auditRepository.get().save(entry)
                .doOnSuccess(saved -> log.debug("Recorded operation completed audit entry: {}", saved.getAuditId()))
                .doOnError(error -> log.error("Failed to save audit entry: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty());
    }

    /**
     * Records a failed job operation.
     */
    public Mono<JobAuditEntry> recordOperationFailed(
            JobStageRequest request,
            Throwable error,
            Long durationMs,
            String orchestratorType) {
        
        if (auditRepository.isEmpty() || !properties.getPersistence().isAuditEnabled()) {
            return Mono.empty();
        }

        String errorMessage = error.getMessage();
        String errorStackTrace = properties.getPersistence().isIncludeStackTraces() 
                ? getStackTraceAsString(error) 
                : null;

        JobAuditEntry entry = JobAuditEntry.forOperationFailed(
                request.getExecutionId(),
                request.getRequestId(),
                request.getStage(),
                errorMessage,
                errorStackTrace,
                durationMs
        );

        enrichWithContext(entry, orchestratorType);
        entry.setInitiator(request.getInitiator());
        entry.setJobType(request.getJobType());

        return auditRepository.get().save(entry)
                .doOnSuccess(saved -> log.debug("Recorded operation failed audit entry: {}", saved.getAuditId()))
                .doOnError(err -> log.error("Failed to save audit entry: {}", err.getMessage(), err))
                .onErrorResume(err -> Mono.empty());
    }

    /**
     * Records a custom audit event.
     */
    public Mono<JobAuditEntry> recordCustomEvent(
            String executionId,
            String requestId,
            JobStage stage,
            JobAuditEntry.AuditEventType eventType,
            Map<String, String> metadata,
            String orchestratorType) {
        
        if (auditRepository.isEmpty() || !properties.getPersistence().isAuditEnabled()) {
            return Mono.empty();
        }

        JobAuditEntry entry = JobAuditEntry.builder()
                .auditId("audit-" + java.util.UUID.randomUUID().toString())
                .executionId(executionId)
                .requestId(requestId)
                .stage(stage)
                .eventType(eventType)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();

        enrichWithContext(entry, orchestratorType);

        return auditRepository.get().save(entry)
                .doOnSuccess(saved -> log.debug("Recorded custom audit event: {}", saved.getAuditId()))
                .doOnError(error -> log.error("Failed to save audit entry: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty());
    }

    /**
     * Retrieves all audit entries for a specific execution.
     */
    public Flux<JobAuditEntry> getAuditTrail(String executionId) {
        if (auditRepository.isEmpty()) {
            return Flux.empty();
        }
        return auditRepository.get().findByExecutionId(executionId);
    }

    /**
     * Retrieves all audit entries for a specific request.
     */
    public Flux<JobAuditEntry> getAuditTrailByRequest(String requestId) {
        if (auditRepository.isEmpty()) {
            return Flux.empty();
        }
        return auditRepository.get().findByRequestId(requestId);
    }

    /**
     * Retrieves all audit entries within a time range.
     */
    public Flux<JobAuditEntry> getAuditTrailByTimeRange(Instant startTime, Instant endTime) {
        if (auditRepository.isEmpty()) {
            return Flux.empty();
        }
        return auditRepository.get().findByTimestampBetween(startTime, endTime);
    }

    /**
     * Cleans up old audit entries based on retention policy.
     */
    public Mono<Long> cleanupOldAuditEntries() {
        if (auditRepository.isEmpty() || properties.getPersistence().getAuditRetentionDays() <= 0) {
            return Mono.just(0L);
        }

        Instant cutoffTime = Instant.now()
                .minusSeconds(properties.getPersistence().getAuditRetentionDays() * 24 * 60 * 60);

        return auditRepository.get().deleteByTimestampBefore(cutoffTime)
                .doOnSuccess(count -> log.info("Cleaned up {} old audit entries", count))
                .doOnError(error -> log.error("Failed to cleanup old audit entries: {}", error.getMessage(), error));
    }

    /**
     * Enriches an audit entry with contextual information.
     */
    private void enrichWithContext(JobAuditEntry entry, String orchestratorType) {
        entry.setOrchestratorType(orchestratorType);
        entry.setServiceName(serviceName);
        entry.setEnvironment(environment);

        // Add tracing context if available
        var currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation != null && currentObservation.getContext() != null) {
            // Extract trace and span IDs from observation context
            // This is a simplified version - actual implementation depends on tracing backend
            entry.setTraceId(extractTraceId(currentObservation));
            entry.setSpanId(extractSpanId(currentObservation));
        }
    }

    /**
     * Extracts the trace ID from the current observation using the TracingContextExtractor utility.
     * Supports both Brave and OpenTelemetry tracing backends.
     *
     * @param observation the current observation
     * @return the trace ID, or null if not available
     */
    private String extractTraceId(io.micrometer.observation.Observation observation) {
        String traceId = TracingContextExtractor.extractTraceId(observation);
        if (traceId != null) {
            log.trace("Extracted trace ID for audit: {}", traceId);
        } else {
            log.trace("No trace ID available in observation context for audit");
        }
        return traceId;
    }

    /**
     * Extracts the span ID from the current observation using the TracingContextExtractor utility.
     * Supports both Brave and OpenTelemetry tracing backends.
     *
     * @param observation the current observation
     * @return the span ID, or null if not available
     */
    private String extractSpanId(io.micrometer.observation.Observation observation) {
        String spanId = TracingContextExtractor.extractSpanId(observation);
        if (spanId != null) {
            log.trace("Extracted span ID for audit: {}", spanId);
        } else {
            log.trace("No span ID available in observation context for audit");
        }
        return spanId;
    }

    private String getStackTraceAsString(Throwable error) {
        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}

