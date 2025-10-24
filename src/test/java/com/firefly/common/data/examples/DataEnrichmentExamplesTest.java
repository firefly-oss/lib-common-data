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

package com.firefly.common.data.examples;

import com.firefly.common.data.enrichment.EnrichmentRequestValidator;
import com.firefly.common.data.enrichment.EnrichmentResponseBuilder;
import com.firefly.common.data.enrichment.EnrichmentStrategyApplier;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.examples.dto.CompanyProfileDTO;
import com.firefly.common.data.examples.enricher.FinancialDataEnricher;
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
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Test class that validates all code examples from the data enrichment documentation.
 * This ensures that the code examples in the docs are accurate and functional.
 * 
 * <p>Each test corresponds to a specific example in docs/data-enrichment.md</p>
 */
@ExtendWith(MockitoExtension.class)
class DataEnrichmentExamplesTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private FinancialDataEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new FinancialDataEnricher(
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

    /**
     * Example from: Quick Start - Step 3: Implement a Data Enricher
     */
    @Test
    void example_TypedDataEnricher_BasicUsage() {
        // Given
        CompanyProfileDTO source = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getProviderName()).isEqualTo("Financial Data Provider");
                    assertThat(response.getEnrichmentType()).isEqualTo("company-profile");
                    
                    CompanyProfileDTO enriched = (CompanyProfileDTO) response.getEnrichedData();
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Acme Corp"); // Preserved from source
                    assertThat(enriched.getRegisteredAddress()).isNotNull(); // Filled from provider
                    assertThat(enriched.getIndustry()).isNotNull(); // Filled from provider
                })
                .verifyComplete();
    }

    /**
     * Example from: Enricher Utilities - EnrichmentRequest Helper Methods
     */
    @Test
    void example_EnrichmentRequest_HelperMethods() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .parameters(Map.of(
                        "companyId", "12345",
                        "includeFinancials", true,
                        "maxResults", 10
                ))
                .build();

        // When & Then - Get required parameter (throws if missing)
        String companyId = request.requireParam("companyId");
        assertThat(companyId).isEqualTo("12345");

        // Get optional parameter with default
        Boolean includeFinancials = request.param("includeFinancials", false);
        assertThat(includeFinancials).isTrue();

        // Type-safe parameter extraction
        Integer maxResults = request.paramAsInt("maxResults");
        assertThat(maxResults).isEqualTo(10);

        // Check parameter existence
        assertThat(request.hasParam("companyId")).isTrue();
        assertThat(request.hasParam("nonExistent")).isFalse();
    }

    /**
     * Example from: Enricher Utilities - EnrichmentRequestValidator
     */
    @Test
    void example_EnrichmentRequestValidator_FluentValidation() {
        // Given - valid request
        EnrichmentRequest validRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(CompanyProfileDTO.builder().companyId("12345").build())
                .parameters(Map.of("companyId", "12345"))
                .tenantId("tenant-001")
                .build();

        // When & Then - validation passes
        EnrichmentRequestValidator.of(validRequest)
                .requireParam("companyId")
                .requireSourceDto()
                .requireStrategy(EnrichmentStrategy.ENHANCE, EnrichmentStrategy.MERGE)
                .requireTenantId()
                .validate();

        // Given - invalid request (missing parameter)
        EnrichmentRequest invalidRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .parameters(Map.of())
                .build();

        // When & Then - validation fails
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(invalidRequest)
                        .requireParam("companyId")
                        .validate()
        ).isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("companyId");
    }

    /**
     * Example from: Enricher Utilities - EnrichmentStrategyApplier
     */
    @Test
    void example_EnrichmentStrategyApplier_AutomaticStrategyApplication() {
        // Given
        CompanyProfileDTO source = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .registeredAddress(null)
                .build();

        CompanyProfileDTO provider = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Different Name")
                .registeredAddress("123 Main St")
                .annualRevenue(1000000.0)
                .build();

        // When - ENHANCE strategy (fill only null fields)
        CompanyProfileDTO enhanced = EnrichmentStrategyApplier.enhance(
                source, provider, CompanyProfileDTO.class);

        // Then
        assertThat(enhanced.getName()).isEqualTo("Acme Corp"); // Preserved from source
        assertThat(enhanced.getRegisteredAddress()).isEqualTo("123 Main St"); // Filled from provider
        assertThat(enhanced.getAnnualRevenue()).isEqualTo(1000000.0); // Filled from provider

        // When - MERGE strategy (provider wins conflicts)
        CompanyProfileDTO merged = EnrichmentStrategyApplier.merge(
                source, provider, CompanyProfileDTO.class);

        // Then
        assertThat(merged.getName()).isEqualTo("Different Name"); // Overwritten by provider
        assertThat(merged.getRegisteredAddress()).isEqualTo("123 Main St");

        // When - REPLACE strategy (use provider data entirely)
        CompanyProfileDTO replaced = EnrichmentStrategyApplier.replace(
                provider, CompanyProfileDTO.class);

        // Then
        assertThat(replaced.getName()).isEqualTo("Different Name");
        assertThat(replaced.getRegisteredAddress()).isEqualTo("123 Main St");
        assertThat(replaced.getAnnualRevenue()).isEqualTo(1000000.0);
    }

    /**
     * Example from: Enricher Utilities - EnrichmentResponseBuilder
     */
    @Test
    void example_EnrichmentResponseBuilder_FluentResponseBuilding() {
        // Given
        CompanyProfileDTO enrichedData = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .registeredAddress("123 Main St")
                .build();

        CompanyProfileDTO sourceData = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(sourceData)
                .requestId("req-001")
                .build();

        // When - Success response
        EnrichmentResponse response = EnrichmentResponseBuilder
                .success(enrichedData)
                .forRequest(request)
                .withProvider("Financial Data Provider")
                .withMessage("Company data enriched successfully")
                .countingEnrichedFields(sourceData)
                .withCost(0.50, "USD")
                .withConfidence(0.95)
                .withMetadata("provider_id", "FDP-12345")
                .build();

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEnrichedData()).isEqualTo(enrichedData);
        assertThat(response.getProviderName()).isEqualTo("Financial Data Provider");
        assertThat(response.getMessage()).isEqualTo("Company data enriched successfully");
        assertThat(response.getFieldsEnriched()).isEqualTo(1); // Only address was enriched
        assertThat(response.getCost()).isEqualTo(0.50);
        assertThat(response.getCostCurrency()).isEqualTo("USD");
        assertThat(response.getConfidenceScore()).isEqualTo(0.95);
        assertThat(response.getMetadata()).containsEntry("provider_id", "FDP-12345");

        // When - Failure response
        EnrichmentResponse failureResponse = EnrichmentResponseBuilder
                .failure("Provider returned 404: Company not found")
                .forRequest(request)
                .withProvider("Financial Data Provider")
                .build();

        // Then
        assertThat(failureResponse.isSuccess()).isFalse();
        assertThat(failureResponse.getError()).isEqualTo("Provider returned 404: Company not found");
        assertThat(failureResponse.getProviderName()).isEqualTo("Financial Data Provider");
    }

    /**
     * Example from: Best Practices - Use Appropriate Enrichment Strategy
     */
    @Test
    void example_BestPractices_EnrichmentStrategies() {
        // Given
        CompanyProfileDTO source = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        EnrichmentRequest enhanceRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE) // Preserve existing data
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When - ENHANCE strategy
        StepVerifier.create(enricher.enrich(enhanceRequest))
                .assertNext(response -> {
                    CompanyProfileDTO enriched = (CompanyProfileDTO) response.getEnrichedData();
                    assertThat(enriched.getName()).isEqualTo("Acme Corp"); // Preserved
                })
                .verifyComplete();

        // Given - MERGE strategy (provider data is more authoritative)
        EnrichmentRequest mergeRequest = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When - MERGE strategy
        StepVerifier.create(enricher.enrich(mergeRequest))
                .assertNext(response -> {
                    CompanyProfileDTO merged = (CompanyProfileDTO) response.getEnrichedData();
                    assertThat(merged.getName()).isEqualTo("Acme Corporation"); // Overwritten by provider
                })
                .verifyComplete();
    }
}

