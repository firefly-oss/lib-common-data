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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Global health check controller for all data enrichers.
 * 
 * <p>This controller provides a centralized health check endpoint that reports
 * the health status of all registered enrichers in the microservice.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * // Check health of all enrichers
 * GET /api/v1/enrichment/health
 * 
 * // Check health of enrichers for a specific tenant
 * GET /api/v1/enrichment/health?tenantId=550e8400-e29b-41d4-a716-446655440001
 * 
 * // Check health of enrichers for a specific type
 * GET /api/v1/enrichment/health?type=credit-report
 * }</pre>
 * 
 * <p><b>Response Example:</b></p>
 * <pre>{@code
 * {
 *   "overallHealthy": true,
 *   "totalEnrichers": 4,
 *   "healthyEnrichers": 4,
 *   "unhealthyEnrichers": 0,
 *   "enrichers": [
 *     {
 *       "providerName": "Provider A",
 *       "type": "credit-report",
 *       "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *       "healthy": true,
 *       "message": "Provider is healthy and ready",
 *       "priority": 100,
 *       "tags": ["production", "gdpr-compliant"]
 *     },
 *     {
 *       "providerName": "Provider A",
 *       "type": "company-profile",
 *       "tenantId": "550e8400-e29b-41d4-a716-446655440001",
 *       "healthy": true,
 *       "message": "Provider is healthy and ready",
 *       "priority": 100,
 *       "tags": ["production"]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Enrichment Health", description = "Global health check for all data enrichers")
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.discovery",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class GlobalEnrichmentHealthController {

    private final DataEnricherRegistry enricherRegistry;

    public GlobalEnrichmentHealthController(DataEnricherRegistry enricherRegistry) {
        this.enricherRegistry = enricherRegistry;
    }

    /**
     * Global health check endpoint for all enrichers.
     * 
     * <p>This endpoint checks the health of all registered enrichers and returns
     * a summary along with individual health status for each enricher.</p>
     * 
     * @param tenantId optional tenant ID to filter enrichers
     * @param type optional enrichment type to filter enrichers
     * @return health check response with overall status and individual enricher health
     */
    @GetMapping("/health")
    @Operation(
        summary = "Check health of all data enrichers",
        description = "Returns health status of all registered enrichers in this microservice. " +
                     "Can be filtered by tenant ID or enrichment type."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Health check completed successfully",
            content = @Content(schema = @Schema(implementation = GlobalHealthResponse.class))
        )
    })
    public Mono<GlobalHealthResponse> checkGlobalHealth(
            @Parameter(description = "Optional tenant ID to filter enrichers")
            @RequestParam(required = false) UUID tenantId,
            @Parameter(description = "Optional enrichment type to filter enrichers")
            @RequestParam(required = false) String type) {
        
        log.info("Received global health check request - tenantId: {}, type: {}", tenantId, type);

        // Get all enrichers (or filtered by tenant/type)
        List<DataEnricher<?, ?, ?>> enrichers;
        if (tenantId != null && type != null) {
            enrichers = enricherRegistry.getAllEnrichersForTypeAndTenant(type, tenantId);
        } else if (tenantId != null) {
            enrichers = enricherRegistry.getEnrichersByTenant(tenantId);
        } else if (type != null) {
            enrichers = enricherRegistry.getAllEnrichersForType(type);
        } else {
            enrichers = enricherRegistry.getAllEnrichers();
        }

        if (enrichers.isEmpty()) {
            log.warn("No enrichers found for health check - tenantId: {}, type: {}", tenantId, type);
            return Mono.just(GlobalHealthResponse.builder()
                .overallHealthy(true)
                .totalEnrichers(0)
                .healthyEnrichers(0)
                .unhealthyEnrichers(0)
                .enrichers(List.of())
                .build());
        }

        // Check health of each enricher
        return Flux.fromIterable(enrichers)
            .flatMap(enricher -> 
                enricher.isReady()
                    .map(isHealthy -> {
                        String message = isHealthy 
                            ? "Provider is healthy and ready"
                            : "Provider is not ready";
                        
                        return EnricherHealthInfo.builder()
                            .providerName(enricher.getProviderName())
                            .type(EnricherMetadataReader.getType(enricher))
                            .tenantId(enricher.getTenantId())
                            .healthy(isHealthy)
                            .message(message)
                            .priority(enricher.getPriority())
                            .tags(enricher.getTags())
                            .build();
                    })
                    .onErrorResume(error -> {
                        log.error("Health check failed for enricher {}: {}", 
                            enricher.getProviderName(), error.getMessage());
                        
                        return Mono.just(EnricherHealthInfo.builder()
                            .providerName(enricher.getProviderName())
                            .type(EnricherMetadataReader.getType(enricher))
                            .tenantId(enricher.getTenantId())
                            .healthy(false)
                            .message("Health check failed: " + error.getMessage())
                            .priority(enricher.getPriority())
                            .tags(enricher.getTags())
                            .build());
                    })
            )
            .collectList()
            .map(healthInfos -> {
                long healthyCount = healthInfos.stream().filter(EnricherHealthInfo::healthy).count();
                long unhealthyCount = healthInfos.size() - healthyCount;
                boolean overallHealthy = unhealthyCount == 0;

                log.info("Global health check completed - total: {}, healthy: {}, unhealthy: {}", 
                    healthInfos.size(), healthyCount, unhealthyCount);

                return GlobalHealthResponse.builder()
                    .overallHealthy(overallHealthy)
                    .totalEnrichers(healthInfos.size())
                    .healthyEnrichers((int) healthyCount)
                    .unhealthyEnrichers((int) unhealthyCount)
                    .enrichers(healthInfos)
                    .build();
            });
    }

    /**
     * Global health response DTO.
     *
     * @param overallHealthy true if all enrichers are healthy
     * @param totalEnrichers total number of enrichers checked
     * @param healthyEnrichers number of healthy enrichers
     * @param unhealthyEnrichers number of unhealthy enrichers
     * @param enrichers list of individual enricher health information
     */
    public record GlobalHealthResponse(
        @Parameter(description = "True if all enrichers are healthy")
        boolean overallHealthy,

        @Parameter(description = "Total number of enrichers checked")
        int totalEnrichers,

        @Parameter(description = "Number of healthy enrichers")
        int healthyEnrichers,

        @Parameter(description = "Number of unhealthy enrichers")
        int unhealthyEnrichers,

        @Parameter(description = "Individual health information for each enricher")
        List<EnricherHealthInfo> enrichers
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean overallHealthy;
            private int totalEnrichers;
            private int healthyEnrichers;
            private int unhealthyEnrichers;
            private List<EnricherHealthInfo> enrichers;

            public Builder overallHealthy(boolean overallHealthy) {
                this.overallHealthy = overallHealthy;
                return this;
            }

            public Builder totalEnrichers(int totalEnrichers) {
                this.totalEnrichers = totalEnrichers;
                return this;
            }

            public Builder healthyEnrichers(int healthyEnrichers) {
                this.healthyEnrichers = healthyEnrichers;
                return this;
            }

            public Builder unhealthyEnrichers(int unhealthyEnrichers) {
                this.unhealthyEnrichers = unhealthyEnrichers;
                return this;
            }

            public Builder enrichers(List<EnricherHealthInfo> enrichers) {
                this.enrichers = enrichers;
                return this;
            }

            public GlobalHealthResponse build() {
                return new GlobalHealthResponse(overallHealthy, totalEnrichers, healthyEnrichers, 
                    unhealthyEnrichers, enrichers);
            }
        }
    }

    /**
     * Individual enricher health information.
     *
     * @param providerName the provider name
     * @param type the enrichment type
     * @param tenantId the tenant ID
     * @param healthy whether the enricher is healthy
     * @param message health status message
     * @param priority the enricher priority
     * @param tags the enricher tags
     */
    public record EnricherHealthInfo(
        @Parameter(description = "Provider name")
        String providerName,

        @Parameter(description = "Enrichment type")
        String type,

        @Parameter(description = "Tenant ID")
        UUID tenantId,

        @Parameter(description = "Whether the enricher is healthy")
        boolean healthy,

        @Parameter(description = "Health status message")
        String message,

        @Parameter(description = "Enricher priority")
        int priority,

        @Parameter(description = "Enricher tags")
        List<String> tags
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String providerName;
            private String type;
            private UUID tenantId;
            private boolean healthy;
            private String message;
            private int priority;
            private List<String> tags;

            public Builder providerName(String providerName) {
                this.providerName = providerName;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder tenantId(UUID tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder healthy(boolean healthy) {
                this.healthy = healthy;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder priority(int priority) {
                this.priority = priority;
                return this;
            }

            public Builder tags(List<String> tags) {
                this.tags = tags;
                return this;
            }

            public EnricherHealthInfo build() {
                return new EnricherHealthInfo(providerName, type, tenantId, healthy, message, priority, tags);
            }
        }
    }
}

