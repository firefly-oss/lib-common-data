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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for lib-common-eda integration with lib-common-data.
 *
 * This configuration enables event-driven architecture support for core-data microservices,
 * allowing them to publish and consume events related to data processing jobs.
 */
@Configuration(value = "dataEdaAutoConfiguration")
@ConditionalOnClass(name = {
    "com.firefly.common.eda.publisher.EventPublisher",
    "com.firefly.common.eda.subscriber.EventSubscriber"
})
@ConditionalOnProperty(prefix = "firefly.data.eda", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
    "com.firefly.common.eda"
})
@Slf4j
public class EdaAutoConfiguration {

    public EdaAutoConfiguration() {
        log.info("Enabling lib-common-eda integration for lib-common-data");
    }
}
