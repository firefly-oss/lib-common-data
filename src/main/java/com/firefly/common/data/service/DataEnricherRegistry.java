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
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *         DataEnricher enricher = enricherRegistry
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
 *         DataEnricher enricher = enricherRegistry
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
 * <p>All Spring beans implementing {@link DataEnricher} are automatically
 * registered when the application context starts.</p>
 *
 * <p><b>Note:</b> This class is registered as a bean in {@link com.firefly.common.data.config.DataEnrichmentAutoConfiguration}
 * and should NOT be annotated with @Component to avoid duplicate bean creation.</p>
 */
@Slf4j
public class DataEnricherRegistry {
    
    private final Map<String, DataEnricher> enrichersByProvider = new ConcurrentHashMap<>();
    private final Map<String, DataEnricher> enrichersByType = new ConcurrentHashMap<>();
    private final List<DataEnricher> allEnrichers;
    
    /**
     * Constructor that auto-discovers all DataEnricher beans.
     *
     * @param enrichers list of all DataEnricher beans in the Spring context
     */
    public DataEnricherRegistry(List<DataEnricher> enrichers) {
        this.allEnrichers = enrichers;
        registerEnrichers(enrichers);
    }
    
    /**
     * Registers all enrichers in the registry.
     */
    private void registerEnrichers(List<DataEnricher> enrichers) {
        log.info("Registering {} data enrichers", enrichers.size());
        
        for (DataEnricher enricher : enrichers) {
            String providerName = enricher.getProviderName();
            
            // Register by provider name (case-insensitive)
            if (providerName != null && !providerName.isEmpty()) {
                enrichersByProvider.put(providerName.toLowerCase(), enricher);
                log.debug("Registered enricher for provider: {}", providerName);
            }
            
            // Register by supported enrichment types
            String[] supportedTypes = enricher.getSupportedEnrichmentTypes();
            if (supportedTypes != null) {
                for (String type : supportedTypes) {
                    if (type != null && !type.isEmpty()) {
                        if (enrichersByType.containsKey(type)) {
                            log.warn("Multiple enrichers registered for type '{}'. " +
                                    "Using the first one: {}", 
                                    type, enrichersByType.get(type).getProviderName());
                        } else {
                            enrichersByType.put(type, enricher);
                            log.debug("Registered enricher '{}' for type: {}", 
                                    providerName, type);
                        }
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
     *
     * @param providerName the provider name (e.g., "Financial Data Provider", "Credit Bureau Provider")
     * @return Optional containing the enricher, or empty if not found
     */
    public Optional<DataEnricher> getEnricherByProvider(String providerName) {
        if (providerName == null || providerName.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(enrichersByProvider.get(providerName.toLowerCase()));
    }
    
    /**
     * Gets an enricher that supports the specified enrichment type.
     *
     * @param enrichmentType the enrichment type (e.g., "company-profile", "credit-report")
     * @return Optional containing the enricher, or empty if not found
     */
    public Optional<DataEnricher> getEnricherForType(String enrichmentType) {
        if (enrichmentType == null || enrichmentType.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(enrichersByType.get(enrichmentType));
    }
    
    /**
     * Gets all registered enrichers.
     *
     * @return list of all enrichers
     */
    public List<DataEnricher> getAllEnrichers() {
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
}

