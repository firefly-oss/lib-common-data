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

package com.firefly.common.data.demo;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.operation.AbstractEnricherOperation;
import com.firefly.common.data.operation.EnricherOperation;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * COMPLETE DEVELOPER EXPERIENCE DEMONSTRATION
 * 
 * This test demonstrates the ENTIRE developer experience for creating data enrichers.
 * It shows how simple and straightforward it is to:
 * 
 * 1. Create a data enricher (only 2 methods to implement!)
 * 2. Add custom operations (only 2 methods to implement!)
 * 3. Use all enrichment strategies (ENHANCE, MERGE, REPLACE, RAW)
 * 4. Get automatic observability, resiliency, caching, and more
 * 
 * This is the COMPLETE API that developers need to learn.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("✨ Complete Developer Experience Demo - Data Enrichers")
class CompleteDeveloperExperienceDemoTest {

    @Mock private JobTracingService tracingService;
    @Mock private JobMetricsService metricsService;
    @Mock private ResiliencyDecoratorService resiliencyService;
    @Mock private EnrichmentEventPublisher eventPublisher;

    private SimpleCreditBureauEnricher enricher;
    private CompanyLookupOperation lookupOperation;

    @BeforeEach
    void setUp() {
        // Setup mocks (this is test infrastructure, not what developers write)
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        enricher = new SimpleCreditBureauEnricher(tracingService, metricsService, resiliencyService, eventPublisher);

        lookupOperation = new CompanyLookupOperation();
        lookupOperation.initializeMetadata(); // Initialize metadata from annotation
    }

    // ========================================
    // PART 1: DATA ENRICHER - BASIC USAGE
    // ========================================

