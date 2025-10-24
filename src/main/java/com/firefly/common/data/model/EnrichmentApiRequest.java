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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * REST API request DTO for data enrichment operations.
 * 
 * <p>This DTO is used for HTTP requests to enrichment endpoints.
 * It is converted to {@link EnrichmentRequest} by the controller layer.</p>
 * 
 * <p><b>Example Request:</b></p>
 * <pre>{@code
 * POST /api/v1/enrichment/company-profile/enrich
 * {
 *   "enrichmentType": "company-profile",
 *   "strategy": "ENHANCE",
 *   "sourceDto": {
 *     "companyId": "12345",
 *     "name": "Acme Corp"
 *   },
 *   "parameters": {
 *     "companyId": "12345",
 *     "includeFinancials": true
 *   },
 *   "tenantId": "tenant-001",
 *   "requestId": "req-abc-123",
 *   "initiator": "user@example.com"
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for data enrichment operation")
public class EnrichmentApiRequest {
    
    /**
     * The type of enrichment to perform.
     * Examples: "company-profile", "credit-report", "address-validation"
     */
    @NotBlank(message = "Enrichment type is required")
    @Schema(
        description = "Type of enrichment to perform",
        example = "company-profile",
        required = true
    )
    private String enrichmentType;
    
    /**
     * The enrichment strategy to apply.
     */
    @NotNull(message = "Enrichment strategy is required")
    @Schema(
        description = "Strategy for applying enrichment data",
        example = "ENHANCE",
        required = true
    )
    private EnrichmentStrategy strategy;
    
    /**
     * The source DTO to enrich (optional for REPLACE and RAW strategies).
     */
    @Schema(
        description = "Source data object to enrich (optional for REPLACE/RAW strategies)",
        example = "{\"companyId\": \"12345\", \"name\": \"Acme Corp\"}"
    )
    private Object sourceDto;
    
    /**
     * Provider-specific parameters for the enrichment operation.
     */
    @Schema(
        description = "Provider-specific parameters",
        example = "{\"companyId\": \"12345\", \"includeFinancials\": true}"
    )
    private Map<String, Object> parameters;
    
    /**
     * Tenant identifier for multi-tenant routing.
     */
    @Schema(
        description = "Tenant identifier for multi-tenant routing",
        example = "tenant-001"
    )
    private String tenantId;
    
    /**
     * Request ID for tracing and correlation.
     */
    @Schema(
        description = "Request ID for tracing and correlation",
        example = "req-abc-123"
    )
    private String requestId;
    
    /**
     * Initiator of the request (user, system, etc.).
     */
    @Schema(
        description = "Initiator of the request",
        example = "user@example.com"
    )
    private String initiator;
    
    /**
     * Additional metadata for the request.
     */
    @Schema(
        description = "Additional metadata",
        example = "{\"source\": \"web-portal\", \"version\": \"1.0\"}"
    )
    private Map<String, String> metadata;
    
    /**
     * Target DTO class name for deserialization (optional).
     */
    @Schema(
        description = "Target DTO class name for deserialization",
        example = "com.example.dto.CompanyProfileDTO"
    )
    private String targetDtoClass;
    
    /**
     * Timeout in milliseconds for the enrichment operation.
     */
    @Schema(
        description = "Timeout in milliseconds",
        example = "30000"
    )
    private Long timeoutMillis;
}

