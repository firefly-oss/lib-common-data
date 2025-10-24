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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for DataEnricherRegistry.
 */
@ExtendWith(MockitoExtension.class)
class DataEnricherRegistryTest {

    @Mock
    private DataEnricher enricher1;

    @Mock
    private DataEnricher enricher2;

    @Mock
    private DataEnricher enricher3;

    private DataEnricherRegistry registry;

    @BeforeEach
    void setUp() {
        // Setup enricher1 - Financial Data Provider
        lenient().when(enricher1.getProviderName()).thenReturn("Financial Data Provider");
        lenient().when(enricher1.getSupportedEnrichmentTypes()).thenReturn(new String[]{"company-profile", "company-financials"});
        lenient().when(enricher1.supportsEnrichmentType("company-profile")).thenReturn(true);
        lenient().when(enricher1.supportsEnrichmentType("company-financials")).thenReturn(true);

        // Setup enricher2 - Credit Bureau Provider
        lenient().when(enricher2.getProviderName()).thenReturn("Credit Bureau Provider");
        lenient().when(enricher2.getSupportedEnrichmentTypes()).thenReturn(new String[]{"credit-score", "credit-report"});
        lenient().when(enricher2.supportsEnrichmentType("credit-score")).thenReturn(true);
        lenient().when(enricher2.supportsEnrichmentType("credit-report")).thenReturn(true);

        // Setup enricher3 - Business Data Provider
        lenient().when(enricher3.getProviderName()).thenReturn("Business Data Provider");
        lenient().when(enricher3.getSupportedEnrichmentTypes()).thenReturn(new String[]{"company-profile", "risk-assessment"});
        lenient().when(enricher3.supportsEnrichmentType("company-profile")).thenReturn(true);
        lenient().when(enricher3.supportsEnrichmentType("risk-assessment")).thenReturn(true);

        registry = new DataEnricherRegistry(List.of(enricher1, enricher2, enricher3));
    }

    @Test
    void getEnricherByProvider_shouldReturnEnricher_whenProviderExists() {
        // When
        Optional<DataEnricher> result = registry.getEnricherByProvider("Financial Data Provider");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(enricher1);
    }

