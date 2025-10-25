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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response model for data enrichment operations.
 * 
 * <p>This class encapsulates the result of a data enrichment operation,
 * including the enriched data, provider information, and operation metadata.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * EnrichmentResponse response = enricher.enrich(request).block();
 * 
 * if (response.isSuccess()) {
 *     CompanyProfileDTO enrichedData = (CompanyProfileDTO) response.getEnrichedData();
 *     System.out.println("Enriched company: " + enrichedData.getName());
 *     System.out.println("Provider: " + response.getProviderName());
 *     System.out.println("Confidence: " + response.getConfidenceScore());
 * } else {
 *     System.err.println("Enrichment failed: " + response.getError());
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from a data enrichment operation")
public class EnrichmentResponse {
    
    /**
     * Success indicator.
     */
    @Schema(description = "Whether the enrichment was successful", example = "true")
    private boolean success;
    
    /**
     * The enriched data result.
     * 
     * <p>The type of this object depends on the enrichment strategy:</p>
     * <ul>
     *   <li>ENHANCE/MERGE: The enriched DTO with combined data</li>
     *   <li>REPLACE: The provider's DTO mapped to target type</li>
     *   <li>RAW: The raw provider response (Map, String, or provider-specific object)</li>
     * </ul>
     */
    @Schema(description = "The enriched data result")
    private Object enrichedData;
    
    /**
     * The raw provider response (optional).
     * 
     * <p>Contains the unprocessed response from the provider for audit or debugging purposes.
     * Only populated if configured to capture raw responses.</p>
     */
    @Schema(description = "Raw provider response for audit purposes")
    private Object rawProviderResponse;
    
    /**
     * Response message.
     */
    @Schema(description = "Human-readable message about the operation", 
            example = "Company data successfully enriched from Financial Data Provider")
    private String message;
    
    /**
     * Error details if operation failed.
     */
    @Schema(description = "Error message if the operation failed", 
            example = "Provider returned 404: Company not found")
    private String error;
    
    /**
     * The name of the provider that performed the enrichment.
     */
    @Schema(description = "Name of the enrichment provider", example = "Financial Data Provider")
    private String providerName;
    
    /**
     * The type of enrichment that was performed.
     */
    @Schema(description = "Type of enrichment performed", example = "company-profile")
    private String type;
    
    /**
     * The strategy that was applied.
     */
    @Schema(description = "Strategy that was applied", example = "ENHANCE")
    private EnrichmentStrategy strategy;
    
    /**
     * Confidence score of the enrichment (0.0 to 1.0).
     * 
     * <p>Indicates how confident the provider is in the enriched data.
     * Some providers return confidence scores, others may not.</p>
     */
    @Schema(description = "Confidence score (0.0 to 1.0)", example = "0.95")
    private Double confidenceScore;
    
    /**
     * Number of fields that were enriched/updated.
     */
    @Schema(description = "Number of fields enriched", example = "12")
    private Integer fieldsEnriched;
    
    /**
     * Timestamp of the response.
     */
    @Schema(description = "Timestamp when the response was generated", 
            example = "2025-10-23T10:30:00Z")
    private Instant timestamp;
    
    /**
     * Additional metadata about the enrichment operation.
     * 
     * <p>Can include provider-specific information, data quality metrics,
     * or other contextual details.</p>
     */
    @Schema(description = "Additional metadata about the operation")
    private Map<String, String> metadata;
    
    /**
     * Request ID for tracing and correlation.
     */
    @Schema(description = "Request ID for tracing", example = "req-123-456")
    private String requestId;
    
    /**
     * Cost of the enrichment operation (if applicable).
     * 
     * <p>Some providers charge per API call or per data point.
     * This field can track the cost for billing purposes.</p>
     */
    @Schema(description = "Cost of the operation", example = "0.05")
    private Double cost;
    
    /**
     * Currency for the cost (if applicable).
     */
    @Schema(description = "Currency for the cost", example = "USD")
    private String costCurrency;
    
    /**
     * Creates a successful enrichment response.
     *
     * @param enrichedData the enriched data
     * @param providerName the provider name
     * @param type the enrichment type
     * @param message success message
     * @return a successful EnrichmentResponse
     */
    public static EnrichmentResponse success(Object enrichedData,
                                             String providerName,
                                             String type,
                                             String message) {
        return EnrichmentResponse.builder()
                .success(true)
                .enrichedData(enrichedData)
                .providerName(providerName)
                .type(type)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a failed enrichment response.
     *
     * @param type the enrichment type
     * @param providerName the provider name
     * @param error error message
     * @return a failed EnrichmentResponse
     */
    public static EnrichmentResponse failure(String type,
                                             String providerName,
                                             String error) {
        return EnrichmentResponse.builder()
                .success(false)
                .type(type)
                .providerName(providerName)
                .error(error)
                .message("Enrichment failed")
                .timestamp(Instant.now())
                .build();
    }

}

