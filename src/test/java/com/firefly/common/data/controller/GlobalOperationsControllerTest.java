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

import com.firefly.common.data.controller.dto.OperationCatalogResponse;
import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.operation.AbstractEnricherOperation;
import com.firefly.common.data.operation.EnricherOperation;
import com.firefly.common.data.operation.EnricherOperationInterface;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricherRegistry;
import com.firefly.common.data.service.DataEnricher;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GlobalOperationsController}.
 */
@ExtendWith(MockitoExtension.class)
class GlobalOperationsControllerTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private DataEnricherRegistry registry;
    private GlobalOperationsController controller;

    private static final UUID SPAIN_TENANT = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private static final UUID USA_TENANT = UUID.fromString("660e8400-e29b-41d4-a716-446655440002");

    @BeforeEach
    void setUp() {
        // Registry will be created per test with specific enrichers
    }

    @Test
    void shouldListAllOperations() {
        // Given
        TestEnricherWithOperations enricher = new TestEnricherWithOperations(
                tracingService, metricsService, resiliencyService, eventPublisher);
        enricher.initializeMetadata();

        registry = new DataEnricherRegistry(List.of(enricher));
        controller = new GlobalOperationsController(registry);

        // When
        Mono<ResponseEntity<List<OperationCatalogResponse>>> result = controller.listOperations(null, null);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    List<OperationCatalogResponse> catalogs = response.getBody();
                    assertThat(catalogs).isNotNull();
                    assertThat(catalogs).hasSize(1);
                    
                    OperationCatalogResponse catalog = catalogs.get(0);
                    assertThat(catalog.getProviderName()).isEqualTo("Test Provider");
                    assertThat(catalog.getOperations()).hasSize(2);
                    
                    // Check search operation
                    OperationCatalogResponse.OperationInfo searchOp = catalog.getOperations().get(0);
                    assertThat(searchOp.getOperationId()).isEqualTo("search-company");
                    assertThat(searchOp.getDescription()).contains("Search for a company");
                    assertThat(searchOp.getMethod()).isEqualTo("POST");
                    assertThat(searchOp.getPath()).isEqualTo("/api/v1/enrichment/operations/execute");
                    assertThat(searchOp.getRequestType()).isEqualTo("CompanySearchRequest");
                    assertThat(searchOp.getResponseType()).isEqualTo("CompanySearchResponse");
                    
                    // Check validate operation
                    OperationCatalogResponse.OperationInfo validateOp = catalog.getOperations().get(1);
                    assertThat(validateOp.getOperationId()).isEqualTo("validate-tax-id");
                    assertThat(validateOp.getDescription()).contains("Validate");
                })
                .verifyComplete();
    }

    @Test
    void shouldFilterOperationsByType() {
        // Given
        TestEnricherWithOperations enricher1 = new TestEnricherWithOperations(
                tracingService, metricsService, resiliencyService, eventPublisher);
        enricher1.initializeMetadata();

        registry = new DataEnricherRegistry(List.of(enricher1));
        controller = new GlobalOperationsController(registry);

        // When
        Mono<ResponseEntity<List<OperationCatalogResponse>>> result = 
                controller.listOperations("credit-report", null);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    List<OperationCatalogResponse> catalogs = response.getBody();
                    assertThat(catalogs).isNotNull();
                    assertThat(catalogs).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void shouldFilterOperationsByTenant() {
        // Given
        TestEnricherWithOperations enricher1 = new TestEnricherWithOperations(
                tracingService, metricsService, resiliencyService, eventPublisher);
        enricher1.initializeMetadata();

        registry = new DataEnricherRegistry(List.of(enricher1));
        controller = new GlobalOperationsController(registry);

        // When
        Mono<ResponseEntity<List<OperationCatalogResponse>>> result = 
                controller.listOperations(null, SPAIN_TENANT);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    List<OperationCatalogResponse> catalogs = response.getBody();
                    assertThat(catalogs).isNotNull();
                    assertThat(catalogs).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void shouldExecuteOperation() {
        // Given
        TestEnricherWithOperations enricher = new TestEnricherWithOperations(
                tracingService, metricsService, resiliencyService, eventPublisher);
        enricher.initializeMetadata();

        registry = new DataEnricherRegistry(List.of(enricher));
        controller = new GlobalOperationsController(registry);

        GlobalOperationsController.OperationExecutionRequest request = 
                new GlobalOperationsController.OperationExecutionRequest();
        request.setType("credit-report");
        request.setTenantId(SPAIN_TENANT);
        request.setOperationId("search-company");
        
        CompanySearchRequest searchRequest = CompanySearchRequest.builder()
                .companyName("Acme Corp")
                .taxId("TAX-123")
                .build();
        request.setRequest(searchRequest);

        // When
        Mono<ResponseEntity<Object>> result = controller.executeOperation(request);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    Object body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body).isInstanceOf(CompanySearchResponse.class);
                    
                    CompanySearchResponse searchResponse = (CompanySearchResponse) body;
                    assertThat(searchResponse.getProviderId()).isEqualTo("PROV-12345");
                    assertThat(searchResponse.getCompanyName()).isEqualTo("ACME CORP");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyListWhenNoOperations() {
        // Given - enricher with no operations
        TestEnricherWithoutOperations enricher = new TestEnricherWithoutOperations(
                tracingService, metricsService, resiliencyService, eventPublisher);

        registry = new DataEnricherRegistry(List.of(enricher));
        controller = new GlobalOperationsController(registry);

        // When
        Mono<ResponseEntity<List<OperationCatalogResponse>>> result = controller.listOperations(null, null);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                    List<OperationCatalogResponse> catalogs = response.getBody();
                    assertThat(catalogs).isNotNull();
                    assertThat(catalogs).isEmpty();
                })
                .verifyComplete();
    }

    // Test DTOs
    @Data
    @Builder
    static class CompanySearchRequest {
        private String companyName;
        private String taxId;
    }

    @Data
    @Builder
    static class CompanySearchResponse {
        private String providerId;
        private String companyName;
        private String taxId;
        private Double confidence;
    }

    @Data
    @Builder
    static class TaxIdValidationRequest {
        private String taxId;
    }

    @Data
    @Builder
    static class TaxIdValidationResponse {
        private boolean valid;
        private String message;
    }

    // Test Operations
    @EnricherOperation(
        operationId = "search-company",
        description = "Search for a company by name or tax ID",
        method = RequestMethod.POST,
        tags = {"lookup", "search"}
    )
    static class TestSearchOperation extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> {
        @Override
        protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
            return Mono.just(CompanySearchResponse.builder()
                    .providerId("PROV-12345")
                    .companyName(request.getCompanyName() != null ? request.getCompanyName().toUpperCase() : "UNKNOWN")
                    .taxId(request.getTaxId())
                    .confidence(0.95)
                    .build());
        }
    }

    @EnricherOperation(
        operationId = "validate-tax-id",
        description = "Validate a tax ID",
        method = RequestMethod.POST,
        tags = {"validation"}
    )
    static class TestValidateOperation extends AbstractEnricherOperation<TaxIdValidationRequest, TaxIdValidationResponse> {
        @Override
        protected Mono<TaxIdValidationResponse> doExecute(TaxIdValidationRequest request) {
            return Mono.just(TaxIdValidationResponse.builder()
                    .valid(true)
                    .message("Tax ID is valid")
                    .build());
        }
    }

    // Test Enrichers
    @EnricherMetadata(
        providerName = "Test Provider",
        tenantId = "550e8400-e29b-41d4-a716-446655440001",
        type = "credit-report",
        priority = 100
    )
    static class TestEnricherWithOperations extends DataEnricher<Object, Object, Object> {
        private final TestSearchOperation searchOperation;
        private final TestValidateOperation validateOperation;

        public TestEnricherWithOperations(JobTracingService tracingService,
                                         JobMetricsService metricsService,
                                         ResiliencyDecoratorService resiliencyService,
                                         EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, Object.class);
            this.searchOperation = new TestSearchOperation();
            this.validateOperation = new TestValidateOperation();
        }

        public void initializeMetadata() {
            this.searchOperation.initializeMetadata();
            this.validateOperation.initializeMetadata();
        }

        @Override
        public List<EnricherOperationInterface<?, ?>> getOperations() {
            return List.of(searchOperation, validateOperation);
        }

        @Override
        protected Mono<Object> fetchProviderData(EnrichmentRequest request) {
            return Mono.just(new Object());
        }

        @Override
        protected Object mapToTarget(Object providerData) {
            return providerData;
        }
    }

    @EnricherMetadata(
        providerName = "Test Provider Without Operations",
        tenantId = "550e8400-e29b-41d4-a716-446655440001",
        type = "company-profile",
        priority = 100
    )
    static class TestEnricherWithoutOperations extends DataEnricher<Object, Object, Object> {
        public TestEnricherWithoutOperations(JobTracingService tracingService,
                                            JobMetricsService metricsService,
                                            ResiliencyDecoratorService resiliencyService,
                                            EnrichmentEventPublisher eventPublisher) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, Object.class);
        }

        @Override
        protected Mono<Object> fetchProviderData(EnrichmentRequest request) {
            return Mono.just(new Object());
        }

        @Override
        protected Object mapToTarget(Object providerData) {
            return providerData;
        }
    }
}

