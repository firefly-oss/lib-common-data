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
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.persistence.model.JobExecutionResult;
import com.firefly.common.data.persistence.port.JobExecutionResultRepository;
import com.firefly.common.data.util.DataSizeCalculator;
import com.firefly.common.data.util.TracingContextExtractor;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing job execution results.
 * 
 * This service provides high-level operations for storing and retrieving
 * job execution results. It uses the JobExecutionResultRepository port for persistence.
 */
@Slf4j
public class JobExecutionResultService {

    private final Optional<JobExecutionResultRepository> resultRepository;
    private final JobOrchestrationProperties properties;
    private final ObservationRegistry observationRegistry;
    private final String serviceName;
    private final String environment;

    public JobExecutionResultService(
            Optional<JobExecutionResultRepository> resultRepository,
            JobOrchestrationProperties properties,
            ObservationRegistry observationRegistry) {
        this.resultRepository = resultRepository;
        this.properties = properties;
        this.observationRegistry = observationRegistry;
        this.serviceName = System.getProperty("spring.application.name", "unknown-service");
        this.environment = System.getProperty("spring.profiles.active", "default");
    }

    /**
     * Saves a successful job execution result.
     */
    public Mono<JobExecutionResult> saveSuccessResult(
            JobStageRequest request,
            JobStageResponse response,
            Instant startTime,
            Map<String, Object> rawOutput,
            Map<String, Object> transformedOutput,
            String orchestratorType,
            String jobDefinition) {
        
        if (resultRepository.isEmpty() || !properties.getPersistence().isResultPersistenceEnabled()) {
            log.debug("Result repository not configured or result persistence disabled, skipping result save");
            return Mono.empty();
        }

        JobExecutionResult result = JobExecutionResult.forSuccess(
                request.getExecutionId(),
                request.getRequestId(),
                request.getJobType(),
                startTime,
                Instant.now(),
                rawOutput,
                transformedOutput
        );

        enrichWithContext(result, request, orchestratorType, jobDefinition);

        return resultRepository.get().save(result)
                .doOnSuccess(saved -> log.info("Saved successful job execution result: {}", saved.getResultId()))
                .doOnError(error -> log.error("Failed to save job execution result: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty()); // Don't fail the operation if save fails
    }

    /**
     * Saves a failed job execution result.
     */
    public Mono<JobExecutionResult> saveFailureResult(
            JobStageRequest request,
            Instant startTime,
            String errorMessage,
            String errorCause,
            String orchestratorType,
            String jobDefinition) {
        
        if (resultRepository.isEmpty() || !properties.getPersistence().isResultPersistenceEnabled()) {
            return Mono.empty();
        }

        JobExecutionResult result = JobExecutionResult.forFailure(
                request.getExecutionId(),
                request.getRequestId(),
                request.getJobType(),
                startTime,
                Instant.now(),
                errorMessage,
                errorCause
        );

        enrichWithContext(result, request, orchestratorType, jobDefinition);

        return resultRepository.get().save(result)
                .doOnSuccess(saved -> log.info("Saved failed job execution result: {}", saved.getResultId()))
                .doOnError(error -> log.error("Failed to save job execution result: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty());
    }

    /**
     * Updates an existing result with transformed output.
     */
    public Mono<JobExecutionResult> updateWithTransformedOutput(
            String executionId,
            Map<String, Object> transformedOutput,
            String targetDtoClass,
            String mapperName) {
        
        if (resultRepository.isEmpty()) {
            return Mono.empty();
        }

        return resultRepository.get().findByExecutionId(executionId)
                .flatMap(result -> {
                    result.setTransformedOutput(transformedOutput);
                    result.setTargetDtoClass(targetDtoClass);
                    result.setMapperName(mapperName);
                    return resultRepository.get().save(result);
                })
                .doOnSuccess(saved -> log.debug("Updated result with transformed output: {}", saved.getResultId()))
                .doOnError(error -> log.error("Failed to update result: {}", error.getMessage(), error));
    }

    /**
     * Retrieves a result by execution ID.
     */
    public Mono<JobExecutionResult> getResult(String executionId) {
        if (resultRepository.isEmpty()) {
            return Mono.empty();
        }
        return resultRepository.get().findByExecutionId(executionId);
    }

    /**
     * Retrieves a result by request ID.
     */
    public Mono<JobExecutionResult> getResultByRequest(String requestId) {
        if (resultRepository.isEmpty()) {
            return Mono.empty();
        }
        return resultRepository.get().findByRequestId(requestId);
    }

    /**
     * Retrieves all results for a specific job type.
     */
    public Flux<JobExecutionResult> getResultsByJobType(String jobType) {
        if (resultRepository.isEmpty()) {
            return Flux.empty();
        }
        return resultRepository.get().findByJobType(jobType);
    }

    /**
     * Retrieves all results with a specific status.
     */
    public Flux<JobExecutionResult> getResultsByStatus(JobExecutionStatus status) {
        if (resultRepository.isEmpty()) {
            return Flux.empty();
        }
        return resultRepository.get().findByStatus(status);
    }

    /**
     * Checks if a cached result exists and is valid.
     */
    public Mono<Optional<JobExecutionResult>> getCachedResult(String executionId) {
        if (resultRepository.isEmpty() || !properties.getPersistence().isEnableResultCaching()) {
            return Mono.just(Optional.empty());
        }

        return resultRepository.get().findByExecutionId(executionId)
                .filter(JobExecutionResult::isCacheableAndValid)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnNext(result -> {
                    if (result.isPresent()) {
                        log.debug("Found valid cached result for execution: {}", executionId);
                    }
                });
    }

    /**
     * Cleans up old results based on retention policy.
     */
    public Mono<Long> cleanupOldResults() {
        if (resultRepository.isEmpty() || properties.getPersistence().getResultRetentionDays() <= 0) {
            return Mono.just(0L);
        }

        Instant cutoffTime = Instant.now()
                .minusSeconds(properties.getPersistence().getResultRetentionDays() * 24 * 60 * 60);

        return resultRepository.get().deleteByEndTimeBefore(cutoffTime)
                .doOnSuccess(count -> log.info("Cleaned up {} old job execution results", count))
                .doOnError(error -> log.error("Failed to cleanup old results: {}", error.getMessage(), error));
    }

    /**
     * Cleans up expired cached results.
     */
    public Mono<Long> cleanupExpiredCachedResults() {
        if (resultRepository.isEmpty() || !properties.getPersistence().isEnableResultCaching()) {
            return Mono.just(0L);
        }

        return resultRepository.get().deleteExpired()
                .doOnSuccess(count -> log.info("Cleaned up {} expired cached results", count))
                .doOnError(error -> log.error("Failed to cleanup expired results: {}", error.getMessage(), error));
    }

    /**
     * Enriches a result with contextual information.
     */
    private void enrichWithContext(
            JobExecutionResult result,
            JobStageRequest request,
            String orchestratorType,
            String jobDefinition) {
        
        result.setOrchestratorType(orchestratorType);
        result.setJobDefinition(jobDefinition);
        result.setServiceName(serviceName);
        result.setEnvironment(environment);
        result.setInitiator(request.getInitiator());
        result.setTargetDtoClass(request.getTargetDtoClass());
        result.setMapperName(request.getMapperName());
        result.setSchemaVersion("1.0");

        // Set caching properties if enabled
        if (properties.getPersistence().isEnableResultCaching()) {
            result.setCacheable(true);
            result.setTtlSeconds(properties.getPersistence().getResultCacheTtlSeconds());
            result.setExpiresAt(Instant.now().plusSeconds(result.getTtlSeconds()));
        }

        // Add tracing context if available
        var currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation != null && currentObservation.getContext() != null) {
            result.setTraceId(extractTraceId(currentObservation));
            result.setSpanId(extractSpanId(currentObservation));
        }

        // Calculate data size if output is present
        if (result.getRawOutput() != null || result.getTransformedOutput() != null) {
            result.setDataSizeBytes(estimateDataSize(result));
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
            log.trace("Extracted trace ID: {}", traceId);
        } else {
            log.trace("No trace ID available in observation context");
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
            log.trace("Extracted span ID: {}", spanId);
        } else {
            log.trace("No span ID available in observation context");
        }
        return spanId;
    }

    /**
     * Calculates the actual data size by serializing the output to JSON and measuring bytes.
     * This provides an accurate measurement of the data size for storage and monitoring purposes.
     *
     * @param result the job execution result
     * @return the total data size in bytes
     */
    private Long estimateDataSize(JobExecutionResult result) {
        long totalSize = DataSizeCalculator.calculateCombinedSize(
                result.getRawOutput(),
                result.getTransformedOutput()
        );

        if (totalSize > 0) {
            log.debug("Calculated data size for execution {}: {} ({})",
                    result.getExecutionId(),
                    totalSize,
                    DataSizeCalculator.formatSize(totalSize));
        }

        return totalSize;
    }
}

