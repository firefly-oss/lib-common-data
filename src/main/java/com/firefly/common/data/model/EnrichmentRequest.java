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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request model for data enrichment operations.
 * 
 * <p>This class encapsulates all the information needed to perform a data enrichment
 * operation, including the source DTO to enrich, provider-specific parameters,
 * and the enrichment strategy to apply.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * // Enhance company data with Financial Data Provider
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .enrichmentType("company-profile")
 *     .strategy(EnrichmentStrategy.ENHANCE)
 *     .sourceDto(partialCompanyData)
 *     .parameters(Map.of(
 *         "companyId", "12345",
 *         "includeFinancials", true
 *     ))
 *     .tenantId("tenant-abc")
 *     .requestId(UUID.randomUUID().toString())
 *     .build();
 *
 * // Get credit report from Credit Bureau Provider
 * EnrichmentRequest request = EnrichmentRequest.builder()
 *     .enrichmentType("credit-report")
 *     .strategy(EnrichmentStrategy.REPLACE)
 *     .parameters(Map.of(
 *         "subjectId", "12345",
 *         "reportType", "FULL"
 *     ))
 *     .tenantId("tenant-xyz")
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for data enrichment operation")
public class EnrichmentRequest {
    
    /**
     * The type of enrichment being requested.
     * 
     * <p>This identifies what kind of enrichment to perform, such as:</p>
     * <ul>
     *   <li>"company-profile" - Company information enrichment</li>
     *   <li>"director-info" - Director/officer information</li>
     *   <li>"credit-report" - Credit report for individual or company</li>
     *   <li>"address-verification" - Address validation and standardization</li>
     * </ul>
     */
    @NotNull(message = "Enrichment type is required")
    @Schema(description = "Type of enrichment to perform", example = "company-profile", required = true)
    private String enrichmentType;
    
    /**
     * The enrichment strategy to apply.
     * 
     * <p>Determines how the provider data should be combined with the source DTO.</p>
     * 
     * @see EnrichmentStrategy
     */
    @NotNull(message = "Enrichment strategy is required")
    @Schema(description = "Strategy for applying enrichment data", example = "ENHANCE", required = true)
    private EnrichmentStrategy strategy;
    
    /**
     * The source DTO to be enriched (optional, depends on strategy).
     * 
     * <p>For ENHANCE and MERGE strategies, this contains the existing data to be enriched.
     * For REPLACE and RAW strategies, this may be null.</p>
     */
    @Schema(description = "Source DTO to enrich (optional for REPLACE/RAW strategies)")
    private Object sourceDto;
    
    /**
     * Provider-specific parameters for the enrichment request.
     * 
     * <p>These parameters are passed to the provider and vary by enrichment type:</p>
     * <ul>
     *   <li>Company enrichment: companyId, companyName, taxId, etc.</li>
     *   <li>Credit report: ssn, ein, reportType, etc.</li>
     *   <li>Address verification: street, city, state, zip, country, etc.</li>
     * </ul>
     */
    @NotNull(message = "Parameters are required")
    @Schema(description = "Provider-specific parameters", required = true)
    private Map<String, Object> parameters;
    
    /**
     * Tenant identifier for multi-tenant environments.
     * 
     * <p>This is used to determine which provider configuration to use,
     * as different tenants may use different providers or have different
     * provider credentials.</p>
     */
    @Schema(description = "Tenant identifier for multi-tenant routing", example = "tenant-abc")
    private String tenantId;
    
    /**
     * Request ID for tracing and correlation.
     * 
     * <p>Used for distributed tracing and correlating requests across services.</p>
     */
    @Schema(description = "Request ID for tracing", example = "req-123-456")
    private String requestId;
    
    /**
     * Initiator or user who triggered the enrichment request.
     */
    @Schema(description = "User or system that initiated the request", example = "user@example.com")
    private String initiator;
    
    /**
     * Additional metadata for the enrichment request.
     * 
     * <p>Can include custom headers, provider-specific options, or other contextual information.</p>
     */
    @Schema(description = "Additional metadata for the request")
    private Map<String, String> metadata;
    
    /**
     * The fully qualified class name of the target DTO for the enrichment result.
     * 
     * <p>This is used to determine which mapper should be used to transform
     * the provider response into the target DTO.</p>
     * 
     * <p>Example: "com.firefly.customer.dto.CompanyProfileDTO"</p>
     */
    @Schema(description = "Target DTO class for the enrichment result", 
            example = "com.firefly.customer.dto.CompanyProfileDTO")
    private String targetDtoClass;
    
    /**
     * The name of the specific mapper to use for transformation.
     * 
     * <p>If not specified, the mapper will be auto-selected based on targetDtoClass.
     * This is useful when multiple mappers exist for the same target type.</p>
     */
    @Schema(description = "Specific mapper name to use", example = "CompanyProfileMapperV2")
    private String mapperName;
    
    /**
     * Timeout for the enrichment operation in milliseconds.
     *
     * <p>If not specified, the default timeout from the enricher configuration will be used.</p>
     */
    @Schema(description = "Timeout for the operation in milliseconds", example = "30000")
    private Long timeoutMillis;

    // ========== Helper Methods for Parameter Extraction ==========

    /**
     * Gets a parameter value by name.
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * String companyId = request.param("companyId");
     * }</pre>
     *
     * @param name the parameter name
     * @return the parameter value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T param(String name) {
        if (parameters == null) {
            return null;
        }
        return (T) parameters.get(name);
    }

    /**
     * Gets a parameter value by name with a default value.
     *
     * @param name the parameter name
     * @param defaultValue the default value if parameter is not found
     * @return the parameter value, or defaultValue if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T param(String name, T defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        T value = (T) parameters.get(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a required parameter value by name.
     *
     * <p>Throws an exception if the parameter is missing or null.</p>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * String companyId = request.requireParam("companyId");
     * }</pre>
     *
     * @param name the parameter name
     * @return the parameter value
     * @throws IllegalArgumentException if parameter is missing or null
     */
    @SuppressWarnings("unchecked")
    public <T> T requireParam(String name) {
        if (parameters == null || !parameters.containsKey(name)) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        T value = (T) parameters.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is null");
        }
        return value;
    }

    /**
     * Gets a parameter value as a String.
     *
     * @param name the parameter name
     * @return the parameter value as String, or null if not found
     */
    public String paramAsString(String name) {
        Object value = param(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a parameter value as an Integer.
     *
     * @param name the parameter name
     * @return the parameter value as Integer, or null if not found
     */
    public Integer paramAsInt(String name) {
        Object value = param(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Gets a parameter value as a Boolean.
     *
     * @param name the parameter name
     * @return the parameter value as Boolean, or null if not found
     */
    public Boolean paramAsBoolean(String name) {
        Object value = param(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Checks if a parameter exists.
     *
     * @param name the parameter name
     * @return true if the parameter exists, false otherwise
     */
    public boolean hasParam(String name) {
        return parameters != null && parameters.containsKey(name);
    }

    /**
     * Gets the source DTO cast to the specified type.
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CompanyProfileDTO source = request.getSourceDtoAs(CompanyProfileDTO.class);
     * }</pre>
     *
     * @param clazz the target class
     * @return the source DTO cast to the specified type
     */
    @SuppressWarnings("unchecked")
    public <T> T getSourceDtoAs(Class<T> clazz) {
        if (sourceDto == null) {
            return null;
        }
        if (clazz.isInstance(sourceDto)) {
            return (T) sourceDto;
        }
        throw new ClassCastException("Source DTO is not of type " + clazz.getName());
    }
}

