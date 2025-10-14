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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for job orchestration in lib-common-data.
 * 
 * This configuration enables the job orchestration infrastructure for core-data microservices,
 * providing support for workflow management through various orchestrators like AWS Step Functions.
 */
@Configuration
@ConditionalOnProperty(prefix = "firefly.data.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JobOrchestrationProperties.class)
@ComponentScan(basePackages = {
    "com.firefly.common.data.orchestration",
    "com.firefly.common.data.service",
    "com.firefly.common.data.controller"
})
@Slf4j
public class JobOrchestrationAutoConfiguration {
    
    public JobOrchestrationAutoConfiguration(JobOrchestrationProperties properties) {
        log.info("Enabling job orchestration for lib-common-data with orchestrator type: {}", 
                properties.getOrchestratorType());
    }
}
