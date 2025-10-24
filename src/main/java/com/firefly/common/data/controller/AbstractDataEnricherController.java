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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.config.DataEnrichmentProperties;
import com.firefly.common.data.controller.dto.OperationCatalogResponse;
import com.firefly.common.data.controller.dto.OperationErrorResponse;
import com.firefly.common.data.model.EnrichmentApiRequest;
import com.firefly.common.data.model.EnrichmentApiResponse;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.operation.ProviderOperation;
import com.firefly.common.data.operation.ProviderOperationMetadata;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import com.firefly.common.data.service.TypedDataEnricher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base controller implementation for data enrichment endpoints.
 * 
 * <p>This class provides comprehensive logging and request/response handling
 * for enrichment operations. It automatically:</p>
 * <ul>
 *   <li>Logs incoming HTTP requests with parameters</li>
 *   <li>Converts API DTOs to domain models</li>
 *   <li>Delegates execution to {@link DataEnricher}</li>
 *   <li>Converts domain models back to API DTOs</li>
 *   <li>Logs successful responses with enrichment details</li>
 *   <li>Logs error responses with error details</li>
 *   <li>Tracks request/response timing</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/enrichment/company-profile")
 * @Tag(name = "Data Enrichment - Company Profile", description = "Company profile enrichment endpoints")
 * public class CompanyProfileEnrichmentController extends AbstractDataEnricherController {
 *     
 *     public CompanyProfileEnrichmentController(
 *             @Qualifier("financialDataEnricher") DataEnricher enricher,
 *             DataEnricherRegistry registry) {
 *         super(enricher, registry);
 *     }
 * }
 * }</pre>
 * 
 * <p>The above example creates REST endpoints at:</p>
 * <pre>
 * POST /api/v1/enrichment/company-profile/enrich
 * GET  /api/v1/enrichment/company-profile/health
 * </pre>
 *
 * <p>For provider discovery, use the global endpoint provided by {@link EnrichmentDiscoveryController}:</p>
 * <pre>
 * GET  /api/v1/enrichment/providers
 * GET  /api/v1/enrichment/providers?enrichmentType=company-profile
 * </pre>
 * 
 * <p><b>Request Example:</b></p>
 * <pre>{@code
 * POST /api/v1/enrichment/company-profile/enrich
 * {
 *   "enrichmentType": "company-profile",
 *   "strategy": "ENHANCE",
 *   "sourceDto": {
 *     "companyId": "12345",
 *     "name": "Acme Corp"
 *   },
 *   "parameters": {
 *     "companyId": "12345"
 *   },
 *   "tenantId": "tenant-001",
 *   "requestId": "req-abc-123"
 * }
 * }</pre>
 * 
 * <p><b>Response Example:</b></p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "enrichedData": {
 *     "companyId": "12345",
 *     "name": "Acme Corp",
 *     "address": "123 Main St",
 *     "revenue": 1000000
 *   },
 *   "providerName": "Financial Data Provider",
 *   "enrichmentType": "company-profile",
 *   "strategy": "ENHANCE",
 *   "message": "Company data enriched successfully",
 *   "fieldsEnriched": 2,
 *   "timestamp": "2025-10-23T10:30:00Z"
 * }
 * }</pre>
 * 
 * @see DataEnricherController
 * @see DataEnricher
 */
@Slf4j
@RestController
public abstract class AbstractDataEnricherController implements DataEnricherController {

    private final DataEnricher dataEnricher;
    private final DataEnricherRegistry enricherRegistry;
    private final DataEnrichmentProperties enrichmentProperties;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * Constructor with enricher and registry dependencies.
     *
     * @param dataEnricher the data enricher implementation
     * @param enricherRegistry the enricher registry for listing providers
     * @param enrichmentProperties enrichment configuration properties
     */
    protected AbstractDataEnricherController(DataEnricher dataEnricher,
                                            DataEnricherRegistry enricherRegistry,
                                            DataEnrichmentProperties enrichmentProperties) {
        this.dataEnricher = dataEnricher;
        this.enricherRegistry = enricherRegistry;
        this.enrichmentProperties = enrichmentProperties;
    }

    /**
     * Constructor without enrichment properties for backward compatibility.
     */
    protected AbstractDataEnricherController(DataEnricher dataEnricher,
                                            DataEnricherRegistry enricherRegistry) {
        this(dataEnricher, enricherRegistry, null);
    }

