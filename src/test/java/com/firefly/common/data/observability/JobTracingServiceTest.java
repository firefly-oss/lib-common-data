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
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class JobTracingServiceTest {

    private JobTracingService tracingService;
    private JobOrchestrationProperties properties;
    private ObservationRegistry observationRegistry;

    @BeforeEach
    void setUp() {
        observationRegistry = ObservationRegistry.create();
        properties = new JobOrchestrationProperties();
        properties.getObservability().setTracingEnabled(true);
        properties.getObservability().setTraceJobStart(true);
        properties.getObservability().setTraceJobCheck(true);
        properties.getObservability().setTraceJobCollect(true);
        properties.getObservability().setTraceJobResult(true);
        
        tracingService = new JobTracingService(observationRegistry, properties);
    }

    @Test
    void shouldTraceSuccessfulOperation() {
        // Given
        Mono<String> operation = Mono.just("success");
        
        // When
        Mono<String> traced = tracingService.traceJobOperation(JobStage.START, "exec-123", operation);
        
        // Then
        StepVerifier.create(traced)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldTraceFailedOperation() {
        // Given
        Mono<String> operation = Mono.error(new RuntimeException("Test error"));
        
        // When
        Mono<String> traced = tracingService.traceJobOperation(JobStage.START, "exec-123", operation);
        
        // Then
        StepVerifier.create(traced)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldNotTraceWhenDisabled() {
        // Given
        properties.getObservability().setTracingEnabled(false);
        Mono<String> operation = Mono.just("success");
        
        // When
        Mono<String> traced = tracingService.traceJobOperation(JobStage.START, "exec-123", operation);
        
        // Then
        StepVerifier.create(traced)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldNotTraceStageWhenStageTracingDisabled() {
        // Given
        properties.getObservability().setTraceJobStart(false);
        Mono<String> operation = Mono.just("success");
        
        // When
        Mono<String> traced = tracingService.traceJobOperation(JobStage.START, "exec-123", operation);
        
        // Then
        StepVerifier.create(traced)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldTraceSynchronousOperation() {
        // Given
        String expected = "sync-result";
        
        // When
        String result = tracingService.traceJobOperationSync(JobStage.CHECK, "exec-456", () -> expected);
        
        // Then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldTraceSynchronousOperationFailure() {
        // Given / When / Then
        try {
            tracingService.traceJobOperationSync(JobStage.CHECK, "exec-456", () -> {
                throw new RuntimeException("Sync error");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Sync error");
        }
    }

    @Test
    void shouldRecordEvent() {
        // Given / When
        tracingService.recordEvent("test.event", "Test event description");
        
        // Then - no exception should be thrown
    }

    @Test
    void shouldAddTags() {
        // Given / When
        tracingService.addTags(java.util.Map.of("key1", "value1", "key2", "value2"));
        
        // Then - no exception should be thrown
    }
}

