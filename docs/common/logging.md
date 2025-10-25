# Logging Guide

This document describes the comprehensive logging capabilities built into the Firefly Common Data Library.

## Overview

The library provides structured logging for all job lifecycle phases (START, CHECK, COLLECT, RESULT) at both the service and controller layers. Logging is implemented using SLF4J with Logback, providing flexibility for different logging backends.

## Logging Levels

The library uses the following logging levels:

- **INFO**: Normal operational messages (job start, completion, status)
- **WARN**: Warning conditions (job failures, degraded performance)
- **ERROR**: Error conditions (exceptions, failures)
- **DEBUG**: Detailed diagnostic information (full request/response payloads)

## Service Layer Logging

### AbstractResilientDataJobService

The `AbstractResilientDataJobService` provides comprehensive logging for all job operations:

```java
@Service
public class MyDataJobService extends AbstractResilientDataJobService {

    public MyDataJobService(JobTracingService tracingService,
                           JobMetricsService metricsService,
                           ResiliencyDecoratorService resiliencyService,
                           JobEventPublisher eventPublisher,
                           JobAuditService auditService,
                           JobExecutionResultService resultService) {
        super(tracingService, metricsService, resiliencyService,
              eventPublisher, auditService, resultService);
    }

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        // Your business logic here
        // Logging is handled automatically by the base class
        return Mono.just(JobStageResponse.success(JobStage.START, "exec-123", "Job started"));
    }

    // Implement other methods...
}
```

### Logged Information

For each job stage, the following information is logged:

**On Stage Start (INFO level):**
```
Starting START stage - executionId: exec-123, parameters: [param1, param2]
```

**On Stage Start (DEBUG level):**
```
Full request details for START stage - executionId: exec-123, request: JobStageRequest(...)
```

**On Subscription (DEBUG level):**
```
Subscribed to START stage operation for execution exec-123
```

**On Success (INFO level):**
```
Successfully completed START stage - executionId: exec-123, duration: 1234ms, status: RUNNING
```

**On Success (DEBUG level):**
```
Response details for START stage - executionId: exec-123, data keys: [jobId, startTime], message: Job started successfully
```

**On Success with Failure Response (WARN level):**
```
Completed START stage with failure - executionId: exec-123, duration: 1234ms, status: FAILED, message: Validation error
```

**On Error (ERROR level):**
```
Failed START stage - executionId: exec-123, duration: 1234ms, errorType: RuntimeException, errorMessage: Connection timeout
```

**On Error (DEBUG level):**
```
Full error details for START stage - executionId: exec-123
java.lang.RuntimeException: Connection timeout
    at ...
```

**On Error Resume (WARN level):**
```
Returning failure response for START stage - executionId: exec-123, error: Connection timeout
```

## Controller Layer Logging

### AbstractDataJobController

The `AbstractDataJobController` provides HTTP request/response logging:

```java
@RestController
public class MyDataJobController extends AbstractDataJobController {
    
    public MyDataJobController(DataJobService dataJobService) {
        super(dataJobService);
    }
    
    // All endpoints are implemented by the base class with logging
}
```

### Logged Information

**On Request Received (INFO level):**
```
Received START job request - parameters: [param1, param2]
Received CHECK job request - executionId: exec-123, requestId: req-456
Received COLLECT job results request - executionId: exec-123, requestId: req-456
Received GET job result request - executionId: exec-123, requestId: req-456
```

**On Request Received (DEBUG level):**
```
Full START job request: JobStageRequest(stage=START, executionId=null, parameters={...})
```

**On Success Response (INFO level):**
```
START job completed successfully - executionId: exec-123, status: RUNNING
CHECK job completed successfully - executionId: exec-123, status: RUNNING
COLLECT job results completed successfully - executionId: exec-123, status: COMPLETED, dataKeys: [results, metadata]
GET job result completed successfully - executionId: exec-123, status: COMPLETED, dataKeys: [finalResult]
```

**On Failure Response (WARN level):**
```
START job completed with failure - executionId: exec-123, status: FAILED, message: Validation error
```

**On Error (ERROR level):**
```
START job failed with error: Connection timeout
java.lang.RuntimeException: Connection timeout
    at ...
```

**On Response (DEBUG level):**
```
Full START job response: JobStageResponse(stage=START, executionId=exec-123, success=true, ...)
Full CHECK job response for executionId exec-123: JobStageResponse(...)
Full COLLECT job results response for executionId exec-123: JobStageResponse(...)
Full GET job result response for executionId exec-123: JobStageResponse(...)
```

## Configuration

### Logback Configuration

Configure logging levels in `logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- Console appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- File appender for job operations -->
    <appender name="JOB_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/jobs.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/jobs.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Logger for service layer -->
    <logger name="com.firefly.common.data.service" level="INFO" additivity="false">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="JOB_FILE"/>
    </logger>
    
    <!-- Logger for controller layer -->
    <logger name="com.firefly.common.data.controller" level="INFO" additivity="false">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="JOB_FILE"/>
    </logger>
    
    <!-- Root logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Application Properties

Configure logging levels in `application.yml`:

```yaml
logging:
  level:
    # Service layer - INFO for production, DEBUG for development
    com.firefly.common.data.service: INFO
    
    # Controller layer - INFO for production, DEBUG for development
    com.firefly.common.data.controller: INFO
    
    # Observability components
    com.firefly.common.data.observability: INFO
    
    # Resiliency components
    com.firefly.common.data.resiliency: INFO
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    
  file:
    name: logs/application.log
    max-size: 10MB
    max-history: 30
