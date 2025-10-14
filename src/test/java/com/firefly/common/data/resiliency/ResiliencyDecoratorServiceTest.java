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

package com.firefly.common.data.resiliency;

import com.firefly.common.data.config.JobOrchestrationProperties;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResiliencyDecoratorServiceTest {

    private ResiliencyDecoratorService resiliencyService;
    private JobOrchestrationProperties properties;
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private RateLimiter rateLimiter;
    private Bulkhead bulkhead;

    @BeforeEach
    void setUp() {
        properties = new JobOrchestrationProperties();
        properties.getResiliency().setCircuitBreakerEnabled(true);
        properties.getResiliency().setRetryEnabled(true);
        properties.getResiliency().setRateLimiterEnabled(true);
        properties.getResiliency().setBulkheadEnabled(true);

        // Create circuit breaker
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .slidingWindowSize(10)
                .build();
        circuitBreaker = CircuitBreaker.of("test", cbConfig);

        // Create retry
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .build();
        retry = Retry.of("test", retryConfig);

        // Create rate limiter
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .build();
        rateLimiter = RateLimiter.of("test", rlConfig);

        // Create bulkhead
        BulkheadConfig bhConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(5)
                .build();
        bulkhead = Bulkhead.of("test", bhConfig);

        resiliencyService = new ResiliencyDecoratorService(
                properties,
                Optional.of(circuitBreaker),
                Optional.of(retry),
                Optional.of(rateLimiter),
                Optional.of(bulkhead)
        );
    }

    @Test
    void shouldDecorateWithAllPatterns() {
        // Given
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = resiliencyService.decorate(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldRetryOnFailure() {
        // Given
        AtomicInteger attempts = new AtomicInteger(0);
        Mono<String> operation = Mono.defer(() -> {
            if (attempts.incrementAndGet() < 3) {
                return Mono.error(new RuntimeException("Temporary failure"));
            }
            return Mono.just("success");
        });

        // When
        Mono<String> decorated = resiliencyService.decorateWithRetry(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
        
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldApplyCircuitBreaker() {
        // Given
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = resiliencyService.decorateWithCircuitBreaker(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
        
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldApplyRateLimiter() {
        // Given
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = resiliencyService.decorateWithRateLimiter(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldApplyBulkhead() {
        // Given
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = resiliencyService.decorateWithBulkhead(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldGetCircuitBreakerState() {
        // When
        String state = resiliencyService.getCircuitBreakerState();

        // Then
        assertThat(state).isEqualTo("CLOSED");
    }

    @Test
    void shouldGetCircuitBreakerMetrics() {
        // Given
        Mono<String> operation = Mono.just("success");
        resiliencyService.decorateWithCircuitBreaker(operation).block();

        // When
        ResiliencyDecoratorService.CircuitBreakerMetrics metrics = resiliencyService.getCircuitBreakerMetrics();

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.state()).isEqualTo("CLOSED");
    }

    @Test
    void shouldGetRetryMetrics() {
        // When
        ResiliencyDecoratorService.RetryMetrics metrics = resiliencyService.getRetryMetrics();

        // Then
        assertThat(metrics).isNotNull();
    }

    @Test
    void shouldNotApplyPatternsWhenDisabled() {
        // Given
        properties.getResiliency().setCircuitBreakerEnabled(false);
        properties.getResiliency().setRetryEnabled(false);
        properties.getResiliency().setRateLimiterEnabled(false);
        properties.getResiliency().setBulkheadEnabled(false);
        
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = resiliencyService.decorate(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyOptionals() {
        // Given
        ResiliencyDecoratorService serviceWithoutPatterns = new ResiliencyDecoratorService(
                properties,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        
        Mono<String> operation = Mono.just("success");

        // When
        Mono<String> decorated = serviceWithoutPatterns.decorate(operation);

        // Then
        StepVerifier.create(decorated)
                .expectNext("success")
                .verifyComplete();
        
        assertThat(serviceWithoutPatterns.getCircuitBreakerState()).isEqualTo("NOT_CONFIGURED");
    }
}

