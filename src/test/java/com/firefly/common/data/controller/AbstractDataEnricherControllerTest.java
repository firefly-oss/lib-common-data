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

import com.firefly.common.data.model.EnrichmentApiRequest;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AbstractDataEnricherController.
 */
@ExtendWith(MockitoExtension.class)
class AbstractDataEnricherControllerTest {

    @Mock
    private DataEnricher enricher;

    @Mock
    private DataEnricherRegistry registry;

    private TestDataEnricherController controller;

    @BeforeEach
    void setUp() {
        controller = new TestDataEnricherController(enricher, registry);
        lenient().when(enricher.getProviderName()).thenReturn("Test Provider");
        lenient().when(enricher.getSupportedEnrichmentTypes()).thenReturn(new String[]{"test-type"});
    }

    @Test
    void enrich_shouldReturnSuccessResponse_whenEnrichmentSucceeds() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .tenantId("tenant-001")
                .requestId("req-001")
                .build();

        EnrichmentResponse enrichmentResponse = EnrichmentResponse.builder()
                .success(true)
                .enrichedData(Map.of("companyId", "12345", "name", "Acme Corp"))
                .providerName("Test Provider")
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .message("Enrichment successful")
                .fieldsEnriched(2)
                .requestId("req-001")
                .build();

        when(enricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(enrichmentResponse));

