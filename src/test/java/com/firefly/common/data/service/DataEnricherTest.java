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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Tests for DataEnricher demonstrating the improved developer experience.
 */
@ExtendWith(MockitoExtension.class)
class DataEnricherTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private TestTypedEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new TestTypedEnricher(
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
    void enrich_shouldAutomaticallyApplyEnhanceStrategy() {
        // Given - source DTO with partial data
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - should enhance with provider data (only fill null fields)
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Acme Corp"); // Original value preserved
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St"); // Filled from provider
                    assertThat(enriched.getRevenue()).isEqualTo(1000000.0); // Filled from provider
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldAutomaticallyApplyMergeStrategy() {
        // Given - source DTO with partial data
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .address("Old Address")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - should merge with provider data (provider wins conflicts)
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Provider Company"); // Overwritten by provider
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St"); // Overwritten by provider
                    assertThat(enriched.getRevenue()).isEqualTo(1000000.0);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldAutomaticallyApplyReplaceStrategy() {
        // Given - source DTO with data
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .address("Old Address")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.REPLACE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - should replace entirely with provider data
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Provider Company");
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St");
                    assertThat(enriched.getRevenue()).isEqualTo(1000000.0);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldAutomaticallyCountEnrichedFields() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - should automatically count enriched fields
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getFieldsEnriched()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldHandleValidationErrors() {
        // Given - request without required parameter
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of())
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - should handle error and return failure response
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getError())
                            .isNotNull()
                            .contains("companyId");
                })
                .verifyComplete();
    }

    // Test DTOs
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class ProviderResponse {
        private String id;
        private String companyName;
        private String registeredAddress;
        private Double annualRevenue;
    }

    // Test implementation of DataEnricher
    @com.firefly.common.data.enrichment.EnricherMetadata(
        providerName = "Test Provider",
        type = "company-profile",
        description = "Test enricher for unit testing"
    )
    static class TestTypedEnricher extends DataEnricher<CompanyDTO, ProviderResponse, CompanyDTO> {

        public TestTypedEnricher(
                JobTracingService tracingService,
                JobMetricsService metricsService,
                ResiliencyDecoratorService resiliencyService,
                EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyDTO.class);
        }

        @Override
        protected Mono<ProviderResponse> fetchProviderData(EnrichmentRequest request) {
            // Wrap validation in Mono to allow error handling
            return Mono.fromCallable(() -> {
                // Automatic validation with fluent API
                String companyId = request.requireParam("companyId");
                return companyId;
            })
            .flatMap(companyId ->
                // Simulate fetching from provider
                Mono.just(ProviderResponse.builder()
                        .id(companyId)
                        .companyName("Provider Company")
                        .registeredAddress("123 Provider St")
                        .annualRevenue(1000000.0)
                        .build())
            );
        }

        @Override
        protected CompanyDTO mapToTarget(ProviderResponse providerData) {
            // Simple mapping - strategy is applied automatically!
            return CompanyDTO.builder()
                    .companyId(providerData.getId())
                    .name(providerData.getCompanyName())
                    .address(providerData.getRegisteredAddress())
                    .revenue(providerData.getAnnualRevenue())
                    .build();
        }
    }
}

