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
import java.util.UUID;

/**
 * REST API request DTO for data enrichment operations.
 * 
 * <p>This DTO is used for HTTP requests to enrichment endpoints.
 * It is converted to {@link EnrichmentRequest} by the controller layer.</p>
 * 
 * <p><b>Example Request (Simplified):</b></p>
 * <pre>{@code
 * POST /api/v1/enrichment/financial-data/enrich
 * {
 *   "type": "company-profile",
 *   "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *   "params": {
 *     "companyId": "12345",
 *     "includeFinancials": true
 *   }
 * }
 * }</pre>
 *
 * <p><b>Example Request (Full):</b></p>
 * <pre>{@code
 * POST /api/v1/enrichment/financial-data/enrich
 * {
 *   "type": "company-profile",
 *   "strategy": "ENHANCE",
 *   "source": {
 *     "companyId": "12345",
 *     "name": "Acme Corp"
 *   },
 *   "params": {
 *     "companyId": "12345",
 *     "includeFinancials": true
 *   },
 *   "tenantId": "550e8400-e29b-41d4-a716-446655440001",
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
    @NotBlank(message = "Type is required")
    @Schema(
        description = "Type of enrichment to perform",
        example = "company-profile",
        required = true
    )
    private String type;

    /**
     * The enrichment strategy to apply.
     * Defaults to ENHANCE if not specified.
     */
    @Builder.Default
    @Schema(
        description = "Strategy for applying enrichment data (defaults to ENHANCE)",
        example = "ENHANCE"
    )
    private EnrichmentStrategy strategy = EnrichmentStrategy.ENHANCE;

    /**
     * The source DTO to enrich (optional for REPLACE and RAW strategies).
     * Alias: "source" for shorter JSON.
     */
    @Schema(
        description = "Source data object to enrich (optional for REPLACE/RAW strategies)",
        example = "{\"companyId\": \"12345\", \"name\": \"Acme Corp\"}"
    )
    private Object source;

    /**
     * Provider-specific parameters for the enrichment operation.
     * Alias: "params" for shorter JSON.
     */
    @Schema(
        description = "Provider-specific parameters",
        example = "{\"companyId\": \"12345\", \"includeFinancials\": true}",
        required = true
    )
    private Map<String, Object> params;

    /**
     * Tenant identifier (UUID) for multi-tenant routing.
     */
    @NotNull(message = "Tenant ID is required")
    @Schema(
        description = "Tenant identifier (UUID) for multi-tenant routing",
        example = "550e8400-e29b-41d4-a716-446655440001",
        required = true
    )
    private UUID tenantId;

    /**
     * Request ID for tracing and correlation.
     * Auto-generated if not provided.
     */
    @Schema(
        description = "Request ID for tracing and correlation (auto-generated if not provided)",
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
     * Timeout in milliseconds for the enrichment operation.
     */
    @Schema(
        description = "Timeout in milliseconds",
        example = "30000"
    )
    private Long timeoutMillis;

}