    /**
     * Initializes the controller and registers the endpoint path with the enricher.
     *
     * <p>This method is called after dependency injection is complete. It extracts
     * the base path from the @RequestMapping annotation and constructs the full
     * endpoint path by appending "/enrich".</p>
     *
     * <p>The endpoint path is then set on the enricher via reflection if the enricher
     * has a setEnrichmentEndpoint() method, or stored in enricher metadata.</p>
     */
    @PostConstruct
    protected void registerEndpoint() {
        // Get the @RequestMapping annotation from the concrete controller class
        RequestMapping mapping = this.getClass().getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.value().length > 0) {
            String basePath = mapping.value()[0];
            String enrichmentEndpoint = basePath + "/enrich";

            log.debug("Registering enrichment endpoint for provider {}: {}",
                    dataEnricher.getProviderName(), enrichmentEndpoint);

            // Store the endpoint in the enricher if it supports it
            if (dataEnricher instanceof EndpointAware) {
                ((EndpointAware) dataEnricher).setEnrichmentEndpoint(enrichmentEndpoint);
            }
        }
    }
    
    @Override
    public Mono<EnrichmentApiResponse> enrich(EnrichmentApiRequest apiRequest) {
        log.info("Received enrichment request - type: {}, strategy: {}, provider: {}, requestId: {}, initiator: {}",
                apiRequest.getEnrichmentType(),
                apiRequest.getStrategy(),
                dataEnricher.getProviderName(),
                apiRequest.getRequestId(),
                apiRequest.getInitiator());
        log.debug("Full enrichment request: {}", apiRequest);
        
        // Convert API request to domain request
        EnrichmentRequest domainRequest = convertToDomainRequest(apiRequest);
        
        return dataEnricher.enrich(domainRequest)
                .map(this::convertToApiResponse)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("Enrichment completed successfully - type: {}, provider: {}, fieldsEnriched: {}, requestId: {}",
                                response.getEnrichmentType(),
                                response.getProviderName(),
                                response.getFieldsEnriched(),
                                response.getRequestId());
                    } else {
                        log.warn("Enrichment completed with failure - type: {}, provider: {}, message: {}, requestId: {}",
                                response.getEnrichmentType(),
                                response.getProviderName(),
                                response.getMessage(),
                                response.getRequestId());
                    }
                    log.debug("Full enrichment response for requestId {}: {}", response.getRequestId(), response);
                })
                .doOnError(error -> {
                    log.error("Enrichment failed for type {} with provider {} and requestId {}: {}",
                            apiRequest.getEnrichmentType(),
                            dataEnricher.getProviderName(),
                            apiRequest.getRequestId(),
                            error.getMessage(),
                            error);
                });
    }

    @Override
    public Flux<EnrichmentApiResponse> enrichBatch(List<EnrichmentApiRequest> apiRequests) {
        if (apiRequests == null || apiRequests.isEmpty()) {
            log.warn("Received empty batch enrichment request");
            return Flux.empty();
        }

        int batchSize = apiRequests.size();
        int maxBatchSize = enrichmentProperties != null ? enrichmentProperties.getMaxBatchSize() : 100;

        if (batchSize > maxBatchSize) {
            log.error("Batch size {} exceeds maximum allowed size {}", batchSize, maxBatchSize);
            return Flux.error(new IllegalArgumentException(
                String.format("Batch size %d exceeds maximum allowed size %d", batchSize, maxBatchSize)
            ));
        }

        log.info("Received batch enrichment request - provider: {}, batchSize: {}",
                dataEnricher.getProviderName(), batchSize);

        // Convert API requests to domain requests
        List<EnrichmentRequest> domainRequests = apiRequests.stream()
                .map(this::convertToDomainRequest)
                .collect(Collectors.toList());

        // Execute batch enrichment
        return dataEnricher.enrichBatch(domainRequests)
                .map(this::convertToApiResponse)
                .doOnNext(response -> {
                    if (response.isSuccess()) {
                        log.debug("Batch item enriched successfully - type: {}, provider: {}, fieldsEnriched: {}, requestId: {}",
                                response.getEnrichmentType(),
                                response.getProviderName(),
                                response.getFieldsEnriched(),
                                response.getRequestId());
                    } else {
                        log.warn("Batch item enrichment failed - type: {}, provider: {}, message: {}, requestId: {}",
                                response.getEnrichmentType(),
                                response.getProviderName(),
                                response.getMessage(),
                                response.getRequestId());
                    }
                })
                .doOnComplete(() -> log.info("Batch enrichment completed - provider: {}, batchSize: {}",
                        dataEnricher.getProviderName(), batchSize))
                .doOnError(error -> log.error("Batch enrichment failed for provider {}: {}",
                        dataEnricher.getProviderName(), error.getMessage(), error));
    }

    @Override
    public Mono<ProviderHealthResponse> checkHealth() {
        log.info("Received health check request for provider: {}", dataEnricher.getProviderName());
        
        return dataEnricher.isReady()
                .map(isHealthy -> {
                    String message = isHealthy 
                            ? "Provider is healthy and ready" 
                            : "Provider is not ready";
                    
                    log.info("Health check for provider {}: {}", 
                            dataEnricher.getProviderName(), 
                            message);
                    
                    return new ProviderHealthResponse(
                            isHealthy,
                            dataEnricher.getProviderName(),
                            message,
                            dataEnricher.getSupportedEnrichmentTypes()
                    );
                })
                .doOnError(error -> {
                    log.error("Health check failed for provider {}: {}", 
                            dataEnricher.getProviderName(), 
                            error.getMessage(), 
                            error);
                });
    }
    
    /**
     * Converts API request DTO to domain request model.
     */
    private EnrichmentRequest convertToDomainRequest(EnrichmentApiRequest apiRequest) {
        return EnrichmentRequest.builder()
                .enrichmentType(apiRequest.getEnrichmentType())
                .strategy(apiRequest.getStrategy())
                .sourceDto(apiRequest.getSourceDto())
                .parameters(apiRequest.getParameters())
                .tenantId(apiRequest.getTenantId())
                .requestId(apiRequest.getRequestId() != null 
                        ? apiRequest.getRequestId() 
                        : UUID.randomUUID().toString())
                .initiator(apiRequest.getInitiator())
                .metadata(apiRequest.getMetadata())
                .targetDtoClass(apiRequest.getTargetDtoClass())
                .timeoutMillis(apiRequest.getTimeoutMillis())
                .build();
    }
    
    /**
     * Converts domain response model to API response DTO.
     */
    private EnrichmentApiResponse convertToApiResponse(EnrichmentResponse domainResponse) {
        return EnrichmentApiResponse.builder()
                .success(domainResponse.isSuccess())
                .enrichedData(domainResponse.getEnrichedData())
                .rawProviderResponse(domainResponse.getRawProviderResponse())
                .message(domainResponse.getMessage())
                .error(domainResponse.getError())
                .providerName(domainResponse.getProviderName())
                .enrichmentType(domainResponse.getEnrichmentType())
                .strategy(domainResponse.getStrategy())
                .confidenceScore(domainResponse.getConfidenceScore())
                .fieldsEnriched(domainResponse.getFieldsEnriched())
                .timestamp(domainResponse.getTimestamp())
                .metadata(domainResponse.getMetadata())
                .requestId(domainResponse.getRequestId())
                .cost(domainResponse.getCost())
                .costCurrency(domainResponse.getCostCurrency())
                .build();
    }
    
    /**
     * Gets the data enricher.
     * 
     * @return the data enricher
     */
    protected DataEnricher getDataEnricher() {
        return dataEnricher;
    }
    
    /**
     * Gets the enricher registry.
     *
     * @return the enricher registry
     */
    protected DataEnricherRegistry getEnricherRegistry() {
        return enricherRegistry;
    }

    /**
     * Lists all provider-specific operations available in this enricher.
     *
     * <p>This endpoint returns the catalog of auxiliary operations that the provider
     * exposes, such as ID lookups, entity matching, validation, etc.</p>
     *
     * <p>Operations are automatically discovered from the enricher's {@code getOperations()}
     * method, which returns a list of {@link ProviderOperation} instances.</p>
     *
     * <p><b>Example Response:</b></p>
     * <pre>{@code
     * {
     *   "providerName": "Credit Bureau Provider",
     *   "operations": [
     *     {
     *       "operationId": "search-company",
     *       "path": "/api/v1/enrichment/credit-bureau/search-company",
     *       "method": "GET",
     *       "description": "Search for a company by name or tax ID to obtain provider internal ID",
     *       "tags": ["lookup", "search"],
     *       "requiresAuth": true,
     *       "requestType": "CompanySearchRequest",
     *       "responseType": "CompanySearchResponse",
     *       "requestSchema": { ... JSON Schema ... },
     *       "responseSchema": { ... JSON Schema ... },
     *       "requestExample": {
     *         "companyName": "Acme Corp",
     *         "taxId": "TAX-12345678"
     *       },
     *       "responseExample": {
     *         "providerId": "PROV-12345",
     *         "companyName": "ACME CORPORATION",
     *         "taxId": "TAX-12345678",
     *         "confidence": 0.95
     *       }
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return the operation catalog with JSON schemas and examples
     */
    @GetMapping("/operations")
    public Mono<ResponseEntity<OperationCatalogResponse>> listOperations() {
        log.info("Received request to list operations for provider: {}", dataEnricher.getProviderName());

        // Check if enricher supports operations
        if (!(dataEnricher instanceof TypedDataEnricher)) {
            log.debug("Provider {} is not a TypedDataEnricher - no operations available",
                    dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.ok(
                OperationCatalogResponse.builder()
                    .providerName(dataEnricher.getProviderName())
                    .operations(List.of())
                    .build()
            ));
        }

        TypedDataEnricher<?, ?, ?> typedEnricher = (TypedDataEnricher<?, ?, ?>) dataEnricher;
        List<ProviderOperation<?, ?>> operations = typedEnricher.getOperations();

        if (operations.isEmpty()) {
            log.debug("Provider {} has no operations defined", dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.ok(
                OperationCatalogResponse.builder()
                    .providerName(dataEnricher.getProviderName())
                    .operations(List.of())
                    .build()
            ));
        }

        // Get base path from @RequestMapping
        RequestMapping mapping = this.getClass().getAnnotation(RequestMapping.class);
        String basePath = mapping != null && mapping.value().length > 0
            ? mapping.value()[0]
            : "";

        // Convert operations to response format with full metadata
        List<OperationCatalogResponse.OperationInfo> operationList = operations.stream()
            .map(op -> {
                ProviderOperationMetadata metadata = op.getMetadata();

                return OperationCatalogResponse.OperationInfo.builder()
                    .operationId(metadata.getOperationId())
                    .path(metadata.getFullPath(basePath))
                    .method(metadata.getHttpMethod())
                    .description(metadata.getDescription())
                    .tags(List.of(metadata.getTags()))
                    .requiresAuth(metadata.isRequiresAuth())
                    .requestType(metadata.getRequestTypeName())
                    .responseType(metadata.getResponseTypeName())
                    .requestSchema(metadata.getRequestSchema())
                    .responseSchema(metadata.getResponseSchema())
                    .requestExample(metadata.getRequestExample())
                    .responseExample(metadata.getResponseExample())
                    .build();
            })
            .toList();

        log.info("Returning {} operations for provider {}",
                operationList.size(), dataEnricher.getProviderName());

        return Mono.just(ResponseEntity.ok(
            OperationCatalogResponse.builder()
                .providerName(dataEnricher.getProviderName())
                .operations(operationList)
                .build()
        ));
    }

    /**
     * Executes a provider-specific operation.
     *
     * <p>This endpoint routes to the appropriate operation handler based on the operationId.
     * It automatically handles:</p>
     * <ul>
     *   <li>Request deserialization from JSON to typed DTO</li>
     *   <li>Operation execution with type safety</li>
     *   <li>Response serialization from typed DTO to JSON</li>
     *   <li>Error handling and logging</li>
     * </ul>
     *
     * <p><b>Example Request (GET with query params):</b></p>
     * <pre>{@code
     * GET /api/v1/enrichment/credit-bureau/operation/search-company?companyName=Acme%20Corp&taxId=TAX-12345678
     * }</pre>
     *
     * <p><b>Example Request (POST with JSON body):</b></p>
     * <pre>{@code
     * POST /api/v1/enrichment/credit-bureau/operation/search-company
     * Content-Type: application/json
     *
     * {
     *   "companyName": "Acme Corp",
     *   "taxId": "TAX-12345678"
     * }
     * }</pre>
     *
     * <p><b>Example Response:</b></p>
     * <pre>{@code
     * {
     *   "providerId": "PROV-12345",
     *   "companyName": "ACME CORPORATION",
     *   "taxId": "TAX-12345678",
     *   "confidence": 0.95
     * }
     * }</pre>
     *
     * @param operationId the operation identifier
     * @param queryParams query parameters (for GET requests)
     * @param bodyParams request body parameters (for POST requests)
     * @return the operation result
     */
    @RequestMapping(value = "/operation/{operationId}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Object>> executeProviderOperation(
            @PathVariable String operationId,
            @RequestParam(required = false) Map<String, Object> queryParams,
            @RequestBody(required = false) Map<String, Object> bodyParams) {

        log.info("Received request to execute operation {} for provider {}",
                operationId, dataEnricher.getProviderName());

        // Check if enricher supports operations
        if (!(dataEnricher instanceof TypedDataEnricher)) {
            log.warn("Provider {} is not a TypedDataEnricher - operations not supported",
                    dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.badRequest().body(
                OperationErrorResponse.builder()
                    .error("Provider does not support operations")
                    .operationId(operationId)
                    .providerName(dataEnricher.getProviderName())
                    .build()
            ));
        }

        TypedDataEnricher<?, ?, ?> typedEnricher = (TypedDataEnricher<?, ?, ?>) dataEnricher;
        List<ProviderOperation<?, ?>> operations = typedEnricher.getOperations();

        // Find the operation by ID
        ProviderOperation<?, ?> operation = operations.stream()
            .filter(op -> op.getMetadata().getOperationId().equals(operationId))
            .findFirst()
            .orElse(null);

        if (operation == null) {
            log.warn("Operation {} not found for provider {}", operationId, dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.badRequest().body(
                OperationErrorResponse.builder()
                    .error("Operation not found: " + operationId)
                    .operationId(operationId)
                    .providerName(dataEnricher.getProviderName())
                    .availableOperations(operations.stream()
                        .map(op -> op.getMetadata().getOperationId())
                        .collect(Collectors.toList()))
                    .build()
            ));
        }

        // Merge query params and body params (body takes precedence)
        Map<String, Object> parameters = new HashMap<>();
        if (queryParams != null) {
            parameters.putAll(queryParams);
        }
        if (bodyParams != null && !bodyParams.isEmpty()) {
            parameters.putAll(bodyParams);
        }

        // Extract tenantId and requestId from parameters (if present)
        String tenantId = extractStringParam(parameters, "tenantId");
        String requestId = extractStringParam(parameters, "requestId");

        log.debug("Executing operation {} with parameters: {}, tenantId: {}, requestId: {}",
                operationId, parameters, tenantId, requestId);

        // Convert parameters to request DTO and execute
        return executeTypedOperation(operation, parameters, tenantId, requestId)
            .map(ResponseEntity::ok)
            .doOnSuccess(response -> {
                log.info("Operation {} completed successfully for provider {}",
                        operationId, dataEnricher.getProviderName());
            })
            .onErrorResume(error -> {
                log.error("Operation {} failed for provider {}: {}",
                        operationId, dataEnricher.getProviderName(), error.getMessage(), error);
                return Mono.just(ResponseEntity.badRequest().body(
                    OperationErrorResponse.builder()
                        .error(error.getMessage())
                        .operationId(operationId)
                        .providerName(dataEnricher.getProviderName())
                        .build()
                ));
            });
    }

    /**
     * Extracts a string parameter from the parameters map.
     *
     * @param parameters the parameters map
     * @param key the parameter key
     * @return the parameter value, or null if not present
     */
    private String extractStringParam(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Executes a typed operation with automatic request/response conversion.
     *
     * @param operation the operation to execute
     * @param parameters the request parameters as a map
     * @param tenantId the tenant ID (optional)
     * @param requestId the request ID (optional)
     * @return the operation response
     */
    @SuppressWarnings("unchecked")
    private <TRequest, TResponse> Mono<TResponse> executeTypedOperation(
            ProviderOperation<TRequest, TResponse> operation,
            Map<String, Object> parameters,
            String tenantId,
            String requestId) {

        try {
            // Convert parameters map to request DTO
            TRequest request;
            if (objectMapper != null) {
                request = objectMapper.convertValue(parameters, operation.getRequestType());
            } else {
                throw new IllegalStateException("ObjectMapper not available for request conversion");
            }

            // Execute the operation with context if it's an AbstractProviderOperation
            if (operation instanceof com.firefly.common.data.operation.AbstractProviderOperation) {
                com.firefly.common.data.operation.AbstractProviderOperation<TRequest, TResponse> abstractOp =
                    (com.firefly.common.data.operation.AbstractProviderOperation<TRequest, TResponse>) operation;

                // Set provider name if not already set
                if (abstractOp.getMetadata() != null) {
                    abstractOp.setProviderName(dataEnricher.getProviderName());
                }

                return abstractOp.executeWithContext(request, tenantId, requestId);
            }

            // Fallback to simple execute for non-AbstractProviderOperation implementations
            return operation.execute(request);
        } catch (Exception e) {
            log.error("Failed to convert request parameters to DTO: {}", e.getMessage(), e);
            return Mono.error(new IllegalArgumentException(
                "Invalid request parameters: " + e.getMessage(), e));
        }
    }
}

