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

import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.util.List;

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
    private DataEnricher<?, ?, ?> providerASpainCreditEnricher;

    @Mock
    private DataEnricher<?, ?, ?> providerASpainCompanyEnricher;

    @Mock
    private DataEnricher<?, ?, ?> providerAUSACreditEnricher;

    @Mock
    private DataEnricher<?, ?, ?> providerBSpainCreditEnricher;

    private EnrichmentDiscoveryController controller;

    private static final java.util.UUID SPAIN_TENANT_ID = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final java.util.UUID USA_TENANT_ID = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    @BeforeEach
    void setUp() {
        controller = new EnrichmentDiscoveryController(registry);

        // Setup Provider A Spain - Credit Report enricher
        lenient().when(providerASpainCreditEnricher.getProviderName()).thenReturn("Provider A Spain");
        lenient().when(providerASpainCreditEnricher.getSupportedEnrichmentTypes())
                .thenReturn(List.of("credit-report"));
        lenient().when(providerASpainCreditEnricher.getEnricherDescription())
                .thenReturn("Provider A Spain credit report enrichment");
        lenient().when(providerASpainCreditEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/provider-a-spain-credit/enrich");
        lenient().when(providerASpainCreditEnricher.getTenantId()).thenReturn(SPAIN_TENANT_ID);
        lenient().when(providerASpainCreditEnricher.getPriority()).thenReturn(100);
        lenient().when(providerASpainCreditEnricher.getTags()).thenReturn(List.of("credit", "spain"));
        lenient().when(providerASpainCreditEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);

        // Setup Provider A Spain - Company Profile enricher
        lenient().when(providerASpainCompanyEnricher.getProviderName()).thenReturn("Provider A Spain");
        lenient().when(providerASpainCompanyEnricher.getSupportedEnrichmentTypes())
                .thenReturn(List.of("company-profile"));
        lenient().when(providerASpainCompanyEnricher.getEnricherDescription())
                .thenReturn("Provider A Spain company profile enrichment");
        lenient().when(providerASpainCompanyEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/provider-a-spain-company/enrich");
        lenient().when(providerASpainCompanyEnricher.getTenantId()).thenReturn(SPAIN_TENANT_ID);
        lenient().when(providerASpainCompanyEnricher.getPriority()).thenReturn(100);
        lenient().when(providerASpainCompanyEnricher.getTags()).thenReturn(List.of("company", "spain"));
        lenient().when(providerASpainCompanyEnricher.supportsEnrichmentType("company-profile")).thenReturn(true);

        // Setup Provider A USA - Credit Report enricher
        lenient().when(providerAUSACreditEnricher.getProviderName()).thenReturn("Provider A USA");
        lenient().when(providerAUSACreditEnricher.getSupportedEnrichmentTypes())
                .thenReturn(List.of("credit-report"));
        lenient().when(providerAUSACreditEnricher.getEnricherDescription())
                .thenReturn("Provider A USA credit report enrichment");
        lenient().when(providerAUSACreditEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/provider-a-usa-credit/enrich");
        lenient().when(providerAUSACreditEnricher.getTenantId()).thenReturn(USA_TENANT_ID);
        lenient().when(providerAUSACreditEnricher.getPriority()).thenReturn(100);
        lenient().when(providerAUSACreditEnricher.getTags()).thenReturn(List.of("credit", "usa"));
        lenient().when(providerAUSACreditEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);

        // Setup Provider B Spain - Credit Report enricher
        lenient().when(providerBSpainCreditEnricher.getProviderName()).thenReturn("Provider B Spain");
        lenient().when(providerBSpainCreditEnricher.getSupportedEnrichmentTypes())
                .thenReturn(List.of("credit-report"));
        lenient().when(providerBSpainCreditEnricher.getEnricherDescription())
                .thenReturn("Provider B Spain credit report enrichment");
        lenient().when(providerBSpainCreditEnricher.getEnrichmentEndpoint())
                .thenReturn("/api/v1/enrichment/provider-b-spain-credit/enrich");
        lenient().when(providerBSpainCreditEnricher.getTenantId()).thenReturn(SPAIN_TENANT_ID);
        lenient().when(providerBSpainCreditEnricher.getPriority()).thenReturn(50);
        lenient().when(providerBSpainCreditEnricher.getTags()).thenReturn(List.of("credit", "spain"));
        lenient().when(providerBSpainCreditEnricher.supportsEnrichmentType("credit-report")).thenReturn(true);
    }

    @Test
    void listProviders_shouldReturnAllEnrichers_whenNoFilterSpecified() {
        // Given - 4 enrichers (each enricher = one entry)
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerASpainCompanyEnricher,
                providerAUSACreditEnricher,
                providerBSpainCreditEnricher
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null, null))
                .assertNext(providers -> {
                    // Should have 4 entries (one per enricher)
                    assertThat(providers).hasSize(4);

                    // Verify Provider A Spain - Credit Report
                    var providerASpainCredit = providers.stream()
                            .filter(p -> p.providerName().equals("Provider A Spain") && p.type().equals("credit-report"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(providerASpainCredit.type()).isEqualTo("credit-report");
                    assertThat(providerASpainCredit.tenantId()).isEqualTo(SPAIN_TENANT_ID);
                    assertThat(providerASpainCredit.description()).isEqualTo("Provider A Spain credit report enrichment");
                    assertThat(providerASpainCredit.endpoint()).isEqualTo("/api/v1/enrichment/provider-a-spain-credit/enrich");
                    assertThat(providerASpainCredit.priority()).isEqualTo(100);

                    // Verify Provider A Spain - Company Profile
                    var providerASpainCompany = providers.stream()
                            .filter(p -> p.providerName().equals("Provider A Spain") && p.type().equals("company-profile"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(providerASpainCompany.type()).isEqualTo("company-profile");
                    assertThat(providerASpainCompany.tenantId()).isEqualTo(SPAIN_TENANT_ID);

                    // Verify Provider A USA - Credit Report
                    var providerAUSACredit = providers.stream()
                            .filter(p -> p.providerName().equals("Provider A USA"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(providerAUSACredit.type()).isEqualTo("credit-report");
                    assertThat(providerAUSACredit.tenantId()).isEqualTo(USA_TENANT_ID);

                    // Verify Provider B Spain - Credit Report
                    var providerBSpainCredit = providers.stream()
                            .filter(p -> p.providerName().equals("Provider B Spain"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(providerBSpainCredit.type()).isEqualTo("credit-report");
                    assertThat(providerBSpainCredit.tenantId()).isEqualTo(SPAIN_TENANT_ID);
                    assertThat(providerBSpainCredit.priority()).isEqualTo(50);
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldFilterByEnrichmentType_whenTypeSpecified() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerASpainCompanyEnricher,
                providerAUSACreditEnricher,
                providerBSpainCreditEnricher
        ));

        // When & Then - filter by credit-report (should return 3 enrichers)
        StepVerifier.create(controller.listProviders("credit-report", null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(3);
                    assertThat(providers).extracting("providerName")
                            .containsExactlyInAnyOrder("Provider A Spain", "Provider A USA", "Provider B Spain");
                    assertThat(providers).allMatch(p -> p.type().equals("credit-report"));
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldFilterByEnrichmentType_whenOnlySomeEnrichersSupport() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerASpainCompanyEnricher,
                providerAUSACreditEnricher,
                providerBSpainCreditEnricher
        ));

        // When & Then - filter by company-profile (only one enricher supports this)
        StepVerifier.create(controller.listProviders("company-profile", null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(1);
                    assertThat(providers.get(0).providerName()).isEqualTo("Provider A Spain");
                    assertThat(providers.get(0).type()).isEqualTo("company-profile");
                    assertThat(providers.get(0).tenantId()).isEqualTo(SPAIN_TENANT_ID);
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldReturnEmptyList_whenNoEnrichersMatchFilter() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerAUSACreditEnricher
        ));

        // When & Then - filter by non-existent type
        StepVerifier.create(controller.listProviders("non-existent-type", null))
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
        StepVerifier.create(controller.listProviders(null, null))
                .assertNext(providers -> {
                    assertThat(providers).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldReturnSeparateEntries_whenMultipleEnrichersForSameProvider() {
        // Given - Two enrichers for Provider A Spain with different types
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerASpainCompanyEnricher
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null, null))
                .assertNext(providers -> {
                    // Should have 2 entries (one per enricher, even though same provider)
                    assertThat(providers).hasSize(2);

                    // Both have same provider name but different types
                    assertThat(providers).allMatch(p -> p.providerName().equals("Provider A Spain"));
                    assertThat(providers).extracting("type")
                            .containsExactlyInAnyOrder("credit-report", "company-profile");
                    assertThat(providers).extracting("endpoint")
                            .containsExactlyInAnyOrder(
                                    "/api/v1/enrichment/provider-a-spain-credit/enrich",
                                    "/api/v1/enrichment/provider-a-spain-company/enrich"
                            );
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldHandleEmptyEnrichmentTypeFilter() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerASpainCreditEnricher,
                providerAUSACreditEnricher
        ));

        // When & Then - empty string should be treated as no filter
        StepVerifier.create(controller.listProviders("", null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(2);
                })
                .verifyComplete();
    }

    @Test
    void listProviders_shouldSortEnrichersByProviderNameThenType() {
        // Given
        when(registry.getAllEnrichers()).thenReturn(List.of(
                providerBSpainCreditEnricher,  // Provider B comes after Provider A alphabetically
                providerAUSACreditEnricher,
                providerASpainCompanyEnricher,
                providerASpainCreditEnricher
        ));

        // When & Then
        StepVerifier.create(controller.listProviders(null, null))
                .assertNext(providers -> {
                    assertThat(providers).hasSize(4);
                    // Should be sorted by provider name, then by type
                    assertThat(providers.get(0).providerName()).isEqualTo("Provider A Spain");
                    assertThat(providers.get(0).type()).isEqualTo("company-profile");
                    assertThat(providers.get(1).providerName()).isEqualTo("Provider A Spain");
                    assertThat(providers.get(1).type()).isEqualTo("credit-report");
                    assertThat(providers.get(2).providerName()).isEqualTo("Provider A USA");
                    assertThat(providers.get(3).providerName()).isEqualTo("Provider B Spain");
                })
                .verifyComplete();
    }
}

