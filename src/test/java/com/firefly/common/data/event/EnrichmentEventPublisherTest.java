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

package com.firefly.common.data.event;

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for EnrichmentEventPublisher.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EnrichmentEventPublisher enrichmentEventPublisher;

    @BeforeEach
    void setUp() {
        enrichmentEventPublisher = new EnrichmentEventPublisher(eventPublisher);
    }

    @Test
    void publishEnrichmentStarted_shouldPublishEvent() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .tenantId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .requestId("req-001")
                .initiator("test-user")
                .build();

        String providerName = "Test Provider";

        // When
        enrichmentEventPublisher.publishEnrichmentStarted(request, providerName);

        // Then
        ArgumentCaptor<EnrichmentEvent> eventCaptor = ArgumentCaptor.forClass(EnrichmentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EnrichmentEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("ENRICHMENT_STARTED");
        assertThat(capturedEvent.getEnrichmentType()).isEqualTo("company-profile");
        assertThat(capturedEvent.getProviderName()).isEqualTo("Test Provider");
        assertThat(capturedEvent.getTenantId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
        assertThat(capturedEvent.getRequestId()).isEqualTo("req-001");
        assertThat(capturedEvent.getTimestamp()).isNotNull();
    }

    @Test
    void publishEnrichmentCompleted_shouldPublishEvent() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .tenantId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .requestId("req-001")
                .build();

        EnrichmentResponse response = EnrichmentResponse.builder()
                .success(true)
                .enrichedData(Map.of("companyId", "12345", "name", "Acme Corp"))
                .providerName("Test Provider")
                .type("company-profile")
                .fieldsEnriched(2)
                .cost(0.05)
                .message("Enrichment successful")
                .requestId("req-001")
                .build();

        long durationMillis = 150L;

        // When
        enrichmentEventPublisher.publishEnrichmentCompleted(request, response, durationMillis);

        // Then
        ArgumentCaptor<EnrichmentEvent> eventCaptor = ArgumentCaptor.forClass(EnrichmentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EnrichmentEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("ENRICHMENT_COMPLETED");
        assertThat(capturedEvent.getEnrichmentType()).isEqualTo("company-profile");
        assertThat(capturedEvent.getProviderName()).isEqualTo("Test Provider");
        assertThat(capturedEvent.isSuccess()).isTrue();
        assertThat(capturedEvent.getMessage()).isEqualTo("Enrichment successful");
        assertThat(capturedEvent.getDurationMillis()).isEqualTo(durationMillis);
        assertThat(capturedEvent.getFieldsEnriched()).isEqualTo(2);
        assertThat(capturedEvent.getCost()).isEqualTo(0.05);
    }

    @Test
    void publishEnrichmentFailed_shouldPublishEvent() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .tenantId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .requestId("req-001")
                .build();

        String providerName = "Test Provider";
        String errorMessage = "Provider unavailable";
        Exception error = new RuntimeException(errorMessage);
        long durationMillis = 50L;

        // When
        enrichmentEventPublisher.publishEnrichmentFailed(request, providerName, errorMessage, error, durationMillis);

        // Then
        ArgumentCaptor<EnrichmentEvent> eventCaptor = ArgumentCaptor.forClass(EnrichmentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EnrichmentEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("ENRICHMENT_FAILED");
        assertThat(capturedEvent.getEnrichmentType()).isEqualTo("company-profile");
        assertThat(capturedEvent.getProviderName()).isEqualTo("Test Provider");
        assertThat(capturedEvent.isSuccess()).isFalse();
        assertThat(capturedEvent.getError()).isEqualTo("Provider unavailable");
        assertThat(capturedEvent.getDurationMillis()).isEqualTo(durationMillis);
    }

    @Test
    void publishEnrichmentEvent_shouldIncludeAllMetadata() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .tenantId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
                .requestId("req-001")
                .metadata(Map.of("source", "api", "priority", "high"))
                .build();

        EnrichmentResponse response = EnrichmentResponse.builder()
                .success(true)
                .providerName("Test Provider")
                .type("company-profile")
                .fieldsEnriched(5)
                .cost(0.10)
                .costCurrency("USD")
                .confidenceScore(0.95)
                .requestId("req-001")
                .metadata(Map.of("cacheHit", "false"))
                .build();

        // When
        enrichmentEventPublisher.publishEnrichmentCompleted(request, response, 200L);

        // Then
        ArgumentCaptor<EnrichmentEvent> eventCaptor = ArgumentCaptor.forClass(EnrichmentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        EnrichmentEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getMetadata()).containsEntry("cacheHit", "false");
        assertThat(capturedEvent.getFieldsEnriched()).isEqualTo(5);
        assertThat(capturedEvent.getCost()).isEqualTo(0.10);
    }
}

