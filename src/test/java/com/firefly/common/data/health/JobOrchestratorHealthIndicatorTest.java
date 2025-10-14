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
import com.firefly.common.data.orchestration.model.JobExecution;
import com.firefly.common.data.orchestration.model.JobExecutionRequest;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JobOrchestratorHealthIndicatorTest {

    private JobOrchestratorHealthIndicator healthIndicator;
    private JobOrchestrationProperties properties;
    private MockJobOrchestrator mockOrchestrator;

    @BeforeEach
    void setUp() {
        properties = new JobOrchestrationProperties();
        properties.setEnabled(true);
        properties.setOrchestratorType("AWS_STEP_FUNCTIONS");
        properties.getHealthCheck().setEnabled(true);
        properties.getHealthCheck().setCheckConnectivity(true);
        properties.getHealthCheck().setShowDetails(true);
        
        mockOrchestrator = new MockJobOrchestrator();
    }

    @Test
    void shouldReturnUpWhenOrchestratorIsAvailable() {
        // Given
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.of(mockOrchestrator), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.UP);
                    assertThat(h.getDetails()).containsKey("orchestratorType");
                    assertThat(h.getDetails().get("orchestratorType")).isEqualTo("TEST");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnDownWhenOrchestratorIsNotConfigured() {
        // Given
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.empty(), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.DOWN);
                    assertThat(h.getDetails()).containsKey("message");
                    assertThat(h.getDetails().get("message")).isEqualTo("No orchestrator configured");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnUpWhenHealthChecksDisabled() {
        // Given
        properties.getHealthCheck().setEnabled(false);
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.of(mockOrchestrator), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.UP);
                    assertThat(h.getDetails()).containsKey("message");
                    assertThat(h.getDetails().get("message")).isEqualTo("Health checks disabled");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnUpWhenOrchestrationDisabled() {
        // Given
        properties.setEnabled(false);
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.of(mockOrchestrator), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.UP);
                    assertThat(h.getDetails()).containsKey("message");
                    assertThat(h.getDetails().get("message")).isEqualTo("Job orchestration disabled");
                })
                .verifyComplete();
    }

    @Test
    void shouldIncludeDetailsWhenShowDetailsEnabled() {
        // Given
        properties.getHealthCheck().setShowDetails(true);
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.of(mockOrchestrator), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getDetails()).containsKeys(
                            "orchestratorType",
                            "enabled",
                            "defaultTimeout",
                            "maxRetries",
                            "resiliency.circuitBreakerEnabled",
                            "resiliency.retryEnabled",
                            "observability.tracingEnabled",
                            "observability.metricsEnabled"
                    );
                })
                .verifyComplete();
    }

    @Test
    void shouldNotCheckConnectivityWhenDisabled() {
        // Given
        properties.getHealthCheck().setCheckConnectivity(false);
        healthIndicator = new JobOrchestratorHealthIndicator(Optional.of(mockOrchestrator), properties);
        
        // When
        Mono<Health> health = healthIndicator.health();
        
        // Then
        StepVerifier.create(health)
                .assertNext(h -> {
                    assertThat(h.getStatus()).isEqualTo(Status.UP);
                    assertThat(h.getDetails()).containsKey("message");
                    assertThat(h.getDetails().get("message")).isEqualTo("Connectivity check disabled");
                })
                .verifyComplete();
    }

    /**
     * Mock implementation of JobOrchestrator for testing.
     */
    private static class MockJobOrchestrator implements JobOrchestrator {
        @Override
        public Mono<JobExecution> startJob(JobExecutionRequest request) {
            return Mono.just(JobExecution.builder()
                    .executionId("test-exec-123")
                    .status(JobExecutionStatus.RUNNING)
                    .build());
        }

        @Override
        public Mono<JobExecutionStatus> checkJobStatus(String executionId) {
            return Mono.just(JobExecutionStatus.RUNNING);
        }

        @Override
        public Mono<JobExecutionStatus> stopJob(String executionId, String reason) {
            return Mono.just(JobExecutionStatus.ABORTED);
        }

        @Override
        public Mono<JobExecution> getJobExecution(String executionId) {
            return Mono.just(JobExecution.builder()
                    .executionId(executionId)
                    .status(JobExecutionStatus.SUCCEEDED)
                    .build());
        }

        @Override
        public String getOrchestratorType() {
            return "TEST";
        }
    }
}

