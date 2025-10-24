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

import lombok.Builder;
import lombok.Value;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

/**
 * Represents a provider-specific operation that can be exposed as a REST endpoint.
 *
 * <p>Provider operations are auxiliary endpoints that enrichers can declare to expose
 * provider-specific functionality. These are typically used for:</p>
 * <ul>
 *   <li>Looking up internal provider IDs before enrichment</li>
 *   <li>Searching/matching entities in the provider's system</li>
 *   <li>Validating identifiers (CIF, DUNS, etc.)</li>
 *   <li>Retrieving provider-specific metadata</li>
 * </ul>
 *
 * <p><b>Example - Equifax Spain Company Search:</b></p>
 * <pre>{@code
 * ProviderOperation.builder()
 *     .operationId("search-company")
 *     .path("/search-company")
 *     .method(RequestMethod.GET)
 *     .description("Search for a company by name or CIF to obtain Equifax internal ID")
 *     .requestExample(Map.of(
 *         "companyName", "Acme Corporation",
 *         "cif", "A12345678"
 *     ))
 *     .responseExample(Map.of(
 *         "equifaxId", "ES-12345",
 *         "companyName", "ACME CORPORATION SL",
 *         "cif", "A12345678",
 *         "confidence", 0.95
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Example - D&B DUNS Lookup:</b></p>
 * <pre>{@code
 * ProviderOperation.builder()
 *     .operationId("duns-lookup")
 *     .path("/duns-lookup")
 *     .method(RequestMethod.GET)
 *     .description("Look up DUNS number by company name and location")
 *     .requestExample(Map.of(
 *         "companyName", "Apple Inc",
 *         "country", "US",
 *         "state", "CA"
 *     ))
 *     .responseExample(Map.of(
 *         "dunsNumber", "123456789",
 *         "companyName", "APPLE INC",
 *         "address", "One Apple Park Way, Cupertino, CA"
 *     ))
 *     .build();
 * }</pre>
 *
 * @see ProviderOperationCatalog
 * @see AbstractDataEnricherController
 */
@Value
@Builder
public class ProviderOperation {

    /**
     * Unique identifier for this operation within the provider.
     *
     * <p>This will be used as part of the URL path. Should be kebab-case.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"search-company"</li>
     *   <li>"duns-lookup"</li>
     *   <li>"validate-cif"</li>
     *   <li>"match-company"</li>
     * </ul>
     */
    String operationId;

    /**
     * The URL path for this operation (relative to the enricher's base path).
     *
     * <p>Should start with "/" and match the operationId.</p>
     *
     * <p><b>Example:</b> If enricher base path is {@code /api/v1/enrichment/provider-a-spain}
     * and this path is {@code /search-company}, the full endpoint will be:
     * {@code /api/v1/enrichment/provider-a-spain/search-company}</p>
     */
    String path;

    /**
     * HTTP method for this operation.
     *
     * <p>Typically GET for lookups/searches, POST for complex queries.</p>
     */
    RequestMethod method;

    /**
     * Human-readable description of what this operation does.
     *
     * <p>This will be included in OpenAPI documentation and discovery responses.</p>
     */
    String description;

    /**
     * Example request parameters/body for documentation.
     *
     * <p>This helps API consumers understand how to call the operation.</p>
     */
    @Builder.Default
    Map<String, Object> requestExample = Map.of();

    /**
     * Example response for documentation.
     *
     * <p>This helps API consumers understand what to expect from the operation.</p>
     */
    @Builder.Default
    Map<String, Object> responseExample = Map.of();

    /**
     * Whether this operation requires authentication.
     *
     * <p>Default is true. Set to false for public operations.</p>
     */
    @Builder.Default
    boolean requiresAuth = true;

    /**
     * Tags for categorizing this operation in API documentation.
     *
     * <p><b>Examples:</b> "lookup", "search", "validation", "metadata"</p>
     */
    @Builder.Default
    String[] tags = new String[0];

    /**
     * Gets the full endpoint path by combining base path and operation path.
     *
     * @param basePath the enricher's base path (e.g., "/api/v1/enrichment/provider-a-spain")
     * @return the full endpoint path
     */
    public String getFullPath(String basePath) {
        // Remove trailing slash from base path if present
        String cleanBasePath = basePath.endsWith("/") 
            ? basePath.substring(0, basePath.length() - 1) 
            : basePath;
        
        // Ensure operation path starts with /
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        
        return cleanBasePath + cleanPath;
    }

    /**
     * Converts Spring RequestMethod to HTTP method string.
     *
     * @return HTTP method as string (GET, POST, etc.)
     */
    public String getHttpMethod() {
        return method.name();
    }
}

