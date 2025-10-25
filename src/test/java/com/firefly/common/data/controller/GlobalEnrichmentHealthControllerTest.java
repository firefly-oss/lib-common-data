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

package com.firefly.common.data.controller;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricherRegistry;
import com.firefly.common.data.service.DataEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for GlobalEnrichmentHealthController.
 */
@ExtendWith(MockitoExtension.class)
class GlobalEnrichmentHealthControllerTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private DataEnricherRegistry registry;
    private GlobalEnrichmentHealthController controller;

    @BeforeEach
    void setUp() {
        // Create test enrichers
        TestCreditEnricher creditEnricher = new TestCreditEnricher(
                tracingService, metricsService, resiliencyService, eventPublisher);
        TestCompanyEnricher companyEnricher = new TestCompanyEnricher(
                tracingService, metricsService, resiliencyService, eventPublisher);

        // Create registry with test enrichers
        registry = new DataEnricherRegistry(List.of(creditEnricher, companyEnricher));
        controller = new GlobalEnrichmentHealthController(registry);
    }

    @Test
    void checkGlobalHealth_shouldReturnHealthyStatus() {
        // When
        Mono<GlobalEnrichmentHealthController.GlobalHealthResponse> result = controller.checkGlobalHealth(null, null);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.overallHealthy()).isTrue();
                    assertThat(response.totalEnrichers()).isEqualTo(2);
                    assertThat(response.enrichers()).hasSize(2);

                    // Verify credit enricher
                    var creditHealth = response.enrichers().stream()
                            .filter(e -> e.providerName().equals("Test Credit Provider"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(creditHealth.healthy()).isTrue();
                    assertThat(creditHealth.type()).isEqualTo("credit-report");

                    // Verify company enricher
                    var companyHealth = response.enrichers().stream()
                            .filter(e -> e.providerName().equals("Test Company Provider"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(companyHealth.healthy()).isTrue();
                    assertThat(companyHealth.type()).isEqualTo("company-profile");
                })
                .verifyComplete();
    }

    /**
     * Test enricher for credit reports.
     */
    @EnricherMetadata(
        providerName = "Test Credit Provider",
        type = "credit-report",
        description = "Test credit enricher"
    )
    static class TestCreditEnricher extends DataEnricher<Map<String, Object>, Map<String, Object>, Map<String, Object>> {

        @SuppressWarnings("unchecked")
        public TestCreditEnricher(JobTracingService tracingService,
                                 JobMetricsService metricsService,
                                 ResiliencyDecoratorService resiliencyService,
                                 EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, (Class<Map<String, Object>>) (Class<?>) Map.class);
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.just(Map.of("creditScore", 750));
        }

        @Override
        protected Map<String, Object> mapToTarget(Map<String, Object> providerData) {
            return providerData;
        }
    }

    /**
     * Test enricher for company profiles.
     */
    @EnricherMetadata(
        providerName = "Test Company Provider",
        type = "company-profile",
        description = "Test company enricher"
    )
    static class TestCompanyEnricher extends DataEnricher<Map<String, Object>, Map<String, Object>, Map<String, Object>> {

        @SuppressWarnings("unchecked")
        public TestCompanyEnricher(JobTracingService tracingService,
                                  JobMetricsService metricsService,
                                  ResiliencyDecoratorService resiliencyService,
                                  EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, (Class<Map<String, Object>>) (Class<?>) Map.class);
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.just(Map.of("companyName", "Acme Corp"));
        }

        @Override
        protected Map<String, Object> mapToTarget(Map<String, Object> providerData) {
            return providerData;
        }
    }
}

