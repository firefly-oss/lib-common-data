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

package com.firefly.common.data.integration;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * End-to-end tests for concurrency and thread safety of DataEnricher.
 * Verifies that enrichers can handle concurrent requests safely.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyAndThreadSafetyE2ETest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private TestConcurrentEnricher enricher;
    private AtomicInteger callCount;
    private ConcurrentHashMap<String, Integer> requestCounts;

    @BeforeEach
    void setUp() {
        callCount = new AtomicInteger(0);
        requestCounts = new ConcurrentHashMap<>();
        
        // Setup mocks to pass through - CRITICAL for tests to work
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        enricher = new TestConcurrentEnricher(
                tracingService,
                metricsService,
                resiliencyService,
                eventPublisher,
                callCount,
                requestCounts
        );
    }

    @Test
    void enrich_shouldHandleConcurrentRequests() {
        // Given - 100 concurrent requests
        int concurrentRequests = 100;
        
        List<EnrichmentRequest> requests = IntStream.range(0, concurrentRequests)
                .mapToObj(i -> {
                    CompanyDTO source = CompanyDTO.builder()
                            .companyId("company-" + i)
                            .build();
                    
                    return EnrichmentRequest.builder()
                            .type("company-profile")
                            .strategy(EnrichmentStrategy.ENHANCE)
                            .sourceDto(source)
                            .parameters(Map.of("companyId", "company-" + i))
                            .build();
                })
                .toList();

        // When - Execute all requests concurrently
        Flux<EnrichmentResponse> results = Flux.fromIterable(requests)
                .flatMap(request -> enricher.enrich(request), concurrentRequests);

        // Then - All requests should complete successfully
        StepVerifier.create(results)
                .expectNextCount(concurrentRequests)
                .verifyComplete();

        // Verify all requests were processed
        assertThat(callCount.get()).isEqualTo(concurrentRequests);
        assertThat(requestCounts.size()).isEqualTo(concurrentRequests);
    }

    @Test
    void enrich_shouldHandleHighThroughput() {
        // Given - 1000 requests with high concurrency
        int totalRequests = 1000;
        int concurrency = 50;
        
        List<EnrichmentRequest> requests = IntStream.range(0, totalRequests)
                .mapToObj(i -> {
                    CompanyDTO source = CompanyDTO.builder()
                            .companyId("company-" + (i % 100)) // Reuse some IDs
                            .build();
                    
                    return EnrichmentRequest.builder()
                            .type("company-profile")
                            .strategy(EnrichmentStrategy.ENHANCE)
                            .sourceDto(source)
                            .parameters(Map.of("companyId", "company-" + (i % 100)))
                            .build();
                })
                .toList();

        // When
        Flux<EnrichmentResponse> results = Flux.fromIterable(requests)
                .flatMap(request -> enricher.enrich(request), concurrency);

        // Then
        StepVerifier.create(results)
                .expectNextCount(totalRequests)
                .expectComplete()
                .verify(Duration.ofSeconds(30));

        assertThat(callCount.get()).isEqualTo(totalRequests);
    }

    @Test
    void enrich_shouldMaintainThreadSafety() {
        // Given - Multiple threads accessing the same enricher
        CompanyDTO source = CompanyDTO.builder()
                .companyId("12345")
                .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
                .type("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(source)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When - Execute same request 100 times concurrently
        Flux<EnrichmentResponse> results = Flux.range(0, 100)
                .flatMap(i -> enricher.enrich(request), 100);

        // Then - All should succeed
        StepVerifier.create(results)
                .expectNextCount(100)
                .verifyComplete();

        assertThat(callCount.get()).isEqualTo(100);
    }

    /**
     * Test DTO for concurrency tests.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class CompanyDTO {
        private String companyId;
        private String name;
        private String address;
    }

    /**
     * Test enricher that tracks concurrent access.
     */
    @EnricherMetadata(
        providerName = "Test Concurrent Provider",
        type = "company-profile",
        description = "Test enricher for concurrency testing"
    )
    static class TestConcurrentEnricher extends DataEnricher<CompanyDTO, Map<String, Object>, CompanyDTO> {

        private final AtomicInteger callCount;
        private final ConcurrentHashMap<String, Integer> requestCounts;

        public TestConcurrentEnricher(JobTracingService tracingService,
                                     JobMetricsService metricsService,
                                     ResiliencyDecoratorService resiliencyService,
                                     EnrichmentEventPublisher eventPublisher,
                                     AtomicInteger callCount,
                                     ConcurrentHashMap<String, Integer> requestCounts) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyDTO.class);
            this.callCount = callCount;
            this.requestCounts = requestCounts;
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.fromCallable(() -> {
                callCount.incrementAndGet();
                String companyId = request.requireParam("companyId");
                requestCounts.merge(companyId, 1, Integer::sum);
                
                // Simulate some processing time
                Thread.sleep(10);
                
                return Map.of(
                        "companyId", companyId,
                        "name", "Company " + companyId,
                        "address", "Address for " + companyId
                );
            });
        }

        @Override
        protected CompanyDTO mapToTarget(Map<String, Object> providerData) {
            return CompanyDTO.builder()
                    .companyId((String) providerData.get("companyId"))
                    .name((String) providerData.get("name"))
                    .address((String) providerData.get("address"))
                    .build();
        }
    }
}

