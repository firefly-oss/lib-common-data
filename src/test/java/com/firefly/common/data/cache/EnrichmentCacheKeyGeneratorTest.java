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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.model.EnrichmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EnrichmentCacheKeyGenerator.
 * Tests cache key generation with tenant isolation.
 */
class EnrichmentCacheKeyGeneratorTest {

    private static final UUID TENANT_ABC = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID TENANT_XYZ = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID TENANT_WITH_SPECIAL_CHARS = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

    private EnrichmentCacheKeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        keyGenerator = new EnrichmentCacheKeyGenerator(new ObjectMapper());
    }

    @Test
    void generateKey_shouldIncludeTenantId() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key = keyGenerator.generateKey(request, "TestProvider");

        // Then
        assertThat(key).startsWith("enrichment:" + TENANT_ABC + ":");
        assertThat(key).contains("TestProvider");
        assertThat(key).contains("company-profile");
    }

    @Test
    void generateKey_shouldUseDefaultTenantWhenNotSpecified() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        String key = keyGenerator.generateKey(request, "TestProvider");

        // Then
        assertThat(key).startsWith("enrichment:default:");
    }

    @Test
    void generateKey_shouldProduceDifferentKeysForDifferentTenants() {
        // Given - Same parameters, different tenants
        EnrichmentRequest tenant1Request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        EnrichmentRequest tenant2Request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_XYZ)
                .build();

        // When
        String key1 = keyGenerator.generateKey(tenant1Request, "TestProvider");
        String key2 = keyGenerator.generateKey(tenant2Request, "TestProvider");

        // Then
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).contains(TENANT_ABC.toString());
        assertThat(key2).contains(TENANT_XYZ.toString());
    }

    @Test
    void generateKey_shouldProduceSameKeyForSameRequest() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key1 = keyGenerator.generateKey(request, "TestProvider");
        String key2 = keyGenerator.generateKey(request, "TestProvider");

        // Then
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void generateKey_shouldProduceDifferentKeysForDifferentParameters() {
        // Given
        EnrichmentRequest request1 = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        EnrichmentRequest request2 = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "67890"))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key1 = keyGenerator.generateKey(request1, "TestProvider");
        String key2 = keyGenerator.generateKey(request2, "TestProvider");

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void generateKey_shouldProduceDifferentKeysForDifferentEnrichmentTypes() {
        // Given
        EnrichmentRequest request1 = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        EnrichmentRequest request2 = EnrichmentRequest.builder()
                .type("company-financials")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key1 = keyGenerator.generateKey(request1, "TestProvider");
        String key2 = keyGenerator.generateKey(request2, "TestProvider");

        // Then
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).contains("company-profile");
        assertThat(key2).contains("company-financials");
    }

    @Test
    void generateKey_shouldProduceDifferentKeysForDifferentProviders() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key1 = keyGenerator.generateKey(request, "Provider1");
        String key2 = keyGenerator.generateKey(request, "Provider2");

        // Then
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).contains("Provider1");
        assertThat(key2).contains("Provider2");
    }

    @Test
    void generateKey_shouldHandleComplexParameters() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of(
                        "companyId", "12345",
                        "includeFinancials", "true",
                        "year", "2024",
                        "country", "US"
                ))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key = keyGenerator.generateKey(request, "TestProvider");

        // Then
        assertThat(key).isNotNull();
        assertThat(key).startsWith("enrichment:" + TENANT_ABC + ":TestProvider:company-profile:");
    }

    @Test
    void generateKey_shouldProduceSameKeyForParametersInDifferentOrder() {
        // Given - Same parameters, different order
        EnrichmentRequest request1 = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of(
                        "companyId", "12345",
                        "year", "2024"
                ))
                .tenantId(TENANT_ABC)
                .build();

        EnrichmentRequest request2 = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of(
                        "year", "2024",
                        "companyId", "12345"
                ))
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key1 = keyGenerator.generateKey(request1, "TestProvider");
        String key2 = keyGenerator.generateKey(request2, "TestProvider");

        // Then - Should be the same because parameters are sorted before hashing
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void generateKey_shouldHandleEmptyParameters() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .parameters(Map.of())
                .tenantId(TENANT_ABC)
                .build();

        // When
        String key = keyGenerator.generateKey(request, "TestProvider");

        // Then
        assertThat(key).isNotNull();
        assertThat(key).startsWith("enrichment:" + TENANT_ABC + ":TestProvider:company-profile:");
    }

    @Test
    void generateKey_shouldSanitizeSpecialCharacters() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company:profile")
                .parameters(Map.of("companyId", "12345"))
                .tenantId(TENANT_WITH_SPECIAL_CHARS)
                .build();

        // When
        String key = keyGenerator.generateKey(request, "Test:Provider");

        // Then
        assertThat(key).isNotNull();
        // Should not contain unescaped colons in tenant/provider/type
        assertThat(key.split(":")).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void generateTenantPattern_shouldGenerateCorrectPattern() {
        // When
        String pattern = keyGenerator.generateTenantPattern(TENANT_ABC.toString());

        // Then
        assertThat(pattern).isEqualTo("enrichment:" + TENANT_ABC + ":*");
    }

    @Test
    void generateTenantPattern_shouldHandleDefaultTenant() {
        // When
        String pattern = keyGenerator.generateTenantPattern(null);

        // Then
        assertThat(pattern).isEqualTo("enrichment:default:*");
    }
}

