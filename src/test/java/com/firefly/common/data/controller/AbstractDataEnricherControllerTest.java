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
import com.firefly.common.data.operation.dto.CompanySearchRequest;
import com.firefly.common.data.operation.dto.CompanySearchResponse;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import com.firefly.common.data.service.TypedDataEnricher;
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

                    com.firefly.common.data.controller.dto.OperationCatalogResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getProviderName()).isEqualTo("Test Provider With Ops");

                    List<com.firefly.common.data.controller.dto.OperationCatalogResponse.OperationInfo> operations = body.getOperations();
                    assertThat(operations).hasSize(2);

                    com.firefly.common.data.controller.dto.OperationCatalogResponse.OperationInfo op1 = operations.get(0);
                    assertThat(op1.getOperationId()).isEqualTo("search-company");
                    assertThat(op1.getMethod()).isEqualTo("GET");
                })
                .verifyComplete();
    }

    @Test
    void listOperations_shouldReturnEmptyList_whenEnricherDoesNotImplementCatalog() {
        // When & Then
        StepVerifier.create(controller.listOperations())
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    com.firefly.common.data.controller.dto.OperationCatalogResponse body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getProviderName()).isEqualTo("Test Provider");
                    assertThat(body.getOperations()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void executeProviderOperation_shouldExecuteOperation_whenEnricherImplementsCatalog() throws Exception {
        // Given
        TestEnricherWithOperations enricherWithOps = new TestEnricherWithOperations();
        TestDataEnricherController controllerWithOps = new TestDataEnricherController(enricherWithOps, registry);

        // Inject ObjectMapper using reflection
        java.lang.reflect.Field objectMapperField = AbstractDataEnricherController.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(controllerWithOps, new com.fasterxml.jackson.databind.ObjectMapper());

        Map<String, Object> params = Map.of("companyName", "Acme Corp");

        // When & Then
        StepVerifier.create(controllerWithOps.executeProviderOperation("search-company", params, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

                    CompanySearchResponse body = (CompanySearchResponse) response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getProviderId()).isEqualTo("PROV-123");
                    assertThat(body.getCompanyName()).isEqualTo("ACME CORP");
                    assertThat(body.getConfidence()).isEqualTo(0.95);
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

                    com.firefly.common.data.controller.dto.OperationErrorResponse body =
                        (com.firefly.common.data.controller.dto.OperationErrorResponse) response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getError()).contains("Operation not found");
                    assertThat(body.getOperationId()).isEqualTo("unknown-operation");
                    assertThat(body.getProviderName()).isEqualTo("Test Provider With Ops");
                    assertThat(body.getAvailableOperations()).hasSize(2);
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

                    com.firefly.common.data.controller.dto.OperationErrorResponse body =
                        (com.firefly.common.data.controller.dto.OperationErrorResponse) response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getError()).contains("does not support");
                    assertThat(body.getOperationId()).isEqualTo("any-operation");
                    assertThat(body.getProviderName()).isEqualTo("Test Provider");
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
     * Test enricher with operations - extends TypedDataEnricher to support operations.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class TestEnricherWithOperations
            extends TypedDataEnricher<Map<String, Object>, Map<String, Object>, Map<String, Object>> {

        private String endpoint = "/api/v1/test/enrich";
        private final List<com.firefly.common.data.operation.ProviderOperation<?, ?>> operations;

        public TestEnricherWithOperations() {
            // Call parent constructor with null dependencies (acceptable for tests)
            super(null, null, null, null, (Class) Map.class);

            // Create and initialize operations
            TestSearchOperation searchOp = new TestSearchOperation();
            TestValidateOperation validateOp = new TestValidateOperation();

            // Set dependencies manually (normally done by Spring)
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.firefly.common.data.operation.schema.JsonSchemaGenerator schemaGen =
                new com.firefly.common.data.operation.schema.JsonSchemaGenerator(mapper);

            searchOp.setSchemaGenerator(schemaGen);
            searchOp.setObjectMapper(mapper);
            searchOp.initializeMetadata();

            validateOp.setSchemaGenerator(schemaGen);
            validateOp.setObjectMapper(mapper);
            validateOp.initializeMetadata();

            this.operations = List.of(searchOp, validateOp);
        }

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
        public List<com.firefly.common.data.operation.ProviderOperation<?, ?>> getOperations() {
            return operations;
        }

        @Override
        protected Mono<Map<String, Object>> fetchProviderData(EnrichmentRequest request) {
            return Mono.just(Map.of("test", "data"));
        }

        @Override
        protected Map<String, Object> mapToTarget(Map<String, Object> providerData) {
            return providerData;
        }
    }

    /**
     * Test search operation.
     */
    @com.firefly.common.data.operation.ProviderCustomOperation(
        operationId = "search-company",
        description = "Search for a company",
        method = RequestMethod.GET
    )
    private static class TestSearchOperation
            extends com.firefly.common.data.operation.AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {

        @Override
        protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
            return Mono.just(CompanySearchResponse.builder()
                .providerId("PROV-123")
                .companyName(request.getCompanyName() != null ? request.getCompanyName().toUpperCase() : "UNKNOWN")
                .taxId(request.getTaxId())
                .confidence(0.95)
                .build());
        }
    }

    /**
     * Test validate operation.
     */
    @com.firefly.common.data.operation.ProviderCustomOperation(
        operationId = "validate-id",
        description = "Validate an ID",
        method = RequestMethod.POST
    )
    private static class TestValidateOperation
            extends com.firefly.common.data.operation.AbstractProviderOperation<Map<String, Object>, Map<String, Object>> {

        @Override
        protected Mono<Map<String, Object>> doExecute(Map<String, Object> request) {
            return Mono.just(Map.of("valid", true));
        }
    }
}

