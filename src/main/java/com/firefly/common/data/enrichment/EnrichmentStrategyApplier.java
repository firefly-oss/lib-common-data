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

package com.firefly.common.data.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.model.EnrichmentStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for applying enrichment strategies to DTOs.
 * 
 * <p>This class provides automatic strategy application without requiring
 * developers to manually implement strategy logic in each enricher.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // In your enricher's doEnrich method:
 * CompanyProfileDTO enriched = EnrichmentStrategyApplier.apply(
 *     request.getStrategy(),
 *     request.getSourceDto(),
 *     providerData,
 *     CompanyProfileDTO.class
 * );
 * }</pre>
 * 
 * <p><b>Supported Strategies:</b></p>
 * <ul>
 *   <li><b>ENHANCE</b> - Fill only null/empty fields from provider data</li>
 *   <li><b>MERGE</b> - Combine source and provider data (provider wins conflicts)</li>
 *   <li><b>REPLACE</b> - Use provider data entirely, ignore source</li>
 *   <li><b>RAW</b> - Return provider data as-is without transformation</li>
 * </ul>
 */
@Slf4j
public class EnrichmentStrategyApplier {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Applies the enrichment strategy to combine source and provider data.
     *
     * @param strategy the enrichment strategy to apply
     * @param sourceDto the source DTO (may be null for REPLACE/RAW)
     * @param providerData the provider response data
     * @param targetClass the target DTO class
     * @param <T> the target type
     * @return the enriched DTO
     */
    @SuppressWarnings("unchecked")
    public static <T> T apply(EnrichmentStrategy strategy,
                             Object sourceDto,
                             Object providerData,
                             Class<T> targetClass) {
        
        log.debug("Applying enrichment strategy: {} for target class: {}", strategy, targetClass.getSimpleName());
        
        switch (strategy) {
            case ENHANCE:
                return enhance(sourceDto, providerData, targetClass);
            
            case MERGE:
                return merge(sourceDto, providerData, targetClass);
            
            case REPLACE:
                return replace(providerData, targetClass);
            
            case RAW:
                return (T) providerData;
            
            default:
                throw new IllegalArgumentException("Unsupported enrichment strategy: " + strategy);
        }
    }
    
    /**
     * ENHANCE strategy: Fill only null/empty fields from provider data.
     * 
     * <p>This preserves all existing data in the source DTO and only adds
     * information from the provider where fields are null or empty.</p>
     *
     * @param sourceDto the source DTO
     * @param providerData the provider data
     * @param targetClass the target class
     * @param <T> the target type
     * @return the enhanced DTO
     */
    @SuppressWarnings("unchecked")
    public static <T> T enhance(Object sourceDto, Object providerData, Class<T> targetClass) {
        if (sourceDto == null) {
            return replace(providerData, targetClass);
        }
        
        try {
            // Convert both to maps for easier manipulation
            Map<String, Object> sourceMap = objectToMap(sourceDto);
            Map<String, Object> providerMap = objectToMap(providerData);
            
            // Create result map starting with source
            Map<String, Object> resultMap = new HashMap<>(sourceMap);
            
            // Only add provider fields if they don't exist or are null in source
            for (Map.Entry<String, Object> entry : providerMap.entrySet()) {
                String key = entry.getKey();
                Object providerValue = entry.getValue();
                
                if (!resultMap.containsKey(key) || resultMap.get(key) == null) {
                    resultMap.put(key, providerValue);
                    log.trace("Enhanced field '{}' with provider value", key);
                }
            }
            
            // Convert back to target class
            return mapToObject(resultMap, targetClass);
            
        } catch (Exception e) {
            log.error("Error applying ENHANCE strategy", e);
            throw new EnrichmentStrategyException("Failed to apply ENHANCE strategy", e);
        }
    }
    
