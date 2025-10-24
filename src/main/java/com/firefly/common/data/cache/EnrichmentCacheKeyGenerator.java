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

package com.firefly.common.data.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.model.EnrichmentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates cache keys for enrichment requests with tenant isolation.
 * 
 * <p>Cache keys are generated using the following format:</p>
 * <pre>
 * enrichment:{tenantId}:{providerName}:{enrichmentType}:{parametersHash}
 * </pre>
 * 
 * <p><b>Key Components:</b></p>
 * <ul>
 *   <li><b>enrichment</b> - Prefix to identify enrichment cache entries</li>
 *   <li><b>tenantId</b> - Ensures tenant isolation (defaults to "default" if not specified)</li>
 *   <li><b>providerName</b> - The data provider name</li>
 *   <li><b>enrichmentType</b> - The type of enrichment (e.g., "company-profile")</li>
 *   <li><b>parametersHash</b> - SHA-256 hash of sorted parameters for uniqueness</li>
 * </ul>
 * 
 * <p><b>Example Cache Keys:</b></p>
 * <pre>
 * enrichment:tenant-abc:Financial Data Provider:company-profile:a3f2b1c4...
 * enrichment:tenant-xyz:Credit Bureau Provider:credit-report:d5e6f7a8...
 * enrichment:default:Address Validator:address-verification:b9c0d1e2...
 * </pre>
 * 
 * <p><b>Tenant Isolation:</b></p>
 * <p>Each tenant has completely isolated cache entries. This ensures:</p>
 * <ul>
 *   <li>Data privacy - Tenants cannot access each other's cached data</li>
 *   <li>Configuration isolation - Different tenants may use different providers</li>
 *   <li>Rate limiting - Cache hit rates are tracked per tenant</li>
 * </ul>
 */
@Slf4j
@Component
public class EnrichmentCacheKeyGenerator {

    private static final String CACHE_PREFIX = "enrichment";
    private static final String DEFAULT_TENANT = "default";
    private static final String SEPARATOR = ":";
    
    private final ObjectMapper objectMapper;
    private final MessageDigest messageDigest;

    public EnrichmentCacheKeyGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            this.messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates a cache key for an enrichment request.
     *
     * @param request the enrichment request
     * @param providerName the provider name
     * @return the cache key
     */
    public String generateKey(EnrichmentRequest request, String providerName) {
        String tenantId = getTenantId(request);
        String enrichmentType = request.getEnrichmentType();
        String parametersHash = hashParameters(request.getParameters());
        
        return String.join(SEPARATOR, 
            CACHE_PREFIX,
            sanitize(tenantId),
            sanitize(providerName),
            sanitize(enrichmentType),
            parametersHash
        );
    }

    /**
     * Generates a cache key pattern for evicting all entries for a tenant.
     *
     * @param tenantId the tenant ID
     * @return the cache key pattern (e.g., "enrichment:tenant-abc:*")
     */
    public String generateTenantPattern(String tenantId) {
        return String.join(SEPARATOR, 
            CACHE_PREFIX,
            sanitize(tenantId != null ? tenantId : DEFAULT_TENANT),
            "*"
        );
    }

    /**
     * Generates a cache key pattern for evicting all entries for a provider.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @return the cache key pattern (e.g., "enrichment:tenant-abc:Financial Data Provider:*")
     */
    public String generateProviderPattern(String tenantId, String providerName) {
        return String.join(SEPARATOR, 
            CACHE_PREFIX,
            sanitize(tenantId != null ? tenantId : DEFAULT_TENANT),
            sanitize(providerName),
            "*"
        );
    }

    /**
     * Generates a cache key pattern for evicting all entries for an enrichment type.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @param enrichmentType the enrichment type
     * @return the cache key pattern
     */
    public String generateEnrichmentTypePattern(String tenantId, String providerName, String enrichmentType) {
        return String.join(SEPARATOR, 
            CACHE_PREFIX,
            sanitize(tenantId != null ? tenantId : DEFAULT_TENANT),
            sanitize(providerName),
            sanitize(enrichmentType),
            "*"
        );
    }

    /**
     * Extracts the tenant ID from the request.
     *
     * @param request the enrichment request
     * @return the tenant ID or "default" if not specified
     */
    private String getTenantId(EnrichmentRequest request) {
        String tenantId = request.getTenantId();
        return tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT;
    }

    /**
     * Hashes the parameters to create a unique identifier.
     * 
     * <p>Parameters are sorted alphabetically to ensure consistent hashing
     * regardless of parameter order.</p>
     *
     * @param parameters the request parameters
     * @return the base64-encoded hash
     */
    private String hashParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "empty";
        }

        try {
            // Sort parameters to ensure consistent hashing
            Map<String, Object> sortedParams = new TreeMap<>(parameters);
            String json = objectMapper.writeValueAsString(sortedParams);
            
            // Generate SHA-256 hash
            byte[] hash = messageDigest.digest(json.getBytes(StandardCharsets.UTF_8));
            
            // Encode as base64 URL-safe (no padding)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize parameters for hashing, using fallback", e);
            return "hash-error-" + System.currentTimeMillis();
        }
    }

    /**
     * Sanitizes a string for use in cache keys.
     * 
     * <p>Replaces colons and other special characters to avoid conflicts with the separator.</p>
     *
     * @param value the value to sanitize
     * @return the sanitized value
     */
    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        // Replace colons and whitespace with underscores
        return value.replaceAll("[:\\s]+", "_");
    }
}

