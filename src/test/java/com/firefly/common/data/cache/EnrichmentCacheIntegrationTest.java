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
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheAdapter;
import com.firefly.common.cache.adapter.caffeine.CaffeineCacheConfig;
import com.firefly.common.cache.core.CacheAdapter;
import com.firefly.common.data.config.DataEnrichmentProperties;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataEnricher;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for enrichment caching with tenant isolation and batch processing.
 */
@ExtendWith(MockitoExtension.class)
class EnrichmentCacheIntegrationTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private CacheAdapter cacheAdapter;
    private EnrichmentCacheKeyGenerator keyGenerator;
    private EnrichmentCacheService cacheService;
    private TestCachedEnricher enricher;
    private AtomicInteger providerCallCount;

    @BeforeEach
    void setUp() {
        // Setup cache
        CaffeineCacheConfig cacheConfig = CaffeineCacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats(true)
                .build();
        cacheAdapter = new CaffeineCacheAdapter("enrichment-test-cache", cacheConfig);
        
        // Setup cache service
        keyGenerator = new EnrichmentCacheKeyGenerator(new ObjectMapper());
        DataEnrichmentProperties properties = new DataEnrichmentProperties();
        properties.setCacheEnabled(true);
        properties.setCacheTtlSeconds(600);
        properties.setMaxBatchSize(100);
        properties.setBatchParallelism(10);
        cacheService = new EnrichmentCacheService(cacheAdapter, keyGenerator, properties);
        
        // Setup enricher with cache
        providerCallCount = new AtomicInteger(0);
        enricher = new TestCachedEnricher(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                cacheService,
                providerCallCount
        );

        // Setup default mock behaviors
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enrich_shouldCacheSuccessfulResponses() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .tenantId("tenant-abc")
                .build();

        // When - First call (cache miss)
        Mono<EnrichmentResponse> firstCall = enricher.enrich(request);
        
        // Then - Should call provider
        StepVerifier.create(firstCall)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(providerCallCount.get()).isEqualTo(1);
                })
                .verifyComplete();

        // When - Second call (cache hit)
        Mono<EnrichmentResponse> secondCall = enricher.enrich(request);
        
        // Then - Should NOT call provider again
        StepVerifier.create(secondCall)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(providerCallCount.get()).isEqualTo(1); // Still 1, not 2!
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldIsolateCacheByTenant() {
        // Given - Same parameters, different tenants
        EnrichmentRequest tenant1Request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .tenantId("tenant-abc")
                .build();

        EnrichmentRequest tenant2Request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .tenantId("tenant-xyz")
                .build();

        // When - Call for tenant 1
        StepVerifier.create(enricher.enrich(tenant1Request))
                .assertNext(response -> assertThat(response.isSuccess()).isTrue())
                .verifyComplete();
        
        int callsAfterTenant1 = providerCallCount.get();
        assertThat(callsAfterTenant1).isEqualTo(1);

        // When - Call for tenant 2 with same parameters
        StepVerifier.create(enricher.enrich(tenant2Request))
                .assertNext(response -> assertThat(response.isSuccess()).isTrue())
                .verifyComplete();

        // Then - Should call provider again (different tenant = different cache key)
        assertThat(providerCallCount.get()).isEqualTo(2);
    }

    @Test
    void enrichBatch_shouldProcessMultipleRequestsInParallel() {
        // Given - Batch of 5 requests
        List<EnrichmentRequest> requests = List.of(
                createRequest("12345", "tenant-abc"),
                createRequest("67890", "tenant-abc"),
                createRequest("11111", "tenant-abc"),
                createRequest("22222", "tenant-abc"),
                createRequest("33333", "tenant-abc")
        );

        // When
        Flux<EnrichmentResponse> results = enricher.enrichBatch(requests);

        // Then - Should process all requests
        StepVerifier.create(results)
                .expectNextCount(5)
                .verifyComplete();
        
        assertThat(providerCallCount.get()).isEqualTo(5);
    }

    @Test
    void enrichBatch_shouldUseCacheForDuplicateRequests() {
        // Given - Batch with duplicate requests
        List<EnrichmentRequest> requests = List.of(
                createRequest("12345", "tenant-abc"),
                createRequest("12345", "tenant-abc"), // Duplicate
                createRequest("67890", "tenant-abc"),
                createRequest("12345", "tenant-abc"), // Duplicate
                createRequest("67890", "tenant-abc")  // Duplicate
        );

        // When
        Flux<EnrichmentResponse> results = enricher.enrichBatch(requests);

        // Then - Should only call provider for unique requests
        StepVerifier.create(results)
                .expectNextCount(5)
                .verifyComplete();
        
        // Only 2 unique requests, so only 2 provider calls
        assertThat(providerCallCount.get()).isEqualTo(2);
    }

    @Test
    void enrichBatch_shouldIsolateCacheByTenantInBatch() {
        // Given - Same companyId, different tenants
        List<EnrichmentRequest> requests = List.of(
                createRequest("12345", "tenant-abc"),
                createRequest("12345", "tenant-xyz"),
                createRequest("12345", "tenant-abc"), // Duplicate for tenant-abc
                createRequest("12345", "tenant-xyz")  // Duplicate for tenant-xyz
        );

        // When
        Flux<EnrichmentResponse> results = enricher.enrichBatch(requests);

        // Then - Should call provider once per tenant (2 unique tenant+companyId combinations)
        StepVerifier.create(results)
                .expectNextCount(4)
                .verifyComplete();
        
        assertThat(providerCallCount.get()).isEqualTo(2);
    }

    private EnrichmentRequest createRequest(String companyId, String tenantId) {
        return EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", companyId))
                .tenantId(tenantId)
                .build();
    }

    // Test enricher that counts provider calls
    static class TestCachedEnricher extends AbstractResilientDataEnricher {
        private final AtomicInteger callCount;

        public TestCachedEnricher(JobTracingService tracingService,
                                 JobMetricsService metricsService,
                                 ResiliencyDecoratorService resiliencyService,
                                 EnrichmentEventPublisher eventPublisher,
                                 EnrichmentCacheService cacheService,
                                 AtomicInteger callCount) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, cacheService);
            this.callCount = callCount;
        }

        @Override
        protected Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
            // Increment call count to track provider calls
            callCount.incrementAndGet();
            
            // Simulate provider call
            return Mono.just(EnrichmentResponse.builder()
                    .success(true)
                    .enrichedData(Map.of("companyId", request.param("companyId"), "name", "Test Company"))
                    .providerName(getProviderName())
                    .enrichmentType(request.getEnrichmentType())
                    .message("Enrichment successful")
                    .build());
        }

        @Override
        public String getProviderName() {
            return "Test Provider";
        }
    }
}

