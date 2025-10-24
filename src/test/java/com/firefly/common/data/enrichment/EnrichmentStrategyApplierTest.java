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

package com.firefly.common.data.enrichment;

import com.firefly.common.data.model.EnrichmentStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for EnrichmentStrategyApplier.
 */
class EnrichmentStrategyApplierTest {

    @Test
    void enhance_shouldFillOnlyNullFields_whenSourceHasPartialData() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .address(null)
                .revenue(null)
                .build();

        CompanyDTO provider = CompanyDTO.builder()
                .companyId("12345")
                .name("Different Name")  // Should NOT overwrite
                .address("123 Main St")  // Should fill
                .revenue(1000000.0)      // Should fill
                .build();

        // When
        CompanyDTO result = EnrichmentStrategyApplier.enhance(source, provider, CompanyDTO.class);

        // Then
        assertThat(result.getCompanyId()).isEqualTo("12345");
        assertThat(result.getName()).isEqualTo("Acme Corp");  // Preserved from source
        assertThat(result.getAddress()).isEqualTo("123 Main St");  // Filled from provider
        assertThat(result.getRevenue()).isEqualTo(1000000.0);  // Filled from provider
    }

    @Test
    void merge_shouldOverwriteWithProviderData_whenFieldsConflict() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Old Name")
                .address("Old Address")
                .revenue(500000.0)
                .build();

        CompanyDTO provider = CompanyDTO.builder()
                .companyId("12345")
                .name("New Name")
                .address("New Address")
                .revenue(1000000.0)
                .build();

        // When
        CompanyDTO result = EnrichmentStrategyApplier.merge(source, provider, CompanyDTO.class);

        // Then
        assertThat(result.getCompanyId()).isEqualTo("12345");
        assertThat(result.getName()).isEqualTo("New Name");  // Overwritten by provider
        assertThat(result.getAddress()).isEqualTo("New Address");  // Overwritten by provider
        assertThat(result.getRevenue()).isEqualTo(1000000.0);  // Overwritten by provider
    }

    @Test
    void replace_shouldUseOnlyProviderData_ignoringSource() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Old Name")
                .build();

        CompanyDTO provider = CompanyDTO.builder()
                .companyId("67890")
                .name("New Name")
                .address("New Address")
                .revenue(1000000.0)
                .build();

        // When
        CompanyDTO result = EnrichmentStrategyApplier.replace(provider, CompanyDTO.class);

        // Then
        assertThat(result.getCompanyId()).isEqualTo("67890");  // From provider only
        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getAddress()).isEqualTo("New Address");
        assertThat(result.getRevenue()).isEqualTo(1000000.0);
    }

    @Test
    void apply_shouldReturnRawProviderData_whenStrategyIsRaw() {
        // Given
        Map<String, Object> providerData = Map.of(
                "raw_id", "12345",
                "raw_data", "some data"
        );

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> result = EnrichmentStrategyApplier.apply(
                EnrichmentStrategy.RAW,
                null,
                providerData,
                Map.class
        );

        // Then
        assertThat(result).isEqualTo(providerData);
    }

    @Test
    void enhance_shouldHandleNullSource_byReturningProviderData() {
        // Given
        CompanyDTO provider = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        // When
        CompanyDTO result = EnrichmentStrategyApplier.enhance(null, provider, CompanyDTO.class);

        // Then
        assertThat(result.getCompanyId()).isEqualTo("12345");
        assertThat(result.getName()).isEqualTo("Acme Corp");
    }

    @Test
    void merge_shouldHandleNullSource_byReturningProviderData() {
        // Given
        CompanyDTO provider = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        // When
        CompanyDTO result = EnrichmentStrategyApplier.merge(null, provider, CompanyDTO.class);

        // Then
        assertThat(result.getCompanyId()).isEqualTo("12345");
        assertThat(result.getName()).isEqualTo("Acme Corp");
    }

    @Test
    void replace_shouldThrowException_whenProviderDataIsNull() {
        // When/Then
        assertThatThrownBy(() -> 
                EnrichmentStrategyApplier.replace(null, CompanyDTO.class))
                .isInstanceOf(EnrichmentStrategyApplier.EnrichmentStrategyException.class)
                .hasMessageContaining("Provider data cannot be null");
    }

    @Test
    void countEnrichedFields_shouldCountAddedFields_whenSourceIsPartial() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        CompanyDTO enriched = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .address("123 Main St")
                .revenue(1000000.0)
                .build();

        // When
        int count = EnrichmentStrategyApplier.countEnrichedFields(source, enriched);

        // Then
        assertThat(count).isEqualTo(2);  // address and revenue were added
    }

    @Test
    void countEnrichedFields_shouldCountChangedFields_whenFieldsWereModified() {
        // Given
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .name("Old Name")
                .address("Old Address")
                .build();

        CompanyDTO enriched = CompanyDTO.builder()
                .companyId("12345")
                .name("New Name")
                .address("New Address")
                .build();

        // When
        int count = EnrichmentStrategyApplier.countEnrichedFields(source, enriched);

        // Then
        assertThat(count).isEqualTo(2);  // name and address were changed
    }

    @Test
    void countEnrichedFields_shouldCountAllFields_whenSourceIsNull() {
        // Given
        CompanyDTO enriched = CompanyDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .address("123 Main St")
                .revenue(1000000.0)
                .build();

        // When
        int count = EnrichmentStrategyApplier.countEnrichedFields(null, enriched);

        // Then
        assertThat(count).isEqualTo(4);  // All non-null fields
    }

    @Test
    void enhance_shouldWorkWithMaps_whenDtosAreNotAvailable() {
        // Given
        Map<String, Object> source = new HashMap<>();
        source.put("id", "12345");
        source.put("name", "Acme Corp");

        Map<String, Object> provider = new HashMap<>();
        provider.put("id", "12345");
        provider.put("name", "Different Name");
        provider.put("address", "123 Main St");

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> result = EnrichmentStrategyApplier.enhance(source, provider, Map.class);

        // Then
        assertThat(result.get("id")).isEqualTo("12345");
        assertThat(result.get("name")).isEqualTo("Acme Corp");  // Preserved
        assertThat(result.get("address")).isEqualTo("123 Main St");  // Added
    }

    // Test DTO
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
}

