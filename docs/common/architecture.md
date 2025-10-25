# Architecture

This document provides a comprehensive overview of the `lib-common-data` architecture, design patterns, and integration points.

## Table of Contents

- [Overview](#overview)
- [Hexagonal Architecture](#hexagonal-architecture)
- [Core Components](#core-components)
- [Integration Architecture](#integration-architecture)
- [Data Flow](#data-flow)
- [Design Patterns](#design-patterns)
- [Component Interactions](#component-interactions)

---

## Overview

The `lib-common-data` library is built on **Hexagonal Architecture** (also known as Ports and Adapters pattern), which provides:

- **Clean separation** between business logic and infrastructure
- **Pluggable adapters** for different orchestration platforms
- **Testability** through dependency inversion
- **Flexibility** to swap implementations without changing core logic

### Architectural Principles

1. **Dependency Inversion** - Core domain depends on abstractions, not implementations
2. **Interface Segregation** - Small, focused interfaces for specific purposes
3. **Single Responsibility** - Each component has one clear purpose
4. **Open/Closed** - Open for extension, closed for modification
5. **Reactive Programming** - Non-blocking, event-driven operations

---

## Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        External World                           │
│  ┌──────────────┐  ┌───────────────┐  ┌───────────────┐         │
│  │ REST Clients │  │ Airflow/AWS   │  │ Kafka/RabbitMQ│         │
│  └──────┬───────┘  └───────┬───────┘  └───────┬───────┘         │
└─────────┼──────────────────┼──────────────────┼─────────────────┘
          │                  │                  │
          │ HTTP             │ REST API         │ Events
          │                  │                  │
┌─────────┼──────────────────┼──────────────────┼─────────────────┐
│         ▼                  ▼                  ▼                 │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐          │
│  │   Adapter    │  │   Adapter    │  │   Adapter     │          │
│  │ (Controller) │  │(Orchestrator)│  │(EDA Publisher)│          │
│  └──────┬───────┘  └──────┬───────┘  └────────┬──────┘          │
│         │                  │                  │                 │
│         │                  │                  │                 │
│  ┌──────┴──────────────────┴──────────────────┴───────┐         │
│  │                                                    │         │
│  │              PORT INTERFACES                       │         │
│  │  ┌─────────────────────────────────────────────┐   │         │
│  │  │ DataJobController (REST API Port)           │   │         │
│  │  │ JobOrchestrator (Orchestration Port)        │   │         │
│  │  │ EventPublisher (EDA Port)                   │   │         │
│  │  │ StepEventPublisher (SAGA Port)              │   │         │
│  │  └─────────────────────────────────────────────┘   │         │
│  │                                                    │         │
│  │              DOMAIN CORE                           │         │
│  │  ┌─────────────────────────────────────────────┐   │         │
│  │  │ DataJobService (Business Logic)             │   │         │
│  │  │ JobResultMapperRegistry (Transformation)    │   │         │
│  │  │ Job Models (Domain Objects)                 │   │         │
│  │  │ Job Lifecycle (START→CHECK→COLLECT→RESULT)  │   │         │
│  │  └─────────────────────────────────────────────┘   │         │
│  │                                                    │         │
│  └────────────────────────────────────────────────────┘         │
│                                                                 │
│                    lib-common-data                              │
└─────────────────────────────────────────────────────────────────┘
```

### Port Types

#### Inbound Ports (Driving)
- **DataJobController** - REST API interface for external clients
- Defines what the application can do
- Implemented by adapters (controllers)

#### Outbound Ports (Driven)
- **JobOrchestrator** - Interface for workflow orchestration
- **EventPublisher** - Interface for event publishing
- **StepEventPublisher** - Interface for SAGA step events
- Defines what the application needs
- Implemented by infrastructure adapters

---

## Core Components

### 1. Configuration Layer

Auto-configuration classes that enable seamless integration:

```
config/
├── CqrsAutoConfiguration          # CQRS pattern support
├── EdaAutoConfiguration            # Event-driven architecture
├── JobOrchestrationAutoConfiguration  # Job orchestration setup
├── JobOrchestrationProperties      # Configuration properties
├── StepBridgeConfiguration         # SAGA step event bridge
├── StepEventsProperties            # Step event configuration
└── TransactionalEngineAutoConfiguration  # SAGA support
```

**Key Features:**
- Conditional bean creation based on classpath and properties
- Zero-configuration defaults with override capability
- Spring Boot auto-configuration mechanism
- Component scanning for automatic discovery

### 2. Controller Layer

REST API interface definitions:

#### Asynchronous Jobs (Multi-Stage)

```java
@Tag(name = "Data Jobs")
@RequestMapping("/api/v1/jobs")
public interface DataJobController {
    @PostMapping("/start")
    Mono<JobStageResponse> startJob(@Valid @RequestBody JobStageRequest request);

    @GetMapping("/{executionId}/check")
    Mono<JobStageResponse> checkJob(@PathVariable String executionId, ...);

    @GetMapping("/{executionId}/collect")
    Mono<JobStageResponse> collectJobResults(@PathVariable String executionId, ...);

    @GetMapping("/{executionId}/result")
    Mono<JobStageResponse> getJobResult(@PathVariable String executionId, ...);
}
```

#### Synchronous Jobs (Single-Stage)

```java
@Tag(name = "Sync Data Jobs")
@RequestMapping("/api/v1")
public interface SyncDataJobController {
    @PostMapping("/execute")
    Mono<JobStageResponse> execute(@RequestParam Map<String, Object> parameters, ...);
}
```

**Responsibilities:**
- Define REST API contract
- OpenAPI/Swagger documentation
- Request validation
- Delegate to service layer

### 3. Service Layer

Business logic interfaces:

#### Asynchronous Jobs (Multi-Stage)

```java
public interface DataJobService {
    Mono<JobStageResponse> startJob(JobStageRequest request);
    Mono<JobStageResponse> checkJob(JobStageRequest request);
    Mono<JobStageResponse> collectJobResults(JobStageRequest request);
    Mono<JobStageResponse> getJobResult(JobStageRequest request);
}
```

#### Synchronous Jobs (Single-Stage)

```java
public interface SyncDataJobService {
    Mono<JobStageResponse> execute(JobStageRequest request);
}
```

**Responsibilities:**
- Implement job lifecycle logic
- Coordinate with orchestrator (async) or execute directly (sync)
- Handle transformations via mappers
- Publish events for observability
- Provide observability, resiliency, and persistence (via abstract base classes)

### 4. Model Layer

Domain models representing job concepts:

```
model/
├── JobStage                # Enum: START, CHECK, COLLECT, RESULT
├── JobStageRequest         # Request DTO with parameters
└── JobStageResponse        # Response DTO with results
```

### 5. Orchestration Layer

Port/adapter for workflow orchestration:

```
orchestration/
├── port/
│   └── JobOrchestrator     # Port interface
└── model/
    ├── JobExecution        # Execution details
    ├── JobExecutionRequest # Start request
    └── JobExecutionStatus  # Status enum
```

**Port Interface:**
```java
public interface JobOrchestrator {
    Mono<JobExecution> startJob(JobExecutionRequest request);
    Mono<JobExecutionStatus> checkJobStatus(String executionId);
    Mono<JobExecutionStatus> stopJob(String executionId, String reason);
    Mono<JobExecution> getJobExecution(String executionId);
    String getOrchestratorType();
}
```

**Orchestrator Support:**

The library provides the `JobOrchestrator` port interface that can be implemented for any workflow orchestrator:

1. **Apache Airflow** (Recommended)
   - Open-source workflow orchestration platform
   - REST API integration
   - Flexible DAG-based workflows
   - Supports complex data pipelines
   - Configuration: `orchestrator-type: APACHE_AIRFLOW`
   - **Note**: Adapter implementation required in your application

2. **AWS Step Functions**
   - Serverless orchestration service
   - Native AWS integration
   - Visual workflow designer
   - Pay-per-use pricing
   - Configuration: `orchestrator-type: AWS_STEP_FUNCTIONS`
   - **Note**: Adapter implementation required in your application

3. **Custom Orchestrators**
   - Implement the `JobOrchestrator` interface
   - Full control over orchestration logic
   - Integration with proprietary systems

> **Important**: This library provides the port interface (`JobOrchestrator`) and configuration support. You must implement the adapter for your chosen orchestrator in your application. See the [Getting Started](getting-started.md) guide for implementation examples.

### 6. Mapper Layer

MapStruct integration for result transformation:

```
mapper/
├── JobResultMapper         # Generic mapper interface
└── JobResultMapperRegistry # Auto-discovery registry
```

**Generic Interface:**
```java
public interface JobResultMapper<S, T> {
    T mapToTarget(S source);
    default Class<S> getSourceType() { ... }
    default Class<T> getTargetType() { ... }
}
```

### 7. Step Events Layer

SAGA integration bridge:

```
stepevents/
└── StepEventPublisherBridge  # Bridges SAGA to EDA
```

---

## Integration Architecture

### Integration with lib-common-eda

```
┌─────────────────────────────────────────────────────────┐
│                  lib-common-data                        │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  Job Events                                │         │
│  │  - Job started                             │         │
│  │  - Job completed                           │         │
│  │  - Job failed                              │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│  ┌────────────▼───────────────────────────────┐         │
│  │  StepEventPublisherBridge                  │         │
│  │  - SAGA step events                        │         │
│  │  - Metadata enrichment                     │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              lib-common-eda                             │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  EventPublisher                            │         │
│  │  - Multi-platform support                  │         │
│  │  - Resilience patterns                     │         │
│  │  - Metrics & monitoring                    │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│         Message Brokers                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  Kafka   │  │ RabbitMQ │  │   SQS    │               │
│  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────┘
```

**Integration Points:**
- Job lifecycle events published to EDA
- SAGA step events bridged to EDA
- Configurable topics and routing
- Automatic metadata enrichment

### Integration with lib-common-cqrs

```
┌─────────────────────────────────────────────────────────┐
│              lib-common-data                            │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  DataJobService                            │         │
│  │  - Write operations (Commands)             │         │
│  │  - Read operations (Queries)               │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              lib-common-cqrs                            │
│                                                         │
│  ┌────────────────────┐  ┌────────────────────┐         │
│  │  CommandHandler    │  │  QueryHandler      │         │
│  │  - Start job       │  │  - Get job status  │         │
│  │  - Stop job        │  │  - Get results     │         │
│  └────────────────────┘  └────────────────────┘         │
└─────────────────────────────────────────────────────────┘
```

**Separation:**
- **Commands** - Modify state (START, STOP)
- **Queries** - Read state (CHECK, COLLECT, RESULT)
- Enables independent scaling of read/write operations

### Integration with lib-transactional-engine

```
┌─────────────────────────────────────────────────────────┐
│         lib-transactional-engine                        │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  SAGA Orchestrator                         │         │
│  │  - Step execution                          │         │
│  │  - Compensation logic                      │         │
│  │  - Transaction coordination                │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│               │ Step Events                             │
│               ▼                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  StepEventPublisher (interface)            │         │
│  └────────────────────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              lib-common-data                            │
│                                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  StepEventPublisherBridge                  │         │
│  │  (implements StepEventPublisher)           │         │
│  │  - Enriches with data context              │         │
│  │  - Routes to EDA infrastructure            │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
         (to lib-common-eda)
```

---

## Data Flow

### Complete Job Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. START Stage                                                  │
│                                                                 │
│  Client Request                                                 │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.startJob()                                   │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.startJob()                                      │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.startJob()  ──────► Airflow/AWS Step Functions │
│       │                                                         │
│       ▼                                                         │
│  Return: executionId, status=RUNNING                            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 2. CHECK Stage                                                  │
│                                                                 │
│  Client Request (with executionId)                              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.checkJob()                                   │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.checkJob()                                      │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.checkJobStatus()  ──────► Query orchestrator   │
│       │                                                         │
│       ▼                                                         │
│  Return: status, progress                                       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 3. COLLECT Stage (Raw Data)                                     │
│                                                                 │
│  Client Request (with executionId)                              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.collectJobResults()                          │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.collectJobResults()                             │
│       │                                                         │
│       ▼                                                         │
│  JobOrchestrator.getJobExecution()  ──────► Get raw output      │
│       │                                                         │
│       ▼                                                         │
│  Return: Map<String, Object> (RAW DATA - no transformation)     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 4. RESULT Stage (Mapped Data)                                   │
│                                                                 │
│  Client Request (with executionId, targetDtoClass)              │
│       │                                                         │
│       ▼                                                         │
│  DataJobController.getJobResult()                               │
│       │                                                         │
│       ▼                                                         │
│  DataJobService.getJobResult()                                  │
│       │                                                         │
│       ├──► 1. Call collectJobResults() ──► Get raw data         │
│       │                                                         │
│       ├──► 2. JobResultMapperRegistry.getMapper(targetClass)    │
│       │                                                         │
│       ├──► 3. mapper.mapToTarget(rawData) ──► MapStruct         │
│       │                                                         │
│       └──► 4. Return: Mapped DTO (TRANSFORMED DATA)             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Design Patterns

### 1. Port/Adapter (Hexagonal Architecture)

**Purpose:** Decouple business logic from infrastructure

**Implementation:**
- `JobOrchestrator` is a port (interface) provided by this library
- Adapters for Apache Airflow, AWS Step Functions, or custom orchestrators must be implemented in your application
- Easy to add custom adapters for other orchestrators

### 2. Strategy Pattern

**Purpose:** Pluggable orchestrator implementations

**Implementation:**
- Different orchestrator strategies (Apache Airflow, AWS Step Functions, Custom)
- Selected via configuration property
- Runtime strategy selection

### 3. Registry Pattern

**Purpose:** Manage and discover mappers

**Implementation:**
- `JobResultMapperRegistry` auto-discovers all mappers
- Type-safe lookup by target class
- Reflection-based generic type extraction

### 4. Builder Pattern

**Purpose:** Fluent API for complex objects

**Implementation:**
- All request/response models use Lombok `@Builder`
- Readable, maintainable code
- Immutable objects

### 5. Template Method Pattern

**Purpose:** Define algorithm skeleton

**Implementation:**
- Job lifecycle stages define the template
- Implementations fill in specific steps
- Consistent flow across all services

### 6. Bridge Pattern

**Purpose:** Decouple abstraction from implementation

**Implementation:**
- `StepEventPublisherBridge` bridges SAGA to EDA
- Allows independent evolution of both sides
- Transparent integration

---

## Component Interactions

### Sequence Diagram: Complete Job Flow

```
Client          Controller      Service         Orchestrator    Mapper          EDA
  │                 │              │                 │            │              │
  │─START──────────>│              │                 │            │              │
  │                 │───startJob──>│                 │            │              │
  │                 │              │──startJob──────>│            │              │
  │                 │              │<─execution──────│            │              │
  │                 │              │─────────────────────────────────publish────>│
  │                 │<─response────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─CHECK──────────>│              │                 │            │              │
  │                 │──checkJob───>│                 │            │              │
  │                 │              │──checkStatus───>│            │              │
  │                 │              │<─status─────────│            │              │
  │                 │<─response────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─COLLECT────────>│              │                 │            │              │
  │                 │──collect────>│                 │            │              │
  │                 │              │──getExecution──>│            │              │
  │                 │              │<─rawData────────│            │              │
  │                 │<─rawData─────│                 │            │              │
  │<────────────────│              │                 │            │              │
  │                 │              │                 │            │              │
  │─RESULT─────────>│              │                 │            │              │
  │                 │───getResult─>│                 │            │              │
  │                 │              │──collect───────>│            │              │
  │                 │              │<─rawData────────│            │              │
  │                 │              │──getMapper──────────────────>│              │
  │                 │              │<─mapper──────────────────────│              │
  │                 │              │──mapToTarget────────────────>│              │
  │                 │              │<─mappedDTO───────────────────│              │
  │                 │<─mappedDTO───│                 │            │              │
  │<────────────────│              │                 │            │              │
```

---

## Summary

The `lib-common-data` architecture provides:

✅ **Clean Architecture** - Hexagonal design with clear boundaries
✅ **Flexibility** - Pluggable adapters for different platforms
✅ **Dual Job Types** - Asynchronous (multi-stage) and Synchronous (single-stage) jobs
✅ **Testability** - Dependency inversion enables easy mocking
✅ **Scalability** - Reactive programming and CQRS support
✅ **Observability** - Built-in event publishing and tracing
✅ **Reliability** - SAGA pattern for distributed transactions

For more details, see:
- [Job Lifecycle](../data-jobs/guide.md#job-lifecycle-async) - Detailed stage documentation for async jobs
- [Synchronous Jobs](../data-jobs/guide.md#quick-start-sync) - Complete guide for synchronous jobs
- [Configuration](configuration.md) - Configuration options
- [Examples](examples.md) - Real-world usage patterns

