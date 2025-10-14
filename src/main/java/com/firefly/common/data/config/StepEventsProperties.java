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
 * Configuration properties for SAGA Step Events in core-data microservices.
 * <p>
 * Step Events from lib-transactional-engine are published through lib-common-eda's
 * EventPublisher infrastructure via the StepEventPublisherBridge. This allows step
 * events from data processing SAGAs to leverage all EDA features including multi-platform 
 * support, resilience patterns, metrics, and health checks.
 * <p>
 * This is particularly useful when data processing jobs are orchestrated using SAGAs
 * to coordinate distributed operations across multiple services or job stages.
 */
@ConfigurationProperties(prefix = "firefly.stepevents")
@Data
public class StepEventsProperties {

    /**
     * Whether Step Events are enabled for data processing SAGAs.
     * <p>
     * When enabled, the StepEventPublisherBridge will be configured to publish
     * step events from data processing workflows through the EDA infrastructure.
     * <p>
     * Default: true
     */
    private boolean enabled = true;

    /**
     * The default topic/destination for step events from data processing SAGAs.
     * <p>
     * This topic will be used when a step event doesn't specify its own topic.
     * The actual messaging platform (Kafka, RabbitMQ, SQS, etc.) is determined by
     * the lib-common-eda configuration.
     * <p>
     * It's recommended to use a separate topic for data processing step events
     * to enable independent scaling and monitoring.
     * <p>
     * Default: "data-processing-step-events"
     */
    private String topic = "data-processing-step-events";

    /**
     * Whether to include detailed job context in step events.
     * <p>
     * When enabled, additional context about the data processing job
     * (execution ID, job type, parameters) will be included in step event headers.
     * <p>
     * Default: true
     */
    private boolean includeJobContext = true;
}
