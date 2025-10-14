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
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Main configuration properties for firefly.data namespace.
 * This is the root configuration that encompasses all data library features.
 */
@ConfigurationProperties(prefix = "firefly.data")
@Data
public class DataConfiguration {
    
    /**
     * Event-Driven Architecture settings.
     */
    @NestedConfigurationProperty
    private EdaConfig eda = new EdaConfig();
    
    /**
     * CQRS pattern settings.
     */
    @NestedConfigurationProperty
    private CqrsConfig cqrs = new CqrsConfig();
    
    /**
     * Job orchestration settings.
     */
    @NestedConfigurationProperty
    private JobOrchestrationProperties orchestration = new JobOrchestrationProperties();
    
    /**
     * SAGA/transactional settings.
     */
    @NestedConfigurationProperty
    private TransactionalConfig transactional = new TransactionalConfig();
    
    @Data
    public static class EdaConfig {
        /**
         * Enable EDA integration (default: true).
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class CqrsConfig {
        /**
         * Enable CQRS integration (default: true).
         */
        private boolean enabled = true;
    }
    
    @Data
    public static class TransactionalConfig {
        /**
         * Enable SAGA support (default: true).
         */
        private boolean enabled = true;
    }
}