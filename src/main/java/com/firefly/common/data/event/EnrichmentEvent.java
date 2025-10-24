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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Event representing a data enrichment operation.
 * 
 * <p>This event is published during enrichment lifecycle for observability
 * and can be consumed by EDA components or other parts of the system.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichmentEvent {
    
    /**
     * Type of event (e.g., "ENRICHMENT_STARTED", "ENRICHMENT_COMPLETED", "ENRICHMENT_FAILED").
     */
    private String eventType;
    
    /**
     * Type of enrichment performed.
     */
    private String enrichmentType;
    
    /**
     * Name of the provider.
     */
    private String providerName;
    
    /**
     * Request ID for correlation.
     */
    private String requestId;
    
    /**
     * Tenant ID.
     */
    private String tenantId;
    
    /**
     * Success indicator.
     */
    private boolean success;
    
    /**
     * Message describing the event.
     */
    private String message;
    
    /**
     * Error message if failed.
     */
    private String error;
    
    /**
     * Timestamp of the event.
     */
    private Instant timestamp;
    
    /**
     * Duration of the enrichment operation in milliseconds.
     */
    private Long durationMillis;
    
    /**
     * Number of fields enriched.
     */
    private Integer fieldsEnriched;
    
    /**
     * Cost of the operation.
     */
    private Double cost;
    
    /**
     * Additional metadata.
     */
    private Map<String, String> metadata;
}

