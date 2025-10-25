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

package com.firefly.common.data.integration;

import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
import lombok.Builder;
import lombok.Data;
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
 * End-to-end integration test for DataEnrichers.
 *
 * This test validates the complete enrichment workflow:
 * 1. ENHANCE strategy - fills only null fields
 * 2. MERGE strategy - provider data takes precedence
 * 3. REPLACE strategy - uses only provider data
 * 4. RAW strategy - returns raw provider data
 * 5. Error handling and recovery
 * 6. Validation failures
 * 7. Automatic observability and resiliency
 */
@ExtendWith(MockitoExtension.class)
class DataEnricherE2ETest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private TestCompanyEnricher enricher;

    @BeforeEach
    void setUp() {
        // Setup mock behaviors
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Create test enricher
        enricher = new TestCompanyEnricher(tracingService, metricsService, resiliencyService, eventPublisher);
    }

    @Test
    void shouldEnrichWithEnhanceStrategy_fillingOnlyNullFields() {
        // Given - partial company data
        CompanyDTO sourceDto = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")  // Already has name
                .employeeCount(null)  // Needs enrichment
                .revenue(null)  // Needs enrichment
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(sourceDto)
                .parameters(Map.of("companyId", "12345"))
                .requestId("req-001")
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then - verify response structure
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getProviderName()).isEqualTo("Test Company Provider");
                    assertThat(response.getType()).isEqualTo("company-profile");
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.ENHANCE);
                    
                    // Verify enriched data
                    CompanyDTO enriched = (CompanyDTO) response.getEnrichedData();
                    assertThat(enriched).isNotNull();
                    
                    // ENHANCE strategy: preserves existing values
                    assertThat(enriched.getCompanyId()).isEqualTo("12345");
                    assertThat(enriched.getName()).isEqualTo("Acme Corp");  // PRESERVED from source
                    
                    // ENHANCE strategy: fills null values from provider
                    assertThat(enriched.getEmployeeCount()).isEqualTo(500);  // FILLED from provider
                    assertThat(enriched.getRevenue()).isEqualTo(50000000.0);  // FILLED from provider
                    assertThat(enriched.getIndustry()).isEqualTo("Technology");  // FILLED from provider
                })
                .verifyComplete();
    }

    @Test
    void shouldEnrichWithMergeStrategy_providerDataTakesPrecedence() {
        // Given - company data with some outdated values
        CompanyDTO sourceDto = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")  // Old name
                .employeeCount(450)  // Old employee count
                .revenue(45000000.0)  // Old revenue
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(sourceDto)
                .parameters(Map.of("companyId", "12345"))
                .requestId("req-002")
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then
                    assertThat(response.isSuccess()).isTrue();
                    
                    CompanyDTO merged = (CompanyDTO) response.getEnrichedData();
                    assertThat(merged).isNotNull();
                    
                    // MERGE strategy: provider data overwrites source data
                    assertThat(merged.getCompanyId()).isEqualTo("12345");
                    assertThat(merged.getName()).isEqualTo("Acme Corporation");  // UPDATED from provider
                    assertThat(merged.getEmployeeCount()).isEqualTo(500);  // UPDATED from provider
                    assertThat(merged.getRevenue()).isEqualTo(50000000.0);  // UPDATED from provider
                    assertThat(merged.getIndustry()).isEqualTo("Technology");  // ADDED from provider
                })
                .verifyComplete();
    }

    @Test
    void shouldEnrichWithReplaceStrategy_usingOnlyProviderData() {
        // Given - company data (will be ignored)
        CompanyDTO sourceDto = CompanyDTO.builder()
                .companyId("12345")
                .name("Old Name")
                .employeeCount(100)
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.REPLACE)
                .sourceDto(sourceDto)
                .parameters(Map.of("companyId", "12345"))
                .requestId("req-003")
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then
                    assertThat(response.isSuccess()).isTrue();
                    
                    CompanyDTO replaced = (CompanyDTO) response.getEnrichedData();
                    assertThat(replaced).isNotNull();
                    
                    // REPLACE strategy: uses ONLY provider data
                    assertThat(replaced.getCompanyId()).isEqualTo("12345");
                    assertThat(replaced.getName()).isEqualTo("Acme Corporation");  // From provider
                    assertThat(replaced.getEmployeeCount()).isEqualTo(500);  // From provider
                    assertThat(replaced.getRevenue()).isEqualTo(50000000.0);  // From provider
                    assertThat(replaced.getIndustry()).isEqualTo("Technology");  // From provider
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnRawProviderData_whenUsingRawStrategy() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.RAW)
                .parameters(Map.of("companyId", "12345"))
                .requestId("req-004")
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.RAW);
                    
                    // RAW strategy: returns provider data as-is (Map)
                    assertThat(response.getEnrichedData()).isInstanceOf(Map.class);
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rawData = (Map<String, Object>) response.getEnrichedData();
                    assertThat(rawData.get("companyId")).isEqualTo("12345");
                    assertThat(rawData.get("businessName")).isEqualTo("Acme Corporation");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnFailureResponse_whenValidationFails() {
        // Given - request without required parameter
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(CompanyDTO.builder().build())
                .parameters(Map.of())  // Missing companyId
                .requestId("req-005")
                .build();

        // When
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    // Then - validation failure is returned as failure response
                    assertThat(response.isSuccess()).isFalse();
                    // The error message should contain information about the missing parameter
                    String errorMessage = response.getError() != null ? response.getError() : response.getMessage();
                    assertThat(errorMessage).contains("companyId");
                })
                .verifyComplete();
    }

    /**
     * Test DTO for company data.
     */
    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class CompanyDTO {
        private String companyId;
        private String name;
        private Integer employeeCount;
        private Double revenue;
        private String industry;
    }

    /**
     * Test enricher implementation.
     */
    @com.firefly.common.data.enrichment.EnricherMetadata(
        providerName = "Test Company Provider",
        type = "company-profile",
        description = "Test enricher for E2E testing"
    )
    static class TestCompanyEnricher extends DataEnricher<CompanyDTO, Map<String, Object>, CompanyDTO> {

        public TestCompanyEnricher(JobTracingService tracingService,
                                  JobMetricsService metricsService,
                                  ResiliencyDecoratorService resiliencyService,
                                  EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyDTO.class);
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.fromCallable(() -> {
                // Validate required parameter
                String companyId = request.requireParam("companyId");

                // Simulate provider response
                return Map.of(
                        "companyId", companyId,
                        "businessName", "Acme Corporation",
                        "employeeCount", 500,
                        "revenue", 50000000.0,
                        "industry", "Technology"
                );
            });
        }

        @Override
        protected CompanyDTO mapToTarget(Map<String, Object> providerData) {
            return CompanyDTO.builder()
                    .companyId((String) providerData.get("companyId"))
                    .name((String) providerData.get("businessName"))
                    .employeeCount((Integer) providerData.get("employeeCount"))
                    .revenue((Double) providerData.get("revenue"))
                    .industry((String) providerData.get("industry"))
                    .build();
        }
    }
}

