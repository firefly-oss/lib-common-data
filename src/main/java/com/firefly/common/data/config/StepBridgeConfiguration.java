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

import com.firefly.common.data.stepevents.StepEventPublisherBridge;
import com.firefly.common.eda.publisher.EventPublisherFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for SAGA Step Event Bridge in core-data microservices.
 * <p>
 * This configuration creates a bridge between lib-transactional-engine's StepEventPublisher
 * and lib-common-eda's EventPublisher, allowing SAGA step events from data processing
 * workflows to be published through the unified EDA infrastructure.
 * <p>
 * This enables core-data microservices to:
 * <ul>
 *   <li>Publish step events from data processing SAGAs</li>
 *   <li>Leverage EDA features (resilience, metrics, multi-platform)</li>
 *   <li>Monitor and trace distributed data processing workflows</li>
 *   <li>React to step events in other microservices</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = {
    "com.firefly.transactional.saga.events.StepEventPublisher",
    "com.firefly.transactional.saga.events.StepEventEnvelope"
})
@ConditionalOnProperty(prefix = "firefly.stepevents", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(EventPublisherFactory.class)
@EnableConfigurationProperties(StepEventsProperties.class)
public class StepBridgeConfiguration {

    /**
     * Creates the StepEventPublisherBridge bean for data processing workflows.
     * <p>
     * This bridge uses the default EDA publisher configured in lib-common-eda to publish
     * step events from data processing SAGAs. The destination topic is configured via 
     * firefly.stepevents.topic property.
     * <p>
     * The bridge is marked as @Primary to ensure it's used by the transactional engine
     * when publishing step events.
     *
     * @param publisherFactory the EDA event publisher factory
     * @param properties the step events configuration properties
     * @return the configured StepEventPublisherBridge
     */
    @Bean
    @Primary
    public StepEventPublisherBridge stepEventPublisherBridge(
            EventPublisherFactory publisherFactory,
            StepEventsProperties properties) {

        String topic = properties.getTopic();
        log.info("Configuring StepEventPublisherBridge for lib-common-data with topic: {} (includeJobContext: {})", 
                topic, properties.isIncludeJobContext());

        return new StepEventPublisherBridge(topic, publisherFactory.getDefaultPublisher());
    }
}
