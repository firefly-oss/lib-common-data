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
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Smart enrichment controller that automatically selects the best enricher
 * based on type, tenant, and priority.
 * 
 * <p>This controller provides a simplified API for enrichment requests where
 * the client doesn't need to know which specific provider to use. The registry
 * automatically selects the highest-priority enricher that matches the requested
 * type and tenant.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * POST /api/v1/enrichment/smart
 * {
 *   "type": "company-profile",
 *   "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *   "params": {
 *     "companyId": "12345",
 *     "includeFinancials": true
 *   }
 * }
 * }</pre>
 * 
 * <p><b>Response:</b></p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "companyId": "12345",
 *     "name": "Acme Corp",
 *     "address": "123 Main St",
 *     "revenue": 1000000
 *   },
 *   "providerName": "Financial Data Provider",
 *   "type": "company-profile",
 *   "priority": 100,
 *   "message": "Enrichment completed successfully"
 * }
 * }</pre>
 * 
 * <p><b>How It Works:</b></p>
 * <ol>
 *   <li>Receives enrichment request with type and tenantId</li>
 *   <li>Queries {@link DataEnricherRegistry#getEnricherForTypeAndTenant(String, UUID)} 
 *       to find the best enricher</li>
 *   <li>The registry returns the highest-priority enricher that:
 *     <ul>
 *       <li>Supports the requested type</li>
 *       <li>Matches the tenant (or is a global enricher)</li>
 *     </ul>
 *   </li>
 *   <li>Executes the enrichment and returns the result</li>
 * </ol>
 * 
 * <p><b>Error Handling:</b></p>
 * <ul>
 *   <li><b>404 Not Found</b> - No enricher found for the type + tenant combination</li>
 *   <li><b>400 Bad Request</b> - Invalid request (missing type or tenantId)</li>
 *   <li><b>500 Internal Server Error</b> - Enrichment execution failed</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Smart Enrichment", description = "Automatic enricher selection and execution")
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.discovery",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class SmartEnrichmentController {

    private final DataEnricherRegistry enricherRegistry;

    public SmartEnrichmentController(DataEnricherRegistry enricherRegistry) {
        this.enricherRegistry = enricherRegistry;
    }

    /**
     * Smart enrichment endpoint that automatically selects the best enricher.
     * 
     * <p>This endpoint simplifies enrichment requests by automatically selecting
     * the highest-priority enricher that matches the requested type and tenant.</p>
     * 
     * @param request the enrichment request
     * @return the enrichment response
     */
    @PostMapping("/smart")
    @Operation(
        summary = "Smart enrichment with automatic provider selection",
        description = "Automatically selects the best enricher based on type, tenant, and priority. " +
                     "The registry will choose the highest-priority enricher that supports the requested type " +
                     "for the specified tenant (or a global enricher if no tenant-specific enricher exists)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Enrichment completed successfully",
            content = @Content(schema = @Schema(implementation = EnrichmentApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No enricher found for the specified type and tenant"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (missing type or tenantId)"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Enrichment execution failed"
        )
    })
    public Mono<EnrichmentApiResponse> smartEnrich(
            @Parameter(description = "Enrichment request with type, tenantId, and parameters", required = true)
            @RequestBody EnrichmentApiRequest request) {
        
        log.info("Received smart enrichment request - type: {}, tenantId: {}, requestId: {}",
                request.getType(),
                request.getTenantId(),
                request.getRequestId());

        // Validate request
        if (request.getType() == null || request.getType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type is required");
        }
        if (request.getTenantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant ID is required");
        }

        // Find the best enricher for this type + tenant
        DataEnricher<?, ?, ?> enricher = enricherRegistry.getEnricherForTypeAndTenant(
                request.getType(),
                request.getTenantId()
        ).orElseThrow(() -> {
            String message = String.format(
                    "No enricher found for type '%s' and tenant '%s'",
                    request.getType(),
                    request.getTenantId()
            );
            log.warn(message);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        });

        log.info("Selected enricher: {} (priority: {}) for type: {}, tenant: {}",
                enricher.getProviderName(),
                enricher.getPriority(),
                request.getType(),
                request.getTenantId());

        // Convert API request to domain request
        EnrichmentRequest domainRequest = convertToDomainRequest(request);

        // Execute enrichment
        return enricher.enrich(domainRequest)
                .map(domainResponse -> convertToApiResponse(domainResponse, enricher))
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("Smart enrichment completed successfully - type: {}, provider: {}, priority: {}, fieldsEnriched: {}, requestId: {}",
                                response.getType(),
                                response.getProviderName(),
                                enricher.getPriority(),
                                response.getFieldsEnriched(),
                                response.getRequestId());
                    } else {
                        log.warn("Smart enrichment completed with failure - type: {}, provider: {}, message: {}, requestId: {}",
                                response.getType(),
                                response.getProviderName(),
                                response.getMessage(),
                                response.getRequestId());
                    }
                })
                .doOnError(error -> {
                    log.error("Smart enrichment failed for type {} with provider {} and requestId {}: {}",
                            request.getType(),
                            enricher.getProviderName(),
                            request.getRequestId(),
                            error.getMessage(),
                            error);
                });
    }

    /**
     * Converts API request DTO to domain request model.
     */
    private EnrichmentRequest convertToDomainRequest(EnrichmentApiRequest apiRequest) {
        return EnrichmentRequest.builder()
                .type(apiRequest.getType())
                .strategy(apiRequest.getStrategy() != null ? apiRequest.getStrategy() : EnrichmentStrategy.ENHANCE)
                .sourceDto(apiRequest.getSource())
                .parameters(apiRequest.getParams())
                .tenantId(apiRequest.getTenantId())
                .requestId(apiRequest.getRequestId() != null
                        ? apiRequest.getRequestId()
                        : UUID.randomUUID().toString())
                .initiator(apiRequest.getInitiator())
                .metadata(apiRequest.getMetadata())
                .timeoutMillis(apiRequest.getTimeoutMillis())
                .build();
    }

    /**
     * Converts domain response model to API response DTO.
     */
    private EnrichmentApiResponse convertToApiResponse(EnrichmentResponse domainResponse, DataEnricher<?, ?, ?> enricher) {
        // Add enricher metadata
        Map<String, String> metadata = domainResponse.getMetadata() != null
                ? new java.util.HashMap<>(domainResponse.getMetadata())
                : new java.util.HashMap<>();

        if (enricher != null) {
            metadata.put("priority", String.valueOf(enricher.getPriority()));
            metadata.put("tenantId", enricher.getTenantId().toString());
            metadata.put("autoSelected", "true");
        }

        return EnrichmentApiResponse.builder()
                .success(domainResponse.isSuccess())
                .enrichedData(domainResponse.getEnrichedData())
                .rawProviderResponse(domainResponse.getRawProviderResponse())
                .message(domainResponse.getMessage())
                .error(domainResponse.getError())
                .providerName(domainResponse.getProviderName())
                .type(domainResponse.getType())
                .strategy(domainResponse.getStrategy())
                .confidenceScore(domainResponse.getConfidenceScore())
                .fieldsEnriched(domainResponse.getFieldsEnriched())
                .timestamp(domainResponse.getTimestamp())
                .metadata(metadata)
                .requestId(domainResponse.getRequestId())
                .cost(domainResponse.getCost())
                .costCurrency(domainResponse.getCostCurrency())
                .build();
    }

    /**
     * Batch enrichment endpoint that processes multiple requests in parallel.
     *
     * <p>This endpoint allows clients to enrich multiple items in a single request,
     * with automatic parallelization and error handling.</p>
     *
     * @param requests list of enrichment requests
     * @return flux of enrichment responses in the same order as requests
     */
    @PostMapping("/smart/batch")
    @Operation(
        summary = "Batch enrichment with automatic provider selection",
        description = "Processes multiple enrichment requests in parallel. Each request is routed to the " +
                     "best enricher based on type, tenant, and priority. Responses are returned in the same " +
                     "order as requests. Individual failures do not stop the batch processing."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Batch enrichment completed (check individual responses for success/failure)",
            content = @Content(schema = @Schema(implementation = EnrichmentApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request (empty batch or invalid items)"
        )
    })
    public Flux<EnrichmentApiResponse> smartEnrichBatch(
            @Parameter(description = "List of enrichment requests", required = true)
            @RequestBody List<EnrichmentApiRequest> requests) {

        log.info("Received batch enrichment request with {} items", requests.size());

        // Validate batch
        if (requests == null || requests.isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch cannot be empty"));
        }

        // Group requests by type + tenant to use the same enricher for efficiency
        Map<String, List<EnrichmentApiRequest>> groupedRequests = requests.stream()
                .collect(Collectors.groupingBy(req -> req.getType() + ":" + req.getTenantId()));

        log.info("Batch grouped into {} enricher(s)", groupedRequests.size());

        // Process each group with its corresponding enricher
        return Flux.fromIterable(groupedRequests.entrySet())
                .flatMap(entry -> {
                    List<EnrichmentApiRequest> group = entry.getValue();
                    EnrichmentApiRequest first = group.get(0);

                    // Find enricher for this group
                    DataEnricher<?, ?, ?> enricher = enricherRegistry.getEnricherForTypeAndTenant(
                            first.getType(),
                            first.getTenantId()
                    ).orElseThrow(() -> {
                        String message = String.format(
                                "No enricher found for type '%s' and tenant '%s'",
                                first.getType(),
                                first.getTenantId()
                        );
                        log.warn(message);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
                    });

                    log.info("Processing {} requests with enricher: {} (priority: {})",
                            group.size(),
                            enricher.getProviderName(),
                            enricher.getPriority());

                    // Convert to domain requests
                    List<EnrichmentRequest> domainRequests = group.stream()
                            .map(this::convertToDomainRequest)
                            .collect(Collectors.toList());

                    // Execute batch enrichment
                    return enricher.enrichBatch(domainRequests)
                            .map(domainResponse -> convertToApiResponse(domainResponse, enricher))
                            .onErrorResume(error -> {
                                // Handle individual errors gracefully
                                log.error("Error in batch enrichment for provider {}: {}",
                                        enricher.getProviderName(), error.getMessage());

                                EnrichmentApiResponse errorResponse = EnrichmentApiResponse.builder()
                                        .success(false)
                                        .message("Enrichment failed")
                                        .error(error.getMessage())
                                        .providerName(enricher.getProviderName())
                                        .type(first.getType())
                                        .timestamp(java.time.Instant.now())
                                        .build();

                                return Mono.just(errorResponse);
                            });
                })
                .doOnComplete(() -> log.info("Batch enrichment completed for {} total requests", requests.size()));
    }
}

