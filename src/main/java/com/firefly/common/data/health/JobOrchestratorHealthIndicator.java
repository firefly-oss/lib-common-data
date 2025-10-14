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

package com.firefly.common.data.health;

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Health indicator for job orchestrator.
 */
@Component
@Slf4j
public class JobOrchestratorHealthIndicator implements ReactiveHealthIndicator {

    private final Optional<JobOrchestrator> orchestrator;
    private final JobOrchestrationProperties properties;
    private volatile Instant lastCheckTime;
    private volatile Health lastHealth;

    public JobOrchestratorHealthIndicator(Optional<JobOrchestrator> orchestrator,
                                          JobOrchestrationProperties properties) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.lastHealth = Health.unknown().build();
    }
    
    /**
     * Constructor without orchestrator for basic health checks.
     */
    public JobOrchestratorHealthIndicator(JobOrchestrationProperties properties) {
        this(Optional.empty(), properties);
    }

    @Override
    public Mono<Health> health() {
        if (!properties.getHealthCheck().isEnabled()) {
            return Mono.just(Health.up()
                    .withDetail("message", "Health checks disabled")
                    .build());
        }

        if (!properties.isEnabled()) {
            return Mono.just(Health.up()
                    .withDetail("message", "Job orchestration disabled")
                    .build());
        }

        if (orchestrator.isEmpty()) {
            return Mono.just(Health.down()
                    .withDetail("message", "No orchestrator configured")
                    .withDetail("orchestratorType", properties.getOrchestratorType())
                    .build());
        }

        // Use cached health if within interval
        if (lastCheckTime != null && 
            Duration.between(lastCheckTime, Instant.now()).compareTo(properties.getHealthCheck().getInterval()) < 0) {
            log.debug("Returning cached health status");
            return Mono.just(lastHealth);
        }

        return performHealthCheck()
                .timeout(properties.getHealthCheck().getTimeout())
                .doOnNext(health -> {
                    lastHealth = health;
                    lastCheckTime = Instant.now();
                })
                .onErrorResume(error -> {
                    log.error("Health check failed: {}", error.getMessage());
                    Health errorHealth = Health.down()
                            .withDetail("error", error.getMessage())
                            .withDetail("orchestratorType", properties.getOrchestratorType())
                            .build();
                    lastHealth = errorHealth;
                    lastCheckTime = Instant.now();
                    return Mono.just(errorHealth);
                });
    }

    /**
     * Performs the actual health check.
     */
    private Mono<Health> performHealthCheck() {
        Map<String, Object> details = new HashMap<>();
        details.put("orchestratorType", orchestrator.get().getOrchestratorType());
        details.put("enabled", properties.isEnabled());

        if (properties.getHealthCheck().isShowDetails()) {
            details.put("defaultTimeout", properties.getDefaultTimeout().toString());
            details.put("maxRetries", properties.getMaxRetries());
            details.put("resiliency.circuitBreakerEnabled", properties.getResiliency().isCircuitBreakerEnabled());
            details.put("resiliency.retryEnabled", properties.getResiliency().isRetryEnabled());
            details.put("observability.tracingEnabled", properties.getObservability().isTracingEnabled());
            details.put("observability.metricsEnabled", properties.getObservability().isMetricsEnabled());
        }

        if (!properties.getHealthCheck().isCheckConnectivity()) {
            return Mono.just(Health.up()
                    .withDetails(details)
                    .withDetail("message", "Connectivity check disabled")
                    .build());
        }

        // Perform a simple connectivity check
        // This is a placeholder - actual implementation would depend on the orchestrator type
        return checkOrchestratorConnectivity()
                .map(connected -> {
                    if (connected) {
                        return Health.up()
                                .withDetails(details)
                                .withDetail("connectivity", "OK")
                                .build();
                    } else {
                        return Health.down()
                                .withDetails(details)
                                .withDetail("connectivity", "FAILED")
                                .build();
                    }
                });
    }

    /**
     * Checks orchestrator connectivity.
     * This is a simple check - actual implementation would vary by orchestrator type.
     */
    private Mono<Boolean> checkOrchestratorConnectivity() {
        // For now, we'll just check if the orchestrator is available
        // In a real implementation, this would make a lightweight API call to the orchestrator
        return Mono.just(orchestrator.isPresent())
                .doOnNext(present -> log.debug("Orchestrator connectivity check: {}", present ? "OK" : "FAILED"));
    }

    /**
     * Gets the last health check result.
     */
    public Health getLastHealth() {
        return lastHealth;
    }

    /**
     * Gets the last health check time.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }
}

