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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for resiliency patterns using Resilience4j.
 */
@Configuration
@ConditionalOnProperty(prefix = "firefly.data.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ResiliencyConfiguration {

    /**
     * Creates a circuit breaker for job orchestrator operations.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.resiliency", name = "circuit-breaker-enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreaker jobOrchestratorCircuitBreaker(JobOrchestrationProperties properties) {
        JobOrchestrationProperties.ResiliencyConfig config = properties.getResiliency();
        
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.getCircuitBreakerFailureRateThreshold())
                .slowCallRateThreshold(config.getCircuitBreakerSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getCircuitBreakerSlowCallDurationThreshold())
                .waitDurationInOpenState(config.getCircuitBreakerWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(config.getCircuitBreakerPermittedNumberOfCallsInHalfOpenState())
                .slidingWindowSize(config.getCircuitBreakerSlidingWindowSize())
                .minimumNumberOfCalls(config.getCircuitBreakerMinimumNumberOfCalls())
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("jobOrchestrator", circuitBreakerConfig);
        
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.info("Circuit breaker state transition: {}", event))
                .onError(event -> log.error("Circuit breaker error: {}", event))
                .onSuccess(event -> log.debug("Circuit breaker success: {}", event));

        log.info("Created circuit breaker for job orchestrator with failure rate threshold: {}%", 
                config.getCircuitBreakerFailureRateThreshold());
        
        return circuitBreaker;
    }

    /**
     * Creates a retry mechanism for job orchestrator operations.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.resiliency", name = "retry-enabled", havingValue = "true", matchIfMissing = true)
    public Retry jobOrchestratorRetry(JobOrchestrationProperties properties) {
        JobOrchestrationProperties.ResiliencyConfig config = properties.getResiliency();
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(config.getRetryMaxAttempts())
                .waitDuration(config.getRetryWaitDuration())
                .retryExceptions(Exception.class)
                .build();

        Retry retry = Retry.of("jobOrchestrator", retryConfig);
        
        retry.getEventPublisher()
                .onRetry(event -> log.warn("Retry attempt {} for job orchestrator operation", event.getNumberOfRetryAttempts()))
                .onError(event -> log.error("Retry failed after {} attempts", event.getNumberOfRetryAttempts()))
                .onSuccess(event -> log.debug("Retry succeeded after {} attempts", event.getNumberOfRetryAttempts()));

        log.info("Created retry mechanism for job orchestrator with max attempts: {}", 
                config.getRetryMaxAttempts());
        
        return retry;
    }

    /**
     * Creates a rate limiter for job orchestrator operations.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.resiliency", name = "rate-limiter-enabled", havingValue = "true")
    public RateLimiter jobOrchestratorRateLimiter(JobOrchestrationProperties properties) {
        JobOrchestrationProperties.ResiliencyConfig config = properties.getResiliency();
        
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(config.getRateLimiterLimitForPeriod())
                .limitRefreshPeriod(config.getRateLimiterLimitRefreshPeriod())
                .timeoutDuration(config.getRateLimiterTimeoutDuration())
                .build();

        RateLimiter rateLimiter = RateLimiter.of("jobOrchestrator", rateLimiterConfig);
        
        rateLimiter.getEventPublisher()
                .onSuccess(event -> log.debug("Rate limiter allowed operation"))
                .onFailure(event -> log.warn("Rate limiter rejected operation"));

        log.info("Created rate limiter for job orchestrator with limit: {} per {}", 
                config.getRateLimiterLimitForPeriod(), 
                config.getRateLimiterLimitRefreshPeriod());
        
        return rateLimiter;
    }

    /**
     * Creates a bulkhead for job orchestrator operations.
     */
    @Bean
    @ConditionalOnProperty(prefix = "firefly.data.orchestration.resiliency", name = "bulkhead-enabled", havingValue = "true")
    public Bulkhead jobOrchestratorBulkhead(JobOrchestrationProperties properties) {
        JobOrchestrationProperties.ResiliencyConfig config = properties.getResiliency();
        
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(config.getBulkheadMaxConcurrentCalls())
                .maxWaitDuration(config.getBulkheadMaxWaitDuration())
                .build();

        Bulkhead bulkhead = Bulkhead.of("jobOrchestrator", bulkheadConfig);
        
        bulkhead.getEventPublisher()
                .onCallPermitted(event -> log.debug("Bulkhead permitted call"))
                .onCallRejected(event -> log.warn("Bulkhead rejected call"))
                .onCallFinished(event -> log.debug("Bulkhead call finished"));

        log.info("Created bulkhead for job orchestrator with max concurrent calls: {}", 
                config.getBulkheadMaxConcurrentCalls());
        
        return bulkhead;
    }
}

