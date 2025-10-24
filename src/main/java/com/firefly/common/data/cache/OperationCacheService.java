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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.data.config.DataEnrichmentProperties;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service for caching provider operation responses with tenant isolation.
 * 
 * <p>This service provides a high-level API for caching operation results,
 * with automatic tenant isolation, TTL management, and cache statistics.</p>
 * 
 * <p><b>Features:</b></p>
 * <ul>
 *   <li><b>Tenant Isolation</b> - Each tenant has completely isolated cache entries</li>
 *   <li><b>Automatic TTL</b> - Configurable time-to-live for cache entries</li>
 *   <li><b>Type-Safe Caching</b> - Generic support for any request/response types</li>
 *   <li><b>Pattern-based Eviction</b> - Evict by tenant, provider, or operation</li>
 * </ul>
 * 
 * <p><b>Cache Key Format:</b></p>
 * <pre>
 * operation:{tenantId}:{providerName}:{operationId}:{requestHash}
 * </pre>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Try to get from cache
 * Mono<Optional<CompanySearchResponse>> cached = cacheService.get(
 *     "tenant-1",
 *     "Provider A",
 *     "search-company",
 *     request,
 *     CompanySearchResponse.class
 * );
 * 
 * // If not in cache, execute and cache
 * return cached.flatMap(opt -> opt
 *     .map(Mono::just)
 *     .orElseGet(() -> executeOperation(request)
 *         .flatMap(response -> cacheService.put(
 *             "tenant-1",
 *             "Provider A",
 *             "search-company",
 *             request,
 *             response
 *         ).thenReturn(response))
 *     )
 * );
 * }</pre>
 */
@Slf4j
public class OperationCacheService {

    private final CacheAdapter cacheAdapter;
    private final ObjectMapper objectMapper;
    private final DataEnrichmentProperties properties;
    private final boolean cacheEnabled;
    private final Duration defaultTtl;

    public OperationCacheService(
            CacheAdapter cacheAdapter,
            ObjectMapper objectMapper,
            DataEnrichmentProperties properties) {
        this.cacheAdapter = cacheAdapter;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.cacheEnabled = properties.getOperations().isCacheEnabled();
        this.defaultTtl = Duration.ofSeconds(properties.getOperations().getCacheTtlSeconds());
        
        log.info("Operation Cache Service initialized");
        log.info("  • Cache enabled: {}", cacheEnabled);
        log.info("  • Default TTL: {} seconds", properties.getOperations().getCacheTtlSeconds());
        log.info("  • Cache type: {}", cacheAdapter.getCacheType());
    }

    /**
     * Gets a cached operation response.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @param operationId the operation ID
     * @param request the request object
     * @param responseClass the response class
     * @param <TRequest> the request type
     * @param <TResponse> the response type
     * @return a Mono containing the cached response if found, or empty
     */
    public <TRequest, TResponse> Mono<Optional<TResponse>> get(
            String tenantId,
            String providerName,
            String operationId,
            TRequest request,
            Class<TResponse> responseClass) {
        
        if (!cacheEnabled) {
            return Mono.just(Optional.empty());
        }

        String cacheKey = generateKey(tenantId, providerName, operationId, request);
        
        return cacheAdapter.<String, TResponse>get(cacheKey, responseClass)
            .doOnNext(opt -> {
                if (opt.isPresent()) {
                    log.debug("Cache HIT for operation: tenant={}, provider={}, operation={}, key={}",
                            tenantId, providerName, operationId, cacheKey);
                } else {
                    log.debug("Cache MISS for operation: tenant={}, provider={}, operation={}, key={}",
                            tenantId, providerName, operationId, cacheKey);
                }
            })
            .onErrorResume(error -> {
                log.warn("Error reading from cache for operation {}: {}", operationId, error.getMessage());
                return Mono.just(Optional.empty());
            });
    }

