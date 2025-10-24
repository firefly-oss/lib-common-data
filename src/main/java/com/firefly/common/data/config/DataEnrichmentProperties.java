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

package com.firefly.common.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for data enrichment.
 * 
 * <p>These properties control the behavior of data enrichers, including
 * event publishing, caching, and provider-specific settings.</p>
 * 
 * <p><b>Example Configuration:</b></p>
 * <pre>{@code
 * firefly:
 *   data:
 *     enrichment:
 *       enabled: true
 *       publish-events: true
 *       cache-enabled: true
 *       cache-ttl-seconds: 3600
 *       default-timeout-seconds: 30
 *       capture-raw-responses: false
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "firefly.data.enrichment")
public class DataEnrichmentProperties {
    
    /**
     * Whether data enrichment is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Whether to publish enrichment events.
     */
    private boolean publishEvents = true;
    
    /**
     * Whether to enable caching of enrichment results.
     */
    private boolean cacheEnabled = false;
    
    /**
     * Time-to-live for cached enrichment results in seconds.
     */
    private int cacheTtlSeconds = 3600;
    
    /**
     * Default timeout for enrichment operations in seconds.
     */
    private int defaultTimeoutSeconds = 30;
    
    /**
     * Whether to capture raw provider responses for audit purposes.
     */
    private boolean captureRawResponses = false;
    
    /**
     * Maximum number of concurrent enrichment operations.
     */
    private int maxConcurrentEnrichments = 100;
    
    /**
     * Whether to enable automatic retry on failure.
     */
    private boolean retryEnabled = true;
    
    /**
     * Maximum number of retry attempts.
     */
    private int maxRetryAttempts = 3;
}

