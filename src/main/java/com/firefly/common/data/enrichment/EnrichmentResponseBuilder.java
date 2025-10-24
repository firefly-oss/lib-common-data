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

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for creating enrichment responses.
 * 
 * <p>This builder simplifies the creation of enrichment responses with
 * a fluent API that makes the code more readable and maintainable.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Success response
 * EnrichmentResponse response = EnrichmentResponseBuilder
 *     .success(enrichedData)
 *     .forRequest(request)
 *     .withProvider("Financial Data Provider")
 *     .withMessage("Company data enriched successfully")
 *     .withFieldsEnriched(5)
 *     .withCost(0.50, "USD")
 *     .withConfidence(0.95)
 *     .build();
 *
 * // Failure response
 * EnrichmentResponse response = EnrichmentResponseBuilder
 *     .failure("Provider returned 404: Company not found")
 *     .forRequest(request)
 *     .withProvider("Financial Data Provider")
 *     .build();
 * }</pre>
 */
public class EnrichmentResponseBuilder {
    
    private boolean success;
    private Object enrichedData;
    private Object rawProviderResponse;
    private String message;
    private String error;
    private String providerName;
    private String enrichmentType;
    private EnrichmentStrategy strategy;
    private Double confidenceScore;
    private Integer fieldsEnriched;
    private Instant timestamp;
    private Map<String, String> metadata;
    private String requestId;
    private Double cost;
    private String costCurrency;
    
    private EnrichmentResponseBuilder(boolean success) {
        this.success = success;
        this.timestamp = Instant.now();
    }
    
    /**
     * Creates a success response builder.
     *
     * @param enrichedData the enriched data
     * @return a new builder instance
     */
    public static EnrichmentResponseBuilder success(Object enrichedData) {
        EnrichmentResponseBuilder builder = new EnrichmentResponseBuilder(true);
        builder.enrichedData = enrichedData;
        return builder;
    }
    
    /**
     * Creates a failure response builder.
     *
     * @param error the error message
     * @return a new builder instance
     */
    public static EnrichmentResponseBuilder failure(String error) {
        EnrichmentResponseBuilder builder = new EnrichmentResponseBuilder(false);
        builder.error = error;
        return builder;
    }
    
    /**
     * Populates fields from the enrichment request.
     *
     * @param request the enrichment request
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder forRequest(EnrichmentRequest request) {
        if (request != null) {
            this.enrichmentType = request.getEnrichmentType();
            this.strategy = request.getStrategy();
            this.requestId = request.getRequestId();
        }
        return this;
    }
    
    /**
     * Sets the provider name.
     *
     * @param providerName the provider name
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withProvider(String providerName) {
        this.providerName = providerName;
        return this;
    }
    
    /**
     * Sets the enrichment type.
     *
     * @param enrichmentType the enrichment type
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withEnrichmentType(String enrichmentType) {
        this.enrichmentType = enrichmentType;
        return this;
    }
    
    /**
     * Sets the enrichment strategy.
     *
     * @param strategy the enrichment strategy
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withStrategy(EnrichmentStrategy strategy) {
        this.strategy = strategy;
        return this;
    }
    
    /**
     * Sets the success message.
     *
     * @param message the success message
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withMessage(String message) {
        this.message = message;
        return this;
    }
    
    /**
     * Sets the raw provider response.
     *
     * @param rawProviderResponse the raw provider response
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withRawResponse(Object rawProviderResponse) {
        this.rawProviderResponse = rawProviderResponse;
        return this;
    }
    
    /**
     * Sets the confidence score.
     *
     * @param confidenceScore the confidence score (0.0 to 1.0)
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withConfidence(double confidenceScore) {
        this.confidenceScore = confidenceScore;
        return this;
    }
    
    /**
     * Sets the number of fields enriched.
     *
     * @param fieldsEnriched the number of fields enriched
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withFieldsEnriched(int fieldsEnriched) {
        this.fieldsEnriched = fieldsEnriched;
        return this;
    }
    
    /**
     * Automatically counts the number of fields enriched by comparing source and enriched data.
     *
     * @param sourceDto the source DTO
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder countingEnrichedFields(Object sourceDto) {
        if (enrichedData != null) {
            this.fieldsEnriched = EnrichmentStrategyApplier.countEnrichedFields(sourceDto, enrichedData);
        }
        return this;
    }
    
    /**
     * Sets the cost of the enrichment operation.
     *
     * @param cost the cost amount
     * @param currency the currency code (e.g., "USD")
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withCost(double cost, String currency) {
        this.cost = cost;
        this.costCurrency = currency;
        return this;
    }
    
    /**
     * Sets the request ID.
     *
     * @param requestId the request ID
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
    
    /**
     * Adds metadata to the response.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * Sets all metadata at once.
     *
     * @param metadata the metadata map
     * @return this builder for chaining
     */
    public EnrichmentResponseBuilder withMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }
    
    /**
     * Builds the enrichment response.
     *
     * @return the enrichment response
     */
    public EnrichmentResponse build() {
        return EnrichmentResponse.builder()
                .success(success)
                .enrichedData(enrichedData)
                .rawProviderResponse(rawProviderResponse)
                .message(message)
                .error(error)
                .providerName(providerName)
                .enrichmentType(enrichmentType)
                .strategy(strategy)
                .confidenceScore(confidenceScore)
                .fieldsEnriched(fieldsEnriched)
                .timestamp(timestamp)
                .metadata(metadata)
                .requestId(requestId)
                .cost(cost)
                .costCurrency(costCurrency)
                .build();
    }
}

