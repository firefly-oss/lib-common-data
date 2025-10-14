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

import com.firefly.common.data.health.JobOrchestratorHealthIndicator;
import com.firefly.common.data.mapper.JobResultMapperRegistry;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
@EnableConfigurationProperties({JobOrchestrationProperties.class, DataConfiguration.class})
@ComponentScan(basePackages = {
    "com.firefly.common.data.orchestration",
    "com.firefly.common.data.service",
    "com.firefly.common.data.controller",
    "com.firefly.common.data.health",
    "com.firefly.common.data.observability",
    "com.firefly.common.data.resiliency",
    "com.firefly.common.data.mapper",
    "com.firefly.common.data.event"
})
@Slf4j
public class JobOrchestrationAutoConfiguration {
    
    public JobOrchestrationAutoConfiguration(JobOrchestrationProperties properties) {
        log.info("Enabling job orchestration for lib-common-data with orchestrator type: {}", 
                properties.getOrchestratorType());
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(MeterRegistry.class)
    public JobMetricsService jobMetricsService(MeterRegistry meterRegistry, 
                                              JobOrchestrationProperties properties) {
        log.debug("Creating JobMetricsService with prefix: {}", properties.getObservability().getMetricPrefix());
        return new JobMetricsService(meterRegistry, properties);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(ObservationRegistry.class)
    public JobTracingService jobTracingService(ObservationRegistry observationRegistry, JobOrchestrationProperties properties) {
        log.debug("Creating JobTracingService with tracing enabled: {}", properties.getObservability().isTracingEnabled());
        return new JobTracingService(observationRegistry, properties);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ResiliencyDecoratorService resiliencyDecoratorService(JobOrchestrationProperties properties) {
        log.debug("Creating ResiliencyDecoratorService");
        return new ResiliencyDecoratorService(properties);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JobResultMapperRegistry jobResultMapperRegistry() {
        log.debug("Creating JobResultMapperRegistry");
        return new JobResultMapperRegistry();
    }
    
    @Bean("jobOrchestratorHealthIndicator")
    @ConditionalOnMissingBean(name = "jobOrchestratorHealthIndicator")
    @ConditionalOnProperty(name = "firefly.data.orchestration.health-check.enabled", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public JobOrchestratorHealthIndicator jobOrchestratorHealthIndicator(JobOrchestrationProperties properties) {
        log.debug("Creating JobOrchestratorHealthIndicator");
        return new JobOrchestratorHealthIndicator(properties);
    }
}
