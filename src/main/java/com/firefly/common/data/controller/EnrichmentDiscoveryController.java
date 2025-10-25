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

import com.firefly.common.data.enrichment.EnricherMetadataReader;
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
import java.util.UUID;
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
 * │   ├── type: "credit-report"
 * │   ├── tenantId: "550e8400-e29b-41d4-a716-446655440001" (Spain)
 * │   └── endpoint: POST /api/v1/enrichment/provider-a-spain-credit/enrich
 * ├── ProviderAUSACreditReportEnricher
 * │   ├── providerName: "Provider A USA"
 * │   ├── type: "credit-report"
 * │   ├── tenantId: "550e8400-e29b-41d4-a716-446655440002" (USA)
 * │   └── endpoint: POST /api/v1/enrichment/provider-a-usa-credit/enrich
 * └── ProviderASpainCompanyProfileEnricher
 *     ├── providerName: "Provider A Spain"
 *     ├── type: "company-profile"
 *     ├── tenantId: "550e8400-e29b-41d4-a716-446655440001" (Spain)
 *     └── endpoint: POST /api/v1/enrichment/provider-a-spain-company/enrich
 *
 * Microservice: core-data-provider-b-enricher
 * ├── ProviderBSpainCreditReportEnricher
 * └── ProviderBUSACreditReportEnricher
 * </pre>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // List all enrichers in this microservice
 * GET /api/v1/enrichment/providers
 * →  [
 *      {
 *        "providerName": "Provider A Spain",
 *        "type": "credit-report",
 *        "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *        "description": "Provider A Spain credit report enrichment",
 *        "endpoint": "/api/v1/enrichment/provider-a-spain-credit/enrich",
 *        "priority": 100,
 *        "tags": ["credit", "spain"]
 *      },
 *      {
 *        "providerName": "Provider A Spain",
 *        "type": "company-profile",
 *        "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *        "description": "Provider A Spain company profile enrichment",
 *        "endpoint": "/api/v1/enrichment/provider-a-spain-company/enrich",
 *        "priority": 100,
 *        "tags": ["company", "spain"]
 *      },
 *      {
 *        "providerName": "Provider A USA",
 *        "type": "credit-report",
 *        "tenantId": "550e8400-e29b-41d4-a716-446655440002",
 *        "description": "Provider A USA credit report enrichment",
 *        "endpoint": "/api/v1/enrichment/provider-a-usa-credit/enrich",
 *        "priority": 100,
 *        "tags": ["credit", "usa"]
 *      }
 *    ]
 *
 * // List only enrichers that support a specific enrichment type
 * GET /api/v1/enrichment/providers?type=credit-report
 * →  [
 *      { "providerName": "Provider A Spain", "type": "credit-report", "tenantId": "550e...", ... },
 *      { "providerName": "Provider A USA", "type": "credit-report", "tenantId": "550e...", ... }
 *    ]
 *
 * // List only enrichers for a specific tenant
 * GET /api/v1/enrichment/providers?tenantId=550e8400-e29b-41d4-a716-446655440001
 * →  [
 *      { "providerName": "Provider A Spain", "type": "credit-report", ... },
 *      { "providerName": "Provider A Spain", "type": "company-profile", ... }
 *    ]
 *
 * // Combine filters: type + tenant
 * GET /api/v1/enrichment/providers?type=credit-report&tenantId=550e8400-e29b-41d4-a716-446655440001
 * →  [
 *      { "providerName": "Provider A Spain", "type": "credit-report", ... }
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
            @RequestParam(required = false) String type,
            @Parameter(description = "Optional tenant ID (UUID) to filter providers for a specific tenant")
            @RequestParam(required = false) UUID tenantId) {

        log.info("Received request to list providers" +
                (type != null ? " for type: " + type : "") +
                (tenantId != null ? " for tenant: " + tenantId : ""));

        return Mono.fromSupplier(() -> {
            var enrichers = enricherRegistry.getAllEnrichers().stream();

            // Filter by enrichment type if specified
            if (type != null && !type.isEmpty()) {
                enrichers = enrichers.filter(e -> e.supportsEnrichmentType(type));
            }

            // Filter by tenant if specified
            if (tenantId != null) {
                enrichers = enrichers.filter(e -> {
                    UUID enricherTenantId = e.getTenantId();
                    // Include enrichers for this tenant OR global enrichers
                    return tenantId.equals(enricherTenantId) ||
                           EnricherMetadataReader.GLOBAL_TENANT_ID.equals(enricherTenantId);
                });
            }

            // Map each enricher to a ProviderInfo (one enricher = one entry)
            var providers = enrichers
                    .map(enricher -> {
                        String providerName = enricher.getProviderName();
                        UUID enricherTenantId = enricher.getTenantId();
                        String description = enricher.getEnricherDescription();
                        String endpoint = enricher.getEnrichmentEndpoint();
                        int priority = enricher.getPriority();
                        List<String> tags = enricher.getTags();

                        // Get supported types - use the first one as the primary type
                        List<String> supportedTypes = enricher.getSupportedEnrichmentTypes();
                        String primaryType = (supportedTypes != null && !supportedTypes.isEmpty())
                                ? supportedTypes.get(0)
                                : "unknown";

                        return new ProviderInfo(
                            providerName,
                            primaryType,
                            enricherTenantId,
                            description,
                            endpoint,
                            priority,
                            tags
                        );
                    })
                    .sorted((a, b) -> {
                        // Sort by provider name, then by type, then by priority (desc)
                        int nameCompare = a.providerName().compareTo(b.providerName());
                        if (nameCompare != 0) return nameCompare;
                        int typeCompare = a.type().compareTo(b.type());
                        if (typeCompare != 0) return typeCompare;
                        return Integer.compare(b.priority(), a.priority());
                    })
                    .collect(Collectors.toList());

            log.info("Found {} enrichers" +
                    (type != null ? " for type: " + type : "") +
                    (tenantId != null ? " for tenant: " + tenantId : ""),
                    providers.size());

            return providers;
        });
    }
    
    /**
     * Provider information DTO.
     *
     * <p>Each entry represents ONE enricher (one provider + one type + one tenant).</p>
     *
     * @param providerName the name of the provider (e.g., "Provider A", "Provider B")
     * @param type the enrichment type this enricher supports (e.g., "credit-report", "company-profile")
     * @param tenantId the tenant ID this enricher is configured for (or global tenant UUID)
     * @param description human-readable description of what this enricher does
     * @param endpoint the REST API endpoint for this enricher's enrich operation
     * @param priority the priority of this enricher (higher = preferred when multiple match)
     * @param tags list of tags for categorization and filtering
     */
    public record ProviderInfo(
        @Parameter(description = "Provider name (e.g., 'Provider A', 'Provider B')")
        String providerName,

        @Parameter(description = "Enrichment type this enricher supports (e.g., 'credit-report', 'company-profile')")
        String type,

        @Parameter(description = "Tenant ID this enricher is configured for (or global tenant UUID)")
        UUID tenantId,

        @Parameter(description = "Human-readable description of the enricher")
        String description,

        @Parameter(description = "REST API endpoint for enrichment operations")
        String endpoint,

        @Parameter(description = "Priority of this enricher (higher = preferred when multiple match)")
        int priority,

        @Parameter(description = "Tags for categorization and filtering")
        List<String> tags
    ) {}
}

