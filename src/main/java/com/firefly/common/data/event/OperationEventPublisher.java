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

package com.firefly.common.data.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing provider operation lifecycle events through Spring's event mechanism.
 * 
 * <p>These events can be consumed by EDA components or other parts of the system
 * for observability, auditing, and monitoring purposes.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * operationEventPublisher.publishOperationStarted(
 *     "search-company",
 *     "Provider A",
 *     "req-123",
 *     "tenant-1"
 * );
 * }</pre>
 */
@Service
@Slf4j
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.operations",
    name = "publish-events",
    havingValue = "true",
    matchIfMissing = true
)
public class OperationEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public OperationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Publishes an operation started event.
     *
     * @param operationId the operation ID
     * @param providerName the provider name
     * @param requestId the request ID for correlation
     * @param tenantId the tenant ID
     */
    public void publishOperationStarted(String operationId, 
                                       String providerName,
                                       String requestId,
                                       String tenantId) {
        OperationEvent event = OperationEvent.builder()
                .eventType("OPERATION_STARTED")
                .operationId(operationId)
                .providerName(providerName)
                .requestId(requestId)
                .tenantId(tenantId)
                .success(true)
                .message("Operation started")
                .timestamp(Instant.now())
                .fromCache(false)
                .build();
        
        publishEvent(event);
        log.debug("Published operation started event: operation={}, provider={}, requestId={}", 
                operationId, providerName, requestId);
    }
    
    /**
     * Publishes an operation completed event.
     *
     * @param operationId the operation ID
     * @param providerName the provider name
     * @param requestId the request ID for correlation
     * @param tenantId the tenant ID
     * @param durationMillis duration in milliseconds
     * @param fromCache whether the result was served from cache
     */
    public void publishOperationCompleted(String operationId,
                                         String providerName,
                                         String requestId,
                                         String tenantId,
                                         long durationMillis,
                                         boolean fromCache) {
        OperationEvent event = OperationEvent.builder()
                .eventType("OPERATION_COMPLETED")
                .operationId(operationId)
                .providerName(providerName)
                .requestId(requestId)
                .tenantId(tenantId)
                .success(true)
                .message("Operation completed successfully")
                .timestamp(Instant.now())
                .durationMillis(durationMillis)
                .fromCache(fromCache)
                .build();
        
        publishEvent(event);
        log.debug("Published operation completed event: operation={}, provider={}, duration={}ms, fromCache={}", 
                operationId, providerName, durationMillis, fromCache);
    }
    
    /**
     * Publishes an operation failed event.
     *
     * @param operationId the operation ID
     * @param providerName the provider name
     * @param requestId the request ID for correlation
     * @param tenantId the tenant ID
     * @param error the error message
     * @param exception the exception (optional)
     * @param durationMillis duration in milliseconds
     */
    public void publishOperationFailed(String operationId,
                                      String providerName,
                                      String requestId,
                                      String tenantId,
                                      String error,
                                      Throwable exception,
                                      long durationMillis) {
        Map<String, String> metadata = new HashMap<>();
        if (exception != null) {
            metadata.put("exceptionType", exception.getClass().getSimpleName());
            metadata.put("exceptionMessage", exception.getMessage());
        }
        
        OperationEvent event = OperationEvent.builder()
                .eventType("OPERATION_FAILED")
                .operationId(operationId)
                .providerName(providerName)
                .requestId(requestId)
                .tenantId(tenantId)
                .success(false)
                .message("Operation failed")
                .error(error)
                .timestamp(Instant.now())
                .durationMillis(durationMillis)
                .fromCache(false)
                .metadata(metadata)
                .build();
        
        publishEvent(event);
        log.debug("Published operation failed event: operation={}, provider={}, error={}", 
                operationId, providerName, error);
    }
    
    /**
     * Publishes an operation cached event.
     *
     * @param operationId the operation ID
     * @param providerName the provider name
     * @param requestId the request ID for correlation
     * @param tenantId the tenant ID
     */
    public void publishOperationCached(String operationId,
                                      String providerName,
                                      String requestId,
                                      String tenantId) {
        OperationEvent event = OperationEvent.builder()
                .eventType("OPERATION_CACHED")
                .operationId(operationId)
                .providerName(providerName)
                .requestId(requestId)
                .tenantId(tenantId)
                .success(true)
                .message("Operation result served from cache")
                .timestamp(Instant.now())
                .fromCache(true)
                .build();
        
        publishEvent(event);
        log.debug("Published operation cached event: operation={}, provider={}, requestId={}", 
                operationId, providerName, requestId);
    }
    
    /**
     * Publishes the event to the Spring event bus.
     */
    private void publishEvent(OperationEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish operation event: {}", e.getMessage(), e);
        }
    }
}

