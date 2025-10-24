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

import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.cache.core.CacheType;
import com.firefly.common.data.config.DataEnrichmentProperties;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EnrichmentCacheService.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentCacheServiceTest {

    @Mock
    private CacheAdapter cacheAdapter;

    @Mock
    private EnrichmentCacheKeyGenerator keyGenerator;

    private EnrichmentCacheService cacheService;
    private DataEnrichmentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DataEnrichmentProperties();
        properties.setCacheEnabled(true);
        properties.setCacheTtlSeconds(3600);

        // Setup lenient mock for getCacheType (called in constructor)
        lenient().when(cacheAdapter.getCacheType()).thenReturn(CacheType.CAFFEINE);

        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);
    }

    @Test
    void get_shouldReturnEmptyWhenCacheDisabled() {
        // Given
        properties.setCacheEnabled(false);
        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);

        EnrichmentRequest request = createRequest("12345", "tenant-abc");

        // When
        Mono<Optional<EnrichmentResponse>> result = cacheService.get(request, "TestProvider");

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();

        // Verify no get/put operations on cache adapter (getCacheType is called in constructor)
        verify(cacheAdapter, never()).get(anyString(), any());
        verify(keyGenerator, never()).generateKey(any(), anyString());
    }

    @Test
    void get_shouldReturnCachedValueWhenPresent() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse cachedResponse = createResponse(true);
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.get(eq("cache-key-123"), eq(EnrichmentResponse.class)))
                .thenReturn(Mono.just(Optional.of(cachedResponse)));

        // When
        Mono<Optional<EnrichmentResponse>> result = cacheService.get(request, "TestProvider");

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get()).isEqualTo(cachedResponse);
                })
                .verifyComplete();
        
        verify(keyGenerator).generateKey(request, "TestProvider");
        verify(cacheAdapter).get("cache-key-123", EnrichmentResponse.class);
    }

    @Test
    void get_shouldReturnEmptyWhenNotInCache() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.get(eq("cache-key-123"), eq(EnrichmentResponse.class)))
                .thenReturn(Mono.just(Optional.empty()));

        // When
        Mono<Optional<EnrichmentResponse>> result = cacheService.get(request, "TestProvider");

        // Then
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void put_shouldNotCacheWhenCacheDisabled() {
        // Given
        properties.setCacheEnabled(false);
        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);

        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse response = createResponse(true);

        // When
        Mono<Void> result = cacheService.put(request, "TestProvider", response);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        // Verify no put operations on cache adapter (getCacheType is called in constructor)
        verify(cacheAdapter, never()).put(anyString(), any(), any(Duration.class));
        verify(keyGenerator, never()).generateKey(any(), anyString());
    }

    @Test
    void put_shouldCacheSuccessfulResponse() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse response = createResponse(true);
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.put(eq("cache-key-123"), eq(response), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheService.put(request, "TestProvider", response);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(keyGenerator).generateKey(request, "TestProvider");
        verify(cacheAdapter).put(eq("cache-key-123"), eq(response), eq(Duration.ofSeconds(3600)));
    }

    @Test
    void put_shouldNotCacheFailedResponse() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse response = createResponse(false);

        // When
        Mono<Void> result = cacheService.put(request, "TestProvider", response);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        // Verify no put operations on cache adapter (getCacheType is called in constructor)
        verify(cacheAdapter, never()).put(anyString(), any(), any(Duration.class));
        verify(keyGenerator, never()).generateKey(any(), anyString());
    }

    @Test
    void evict_shouldEvictCacheKey() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");

        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.evict("cache-key-123")).thenReturn(Mono.just(true));

        // When
        Mono<Boolean> result = cacheService.evict(request, "TestProvider");

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();

        verify(keyGenerator).generateKey(request, "TestProvider");
        verify(cacheAdapter).evict("cache-key-123");
    }

    @Test
    void evictTenant_shouldClearCacheForTenant() {
        // Given
        when(keyGenerator.generateTenantPattern("tenant-abc")).thenReturn("enrichment:tenant-abc:*");
        when(cacheAdapter.clear()).thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheService.evictTenant("tenant-abc");

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(keyGenerator).generateTenantPattern("tenant-abc");
        verify(cacheAdapter).clear();
    }

    @Test
    void clearAll_shouldClearAllCache() {
        // Given
        when(cacheAdapter.clear()).thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheService.clearAll();

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cacheAdapter).clear();
    }

    @Test
    void isCacheEnabled_shouldReturnTrueWhenEnabled() {
        // When
        boolean enabled = cacheService.isCacheEnabled();

        // Then
        assertThat(enabled).isTrue();
    }

    @Test
    void isCacheEnabled_shouldReturnFalseWhenDisabled() {
        // Given
        properties.setCacheEnabled(false);
        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);

        // When
        boolean enabled = cacheService.isCacheEnabled();

        // Then
        assertThat(enabled).isFalse();
    }

    @Test
    void put_shouldUseConfiguredTtl() {
        // Given
        properties.setCacheTtlSeconds(7200); // 2 hours
        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);
        
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse response = createResponse(true);
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.put(eq("cache-key-123"), eq(response), any(Duration.class)))
                .thenReturn(Mono.empty());

        // When
        Mono<Void> result = cacheService.put(request, "TestProvider", response);

        // Then
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(cacheAdapter).put(eq("cache-key-123"), eq(response), eq(Duration.ofSeconds(7200)));
    }

    @Test
    void get_shouldHandleCacheAdapterError() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.get(eq("cache-key-123"), eq(EnrichmentResponse.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Optional<EnrichmentResponse>> result = cacheService.get(request, "TestProvider");

        // Then - Should return empty on error (fail-safe)
        StepVerifier.create(result)
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    void put_shouldHandleCacheAdapterError() {
        // Given
        EnrichmentRequest request = createRequest("12345", "tenant-abc");
        EnrichmentResponse response = createResponse(true);
        
        when(keyGenerator.generateKey(request, "TestProvider")).thenReturn("cache-key-123");
        when(cacheAdapter.put(eq("cache-key-123"), eq(response), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("Cache error")));

        // When
        Mono<Void> result = cacheService.put(request, "TestProvider", response);

        // Then - Should complete without error (fail-safe)
        StepVerifier.create(result)
                .verifyComplete();
    }

    private EnrichmentRequest createRequest(String companyId, String tenantId) {
        return EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .parameters(Map.of("companyId", companyId))
                .tenantId(tenantId)
                .build();
    }

    private EnrichmentResponse createResponse(boolean success) {
        return EnrichmentResponse.builder()
                .success(success)
                .enrichedData(Map.of("companyId", "12345", "name", "Test Company"))
                .providerName("TestProvider")
                .enrichmentType("company-profile")
                .message(success ? "Success" : "Failed")
                .build();
    }
}