    /**
     * MERGE strategy: Combine source and provider data with provider taking precedence.
     * 
     * <p>This performs a deep merge where provider data overwrites source data
     * for conflicting fields.</p>
     *
     * @param sourceDto the source DTO
     * @param providerData the provider data
     * @param targetClass the target class
     * @param <T> the target type
     * @return the merged DTO
     */
    public static <T> T merge(Object sourceDto, Object providerData, Class<T> targetClass) {
        if (sourceDto == null) {
            return replace(providerData, targetClass);
        }
        
        try {
            // Convert both to maps
            Map<String, Object> sourceMap = objectToMap(sourceDto);
            Map<String, Object> providerMap = objectToMap(providerData);
            
            // Create result map starting with source
            Map<String, Object> resultMap = new HashMap<>(sourceMap);
            
            // Overwrite with all provider fields (provider wins)
            for (Map.Entry<String, Object> entry : providerMap.entrySet()) {
                String key = entry.getKey();
                Object providerValue = entry.getValue();
                
                if (providerValue != null) {
                    resultMap.put(key, providerValue);
                    log.trace("Merged field '{}' with provider value", key);
                }
            }
            
            // Convert back to target class
            return mapToObject(resultMap, targetClass);
            
        } catch (Exception e) {
            log.error("Error applying MERGE strategy", e);
            throw new EnrichmentStrategyException("Failed to apply MERGE strategy", e);
        }
    }
    
    /**
     * REPLACE strategy: Use provider data entirely, ignore source.
     *
     * @param providerData the provider data
     * @param targetClass the target class
     * @param <T> the target type
     * @return the provider data mapped to target class
     */
    public static <T> T replace(Object providerData, Class<T> targetClass) {
        if (providerData == null) {
            throw new EnrichmentStrategyException("Provider data cannot be null for REPLACE strategy");
        }
        
        try {
            // If provider data is already the target class, return it
            if (targetClass.isInstance(providerData)) {
                return targetClass.cast(providerData);
            }
            
            // Otherwise, convert it
            return objectMapper.convertValue(providerData, targetClass);
            
        } catch (Exception e) {
            log.error("Error applying REPLACE strategy", e);
            throw new EnrichmentStrategyException("Failed to apply REPLACE strategy", e);
        }
    }
    
    /**
     * Converts an object to a Map using Jackson.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        
        return objectMapper.convertValue(obj, Map.class);
    }
    
    /**
     * Converts a Map to an object using Jackson.
     */
    private static <T> T mapToObject(Map<String, Object> map, Class<T> targetClass) {
        return objectMapper.convertValue(map, targetClass);
    }
    
    /**
     * Counts the number of fields that were enriched.
     * 
     * <p>This is useful for populating the fieldsEnriched count in the response.</p>
     *
     * @param sourceDto the original source DTO
     * @param enrichedDto the enriched DTO
     * @return the number of fields that were added or modified
     */
    public static int countEnrichedFields(Object sourceDto, Object enrichedDto) {
        if (sourceDto == null) {
            return countNonNullFields(enrichedDto);
        }
        
        try {
            Map<String, Object> sourceMap = objectToMap(sourceDto);
            Map<String, Object> enrichedMap = objectToMap(enrichedDto);
            
            int count = 0;
            for (Map.Entry<String, Object> entry : enrichedMap.entrySet()) {
                String key = entry.getKey();
                Object enrichedValue = entry.getValue();
                Object sourceValue = sourceMap.get(key);
                
                // Count if field was added or changed
                if (sourceValue == null && enrichedValue != null) {
                    count++;
                } else if (sourceValue != null && !sourceValue.equals(enrichedValue)) {
                    count++;
                }
            }
            
            return count;
            
        } catch (Exception e) {
            log.warn("Error counting enriched fields", e);
            return 0;
        }
    }
    
    /**
     * Counts non-null fields in an object.
     */
    private static int countNonNullFields(Object obj) {
        if (obj == null) {
            return 0;
        }
        
        try {
            Map<String, Object> map = objectToMap(obj);
            return (int) map.values().stream()
                    .filter(value -> value != null)
                    .count();
        } catch (Exception e) {
            log.warn("Error counting non-null fields", e);
            return 0;
        }
    }
    
    /**
     * Exception thrown when strategy application fails.
     */
    public static class EnrichmentStrategyException extends RuntimeException {
        public EnrichmentStrategyException(String message) {
            super(message);
        }
        
        public EnrichmentStrategyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

