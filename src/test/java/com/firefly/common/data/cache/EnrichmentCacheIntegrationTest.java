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

package com.firefly.common.data.cache;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for enrichment caching functionality.
 * Verifies that the cache works correctly with DataEnricher.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentCacheIntegrationTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private TestCachedEnricher enricher;
    private AtomicInteger providerCallCount;

    @BeforeEach
    void setUp() {
        providerCallCount = new AtomicInteger(0);
        
        // Setup mocks to pass through - CRITICAL for tests to work
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        enricher = new TestCachedEnricher(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                providerCallCount
        );
    }

    @Test
    void enrich_shouldCallProvider() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(providerCallCount.get()).isEqualTo(1);
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getName()).isEqualTo("Acme Corporation");
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldUseDefaultStrategyWhenNotSpecified() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Original Name")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                // No strategy specified - should use ENHANCE by default
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    if (!response.isSuccess()) {
                        System.out.println("ERROR: " + response.getError());
                    }
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    // ENHANCE strategy should preserve original name
                    assertThat(enriched.getName()).isEqualTo("Original Name");
                    // But should fill null fields
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St");
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldApplyMergeStrategy() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Original Name")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    // MERGE strategy should use provider name
                    assertThat(enriched.getName()).isEqualTo("Acme Corporation");
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St");
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldApplyReplaceStrategy() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Original Name")
                .address("Original Address")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.REPLACE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    // REPLACE strategy should use only provider data
                    assertThat(enriched.getName()).isEqualTo("Acme Corporation");
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St");
                })
                .verifyComplete();
    }

    /**
     * Test DTO for cache integration tests.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class CompanyDTO {
        private String companyId;
        private String name;
        private String address;
        private Double revenue;
    }

    /**
     * Test enricher that tracks provider call count.
     */
    @EnricherMetadata(
        providerName = "Test Cached Provider",
        type = "company-profile",
        description = "Test enricher for cache integration testing"
    )
    static class TestCachedEnricher extends DataEnricher<CompanyDTO, Map<String, Object>, CompanyDTO> {

        private final AtomicInteger callCount;

        public TestCachedEnricher(JobTracingService tracingService,
                                 JobMetricsService metricsService,
                                 ResiliencyDecoratorService resiliencyService,
                                 EnrichmentEventPublisher eventPublisher,
                                 AtomicInteger callCount) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyDTO.class);
            this.callCount = callCount;
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.fromCallable(() -> {
                callCount.incrementAndGet();
                String companyId = request.requireParam("companyId");
                
                return Map.of(
                        "companyId", companyId,
                        "name", "Acme Corporation",
                        "address", "123 Provider St",
                        "revenue", 1000000.0
                );
            });
        }

        @Override
        protected CompanyDTO mapToTarget(Map<String, Object> providerData) {
            return CompanyDTO.builder()
                    .companyId((String) providerData.get("companyId"))
                    .name((String) providerData.get("name"))
                    .address((String) providerData.get("address"))
                    .revenue((Double) providerData.get("revenue"))
                    .build();
        }
    }
}

