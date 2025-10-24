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

package com.firefly.common.data.controller;

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EnrichmentDiscoveryController.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentDiscoveryControllerTest {

    @Mock
    private DataEnricherRegistry registry;

    @Mock
    private DataEnricher equifaxSpainEnricher;

    @Mock
    private DataEnricher equifaxUSAEnricher;

    @Mock
    private DataEnricher experianSpainEnricher;

    private EnrichmentDiscoveryController controller;

    @BeforeEach
    void setUp() {
        controller = new EnrichmentDiscoveryController(registry);

        // Setup Equifax Spain enricher
        lenient().when(equifaxSpainEnricher.getProviderName()).thenReturn("Equifax Spain");
        lenient().when(equifaxSpainEnricher.getSupportedEnrichmentTypes())
                .thenReturn(new String[]{"credit-report", "company-profile"});
        lenient().when(equifaxSpainEnricher.getEnricherDescription())
                .thenReturn("Equifax Spain data enrichment services");
        lenient().when(equifaxSpainEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/equifax-spain-credit/enrich");
        lenient().when(equifaxSpainEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);
        lenient().when(equifaxSpainEnricher.supportsEnrichmentType("company-profile")).thenReturn(true);

        // Setup Equifax USA enricher
        lenient().when(equifaxUSAEnricher.getProviderName()).thenReturn("Equifax USA");
        lenient().when(equifaxUSAEnricher.getSupportedEnrichmentTypes())
                .thenReturn(new String[]{"credit-report", "business-credit"});
        lenient().when(equifaxUSAEnricher.getEnricherDescription())
                .thenReturn("Equifax USA data enrichment services");
        lenient().when(equifaxUSAEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/equifax-usa-credit/enrich");
        lenient().when(equifaxUSAEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);
        lenient().when(equifaxUSAEnricher.supportsEnrichmentType("business-credit")).thenReturn(true);

        // Setup Experian Spain enricher
        lenient().when(experianSpainEnricher.getProviderName()).thenReturn("Experian Spain");
        lenient().when(experianSpainEnricher.getSupportedEnrichmentTypes())
                .thenReturn(new String[]{"credit-report"});
        lenient().when(experianSpainEnricher.getEnricherDescription())
                .thenReturn("Experian Spain credit reporting services");
        lenient().when(experianSpainEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/experian-spain-credit/enrich");
        lenient().when(experianSpainEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);
    }

    @Test
    void listProviders_shouldReturnAllProviders_whenNoFilterSpecified() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainEnricher,
                equifaxUSAEnricher,
                experianSpainEnricher
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(3);

                    // Verify Equifax Spain
                    var equifaxSpain = providers.stream()
                            .filter(p -> p.providerName().equals("Equifax Spain"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(equifaxSpain.supportedTypes())
                            .containsExactlyInAnyOrder("company-profile", "credit-report");
                    assertThat(equifaxSpain.description())
                            .isEqualTo("Equifax Spain data enrichment services");
                    assertThat(equifaxSpain.endpoints())
                            .containsExactly("/api/v1/enrichment/equifax-spain-credit/enrich");

                    // Verify Equifax USA
                    var equifaxUSA = providers.stream()
                            .filter(p -> p.providerName().equals("Equifax USA"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(equifaxUSA.supportedTypes())
                            .containsExactlyInAnyOrder("business-credit", "credit-report");
                    assertThat(equifaxUSA.endpoints())
                            .containsExactly("/api/v1/enrichment/equifax-usa-credit/enrich");

                    // Verify Experian Spain
                    var experianSpain = providers.stream()
                            .filter(p -> p.providerName().equals("Experian Spain"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(experianSpain.supportedTypes())
                            .containsExactly("credit-report");
                    assertThat(experianSpain.endpoints())
                            .containsExactly("/api/v1/enrichment/experian-spain-credit/enrich");
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldFilterByEnrichmentType_whenTypeSpecified() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainEnricher,
                equifaxUSAEnricher,
                experianSpainEnricher
        ));

        // When & Then - filter by credit-report
        StepVerifier.create(controller.listProviders("credit-report"))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(3);
                    assertThat(providers).extracting("providerName")
                            .containsExactlyInAnyOrder("Equifax Spain", "Equifax USA", "Experian Spain");
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldFilterByEnrichmentType_whenOnlySomeProvidersSupport() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainEnricher,
                equifaxUSAEnricher,
                experianSpainEnricher
        ));

        // When & Then - filter by company-profile (only Equifax Spain supports this)
        StepVerifier.create(controller.listProviders("company-profile"))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(1);
                    assertThat(providers.get(0).providerName()).isEqualTo("Equifax Spain");
                    assertThat(providers.get(0).supportedTypes())
                            .containsExactlyInAnyOrder("company-profile", "credit-report");
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldReturnEmptyList_whenNoProvidersMatchFilter() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainEnricher,
                equifaxUSAEnricher
        ));

        // When & Then - filter by non-existent type
        StepVerifier.create(controller.listProviders("non-existent-type"))
                .assertNext(providers -> {
                    assertThat(providers).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldReturnEmptyList_whenNoEnrichersRegistered() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of());

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    assertThat(providers).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldGroupByProviderName_whenMultipleEnrichersForSameProvider() {
        // Given - Two enrichers for Equifax Spain with different supported types and endpoints
        DataEnricher equifaxSpainCredit = equifaxSpainEnricher;

        DataEnricher equifaxSpainCompany = org.mockito.Mockito.mock(DataEnricher.class);
        lenient().when(equifaxSpainCompany.getProviderName()).thenReturn("Equifax Spain");
        lenient().when(equifaxSpainCompany.getSupportedEnrichmentTypes())
                .thenReturn(new String[]{"company-profile"});
        lenient().when(equifaxSpainCompany.getEnricherDescription())
                .thenReturn("Equifax Spain data enrichment services");
        lenient().when(equifaxSpainCompany.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/equifax-spain-company/enrich");
        lenient().when(equifaxSpainCompany.supportsEnrichmentType("company-profile")).thenReturn(true);

        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainCredit,
                equifaxSpainCompany
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    // Should have only 1 provider (Equifax Spain) with combined types and endpoints
                    assertThat(providers).hasSize(1);
                    assertThat(providers.get(0).providerName()).isEqualTo("Equifax Spain");
                    assertThat(providers.get(0).supportedTypes())
                            .containsExactlyInAnyOrder("company-profile", "credit-report");
                    assertThat(providers.get(0).endpoints())
                            .containsExactlyInAnyOrder(
                                    "/api/v1/enrichment/equifax-spain-company/enrich",
                                    "/api/v1/enrichment/equifax-spain-credit/enrich"
                            );
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldHandleEmptyEnrichmentTypeFilter() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                equifaxSpainEnricher,
                equifaxUSAEnricher
        ));

        // When & Then - empty string should be treated as no filter
        StepVerifier.create(controller.listProviders(""))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldSortProvidersByName() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                experianSpainEnricher,  // Experian comes after Equifax alphabetically
                equifaxUSAEnricher,
                equifaxSpainEnricher
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(3);
                    // Should be sorted alphabetically
                    assertThat(providers.get(0).providerName()).isEqualTo("Equifax Spain");
                    assertThat(providers.get(1).providerName()).isEqualTo("Equifax USA");
                    assertThat(providers.get(2).providerName()).isEqualTo("Experian Spain");
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldIncludeOperations_whenEnricherImplementsProviderOperationCatalog() {
        // Given - Create a mock enricher that implements ProviderOperationCatalog
        DataEnricher enricherWithOps = new TestEnricherWithOperations();

        when(registry.getAllEnrichers()).thenReturn(List.of(enricherWithOps));

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(1);

                    EnrichmentDiscoveryController.ProviderInfo provider = providers.get(0);
                    assertThat(provider.providerName()).isEqualTo("Test Provider");
                    assertThat(provider.operations()).hasSize(2);

                    // Verify first operation
                    Map<String, Object> op1 = provider.operations().get(0);
                    assertThat(op1.get("operationId")).isEqualTo("search-company");
                    assertThat(op1.get("path")).isEqualTo("/api/v1/test/search-company");
                    assertThat(op1.get("method")).isEqualTo("GET");
                    assertThat(op1.get("description")).isEqualTo("Search for a company");

                    // Verify second operation
                    Map<String, Object> op2 = provider.operations().get(1);
                    assertThat(op2.get("operationId")).isEqualTo("validate-id");
                    assertThat(op2.get("method")).isEqualTo("POST");
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldReturnNullOperations_whenEnricherDoesNotImplementCatalog() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(equifaxSpainEnricher));

        // When & Then
        StepVerifier.create(controller.listProviders(null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(1);
                    // Operations should be null when enricher doesn't implement ProviderOperationCatalog
                    assertThat(providers.get(0).operations()).isNull();
                })
                .verifyComplete();
    }

    // Test enricher that implements ProviderOperationCatalog
    private static class TestEnricherWithOperations implements DataEnricher, ProviderOperationCatalog, EndpointAware {
        private String endpoint = "/api/v1/test/enrich";

        @Override
        public String getProviderName() {
            return "Test Provider";
        }

        @Override
        public String[] getSupportedEnrichmentTypes() {
            return new String[]{"test-type"};
        }

        @Override
        public String getEnricherDescription() {
            return "Test provider with operations";
        }

        @Override
        public String getEnrichmentEndpoint() {
            return endpoint;
        }

        @Override
        public void setEnrichmentEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
            return Mono.just(EnrichmentResponse.builder()
                .success(true)
                .providerName(getProviderName())
                .enrichmentType(request.getEnrichmentType())
                .strategy(request.getStrategy())
                .enrichedData(Map.of("test", "data"))
                .build());
        }

        @Override
        public List<ProviderOperation> getOperationCatalog() {
            return List.of(
                ProviderOperation.builder()
                    .operationId("search-company")
                    .path("/search-company")
                    .method(RequestMethod.GET)
                    .description("Search for a company")
                    .build(),
                ProviderOperation.builder()
                    .operationId("validate-id")
                    .path("/validate-id")
                    .method(RequestMethod.POST)
                    .description("Validate an ID")
                    .build()
            );
        }
    }
}

