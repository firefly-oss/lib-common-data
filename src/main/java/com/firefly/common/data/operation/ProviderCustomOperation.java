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

package com.firefly.common.data.operation;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a provider-specific custom operation.
 *
 * <p>This annotation is used to define auxiliary operations that a data enricher
 * provides beyond the standard enrichment flow. These are provider-specific operations
 * that clients may need to call before or alongside enrichment requests.</p>
 *
 * <p><b>Common Use Cases:</b></p>
 * <ul>
 *   <li><b>ID Lookups</b> - Search for a company by name to get the provider's internal ID</li>
 *   <li><b>Entity Matching</b> - Match a company name to the provider's database</li>
 *   <li><b>Validation</b> - Validate a tax ID format or check if an ID exists</li>
 *   <li><b>Metadata Retrieval</b> - Get available data fields or coverage information</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @ProviderCustomOperation(
 *     operationId = "search-company",
 *     description = "Search for a company by name or tax ID to obtain provider internal ID",
 *     method = RequestMethod.GET,
 *     tags = {"lookup", "search"}
 * )
 * public class SearchCompanyOperation 
 *         extends AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {
 *     
 *     private final RestClient providerClient;
 *     
 *     public SearchCompanyOperation(RestClient providerClient) {
 *         this.providerClient = providerClient;
 *     }
 *     
 *     @Override
 *     protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
 *         return providerClient.get("/search", CompanySearchResponse.class)
 *             .withQueryParam("name", request.getCompanyName())
 *             .withQueryParam("taxId", request.getTaxId())
 *             .execute();
 *     }
 * }
 * }</pre>
 *
 * <p>The annotation automatically registers the class as a Spring bean via {@code @Component}
 * meta-annotation, so no additional {@code @Component} or {@code @Service} is needed.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Automatic Spring bean registration</li>
 *   <li>Automatic JSON Schema generation for request/response DTOs</li>
 *   <li>Automatic REST endpoint exposure via {@link com.firefly.common.data.controller.AbstractDataEnricherController}</li>
 *   <li>Type-safe request/response handling</li>
 *   <li>Built-in validation and error handling</li>
 * </ul>
 *
 * @see AbstractProviderOperation
 * @see ProviderOperation
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ProviderCustomOperation {

    /**
     * Unique identifier for this operation within the provider.
     *
     * <p>This will be used as part of the URL path. Should be kebab-case.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"search-company"</li>
     *   <li>"validate-tax-id"</li>
     *   <li>"get-credit-score"</li>
     *   <li>"match-company"</li>
     * </ul>
     *
     * @return the operation ID
     */
    String operationId();

    /**
     * Human-readable description of what this operation does.
     *
     * <p>This will be included in OpenAPI documentation and discovery responses.</p>
     *
     * <p><b>Example:</b> "Search for a company by name or tax ID to obtain provider internal ID"</p>
     *
     * @return the operation description
     */
    String description();

    /**
     * HTTP method for this operation.
     *
     * <p>Typically GET for lookups/searches, POST for complex queries.</p>
     *
     * @return the HTTP method (default: GET)
     */
    RequestMethod method() default RequestMethod.GET;

    /**
     * Tags for categorizing this operation.
     *
     * <p>Tags are used for grouping operations in documentation and discovery.</p>
     *
     * <p><b>Common Tags:</b></p>
     * <ul>
     *   <li>"lookup" - ID lookup operations</li>
     *   <li>"search" - Search/matching operations</li>
     *   <li>"validation" - Validation operations</li>
     *   <li>"metadata" - Metadata retrieval</li>
     *   <li>"quick-query" - Quick data queries</li>
     * </ul>
     *
     * @return array of tags (default: empty array)
     */
    String[] tags() default {};

    /**
     * Whether this operation requires authentication.
     *
     * <p>This is informational and included in the operation metadata.
     * Actual authentication enforcement should be handled by Spring Security.</p>
     *
     * @return true if authentication is required (default: true)
     */
    boolean requiresAuth() default true;

    /**
     * The URL path for this operation (relative to the enricher's base path).
     *
     * <p>If not specified, defaults to "/{operationId}".</p>
     *
     * <p><b>Example:</b> If enricher base path is {@code /api/v1/enrichment/credit-bureau}
     * and this path is {@code /search-company}, the full endpoint will be:
     * {@code /api/v1/enrichment/credit-bureau/operation/search-company}</p>
     *
     * @return the operation path (default: empty string, which means use "/{operationId}")
     */
    String path() default "";

    /**
     * Whether to include this operation in the discovery endpoint.
     *
     * <p>Set to false to hide internal/deprecated operations from discovery.</p>
     *
     * @return true if operation should be discoverable (default: true)
     */
    boolean discoverable() default true;

    /**
     * The Spring bean name for this operation.
     *
     * <p>If not specified, Spring will generate a default bean name based on the class name.</p>
     *
     * @return the bean name (default: empty string for auto-generated name)
     */
    String value() default "";
}

