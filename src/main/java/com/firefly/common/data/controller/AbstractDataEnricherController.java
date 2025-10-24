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
import com.firefly.common.data.model.EnrichmentApiResponse;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
    
    /**
     * Constructor with enricher and registry dependencies.
     *
     * @param dataEnricher the data enricher implementation
     * @param enricherRegistry the enricher registry for listing providers
     */
    protected AbstractDataEnricherController(DataEnricher dataEnricher,
                                            DataEnricherRegistry enricherRegistry) {
        this.dataEnricher = dataEnricher;
        this.enricherRegistry = enricherRegistry;
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
     * <p><b>Example Response:</b></p>
     * <pre>{@code
     * {
     *   "providerName": "Equifax Spain",
     *   "operations": [
     *     {
     *       "operationId": "search-company",
     *       "path": "/api/v1/enrichment/equifax-spain/search-company",
     *       "method": "GET",
     *       "description": "Search for a company by name or CIF",
     *       "tags": ["lookup", "search"]
     *     }
     *   ]
     * }
     * }</pre>
     *
     * @return the operation catalog
     */
    @GetMapping("/operations")
    public Mono<ResponseEntity<Map<String, Object>>> listOperations() {
        log.info("Received request to list operations for provider: {}", dataEnricher.getProviderName());

        if (!(dataEnricher instanceof ProviderOperationCatalog)) {
            log.debug("Provider {} does not implement ProviderOperationCatalog",
                    dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.ok(Map.of(
                "providerName", dataEnricher.getProviderName(),
                "operations", List.of()
            )));
        }

        ProviderOperationCatalog catalog = (ProviderOperationCatalog) dataEnricher;
        List<ProviderOperation> operations = catalog.getOperationCatalog();

        // Get base path from @RequestMapping
        RequestMapping mapping = this.getClass().getAnnotation(RequestMapping.class);
        String basePath = mapping != null && mapping.value().length > 0
            ? mapping.value()[0]
            : "";

        // Convert operations to response format with full paths
        List<Map<String, Object>> operationList = operations.stream()
            .map(op -> Map.of(
                "operationId", op.getOperationId(),
                "path", op.getFullPath(basePath),
                "method", op.getHttpMethod(),
                "description", op.getDescription(),
                "tags", op.getTags(),
                "requiresAuth", op.isRequiresAuth(),
                "requestExample", op.getRequestExample(),
                "responseExample", op.getResponseExample()
            ))
            .collect(Collectors.toList());

        log.info("Returning {} operations for provider {}",
                operationList.size(), dataEnricher.getProviderName());

        return Mono.just(ResponseEntity.ok(Map.of(
            "providerName", dataEnricher.getProviderName(),
            "operations", operationList
        )));
    }

    /**
     * Executes a provider-specific operation.
     *
     * <p>This is a generic endpoint that routes to the appropriate operation handler
     * based on the operationId path variable.</p>
     *
     * <p><b>Note:</b> This is a fallback handler. Enrichers can also define custom
     * controller methods for better type safety and documentation.</p>
     *
     * @param operationId the operation identifier
     * @param parameters the operation parameters (from query params or request body)
     * @return the operation result
     */
    @RequestMapping(value = "/operation/{operationId}", method = {RequestMethod.GET, RequestMethod.POST})
    public Mono<ResponseEntity<Map<String, Object>>> executeProviderOperation(
            @PathVariable String operationId,
            @RequestParam(required = false) Map<String, Object> queryParams,
            @RequestBody(required = false) Map<String, Object> bodyParams) {

        log.info("Received request to execute operation {} for provider {}",
                operationId, dataEnricher.getProviderName());

        if (!(dataEnricher instanceof ProviderOperationCatalog)) {
            log.warn("Provider {} does not implement ProviderOperationCatalog",
                    dataEnricher.getProviderName());
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "Provider does not support custom operations",
                "providerName", dataEnricher.getProviderName()
            )));
        }

        // Merge query params and body params (body takes precedence)
        Map<String, Object> parameters = queryParams != null ? queryParams : Map.of();
        if (bodyParams != null && !bodyParams.isEmpty()) {
            parameters = bodyParams;
        }

        ProviderOperationCatalog catalog = (ProviderOperationCatalog) dataEnricher;

        log.debug("Executing operation {} with parameters: {}", operationId, parameters);

        return catalog.executeOperation(operationId, parameters)
            .map(ResponseEntity::ok)
            .doOnSuccess(response -> {
                log.info("Operation {} completed successfully for provider {}",
                        operationId, dataEnricher.getProviderName());
            })
            .onErrorResume(error -> {
                log.error("Operation {} failed for provider {}: {}",
                        operationId, dataEnricher.getProviderName(), error.getMessage(), error);
                return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "error", error.getMessage(),
                    "operationId", operationId,
                    "providerName", dataEnricher.getProviderName()
                )));
            });
    }
}

