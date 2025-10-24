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

package com.firefly.common.data.model;

/**
 * Defines the strategy for how enrichment data should be applied to the target DTO.
 * 
 * <p>This enum determines how the data enricher combines the original DTO with
 * the enrichment data from the provider.</p>
 * 
 * <p><b>Strategy Types:</b></p>
 * <ul>
 *   <li><b>ENHANCE</b> - Fill in missing/null fields in the existing DTO with provider data</li>
 *   <li><b>MERGE</b> - Combine existing DTO with provider data, with provider data taking precedence</li>
 *   <li><b>REPLACE</b> - Replace the entire DTO with provider response data</li>
 *   <li><b>RAW</b> - Return the raw provider response without transformation</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * // ENHANCE - Only fill missing fields
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .strategy(EnrichmentStrategy.ENHANCE)
 *     .sourceDto(partialCompanyData)  // Has name, missing address
 *     .build();
 * // Result: Company with name from source, address from provider
 * 
 * // MERGE - Combine with provider data taking precedence
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .strategy(EnrichmentStrategy.MERGE)
 *     .sourceDto(existingCompanyData)  // Has outdated address
 *     .build();
 * // Result: Company with updated address from provider
 * 
 * // REPLACE - Use provider data entirely
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .strategy(EnrichmentStrategy.REPLACE)
 *     .build();
 * // Result: Fresh company data entirely from provider
 * 
 * // RAW - Get unprocessed provider response
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .strategy(EnrichmentStrategy.RAW)
 *     .build();
 * // Result: Raw JSON/XML from provider for custom processing
 * }</pre>
 */
public enum EnrichmentStrategy {
    
    /**
     * Enhance the existing DTO by filling in only null or missing fields with provider data.
     * 
     * <p>This strategy preserves all existing data in the source DTO and only adds
     * information from the provider where fields are null or empty.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Completing partial data from user input</li>
     *   <li>Adding optional fields without overwriting existing values</li>
     *   <li>Progressive data enrichment from multiple sources</li>
     * </ul>
     */
    ENHANCE,
    
    /**
     * Merge the existing DTO with provider data, with provider data taking precedence.
     * 
     * <p>This strategy combines both the source DTO and provider data, but when
     * there are conflicts, the provider's data is used.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Updating stale data with fresh provider information</li>
     *   <li>Synchronizing local data with authoritative source</li>
     *   <li>Refreshing cached data while preserving local-only fields</li>
     * </ul>
     */
    MERGE,
    
    /**
     * Replace the entire DTO with provider response data.
     * 
     * <p>This strategy ignores the source DTO entirely and uses only the
     * provider's response to construct the result.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Fetching complete data from provider using only an identifier</li>
     *   <li>Refreshing all data from authoritative source</li>
     *   <li>Initial data population from provider</li>
     * </ul>
     */
    REPLACE,
    
    /**
     * Return the raw provider response without any transformation or mapping.
     * 
     * <p>This strategy returns the provider's response in its original format
     * (JSON, XML, etc.) without applying any DTO mapping or transformation.</p>
     * 
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Custom processing of provider data</li>
     *   <li>Debugging provider responses</li>
     *   <li>Storing raw provider data for audit purposes</li>
     *   <li>Handling provider-specific data structures</li>
     * </ul>
     */
    RAW
}

