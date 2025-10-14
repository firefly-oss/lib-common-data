# Observability

This document describes the observability features in lib-common-data, including distributed tracing, metrics collection, and health checks.

## Table of Contents

- [Overview](#overview)
- [Distributed Tracing](#distributed-tracing)
- [Metrics](#metrics)
- [Health Checks](#health-checks)
- [Configuration](#configuration)
- [Integration Examples](#integration-examples)

## Overview

The library provides comprehensive observability features out of the box:

- **Distributed Tracing**: Track job execution across services using Micrometer Observation
- **Metrics**: Collect detailed metrics about job performance and orchestrator operations
- **Health Checks**: Monitor orchestrator availability and connectivity

All observability features are automatically configured and can be customized through properties.

## Distributed Tracing

### Automatic Tracing

The library automatically creates traces for all job operations when using `AbstractResilientDataJobService`:

```java
@Service
public class CustomerDataJobService extends AbstractResilientDataJobService {
    
    public CustomerDataJobService(JobTracingService tracingService,
                                  JobMetricsService metricsService,
                                  ResiliencyDecoratorService resiliencyService) {
        super(tracingService, metricsService, resiliencyService);
    }
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        // This method is automatically traced
        return orchestrator.startJob(buildExecutionRequest(request))
                .map(execution -> JobStageResponse.success(/* ... */));
    }
    
    // Other methods...
}
```

### Manual Tracing

For custom tracing operations:

```java
@Service
public class CustomService {
    
    private final JobTracingService tracingService;
    
    public Mono<Result> processData(String executionId) {
        return tracingService.traceJobOperation(
            JobStage.COLLECT,
            executionId,
            performDataProcessing()
        );
    }
    
    // Synchronous operations
    public Result processDataSync(String executionId) {
        return tracingService.traceJobOperationSync(
            JobStage.COLLECT,
            executionId,
            () -> performSyncProcessing()
        );
    }
}
```

### Adding Custom Tags and Events

```java
// Add custom tags to the current trace
tracingService.addTags(Map.of(
    "customer.id", customerId,
    "data.size", String.valueOf(dataSize)
));

// Record custom events
tracingService.recordEvent("data.validated", "Data validation completed successfully");
```

### Trace Context

Each trace includes:

- **Span Name**: `job.stage.<stage_name>` (e.g., `job.stage.start`)
- **Low Cardinality Tags**:
  - `job.stage`: The job stage (START, CHECK, COLLECT, RESULT)
  - `execution.id`: Truncated execution ID
  - `orchestrator.type`: Type of orchestrator (AWS_STEP_FUNCTIONS, etc.)
- **High Cardinality Tags**:
  - `execution.id.full`: Complete execution ID
- **Events**:
  - `job.stage.success`: Emitted on successful completion
  - `job.stage.error`: Emitted on failure with error details

## Metrics

### Automatic Metrics

The library automatically collects the following metrics:

#### Job Stage Metrics

- **`firefly.data.job.stage.execution`** (Timer)
  - Tags: `stage`, `status`, `orchestrator`
  - Measures execution time for each job stage

- **`firefly.data.job.stage.count`** (Counter)
  - Tags: `stage`, `status`, `orchestrator`
  - Counts job stage executions

#### Job Execution Metrics

- **`firefly.data.job.execution.started`** (Counter)
  - Tags: `orchestrator`
  - Counts job executions started

- **`firefly.data.job.execution.completed`** (Counter)
  - Tags: `status`, `orchestrator`
  - Counts job executions completed

- **`firefly.data.job.execution.duration`** (Timer)
  - Tags: `status`, `orchestrator`
  - Measures total job execution time

#### Error Metrics

- **`firefly.data.job.error`** (Counter)
  - Tags: `stage`, `error.type`, `orchestrator`
  - Counts errors by stage and type

#### Mapper Metrics

- **`firefly.data.job.mapper.execution`** (Timer)
  - Tags: `mapper`, `status`
  - Measures mapper execution time

#### Orchestrator Metrics

- **`firefly.data.job.orchestrator.operation`** (Timer)
  - Tags: `operation`, `status`, `orchestrator`
  - Measures orchestrator operation time

- **`firefly.data.job.active.count`** (Gauge)
  - Current number of active jobs

### Custom Metrics

Record custom metrics using `JobMetricsService`:

```java
@Service
public class CustomService {
    
    private final JobMetricsService metricsService;
    
    public void processData(String executionId) {
        metricsService.recordJobExecutionStart(executionId);
        
        try {
            // Process data
            metricsService.recordJobExecutionCompletion(executionId, JobExecutionStatus.SUCCEEDED);
        } catch (Exception e) {
            metricsService.recordJobError(JobStage.COLLECT, e.getClass().getSimpleName());
            metricsService.recordJobExecutionCompletion(executionId, JobExecutionStatus.FAILED);
        }
    }
}
```

## Health Checks

### Orchestrator Health Indicator

The library provides a reactive health indicator for the job orchestrator:

```bash
# Check health via Actuator endpoint
curl http://localhost:8080/actuator/health/jobOrchestrator
```

Response example:

```json
{
  "status": "UP",
  "details": {
    "orchestratorType": "AWS_STEP_FUNCTIONS",
    "enabled": true,
    "defaultTimeout": "PT24H",
    "maxRetries": 3,
    "resiliency.circuitBreakerEnabled": true,
    "resiliency.retryEnabled": true,
    "observability.tracingEnabled": true,
    "observability.metricsEnabled": true,
    "connectivity": "OK"
  }
}
```

### Health Check States

- **UP**: Orchestrator is available and healthy
- **DOWN**: Orchestrator is unavailable or unhealthy
- **UNKNOWN**: Health check disabled or orchestrator not configured

### Programmatic Health Check

```java
@Service
public class MonitoringService {
    
    private final JobOrchestratorHealthIndicator healthIndicator;
    
    public void checkHealth() {
        healthIndicator.health()
                .subscribe(health -> {
                    if (health.getStatus() == Status.UP) {
                        log.info("Orchestrator is healthy");
                    } else {
                        log.warn("Orchestrator is unhealthy: {}", health.getDetails());
                    }
                });
    }
}
```

## Configuration

### Tracing Configuration

```yaml
firefly:
  data:
    orchestration:
      observability:
        # Enable/disable tracing
        tracing-enabled: true
        
        # Control which stages to trace
        trace-job-start: true
        trace-job-check: true
        trace-job-collect: true
        trace-job-result: true
        
        # Include sensitive data in traces (use with caution)
        include-job-parameters-in-traces: false
        include-job-results-in-traces: false
```

### Metrics Configuration

```yaml
firefly:
  data:
    orchestration:
      observability:
        # Enable/disable metrics
        metrics-enabled: true
        
        # Customize metric name prefix
        metric-prefix: firefly.data.job

# Spring Boot Actuator metrics configuration
management:
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        firefly.data.job: true
```

### Health Check Configuration

```yaml
firefly:
  data:
    orchestration:
      health-check:
        # Enable/disable health checks
        enabled: true
        
        # Health check timeout
        timeout: 5s
        
        # Health check interval (for caching)
        interval: 30s
        
        # Check orchestrator connectivity
        check-connectivity: true
        
        # Show detailed health information
        show-details: true

# Spring Boot Actuator health configuration
management:
  endpoint:
    health:
      show-details: always
      show-components: always
```

## Integration Examples

### Complete Observability Setup

```yaml
# application.yml
spring:
  application:
    name: customer-data-service

firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      
      observability:
        tracing-enabled: true
        metrics-enabled: true
        metric-prefix: customer.data.job
      
      health-check:
        enabled: true
        timeout: 5s
        check-connectivity: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 1.0

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### Service Implementation

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
        // Automatically traced and metered
        return orchestrator.startJob(buildRequest(request))
                .doOnNext(execution -> {
                    // Add custom tags
                    getTracingService().addTags(Map.of(
                        "customer.type", request.getParameters().get("customerType")
                    ));
                })
                .map(this::buildResponse);
    }
    
    // Other methods...
}
```

### Monitoring Dashboard

Use the metrics with Prometheus and Grafana:

```promql
# Job execution rate
rate(firefly_data_job_stage_count_total[5m])

# Job execution duration (p95)
histogram_quantile(0.95, rate(firefly_data_job_stage_execution_seconds_bucket[5m]))

# Error rate by stage
rate(firefly_data_job_error_total[5m])

# Active jobs
firefly_data_job_active_count

# Circuit breaker state
resilience4j_circuitbreaker_state{name="jobOrchestrator"}
```

## Best Practices

1. **Enable Tracing in Production**: Distributed tracing helps diagnose issues across services
2. **Use Sampling**: For high-volume systems, configure sampling to reduce overhead
3. **Monitor Key Metrics**: Focus on execution duration, error rates, and active job count
4. **Set Up Alerts**: Create alerts for health check failures and high error rates
5. **Avoid Sensitive Data**: Don't include sensitive data in traces unless necessary
6. **Use Custom Tags Wisely**: Add business-relevant tags for better observability
7. **Regular Health Checks**: Monitor orchestrator health to detect issues early

