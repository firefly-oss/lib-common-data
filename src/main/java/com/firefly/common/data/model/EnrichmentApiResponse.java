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
 * REST API response DTO for data enrichment operations.
 * 
 * <p>This DTO is used for HTTP responses from enrichment endpoints.
 * It is converted from {@link EnrichmentResponse} by the controller layer.</p>
 * 
 * <p><b>Example Response:</b></p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "enrichedData": {
 *     "companyId": "12345",
 *     "name": "Acme Corp",
 *     "address": "123 Main St",
 *     "revenue": 1000000
 *   },
 *   "providerName": "Financial Data Provider",
 *   "enrichmentType": "company-profile",
 *   "strategy": "ENHANCE",
 *   "message": "Company data enriched successfully",
 *   "confidenceScore": 0.95,
 *   "fieldsEnriched": 2,
 *   "timestamp": "2025-10-23T10:30:00Z",
 *   "requestId": "req-abc-123",
 *   "cost": 0.50,
 *   "costCurrency": "USD"
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from data enrichment operation")
public class EnrichmentApiResponse {
    
    /**
     * Whether the enrichment was successful.
     */
    @Schema(
        description = "Whether the enrichment was successful",
        example = "true",
        required = true
    )
    private boolean success;
    
    /**
     * The enriched data result.
     */
    @Schema(
        description = "The enriched data result",
        example = "{\"companyId\": \"12345\", \"name\": \"Acme Corp\", \"address\": \"123 Main St\"}"
    )
    private Object enrichedData;
    
    /**
     * Raw provider response (optional, for debugging or audit).
     */
    @Schema(
        description = "Raw provider response (optional)",
        example = "{\"status\": \"success\", \"data\": {...}}"
    )
    private Object rawProviderResponse;
    
    /**
     * Success or error message.
     */
    @Schema(
        description = "Success or error message",
        example = "Company data enriched successfully"
    )
    private String message;
    
    /**
     * Error details if enrichment failed.
     */
    @Schema(
        description = "Error details if enrichment failed",
        example = "Provider API returned 404: Company not found"
    )
    private String error;
    
    /**
     * Name of the provider that performed the enrichment.
     */
    @Schema(
        description = "Name of the provider",
        example = "Financial Data Provider"
    )
    private String providerName;
    
    /**
     * Type of enrichment performed.
     */
    @Schema(
        description = "Type of enrichment performed",
        example = "company-profile"
    )
    private String enrichmentType;
    
    /**
     * Strategy used for enrichment.
     */
    @Schema(
        description = "Strategy used for enrichment",
        example = "ENHANCE"
    )
    private EnrichmentStrategy strategy;
    
    /**
     * Provider's confidence score in the enriched data (0.0 to 1.0).
     */
    @Schema(
        description = "Provider's confidence score (0.0 to 1.0)",
        example = "0.95"
    )
    private Double confidenceScore;
    
    /**
     * Number of fields that were enriched.
     */
    @Schema(
        description = "Number of fields enriched",
        example = "5"
    )
    private Integer fieldsEnriched;
    
    /**
     * Timestamp of the enrichment operation.
     */
    @Schema(
        description = "Timestamp of the enrichment",
        example = "2025-10-23T10:30:00Z"
    )
    private Instant timestamp;
    
    /**
     * Additional metadata about the enrichment.
     */
    @Schema(
        description = "Additional metadata",
        example = "{\"processingTime\": \"250ms\", \"cacheHit\": false}"
    )
    private Map<String, String> metadata;
    
    /**
     * Request ID for correlation.
     */
    @Schema(
        description = "Request ID for correlation",
        example = "req-abc-123"
    )
    private String requestId;
    
    /**
     * Cost of the enrichment operation.
     */
    @Schema(
        description = "Cost of the operation",
        example = "0.50"
    )
    private Double cost;
    
    /**
     * Currency of the cost.
     */
    @Schema(
        description = "Currency of the cost",
        example = "USD"
    )
    private String costCurrency;
}

