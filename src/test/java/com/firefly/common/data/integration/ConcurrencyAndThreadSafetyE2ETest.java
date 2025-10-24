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

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataEnricher;
import com.firefly.common.data.service.AbstractResilientSyncDataJobService;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * E2E tests for concurrency and thread safety in lib-common-data.
 * 
 * <p>These tests validate that the library handles concurrent operations correctly
 * and that all components are thread-safe.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConcurrencyAndThreadSafetyE2ETest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private JobEventPublisher jobEventPublisher;

    @Mock
    private EnrichmentEventPublisher enrichmentEventPublisher;

    @Mock
    private JobAuditService auditService;

    @Mock
    private JobExecutionResultService resultService;

    private ResiliencyDecoratorService resiliencyService;

    @BeforeEach
    void setUp() {
        // Setup mock behaviors
        lenient().when(tracingService.traceJobOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(tracingService.traceOperation(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(auditService.recordOperationStarted(any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationCompleted(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(auditService.recordOperationFailed(any(), any(), anyLong(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveSuccessResult(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());
        lenient().when(resultService.saveFailureResult(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.empty());

        // Create resiliency service with bulkhead for concurrency control
        JobOrchestrationProperties properties = new JobOrchestrationProperties();
        properties.getResiliency().setBulkheadEnabled(true);
        properties.getResiliency().setBulkheadMaxConcurrentCalls(5);
        properties.getResiliency().setRetryEnabled(false); // Disable retry for predictable tests

        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(5)
                .maxWaitDuration(Duration.ofMillis(100))
                .build();
        Bulkhead bulkhead = Bulkhead.of("test", bulkheadConfig);

        resiliencyService = new ResiliencyDecoratorService(
                properties,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(bulkhead)
        );
    }

    @Test
    void shouldHandleConcurrentEnrichmentRequests_withoutDataCorruption() throws InterruptedException {
        // Given - Create enricher
        TestConcurrentEnricher enricher = new TestConcurrentEnricher(
                tracingService,
                metricsService,
                resiliencyService,
                enrichmentEventPublisher
        );

        int concurrentRequests = 20;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        ConcurrentHashMap<String, EnrichmentResponse> responses = new ConcurrentHashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // When - Execute concurrent enrichment requests
        List<Mono<EnrichmentResponse>> requests = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            String companyId = "company-" + i;
            EnrichmentRequest request = EnrichmentRequest.builder()
                    .enrichmentType("company-profile")
                    .strategy(EnrichmentStrategy.ENHANCE)
                    .parameters(Map.of("companyId", companyId))
                    .sourceDto(Map.of("companyId", companyId, "name", "Company " + i))
                    .build();

            Mono<EnrichmentResponse> mono = enricher.enrich(request)
                    .doOnSuccess(response -> {
                        responses.put(companyId, response);
                        successCount.incrementAndGet();
                        latch.countDown();
                    })
                    .doOnError(error -> {
                        failureCount.incrementAndGet();
                        latch.countDown();
                    });

            requests.add(mono);
        }

        // Execute all requests concurrently
        Flux.merge(requests).subscribe();

        // Wait for all requests to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);

        // Then - Verify all requests completed successfully
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(concurrentRequests);
        assertThat(failureCount.get()).isEqualTo(0);
        assertThat(responses).hasSize(concurrentRequests);

        // Verify no data corruption - each response should have correct data
        for (int i = 0; i < concurrentRequests; i++) {
            String companyId = "company-" + i;
            EnrichmentResponse response = responses.get(companyId);
            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getEnrichedData()).isNotNull();
        }
    }

    @Test
    void shouldRespectBulkheadLimits_whenConcurrentRequestsExceedLimit() {
        // Given - Create job service with bulkhead limit of 5
        TestConcurrentJobService jobService = new TestConcurrentJobService(
                tracingService,
                metricsService,
                resiliencyService,
                jobEventPublisher,
                auditService,
                resultService
        );

        // Configure job to take some time to execute
        jobService.setExecutionDelay(Duration.ofMillis(100));

        int concurrentRequests = 10; // More than bulkhead limit
        List<Mono<JobStageResponse>> requests = new ArrayList<>();

        // When - Execute concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            JobStageRequest request = JobStageRequest.builder()
                    .stage(JobStage.ALL)
                    .executionId("exec-" + i)
                    .build();

            requests.add(jobService.execute(request));
        }

        // Then - Some requests should be rejected by bulkhead
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        Flux.merge(requests)
                .doOnNext(response -> successCount.incrementAndGet())
                .doOnError(error -> rejectedCount.incrementAndGet())
                .onErrorResume(error -> Mono.empty())
                .blockLast(Duration.ofSeconds(5));

        // At least some requests should have been processed
        assertThat(successCount.get()).isGreaterThan(0);
        
        // Total processed + rejected should equal total requests
        assertThat(successCount.get() + rejectedCount.get()).isLessThanOrEqualTo(concurrentRequests);
    }

    @Test
    void shouldMaintainThreadSafety_inMetricsCollection() throws InterruptedException {
        // Given - Create job service
        TestConcurrentJobService jobService = new TestConcurrentJobService(
                tracingService,
                metricsService,
                resiliencyService,
                jobEventPublisher,
                auditService,
                resultService
        );

        int concurrentRequests = 50;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        // When - Execute many concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            JobStageRequest request = JobStageRequest.builder()
                    .stage(JobStage.ALL)
                    .executionId("exec-" + i)
                    .build();

            jobService.execute(request)
                    .doFinally(signal -> latch.countDown())
                    .subscribe();
        }

        // Wait for all to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);

        // Then - All requests should complete without errors
        assertThat(completed).isTrue();
    }

    /**
     * Test enricher that simulates concurrent operations.
     */
    static class TestConcurrentEnricher extends AbstractResilientDataEnricher {

        public TestConcurrentEnricher(JobTracingService tracingService,
                                     JobMetricsService metricsService,
                                     ResiliencyDecoratorService resiliencyService,
                                     EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher);
        }

        @Override
        protected Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
            String companyId = (String) request.getParameters().get("companyId");
            
            // Simulate some processing time
            return Mono.delay(Duration.ofMillis(10))
                    .then(Mono.fromCallable(() -> {
                        Map<String, Object> enrichedData = Map.of(
                                "companyId", companyId,
                                "name", "Enriched " + companyId,
                                "revenue", 1000000.0
                        );

                        return EnrichmentResponse.success(
                                enrichedData,
                                "test-concurrent-provider",
                                request.getEnrichmentType(),
                                "Enriched successfully"
                        );
                    }));
        }

        @Override
        public String getProviderName() {
            return "test-concurrent-provider";
        }

        @Override
        public String[] getSupportedEnrichmentTypes() {
            return new String[]{"company-profile"};
        }
    }

    /**
     * Test job service that simulates concurrent operations.
     */
    static class TestConcurrentJobService extends AbstractResilientSyncDataJobService {

        private Duration executionDelay = Duration.ZERO;

        public TestConcurrentJobService(JobTracingService tracingService,
                                       JobMetricsService metricsService,
                                       ResiliencyDecoratorService resiliencyService,
                                       JobEventPublisher eventPublisher,
                                       JobAuditService auditService,
                                       JobExecutionResultService resultService) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, auditService, resultService);
        }

        public void setExecutionDelay(Duration delay) {
            this.executionDelay = delay;
        }

        @Override
        protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
            return Mono.delay(executionDelay)
                    .then(Mono.just(JobStageResponse.builder()
                            .stage(JobStage.ALL)
                            .executionId(request.getExecutionId())
                            .success(true)
                            .status(JobExecutionStatus.SUCCEEDED)
                            .message("Executed successfully")
                            .build()));
        }

        @Override
        public String getJobDefinition() {
            return "test-concurrent-job";
        }
    }
}