    @Test
    void getEnricherByProvider_shouldReturnEmpty_whenProviderDoesNotExist() {
        // When
        Optional<DataEnricher> result = registry.getEnricherByProvider("Unknown Provider");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getEnricherByProvider_shouldBeCaseInsensitive() {
        // When
        Optional<DataEnricher> result = registry.getEnricherByProvider("financial data provider");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(enricher1);
    }

    @Test
    void getEnricherForType_shouldReturnFirstMatchingEnricher() {
        // When
        Optional<DataEnricher> result = registry.getEnricherForType("company-profile");

        // Then
        assertThat(result).isPresent();
        // Should return enricher1 (first registered that supports this type)
        assertThat(result.get()).isEqualTo(enricher1);
    }

    @Test
    void getEnricherForType_shouldReturnEmpty_whenNoEnricherSupportsType() {
        // When
        Optional<DataEnricher> result = registry.getEnricherForType("unsupported-type");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getAllEnrichers_shouldReturnAllRegisteredEnrichers() {
        // When
        List<DataEnricher> enrichers = registry.getAllEnrichers();

        // Then
        assertThat(enrichers).hasSize(3);
        assertThat(enrichers).containsExactly(enricher1, enricher2, enricher3);
    }

    @Test
    void getAllProviderNames_shouldReturnAllProviderNames() {
        // When
        List<String> providerNames = registry.getAllProviderNames();

        // Then
        assertThat(providerNames).hasSize(3);
        assertThat(providerNames).containsExactlyInAnyOrder(
                "Financial Data Provider",
                "Credit Bureau Provider",
                "Business Data Provider"
        );
    }

    @Test
    void getAllEnrichmentTypes_shouldReturnAllUniqueTypes() {
        // When
        List<String> types = registry.getAllEnrichmentTypes();

        // Then
        assertThat(types).hasSize(5);
        assertThat(types).containsExactlyInAnyOrder(
                "company-profile",
                "company-financials",
                "credit-score",
                "credit-report",
                "risk-assessment"
        );
    }

    @Test
    void constructor_shouldHandleEmptyList() {
        // Given
        DataEnricherRegistry emptyRegistry = new DataEnricherRegistry(List.of());

        // When & Then
        assertThat(emptyRegistry.getAllEnrichers()).isEmpty();
        assertThat(emptyRegistry.getAllProviderNames()).isEmpty();
        assertThat(emptyRegistry.getAllEnrichmentTypes()).isEmpty();
        assertThat(emptyRegistry.getEnricherByProvider("any")).isEmpty();
        assertThat(emptyRegistry.getEnricherForType("any")).isEmpty();
    }

    @Test
    void getEnricherForType_shouldReturnDifferentEnrichers_forDifferentTypes() {
        // When
        Optional<DataEnricher> companyProfileEnricher = registry.getEnricherForType("company-profile");
        Optional<DataEnricher> creditScoreEnricher = registry.getEnricherForType("credit-score");

        // Then
        assertThat(companyProfileEnricher).isPresent();
        assertThat(creditScoreEnricher).isPresent();
        assertThat(companyProfileEnricher.get()).isEqualTo(enricher1);
        assertThat(creditScoreEnricher.get()).isEqualTo(enricher2);
    }

    @Test
    void getEnricherForType_shouldHandleMultipleEnrichersForSameType() {
        // Given - both enricher1 and enricher3 support "company-profile"
        
        // When
        Optional<DataEnricher> result = registry.getEnricherForType("company-profile");

        // Then - should return the first one registered
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(enricher1);
    }

    @Test
    void getAllEnrichmentTypes_shouldNotContainDuplicates() {
        // Given - "company-profile" is supported by both enricher1 and enricher3

        // When
        List<String> types = registry.getAllEnrichmentTypes();

        // Then
        assertThat(types).doesNotHaveDuplicates();
        long companyProfileCount = types.stream()
                .filter(type -> type.equals("company-profile"))
                .count();
        assertThat(companyProfileCount).isEqualTo(1);
    }

    @Test
    void getEnricherByProvider_shouldReturnCorrectEnricher_forEachProvider() {
        // When & Then
        assertThat(registry.getEnricherByProvider("Financial Data Provider"))
                .isPresent()
                .hasValue(enricher1);

        assertThat(registry.getEnricherByProvider("Credit Bureau Provider"))
                .isPresent()
                .hasValue(enricher2);

        assertThat(registry.getEnricherByProvider("Business Data Provider"))
                .isPresent()
                .hasValue(enricher3);
    }

    @Test
    void getEnricherByProvider_shouldHandleNullProvider() {
        // When
        Optional<DataEnricher> enricher = registry.getEnricherByProvider(null);

        // Then
        assertThat(enricher).isEmpty();
    }

    @Test
    void getEnricherByProvider_shouldHandleEmptyProvider() {
        // When
        Optional<DataEnricher> enricher = registry.getEnricherByProvider("");

        // Then
        assertThat(enricher).isEmpty();
    }

    @Test
    void getEnricherForType_shouldHandleNullType() {
        // When
        Optional<DataEnricher> enricher = registry.getEnricherForType(null);

        // Then
        assertThat(enricher).isEmpty();
    }

    @Test
    void getEnricherForType_shouldHandleEmptyType() {
        // When
        Optional<DataEnricher> enricher = registry.getEnricherForType("");

        // Then
        assertThat(enricher).isEmpty();
    }

    @Test
    void hasEnricherForProvider_shouldReturnTrue_whenProviderExists() {
        // When & Then
        assertThat(registry.hasEnricherForProvider("Financial Data Provider")).isTrue();
        assertThat(registry.hasEnricherForProvider("Credit Bureau Provider")).isTrue();
    }

    @Test
    void hasEnricherForProvider_shouldReturnFalse_whenProviderDoesNotExist() {
        // When & Then
        assertThat(registry.hasEnricherForProvider("Unknown Provider")).isFalse();
    }

    @Test
    void hasEnricherForType_shouldReturnTrue_whenTypeIsSupported() {
        // When & Then
        assertThat(registry.hasEnricherForType("company-profile")).isTrue();
        assertThat(registry.hasEnricherForType("credit-score")).isTrue();
    }

    @Test
    void hasEnricherForType_shouldReturnFalse_whenTypeIsNotSupported() {
        // When & Then
        assertThat(registry.hasEnricherForType("unsupported-type")).isFalse();
    }

    @Test
    void getEnricherCount_shouldReturnCorrectCount() {
        // When
        int count = registry.getEnricherCount();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void getEnricherCount_shouldReturnZero_whenNoEnrichersRegistered() {
        // Given
        DataEnricherRegistry emptyRegistry = new DataEnricherRegistry(List.of());

        // When
        int count = emptyRegistry.getEnricherCount();

        // Then
        assertThat(count).isEqualTo(0);
    }
}

