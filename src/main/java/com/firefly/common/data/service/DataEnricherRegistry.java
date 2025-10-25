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

package com.firefly.common.data.service;

import com.firefly.common.data.enrichment.EnricherMetadataReader;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing and discovering data enrichers.
 *
 * <p>This registry automatically discovers all {@link DataEnricher} implementations
 * in the Spring context and provides methods to look them up by provider name
 * or enrichment type.</p>
 *
 * <p>This follows the Registry pattern used in the library (similar to JobResultMapperRegistry).</p>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * @Service
 * public class EnrichmentService {
 *
 *     private final DataEnricherRegistry enricherRegistry;
 *
 *     public Mono<EnrichmentResponse> enrichCompanyData(EnrichmentRequest request) {
 *         // Get enricher by type
 *         DataEnricher<?, ?, ?> enricher = enricherRegistry
 *             .getEnricherForType(request.getEnrichmentType())
 *             .orElseThrow(() -> new EnricherNotFoundException(
 *                 "No enricher found for type: " + request.getEnrichmentType()));
 *
 *         return enricher.enrich(request);
 *     }
 *
 *     public Mono<EnrichmentResponse> enrichWithSpecificProvider(
 *             EnrichmentRequest request,
 *             String providerName) {
 *         // Get enricher by provider name
 *         DataEnricher<?, ?, ?> enricher = enricherRegistry
 *             .getEnricherByProvider(providerName)
 *             .orElseThrow(() -> new EnricherNotFoundException(
 *                 "No enricher found for provider: " + providerName));
 *
 *         return enricher.enrich(request);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Auto-Discovery:</b></p>
 * <p>All Spring beans extending {@link DataEnricher} are automatically
 * registered when the application context starts.</p>
 *
 * <p><b>Note:</b> This class is registered as a bean in {@link com.firefly.common.data.config.DataEnrichmentAutoConfiguration}
 * and should NOT be annotated with @Component to avoid duplicate bean creation.</p>
 */
@Slf4j
public class DataEnricherRegistry {

    private final Map<String, List<DataEnricher<?, ?, ?>>> enrichersByProvider = new ConcurrentHashMap<>();
    private final Map<String, List<DataEnricher<?, ?, ?>>> enrichersByType = new ConcurrentHashMap<>();
    private final List<DataEnricher<?, ?, ?>> allEnrichers;

    /**
     * Constructor that auto-discovers all DataEnricher beans.
     *
     * @param enrichers list of all DataEnricher beans in the Spring context
     */
    public DataEnricherRegistry(List<DataEnricher<?, ?, ?>> enrichers) {
        this.allEnrichers = enrichers;
        registerEnrichers(enrichers);
    }

    /**
     * Registers all enrichers in the registry.
     */
    private void registerEnrichers(List<DataEnricher<?, ?, ?>> enrichers) {
        log.info("Registering {} data enrichers", enrichers.size());

        for (DataEnricher<?, ?, ?> enricher : enrichers) {
            String providerName = enricher.getProviderName();

            // Register by provider name (case-insensitive)
            if (providerName != null && !providerName.isEmpty()) {
                enrichersByProvider.computeIfAbsent(providerName.toLowerCase(), k -> new ArrayList<>())
                        .add(enricher);
                log.debug("Registered enricher for provider: {}", providerName);
            }

            // Register by supported enrichment types
            List<String> supportedTypes = enricher.getSupportedEnrichmentTypes();
            if (supportedTypes != null) {
                for (String type : supportedTypes) {
                    if (type != null && !type.isEmpty()) {
                        enrichersByType.computeIfAbsent(type, k -> new ArrayList<>())
                                .add(enricher);
                        log.debug("Registered enricher '{}' for type: {}",
                                providerName, type);
                    }
                }
            }
        }

        log.info("Data enricher registration complete. " +
                "Providers: {}, Types: {}",
                enrichersByProvider.size(),
                enrichersByType.size());
    }
    
    /**
     * Gets an enricher by provider name.
     * If multiple enrichers exist for the same provider, returns the first one registered.
     *
     * @param providerName the provider name (e.g., "Financial Data Provider", "Credit Bureau Provider")
     * @return Optional containing the enricher, or empty if not found
     */
    public Optional<DataEnricher<?, ?, ?>> getEnricherByProvider(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return Optional.empty();
        }
        List<DataEnricher<?, ?, ?>> enrichers = enrichersByProvider.get(providerName.toLowerCase());
        return enrichers != null && !enrichers.isEmpty()
                ? Optional.of(enrichers.get(0))
                : Optional.empty();
    }

    /**
     * Gets an enricher that supports the specified enrichment type.
     * If multiple enrichers support the same type, returns the one with highest priority.
     *
     * @param enrichmentType the enrichment type (e.g., "company-profile", "credit-report")
     * @return Optional containing the enricher, or empty if not found
     */
    public Optional<DataEnricher<?, ?, ?>> getEnricherForType(String enrichmentType) {
        if (enrichmentType == null || enrichmentType.isEmpty()) {
            return Optional.empty();
        }
        List<DataEnricher<?, ?, ?>> enrichers = enrichersByType.get(enrichmentType);
        if (enrichers == null || enrichers.isEmpty()) {
            return Optional.empty();
        }
        // Return the enricher with highest priority
        return enrichers.stream()
                .max(Comparator.comparingInt(DataEnricher::getPriority));
    }

    /**
     * Gets all registered enrichers.
     *
     * @return list of all enrichers
     */
    public List<DataEnricher<?, ?, ?>> getAllEnrichers() {
        return allEnrichers;
    }

    /**
     * Gets all registered provider names.
     *
     * @return list of provider names
     */
    public List<String> getAllProviderNames() {
        return allEnrichers.stream()
                .map(DataEnricher::getProviderName)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all registered enrichment types.
     *
     * @return list of enrichment types
     */
    public List<String> getAllEnrichmentTypes() {
        return enrichersByType.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if an enricher exists for the specified provider.
     *
     * @param providerName the provider name
     * @return true if an enricher exists, false otherwise
     */
    public boolean hasEnricherForProvider(String providerName) {
        return getEnricherByProvider(providerName).isPresent();
    }
    
    /**
     * Checks if an enricher exists for the specified enrichment type.
     *
     * @param enrichmentType the enrichment type
     * @return true if an enricher exists, false otherwise
     */
    public boolean hasEnricherForType(String enrichmentType) {
        return getEnricherForType(enrichmentType).isPresent();
    }
    
    /**
     * Gets the number of registered enrichers.
     *
     * @return the count of enrichers
     */
    public int getEnricherCount() {
        return allEnrichers.size();
    }

    /**
     * Gets all enrichers for a specific tenant.
     *
     * @param tenantId the tenant UUID
     * @return list of enrichers for the tenant
     */
    public List<DataEnricher<?, ?, ?>> getEnrichersByTenant(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return allEnrichers.stream()
                .filter(enricher -> tenantId.equals(EnricherMetadataReader.getTenantId(enricher)))
                .collect(Collectors.toList());
    }

    /**
     * Gets all enrichers for a specific enrichment type.
     *
     * @param enrichmentType the enrichment type
     * @return list of enrichers that support the type
     */
    public List<DataEnricher<?, ?, ?>> getAllEnrichersForType(String enrichmentType) {
        if (enrichmentType == null || enrichmentType.isEmpty()) {
            return Collections.emptyList();
        }
        return allEnrichers.stream()
                .filter(enricher -> {
                    List<String> supportedTypes = enricher.getSupportedEnrichmentTypes();
                    return supportedTypes != null && supportedTypes.contains(enrichmentType);
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets all enrichers for a specific enrichment type and tenant.
     *
     * @param enrichmentType the enrichment type
     * @param tenantId the tenant UUID
     * @return list of enrichers that match both criteria
     */
    public List<DataEnricher<?, ?, ?>> getAllEnrichersForTypeAndTenant(String enrichmentType, UUID tenantId) {
        if (enrichmentType == null || enrichmentType.isEmpty() || tenantId == null) {
            return Collections.emptyList();
        }
        return allEnrichers.stream()
                .filter(enricher -> {
                    List<String> supportedTypes = enricher.getSupportedEnrichmentTypes();
                    UUID enricherTenantId = EnricherMetadataReader.getTenantId(enricher);
                    return supportedTypes != null &&
                           supportedTypes.contains(enrichmentType) &&
                           tenantId.equals(enricherTenantId);
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets the highest priority enricher for a specific enrichment type and tenant.
     *
     * @param enrichmentType the enrichment type
     * @param tenantId the tenant UUID
     * @return Optional containing the highest priority enricher, or empty if not found
     */
    public Optional<DataEnricher<?, ?, ?>> getEnricherForTypeAndTenant(String enrichmentType, UUID tenantId) {
        return getAllEnrichersForTypeAndTenant(enrichmentType, tenantId).stream()
                .max(Comparator.comparingInt(DataEnricher::getPriority));
    }
}