        // When & Then
        StepVerifier.create(controller.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getProviderName()).isEqualTo("Test Provider");
                    assertThat(response.getFieldsEnriched()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldReturnErrorResponse_whenEnrichmentFails() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        EnrichmentResponse enrichmentResponse = EnrichmentResponse.builder()
                .success(false)
                .message("Provider unavailable")
                .error("Connection timeout")
                .providerName("Test Provider")
                .enrichmentType("company-profile")
                .build();

        when(enricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(enrichmentResponse));

        // When & Then
        StepVerifier.create(controller.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getMessage()).isEqualTo("Provider unavailable");
                    assertThat(response.getError()).isEqualTo("Connection timeout");
                })
                .verifyComplete();
    }

    // listProviders() has been removed from AbstractDataEnricherController
    // Provider discovery is now handled by EnrichmentDiscoveryController
    // See EnrichmentDiscoveryControllerTest for tests

    @Test
    void checkHealth_shouldReturnHealthy_whenEnricherIsReady() {
        // Given
        when(enricher.isReady()).thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(controller.checkHealth())
                .assertNext(response -> {
                    assertThat(response.healthy()).isTrue();
                    assertThat(response.providerName()).isEqualTo("Test Provider");
                    assertThat(response.supportedTypes()).containsExactly("test-type");
                })
                .verifyComplete();
    }

    @Test
    void checkHealth_shouldReturnUnhealthy_whenEnricherIsNotReady() {
        // Given
        when(enricher.isReady()).thenReturn(Mono.just(false));

        // When & Then
        StepVerifier.create(controller.checkHealth())
                .assertNext(response -> {
                    assertThat(response.healthy()).isFalse();
                    assertThat(response.providerName()).isEqualTo("Test Provider");
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldGenerateRequestId_whenNotProvided() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build(); // No requestId

        EnrichmentResponse enrichmentResponse = EnrichmentResponse.builder()
                .success(true)
                .enrichedData(Map.of("companyId", "12345"))
                .providerName("Test Provider")
                .enrichmentType("company-profile")
                .requestId("generated-id")
                .build();

        when(enricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(enrichmentResponse));

        // When & Then
        StepVerifier.create(controller.enrich(request))
                .assertNext(response -> {
                    assertThat(response.getRequestId()).isNotNull();
                    assertThat(response.getRequestId()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void enrich_shouldHandleReactiveError_whenEnricherThrowsException() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        when(enricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Provider connection failed")));

        // When & Then
        StepVerifier.create(controller.enrich(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void enrich_shouldPreserveAllRequestFields_whenConverting() {
        // Given
        EnrichmentApiRequest request = EnrichmentApiRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .sourceDto(Map.of("companyId", "12345"))
                .parameters(Map.of("includeFinancials", true))
                .tenantId("tenant-001")
                .requestId("req-001")
                .initiator("user@example.com")
                .metadata(Map.of("source", "api"))
                .targetDtoClass("com.example.CompanyDTO")
                .timeoutMillis(5000L)
                .build();

        EnrichmentResponse enrichmentResponse = EnrichmentResponse.builder()
                .success(true)
                .enrichedData(Map.of("companyId", "12345", "name", "Acme"))
                .providerName("Test Provider")
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.MERGE)
                .requestId("req-001")
                .build();

        when(enricher.enrich(any(EnrichmentRequest.class)))
                .thenReturn(Mono.just(enrichmentResponse));

        // When & Then
        StepVerifier.create(controller.enrich(request))
                .assertNext(response -> {
                    assertThat(response.getRequestId()).isEqualTo("req-001");
                    assertThat(response.getStrategy()).isEqualTo(EnrichmentStrategy.MERGE);
                })
                .verifyComplete();
    }

    // listProviders() test removed - see EnrichmentDiscoveryControllerTest

    @Test
    void listOperations_shouldReturnOperations_whenEnricherImplementsCatalog() {
        // Given
        DataEnricher enricherWithOps = new TestEnricherWithOperations();
        TestDataEnricherController controllerWithOps = new TestDataEnricherController(enricherWithOps, registry);

        // When & Then
        StepVerifier.create(controllerWithOps.listOperations())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("providerName")).isEqualTo("Test Provider With Ops");

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("operations");
                    assertThat(operations).hasSize(2);

                    Map<String, Object> op1 = operations.get(0);
                    assertThat(op1.get("operationId")).isEqualTo("search-company");
                    assertThat(op1.get("method")).isEqualTo("GET");
                })
                .verifyComplete();
    }

    @Test
    void listOperations_shouldReturnEmptyList_whenEnricherDoesNotImplementCatalog() {
        // When & Then
        StepVerifier.create(controller.listOperations())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> operations = (List<Map<String, Object>>) body.get("operations");
                    assertThat(operations).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void executeProviderOperation_shouldExecuteOperation_whenEnricherImplementsCatalog() {
        // Given
        TestEnricherWithOperations enricherWithOps = new TestEnricherWithOperations();
        TestDataEnricherController controllerWithOps = new TestDataEnricherController(enricherWithOps, registry);

        Map<String, Object> params = Map.of("companyName", "Acme Corp");

        // When & Then
        StepVerifier.create(controllerWithOps.executeProviderOperation("search-company", params, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("result")).isEqualTo("found");
                    assertThat(body.get("companyName")).isEqualTo("Acme Corp");
                })
                .verifyComplete();
    }

    @Test
    void executeProviderOperation_shouldReturnError_whenOperationNotFound() {
        // Given
        TestEnricherWithOperations enricherWithOps = new TestEnricherWithOperations();
        TestDataEnricherController controllerWithOps = new TestDataEnricherController(enricherWithOps, registry);

        Map<String, Object> params = Map.of();

        // When & Then
        StepVerifier.create(controllerWithOps.executeProviderOperation("unknown-operation", params, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is4xxClientError()).isTrue();

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("error")).asString().contains("Unknown operation");
                })
                .verifyComplete();
    }

    @Test
    void executeProviderOperation_shouldReturnError_whenEnricherDoesNotSupportOperations() {
        // Given
        Map<String, Object> params = Map.of();

        // When & Then
        StepVerifier.create(controller.executeProviderOperation("any-operation", params, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is4xxClientError()).isTrue();

                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("error")).asString().contains("does not support custom operations");
                })
                .verifyComplete();
    }

    /**
     * Test implementation of AbstractDataEnricherController.
     */
    private static class TestDataEnricherController extends AbstractDataEnricherController {

        public TestDataEnricherController(DataEnricher enricher, DataEnricherRegistry registry) {
            super(enricher, registry);
        }
    }

    /**
     * Test enricher that implements ProviderOperationCatalog.
     */
    private static class TestEnricherWithOperations implements DataEnricher, ProviderOperationCatalog, EndpointAware {
        private String endpoint = "/api/v1/test/enrich";

        @Override
        public String getProviderName() {
            return "Test Provider With Ops";
        }

        @Override
        public String[] getSupportedEnrichmentTypes() {
            return new String[]{"test-type"};
        }

        @Override
        public String getEnricherDescription() {
            return "Test provider with operations";
        }

        @Override
        public String getEnrichmentEndpoint() {
            return endpoint;
        }

        @Override
        public void setEnrichmentEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
            return Mono.just(EnrichmentResponse.builder()
                .success(true)
                .providerName(getProviderName())
                .enrichmentType(request.getEnrichmentType())
                .strategy(request.getStrategy())
                .enrichedData(Map.of("test", "data"))
                .build());
        }

        @Override
        public List<ProviderOperation> getOperationCatalog() {
            return List.of(
                ProviderOperation.builder()
                    .operationId("search-company")
                    .path("/search-company")
                    .method(RequestMethod.GET)
                    .description("Search for a company")
                    .build(),
                ProviderOperation.builder()
                    .operationId("validate-id")
                    .path("/validate-id")
                    .method(RequestMethod.POST)
                    .description("Validate an ID")
                    .build()
            );
        }

        @Override
        public Mono<Map<String, Object>> executeOperation(String operationId, Map<String, Object> parameters) {
            return switch (operationId) {
                case "search-company" -> Mono.just(Map.of(
                    "result", "found",
                    "companyName", parameters.get("companyName")
                ));
                case "validate-id" -> Mono.just(Map.of(
                    "valid", true
                ));
                default -> Mono.error(new IllegalArgumentException("Unknown operation: " + operationId));
            };
        }
    }
}

