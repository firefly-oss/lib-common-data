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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Service for applying resiliency patterns to reactive operations.
 */
@Slf4j
public class ResiliencyDecoratorService {

    private final JobOrchestrationProperties properties;
    private final Optional<CircuitBreaker> circuitBreaker;
    private final Optional<Retry> retry;
    private final Optional<RateLimiter> rateLimiter;
    private final Optional<Bulkhead> bulkhead;

    public ResiliencyDecoratorService(JobOrchestrationProperties properties,
                                      Optional<CircuitBreaker> circuitBreaker,
                                      Optional<Retry> retry,
                                      Optional<RateLimiter> rateLimiter,
                                      Optional<Bulkhead> bulkhead) {
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.rateLimiter = rateLimiter;
        this.bulkhead = bulkhead;
        
        log.info("Initialized ResiliencyDecoratorService with circuit breaker: {}, retry: {}, rate limiter: {}, bulkhead: {}",
                circuitBreaker.isPresent(), retry.isPresent(), rateLimiter.isPresent(), bulkhead.isPresent());
    }
    
    /**
     * Simple constructor that creates empty optionals for all resilience components.
     */
    public ResiliencyDecoratorService(JobOrchestrationProperties properties) {
        this(properties, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Decorates a Mono with all configured resiliency patterns.
     *
     * @param operation the operation to decorate
     * @param <T> the return type
     * @return the decorated operation
     */
    public <T> Mono<T> decorate(Mono<T> operation) {
        Mono<T> decorated = operation;

        // Apply bulkhead first (controls concurrency)
        if (bulkhead.isPresent() && properties.getResiliency().isBulkheadEnabled()) {
            log.debug("Applying bulkhead to operation");
            decorated = decorated.transformDeferred(BulkheadOperator.of(bulkhead.get()));
        }

        // Apply rate limiter
        if (rateLimiter.isPresent() && properties.getResiliency().isRateLimiterEnabled()) {
            log.debug("Applying rate limiter to operation");
            decorated = decorated.transformDeferred(RateLimiterOperator.of(rateLimiter.get()));
        }

        // Apply circuit breaker
        if (circuitBreaker.isPresent() && properties.getResiliency().isCircuitBreakerEnabled()) {
            log.debug("Applying circuit breaker to operation");
            decorated = decorated.transformDeferred(CircuitBreakerOperator.of(circuitBreaker.get()));
        }

        // Apply retry last (so it retries through all other patterns)
        if (retry.isPresent() && properties.getResiliency().isRetryEnabled()) {
            log.debug("Applying retry to operation");
            decorated = decorated.transformDeferred(RetryOperator.of(retry.get()));
        }

        return decorated;
    }

    /**
     * Decorates a Mono with circuit breaker only.
     */
    public <T> Mono<T> decorateWithCircuitBreaker(Mono<T> operation) {
        if (circuitBreaker.isPresent() && properties.getResiliency().isCircuitBreakerEnabled()) {
            log.debug("Applying circuit breaker to operation");
            return operation.transformDeferred(CircuitBreakerOperator.of(circuitBreaker.get()));
        }
        return operation;
    }

    /**
     * Decorates a Mono with retry only.
     */
    public <T> Mono<T> decorateWithRetry(Mono<T> operation) {
        if (retry.isPresent() && properties.getResiliency().isRetryEnabled()) {
            log.debug("Applying retry to operation");
            return operation.transformDeferred(RetryOperator.of(retry.get()));
        }
        return operation;
    }

    /**
     * Decorates a Mono with rate limiter only.
     */
    public <T> Mono<T> decorateWithRateLimiter(Mono<T> operation) {
        if (rateLimiter.isPresent() && properties.getResiliency().isRateLimiterEnabled()) {
            log.debug("Applying rate limiter to operation");
            return operation.transformDeferred(RateLimiterOperator.of(rateLimiter.get()));
        }
        return operation;
    }

    /**
     * Decorates a Mono with bulkhead only.
     */
    public <T> Mono<T> decorateWithBulkhead(Mono<T> operation) {
        if (bulkhead.isPresent() && properties.getResiliency().isBulkheadEnabled()) {
            log.debug("Applying bulkhead to operation");
            return operation.transformDeferred(BulkheadOperator.of(bulkhead.get()));
        }
        return operation;
    }

    /**
     * Gets the circuit breaker state.
     */
    public String getCircuitBreakerState() {
        return circuitBreaker.map(cb -> cb.getState().name()).orElse("NOT_CONFIGURED");
    }

    /**
     * Gets the circuit breaker metrics.
     */
    public CircuitBreakerMetrics getCircuitBreakerMetrics() {
        if (circuitBreaker.isEmpty()) {
            return new CircuitBreakerMetrics("NOT_CONFIGURED", 0, 0, 0, 0, 0);
        }

        CircuitBreaker cb = circuitBreaker.get();
        CircuitBreaker.Metrics metrics = cb.getMetrics();
        
        return new CircuitBreakerMetrics(
                cb.getState().name(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSlowCalls(),
                metrics.getFailureRate(),
                metrics.getSlowCallRate()
        );
    }

    /**
     * Gets the retry metrics.
     */
    public RetryMetrics getRetryMetrics() {
        if (retry.isEmpty()) {
            return new RetryMetrics(0, 0, 0);
        }

        Retry r = retry.get();
        Retry.Metrics metrics = r.getMetrics();
        
        return new RetryMetrics(
                metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
                metrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
                metrics.getNumberOfFailedCallsWithRetryAttempt()
        );
    }

    /**
     * Circuit breaker metrics record.
     */
    public record CircuitBreakerMetrics(
            String state,
            long successfulCalls,
            long failedCalls,
            long slowCalls,
            float failureRate,
            float slowCallRate
    ) {}

    /**
     * Retry metrics record.
     */
    public record RetryMetrics(
            long successfulCallsWithoutRetry,
            long successfulCallsWithRetry,
            long failedCallsWithRetry
    ) {}
}