```

## Structured Logging with JSON

For production environments, consider using JSON logging for better log aggregation:

### Add Logstash Encoder Dependency

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

### Configure JSON Logging

```xml
<appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/jobs.json</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/jobs.%d{yyyy-MM-dd}.json</fileNamePattern>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>executionId</includeMdcKeyName>
        <includeMdcKeyName>stage</includeMdcKeyName>
        <includeMdcKeyName>requestId</includeMdcKeyName>
    </encoder>
</appender>
```

## Log Correlation

### Using MDC (Mapped Diagnostic Context)

Add execution context to logs using MDC:

```java
import org.slf4j.MDC;

public class MyDataJobService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        return Mono.deferContextual(ctx -> {
            // Add context to MDC
            MDC.put("executionId", request.getExecutionId());
            MDC.put("stage", JobStage.START.name());
            MDC.put("requestId", request.getRequestId());
            
            try {
                // Your business logic
                return performJobStart(request);
            } finally {
                // Clean up MDC
                MDC.clear();
            }
        });
    }
}
```

## Best Practices

1. **Use Appropriate Log Levels**
   - INFO: Normal operations, job lifecycle events
   - WARN: Recoverable errors, degraded performance
   - ERROR: Unrecoverable errors, exceptions
   - DEBUG: Detailed diagnostic information (disable in production)

2. **Include Context**
   - Always include `executionId` in log messages
   - Include `requestId` for request tracing
   - Include `stage` to identify the job phase

3. **Avoid Logging Sensitive Data**
   - Don't log passwords, tokens, or PII
   - Use DEBUG level for full request/response payloads
   - Sanitize data before logging

4. **Use Structured Logging**
   - Use JSON format for production
   - Include consistent fields across all logs
   - Use MDC for contextual information

5. **Monitor Log Volume**
   - Use appropriate log levels to control volume
   - Implement log rotation and retention policies
   - Consider sampling for high-volume operations

6. **Integrate with Observability**
   - Correlate logs with traces using `executionId`
   - Use log aggregation tools (ELK, Splunk, Datadog)
   - Set up alerts for ERROR level logs

## Example Log Output

### Development Environment (DEBUG level)

```
2025-10-14 16:30:04.738 [main] INFO  c.f.c.d.c.AbstractDataJobController - Received START job request - parameters: [dataSource, targetTable]
2025-10-14 16:30:04.738 [main] DEBUG c.f.c.d.c.AbstractDataJobController - Full START job request: JobStageRequest(stage=START, executionId=null, parameters={dataSource=postgres, targetTable=users})
2025-10-14 16:30:04.739 [main] INFO  c.f.c.d.s.AbstractResilientDataJobService - Starting START stage - executionId: exec-123, parameters: [dataSource, targetTable]
2025-10-14 16:30:04.739 [main] DEBUG c.f.c.d.s.AbstractResilientDataJobService - Full request details for START stage - executionId: exec-123, request: JobStageRequest(...)
2025-10-14 16:30:04.740 [main] DEBUG c.f.c.d.s.AbstractResilientDataJobService - Subscribed to START stage operation for execution exec-123
2025-10-14 16:30:04.850 [main] INFO  c.f.c.d.s.AbstractResilientDataJobService - Successfully completed START stage - executionId: exec-123, duration: 111ms, status: RUNNING
2025-10-14 16:30:04.850 [main] DEBUG c.f.c.d.s.AbstractResilientDataJobService - Response details for START stage - executionId: exec-123, data keys: [jobId, startTime], message: Job started successfully
2025-10-14 16:30:04.851 [main] INFO  c.f.c.d.c.AbstractDataJobController - START job completed successfully - executionId: exec-123, status: RUNNING
2025-10-14 16:30:04.851 [main] DEBUG c.f.c.d.c.AbstractDataJobController - Full START job response: JobStageResponse(stage=START, executionId=exec-123, success=true, status=RUNNING, ...)
```

### Production Environment (INFO level)

```
2025-10-14 16:30:04.738 [http-nio-8080-exec-1] INFO  c.f.c.d.c.AbstractDataJobController - Received START job request - parameters: [dataSource, targetTable]
2025-10-14 16:30:04.739 [http-nio-8080-exec-1] INFO  c.f.c.d.s.AbstractResilientDataJobService - Starting START stage - executionId: exec-123, parameters: [dataSource, targetTable]
2025-10-14 16:30:04.850 [http-nio-8080-exec-1] INFO  c.f.c.d.s.AbstractResilientDataJobService - Successfully completed START stage - executionId: exec-123, duration: 111ms, status: RUNNING
2025-10-14 16:30:04.851 [http-nio-8080-exec-1] INFO  c.f.c.d.c.AbstractDataJobController - START job completed successfully - executionId: exec-123, status: RUNNING
```

## See Also

- [Observability Guide](observability.md) - Distributed tracing and metrics
- [Testing Guide](testing.md) - Testing with logging
- [Configuration Guide](configuration.md) - Application configuration

