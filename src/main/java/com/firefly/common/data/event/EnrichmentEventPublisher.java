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

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing enrichment lifecycle events through Spring's event mechanism.
 * 
 * <p>These events can be consumed by EDA components or other parts of the system
 * for observability, auditing, and monitoring purposes.</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment",
    name = "publish-events",
    havingValue = "true",
    matchIfMissing = true
)
public class EnrichmentEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    public EnrichmentEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Publishes an enrichment started event.
     */
    public void publishEnrichmentStarted(EnrichmentRequest request, String providerName) {
        EnrichmentEvent event = EnrichmentEvent.builder()
                .eventType("ENRICHMENT_STARTED")
                .enrichmentType(request.getType())
                .providerName(providerName)
                .requestId(request.getRequestId())
                .tenantId(request.getTenantId() != null ? request.getTenantId().toString() : null)
                .success(true)
                .message("Enrichment started")
                .timestamp(Instant.now())
                .metadata(request.getMetadata())
                .build();
        
        publishEvent(event);
        log.debug("Published enrichment started event: type={}, provider={}, requestId={}",
                request.getType(), providerName, request.getRequestId());
    }
    
    /**
     * Publishes an enrichment completed event.
     */
    public void publishEnrichmentCompleted(EnrichmentRequest request, 
                                          EnrichmentResponse response,
                                          long durationMillis) {
        EnrichmentEvent event = EnrichmentEvent.builder()
                .eventType("ENRICHMENT_COMPLETED")
                .enrichmentType(response.getType())
                .providerName(response.getProviderName())
                .requestId(response.getRequestId())
                .tenantId(request.getTenantId() != null ? request.getTenantId().toString() : null)
                .success(response.isSuccess())
                .message(response.getMessage())
                .timestamp(Instant.now())
                .durationMillis(durationMillis)
                .fieldsEnriched(response.getFieldsEnriched())
                .cost(response.getCost())
                .metadata(response.getMetadata())
                .build();
        
        publishEvent(event);
        log.debug("Published enrichment completed event: type={}, provider={}, success={}, duration={}ms",
                response.getType(), response.getProviderName(),
                response.isSuccess(), durationMillis);
    }
    
    /**
     * Publishes an enrichment failed event.
     */
    public void publishEnrichmentFailed(EnrichmentRequest request,
                                       String providerName,
                                       String error,
                                       Throwable exception,
                                       long durationMillis) {
        Map<String, String> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        if (exception != null) {
            metadata.put("exceptionType", exception.getClass().getSimpleName());
            metadata.put("exceptionMessage", exception.getMessage());
        }
        
        EnrichmentEvent event = EnrichmentEvent.builder()
                .eventType("ENRICHMENT_FAILED")
                .enrichmentType(request.getType())
                .providerName(providerName)
                .requestId(request.getRequestId())
                .tenantId(request.getTenantId() != null ? request.getTenantId().toString() : null)
                .success(false)
                .message("Enrichment failed")
                .error(error)
                .timestamp(Instant.now())
                .durationMillis(durationMillis)
                .metadata(metadata)
                .build();
        
        publishEvent(event);
        log.debug("Published enrichment failed event: type={}, provider={}, error={}",
                request.getType(), providerName, error);
    }
    
    /**
     * Publishes the event to the Spring event bus.
     */
    private void publishEvent(EnrichmentEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("Failed to publish enrichment event: {}", e.getMessage(), e);
        }
    }
}

