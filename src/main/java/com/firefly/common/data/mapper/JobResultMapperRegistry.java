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

package com.firefly.common.data.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing job result mappers in core-data microservices.
 * 
 * This registry automatically discovers all JobResultMapper beans and makes them
 * available for the RESULT stage transformation. Mappers are indexed by their
 * target type for efficient lookup.
 * 
 * <p>Usage:</p>
 * <pre>
 * {@code
 * @Service
 * public class MyDataJobService implements DataJobService {
 *     
 *     @Autowired
 *     private JobResultMapperRegistry mapperRegistry;
 *     
 *     public Mono<JobStageResponse> getJobResult(JobStageRequest request) {
 *         String targetClassName = request.getTargetDtoClass();
 *         Class<?> targetClass = Class.forName(targetClassName);
 *         
 *         JobResultMapper mapper = mapperRegistry.getMapper(targetClass)
 *             .orElseThrow(() -> new MapperNotFoundException(targetClass));
 *             
 *         // Use mapper to transform raw data...
 *     }
 * }
 * }
 * </pre>
 */
@Component
@Slf4j
public class JobResultMapperRegistry {

    private final Map<Class<?>, JobResultMapper<?, ?>> mappersByTargetType = new ConcurrentHashMap<>();
    private final Map<String, JobResultMapper<?, ?>> mappersByName = new ConcurrentHashMap<>();

    /**
     * Constructor that auto-discovers all JobResultMapper beans.
     * 
     * @param mappers list of all JobResultMapper beans in the application context
     */
    public JobResultMapperRegistry(List<JobResultMapper<?, ?>> mappers) {
        log.info("Initializing JobResultMapperRegistry with {} mapper(s)", mappers.size());
        
        for (JobResultMapper<?, ?> mapper : mappers) {
            registerMapper(mapper);
        }
        
        log.info("JobResultMapperRegistry initialized successfully with {} mapper(s)", 
                mappersByTargetType.size());
    }
    
    /**
     * Default constructor for cases where no mappers are available.
     */
    public JobResultMapperRegistry() {
        log.info("Initializing JobResultMapperRegistry with no mappers");
    }

    /**
     * Registers a mapper in the registry.
     * 
     * @param mapper the mapper to register
     */
    private void registerMapper(JobResultMapper<?, ?> mapper) {
        Class<?> targetType = extractTargetType(mapper);
        
        if (targetType != null) {
            mappersByTargetType.put(targetType, mapper);
            String mapperName = mapper.getClass().getSimpleName();
            mappersByName.put(mapperName, mapper);
            
            log.debug("Registered mapper: {} for target type: {}", 
                     mapperName, targetType.getSimpleName());
        } else {
            log.warn("Could not determine target type for mapper: {}", 
                    mapper.getClass().getSimpleName());
        }
    }

    /**
     * Retrieves a mapper for the specified target type.
     * 
     * @param targetType the target DTO class
     * @return an Optional containing the mapper if found
     */
    public Optional<JobResultMapper<?, ?>> getMapper(Class<?> targetType) {
        JobResultMapper<?, ?> mapper = mappersByTargetType.get(targetType);
        
        if (mapper == null) {
            log.warn("No mapper found for target type: {}", targetType.getSimpleName());
            return Optional.empty();
        }
        
        log.debug("Found mapper for target type: {}", targetType.getSimpleName());
        return Optional.of(mapper);
    }

    /**
     * Retrieves a mapper by its class name.
     * 
     * @param mapperName the simple class name of the mapper
     * @return an Optional containing the mapper if found
     */
    public Optional<JobResultMapper<?, ?>> getMapperByName(String mapperName) {
        JobResultMapper<?, ?> mapper = mappersByName.get(mapperName);
        
        if (mapper == null) {
            log.warn("No mapper found with name: {}", mapperName);
            return Optional.empty();
        }
        
        return Optional.of(mapper);
    }

    /**
     * Checks if a mapper exists for the specified target type.
     * 
     * @param targetType the target DTO class
     * @return true if a mapper exists, false otherwise
     */
    public boolean hasMapper(Class<?> targetType) {
        return mappersByTargetType.containsKey(targetType);
    }

    /**
     * Gets all registered mappers.
     * 
     * @return a map of target types to their mappers
     */
    public Map<Class<?>, JobResultMapper<?, ?>> getAllMappers() {
        return Map.copyOf(mappersByTargetType);
    }

    /**
     * Extracts the target type from the mapper's generic parameters.
     * 
     * @param mapper the mapper instance
     * @return the target class or null if it cannot be determined
     */
    private Class<?> extractTargetType(JobResultMapper<?, ?> mapper) {
        try {
            // Try to call the mapper's getTargetType() method first
            return mapper.getTargetType();
        } catch (UnsupportedOperationException e) {
            // Fall back to reflection to extract generic type
            return extractTargetTypeViaReflection(mapper);
        }
    }

    /**
     * Extracts target type using reflection on generic interfaces.
     * 
     * @param mapper the mapper instance
     * @return the target class or null if it cannot be determined
     */
    private Class<?> extractTargetTypeViaReflection(JobResultMapper<?, ?> mapper) {
        Class<?> mapperClass = mapper.getClass();
        
        // Look for JobResultMapper interface in the class hierarchy
        for (Type genericInterface : mapperClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericInterface;
                
                if (paramType.getRawType().equals(JobResultMapper.class)) {
                    Type[] typeArguments = paramType.getActualTypeArguments();
                    
                    if (typeArguments.length >= 2 && typeArguments[1] instanceof Class) {
                        return (Class<?>) typeArguments[1];
                    }
                }
            }
        }
        
        return null;
    }
}
