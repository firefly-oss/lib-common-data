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
import com.firefly.common.data.enrichment.EnricherMetadataReader;
import com.firefly.common.data.operation.EnricherOperationInterface;
import com.firefly.common.data.operation.EnricherOperationMetadata;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import com.firefly.common.data.service.DataEnricher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global controller for provider-specific custom operations.
 * 
 * <p>This controller provides centralized endpoints to discover and execute
 * provider-specific operations (such as search, validation, lookup) across all
 * registered data enrichers.</p>
 * 
 * <p><b>What are Provider Operations?</b></p>
 * <p>Provider operations are auxiliary operations that enrichers expose to support
 * their enrichment workflow. Common use cases include:</p>
 * <ul>
 *   <li><b>ID Lookup:</b> Search for internal provider IDs before enrichment</li>
 *   <li><b>Entity Matching:</b> Fuzzy match companies/individuals in provider's database</li>
 *   <li><b>Validation:</b> Validate identifiers (tax IDs, business numbers, etc.)</li>
 *   <li><b>Quick Queries:</b> Get specific data points without full enrichment</li>
 * </ul>
 * 
 * <p><b>Example Usage - List Operations:</b></p>
 * <pre>{@code
 * // List all operations across all enrichers
 * GET /api/v1/enrichment/operations
 * 
 * // List operations for a specific enrichment type
 * GET /api/v1/enrichment/operations?type=credit-report
 * 
 * // List operations for a specific tenant
 * GET /api/v1/enrichment/operations?tenantId=550e8400-e29b-41d4-a716-446655440001
 * 
 * // List operations for specific type and tenant
 * GET /api/v1/enrichment/operations?type=credit-report&tenantId=550e8400-...
 * }</pre>
 * 
 * <p><b>Example Usage - Execute Operation:</b></p>
 * <pre>{@code
 * // Execute a specific operation
 * POST /api/v1/enrichment/operations/execute
 * {
 *   "type": "credit-report",
 *   "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *   "operationId": "search-company",
 *   "request": {
 *     "companyName": "Acme Corp",
 *     "taxId": "TAX-12345678"
 *   }
 * }
 * 
 * // Response
 * {
 *   "providerId": "PROV-12345",
 *   "companyName": "ACME CORPORATION",
 *   "taxId": "TAX-12345678",
 *   "confidence": 0.95
 * }
 * }</pre>
 * 
 * <p><b>How It Works:</b></p>
 * <ol>
 *   <li>Client requests to list operations (optionally filtered by type/tenant)</li>
 *   <li>Controller queries all registered enrichers</li>
 *   <li>For each enricher, retrieves its operations via {@code getOperations()}</li>
 *   <li>Returns consolidated catalog with metadata, schemas, and examples</li>
 * </ol>
 * 
 * <p><b>Configuration:</b></p>
 * <p>This controller is enabled by default. To disable it, set:</p>
 * <pre>
 * firefly.data.enrichment.discovery.enabled=false
 * </pre>
 *
 * @see EnricherOperationInterface
 * @see DataEnricher#getOperations()
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Provider Operations", description = "Provider-specific custom operations (search, validation, lookup)")
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.discovery",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GlobalOperationsController {

    private final DataEnricherRegistry enricherRegistry;

    public GlobalOperationsController(DataEnricherRegistry enricherRegistry) {
        this.enricherRegistry = enricherRegistry;
    }

    /**
     * Lists all provider-specific operations available across all enrichers.
     * 
     * <p>This endpoint returns a consolidated catalog of all operations from all
     * registered enrichers, optionally filtered by enrichment type and/or tenant.</p>
     * 
     * @param type optional enrichment type to filter operations
     * @param tenantId optional tenant ID to filter operations
     * @return list of operation catalogs grouped by provider
     */
    @GetMapping("/operations")
    @Operation(
        summary = "List all provider-specific operations",
        description = "Returns a catalog of all custom operations available across all enrichers. " +
                     "Operations can be filtered by enrichment type and/or tenant ID. " +
                     "Each operation includes metadata, JSON schemas, and examples."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Operations catalog retrieved successfully",
            content = @Content(schema = @Schema(implementation = OperationCatalogResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    public Mono<ResponseEntity<List<OperationCatalogResponse>>> listOperations(
            @Parameter(description = "Optional enrichment type to filter operations (e.g., 'credit-report')")
            @RequestParam(required = false) String type,
            @Parameter(description = "Optional tenant ID to filter operations")
            @RequestParam(required = false) UUID tenantId) {
        
        log.info("Received request to list operations - type: {}, tenantId: {}", type, tenantId);

        // Get enrichers (filtered or all)
        List<DataEnricher<?, ?, ?>> enrichers;
        if (type != null && tenantId != null) {
            enrichers = enricherRegistry.getAllEnrichersForTypeAndTenant(type, tenantId);
        } else if (type != null) {
            enrichers = enricherRegistry.getAllEnrichersForType(type);
        } else if (tenantId != null) {
            enrichers = enricherRegistry.getEnrichersByTenant(tenantId);
        } else {
            enrichers = enricherRegistry.getAllEnrichers();
        }

        log.debug("Found {} enrichers matching criteria", enrichers.size());

        // Build operation catalogs
        List<OperationCatalogResponse> catalogs = new ArrayList<>();
        
        for (DataEnricher<?, ?, ?> enricher : enrichers) {
            // Only DataEnricher supports operations
            if (!(enricher instanceof DataEnricher)) {
                continue;
            }

            DataEnricher<?, ?, ?> typedEnricher = (DataEnricher<?, ?, ?>) enricher;
            List<EnricherOperationInterface<?, ?>> operations = typedEnricher.getOperations();

            if (operations.isEmpty()) {
                continue;
            }

            // Build operation info list
            List<OperationCatalogResponse.OperationInfo> operationInfos = new ArrayList<>();
            for (EnricherOperationInterface<?, ?> operation : operations) {
                EnricherOperationMetadata metadata = operation.getMetadata();
                operationInfos.add(OperationCatalogResponse.OperationInfo.builder()
                    .operationId(metadata.getOperationId())
                    .path("/api/v1/enrichment/operations/execute")  // Global execution endpoint
                    .method(metadata.getHttpMethod())
                    .description(metadata.getDescription())
                    .tags(java.util.Arrays.asList(metadata.getTags()))
                    .requiresAuth(metadata.isRequiresAuth())
                    .requestType(operation.getRequestType().getSimpleName())
                    .responseType(operation.getResponseType().getSimpleName())
                    .requestSchema(metadata.getRequestSchema())
                    .responseSchema(metadata.getResponseSchema())
                    .requestExample(metadata.getRequestExample())
                    .responseExample(metadata.getResponseExample())
                    .build());
            }

            // Add catalog for this enricher
            catalogs.add(OperationCatalogResponse.builder()
                .providerName(enricher.getProviderName())
                .operations(operationInfos)
                .build());
        }

        log.info("Returning {} operation catalogs with total {} operations",
                catalogs.size(),
                catalogs.stream().mapToInt(c -> c.getOperations().size()).sum());

        return Mono.just(ResponseEntity.ok(catalogs));
    }

    /**
     * Executes a provider-specific operation.
     * 
     * <p>This endpoint allows executing any operation from any enricher by specifying
     * the enrichment type, tenant ID, and operation ID.</p>
     * 
     * @param executionRequest the operation execution request
     * @return the operation response
     */
    @PostMapping("/operations/execute")
    @Operation(
        summary = "Execute a provider-specific operation",
        description = "Executes a custom operation from a specific enricher. " +
                     "The enricher is selected based on type and tenant ID, " +
                     "then the specified operation is executed with the provided request data."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Operation executed successfully"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Enricher or operation not found"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Operation execution failed"
        )
    })
    public Mono<ResponseEntity<Object>> executeOperation(
            @Parameter(description = "Operation execution request", required = true)
            @RequestBody OperationExecutionRequest executionRequest) {
        
        log.info("Received operation execution request - type: {}, tenantId: {}, operationId: {}",
                executionRequest.getType(),
                executionRequest.getTenantId(),
                executionRequest.getOperationId());

        // Validate request
        if (executionRequest.getType() == null || executionRequest.getType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type is required");
        }
        if (executionRequest.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant ID is required");
        }
        if (executionRequest.getOperationId() == null || executionRequest.getOperationId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operation ID is required");
        }

        // Find enricher
        DataEnricher<?, ?, ?> enricher = enricherRegistry.getEnricherForTypeAndTenant(
                executionRequest.getType(),
                executionRequest.getTenantId()
        ).orElseThrow(() -> {
            String message = String.format(
                    "No enricher found for type '%s' and tenant '%s'",
                    executionRequest.getType(),
                    executionRequest.getTenantId()
            );
            log.warn(message);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        });

        // Check if enricher supports operations
        if (!(enricher instanceof DataEnricher)) {
            String message = String.format(
                    "Enricher '%s' does not support operations",
                    enricher.getProviderName()
            );
            log.warn(message);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }

        DataEnricher<?, ?, ?> typedEnricher = (DataEnricher<?, ?, ?>) enricher;

        // Find operation
        EnricherOperationInterface<Object, Object> operation = (EnricherOperationInterface<Object, Object>) typedEnricher.getOperations()
                .stream()
                .filter(op -> op.getMetadata().getOperationId().equals(executionRequest.getOperationId()))
                .findFirst()
                .orElseThrow(() -> {
                    String message = String.format(
                            "Operation '%s' not found in enricher '%s'",
                            executionRequest.getOperationId(),
                            enricher.getProviderName()
                    );
                    log.warn(message);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
                });

        log.info("Executing operation '{}' from provider '{}' for tenant '{}'",
                executionRequest.getOperationId(),
                enricher.getProviderName(),
                executionRequest.getTenantId());

        // Execute operation
        return operation.execute(executionRequest.getRequest())
        .map(ResponseEntity::ok)
        .doOnSuccess(response -> {
            log.info("Operation '{}' executed successfully for provider '{}', tenant '{}'",
                    executionRequest.getOperationId(),
                    enricher.getProviderName(),
                    executionRequest.getTenantId());
        })
        .doOnError(error -> {
            log.error("Operation '{}' failed for provider '{}', tenant '{}': {}",
                    executionRequest.getOperationId(),
                    enricher.getProviderName(),
                    executionRequest.getTenantId(),
                    error.getMessage(),
                    error);
        });
    }

    /**
     * Request DTO for operation execution.
     */
    @Schema(description = "Request to execute a provider operation")
    public static class OperationExecutionRequest {
        
        @Schema(description = "Enrichment type", example = "credit-report", required = true)
        private String type;
        
        @Schema(description = "Tenant ID", example = "550e8400-e29b-41d4-a716-446655440001", required = true)
        private UUID tenantId;
        
        @Schema(description = "Operation ID", example = "search-company", required = true)
        private String operationId;
        
        @Schema(description = "Operation request data", required = true)
        private Object request;
        
        @Schema(description = "Optional request ID for tracing")
        private String requestId;

        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
        
        public String getOperationId() { return operationId; }
        public void setOperationId(String operationId) { this.operationId = operationId; }
        
        public Object getRequest() { return request; }
        public void setRequest(Object request) { this.request = request; }
        
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
    }
}

