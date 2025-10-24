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

package com.firefly.common.data.observability;

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.model.JobStage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Service for adding distributed tracing to job operations.
 */
@Service
@Slf4j
public class JobTracingService {

    private final ObservationRegistry observationRegistry;
    private final JobOrchestrationProperties properties;

    public JobTracingService(ObservationRegistry observationRegistry,
                             JobOrchestrationProperties properties) {
        this.observationRegistry = observationRegistry;
        this.properties = properties;
    }

    /**
     * Wraps a job operation with tracing.
     *
     * @param stage the job stage
     * @param executionId the execution ID
     * @param operation the operation to trace
     * @param <T> the return type
     * @return the traced operation
     */
    public <T> Mono<T> traceJobOperation(JobStage stage, String executionId, Mono<T> operation) {
        if (!properties.getObservability().isTracingEnabled() || !shouldTraceStage(stage)) {
            return operation;
        }

        return Mono.defer(() -> {
            Observation observation = createObservation(stage, executionId);
            
            return operation
                    .doOnSubscribe(s -> {
                        observation.start();
                        log.debug("Started tracing for stage {} with execution ID {}", stage, executionId);
                    })
                    .doOnSuccess(result -> {
                        observation.event(Observation.Event.of("job.stage.success", "Job stage completed successfully"));
                        log.debug("Job stage {} completed successfully for execution {}", stage, executionId);
                    })
                    .doOnError(error -> {
                        observation.error(error);
                        observation.event(Observation.Event.of("job.stage.error", "Job stage failed: " + error.getMessage()));
                        log.error("Job stage {} failed for execution {}: {}", stage, executionId, error.getMessage());
                    })
                    .doFinally(signalType -> {
                        observation.stop();
                        log.debug("Stopped tracing for stage {} with execution ID {}", stage, executionId);
                    });
        });
    }

    /**
     * Wraps a synchronous job operation with tracing.
     *
     * @param stage the job stage
     * @param executionId the execution ID
     * @param operation the operation to trace
     * @param <T> the return type
     * @return the result
     */
    public <T> T traceJobOperationSync(JobStage stage, String executionId, Supplier<T> operation) {
        if (!properties.getObservability().isTracingEnabled() || !shouldTraceStage(stage)) {
            return operation.get();
        }

        Observation observation = createObservation(stage, executionId);
        
        try {
            observation.start();
            log.debug("Started tracing for stage {} with execution ID {}", stage, executionId);
            
            T result = operation.get();
            
            observation.event(Observation.Event.of("job.stage.success", "Job stage completed successfully"));
            log.debug("Job stage {} completed successfully for execution {}", stage, executionId);
            
            return result;
        } catch (Exception e) {
            observation.error(e);
            observation.event(Observation.Event.of("job.stage.error", "Job stage failed: " + e.getMessage()));
            log.error("Job stage {} failed for execution {}: {}", stage, executionId, e.getMessage());
            throw e;
        } finally {
            observation.stop();
            log.debug("Stopped tracing for stage {} with execution ID {}", stage, executionId);
        }
    }

    /**
     * Creates an observation for a job operation.
     */
    private Observation createObservation(JobStage stage, String executionId) {
        return Observation.createNotStarted("job.stage." + stage.name().toLowerCase(), observationRegistry)
                .lowCardinalityKeyValue("job.stage", stage.name())
                .lowCardinalityKeyValue("execution.id", executionId != null ? executionId : "unknown")
                .lowCardinalityKeyValue("orchestrator.type", properties.getOrchestratorType())
                .highCardinalityKeyValue("execution.id.full", executionId != null ? executionId : "unknown");
    }

    /**
     * Determines if a stage should be traced based on configuration.
     */
    private boolean shouldTraceStage(JobStage stage) {
        return switch (stage) {
            case START -> properties.getObservability().isTraceJobStart();
            case CHECK -> properties.getObservability().isTraceJobCheck();
            case COLLECT -> properties.getObservability().isTraceJobCollect();
            case RESULT -> properties.getObservability().isTraceJobResult();
            case STOP -> true; // Always trace STOP operations
            case ALL -> true; // Always trace START operations
        };
    }

    /**
     * Adds custom tags to the current observation.
     */
    public void addTags(Map<String, String> tags) {
        if (!properties.getObservability().isTracingEnabled()) {
            return;
        }

        Observation currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation != null) {
            tags.forEach((key, value) -> 
                currentObservation.highCardinalityKeyValue(key, value)
            );
        }
    }

    /**
     * Records an event in the current observation.
     */
    public void recordEvent(String eventName, String eventDescription) {
        if (!properties.getObservability().isTracingEnabled()) {
            return;
        }

        Observation currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation != null) {
            currentObservation.event(Observation.Event.of(eventName, eventDescription));
        }
    }

    /**
     * Wraps a generic operation with tracing.
     *
     * <p>This is a generic tracing method that can be used for any operation,
     * not just job stages. Useful for enrichment operations, custom operations, etc.</p>
     *
     * @param operationName the name of the operation
     * @param operationId the operation ID for correlation
     * @param operation the operation to trace
     * @param <T> the return type
     * @return the traced operation
     */
    public <T> Mono<T> traceOperation(String operationName, String operationId, Mono<T> operation) {
        if (!properties.getObservability().isTracingEnabled()) {
            return operation;
        }

        return Mono.defer(() -> {
            Observation observation = Observation.createNotStarted(operationName, observationRegistry)
                    .lowCardinalityKeyValue("operation.name", operationName)
                    .lowCardinalityKeyValue("operation.id", operationId != null ? operationId : "unknown");

            return operation
                    .doOnSubscribe(s -> {
                        observation.start();
                        log.debug("Started tracing for operation {} with ID {}", operationName, operationId);
                    })
                    .doOnSuccess(result -> {
                        observation.event(Observation.Event.of("operation.success", "Operation completed successfully"));
                        log.debug("Operation {} completed successfully for ID {}", operationName, operationId);
                    })
                    .doOnError(error -> {
                        observation.error(error);
                        observation.event(Observation.Event.of("operation.error", "Operation failed: " + error.getMessage()));
                        log.error("Operation {} failed for ID {}: {}", operationName, operationId, error.getMessage());
                    })
                    .doFinally(signalType -> {
                        observation.stop();
                        log.debug("Stopped tracing for operation {} with ID {}", operationName, operationId);
                    });
        });
    }
}

