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
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.util.TracingContextExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * Auto-configuration for observability features including tracing, metrics, and health checks.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "firefly.data.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ObservabilityAutoConfiguration {

    /**
     * Creates the job tracing service.
     */
    @Bean
    @ConditionalOnClass(ObservationRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.observability", name = "tracing-enabled", havingValue = "true", matchIfMissing = true)
    public JobTracingService jobTracingService(ObservationRegistry observationRegistry,
                                               JobOrchestrationProperties properties,
                                               Optional<Tracer> tracer) {
        log.info("Configuring job tracing service with Micrometer Observation");

        // Configure the TracingContextExtractor with the tracer if available
        tracer.ifPresent(t -> {
            TracingContextExtractor.setTracer(t);
            log.info("Configured TracingContextExtractor with Tracer: {}", t.getClass().getSimpleName());
        });

        return new JobTracingService(observationRegistry, properties);
    }

    /**
     * Creates the job metrics service.
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.observability", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    public JobMetricsService jobMetricsService(MeterRegistry meterRegistry,
                                               JobOrchestrationProperties properties) {
        log.info("Configuring job metrics service with metric prefix: {}", 
                properties.getObservability().getMetricPrefix());
        return new JobMetricsService(meterRegistry, properties);
    }

    /**
     * Creates the job orchestrator health indicator.
     */
    @Bean
    @ConditionalOnEnabledHealthIndicator("jobOrchestrator")
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.health-check", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JobOrchestratorHealthIndicator jobOrchestratorHealthIndicator(
            Optional<JobOrchestrator> orchestrator,
            JobOrchestrationProperties properties) {
        log.info("Configuring job orchestrator health indicator");
        return new JobOrchestratorHealthIndicator(orchestrator, properties);
    }
}

