# Persistence & Audit Trail

This document describes the persistence features in lib-common-data, including audit trail and execution result storage using hexagonal architecture with R2DBC.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Audit Trail](#audit-trail)
- [Execution Results](#execution-results)
- [Implementation Guide](#implementation-guide)
- [Configuration](#configuration)
- [Best Practices](#best-practices)

## Overview

The library provides built-in support for persisting:

- **Audit Trail** - Complete history of all job operations for compliance and debugging
- **Execution Results** - Job outputs with caching support for performance optimization

Key features:

- ✅ **Hexagonal Architecture** - Port/adapter pattern for flexible implementations
- ✅ **Reactive** - Fully reactive with R2DBC support
- ✅ **Multi-Database** - PostgreSQL, MySQL, H2, MongoDB, DynamoDB, Redis
- ✅ **Automatic** - Integrated into AbstractResilientDataJobService
- ✅ **Configurable** - Enable/disable features via properties
- ✅ **Retention Policies** - Automatic cleanup of old data
- ✅ **Security** - Sensitive data sanitization

## Architecture

### Hexagonal Architecture (Ports & Adapters)

```
┌─────────────────────────────────────────────────────────────┐
│                    lib-common-data (Core)                   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Domain Models                           │   │
│  │  - JobAuditEntry                                     │   │
│  │  - JobExecutionResult                                │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Domain Services                         │   │
│  │  - JobAuditService                                   │   │
│  │  - JobExecutionResultService                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Ports (Interfaces)                      │   │
│  │  - JobAuditRepository                                │   │
│  │  - JobExecutionResultRepository                      │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ implements
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Your Microservice (Adapters)                   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         R2DBC Adapters (Recommended)                 │   │
│  │  - R2dbcJobAuditRepositoryAdapter                    │   │
│  │  - R2dbcJobExecutionResultRepositoryAdapter          │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         R2DBC Entity Repositories                    │   │
│  │  - R2dbcJobAuditEntityRepository                     │   │
│  │  - R2dbcJobExecutionResultEntityRepository           │   │
│  └──────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│                  PostgreSQL / MySQL / H2                    │
└─────────────────────────────────────────────────────────────┘
```

## Audit Trail

### What is Audited?

Every job operation is automatically audited with:

- **Operation Details** - Stage, execution ID, request ID, job type
- **Timing** - Timestamp, duration
- **Status** - Success, failure, retry attempts
- **Context** - Initiator, orchestrator type, service name, environment
- **Tracing** - **Real trace ID and span ID** extracted from Micrometer Tracing for distributed tracing correlation
- **Data** - Input parameters, output data (configurable)
- **Errors** - Error messages, stack traces (optional)
- **Resiliency** - Circuit breaker events, rate limiting

#### Tracing Integration

The library now includes **effective tracing context extraction** using `TracingContextExtractor`:

- ✅ **Real Trace IDs** - Extracted from Micrometer Tracing (not generated timestamps)
- ✅ **Real Span IDs** - Extracted from current observation span
- ✅ **Brave Support** - Full support for Brave tracing backend (Zipkin)
- ✅ **OpenTelemetry Ready** - Prepared for OpenTelemetry backend
- ✅ **Automatic Configuration** - Tracer is automatically injected via Spring Boot
- ✅ **Distributed Tracing** - Full correlation with Zipkin, Jaeger, and other tracing systems

**Example trace IDs:**
```
traceId: 59e63de2fc596870  (16-character hex from Brave)
spanId: 59e63de2fc596870   (16-character hex from Brave)
```

**Implementation:** See `TracingContextExtractor` utility class for details.

### Audit Event Types

```java
public enum AuditEventType {
    OPERATION_STARTED,        // Job operation started
    OPERATION_COMPLETED,      // Job operation completed successfully
    OPERATION_FAILED,         // Job operation failed
    OPERATION_RETRIED,        // Job operation was retried
    CIRCUIT_BREAKER_OPENED,   // Circuit breaker opened
    CIRCUIT_BREAKER_CLOSED,   // Circuit breaker closed
    RATE_LIMIT_EXCEEDED,      // Rate limit exceeded
    STATUS_CHANGED,           // Job execution status changed
    CUSTOM                    // Custom audit event
}
```

## Execution Results

### What is Persisted?

Job execution results include:

- **Execution Metadata** - Execution ID, request ID, job type, status
- **Timing** - Start time, end time, duration
- **Data** - Raw output (COLLECT stage), transformed output (RESULT stage)
- **Transformation** - Target DTO class, mapper name
- **Caching** - Cacheable flag, TTL, expiration time
- **Context** - Initiator, orchestrator, job definition
- **Metrics** - **Precise data size in bytes** (calculated via JSON serialization), progress percentage, retry attempts
- **Tracing** - **Real trace ID and span ID** from Micrometer Tracing

#### Data Size Calculation

The library now includes **effective data size calculation** using `DataSizeCalculator`:

- ✅ **Precise Measurement** - Actual byte size via JSON serialization (not toString() estimation)
- ✅ **UTF-8 Encoding** - Accurate byte count using UTF-8 encoding
- ✅ **Combined Calculation** - Calculates total size of raw + transformed output
- ✅ **Human-Readable Formatting** - Converts bytes to KB, MB, GB, etc.
- ✅ **Size Validation** - Check if data exceeds size limits
- ✅ **Automatic Integration** - Used automatically in `JobExecutionResultService`

**Example usage:**
```java
// Automatic in JobExecutionResultService
long size = DataSizeCalculator.calculateCombinedSize(
    result.getRawOutput(),
    result.getTransformedOutput()
);
// size = 1247 bytes

String formatted = DataSizeCalculator.formatSize(size);
// formatted = "1.2 KB"

boolean tooLarge = DataSizeCalculator.exceedsSize(data, 1024 * 1024);
// tooLarge = false (< 1MB)
```

**Implementation:** See `DataSizeCalculator` utility class for details.

### Result Caching

Results can be cached for performance optimization:

```java
JobExecutionResult result = JobExecutionResult.builder()
    .cacheable(true)
    .ttlSeconds(3600)  // 1 hour
    .expiresAt(Instant.now().plusSeconds(3600))
    .build();

// Check if cached result is valid
if (result.isCacheableAndValid()) {
    // Use cached result
}
```

## Implementation Guide

### Step 1: Add Dependencies

Add R2DBC dependencies to your microservice `pom.xml`:

```xml
<!-- R2DBC PostgreSQL (Recommended) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>

<!-- Or R2DBC MySQL -->
<dependency>
    <groupId>io.asyncer</groupId>
    <artifactId>r2dbc-mysql</artifactId>
</dependency>

<!-- Or R2DBC H2 (for testing) -->
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-h2</artifactId>
    <scope>test</scope>
</dependency>
```

### Step 2: Configure R2DBC Connection

Add R2DBC configuration to `application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/yourdb
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      validation-query: SELECT 1

# Enable persistence features
firefly:
  data:
    orchestration:
      enabled: true
      persistence:
        audit-enabled: true
        result-persistence-enabled: true
        audit-retention-days: 90
        result-retention-days: 30
        enable-result-caching: true
        result-cache-ttl-seconds: 3600
```

### Step 3: Create Database Schema

Create tables for audit and results (PostgreSQL example):

```sql
-- Job Audit Trail Table
CREATE TABLE job_audit_entry (
    audit_id VARCHAR(255) PRIMARY KEY,
    execution_id VARCHAR(255) NOT NULL,
    request_id VARCHAR(255),
    stage VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(50),
    timestamp TIMESTAMP NOT NULL,
    initiator VARCHAR(255),
    job_type VARCHAR(255),
    input_parameters JSONB,
    output_data JSONB,
    error_message TEXT,
    error_stack_trace TEXT,
    duration_ms BIGINT,
    orchestrator_type VARCHAR(100),
    metadata JSONB,
    trace_id VARCHAR(255),
    span_id VARCHAR(255),
    resiliency_applied BOOLEAN,
    retry_attempts INTEGER,
    service_name VARCHAR(255),
    environment VARCHAR(50)
);

CREATE INDEX idx_audit_execution_id ON job_audit_entry(execution_id);
CREATE INDEX idx_audit_request_id ON job_audit_entry(request_id);
CREATE INDEX idx_audit_timestamp ON job_audit_entry(timestamp);
CREATE INDEX idx_audit_trace_id ON job_audit_entry(trace_id);
CREATE INDEX idx_audit_job_type ON job_audit_entry(job_type);

-- Job Execution Result Table
CREATE TABLE job_execution_result (
    result_id VARCHAR(255) PRIMARY KEY,
    execution_id VARCHAR(255) UNIQUE NOT NULL,
    request_id VARCHAR(255),
    job_type VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration BIGINT,
    initiator VARCHAR(255),
    input_parameters JSONB,
    raw_output JSONB,
    transformed_output JSONB,
    target_dto_class VARCHAR(500),
    mapper_name VARCHAR(255),
    error_message TEXT,
    error_cause TEXT,
    error_stack_trace TEXT,
    orchestrator_type VARCHAR(100),
    job_definition VARCHAR(500),
    progress_percentage INTEGER,
    retry_attempts INTEGER,
    cacheable BOOLEAN,
    ttl_seconds BIGINT,
    expires_at TIMESTAMP,
    metadata JSONB,
    trace_id VARCHAR(255),
    span_id VARCHAR(255),
    service_name VARCHAR(255),
    environment VARCHAR(50),
    schema_version VARCHAR(50),
    data_size_bytes BIGINT,
    tags JSONB
);

CREATE INDEX idx_result_execution_id ON job_execution_result(execution_id);
CREATE INDEX idx_result_request_id ON job_execution_result(request_id);
CREATE INDEX idx_result_job_type ON job_execution_result(job_type);
CREATE INDEX idx_result_status ON job_execution_result(status);
CREATE INDEX idx_result_start_time ON job_execution_result(start_time);
CREATE INDEX idx_result_expires_at ON job_execution_result(expires_at);
CREATE INDEX idx_result_trace_id ON job_execution_result(trace_id);
```




### Step 4: Create R2DBC Entity Classes

Create entity classes mapped to database tables:

```java
package com.example.yourservice.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("job_audit_entry")
@Data
public class JobAuditEntity {
    @Id
    private String auditId;
    private String executionId;
    private String requestId;
    private String stage;
    private String eventType;
    private String status;
    private Instant timestamp;
    private String initiator;
    private String jobType;

    @Column("input_parameters")
    private String inputParametersJson;

    @Column("output_data")
    private String outputDataJson;

    private String errorMessage;
    private String errorStackTrace;
    private Long durationMs;
    private String orchestratorType;

    @Column("metadata")
    private String metadataJson;

    private String traceId;
    private String spanId;
    private Boolean resiliencyApplied;
    private Integer retryAttempts;
    private String serviceName;
    private String environment;
}

@Table("job_execution_result")
@Data
public class JobExecutionResultEntity {
    @Id
    private String resultId;
    private String executionId;
    private String requestId;
    private String jobType;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private Long duration;
    private String initiator;

    @Column("input_parameters")
    private String inputParametersJson;

    @Column("raw_output")
    private String rawOutputJson;

    @Column("transformed_output")
    private String transformedOutputJson;

    private String targetDtoClass;
    private String mapperName;
    private String errorMessage;
    private String errorCause;
    private String errorStackTrace;
    private String orchestratorType;
    private String jobDefinition;
    private Integer progressPercentage;
    private Integer retryAttempts;
    private Boolean cacheable;
    private Long ttlSeconds;
    private Instant expiresAt;

    @Column("metadata")
    private String metadataJson;

    private String traceId;
    private String spanId;
    private String serviceName;
    private String environment;
    private String schemaVersion;
    private Long dataSizeBytes;

    @Column("tags")
    private String tagsJson;
}
```

### Step 5: Create R2DBC Entity Repositories

Create Spring Data R2DBC repositories:

```java
package com.example.yourservice.persistence.repository;

import com.example.yourservice.persistence.entity.JobAuditEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface R2dbcJobAuditEntityRepository extends ReactiveCrudRepository<JobAuditEntity, String> {

    Flux<JobAuditEntity> findByExecutionId(String executionId);

    Flux<JobAuditEntity> findByRequestId(String requestId);

    Flux<JobAuditEntity> findByInitiator(String initiator);

    Flux<JobAuditEntity> findByTimestampBetween(Instant startTime, Instant endTime);

    Flux<JobAuditEntity> findByJobType(String jobType);

    Flux<JobAuditEntity> findByEventType(String eventType);

    Flux<JobAuditEntity> findByTraceId(String traceId);

    @Query("DELETE FROM job_audit_entry WHERE timestamp < :timestamp")
    Mono<Long> deleteByTimestampBefore(Instant timestamp);

    Mono<Long> countByExecutionId(String executionId);

    Mono<Boolean> existsByExecutionId(String executionId);
}

@Repository
public interface R2dbcJobExecutionResultEntityRepository extends ReactiveCrudRepository<JobExecutionResultEntity, String> {

    Mono<JobExecutionResultEntity> findByExecutionId(String executionId);

    Mono<JobExecutionResultEntity> findByRequestId(String requestId);

    Flux<JobExecutionResultEntity> findByJobType(String jobType);

    Flux<JobExecutionResultEntity> findByStatus(String status);

    Flux<JobExecutionResultEntity> findByInitiator(String initiator);

    Flux<JobExecutionResultEntity> findByStartTimeBetween(Instant startTime, Instant endTime);

    @Query("SELECT * FROM job_execution_result WHERE cacheable = true AND (expires_at IS NULL OR expires_at > NOW())")
    Flux<JobExecutionResultEntity> findCacheableAndValid();

    Flux<JobExecutionResultEntity> findByTraceId(String traceId);

    @Query("DELETE FROM job_execution_result WHERE end_time < :timestamp")
    Mono<Long> deleteByEndTimeBefore(Instant timestamp);

    @Query("DELETE FROM job_execution_result WHERE expires_at < NOW()")
    Mono<Long> deleteExpired();

    Mono<Boolean> existsByExecutionId(String executionId);

    Mono<Long> countByJobType(String jobType);

    Mono<Long> countByStatus(String status);
}
```

### Step 6: Create Repository Adapters

Implement the port interfaces with R2DBC adapters. See the complete example in the [examples directory](../examples/r2dbc-persistence-adapter).

Key points for adapter implementation:

1. **JSON Serialization** - Use ObjectMapper to convert Maps to JSON strings for JSONB columns
2. **Enum Conversion** - Convert domain enums to strings and vice versa
3. **Error Handling** - Log errors and handle serialization failures gracefully
4. **Reactive Patterns** - Use `Mono.fromCallable()` for blocking operations like JSON serialization

Example adapter skeleton:

```java
@Repository
@Slf4j
public class R2dbcJobAuditRepositoryAdapter implements JobAuditRepository {

    private final R2dbcJobAuditEntityRepository r2dbcRepository;
    private final ObjectMapper objectMapper;

    public R2dbcJobAuditRepositoryAdapter(
            R2dbcJobAuditEntityRepository r2dbcRepository,
            ObjectMapper objectMapper) {
        this.r2dbcRepository = r2dbcRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<JobAuditEntry> save(JobAuditEntry entry) {
        return Mono.fromCallable(() -> toEntity(entry))
                .flatMap(r2dbcRepository::save)
                .map(this::toDomain)
                .doOnError(error -> log.error("Failed to save audit entry", error));
    }

    // Implement other methods...

    private JobAuditEntity toEntity(JobAuditEntry domain) {
        // Convert domain model to entity
        // Serialize Maps to JSON strings
    }

    private JobAuditEntry toDomain(JobAuditEntity entity) {
        // Convert entity to domain model
        // Deserialize JSON strings to Maps
    }
}
```

## Configuration

### Application Properties

Configure persistence features in `application.yml`:

```yaml
firefly:
  data:
    orchestration:
      enabled: true

      # Persistence Configuration
      persistence:
        # Audit Trail
        audit-enabled: true                    # Enable audit trail
        include-stack-traces: false            # Include stack traces in audit
        audit-retention-days: 90               # Keep audit entries for 90 days

        # Execution Results
        result-persistence-enabled: true       # Enable result persistence
        result-retention-days: 30              # Keep results for 30 days

        # Caching
        enable-result-caching: true            # Enable result caching
        result-cache-ttl-seconds: 3600         # Cache TTL: 1 hour

        # Data Management
        max-data-size-bytes: 10485760          # Max 10MB per result
        persist-raw-output: true               # Persist raw output
        persist-transformed-output: true       # Persist transformed output
        persist-input-parameters: true         # Persist input parameters

        # Security
        sanitize-sensitive-data: true          # Sanitize sensitive data
        excluded-parameter-keys: "password,secret,token,apiKey,authorization"
```

### Scheduled Cleanup

Configure scheduled cleanup of old data:

```java
@Configuration
@EnableScheduling
public class PersistenceCleanupConfig {

    @Autowired
    private JobAuditService auditService;

    @Autowired
    private JobExecutionResultService resultService;

    @Autowired
    private JobOrchestrationProperties properties;

    // Clean up old audit entries daily at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldAuditEntries() {
        int retentionDays = properties.getPersistence().getAuditRetentionDays();
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        auditService.cleanupOldAuditEntries(cutoffTime)
                .subscribe(
                    count -> log.info("Deleted {} old audit entries", count),
                    error -> log.error("Failed to cleanup audit entries", error)
                );
    }

    // Clean up old results daily at 3 AM
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldResults() {
        int retentionDays = properties.getPersistence().getResultRetentionDays();
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        resultService.cleanupOldResults(cutoffTime)
                .subscribe(
                    count -> log.info("Deleted {} old results", count),
                    error -> log.error("Failed to cleanup results", error)
                );
    }

    // Clean up expired cached results hourly
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredCachedResults() {
        resultService.cleanupExpiredCachedResults()
                .subscribe(
                    count -> log.info("Deleted {} expired cached results", count),
                    error -> log.error("Failed to cleanup expired cache", error)
                );
    }
}
```

## Best Practices

### 1. Use R2DBC for Reactive Applications

R2DBC is the recommended persistence technology for reactive applications. It provides:
- Non-blocking database access
- Better resource utilization
- Seamless integration with Project Reactor
- Support for PostgreSQL, MySQL, H2, and more

### 2. Implement Proper Error Handling

Always handle errors in your adapters:

```java
@Override
public Mono<JobAuditEntry> save(JobAuditEntry entry) {
    return Mono.fromCallable(() -> toEntity(entry))
            .flatMap(r2dbcRepository::save)
            .map(this::toDomain)
            .doOnError(error -> log.error("Failed to save audit entry: {}", error.getMessage(), error))
            .onErrorResume(error -> Mono.empty());  // Or handle appropriately
}
```

### 3. Sanitize Sensitive Data

Enable sensitive data sanitization to protect credentials:

```yaml
firefly:
  data:
    orchestration:
      persistence:
        sanitize-sensitive-data: true
        excluded-parameter-keys: "password,secret,token,apiKey,authorization,credentials"
```

### 4. Configure Appropriate Retention Policies

Balance compliance requirements with storage costs:

- **Audit Trail**: 90-365 days for compliance
- **Execution Results**: 30-90 days for debugging
- **Cached Results**: 1-24 hours for performance

### 5. Monitor Database Performance

Create indexes on frequently queried columns:

```sql
CREATE INDEX idx_audit_execution_id ON job_audit_entry(execution_id);
CREATE INDEX idx_audit_timestamp ON job_audit_entry(timestamp);
CREATE INDEX idx_result_execution_id ON job_execution_result(execution_id);
CREATE INDEX idx_result_expires_at ON job_execution_result(expires_at);
```

### 6. Use Connection Pooling

Configure R2DBC connection pooling for optimal performance:

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 50
      max-idle-time: 30m
      validation-query: SELECT 1
```

### 7. Test with H2 for Development

Use H2 for local development and testing:

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///testdb
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

### 8. Implement Observability

Monitor persistence operations with metrics:

```java
@Repository
@Slf4j
public class R2dbcJobAuditRepositoryAdapter implements JobAuditRepository {

    private final MeterRegistry meterRegistry;

    @Override
    public Mono<JobAuditEntry> save(JobAuditEntry entry) {
        return Mono.fromCallable(() -> toEntity(entry))
                .flatMap(r2dbcRepository::save)
                .map(this::toDomain)
                .doOnSuccess(saved -> meterRegistry.counter("audit.save.success").increment())
                .doOnError(error -> meterRegistry.counter("audit.save.error").increment());
    }
}
```

---

For complete working examples, see:
- [R2DBC PostgreSQL Adapter Example](../examples/r2dbc-persistence-adapter)
- [MongoDB Reactive Adapter Example](../examples/mongodb-persistence-adapter)
- [Redis Caching Adapter Example](../examples/redis-cache-adapter)
