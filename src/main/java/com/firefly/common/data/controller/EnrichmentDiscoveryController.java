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

import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global controller for discovering available data enrichment providers.
 * 
 * <p>This controller provides endpoints to discover which data enrichers are available
 * in the current microservice. This is useful for:</p>
 * <ul>
 *   <li><b>Service Discovery:</b> Finding which providers and enrichment types are available</li>
 *   <li><b>Multi-Region Support:</b> Discovering regional implementations (e.g., Equifax Spain vs Equifax USA)</li>
 *   <li><b>Dynamic Configuration:</b> Building UIs or configurations based on available providers</li>
 *   <li><b>Health Monitoring:</b> Checking which enrichers are registered and active</li>
 * </ul>
 * 
 * <p><b>Architecture Context:</b></p>
 * <p>In a typical deployment, you might have multiple microservices, each dedicated to a specific provider:</p>
 * <pre>
 * Microservice: core-data-provider-a-enricher
 * ├── ProviderASpainCreditReportEnricher
 * │   ├── providerName: "Provider A Spain"
 * │   ├── supportedTypes: ["credit-report", "credit-score"]
 * │   └── endpoint: POST /api/v1/enrichment/provider-a-spain-credit/enrich
 * ├── ProviderAUSACreditReportEnricher
 * │   ├── providerName: "Provider A USA"
 * │   ├── supportedTypes: ["credit-report", "business-credit"]
 * │   └── endpoint: POST /api/v1/enrichment/provider-a-usa-credit/enrich
 * └── ProviderASpainCompanyProfileEnricher
 *     ├── providerName: "Provider A Spain"
 *     ├── supportedTypes: ["company-profile"]
 *     └── endpoint: POST /api/v1/enrichment/provider-a-spain-company/enrich
 *
 * Microservice: core-data-provider-b-enricher
 * ├── ProviderBSpainCreditReportEnricher
 * └── ProviderBUSACreditReportEnricher
 * </pre>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // List all providers in this microservice
 * GET /api/v1/enrichment/providers
 * →  [
 *      {
 *        "providerName": "Provider A Spain",
 *        "supportedTypes": ["credit-report", "credit-score", "company-profile"],
 *        "description": "Provider A Spain data enrichment services",
 *        "endpoints": [
 *          "/api/v1/enrichment/provider-a-spain-credit/enrich",
 *          "/api/v1/enrichment/provider-a-spain-company/enrich"
 *        ]
 *      },
 *      {
 *        "providerName": "Provider A USA",
 *        "supportedTypes": ["credit-report", "business-credit"],
 *        "description": "Provider A USA data enrichment services",
 *        "endpoints": [
 *          "/api/v1/enrichment/provider-a-usa-credit/enrich"
 *        ]
 *      }
 *    ]
 *
 * // List only providers that support a specific enrichment type
 * GET /api/v1/enrichment/providers?enrichmentType=credit-report
 * →  [
 *      { "providerName": "Provider A Spain", ... },
 *      { "providerName": "Provider A USA", ... }
 *    ]
 *
 * // List only providers that support a specific enrichment type
 * GET /api/v1/enrichment/providers?enrichmentType=company-profile
 * →  [
 *      { "providerName": "Provider A Spain", ... }
 *    ]
 * }</pre>
 * 
 * <p><b>Configuration:</b></p>
 * <p>This controller is enabled by default. To disable it, set:</p>
 * <pre>
 * firefly.data.enrichment.discovery.enabled=false
 * </pre>
 * 
 * @see DataEnricherRegistry
 * @see DataEnricher
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Data Enrichment - Discovery", description = "Provider discovery and service information endpoints")
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.discovery",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class EnrichmentDiscoveryController {
    
    private final DataEnricherRegistry enricherRegistry;
    
    public EnrichmentDiscoveryController(DataEnricherRegistry enricherRegistry) {
        this.enricherRegistry = enricherRegistry;
    }
    
    /**
     * Lists all available data enrichment providers in this microservice.
     * 
     * <p>This endpoint returns information about all registered data enrichers,
     * optionally filtered by enrichment type.</p>
     * 
     * @param enrichmentType optional filter to only return providers that support this enrichment type
     * @return list of provider information
     */
    @Operation(
        summary = "List available providers",
        description = "Returns a list of all data enrichment providers available in this microservice. " +
                     "Can be filtered by enrichment type to find providers that support specific enrichment operations."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Providers retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/providers")
    public Mono<List<ProviderInfo>> listProviders(
            @Parameter(description = "Optional enrichment type to filter providers (e.g., 'credit-report', 'company-profile')")
            @RequestParam(required = false) String enrichmentType) {
        
        log.info("Received request to list providers" + 
                (enrichmentType != null ? " for enrichment type: " + enrichmentType : ""));
        
        return Mono.fromSupplier(() -> {
            var enrichers = enricherRegistry.getAllEnrichers().stream();
            
            // Filter by enrichment type if specified
            if (enrichmentType != null && !enrichmentType.isEmpty()) {
                enrichers = enrichers.filter(e -> e.supportsEnrichmentType(enrichmentType));
            }
            
            // Group by provider name to avoid duplicates
            var providers = enrichers
                    .collect(Collectors.groupingBy(DataEnricher::getProviderName))
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        String providerName = entry.getKey();
                        List<DataEnricher> enrichersForProvider = entry.getValue();
                        
                        // Collect all unique supported types for this provider
                        String[] allSupportedTypes = enrichersForProvider.stream()
                                .flatMap(e -> java.util.Arrays.stream(e.getSupportedEnrichmentTypes()))
                                .distinct()
                                .sorted()
                                .toArray(String[]::new);

                        // Use description from first enricher (they should all be the same for same provider)
                        String description = enrichersForProvider.get(0).getEnricherDescription();

                        // Collect all endpoints for this provider
                        String[] endpoints = enrichersForProvider.stream()
                                .map(DataEnricher::getEnrichmentEndpoint)
                                .filter(endpoint -> endpoint != null && !endpoint.isEmpty())
                                .distinct()
                                .sorted()
                                .toArray(String[]::new);

                        // Collect all provider-specific operations
                        List<Map<String, Object>> allOperations = new ArrayList<>();
                        for (DataEnricher enricher : enrichersForProvider) {
                            if (enricher instanceof ProviderOperationCatalog catalog) {
                                List<ProviderOperation> operations = catalog.getOperationCatalog();
                                if (operations != null && !operations.isEmpty()) {
                                    // Get base path from enricher's endpoint
                                    String endpoint = enricher.getEnrichmentEndpoint();
                                    String basePath = endpoint != null && endpoint.endsWith("/enrich")
                                        ? endpoint.substring(0, endpoint.length() - "/enrich".length())
                                        : "";

                                    for (ProviderOperation op : operations) {
                                        allOperations.add(Map.of(
                                            "operationId", op.getOperationId(),
                                            "path", op.getFullPath(basePath),
                                            "method", op.getHttpMethod(),
                                            "description", op.getDescription(),
                                            "tags", op.getTags(),
                                            "requiresAuth", op.isRequiresAuth()
                                        ));
                                    }
                                }
                            }
                        }

                        return new ProviderInfo(
                            providerName,
                            allSupportedTypes,
                            description,
                            endpoints,
                            allOperations.isEmpty() ? null : allOperations
                        );
                    })
                    .sorted((a, b) -> a.providerName().compareTo(b.providerName()))
                    .collect(Collectors.toList());
            
            log.info("Found {} providers" + 
                    (enrichmentType != null ? " for enrichment type: " + enrichmentType : ""), 
                    providers.size());
            
            return providers;
        });
    }
    
    /**
     * Provider information DTO.
     *
     * @param providerName the name of the provider (e.g., "Provider A", "Provider B")
     * @param supportedTypes array of enrichment types this provider supports
     * @param description human-readable description of what this provider does
     * @param endpoints array of REST API endpoints for this provider's enrichers
     * @param operations list of provider-specific operations (lookups, validations, etc.)
     */
    public record ProviderInfo(
        @Parameter(description = "Provider name (e.g., 'Provider A', 'Provider B')")
        String providerName,

        @Parameter(description = "Array of enrichment types this provider supports")
        String[] supportedTypes,

        @Parameter(description = "Human-readable description of the provider")
        String description,

        @Parameter(description = "Array of REST API endpoints for enrichment operations")
        String[] endpoints,

        @Parameter(description = "List of provider-specific operations (ID lookups, validations, etc.)")
        List<Map<String, Object>> operations
    ) {}
}