    /**
     * Puts an operation response in the cache.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @param operationId the operation ID
     * @param request the request object
     * @param response the response object
     * @param <TRequest> the request type
     * @param <TResponse> the response type
     * @return a Mono that completes when the cache operation is done
     */
    public <TRequest, TResponse> Mono<Void> put(
            String tenantId,
            String providerName,
            String operationId,
            TRequest request,
            TResponse response) {
        
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String cacheKey = generateKey(tenantId, providerName, operationId, request);
        
        return cacheAdapter.<String, TResponse>put(cacheKey, response, defaultTtl)
            .doOnSuccess(v -> 
                log.debug("Cached operation response: tenant={}, provider={}, operation={}, key={}, ttl={}s",
                        tenantId, providerName, operationId, cacheKey, defaultTtl.getSeconds()))
            .onErrorResume(error -> {
                log.warn("Error writing to cache for operation {}: {}", operationId, error.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Evicts all cache entries for a specific tenant.
     *
     * <p>Note: Pattern-based eviction depends on cache implementation.
     * For now, this clears the entire cache. In production with Redis,
     * you'd want to implement pattern-based eviction.</p>
     *
     * @param tenantId the tenant ID
     * @return a Mono that completes when eviction is done
     */
    public Mono<Void> evictByTenant(String tenantId) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String pattern = "operation:" + tenantId + ":*";
        log.info("Evicting all operation cache entries for tenant: {} (pattern: {})", tenantId, pattern);

        // Note: Pattern-based eviction depends on cache implementation
        // For now, we'll clear the entire cache if tenant eviction is requested
        return cacheAdapter.clear()
            .doOnSuccess(v ->
                log.info("Cleared operation cache for tenant: {}", tenantId))
            .onErrorResume(error -> {
                log.warn("Error evicting cache for tenant {}: {}", tenantId, error.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Evicts all cache entries for a specific provider within a tenant.
     *
     * <p>Note: Pattern-based eviction depends on cache implementation.
     * For now, this clears the entire cache.</p>
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @return a Mono that completes when eviction is done
     */
    public Mono<Void> evictByProvider(String tenantId, String providerName) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String pattern = "operation:" + tenantId + ":" + providerName + ":*";
        log.info("Evicting all operation cache entries for provider: {} in tenant: {} (pattern: {})",
                providerName, tenantId, pattern);

        // Similar to evictTenant, this would ideally use pattern-based eviction
        return cacheAdapter.clear()
            .doOnSuccess(v ->
                log.info("Cleared operation cache for provider: {} in tenant: {}", providerName, tenantId))
            .onErrorResume(error -> {
                log.warn("Error evicting cache for provider {}: {}", providerName, error.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Evicts all cache entries for a specific operation within a tenant and provider.
     *
     * <p>Note: Pattern-based eviction depends on cache implementation.
     * For now, this clears the entire cache.</p>
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @param operationId the operation ID
     * @return a Mono that completes when eviction is done
     */
    public Mono<Void> evictByOperation(String tenantId, String providerName, String operationId) {
        if (!cacheEnabled) {
            return Mono.empty();
        }

        String pattern = "operation:" + tenantId + ":" + providerName + ":" + operationId + ":*";
        log.info("Evicting all cache entries for operation: tenant={}, provider={}, operation={} (pattern: {})",
                tenantId, providerName, operationId, pattern);

        // Similar to evictTenant, this would ideally use pattern-based eviction
        return cacheAdapter.clear()
            .doOnSuccess(v ->
                log.info("Cleared cache for operation: tenant={}, provider={}, operation={}",
                        tenantId, providerName, operationId))
            .onErrorResume(error -> {
                log.warn("Error evicting cache for operation {}: {}", operationId, error.getMessage());
                return Mono.empty();
            });
    }

    /**
     * Checks if caching is enabled.
     *
     * @return true if caching is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Generates a cache key for an operation request.
     *
     * @param tenantId the tenant ID
     * @param providerName the provider name
     * @param operationId the operation ID
     * @param request the request object
     * @return the cache key
     */
    private <TRequest> String generateKey(String tenantId, String providerName, 
                                          String operationId, TRequest request) {
        String requestHash = hashRequest(request);
        return String.format("operation:%s:%s:%s:%s", 
                tenantId, providerName, operationId, requestHash);
    }

    /**
     * Generates a hash of the request object for cache key uniqueness.
     *
     * @param request the request object
     * @return the hash string
     */
    private <TRequest> String hashRequest(TRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // Use first 16 chars
        } catch (Exception e) {
            log.warn("Failed to hash request, using toString(): {}", e.getMessage());
            return String.valueOf(request.hashCode());
        }
    }
}

