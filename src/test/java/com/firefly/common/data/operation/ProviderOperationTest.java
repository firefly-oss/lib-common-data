package com.firefly.common.data.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.operation.dto.CompanySearchRequest;
import com.firefly.common.data.operation.dto.CompanySearchResponse;
import com.firefly.common.data.operation.schema.JsonSchemaGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for provider operations.
 */
class ProviderOperationTest {

    private SearchCompanyOperation operation;
    private ObjectMapper objectMapper;
    private JsonSchemaGenerator schemaGenerator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        schemaGenerator = new JsonSchemaGenerator(objectMapper);
        operation = new SearchCompanyOperation();

        // Manually inject dependencies (since we're not using Spring context in this test)
        operation.setSchemaGenerator(schemaGenerator);
        operation.setObjectMapper(objectMapper);

        // Initialize metadata
        operation.initializeMetadata();
    }

    @Test
    void testOperationMetadata() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        assertThat(metadata).isNotNull();
        assertThat(metadata.getOperationId()).isEqualTo("search-company");
        assertThat(metadata.getDescription()).isEqualTo("Search for a company by name or tax ID to obtain provider internal ID");
        assertThat(metadata.getMethod()).isEqualTo(RequestMethod.GET);
        assertThat(metadata.getTags()).containsExactly("lookup", "search");
        assertThat(metadata.isRequiresAuth()).isTrue();
        assertThat(metadata.isDiscoverable()).isTrue();
    }

    @Test
    void testOperationTypes() {
        assertThat(operation.getRequestType()).isEqualTo(CompanySearchRequest.class);
        assertThat(operation.getResponseType()).isEqualTo(CompanySearchResponse.class);
    }

    @Test
    void testOperationMetadataPath() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        assertThat(metadata.getPath()).isEqualTo("/search-company");
        assertThat(metadata.getFullPath("/api/v1/enrichment/credit-bureau"))
            .isEqualTo("/api/v1/enrichment/credit-bureau/search-company");
    }

    @Test
    void testOperationMetadataHttpMethod() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        assertThat(metadata.getHttpMethod()).isEqualTo("GET");
    }

    @Test
    void testOperationMetadataTypeNames() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        assertThat(metadata.getRequestTypeName()).isEqualTo("CompanySearchRequest");
        assertThat(metadata.getResponseTypeName()).isEqualTo("CompanySearchResponse");
    }

    @Test
    void testOperationExecutionWithCompanyName() {
        CompanySearchRequest request = CompanySearchRequest.builder()
            .companyName("Acme Corp")
            .build();

        Mono<CompanySearchResponse> result = operation.execute(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getProviderId()).isEqualTo("PROV-12345");
                assertThat(response.getCompanyName()).isEqualTo("ACME CORP");
                assertThat(response.getConfidence()).isEqualTo(0.95);
            })
            .verifyComplete();
    }

    @Test
    void testOperationExecutionWithTaxId() {
        CompanySearchRequest request = CompanySearchRequest.builder()
            .taxId("TAX-12345678")
            .build();

        Mono<CompanySearchResponse> result = operation.execute(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getProviderId()).isEqualTo("PROV-12345");
                assertThat(response.getTaxId()).isEqualTo("TAX-12345678");
                assertThat(response.getConfidence()).isEqualTo(0.95);
            })
            .verifyComplete();
    }

    @Test
    void testOperationExecutionWithBothParameters() {
        CompanySearchRequest request = CompanySearchRequest.builder()
            .companyName("Acme Corp")
            .taxId("TAX-12345678")
            .minConfidence(0.8)
            .build();

        Mono<CompanySearchResponse> result = operation.execute(request);

        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getProviderId()).isEqualTo("PROV-12345");
                assertThat(response.getCompanyName()).isEqualTo("ACME CORP");
                assertThat(response.getTaxId()).isEqualTo("TAX-12345678");
                assertThat(response.getConfidence()).isEqualTo(0.95);
            })
            .verifyComplete();
    }

    @Test
    void testOperationValidationFailsWithNoParameters() {
        CompanySearchRequest request = CompanySearchRequest.builder().build();

        Mono<CompanySearchResponse> result = operation.execute(request);

        StepVerifier.create(result)
            .expectErrorMatches(error -> 
                error instanceof IllegalArgumentException &&
                error.getMessage().contains("Either companyName or taxId must be provided"))
            .verify();
    }

    @Test
    void testOperationValidationFailsWithInvalidConfidence() {
        CompanySearchRequest request = CompanySearchRequest.builder()
            .companyName("Acme Corp")
            .minConfidence(1.5)
            .build();

        Mono<CompanySearchResponse> result = operation.execute(request);

        StepVerifier.create(result)
            .expectErrorMatches(error -> 
                error instanceof IllegalArgumentException &&
                error.getMessage().contains("minConfidence must be between 0 and 1"))
            .verify();
    }

    @Test
    void testJsonSchemaGeneration() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        assertThat(metadata.getRequestSchema()).isNotNull();
        assertThat(metadata.getResponseSchema()).isNotNull();
    }

    @Test
    void testExampleGeneration() {
        ProviderOperationMetadata metadata = operation.getMetadata();
        
        // Examples should be generated from @Schema annotations
        assertThat(metadata.getRequestExample()).isNotNull();
        assertThat(metadata.getResponseExample()).isNotNull();
    }
}