    @Test
    @DisplayName("1️⃣ Basic Enrichment - ENHANCE strategy (fill missing fields)")
    void demo_basicEnrichment_enhanceStrategy() {
        // Given - partial company data from user
        CompanyDTO partialData = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")  // User provided this
                // Missing: creditScore, riskLevel, address
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("credit-report")
                .strategy(EnrichmentStrategy.ENHANCE)  // Only fill missing fields
                .sourceDto(partialData)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When - enrich the data
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - missing fields are filled, existing fields preserved
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    
                    // Original data preserved
                    assertThat(enriched.getName()).isEqualTo("Acme Corp");
                    
                    // Missing fields filled from provider
                    assertThat(enriched.getCreditScore()).isEqualTo(750);
                    assertThat(enriched.getRiskLevel()).isEqualTo("LOW");
                    assertThat(enriched.getAddress()).isEqualTo("123 Provider St");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("2️⃣ MERGE Strategy - Update with fresh provider data")
    void demo_mergeStrategy_updateWithProviderData() {
        // Given - stale company data
        CompanyDTO staleData = CompanyDTO.builder()
                .companyId("12345")
                .name("Old Name")  // Outdated
                .creditScore(600)  // Outdated
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("credit-report")
                .strategy(EnrichmentStrategy.MERGE)  // Provider data wins
                .sourceDto(staleData)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - provider data overwrites stale data
        StepVerifier.create(result)
                .assertNext(response -> {
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getName()).isEqualTo("Acme Corporation");  // Updated!
                    assertThat(enriched.getCreditScore()).isEqualTo(750);  // Updated!
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("3️⃣ REPLACE Strategy - Use only provider data")
    void demo_replaceStrategy_useOnlyProviderData() {
        // Given - just an ID, fetch everything from provider
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("credit-report")
                .strategy(EnrichmentStrategy.REPLACE)  // Ignore source, use provider
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - all data from provider
        StepVerifier.create(result)
                .assertNext(response -> {
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Acme Corporation");
                    assertThat(enriched.getCreditScore()).isEqualTo(750);
                    assertThat(enriched.getRiskLevel()).isEqualTo("LOW");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("4️⃣ RAW Strategy - Get unprocessed provider response")
    void demo_rawStrategy_getProviderResponseAsIs() {
        // Given - need raw provider data for custom processing
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("credit-report")
                .strategy(EnrichmentStrategy.RAW)  // No mapping, return raw
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then - raw provider response
        StepVerifier.create(result)
                .assertNext(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> raw = (Map<String, Object>) response.getEnrichedData();
                    assertThat(raw.get("company_id")).isEqualTo("12345");
                    assertThat(raw.get("credit_score")).isEqualTo(750);
                })
                .verifyComplete();
    }

    // ========================================
    // PART 2: CUSTOM OPERATIONS
    // ========================================

    @Test
    @DisplayName("5️⃣ Custom Operation - Company lookup by name")
    void demo_customOperation_companyLookup() {
        // Given - need to find company ID by name
        LookupRequest request = LookupRequest.builder()
                .companyName("Acme Corp")
                .build();

        // When - execute custom operation
        Mono<LookupResponse> result = lookupOperation.execute(request);

        // Then - get provider's internal ID
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getProviderId()).isEqualTo("PROV-12345");
                    assertThat(response.getCompanyName()).isEqualTo("ACME CORP");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("6️⃣ Metadata - Automatic from annotations")
    void demo_metadata_automaticFromAnnotations() {
        // All metadata is read automatically from @EnricherMetadata
        assertThat(enricher.getProviderName()).isEqualTo("Simple Credit Bureau");
        assertThat(enricher.getSupportedEnrichmentTypes()).containsExactly("credit-report");
        assertThat(enricher.getPriority()).isEqualTo(100);
        assertThat(enricher.getTags()).containsExactly("demo", "simple");
        
        // Operations metadata also automatic
        assertThat(lookupOperation.getMetadata().getOperationId()).isEqualTo("lookup-company");
        assertThat(lookupOperation.getMetadata().getMethod()).isEqualTo(RequestMethod.GET);
    }

    // ========================================
    // IMPLEMENTATION EXAMPLES
    // ========================================

    /**
     * EXAMPLE 1: Simple Data Enricher
     * 
     * This is ALL the code a developer needs to write!
     * Just 2 methods: fetchProviderData() and mapToTarget()
     * 
     * Everything else is automatic:
     * - Observability (tracing, metrics, logging)
     * - Resiliency (circuit breaker, retry, rate limiting)
     * - Caching (with tenant isolation)
     * - Strategy application (ENHANCE, MERGE, REPLACE, RAW)
     * - Event publishing
     * - Health checks
     */
    @EnricherMetadata(
        providerName = "Simple Credit Bureau",
        type = "credit-report",
        description = "Demo enricher showing how simple it is",
        tags = {"demo", "simple"},
        priority = 100
    )
    static class SimpleCreditBureauEnricher 
            extends DataEnricher<CompanyDTO, Map<String, Object>, CompanyDTO> {

        public SimpleCreditBureauEnricher(JobTracingService tracingService,
                                         JobMetricsService metricsService,
                                         ResiliencyDecoratorService resiliencyService,
                                         EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyDTO.class);
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            // Step 1: Get parameters (with automatic validation)
            String companyId = request.requireParam("companyId");
            
            // Step 2: Call provider API (in real code, use RestClient from lib-common-client)
            // return restClient.get("/credit-report/{id}", Map.class)
            //     .withPathParam("id", companyId)
            //     .execute();
            
            // For demo, return simulated provider response
            return Mono.just(Map.of(
                "company_id", companyId,
                "company_name", "Acme Corporation",
                "credit_score", 750,
                "risk_level", "LOW",
                "address", "123 Provider St"
            ));
        }

        @Override
        protected CompanyDTO mapToTarget(Map<String, Object> providerData) {
            // Step 3: Map provider response to your DTO
            // Strategy is applied AUTOMATICALLY after this!
            return CompanyDTO.builder()
                    .companyId((String) providerData.get("company_id"))
                    .name((String) providerData.get("company_name"))
                    .creditScore((Integer) providerData.get("credit_score"))
                    .riskLevel((String) providerData.get("risk_level"))
                    .address((String) providerData.get("address"))
                    .build();
        }
        
        // That's it! No need to implement:
        // - getProviderName() - read from @EnricherMetadata
        // - getSupportedEnrichmentTypes() - read from @EnricherMetadata
        // - getPriority() - read from @EnricherMetadata
        // - getTags() - read from @EnricherMetadata
        // - enrich() - provided by DataEnricher with full observability/resiliency
        // - enrichBatch() - provided by DataEnricher
        // - isReady() - provided by DataEnricher
    }

    /**
     * EXAMPLE 2: Custom Operation
     * 
     * Also very simple! Just 2 methods: doExecute() and validateRequest()
     */
    @EnricherOperation(
        operationId = "lookup-company",
        description = "Find company by name to get provider ID",
        method = RequestMethod.GET,
        tags = {"lookup"}
    )
    static class CompanyLookupOperation 
            extends AbstractEnricherOperation<LookupRequest, LookupResponse> {

        @Override
        protected Mono<LookupResponse> doExecute(LookupRequest request) {
            // Call provider API to search
            return Mono.just(LookupResponse.builder()
                    .providerId("PROV-12345")
                    .companyName(request.getCompanyName().toUpperCase())
                    .build());
        }

        @Override
        protected void validateRequest(LookupRequest request) {
            if (request.getCompanyName() == null || request.getCompanyName().isEmpty()) {
                throw new IllegalArgumentException("companyName is required");
            }
        }
    }

    // DTOs
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    static class CompanyDTO {
        private String companyId;
        private String name;
        private Integer creditScore;
        private String riskLevel;
        private String address;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    static class LookupRequest {
        private String companyName;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    static class LookupResponse {
        private String providerId;
        private String companyName;
    }
}

