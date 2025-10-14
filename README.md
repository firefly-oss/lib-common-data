# Firefly Common Data Library

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8+-red.svg)](https://maven.apache.org/)

A powerful Spring Boot library that enables standardized data processing architecture for core-data microservices with job orchestration support, CQRS, and event-driven architecture capabilities.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Configuration](#configuration)
- [API Endpoints](#api-endpoints)
- [Best Practices](#best-practices)
- [Extending the Library](#extending-the-library)
- [Testing](#testing)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

The `lib-common-data` library provides a unified approach to building data processing microservices within the Firefly ecosystem. It establishes common patterns for:

- **Job Orchestration**: Integration with workflow orchestrators like AWS Step Functions
- **Job Lifecycle Management**: Standardized stages (START, CHECK, COLLECT, RESULT)
- **Event-Driven Architecture**: Seamless integration with `lib-common-eda`
- **CQRS Support**: Built-in CQRS pattern integration via `lib-common-cqrs`
- **Transactional Workflows**: Full SAGA support through `lib-transactional-engine` with step event publishing

### Why Use This Library?

- ✅ **Standardization** - Consistent patterns across all data processing microservices
- ✅ **Flexibility** - Pluggable orchestrators via port/adapter architecture
- ✅ **Observability** - Built-in distributed tracing, metrics, and health checks
- ✅ **Scalability** - CQRS and reactive programming support
- ✅ **Reliability** - SAGA pattern for distributed transactions
- ✅ **Resiliency** - Circuit breaker, retry, rate limiting, and bulkhead patterns
- ✅ **Persistence** - Audit trail and execution result persistence with hexagonal architecture

---

## Features

### 🎯 Job Orchestration Ports

- **JobOrchestrator Interface**: Abstract port for any workflow orchestrator
- Pre-configured support for Apache Airflow and AWS Step Functions
- Extensible design for custom orchestrators (Azure Durable Functions, Google Cloud Workflows, etc.)

### 🔄 Standardized Job Stages

All core-data microservices follow the same job lifecycle:

1. **START**: Initialize and trigger data processing jobs
2. **CHECK**: Monitor job progress and status
3. **COLLECT**: Gather intermediate or final results
4. **RESULT**: Retrieve final results and perform cleanup

### 🎨 Service & Controller Interfaces

- **DataJobService**: Business logic interface for job stage operations
- **DataJobController**: REST API interface with OpenAPI documentation
- Consistent API contracts across all core-data microservices

### 📡 EDA & CQRS Integration

- Auto-configuration for event publishing and consuming
- Command/Query separation for better scalability
- Job event publishing for observability and auditing

### 🔄 SAGA & Transactional Engine Support

- **StepEventPublisherBridge** - Bridges SAGA step events to EDA infrastructure
- Distributed transaction coordination for complex data workflows
- Step event publishing with full traceability
- Multi-platform event delivery (Kafka, RabbitMQ, SQS, etc.)
- Automatic metadata enrichment for data processing context

### 📊 Observability & Monitoring

- **Distributed Tracing** - Micrometer Observation integration for end-to-end tracing
  - ✅ **Real Trace ID Extraction** - Extracts actual trace IDs from Brave/OpenTelemetry (not generated timestamps)
  - ✅ **Real Span ID Extraction** - Extracts actual span IDs from current observation
  - ✅ **Automatic Configuration** - Tracer automatically injected via Spring Boot
  - ✅ **Full Correlation** - Works with Zipkin, Jaeger, and other distributed tracing systems
- **Metrics Collection** - Comprehensive metrics for job execution, errors, and performance
  - ✅ **Precise Data Size Calculation** - Actual byte size via JSON serialization (not toString() estimation)
  - ✅ **Human-Readable Formatting** - Automatic conversion to KB, MB, GB
  - ✅ **Size Validation** - Built-in utilities to check data size limits
- **Health Checks** - Reactive health indicators for orchestrator availability
- **Prometheus Integration** - Ready-to-use metrics export for monitoring dashboards
- **Structured Logging** - Comprehensive logging for all job lifecycle phases (START, CHECK, COLLECT, RESULT)

### 🛡️ Resiliency Patterns

- **Circuit Breaker** - Prevents cascading failures with automatic recovery
- **Retry** - Configurable retry mechanism with exponential backoff support
- **Rate Limiter** - Controls request rate to prevent system overload
- **Bulkhead** - Isolates resources to prevent resource exhaustion
- **Resilience4j Integration** - Production-ready resiliency patterns

### 💾 Persistence & Audit Trail

- **Audit Trail** - Automatic recording of all job operations for compliance and debugging
- **Execution Results** - Persistent storage of job results with caching support
- **Hexagonal Architecture** - Port/adapter pattern for flexible persistence implementations
- **Multi-Database Support** - JPA, MongoDB, DynamoDB, Redis, or custom implementations
- **Configurable Retention** - Automatic cleanup of old audit entries and results
- **Sensitive Data Sanitization** - Built-in protection for sensitive information

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-data</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Enable Auto-Configuration

The library uses Spring Boot auto-configuration. Simply add the dependency and configure your `application.yml`:

```yaml
firefly:
  data:
    eda:
      enabled: true
    cqrs:
      enabled: true
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:DataJobStateMachine
    transactional:
      enabled: true
  stepevents:
    enabled: true
    topic: data-processing-step-events
    include-job-context: true
```

### 2. Implement DataJobService

Create a service that implements the `DataJobService` interface:

```java
@Service
@Slf4j
public class MyDataJobService implements DataJobService {

    private final JobOrchestrator jobOrchestrator;
    
    @Autowired
    public MyDataJobService(JobOrchestrator jobOrchestrator) {
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    public Mono<JobStageResponse> startJob(JobStageRequest request) {
        log.info("Starting job with parameters: {}", request.getParameters());
        
        JobExecutionRequest executionRequest = JobExecutionRequest.builder()
            .jobDefinition("my-data-processing-workflow")
            .input(request.getParameters())
            .build();
            
        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId(execution.getExecutionId())
                .status(execution.getStatus())
                .success(true)
                .message("Job started successfully")
                .timestamp(Instant.now())
                .build());
    }

    @Override
    public Mono<JobStageResponse> checkJob(JobStageRequest request) {
        return jobOrchestrator.checkJobStatus(request.getExecutionId())
            .map(status -> JobStageResponse.builder()
                .stage(JobStage.CHECK)
                .executionId(request.getExecutionId())
                .status(status)
                .success(true)
                .build());
    }

    @Override
    public Mono<JobStageResponse> collectJobResults(JobStageRequest request) {
        // Collect RAW data from job execution (no transformation)
        return jobOrchestrator.getJobExecution(request.getExecutionId())
            .map(execution -> {
                Map<String, Object> rawData = execution.getOutput();
                
                return JobStageResponse.builder()
                    .stage(JobStage.COLLECT)
                    .executionId(execution.getExecutionId())
                    .status(execution.getStatus())
                    .data(rawData)  // Raw, unprocessed data
                    .success(true)
                    .message("Raw results collected successfully")
                    .timestamp(Instant.now())
                    .build();
            });
    }

    @Override
    public Mono<JobStageResponse> getJobResult(JobStageRequest request) {
        // Get raw data first, then apply MapStruct transformation
        return collectJobResults(request)
            .flatMap(collectResponse -> {
                try {
                    // Load target DTO class
                    Class<?> targetClass = Class.forName(request.getTargetDtoClass());
                    
                    // Get appropriate mapper from registry
                    JobResultMapper mapper = mapperRegistry.getMapper(targetClass)
                        .orElseThrow(() -> new MapperNotFoundException(targetClass));
                    
                    // Extract raw data
                    Map<String, Object> rawData = collectResponse.getData();
                    
                    // Transform raw data to target DTO using MapStruct
                    Object mappedResult = mapper.mapToTarget(rawData);
                    
                    return Mono.just(JobStageResponse.builder()
                        .stage(JobStage.RESULT)
                        .executionId(request.getExecutionId())
                        .status(collectResponse.getStatus())
                        .data(Map.of("result", mappedResult))  // Transformed DTO
                        .success(true)
                        .message("Results transformed successfully")
                        .timestamp(Instant.now())
                        .build());
                        
                } catch (ClassNotFoundException e) {
                    return Mono.error(new IllegalArgumentException(
                        "Target DTO class not found: " + request.getTargetDtoClass(), e));
                }
            });
    }
}
```

### 3. Implement DataJobController

Create a REST controller that implements the `DataJobController` interface:

```java
@RestController
@Slf4j
public class MyDataJobController implements DataJobController {

    private final DataJobService dataJobService;
    
    @Autowired
    public MyDataJobController(DataJobService dataJobService) {
        this.dataJobService = dataJobService;
    }

    @Override
    public Mono<JobStageResponse> startJob(JobStageRequest request) {
        return dataJobService.startJob(request);
    }

    @Override
    public Mono<JobStageResponse> checkJob(String executionId, String requestId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .requestId(requestId)
            .build();
        return dataJobService.checkJob(request);
    }

    @Override
    public Mono<JobStageResponse> collectJobResults(String executionId, String requestId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.COLLECT)
            .executionId(executionId)
            .requestId(requestId)
            .build();
        return dataJobService.collectJobResults(request);
    }

    @Override
    public Mono<JobStageResponse> getJobResult(String executionId, String requestId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.RESULT)
            .executionId(executionId)
            .requestId(requestId)
            .build();
        return dataJobService.getJobResult(request);
    }
}
```

## Architecture

### Port/Adapter Pattern

The library uses the port/adapter (hexagonal) architecture:

- **Ports**: `JobOrchestrator` interface defines contracts (provided by this library)
- **Adapters**: Implementations for specific orchestrators (must be implemented in your application)
- **Domain**: Job models and lifecycle management

### Integration with Other Libraries

```
lib-common-data
├── lib-common-eda (Event-Driven Architecture)
│   └── Multi-platform event publishing (Kafka, RabbitMQ, SQS, etc.)
├── lib-common-cqrs (Command/Query Separation)
│   └── Scalable read/write models
└── lib-transactional-engine (SAGA Support)
    ├── Distributed transaction coordination
    └── StepEventPublisherBridge → publishes to lib-common-eda
```

**Key Integration Points:**

1. **EDA Integration**: Job events and SAGA step events flow through the unified EDA infrastructure
2. **CQRS Integration**: Separate command (write) and query (read) models for job data
3. **Transactional Engine**: SAGA step events are automatically bridged to EDA for observability

### 4. Create MapStruct Mappers for Result Transformation

Define MapStruct mappers to transform raw job results into target DTOs:

```java
@Mapper(componentModel = "spring")
public interface CustomerDataMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    
    @Override
    @Mapping(source = "customerId", target = "id")
    @Mapping(source = "firstName", target = "givenName")
    @Mapping(source = "lastName", target = "familyName")
    @Mapping(source = "email", target = "emailAddress")
    @Mapping(source = "createdAt", target = "registrationDate")
    CustomerDTO mapToTarget(Map<String, Object> rawData);
    
    @Override
    default Class<CustomerDTO> getTargetType() {
        return CustomerDTO.class;
    }
}
```

**Complex Mapping Example:**

```java
@Mapper(componentModel = "spring")
public interface SalesDataMapper extends JobResultMapper<Map<String, Object>, SalesReportDTO> {
    
    @Override
    @Mapping(target = "reportId", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(source = "totalRevenue", target = "revenue")
    @Mapping(source = "orderCount", target = "totalOrders")
    @Mapping(source = "period", target = "reportingPeriod", qualifiedByName = "parsePeriod")
    SalesReportDTO mapToTarget(Map<String, Object> rawData);
    
    @Named("parsePeriod")
    default ReportingPeriod parsePeriod(String period) {
        // Custom transformation logic
        return ReportingPeriod.fromString(period);
    }
    
    @Override
    default Class<SalesReportDTO> getTargetType() {
        return SalesReportDTO.class;
    }
}
```

**Using Mappers in Controller:**

```java
@RestController
public class MyDataJobController implements DataJobController {

    private final DataJobService dataJobService;
    
    @Override
    public Mono<JobStageResponse> getJobResult(String executionId, String requestId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.RESULT)
            .executionId(executionId)
            .requestId(requestId)
            .targetDtoClass("com.example.dto.CustomerDTO")  // Specify target DTO
            .build();
            
        // Service will automatically use CustomerDataMapper
        return dataJobService.getJobResult(request);
    }
}
```

**Result Comparison:**

```json
// COLLECT stage response (raw data)
{
  "stage": "COLLECT",
  "data": {
    "customerId": "12345",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "createdAt": "2024-01-15T10:30:00Z"
  }
}

// RESULT stage response (mapped DTO)
{
  "stage": "RESULT",
  "data": {
    "result": {
      "id": "12345",
      "givenName": "John",
      "familyName": "Doe",
      "emailAddress": "john.doe@example.com",
      "registrationDate": "2024-01-15T10:30:00Z"
    }
  }
}
```

### 5. Implementing Persistence Adapters (Optional)

To enable audit trail and execution result persistence, implement repository adapters in your microservice:

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

    @Override
    public Flux<JobAuditEntry> findByExecutionId(String executionId) {
        return r2dbcRepository.findByExecutionId(executionId)
                .map(this::toDomain);
    }

    // Implement other methods...
}

@Repository
public interface R2dbcJobAuditEntityRepository extends ReactiveCrudRepository<JobAuditEntity, String> {
    Flux<JobAuditEntity> findByExecutionId(String executionId);
    Flux<JobAuditEntity> findByRequestId(String requestId);
    // Other query methods...
}
```

**Configuration:**

```yaml
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
```

For complete implementation guide with R2DBC, see [Persistence Documentation](docs/persistence.md).

### 6. Using SAGAs for Distributed Data Processing (Optional)

For complex workflows requiring distributed transactions:

```java
@Service
@Slf4j
public class DataProcessingSaga {

    @Autowired
    private SagaOrchestrator sagaOrchestrator;
    
    @Autowired
    private DataJobService dataJobService;

    public Mono<SagaResult> processDataWithSaga(DataProcessingRequest request) {
        return sagaOrchestrator.execute(
            SagaDefinition.builder()
                .sagaName("data-processing-workflow")
                .step("extract", this::extractData, this::compensateExtract)
                .step("transform", this::transformData, this::compensateTransform)
                .step("load", this::loadData, this::compensateLoad)
                .build(),
            request
        );
    }

    private Mono<StepResult> extractData(DataProcessingRequest request) {
        log.info("Extracting data from source: {}", request.getSource());
        // Step events are automatically published via StepEventPublisherBridge
        return dataJobService.startJob(
            JobStageRequest.builder()
                .stage(JobStage.START)
                .parameters(Map.of("operation", "extract", "source", request.getSource()))
                .build()
        ).map(response -> StepResult.success(response));
    }

    private Mono<Void> compensateExtract(DataProcessingRequest request) {
        log.warn("Compensating extract step");
        // Cleanup logic
        return Mono.empty();
    }

    // Similar methods for transform and load steps...
}
```

Step events are automatically published to the configured topic and can be consumed by other services:

```java
@Component
public class DataProcessingStepEventListener {

    @EventSubscriber(
        topic = "data-processing-step-events",
        eventType = "STEP_STARTED"
    )
    public Mono<Void> onStepStarted(StepEventEnvelope event) {
        log.info("Data processing step started: SAGA={}, Step={}", 
                event.getSagaName(), event.getStepId());
        // React to step events (monitoring, notifications, etc.)
        return Mono.empty();
    }
}
```

## Configuration

### Job Orchestration Configuration

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      default-timeout: 24h
      max-retries: 3
      retry-delay: 5s
      publish-job-events: true
      job-events-topic: data-job-events
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:DataJobStateMachine
        use-default-credentials: true
```

### EDA Configuration

```yaml
firefly:
  data:
    eda:
      enabled: true
  eda:
    publishers:
      - id: default
        type: KAFKA
        connection-id: default
    connections:
      kafka:
        - id: default
          bootstrap-servers: localhost:9092
```

### CQRS Configuration

```yaml
firefly:
  data:
    cqrs:
      enabled: true
```

### Transactional Engine & Step Events Configuration

```yaml
firefly:
  data:
    transactional:
      enabled: true
  stepevents:
    enabled: true
    # Dedicated topic for data processing step events
    topic: data-processing-step-events
    # Include job execution context in step event headers
    include-job-context: true
```

**Step Event Headers:**

When step events are published, they include comprehensive metadata:

- `step.saga_name` - Name of the SAGA
- `step.saga_id` - Unique SAGA instance ID
- `step.step_id` - Step identifier
- `step.type` - Event type (STEP_STARTED, STEP_COMPLETED, STEP_FAILED, etc.)
- `step.attempts` - Number of execution attempts
- `step.latency_ms` - Step execution time
- `step.started_at` - Start timestamp
- `step.completed_at` - Completion timestamp
- `step.result_type` - Result status
- `context` - Always set to "data-processing"
- `library` - Always set to "lib-common-data"
- `routing_key` - For message partitioning (format: `{sagaName}:{sagaId}`)

## API Endpoints

When implementing `DataJobController`, the following REST endpoints are exposed:

- `POST /api/v1/jobs/start` - Start a new job
- `GET /api/v1/jobs/{executionId}/check` - Check job status
- `GET /api/v1/jobs/{executionId}/collect` - Collect job results
- `GET /api/v1/jobs/{executionId}/result` - Get final results

All endpoints are documented with OpenAPI/Swagger annotations.

## Best Practices

### 1. Job Idempotency

Ensure job operations are idempotent:

```java
@Override
public Mono<JobStageResponse> startJob(JobStageRequest request) {
    // Check if job already exists
    return checkExistingJob(request)
        .switchIfEmpty(createNewJob(request));
}
```

### 2. Error Handling

Implement proper error handling and retry logic:

```java
@Override
public Mono<JobStageResponse> checkJob(JobStageRequest request) {
    return jobOrchestrator.checkJobStatus(request.getExecutionId())
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
        .onErrorResume(error -> {
            log.error("Error checking job status", error);
            return Mono.just(JobStageResponse.failure(
                JobStage.CHECK, 
                request.getExecutionId(), 
                error.getMessage()
            ));
        });
}
```

### 3. Event Publishing

Publish job lifecycle events for observability:

```java
private void publishJobEvent(JobStage stage, String executionId) {
    if (properties.isPublishJobEvents()) {
        JobEvent event = new JobEvent(stage, executionId, Instant.now());
        eventPublisher.publish(event, properties.getJobEventsTopic());
    }
}
```

### 4. Persistence & Audit Trail

The library automatically persists audit trail and execution results when repository adapters are provided:

```java
// Audit trail is automatically recorded for all operations
// No code changes needed - just provide repository adapters

// Query audit trail
auditService.getAuditTrail(executionId)
    .subscribe(entries -> log.info("Audit trail: {}", entries));

// Query execution results
resultService.getResult(executionId)
    .subscribe(result -> log.info("Result: {}", result));

// Get cached result
resultService.getCachedResult(executionId)
    .filter(JobExecutionResult::isCacheableAndValid)
    .subscribe(cached -> log.info("Using cached result"));
```

For implementation details, see [Persistence Documentation](docs/persistence.md).

## Extending the Library

### Creating a Custom JobOrchestrator Adapter

To support a new orchestrator:

```java
@Service
@ConditionalOnProperty(prefix = "firefly.data.orchestration", 
                       name = "orchestrator-type", 
                       havingValue = "CUSTOM")
public class CustomJobOrchestrator implements JobOrchestrator {

    @Override
    public Mono<JobExecution> startJob(JobExecutionRequest request) {
        // Implement custom orchestrator integration
    }

    @Override
    public String getOrchestratorType() {
        return "CUSTOM";
    }
    
    // Implement other methods...
}
```

## Testing

Example test using the library:

```java
@SpringBootTest
class DataJobServiceTest {

    @MockBean
    private JobOrchestrator jobOrchestrator;
    
    @Autowired
    private DataJobService dataJobService;
    
    @Test
    void shouldStartJobSuccessfully() {
        JobExecutionRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .parameters(Map.of("key", "value"))
            .build();
            
        JobExecution execution = JobExecution.builder()
            .executionId("exec-123")
            .status(JobExecutionStatus.RUNNING)
            .build();
            
        when(jobOrchestrator.startJob(any()))
            .thenReturn(Mono.just(execution));
            
        StepVerifier.create(dataJobService.startJob(request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getExecutionId()).isEqualTo("exec-123");
            })
            .verifyComplete();
    }
}
```

## Documentation

For detailed documentation, see the [`docs/`](docs/) directory:

### Core Documentation
- **[Architecture](docs/architecture.md)** - Deep dive into hexagonal architecture and design patterns
- **[Getting Started](docs/getting-started.md)** - Step-by-step guide with complete examples
- **[Configuration](docs/configuration.md)** - Comprehensive configuration reference
- **[Job Lifecycle](docs/job-lifecycle.md)** - Detailed explanation of job stages and data flow

### Advanced Features
- **[Observability](docs/observability.md)** - Distributed tracing, metrics, and health checks
- **[Resiliency](docs/resiliency.md)** - Circuit breaker, retry, rate limiting, and bulkhead patterns
- **[Logging](docs/logging.md)** - Comprehensive logging for all job lifecycle phases
- **[MapStruct Mappers](docs/mappers.md)** - Guide to result transformation with MapStruct
- **[SAGA Integration](docs/saga-integration.md)** - Distributed transactions and step events

### Reference
- **[API Reference](docs/api-reference.md)** - Complete API documentation
- **[Examples](docs/examples.md)** - Real-world usage patterns and scenarios
- **[Testing Guide](docs/testing.md)** - Testing strategies and examples

### Additional Resources
- **[Documentation Cleanup Summary](DOCUMENTATION_CLEANUP_SUMMARY.md)** - Details on documentation accuracy and what's provided vs what must be implemented

## Contributing

We welcome contributions! Please follow these guidelines:

1. Follow the coding standards established in `lib-common-domain` and `lib-common-core`
2. Write tests for new features
3. Update documentation for any API changes
4. Ensure all tests pass before submitting a PR

## License

Copyright 2025 Firefly Software Solutions Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

---

**Made with ❤️ by the Firefly Team**
