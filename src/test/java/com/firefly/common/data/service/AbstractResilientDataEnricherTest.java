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

package com.firefly.common.data.service;

import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for AbstractResilientDataEnricher.
 */
@ExtendWith(MockitoExtension.class)
class AbstractResilientDataEnricherTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private TestDataEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new TestDataEnricher(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher
        );

        // Setup default mock behaviors
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enrich_shouldReturnSuccessResponse_whenEnrichmentSucceeds() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .parameters(Map.of("includeFinancials", true))
                .tenantId("tenant-001")
                .requestId("req-001")
                .build();

        Map<String, Object> enrichedData = Map.of(
                "companyId", "12345",
                "companyName", "Acme Corp",
                "revenue", 1000000
        );

        enricher.setEnrichedData(enrichedData);

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEnrichedData()).isEqualTo(enrichedData);
                    assertThat(response.getProviderName()).isEqualTo("Test Provider");
                    assertThat(response.getEnrichmentType()).isEqualTo("company-profile");
                })
                .verifyComplete();

        // Verify observability
        verify(tracingService).traceOperation(eq("enrich-company-profile"), eq("req-001"), any());
        verify(metricsService).recordEnrichmentMetrics(
                eq("company-profile"),
                eq("Test Provider"),
                eq(true),
                anyLong(),
                eq(3),
                isNull()
        );
        verify(eventPublisher).publishEnrichmentStarted(request, "Test Provider");
        verify(eventPublisher).publishEnrichmentCompleted(eq(request), any(EnrichmentResponse.class), anyLong());
    }

    @Test
    void enrich_shouldReturnFailureResponse_whenEnrichmentFails() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .requestId("req-002")
                .build();

        enricher.setShouldFail(true);

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getMessage()).contains("Enrichment failed");
                    assertThat(response.getError()).isNotNull();
                })
                .verifyComplete();

        // Verify error metrics
        verify(metricsService).recordEnrichmentError(
                eq("company-profile"),
                eq("Test Provider"),
                eq("RuntimeException"),
                anyLong()
        );
        verify(eventPublisher).publishEnrichmentFailed(eq(request), eq("Test Provider"), anyString(), any(), anyLong());
    }

    @Test
    void enrich_shouldApplyResiliency_whenConfigured() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        enricher.setEnrichedData(Map.of("companyId", "12345", "name", "Test"));

        // When
        StepVerifier.create(enricher.enrich(request))
                .expectNextCount(1)
                .verifyComplete();

        // Then
        verify(resiliencyService).decorate(any(Mono.class));
    }

    @Test
    void enrich_shouldIncludeConfidenceScore_whenProvided() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        Map<String, Object> enrichedData = Map.of("companyId", "12345", "name", "Test");
        enricher.setEnrichedData(enrichedData);
        enricher.setConfidenceScore(0.95);

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.getConfidenceScore()).isEqualTo(0.95);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldIncludeCostInformation_whenProvided() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        Map<String, Object> enrichedData = Map.of("companyId", "12345");
        enricher.setEnrichedData(enrichedData);
        enricher.setCost(0.05);
        enricher.setCostCurrency("USD");

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.getCost()).isEqualTo(0.05);
                    assertThat(response.getCostCurrency()).isEqualTo("USD");
                })
                .verifyComplete();

        // Verify cost is recorded in metrics
        verify(metricsService).recordEnrichmentMetrics(
                eq("company-profile"),
                eq("Test Provider"),
                eq(true),
                anyLong(),
                eq(1),
                eq(0.05)
        );
    }

    @Test
    void supportsEnrichmentType_shouldReturnTrue_whenTypeIsSupported() {
        assertThat(enricher.supportsEnrichmentType("company-profile")).isTrue();
        assertThat(enricher.supportsEnrichmentType("company-financials")).isTrue();
    }

    @Test
    void supportsEnrichmentType_shouldReturnFalse_whenTypeIsNotSupported() {
        assertThat(enricher.supportsEnrichmentType("unsupported-type")).isFalse();
    }

    @Test
    void isReady_shouldReturnTrue_byDefault() {
        StepVerifier.create(enricher.isReady())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void enrich_shouldHandleRawProviderResponse_whenProvided() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.RAW)
                .parameters(Map.of("companyId", "12345"))
                .build();

        Map<String, Object> rawResponse = Map.of(
                "provider_id", "ORB-12345",
                "provider_data", Map.of("raw", "data")
        );
        enricher.setEnrichedData(Map.of("companyId", "12345"));
        enricher.setRawProviderResponse(rawResponse);

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getRawProviderResponse()).isEqualTo(rawResponse);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldPropagateMetadata_whenProvided() {
        // Given
        Map<String, String> requestMetadata = Map.of("source", "api", "priority", "high");
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .metadata(requestMetadata)
                .build();

        enricher.setEnrichedData(Map.of("companyId", "12345", "name", "Test"));
        enricher.setMetadata(Map.of("cached", "false", "provider_version", "2.0"));

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.getMetadata()).isNotNull();
                    assertThat(response.getMetadata()).containsEntry("cached", "false");
                    assertThat(response.getMetadata()).containsEntry("provider_version", "2.0");
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldHandleDifferentStrategies() {
        // Test ENHANCE strategy
        EnrichmentRequest enhanceRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        enricher.setEnrichedData(Map.of("companyId", "12345", "name", "Test"));

        StepVerifier.create(enricher.enrich(enhanceRequest))
                .assertNext(response -> {
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.ENHANCE);
                })
                .verifyComplete();

        // Test MERGE strategy
        EnrichmentRequest mergeRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        StepVerifier.create(enricher.enrich(mergeRequest))
                .assertNext(response -> {
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.MERGE);
                })
                .verifyComplete();

        // Test REPLACE strategy
        EnrichmentRequest replaceRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.REPLACE)
                .parameters(Map.of("companyId", "12345"))
                .build();

        StepVerifier.create(enricher.enrich(replaceRequest))
                .assertNext(response -> {
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.REPLACE);
                })
                .verifyComplete();

        // Test RAW strategy
        EnrichmentRequest rawRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.RAW)
                .parameters(Map.of("companyId", "12345"))
                .build();

        StepVerifier.create(enricher.enrich(rawRequest))
                .assertNext(response -> {
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.RAW);
                })
                .verifyComplete();
    }

    @Test
    void getEnricherDescription_shouldReturnDescription() {
        assertThat(enricher.getEnricherDescription()).isNotEmpty();
    }

    /**
     * Test implementation of AbstractResilientDataEnricher.
     */
    private static class TestDataEnricher extends AbstractResilientDataEnricher {
        private Map<String, Object> enrichedData;
        private boolean shouldFail = false;
        private Double confidenceScore;
        private Double cost;
        private String costCurrency;
        private Map<String, Object> rawProviderResponse;
        private Map<String, String> metadata;

        public TestDataEnricher(JobTracingService tracingService,
                                JobMetricsService metricsService,
                                ResiliencyDecoratorService resiliencyService,
                                EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher);
        }

        @Override
        protected Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
            if (shouldFail) {
                return Mono.error(new RuntimeException("Enrichment failed"));
            }

            EnrichmentResponse.EnrichmentResponseBuilder builder = EnrichmentResponse.builder()
                    .success(true)
                    .enrichedData(enrichedData)
                    .providerName(getProviderName())
                    .enrichmentType(request.getEnrichmentType())
                    .strategy(request.getStrategy())
                    .message("Enrichment successful")
                    .fieldsEnriched(enrichedData != null ? enrichedData.size() : 0)
                    .requestId(request.getRequestId());

            if (confidenceScore != null) {
                builder.confidenceScore(confidenceScore);
            }
            if (cost != null) {
                builder.cost(cost);
            }
            if (costCurrency != null) {
                builder.costCurrency(costCurrency);
            }
            if (rawProviderResponse != null) {
                builder.rawProviderResponse(rawProviderResponse);
            }
            if (metadata != null) {
                builder.metadata(metadata);
            }

            return Mono.just(builder.build());
        }

        @Override
        public String getProviderName() {
            return "Test Provider";
        }

        @Override
        public String[] getSupportedEnrichmentTypes() {
            return new String[]{"company-profile", "company-financials"};
        }

        @Override
        public String getEnricherDescription() {
            return "Test enricher for unit testing";
        }

        public void setEnrichedData(Map<String, Object> enrichedData) {
            this.enrichedData = enrichedData;
        }

        public void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        public void setConfidenceScore(Double confidenceScore) {
            this.confidenceScore = confidenceScore;
        }

        public void setCost(Double cost) {
            this.cost = cost;
        }

        public void setCostCurrency(String costCurrency) {
            this.costCurrency = costCurrency;
        }

        public void setRawProviderResponse(Map<String, Object> rawProviderResponse) {
            this.rawProviderResponse = rawProviderResponse;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}

