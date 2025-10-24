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

package com.firefly.common.data.stepevents;

import com.firefly.common.eda.publisher.EventPublisher;
import com.firefly.transactional.saga.events.StepEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StepEventPublisherBridge.
 */
@ExtendWith(MockitoExtension.class)
class StepEventPublisherBridgeTest {

    @Mock
    private EventPublisher edaPublisher;

    private StepEventPublisherBridge bridge;
    private static final String DEFAULT_TOPIC = "test-step-events";

    @BeforeEach
    void setUp() {
        bridge = new StepEventPublisherBridge(DEFAULT_TOPIC, edaPublisher);
    }

    @Test
    void shouldPublishStepEventWithDefaultTopic() {
        // Given
        StepEventEnvelope stepEvent = createStepEvent();
        when(edaPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = bridge.publish(stepEvent);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(edaPublisher).publish(any(StepEventEnvelope.class), topicCaptor.capture(), anyMap());
        assertThat(topicCaptor.getValue()).isEqualTo(DEFAULT_TOPIC);
    }

    @Test
    void shouldPublishStepEventWithCustomTopic() {
        // Given
        String customTopic = "custom-topic";
        StepEventEnvelope stepEvent = createStepEvent();
        stepEvent.setTopic(customTopic);
        when(edaPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = bridge.publish(stepEvent);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(edaPublisher).publish(any(StepEventEnvelope.class), topicCaptor.capture(), anyMap());
        assertThat(topicCaptor.getValue()).isEqualTo(customTopic);
    }

    @Test
    void shouldIncludeStepMetadataInHeaders() {
        // Given
        StepEventEnvelope stepEvent = createStepEvent();
        when(edaPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = bridge.publish(stepEvent);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(edaPublisher).publish(any(StepEventEnvelope.class), anyString(), headersCaptor.capture());
        
        Map<String, Object> headers = headersCaptor.getValue();
        assertThat(headers).containsEntry("step.saga_name", "data-processing-saga");
        assertThat(headers).containsEntry("step.saga_id", "saga-123");
        assertThat(headers).containsEntry("step.step_id", "step-1");
        assertThat(headers).containsEntry("step.type", "STEP_STARTED");
        assertThat(headers).containsEntry("context", "data-processing");
        assertThat(headers).containsEntry("library", "lib-common-data");
    }

    @Test
    void shouldSetRoutingKeyWhenNotProvided() {
        // Given
        StepEventEnvelope stepEvent = createStepEvent();
        stepEvent.setKey(null); // No routing key
        when(edaPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = bridge.publish(stepEvent);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(edaPublisher).publish(any(StepEventEnvelope.class), anyString(), headersCaptor.capture());
        
        Map<String, Object> headers = headersCaptor.getValue();
        assertThat(headers).containsEntry("routing_key", "data-processing-saga:saga-123");
    }

    @Test
    void shouldHandlePublishError() {
        // Given
        StepEventEnvelope stepEvent = createStepEvent();
        RuntimeException error = new RuntimeException("Publish failed");
        when(edaPublisher.publish(any(), anyString(), anyMap()))
                .thenReturn(Mono.error(error));

        // When
        Mono<Void> result = bridge.publish(stepEvent);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    private StepEventEnvelope createStepEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", "job-123");
        payload.put("executionId", "exec-456");

        return new StepEventEnvelope(
                "data-processing-saga",
                "saga-123",
                "step-1",
                null, // topic
                "STEP_STARTED",
                "routing-key",
                payload,
                new HashMap<>(),
                1,
                100L,
                Instant.now().minusSeconds(100),
                Instant.now(),
                "SUCCESS"
        );
    }
}
