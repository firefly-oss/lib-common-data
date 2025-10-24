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

import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

/**
 * Auto-configuration for data enrichment components.
 *
 * <p>This configuration automatically sets up:</p>
 * <ul>
 *   <li>Data enrichment properties</li>
 *   <li>Data enricher registry for discovering enrichers</li>
 * </ul>
 *
 * <p>Note: EnrichmentEventPublisher is auto-discovered via @Service annotation
 * and is conditionally created based on firefly.data.enrichment.publish-events property.</p>
 *
 * <p>The configuration is activated when:</p>
 * <ul>
 *   <li>The property {@code firefly.data.enrichment.enabled} is true (default)</li>
 *   <li>Or the property is not set (enabled by default)</li>
 * </ul>
 *
 * <p><b>Example Configuration:</b></p>
 * <pre>{@code
 * firefly:
 *   data:
 *     enrichment:
 *       enabled: true
 *       publish-events: true
 *       cache-enabled: false
 *       default-timeout-seconds: 30
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DataEnrichmentProperties.class)
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ComponentScan(basePackages = "com.firefly.common.data")
public class DataEnrichmentAutoConfiguration {
    
    public DataEnrichmentAutoConfiguration() {
        log.info("Initializing Data Enrichment Auto-Configuration");
    }
    
    /**
     * Creates the data enricher registry bean.
     *
     * <p>This registry automatically discovers all DataEnricher beans
     * and provides methods to look them up by provider name or enrichment type.</p>
     *
     * <p>Spring will inject all DataEnricher beans into the List parameter.</p>
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "firefly.data.enrichment",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public DataEnricherRegistry dataEnricherRegistry(List<DataEnricher> enrichers) {
        log.info("Creating DataEnricherRegistry bean with {} enrichers", enrichers.size());
        return new DataEnricherRegistry(enrichers);
    }
}
