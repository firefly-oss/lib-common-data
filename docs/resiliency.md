# Resiliency

This document describes the resiliency patterns implemented in lib-common-data using Resilience4j.

## Table of Contents

- [Overview](#overview)
- [Circuit Breaker](#circuit-breaker)
- [Retry](#retry)
- [Rate Limiter](#rate-limiter)
- [Bulkhead](#bulkhead)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Monitoring](#monitoring)

## Overview

The library provides built-in resiliency patterns to handle failures gracefully and prevent cascading failures:

- **Circuit Breaker**: Prevents calls to failing services
- **Retry**: Automatically retries failed operations
- **Rate Limiter**: Controls the rate of requests
- **Bulkhead**: Limits concurrent calls to prevent resource exhaustion

All patterns are implemented using Resilience4j and integrate seamlessly with reactive programming (Project Reactor).

## Circuit Breaker

### How It Works

The circuit breaker monitors the failure rate of operations and transitions between three states:

1. **CLOSED**: Normal operation, requests pass through
2. **OPEN**: Too many failures detected, requests are rejected immediately
3. **HALF_OPEN**: Testing if the service has recovered

### State Transitions

```
CLOSED --[failure rate > threshold]--> OPEN
OPEN --[wait duration elapsed]--> HALF_OPEN
HALF_OPEN --[success rate > threshold]--> CLOSED
HALF_OPEN --[failure rate > threshold]--> OPEN
```

### Configuration

```yaml
firefly:
  data:
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        
        # Failure rate threshold (percentage)
        circuit-breaker-failure-rate-threshold: 50.0
        
        # Slow call rate threshold (percentage)
        circuit-breaker-slow-call-rate-threshold: 100.0
        
        # Slow call duration threshold
        circuit-breaker-slow-call-duration-threshold: 60s
        
        # Wait duration in open state before transitioning to half-open
        circuit-breaker-wait-duration-in-open-state: 60s
        
        # Number of permitted calls in half-open state
        circuit-breaker-permitted-number-of-calls-in-half-open-state: 10
        
        # Sliding window size for calculating failure rate
        circuit-breaker-sliding-window-size: 100
        
        # Minimum number of calls before calculating failure rate
        circuit-breaker-minimum-number-of-calls: 10
```

### Example

```java
@Service
public class CustomerDataJobService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        // Circuit breaker is automatically applied
        return orchestrator.startJob(buildRequest(request))
                .map(this::buildResponse);
    }
}
```

## Retry

### How It Works

The retry mechanism automatically retries failed operations with configurable delays between attempts.

### Configuration

```yaml
firefly:
  data:
    orchestration:
      resiliency:
        retry-enabled: true
        
        # Maximum number of retry attempts (including initial call)
        retry-max-attempts: 3
        
        # Wait duration between retries
        retry-wait-duration: 5s
```

### Retry Strategy

- **Fixed Delay**: Waits a fixed duration between retries
- **Exponential Backoff**: Can be configured by customizing the Retry bean

### Example

```java
// Automatic retry with AbstractResilientDataJobService
@Service
public class OrderDataJobService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
        // Retries automatically on failure
        return orchestrator.checkJobStatus(request.getExecutionId())
                .map(status -> buildResponse(status));
    }
}

// Manual retry for custom operations
@Service
public class CustomService {
    
    private final ResiliencyDecoratorService resiliencyService;
    
    public Mono<Result> fetchData() {
        return resiliencyService.decorateWithRetry(
            performDataFetch()
        );
    }
}
```

## Rate Limiter

### How It Works

The rate limiter controls the number of requests allowed within a time period, preventing system overload.

### Configuration

```yaml
firefly:
  data:
    orchestration:
      resiliency:
        rate-limiter-enabled: true
        
        # Number of requests allowed per period
        rate-limiter-limit-for-period: 100
        
        # Period duration
        rate-limiter-limit-refresh-period: 1s
        
        # Timeout when waiting for permission
        rate-limiter-timeout-duration: 5s
```

### Example

```java
@Service
public class HighVolumeDataService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
        // Rate limiter prevents overwhelming the orchestrator
        return orchestrator.getJobExecution(request.getExecutionId())
                .map(this::extractResults);
    }
}
```

## Bulkhead

### How It Works

The bulkhead pattern limits the number of concurrent calls to prevent resource exhaustion and isolate failures.

### Configuration

```yaml
firefly:
  data:
    orchestration:
      resiliency:
        bulkhead-enabled: true
        
        # Maximum number of concurrent calls
        bulkhead-max-concurrent-calls: 25
        
        # Maximum wait duration when bulkhead is full
        bulkhead-max-wait-duration: 500ms
```

### Example

```java
@Service
public class ResourceIntensiveService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        // Bulkhead limits concurrent job starts
        return orchestrator.startJob(buildRequest(request))
                .map(this::buildResponse);
    }
}
```

## Configuration

### Complete Resiliency Configuration

```yaml
firefly:
  data:
    orchestration:
      resiliency:
        # Circuit Breaker
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-slow-call-rate-threshold: 100.0
        circuit-breaker-slow-call-duration-threshold: 60s
        circuit-breaker-wait-duration-in-open-state: 60s
        circuit-breaker-permitted-number-of-calls-in-half-open-state: 10
        circuit-breaker-sliding-window-size: 100
        circuit-breaker-minimum-number-of-calls: 10
        
        # Retry
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s
        
        # Rate Limiter
        rate-limiter-enabled: false  # Disabled by default
        rate-limiter-limit-for-period: 100
        rate-limiter-limit-refresh-period: 1s
        rate-limiter-timeout-duration: 5s
        
        # Bulkhead
        bulkhead-enabled: false  # Disabled by default
        bulkhead-max-concurrent-calls: 25
        bulkhead-max-wait-duration: 500ms
```

### Selective Pattern Application

You can enable/disable patterns individually:

```yaml
# Only circuit breaker and retry
firefly:
  data:
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        retry-enabled: true
        rate-limiter-enabled: false
        bulkhead-enabled: false
```

## Usage Examples

### Using AbstractResilientDataJobService

The easiest way to use resiliency patterns is to extend `AbstractResilientDataJobService`:

```java
@Service
@Slf4j
public class CustomerDataJobService extends AbstractResilientDataJobService {
    
    private final JobOrchestrator orchestrator;
    private final CustomerRepository repository;
    
    public CustomerDataJobService(JobTracingService tracingService,
                                  JobMetricsService metricsService,
                                  ResiliencyDecoratorService resiliencyService,
                                  JobOrchestrator orchestrator,
                                  CustomerRepository repository) {
        super(tracingService, metricsService, resiliencyService);
        this.orchestrator = orchestrator;
        this.repository = repository;
    }
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        // All configured resiliency patterns are automatically applied
        return orchestrator.startJob(buildExecutionRequest(request))
                .flatMap(execution -> saveExecutionMetadata(execution))
                .map(execution -> buildSuccessResponse(execution));
    }
    
    @Override
    protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
        // Automatic retry on transient failures
        return orchestrator.checkJobStatus(request.getExecutionId())
                .map(status -> buildStatusResponse(status));
    }
    
    @Override
    protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
        // Circuit breaker prevents calls when orchestrator is down
        return orchestrator.getJobExecution(request.getExecutionId())
                .map(execution -> buildResultsResponse(execution));
    }
    
    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        // All patterns work together for maximum resilience
        return collectJobResults(request)
                .flatMap(response -> applyMapper(response, request));
    }
}
```

### Manual Pattern Application

For custom operations outside of `AbstractResilientDataJobService`:

```java
@Service
public class CustomDataService {
    
    private final ResiliencyDecoratorService resiliencyService;
    private final ExternalApiClient apiClient;
    
    // Apply all patterns
    public Mono<Data> fetchDataWithAllPatterns() {
        return resiliencyService.decorate(
            apiClient.fetchData()
        );
    }
    
    // Apply only circuit breaker
    public Mono<Data> fetchDataWithCircuitBreaker() {
        return resiliencyService.decorateWithCircuitBreaker(
            apiClient.fetchData()
        );
    }
    
    // Apply only retry
    public Mono<Data> fetchDataWithRetry() {
        return resiliencyService.decorateWithRetry(
            apiClient.fetchData()
        );
    }
    
    // Apply only rate limiter
    public Mono<Data> fetchDataWithRateLimit() {
        return resiliencyService.decorateWithRateLimiter(
            apiClient.fetchData()
        );
    }
    
    // Apply only bulkhead
    public Mono<Data> fetchDataWithBulkhead() {
        return resiliencyService.decorateWithBulkhead(
            apiClient.fetchData()
        );
    }
}
```

## Monitoring

### Circuit Breaker Metrics

Monitor circuit breaker state and metrics:

```java
@Service
public class ResiliencyMonitoringService {
    
    private final ResiliencyDecoratorService resiliencyService;
    
    public void logCircuitBreakerState() {
        String state = resiliencyService.getCircuitBreakerState();
        log.info("Circuit breaker state: {}", state);
    }
    
    public void logCircuitBreakerMetrics() {
        var metrics = resiliencyService.getCircuitBreakerMetrics();
        log.info("Circuit breaker metrics: state={}, successful={}, failed={}, slow={}, failureRate={}, slowCallRate={}",
                metrics.state(),
                metrics.successfulCalls(),
                metrics.failedCalls(),
                metrics.slowCalls(),
                metrics.failureRate(),
                metrics.slowCallRate());
    }
    
    public void logRetryMetrics() {
        var metrics = resiliencyService.getRetryMetrics();
        log.info("Retry metrics: successWithoutRetry={}, successWithRetry={}, failedWithRetry={}",
                metrics.successfulCallsWithoutRetry(),
                metrics.successfulCallsWithRetry(),
                metrics.failedCallsWithRetry());
    }
}
```

### Resilience4j Actuator Endpoints

Enable Resilience4j actuator endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,circuitbreakers,retries,ratelimiters,bulkheads
  health:
    circuitbreakers:
      enabled: true
```

Access endpoints:

```bash
# Circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers

# Circuit breaker events
curl http://localhost:8080/actuator/circuitbreakerevents

# Retry events
curl http://localhost:8080/actuator/retryevents
```

### Prometheus Metrics

Resilience4j automatically exports metrics to Prometheus:

```promql
# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="jobOrchestrator"}

# Circuit breaker calls
rate(resilience4j_circuitbreaker_calls_total{name="jobOrchestrator"}[5m])

# Retry attempts
rate(resilience4j_retry_calls_total{name="jobOrchestrator"}[5m])

# Rate limiter available permissions
resilience4j_ratelimiter_available_permissions{name="jobOrchestrator"}

# Bulkhead available concurrent calls
resilience4j_bulkhead_available_concurrent_calls{name="jobOrchestrator"}
```

## Best Practices

1. **Start with Circuit Breaker and Retry**: These provide the most value for most use cases
2. **Tune Thresholds**: Adjust failure rate and slow call thresholds based on your SLAs
3. **Monitor State Transitions**: Alert on circuit breaker opening
4. **Use Rate Limiter Sparingly**: Only enable when you need to protect against overload
5. **Test Failure Scenarios**: Verify resiliency patterns work as expected
6. **Combine with Timeouts**: Set appropriate timeouts on orchestrator operations
7. **Log Pattern Events**: Enable logging for circuit breaker and retry events
8. **Consider Fallbacks**: Implement fallback logic for critical operations

