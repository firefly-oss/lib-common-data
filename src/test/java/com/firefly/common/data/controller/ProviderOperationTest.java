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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProviderOperation}.
 */
@DisplayName("ProviderOperation Tests")
class ProviderOperationTest {

    @Test
    @DisplayName("Should build provider operation with all fields")
    void shouldBuildProviderOperationWithAllFields() {
        // Given & When
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("search-company")
            .path("/search-company")
            .method(RequestMethod.GET)
            .description("Search for a company by name or CIF")
            .requestExample(Map.of("companyName", "Acme Corp", "cif", "A12345678"))
            .responseExample(Map.of("equifaxId", "ES-12345", "confidence", 0.95))
            .requiresAuth(true)
            .tags(new String[]{"lookup", "search"})
            .build();

        // Then
        assertThat(operation.getOperationId()).isEqualTo("search-company");
        assertThat(operation.getPath()).isEqualTo("/search-company");
        assertThat(operation.getMethod()).isEqualTo(RequestMethod.GET);
        assertThat(operation.getDescription()).isEqualTo("Search for a company by name or CIF");
        assertThat(operation.getRequestExample()).containsEntry("companyName", "Acme Corp");
        assertThat(operation.getResponseExample()).containsEntry("equifaxId", "ES-12345");
        assertThat(operation.isRequiresAuth()).isTrue();
        assertThat(operation.getTags()).containsExactly("lookup", "search");
    }

    @Test
    @DisplayName("Should build provider operation with minimal fields")
    void shouldBuildProviderOperationWithMinimalFields() {
        // Given & When
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("validate-cif")
            .path("/validate-cif")
            .method(RequestMethod.GET)
            .description("Validate CIF")
            .build();

        // Then
        assertThat(operation.getOperationId()).isEqualTo("validate-cif");
        assertThat(operation.getPath()).isEqualTo("/validate-cif");
        assertThat(operation.getMethod()).isEqualTo(RequestMethod.GET);
        assertThat(operation.getDescription()).isEqualTo("Validate CIF");
        assertThat(operation.getRequestExample()).isEmpty();
        assertThat(operation.getResponseExample()).isEmpty();
        assertThat(operation.isRequiresAuth()).isTrue(); // default
        assertThat(operation.getTags()).isEmpty();
    }

    @Test
    @DisplayName("Should generate full path from base path")
    void shouldGenerateFullPathFromBasePath() {
        // Given
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("search-company")
            .path("/search-company")
            .method(RequestMethod.GET)
            .description("Search")
            .build();

        String basePath = "/api/v1/enrichment/equifax-spain";

        // When
        String fullPath = operation.getFullPath(basePath);

        // Then
        assertThat(fullPath).isEqualTo("/api/v1/enrichment/equifax-spain/search-company");
    }

    @Test
    @DisplayName("Should handle base path with trailing slash")
    void shouldHandleBasePathWithTrailingSlash() {
        // Given
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("validate-cif")
            .path("/validate-cif")
            .method(RequestMethod.GET)
            .description("Validate")
            .build();

        String basePath = "/api/v1/enrichment/equifax-spain/";

        // When
        String fullPath = operation.getFullPath(basePath);

        // Then
        assertThat(fullPath).isEqualTo("/api/v1/enrichment/equifax-spain/validate-cif");
    }

    @Test
    @DisplayName("Should handle operation path without leading slash")
    void shouldHandleOperationPathWithoutLeadingSlash() {
        // Given
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("credit-score")
            .path("credit-score")
            .method(RequestMethod.POST)
            .description("Get credit score")
            .build();

        String basePath = "/api/v1/enrichment/equifax-spain";

        // When
        String fullPath = operation.getFullPath(basePath);

        // Then
        assertThat(fullPath).isEqualTo("/api/v1/enrichment/equifax-spain/credit-score");
    }

    @Test
    @DisplayName("Should convert RequestMethod to HTTP method string")
    void shouldConvertRequestMethodToHttpMethodString() {
        // Given
        ProviderOperation getOperation = ProviderOperation.builder()
            .operationId("search")
            .path("/search")
            .method(RequestMethod.GET)
            .description("Search")
            .build();

        ProviderOperation postOperation = ProviderOperation.builder()
            .operationId("enrich")
            .path("/enrich")
            .method(RequestMethod.POST)
            .description("Enrich")
            .build();

        // When & Then
        assertThat(getOperation.getHttpMethod()).isEqualTo("GET");
        assertThat(postOperation.getHttpMethod()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Should support public operations (requiresAuth=false)")
    void shouldSupportPublicOperations() {
        // Given & When
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("health-check")
            .path("/health")
            .method(RequestMethod.GET)
            .description("Health check")
            .requiresAuth(false)
            .build();

        // Then
        assertThat(operation.isRequiresAuth()).isFalse();
    }

    @Test
    @DisplayName("Should support multiple tags")
    void shouldSupportMultipleTags() {
        // Given & When
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("search-company")
            .path("/search-company")
            .method(RequestMethod.GET)
            .description("Search")
            .tags(new String[]{"lookup", "search", "company", "validation"})
            .build();

        // Then
        assertThat(operation.getTags()).containsExactly("lookup", "search", "company", "validation");
    }

    @Test
    @DisplayName("Should support complex request and response examples")
    void shouldSupportComplexRequestAndResponseExamples() {
        // Given & When
        ProviderOperation operation = ProviderOperation.builder()
            .operationId("search-company")
            .path("/search-company")
            .method(RequestMethod.POST)
            .description("Search")
            .requestExample(Map.of(
                "companyName", "Acme Corporation",
                "cif", "A12345678",
                "country", "ES",
                "filters", Map.of(
                    "active", true,
                    "minRevenue", 1000000
                )
            ))
            .responseExample(Map.of(
                "results", java.util.List.of(
                    Map.of("equifaxId", "ES-12345", "confidence", 0.95),
                    Map.of("equifaxId", "ES-67890", "confidence", 0.85)
                ),
                "totalResults", 2
            ))
            .build();

        // Then
        assertThat(operation.getRequestExample()).containsKey("filters");
        assertThat(operation.getResponseExample()).containsKey("results");
    }
}

