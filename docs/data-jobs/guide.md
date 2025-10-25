# Data Jobs — Complete Guide

> **Complete guide for building data processing jobs with lib-common-data**
>
> **Time**: 1-2 hours | **Prerequisites**: Java 21+, Maven 3.8+, Spring Boot 3.x, WebFlux

This is the complete, canonical guide for implementing Data Jobs with lib-common-data. It consolidates and replaces all previous Data Jobs documents (step-by-step, lifecycle, sync jobs, multiple jobs example, SAGA integration, etc.).

If you need orchestrated workflows (async or quick sync operations) in your core-data microservice, this guide has everything you need.

---

## Table of Contents

### Getting Started
1. [What Are Data Jobs?](#what-are-data-jobs)
2. [Why Do They Exist?](#why-do-they-exist)
3. [Architecture Overview](#architecture-overview)
4. [Quick Start (Async)](#quick-start-async)
5. [Quick Start (Sync)](#quick-start-sync)

### Core Concepts
6. [Job Lifecycle (Async)](#job-lifecycle-async)
7. [API Endpoints](#api-endpoints)
8. [Orchestrators](#orchestrators)

### Implementation
9. [Multiple Jobs in One Service](#multiple-jobs-in-one-service)
10. [SAGA and Step Events](#saga-and-step-events)
11. [Configuration](#configuration)

### Production Readiness
12. [Observability and Resiliency](#observability-and-resiliency)
13. [Testing](#testing)
14. [Troubleshooting](#troubleshooting)
15. [FAQ](#faq)

---

## What Are Data Jobs?

### The Concept

**Data Jobs** are standardized, orchestrated workflows for data processing in core-data services. They implement the **Template Method Pattern** to provide a consistent framework for both synchronous and asynchronous data processing operations.

### Two Execution Models

#### 1. Asynchronous Jobs (Multi-Stage, Long-Running)

**Lifecycle**: START → CHECK → COLLECT → RESULT → STOP

**Use Cases**:
- ETL and batch processing (> 30 seconds)
- Large dataset processing with external systems (DBs, files, APIs)
- Coordinated multi-step business processes
- Jobs that require polling and status checking

**Example**: Import 1 million customer records from external CRM system

#### 2. Synchronous Jobs (Single-Stage, Quick)

**Lifecycle**: EXECUTE (returns immediately)

**Use Cases**:
- Quick validations (< 30 seconds)
- Simple transformations or lookups
- Real-time data enrichment
- Immediate response operations

**Example**: Validate customer data before saving

---

## Why Do They Exist?

### The Problem: Inconsistent Data Processing

Without a standardized framework, data processing jobs suffer from:

#### 1. Code Duplication

Every team implements the same cross-cutting concerns:

```java
// Team A's implementation
public class CustomerImportJob {
    public void execute() {
        // Manual tracing
        Span span = tracer.startSpan("customer-import");
        try {
            // Manual metrics
            metrics.increment("job.started");
            long start = System.currentTimeMillis();

            // Manual retry logic
            int attempts = 0;
            while (attempts < 3) {
                try {
                    // Actual business logic
                    importCustomers();
                    break;
                } catch (Exception e) {
                    attempts++;
                    Thread.sleep(1000 * attempts);
                }
            }

            // Manual metrics
            metrics.recordTime("job.duration", System.currentTimeMillis() - start);
        } finally {
            span.end();
        }
    }
}

// Team B's implementation (different approach!)
public class OrderImportJob {
    public void execute() {
        // Different tracing approach
        // Different metrics approach
        // Different retry logic
        // Same problems, different code
    }
}
```

**Problems**:
- ❌ Every team reinvents the wheel
- ❌ Inconsistent observability across jobs
- ❌ Different error handling strategies
- ❌ Hard to maintain and evolve

#### 2. Missing Cross-Cutting Concerns

Teams often forget important features:

- ❌ No distributed tracing
- ❌ No circuit breaker
- ❌ No audit trail
- ❌ No standardized error handling
- ❌ No event publishing

#### 3. No Standardization

Different jobs have different APIs:

```java
// Job A
POST /import-customers
{ "file": "customers.csv" }

// Job B
POST /process-orders
{ "orderId": "123" }

// Job C
POST /run-analytics
{ "reportType": "sales" }
```

**Problems**:
- ❌ Every job has a different API
- ❌ Clients need to know each job's specific interface
- ❌ No common patterns for status checking, cancellation, etc.

### The Solution: Template Method Pattern + Hexagonal Architecture

Data Jobs solve this using two fundamental design patterns:

#### 1. Template Method Pattern (Standardized Workflow)

The **Template Method Pattern** defines the skeleton of an algorithm in a base class, letting subclasses override specific steps without changing the algorithm's structure.

**In Data Jobs**:
- **Template**: AbstractResilientDataJobService defines the workflow
- **Steps**: You implement only the business logic (doStartJob, doCheckJob, etc.)
- **Cross-Cutting**: Framework handles tracing, metrics, retry, circuit breaker, etc.

```
Template (AbstractResilientDataJobService):

  public final Mono<JobStageResponse> startJob(request) {
      // 1. Publish event (framework)
      publishJobStarted(request);

      // 2. Start tracing (framework)
      Span span = startTracing("START", request);

      // 3. Apply resiliency (framework)
      return applyCircuitBreaker(
          applyRetry(
              // 4. YOUR BUSINESS LOGIC (you implement)
              doStartJob(request)
          )
      )
      // 5. Record metrics (framework)
      .doOnSuccess(response -> recordMetrics(response))
      // 6. Persist audit (framework)
      .doOnSuccess(response -> persistAudit(response))
      // 7. End tracing (framework)
      .doFinally(() -> span.end());
  }

  // You only implement this:
  protected abstract Mono<JobStageResponse> doStartJob(request);
```

**Benefits**:
- ✅ **Consistency**: All jobs follow the same pattern
- ✅ **DRY**: Cross-cutting concerns implemented once
- ✅ **Focus**: You only write business logic
- ✅ **Evolution**: Framework improvements benefit all jobs

#### 2. Hexagonal Architecture (Ports and Adapters)

The **Hexagonal Architecture** (aka Ports and Adapters) separates core business logic from external concerns.

```
┌─────────────────────────────────────────────────────────────┐
│                    EXTERNAL WORLD                           │
│                                                             │
│  HTTP Clients → REST Controllers → Service Layer            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    ADAPTERS (Input)                         │
│                                                             │
│  AbstractDataJobController (REST adapter)                   │
│  - Converts HTTP requests to JobStageRequest                │
│  - Handles HTTP-specific concerns                           │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    PORTS (Interfaces)                       │
│                                                             │
│  DataJobService (port)                                      │
│  - startJob(request): Mono<Response>                        │
│  - checkJob(request): Mono<Response>                        │
│  - stopJob(request): Mono<Response>                         │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    CORE DOMAIN                              │
│                                                             │
│  AbstractResilientDataJobService (template)                 │
│  - Implements DataJobService port                           │
│  - Provides cross-cutting concerns                          │
│  - Defines template methods (doStartJob, etc.)              │
│                                                             │
│  YourJobService (concrete implementation)                   │
│  - Extends AbstractResilientDataJobService                  │
│  - Implements business logic (doStartJob, etc.)             │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    ADAPTERS (Output)                        │
│                                                             │
│  JobOrchestrator (port)                                     │
│  - startJob(): Mono<JobExecution>                           │
│  - checkJobStatus(): Mono<JobExecutionStatus>               │
│                                                             │
│  AirflowOrchestrator (adapter)                              │
│  StepFunctionsOrchestrator (adapter)                        │
│  - Implement JobOrchestrator port                           │
│  - Adapt to specific orchestrator APIs                      │
└─────────────────────────────────────────────────────────────┘
```

**Benefits**:
- ✅ **Testability**: Core logic independent of external systems
- ✅ **Flexibility**: Swap orchestrators without changing core logic
- ✅ **Maintainability**: Clear separation of concerns

---

## Architecture Overview

### Design Patterns in Action

The architecture implements **three design patterns** working together:

1. **Template Method Pattern** - Standardized workflow with customizable steps
2. **Hexagonal Architecture** - Ports and adapters for clean separation
3. **Decorator Pattern** - Cross-cutting concerns wrapped around business logic

### Key Components

#### Ports (Interfaces)

| Port | Purpose | Methods |
|------|---------|---------|
| `DataJobService` | Async job operations | `startJob()`, `checkJob()`, `collectJobResults()`, `getJobResult()`, `stopJob()` |
| `SyncDataJobService` | Sync job operations | `execute()` |
| `JobOrchestrator` | External orchestrator integration | `startJob()`, `checkJobStatus()`, `stopJob()`, `getJobExecution()` |

#### Adapters (Implementations)

**Input Adapters** (REST Controllers):
- `AbstractDataJobController` - Async job REST API
- `AbstractSyncDataJobController` - Sync job REST API

**Core Adapters** (Service Layer):
- `AbstractResilientDataJobService` - Async job template with cross-cutting concerns
- `AbstractResilientSyncDataJobService` - Sync job template with cross-cutting concerns

**Output Adapters** (External Systems):
- Your `JobOrchestrator` implementation (Airflow, Step Functions, etc.)

#### Cross-Cutting Concerns (Automatically Applied)

All jobs automatically get:
- ✅ **Distributed Tracing** - Micrometer Observation integration
- ✅ **Metrics** - Success/failure counts, execution time, error types
- ✅ **Resiliency** - Circuit breaker, retry, rate limiting, bulkhead, timeout
- ✅ **Audit Trail** - Persistence of job execution history
- ✅ **Result Persistence** - Storage of job results
- ✅ **Event Publishing** - Job lifecycle events (started, completed, failed)
- ✅ **Structured Logging** - Comprehensive logging with context

### Key Classes

| Class | Type | Purpose |
|-------|------|---------|
| `AbstractResilientDataJobService` | Abstract Base Class | Template for async jobs with all cross-cutting concerns |
| `AbstractResilientSyncDataJobService` | Abstract Base Class | Template for sync jobs with all cross-cutting concerns |
| `AbstractDataJobController` | Abstract Base Class | REST controller for async jobs with logging |
| `AbstractSyncDataJobController` | Abstract Base Class | REST controller for sync jobs with logging |
| `DataJobController` | Interface | REST API contract for async jobs |
| `SyncDataJobController` | Interface | REST API contract for sync jobs |
| `JobOrchestrationProperties` | Configuration | Central configuration (prefix: `firefly.data.orchestration`) |
| `JobOrchestrator` | Interface | Port for external orchestrators |

---

## Job Lifecycle (Async)

### Overview

Async jobs are designed for tasks that take longer than ~30 seconds and/or require orchestration with external systems. They follow a **state machine pattern** with well-defined stages.

### The Five Stages

#### 1. START — Initialize and Trigger

**Purpose**: Initialize the job and trigger execution in the external orchestrator.

**What happens**:
- Generate execution ID
- Validate input parameters
- Submit job to orchestrator (Airflow, Step Functions, etc.)
- Return execution ID to client

**Implementation**:
```java
@Override
protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    // Extract parameters
    String datasetId = request.requireParam("datasetId");

    // Submit to orchestrator
    return orchestrator.startJob(JobExecutionRequest.builder()
            .jobDefinition("customer-import")
            .parameters(Map.of("datasetId", datasetId))
            .build())
        .map(execution -> JobStageResponse.builder()
            .success(true)
            .status("STARTED")
            .executionId(execution.getExecutionId())
            .message("Customer import job started")
            .build());
}
```

**HTTP Request**:
```http
POST /api/v1/jobs/start
{
  "parameters": {
    "datasetId": "dataset-123"
  }
}
```

**HTTP Response**:
```json
{
  "success": true,
  "status": "STARTED",
  "executionId": "exec-abc-123",
  "message": "Customer import job started"
}
```

#### 2. CHECK — Poll Status

**Purpose**: Check the current status of the running job.

**What happens**:
- Query orchestrator for job status
- Return current state (RUNNING, COMPLETED, FAILED, etc.)
- Optionally return progress information

**Implementation**:
```java
@Override
protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
    String executionId = request.getExecutionId();

    return orchestrator.checkJobStatus(executionId)
        .map(status -> JobStageResponse.builder()
            .success(true)
            .status(status.getState()) // RUNNING, COMPLETED, FAILED
            .executionId(executionId)
            .message("Job is " + status.getState())
            .data(Map.of("progress", status.getProgress()))
            .build());
}
```

**HTTP Request**:
```http
GET /api/v1/jobs/exec-abc-123/check
```

**HTTP Response**:
```json
{
  "success": true,
  "status": "RUNNING",
  "executionId": "exec-abc-123",
  "message": "Job is RUNNING",
  "data": {
    "progress": 45
  }
}
```

#### 3. COLLECT — Gather Intermediate Results

**Purpose**: Collect intermediate or partial results while the job is still running.

**What happens**:
- Retrieve partial results from orchestrator or data store
- Useful for long-running jobs that produce incremental output
- Optional stage (not all jobs need this)

**Implementation**:
```java
@Override
protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
    String executionId = request.getExecutionId();

    return dataStore.getPartialResults(executionId)
        .map(partialData -> JobStageResponse.builder()
            .success(true)
            .status("COLLECTED")
            .executionId(executionId)
            .message("Collected " + partialData.size() + " records")
            .data(Map.of("records", partialData))
            .build());
}
```

**HTTP Request**:
```http
GET /api/v1/jobs/exec-abc-123/collect
```

**HTTP Response**:
```json
{
  "success": true,
  "status": "COLLECTED",
  "executionId": "exec-abc-123",
  "message": "Collected 1000 records",
  "data": {
    "records": [...]
  }
}
```

#### 4. RESULT — Retrieve Final Results

**Purpose**: Get the final results after job completion.

**What happens**:
- Retrieve final output from orchestrator or data store
- Optionally clean up temporary resources
- Return complete results to client

**Implementation**:
```java
@Override
protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
    String executionId = request.getExecutionId();

    return orchestrator.getJobExecution(executionId)
        .flatMap(execution -> {
            if (!execution.isCompleted()) {
                return Mono.just(JobStageResponse.builder()
                    .success(false)
                    .status(execution.getStatus())
                    .executionId(executionId)
                    .message("Job not yet completed")
                    .build());
            }

            return dataStore.getFinalResults(executionId)
                .map(results -> JobStageResponse.builder()
                    .success(true)
                    .status("COMPLETED")
                    .executionId(executionId)
                    .message("Import completed successfully")
                    .data(Map.of(
                        "totalRecords", results.getTotalRecords(),
                        "successCount", results.getSuccessCount(),
                        "errorCount", results.getErrorCount()
                    ))
                    .build());
        });
}
```

**HTTP Request**:
```http
GET /api/v1/jobs/exec-abc-123/result
```

**HTTP Response**:
```json
{
  "success": true,
  "status": "COMPLETED",
  "executionId": "exec-abc-123",
  "message": "Import completed successfully",
  "data": {
    "totalRecords": 10000,
    "successCount": 9950,
    "errorCount": 50
  }
}
```

#### 5. STOP — Terminate/Cancel

**Purpose**: Stop or cancel a running job.

**What happens**:
- Send cancellation request to orchestrator
- Clean up resources
- Return confirmation

**Implementation**:
```java
@Override
protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
    String executionId = request.getExecutionId();

    return orchestrator.stopJob(executionId, reason)
        .map(status -> JobStageResponse.builder()
            .success(true)
            .status("STOPPED")
            .executionId(executionId)
            .message("Job stopped: " + reason)
            .build());
}
```

**HTTP Request**:
```http
POST /api/v1/jobs/exec-abc-123/stop?reason=User%20requested%20cancellation
```

**HTTP Response**:
```json
{
  "success": true,
  "status": "STOPPED",
  "executionId": "exec-abc-123",
  "message": "Job stopped: User requested cancellation"
}
```

### State Machine Diagram

```
                    START
                      ↓
                  [STARTED]
                      ↓
              ┌───────┴───────┐
              ↓               ↓
          [RUNNING]       [FAILED]
              ↓               ↓
    ┌─────────┼─────────┐     ↓
    ↓         ↓         ↓     ↓
  CHECK   COLLECT    STOP    END
    ↓         ↓         ↓
    └─────────┴─────────┘
              ↓
         [COMPLETED]
              ↓
           RESULT
              ↓
            END
```

### Typical Client Flow

```java
// 1. Start the job
JobStageResponse startResponse = client.startJob(params);
String executionId = startResponse.getExecutionId();

// 2. Poll for completion
while (true) {
    JobStageResponse checkResponse = client.checkJob(executionId);

    if (checkResponse.getStatus().equals("COMPLETED")) {
        break;
    } else if (checkResponse.getStatus().equals("FAILED")) {
        throw new JobFailedException("Job failed");
    }

    Thread.sleep(5000); // Wait 5 seconds before next check
}

// 3. Get final results
JobStageResponse resultResponse = client.getJobResult(executionId);
processResults(resultResponse.getData());
```

### Cross-Cutting Concerns Applied to Each Stage

The library automatically applies these concerns to **every stage**:

```
Client Request
     ↓
[1. Event Publishing] → publishJobStarted()
     ↓
[2. Distributed Tracing] → startSpan("START", executionId)
     ↓
[3. Circuit Breaker] → Check if circuit is open
     ↓
[4. Retry Logic] → Retry on transient failures
     ↓
[5. Rate Limiting] → Check rate limits
     ↓
[6. Timeout] → Apply timeout
     ↓
[7. YOUR BUSINESS LOGIC] → doStartJob(request)
     ↓
[8. Metrics Recording] → recordSuccess/Failure()
     ↓
[9. Audit Persistence] → saveAuditEntry()
     ↓
[10. Result Persistence] → saveExecutionResult()
     ↓
[11. Event Publishing] → publishJobCompleted()
     ↓
[12. Tracing End] → endSpan()
     ↓
Response to Client
```

**You only implement step 7** - the framework handles everything else!

---

## Quick Start (Async)

### Step 1: Add Dependency

Add `lib-common-data` to your `pom.xml`:

```xml
<dependency>
  <groupId>com.firefly</groupId>
  <artifactId>lib-common-data</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot WebFlux (required for reactive support) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Step 2: Implement Your Job Service

Create a service that extends `AbstractResilientDataJobService` and implements the business logic for each stage:

**Key Points**:
- ✅ Extend `AbstractResilientDataJobService` (gets all cross-cutting concerns automatically)
- ✅ Inject required dependencies via constructor
- ✅ Override only the `doXxx` methods you need (Template Method Pattern)
- ✅ Return `Mono<JobStageResponse>` from each method
- ✅ Use `@Service` annotation for Spring auto-discovery

```java
package com.example.jobs;

import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CustomerImportJobService extends AbstractResilientDataJobService {

  public CustomerImportJobService(JobTracingService tracing,
                                  JobMetricsService metrics,
                                  ResiliencyDecoratorService resiliency,
                                  JobEventPublisher events,
                                  JobAuditService audit,
                                  JobExecutionResultService results) {
    super(tracing, metrics, resiliency, events, audit, results);
  }

  @Override
  protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    // Trigger orchestrator / submit job, return executionId
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .status("STARTED")
        .executionId("exec-123")
        .message("Customer import started")
        .build());
  }

  @Override
  protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
    // Poll status from orchestrator/external system
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .status("RUNNING")
        .executionId(request.getExecutionId())
        .message("Customer import in progress")
        .build());
  }

  @Override
  protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
    // Optionally collect partial/final results
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .status("COLLECTED")
        .executionId(request.getExecutionId())
        .message("Partial results collected")
        .build());
  }

  @Override
  protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
    // Return final result payload
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .status("COMPLETED")
        .executionId(request.getExecutionId())
        .message("Customer import completed")
        .build());
  }

  @Override
  protected Mono<JobStageResponse> doStopJob(JobStageRequest request) {
    // Cancel job in orchestrator/external system
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .status("STOPPED")
        .executionId(request.getExecutionId())
        .message("Job stopped by request")
        .build());
  }
}
```

### Step 3: Create REST Controller

Create a controller that extends `AbstractDataJobController` to expose REST endpoints:

**Key Points**:
- ✅ Extend `AbstractDataJobController` (gets all REST endpoints automatically)
- ✅ Inject your `DataJobService` implementation
- ✅ Add `@RestController` annotation
- ✅ Add `@RequestMapping` to define base path
- ✅ Add `@Tag` for Swagger documentation

```java
package com.example.jobs;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer-import")  // Base path for all endpoints
@Tag(name = "Data Job - CustomerImport", description = "Customer import job endpoints")
public class CustomerImportJobController extends AbstractDataJobController {

  public CustomerImportJobController(DataJobService dataJobService) {
    super(dataJobService);
  }
}
```

**Important**: The `@RequestMapping` defines the base path. The controller automatically adds these endpoints:
- `POST /api/v1/customer-import/start`
- `GET /api/v1/customer-import/{executionId}/check`
- `GET /api/v1/customer-import/{executionId}/collect`
- `GET /api/v1/customer-import/{executionId}/result`
- `POST /api/v1/customer-import/{executionId}/stop`

### Step 4: Configure (Optional)

Add configuration to `application.yml`:

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS  # or APACHE_AIRFLOW, CUSTOM
      publish-job-events: true

      # Resiliency
      resiliency:
        circuit-breaker:
          enabled: true
          failure-rate-threshold: 50
        retry:
          enabled: true
          max-attempts: 3

      # Observability
      observability:
        tracing-enabled: true
        metrics-enabled: true

      # Persistence
      persistence:
        audit-enabled: true
        result-persistence-enabled: true
```

### Step 5: Test Your Job

Start your application and test the endpoints:

```bash
# 1. Start the job
curl -X POST http://localhost:8080/api/v1/customer-import/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "datasetId": "dataset-123",
      "batchSize": 1000
    }
  }'

# Response:
# {
#   "success": true,
#   "status": "STARTED",
#   "executionId": "exec-abc-123",
#   "message": "Customer import started"
# }

# 2. Check status
curl http://localhost:8080/api/v1/customer-import/exec-abc-123/check

# 3. Get results
curl http://localhost:8080/api/v1/customer-import/exec-abc-123/result
```

That's it! You now have a fully functional async data job with:
- ✅ Standardized REST API
- ✅ Distributed tracing
- ✅ Metrics collection
- ✅ Circuit breaker and retry
- ✅ Audit trail
- ✅ Event publishing

That’s it. You now have standardized endpoints (see API Endpoints below).

---

## Quick Start (Sync)

### Overview

Use synchronous jobs for quick, single-step operations (< ~30 seconds) that return results immediately.

**When to use sync jobs**:
- ✅ Quick validations (< 30 seconds)
- ✅ Simple transformations
- ✅ Real-time lookups
- ✅ Immediate response required

**When NOT to use sync jobs**:
- ❌ Long-running operations (> 30 seconds)
- ❌ Operations requiring polling
- ❌ Multi-stage workflows
- ❌ External orchestration needed

### Step 1: Implement Your Sync Job Service

Create a service that extends `AbstractResilientSyncDataJobService` and implements the `doExecute` method:

**Key Points**:
- ✅ Extend `AbstractResilientSyncDataJobService` (gets all cross-cutting concerns automatically)
- ✅ Inject required dependencies via constructor
- ✅ Override only `doExecute` method (Template Method Pattern)
- ✅ Return `Mono<JobStageResponse>` with immediate results
- ✅ Use `@Service` annotation for Spring auto-discovery

```java
package com.example.jobs;

import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientSyncDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class CustomerValidationJobService extends AbstractResilientSyncDataJobService {

  public CustomerValidationJobService(JobTracingService tracing,
                                      JobMetricsService metrics,
                                      ResiliencyDecoratorService resiliency,
                                      JobEventPublisher events,
                                      JobAuditService audit,
                                      JobExecutionResultService results) {
    super(tracing, metrics, resiliency, events, audit, results);
  }

  @Override
  protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
    // Perform quick validation and return immediately
    String customerId = (String) request.getParameters().get("customerId");
    boolean valid = customerId != null && !customerId.isBlank();
    return Mono.just(JobStageResponse.builder()
        .success(valid)
        .executionId(request.getExecutionId())
        .message(valid ? "Valid customer" : "Invalid customer")
        .build());
  }
}
```

### Step 2: Create REST Controller

Create a controller that extends `AbstractSyncDataJobController` to expose the execute endpoint:

**Key Points**:
- ✅ Extend `AbstractSyncDataJobController` (gets execute endpoint automatically)
- ✅ Inject your `SyncDataJobService` implementation
- ✅ Add `@RestController` annotation
- ✅ Add `@RequestMapping` to define base path
- ✅ Add `@Tag` for Swagger documentation

```java
package com.example.jobs;

import com.firefly.common.data.controller.AbstractSyncDataJobController;
import com.firefly.common.data.service.SyncDataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer-validation")  // Base path
@Tag(name = "Sync Data Job - CustomerValidation", description = "Customer validation endpoints")
public class CustomerValidationJobController extends AbstractSyncDataJobController {

  public CustomerValidationJobController(SyncDataJobService syncDataJobService) {
    super(syncDataJobService);
  }
}
```

**Important**: The controller automatically adds this endpoint:
- `POST /api/v1/customer-validation/execute`

### Step 3: Test Your Sync Job

Start your application and test the endpoint:

```bash
# Execute the job (returns immediately)
curl -X POST http://localhost:8080/api/v1/customer-validation/execute \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-123"
    }
  }'

# Response (immediate):
# {
#   "success": true,
#   "executionId": "exec-xyz-789",
#   "message": "Valid customer"
# }
```

That's it! You now have a fully functional sync data job with:
- ✅ Immediate response
- ✅ Distributed tracing
- ✅ Metrics collection
- ✅ Circuit breaker and retry
- ✅ Audit trail
- ✅ Event publishing

---

## API Endpoints

### Async Job Endpoints

When you extend `AbstractDataJobController`, you automatically get these endpoints:

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `{base}/start` | Start a new job | `JobStageRequest` | `JobStageResponse` with executionId |
| `GET` | `{base}/{executionId}/check` | Check job status | None | `JobStageResponse` with current status |
| `GET` | `{base}/{executionId}/collect` | Collect intermediate results | None | `JobStageResponse` with partial data |
| `GET` | `{base}/{executionId}/result` | Get final results | None | `JobStageResponse` with complete data |
| `POST` | `{base}/{executionId}/stop` | Stop/cancel job | Query param: `reason` | `JobStageResponse` with confirmation |

**Example**: If your controller has `@RequestMapping("/api/v1/customer-import")`, the endpoints are:
- `POST /api/v1/customer-import/start`
- `GET /api/v1/customer-import/{executionId}/check`
- `GET /api/v1/customer-import/{executionId}/collect`
- `GET /api/v1/customer-import/{executionId}/result`
- `POST /api/v1/customer-import/{executionId}/stop`

### Sync Job Endpoints

When you extend `AbstractSyncDataJobController`, you automatically get this endpoint:

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `{base}/execute` | Execute job synchronously | `JobStageRequest` | `JobStageResponse` with immediate results |

**Example**: If your controller has `@RequestMapping("/api/v1/customer-validation")`, the endpoint is:
- `POST /api/v1/customer-validation/execute`

### Request/Response Models

#### JobStageRequest

```json
{
  "executionId": "exec-123",  // Optional for START, required for other stages
  "parameters": {              // Job-specific parameters
    "datasetId": "dataset-123",
    "batchSize": 1000
  }
}
```

#### JobStageResponse

```json
{
  "success": true,             // Whether the operation succeeded
  "status": "STARTED",         // Job status: STARTED, RUNNING, COMPLETED, FAILED, STOPPED
  "executionId": "exec-123",   // Unique execution identifier
  "message": "Job started",    // Human-readable message
  "data": {                    // Optional: job-specific data
    "recordsProcessed": 1000,
    "errors": []
  },
  "error": null                // Optional: error details if success=false
}
```

### Cross-Cutting Concerns

All endpoints automatically include:
- ✅ **Distributed Tracing** - Every request gets a trace span
- ✅ **Metrics** - Success/failure counts, execution time
- ✅ **Structured Logging** - Request/response logging with context
- ✅ **Error Handling** - Standardized error responses
- ✅ **OpenAPI Documentation** - Swagger UI integration

### Important Notes

**Controllers are NOT auto-registered**:
- ❌ Unlike Data Enrichers, Data Job controllers are NOT automatically discovered
- ✅ You MUST create concrete controller classes in your microservice
- ✅ You MUST annotate them with `@RestController` and `@RequestMapping`
- ✅ This is by design - jobs are domain-specific and should be explicitly defined

**Why not auto-register?**
- Data Jobs are domain-specific workflows (CustomerImport, OrderProcessing, etc.)
- Each job needs its own REST endpoint path
- Auto-registration would create naming conflicts
- Explicit controllers give you full control over API design

---

## Configuration

### Overview

All job-related configuration is centralized in `JobOrchestrationProperties` with prefix `firefly.data.orchestration`.

**Configuration Source**: `com.firefly.common.data.config.JobOrchestrationProperties`

### Core Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether job orchestration is enabled |
| `orchestrator-type` | String | `APACHE_AIRFLOW` | Orchestrator type: `APACHE_AIRFLOW`, `AWS_STEP_FUNCTIONS`, `CUSTOM` |
| `default-timeout` | Duration | `24h` | Default timeout for job executions |
| `max-retries` | int | `3` | Maximum number of retry attempts for job operations |
| `retry-delay` | Duration | `5s` | Delay between retry attempts |
| `publish-job-events` | boolean | `true` | Whether to publish job events via EDA |
| `job-events-topic` | String | `data-job-events` | Default topic for job events |

### Apache Airflow Configuration

Prefix: `firefly.data.orchestration.airflow`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `base-url` | String | - | Airflow base URL (e.g., `http://airflow.example.com:8080`) |
| `api-version` | String | `v1` | Airflow API version |
| `authentication-type` | String | `BASIC` | Authentication type: `BASIC`, `BEARER_TOKEN`, `NONE` |
| `username` | String | - | Username for basic authentication |
| `password` | String | - | Password for basic authentication |
| `bearer-token` | String | - | Bearer token for token-based authentication |
| `dag-id-prefix` | String | `data_job` | Default DAG ID prefix for data jobs |
| `connection-timeout` | Duration | `10s` | Connection timeout for Airflow API calls |
| `request-timeout` | Duration | `30s` | Request timeout for Airflow API calls |
| `verify-ssl` | boolean | `true` | Whether to verify SSL certificates |
| `max-concurrent-dag-runs` | int | `10` | Maximum number of concurrent DAG runs |

### AWS Step Functions Configuration

Prefix: `firefly.data.orchestration.aws-step-functions`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `region` | String | `us-east-1` | AWS region for Step Functions |
| `state-machine-arn` | String | - | State machine ARN or ARN prefix |
| `use-default-credentials` | boolean | `true` | Whether to use AWS SDK default credentials provider |
| `access-key-id` | String | - | AWS access key ID (if not using default credentials) |
| `secret-access-key` | String | - | AWS secret access key (if not using default credentials) |
| `session-token` | String | - | AWS session token (for temporary credentials) |
| `connection-timeout` | Duration | `10s` | Connection timeout for AWS API calls |
| `request-timeout` | Duration | `30s` | Request timeout for AWS API calls |
| `max-concurrent-executions` | int | `100` | Maximum number of concurrent executions |
| `enable-x-ray-tracing` | boolean | `false` | Whether to enable X-Ray tracing |

### Resiliency Configuration

Prefix: `firefly.data.orchestration.resiliency`

#### Circuit Breaker

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `circuit-breaker-enabled` | boolean | `true` | Whether circuit breaker is enabled |
| `circuit-breaker-failure-rate-threshold` | float | `50.0` | Failure rate threshold (percentage) |
| `circuit-breaker-slow-call-rate-threshold` | float | `100.0` | Slow call rate threshold (percentage) |
| `circuit-breaker-slow-call-duration-threshold` | Duration | `60s` | Slow call duration threshold |
| `circuit-breaker-wait-duration-in-open-state` | Duration | `60s` | Wait duration in open state |
| `circuit-breaker-permitted-number-of-calls-in-half-open-state` | int | `10` | Permitted calls in half-open state |
| `circuit-breaker-sliding-window-size` | int | `100` | Sliding window size |
| `circuit-breaker-minimum-number-of-calls` | int | `10` | Minimum number of calls before calculating failure rate |

#### Retry

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `retry-enabled` | boolean | `true` | Whether retry is enabled |
| `retry-max-attempts` | int | `3` | Maximum retry attempts |
| `retry-wait-duration` | Duration | `5s` | Wait duration between retries |

#### Rate Limiter

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `rate-limiter-enabled` | boolean | `false` | Whether rate limiter is enabled |
| `rate-limiter-limit-for-period` | int | `100` | Number of permits per period |
| `rate-limiter-limit-refresh-period` | Duration | `1s` | Period duration |
| `rate-limiter-timeout-duration` | Duration | `5s` | Timeout waiting for permit |

#### Bulkhead

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bulkhead-enabled` | boolean | `false` | Whether bulkhead is enabled |
| `bulkhead-max-concurrent-calls` | int | `25` | Maximum concurrent calls |
| `bulkhead-max-wait-duration` | Duration | `500ms` | Maximum wait duration for permit |

### Observability Configuration

Prefix: `firefly.data.orchestration.observability`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `tracing-enabled` | boolean | `true` | Whether tracing is enabled |
| `trace-job-start` | boolean | `true` | Whether to trace job start operations |
| `trace-job-check` | boolean | `true` | Whether to trace job check operations |
| `trace-job-collect` | boolean | `true` | Whether to trace job collect operations |
| `trace-job-result` | boolean | `true` | Whether to trace job result operations |
| `include-job-parameters-in-traces` | boolean | `false` | Whether to include job parameters in traces (may contain sensitive data) |
| `include-job-results-in-traces` | boolean | `false` | Whether to include job results in traces (may be large) |
| `metrics-enabled` | boolean | `true` | Whether metrics are enabled |
| `metric-prefix` | String | `firefly.data.job` | Metric name prefix |

### Health Check Configuration

Prefix: `firefly.data.orchestration.health-check`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Whether health checks are enabled |
| `timeout` | Duration | `5s` | Health check timeout |
| `interval` | Duration | `30s` | Health check interval |
| `check-connectivity` | boolean | `true` | Whether to check orchestrator connectivity |
| `show-details` | boolean | `true` | Whether to include detailed health information |

### Persistence Configuration

Prefix: `firefly.data.orchestration.persistence`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `audit-enabled` | boolean | `true` | Whether audit trail persistence is enabled |
| `include-stack-traces` | boolean | `false` | Whether to include stack traces in audit entries |
| `audit-retention-days` | int | `90` | Audit trail retention period in days (0 = keep forever) |
| `result-persistence-enabled` | boolean | `true` | Whether job execution result persistence is enabled |
| `result-retention-days` | int | `30` | Result retention period in days (0 = keep forever) |
| `enable-result-caching` | boolean | `true` | Whether to enable result caching |
| `result-cache-ttl-seconds` | long | `3600` | Result cache TTL in seconds (1 hour) |
| `max-data-size-bytes` | long | `10485760` | Maximum size of data to persist in bytes (10 MB, 0 = unlimited) |
| `persist-raw-output` | boolean | `true` | Whether to persist raw output data |
| `persist-transformed-output` | boolean | `true` | Whether to persist transformed output data |
| `persist-input-parameters` | boolean | `true` | Whether to persist input parameters |
| `sanitize-sensitive-data` | boolean | `true` | Whether to sanitize sensitive data before persisting |
| `excluded-parameter-keys` | String | `password,secret,token,apiKey` | Comma-separated list of parameter keys to exclude from persistence |

### Complete Configuration Example

```yaml
firefly:
  data:
    orchestration:
      # Core settings
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      default-timeout: 24h
      max-retries: 3
      retry-delay: 5s
      publish-job-events: true
      job-events-topic: customer-job-events

      # AWS Step Functions
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:CustomerDataStateMachine
        use-default-credentials: true
        connection-timeout: 10s
        request-timeout: 30s
        max-concurrent-executions: 100
        enable-x-ray-tracing: false

      # Resiliency
      resiliency:
        # Circuit Breaker
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        circuit-breaker-sliding-window-size: 100
        circuit-breaker-minimum-number-of-calls: 10

        # Retry
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s

        # Rate Limiter
        rate-limiter-enabled: false
        rate-limiter-limit-for-period: 100
        rate-limiter-limit-refresh-period: 1s

        # Bulkhead
        bulkhead-enabled: false
        bulkhead-max-concurrent-calls: 25
        bulkhead-max-wait-duration: 500ms

      # Observability
      observability:
        tracing-enabled: true
        trace-job-start: true
        trace-job-check: true
        trace-job-collect: true
        trace-job-result: true
        include-job-parameters-in-traces: false
        include-job-results-in-traces: false
        metrics-enabled: true
        metric-prefix: firefly.data.job

      # Health Checks
      health-check:
        enabled: true
        timeout: 5s
        interval: 30s
        check-connectivity: true
        show-details: true

      # Persistence
      persistence:
        audit-enabled: true
        include-stack-traces: false
        audit-retention-days: 90
        result-persistence-enabled: true
        result-retention-days: 30
        enable-result-caching: true
        result-cache-ttl-seconds: 3600
        max-data-size-bytes: 10485760
        persist-raw-output: true
        persist-transformed-output: true
        persist-input-parameters: true
        sanitize-sensitive-data: true
        excluded-parameter-keys: password,secret,token,apiKey
```

### Minimal Configuration Example

For development or simple use cases:

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: CUSTOM  # Use custom/mock orchestrator
      publish-job-events: false  # Disable events for dev
```

---

## Orchestrators

### What is a Job Orchestrator?

A **Job Orchestrator** is an external workflow engine that manages the execution of long-running, multi-step data processing jobs. It handles:

- **Workflow Definition**: Define job steps and dependencies
- **Execution Management**: Start, stop, and monitor job executions
- **State Management**: Track job state across steps
- **Scheduling**: Trigger jobs on schedule or events
- **Error Handling**: Retry failed steps, handle timeouts
- **Scalability**: Distribute work across multiple workers

### The JobOrchestrator Port

The library defines a **port** (interface) for orchestrators:

```java
public interface JobOrchestrator {
    Mono<JobExecution> startJob(JobExecutionRequest request);
    Mono<JobExecutionStatus> checkJobStatus(String executionId);
    Mono<JobExecutionStatus> stopJob(String executionId, String reason);
    Mono<JobExecution> getJobExecution(String executionId);
    String getOrchestratorType();
}
```

**Source**: `com.firefly.common.data.orchestration.port.JobOrchestrator`

### Supported Orchestrators

You can implement `JobOrchestrator` to integrate with any workflow engine:

#### 1. AWS Step Functions

**Best for**: Production workloads on AWS

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      orchestrator-type: AWS_STEP_FUNCTIONS
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:MyStateMachine
```

#### 2. Apache Airflow

**Best for**: Complex data pipelines, on-premise deployments

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      orchestrator-type: APACHE_AIRFLOW
      airflow:
        base-url: http://airflow.example.com:8080
        authentication-type: BASIC
        username: admin
        password: ${AIRFLOW_PASSWORD}
```

#### 3. Custom Orchestrator

**Best for**: Development, testing, or integration with proprietary systems

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      orchestrator-type: CUSTOM
```

### Health Checks

The library automatically provides health checks for orchestrators via `JobOrchestratorHealthIndicator`.

**Endpoint**: `GET /actuator/health/jobOrchestrator`

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      health-check:
        enabled: true
        timeout: 5s
        check-connectivity: true
```

---

## Multiple Jobs in One Service

### Overview

It is common to create several job services and controllers in a single microservice. This approach:

- ✅ **Reuses Infrastructure**: All jobs share the same cross-cutting concerns
- ✅ **Clear Responsibilities**: Each job has its own service and controller
- ✅ **Simplified Deployment**: One microservice, multiple jobs
- ✅ **Consistent Patterns**: All jobs follow the same structure

### Example Structure

```
customer-data-service/
├── src/main/java/com/example/customer/
│   ├── jobs/
│   │   ├── import/
│   │   │   ├── CustomerImportJobService.java
│   │   │   └── CustomerImportJobController.java
│   │   ├── validation/
│   │   │   ├── CustomerValidationJobService.java
│   │   │   └── CustomerValidationJobController.java
│   │   └── export/
│   │       ├── CustomerExportJobService.java
│   │       └── CustomerExportJobController.java
│   └── CustomerDataServiceApplication.java
└── application.yml
```

### Implementation Example

#### Job 1: Customer Import (Async)

```java
@Service
public class CustomerImportJobService extends AbstractResilientDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        String datasetId = request.requireParam("datasetId");
        // Start import from external CRM system
        return orchestrator.startJob(JobExecutionRequest.builder()
                .jobDefinition("customer-import")
                .parameters(Map.of("datasetId", datasetId))
                .build())
            .map(execution -> JobStageResponse.builder()
                .success(true)
                .status("STARTED")
                .executionId(execution.getExecutionId())
                .build());
    }
}

@RestController
@RequestMapping("/api/v1/customer-import")
public class CustomerImportJobController extends AbstractDataJobController {
    public CustomerImportJobController(CustomerImportJobService service) {
        super(service);
    }
}
```

**Endpoints**: `POST /api/v1/customer-import/start`, `GET /api/v1/customer-import/{executionId}/check`

#### Job 2: Customer Validation (Sync)

```java
@Service
public class CustomerValidationJobService extends AbstractResilientSyncDataJobService {
    
    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        String customerId = request.requireParam("customerId");
        boolean valid = validateCustomer(customerId);
        return Mono.just(JobStageResponse.builder()
            .success(valid)
            .message(valid ? "Valid customer" : "Invalid customer")
            .build());
    }
}

@RestController
@RequestMapping("/api/v1/customer-validation")
public class CustomerValidationJobController extends AbstractSyncDataJobController {
    public CustomerValidationJobController(CustomerValidationJobService service) {
        super(service);
    }
}
```

**Endpoints**: `POST /api/v1/customer-validation/execute`

### Benefits

All jobs automatically get:
- ✅ Distributed tracing
- ✅ Metrics collection  
- ✅ Circuit breaker and retry
- ✅ Audit trail
- ✅ Event publishing
- ✅ Standardized error handling

**No code duplication** - all cross-cutting concerns are handled by the framework!


---

## SAGA and Step Events

### Overview

For workflows that require distributed transactions or step-level coordination, integrate with Firefly's transactional engine and event-driven architecture.

### Event Publishing

The framework automatically publishes events for job lifecycle:

```java
// Events are automatically published by AbstractResilientDataJobService
// You don't need to write this code - it's built-in!

// Job Started Event
JobEvent.builder()
    .eventType("JOB_STARTED")
    .executionId(executionId)
    .jobType("CustomerImport")
    .timestamp(Instant.now())
    .build();

// Job Completed Event
JobEvent.builder()
    .eventType("JOB_COMPLETED")
    .executionId(executionId)
    .jobType("CustomerImport")
    .success(true)
    .timestamp(Instant.now())
    .build();

// Job Failed Event
JobEvent.builder()
    .eventType("JOB_FAILED")
    .executionId(executionId)
    .jobType("CustomerImport")
    .success(false)
    .error(errorMessage)
    .timestamp(Instant.now())
    .build();
```

### SAGA Pattern

Use job events to coordinate distributed transactions:

```java
@Service
public class OrderProcessingSaga {
    
    @EventListener
    public void onCustomerImportCompleted(JobCompletedEvent event) {
        if (event.getJobType().equals("CustomerImport")) {
            // Trigger next step in saga
            orderImportService.startJob(
                JobStageRequest.builder()
                    .parameters(Map.of("customerId", event.getExecutionId()))
                    .build()
            );
        }
    }
    
    @EventListener
    public void onOrderImportFailed(JobFailedEvent event) {
        if (event.getJobType().equals("OrderImport")) {
            // Compensate: rollback customer import
            customerImportService.compensate(event.getExecutionId());
        }
    }
}
```

### Audit Trail and Results

The framework automatically persists:

**Audit Trail** (via `JobAuditService`):
- Job start/stop times
- Input parameters
- Success/failure status
- Error messages and stack traces (optional)
- Retention: 90 days (configurable)

**Execution Results** (via `JobExecutionResultService`):
- Raw output data
- Transformed output data
- Result caching (1 hour TTL, configurable)
- Retention: 30 days (configurable)

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      persistence:
        audit-enabled: true
        audit-retention-days: 90
        result-persistence-enabled: true
        result-retention-days: 30
        enable-result-caching: true
        result-cache-ttl-seconds: 3600
```


---

## Observability and Resiliency

### The Decorator Pattern in Action

The framework uses the **Decorator Pattern** to wrap your business logic with cross-cutting concerns:

```
Your Business Logic (doStartJob)
         ↓
[Decorator 1: Event Publishing]
         ↓
[Decorator 2: Distributed Tracing]
         ↓
[Decorator 3: Circuit Breaker]
         ↓
[Decorator 4: Retry Logic]
         ↓
[Decorator 5: Rate Limiting]
         ↓
[Decorator 6: Timeout]
         ↓
[Decorator 7: Metrics Recording]
         ↓
[Decorator 8: Audit Persistence]
         ↓
[Decorator 9: Result Persistence]
         ↓
Response
```

### What You Get Automatically

#### 1. Distributed Tracing

**Micrometer Observation** integration provides:
- ✅ Trace ID and Span ID for every request
- ✅ Parent-child span relationships
- ✅ Trace context propagation across services
- ✅ Integration with Zipkin, Jaeger, etc.

**Example Trace**:
```
Trace ID: abc123
├─ Span: POST /api/v1/customer-import/start (200ms)
│  ├─ Span: startJob (180ms)
│  │  ├─ Span: publishJobStarted (5ms)
│  │  ├─ Span: doStartJob (150ms)  ← Your business logic
│  │  ├─ Span: persistAudit (10ms)
│  │  └─ Span: publishJobCompleted (5ms)
```

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      observability:
        tracing-enabled: true
        trace-job-start: true
        include-job-parameters-in-traces: false  # Security: don't log sensitive data
```

#### 2. Metrics

**Micrometer Metrics** provides:
- ✅ Success/failure counts per job type
- ✅ Execution time histograms
- ✅ Error type distribution
- ✅ Circuit breaker state
- ✅ Retry attempts

**Example Metrics**:
```
firefly.data.job.start.count{job_type="CustomerImport",status="success"} = 1000
firefly.data.job.start.count{job_type="CustomerImport",status="failure"} = 10
firefly.data.job.start.duration{job_type="CustomerImport",quantile="0.95"} = 2.5s
firefly.data.job.circuit_breaker.state{job_type="CustomerImport"} = CLOSED
```

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      observability:
        metrics-enabled: true
        metric-prefix: firefly.data.job
```

#### 3. Structured Logging

**SLF4J** with structured context:
- ✅ Request/response logging
- ✅ Execution ID in every log
- ✅ Job type and stage
- ✅ Error details with context

**Example Logs**:
```
INFO  [executionId=exec-123, jobType=CustomerImport, stage=START] Starting job
DEBUG [executionId=exec-123, jobType=CustomerImport, stage=START] Request: {datasetId=dataset-123}
INFO  [executionId=exec-123, jobType=CustomerImport, stage=START] Job started successfully
DEBUG [executionId=exec-123, jobType=CustomerImport, stage=START] Response: {success=true, status=STARTED}
```

#### 4. Resiliency Decorators

**Resilience4j** integration provides:

##### Circuit Breaker

Prevents cascading failures:
- **Closed**: Normal operation
- **Open**: Fast-fail after threshold
- **Half-Open**: Test if service recovered

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0  # Open after 50% failures
        circuit-breaker-wait-duration-in-open-state: 60s  # Wait 60s before half-open
```

##### Retry

Automatic retry on transient failures:
- Exponential backoff
- Configurable max attempts
- Retry only on specific exceptions

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      resiliency:
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s
```

##### Rate Limiter

Prevent overwhelming external systems:
- Limit requests per time period
- Queue or reject excess requests

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      resiliency:
        rate-limiter-enabled: true
        rate-limiter-limit-for-period: 100  # 100 requests
        rate-limiter-limit-refresh-period: 1s  # per second
```

##### Bulkhead

Isolate resources:
- Limit concurrent executions
- Prevent resource exhaustion

**Configuration**:
```yaml
firefly:
  data:
    orchestration:
      resiliency:
        bulkhead-enabled: true
        bulkhead-max-concurrent-calls: 25
```

### You Only Write Business Logic

All of the above is **automatically applied** by `AbstractResilientDataJobService` and `AbstractResilientSyncDataJobService`.

**You only implement**:
```java
@Override
protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    // Your business logic here
    // No tracing, metrics, retry, circuit breaker code needed!
    return Mono.just(JobStageResponse.builder()
        .success(true)
        .build());
}
```

**The framework handles everything else!**


---

## Testing

### Testing Strategy

#### 1. Unit Tests for Service Logic

Test your business logic by calling the public methods (which internally call your `doXxx` methods):

```java
@ExtendWith(MockitoExtension.class)
class CustomerImportJobServiceTest {
    
    @Mock
    private JobOrchestrator orchestrator;
    
    @Mock
    private JobTracingService tracing;
    
    @Mock
    private JobMetricsService metrics;
    
    @Mock
    private ResiliencyDecoratorService resiliency;
    
    @Mock
    private JobEventPublisher events;
    
    @Mock
    private JobAuditService audit;
    
    @Mock
    private JobExecutionResultService results;
    
    @InjectMocks
    private CustomerImportJobService service;
    
    @Test
    void shouldStartJobSuccessfully() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .parameters(Map.of("datasetId", "dataset-123"))
            .build();
        
        JobExecution execution = JobExecution.builder()
            .executionId("exec-123")
            .status("RUNNING")
            .build();
        
        when(orchestrator.startJob(any())).thenReturn(Mono.just(execution));
        when(resiliency.decorateWithResiliency(any(), any())).thenAnswer(
            invocation -> invocation.getArgument(0)
        );
        
        // When
        JobStageResponse response = service.startJob(request).block();
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getStatus()).isEqualTo("STARTED");
        assertThat(response.getExecutionId()).isEqualTo("exec-123");
        
        verify(orchestrator).startJob(any());
        verify(events).publishJobStarted(any());
    }
    
    @Test
    void shouldHandleJobStartFailure() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .parameters(Map.of("datasetId", "dataset-123"))
            .build();
        
        when(orchestrator.startJob(any()))
            .thenReturn(Mono.error(new RuntimeException("Orchestrator unavailable")));
        when(resiliency.decorateWithResiliency(any(), any())).thenAnswer(
            invocation -> invocation.getArgument(0)
        );
        
        // When/Then
        StepVerifier.create(service.startJob(request))
            .expectError(RuntimeException.class)
            .verify();
        
        verify(events).publishJobFailed(any());
    }
}
```

#### 2. Integration Tests with WebTestClient

Test your controllers and the full request/response cycle:

```java
@WebFluxTest(CustomerImportJobController.class)
class CustomerImportJobControllerTest {
    
    @Autowired
    private WebTestClient webTestClient;
    
    @MockBean
    private CustomerImportJobService service;
    
    @Test
    void shouldStartJobViaRestEndpoint() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .parameters(Map.of("datasetId", "dataset-123"))
            .build();
        
        JobStageResponse response = JobStageResponse.builder()
            .success(true)
            .status("STARTED")
            .executionId("exec-123")
            .message("Job started")
            .build();
        
        when(service.startJob(any())).thenReturn(Mono.just(response));
        
        // When/Then
        webTestClient.post()
            .uri("/api/v1/customer-import/start")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JobStageResponse.class)
            .value(resp -> {
                assertThat(resp.isSuccess()).isTrue();
                assertThat(resp.getStatus()).isEqualTo("STARTED");
                assertThat(resp.getExecutionId()).isEqualTo("exec-123");
            });
    }
    
    @Test
    void shouldCheckJobStatus() {
        // Given
        String executionId = "exec-123";
        JobStageResponse response = JobStageResponse.builder()
            .success(true)
            .status("RUNNING")
            .executionId(executionId)
            .build();
        
        when(service.checkJob(any())).thenReturn(Mono.just(response));
        
        // When/Then
        webTestClient.get()
            .uri("/api/v1/customer-import/{executionId}/check", executionId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(JobStageResponse.class)
            .value(resp -> {
                assertThat(resp.getStatus()).isEqualTo("RUNNING");
            });
    }
}
```

#### 3. Reactive Testing with StepVerifier

Test reactive flows:

```java
@Test
void shouldHandleReactiveFlow() {
    // Given
    JobStageRequest request = JobStageRequest.builder()
        .parameters(Map.of("customerId", "CUST-123"))
        .build();
    
    // When
    Mono<JobStageResponse> result = service.execute(request);
    
    // Then
    StepVerifier.create(result)
        .assertNext(response -> {
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getMessage()).contains("Valid customer");
        })
        .verifyComplete();
}
```

### Test Examples in Repository

See the test sources in this repository for working examples:
- `src/test/java/com/firefly/common/data/controller/AbstractDataJobControllerTest.java`
- `src/test/java/com/firefly/common/data/controller/AbstractSyncDataJobControllerTest.java`
- `src/test/java/com/firefly/common/data/service/AbstractResilientSyncDataJobServiceTest.java`

### Testing Best Practices

1. **Mock External Dependencies**: Always mock `JobOrchestrator`, databases, external APIs
2. **Test Business Logic**: Focus on testing your `doXxx` methods
3. **Test Error Handling**: Verify behavior when orchestrator fails, timeouts occur, etc.
4. **Test Reactive Flows**: Use `StepVerifier` for reactive testing
5. **Integration Tests**: Test full HTTP request/response cycle with `WebTestClient`
6. **Don't Test Framework Code**: The framework's cross-cutting concerns are already tested


---

## Troubleshooting

### Common Issues and Solutions

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| **No beans of DataJobService/SyncDataJobService type found** | Service not picked up by component scanning | Ensure your `@Service` class is in a package scanned by Spring. Add `@ComponentScan` if needed. |
| **Endpoints not exposed** | Controller not registered | Ensure controller is annotated with `@RestController` and `@RequestMapping`. Verify component scan covers the package. |
| **404 Not Found on endpoints** | Wrong base path | Check `@RequestMapping` value on controller. Verify application context path. |
| **Orchestrator timeouts** | Orchestrator slow or unreachable | Adjust `firefly.data.orchestration.default-timeout`. Verify orchestrator connectivity and credentials. |
| **Circuit breaker always open** | Too many failures | Check `circuit-breaker-failure-rate-threshold`. Verify orchestrator health. Review error logs. |
| **Missing metrics** | Micrometer not configured | Add `spring-boot-starter-actuator` dependency. Enable metrics in `application.yml`. |
| **Missing traces** | Tracing not configured | Add Micrometer Tracing dependencies. Configure trace exporter (Zipkin, Jaeger). |
| **Job events not published** | Events disabled | Set `firefly.data.orchestration.publish-job-events: true`. Verify event publisher bean exists. |
| **Audit data not persisted** | Persistence disabled | Set `firefly.data.orchestration.persistence.audit-enabled: true`. Verify database configuration. |
| **Results not cached** | Caching disabled | Set `firefly.data.orchestration.persistence.enable-result-caching: true`. |
| **Retry not working** | Retry disabled | Set `firefly.data.orchestration.resiliency.retry-enabled: true`. |
| **Rate limiter rejecting requests** | Too many requests | Increase `rate-limiter-limit-for-period` or `rate-limiter-limit-refresh-period`. |

### Debugging Tips

#### 1. Enable Debug Logging

```yaml
logging:
  level:
    com.firefly.common.data: DEBUG
    com.firefly.common.data.service: TRACE
    com.firefly.common.data.controller: DEBUG
```

#### 2. Check Actuator Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Job orchestrator health
curl http://localhost:8080/actuator/health/jobOrchestrator

# Metrics
curl http://localhost:8080/actuator/metrics/firefly.data.job.start.count

# Circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers
```

#### 3. Verify Configuration

```bash
# View all configuration properties
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.jobOrchestrationProperties'
```

#### 4. Check Component Scanning

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.firefly.common.data",  // Required for lib-common-data
    "com.example.customer"       // Your application packages
})
public class CustomerDataServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerDataServiceApplication.class, args);
    }
}
```

#### 5. Verify Bean Registration

```java
@SpringBootApplication
public class CustomerDataServiceApplication implements CommandLineRunner {
    
    @Autowired
    private ApplicationContext context;
    
    @Override
    public void run(String... args) {
        // List all DataJobService beans
        String[] beans = context.getBeanNamesForType(DataJobService.class);
        System.out.println("DataJobService beans: " + Arrays.toString(beans));
        
        // List all controllers
        String[] controllers = context.getBeanNamesForAnnotation(RestController.class);
        System.out.println("Controllers: " + Arrays.toString(controllers));
    }
}
```

### Getting Help

If you encounter issues not covered here:

1. **Check Logs**: Enable DEBUG logging for `com.firefly.common.data`
2. **Review Configuration**: Verify all required properties are set
3. **Check Dependencies**: Ensure all required dependencies are in `pom.xml`
4. **Test Isolation**: Create a minimal test case to isolate the issue
5. **Consult Documentation**: Review this guide and the source code


---

## FAQ

### General Questions

**Q: Do I need to create controllers for Data Jobs?**

A: Yes. Unlike Data Enrichers (which have auto-registered global controllers), Data Job controllers are NOT auto-registered. You must create concrete `@RestController` classes in your microservice that extend `AbstractDataJobController` or `AbstractSyncDataJobController`. This is by design because jobs are domain-specific and each needs its own REST endpoint path.

**Q: Why aren't Data Job controllers auto-registered like Data Enrichers?**

A: Data Jobs are domain-specific workflows (CustomerImport, OrderProcessing, etc.) that need explicit REST endpoint paths. Auto-registration would create naming conflicts and reduce flexibility. Explicit controllers give you full control over API design.

**Q: Can I run Data Jobs without an external orchestrator?**

A: For sync jobs, yes - they execute immediately and don't need an orchestrator. For async jobs, you typically integrate with an orchestrator (AWS Step Functions, Airflow). For development/testing, you can create a simple custom orchestrator or mock implementation.

**Q: Where do audit data and results go?**

A: If you enable persistence in `JobOrchestrationProperties`, the library's `JobAuditService` and `JobExecutionResultService` will store audit trail and execution results. You need to configure the underlying data store (database, cache, etc.).

**Q: How do I choose between async and sync jobs?**

A: Use **async jobs** for:
- Long-running operations (> 30 seconds)
- Multi-step workflows
- Operations requiring polling
- External orchestration

Use **sync jobs** for:
- Quick operations (< 30 seconds)
- Immediate response required
- Simple validations or transformations
- Real-time processing

### Configuration Questions

**Q: What's the minimum configuration needed?**

A: For development:
```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: CUSTOM
      publish-job-events: false
```

**Q: How do I configure multiple orchestrators?**

A: Set `orchestrator-type` to the primary orchestrator. For multiple orchestrators, create separate `JobOrchestrator` beans with `@ConditionalOnProperty` and route jobs based on job type.

**Q: How do I disable specific resiliency features?**

A: Set the corresponding `enabled` property to `false`:
```yaml
firefly:
  data:
    orchestration:
      resiliency:
        circuit-breaker-enabled: false
        retry-enabled: false
        rate-limiter-enabled: false
        bulkhead-enabled: false
```

### Implementation Questions

**Q: Can I override the cross-cutting concerns?**

A: The cross-cutting concerns are applied by `AbstractResilientDataJobService`. If you need custom behavior, you can:
1. Extend `AbstractResilientDataJobService` and override specific methods
2. Implement `DataJobService` directly (not recommended - you lose all framework benefits)

**Q: How do I add custom metrics?**

A: Inject `JobMetricsService` and record custom metrics in your `doXxx` methods:
```java
@Override
protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    return Mono.defer(() -> {
        metrics.recordCustomMetric("custom.metric", 1.0);
        // Your logic here
    });
}
```

**Q: How do I add custom tracing spans?**

A: Inject `JobTracingService` and create custom spans:
```java
@Override
protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    return tracing.traceOperation("custom-operation", () -> {
        // Your logic here
    });
}
```

**Q: Can I use Data Jobs with Spring MVC (non-reactive)?**

A: No. Data Jobs are built on Spring WebFlux and require reactive programming with `Mono`/`Flux`. If you need blocking/synchronous behavior, use sync jobs but still return `Mono`.

### Troubleshooting Questions

**Q: Why are my endpoints returning 404?**

A: Check:
1. Controller has `@RestController` and `@RequestMapping`
2. Controller package is scanned by Spring
3. Application context path is correct
4. Endpoint path matches your `@RequestMapping` + method path

**Q: Why is the circuit breaker always open?**

A: Check:
1. `circuit-breaker-failure-rate-threshold` (default 50%)
2. Recent error rate in logs
3. Orchestrator health
4. `circuit-breaker-minimum-number-of-calls` (need enough calls to calculate rate)

**Q: Why aren't events being published?**

A: Check:
1. `publish-job-events: true` in configuration
2. `JobEventPublisher` bean exists
3. Event listener is registered
4. Event topic is configured correctly

### Performance Questions

**Q: How many concurrent jobs can I run?**

A: Depends on:
1. `bulkhead-max-concurrent-calls` (default 25)
2. Orchestrator limits (e.g., AWS Step Functions: 100 concurrent executions)
3. Your infrastructure capacity

**Q: How do I optimize for high throughput?**

A: 
1. Enable bulkhead: `bulkhead-enabled: true`
2. Increase concurrent calls: `bulkhead-max-concurrent-calls: 100`
3. Enable rate limiter: `rate-limiter-enabled: true`
4. Tune circuit breaker thresholds
5. Use result caching: `enable-result-caching: true`

**Q: How do I reduce latency?**

A:
1. Disable unnecessary features (e.g., audit if not needed)
2. Reduce retry attempts: `retry-max-attempts: 1`
3. Optimize orchestrator timeouts
4. Use sync jobs for quick operations

### Migration Questions

**Q: How do I migrate from custom job implementation to lib-common-data?**

A:
1. Create service extending `AbstractResilientDataJobService`
2. Move business logic to `doXxx` methods
3. Create controller extending `AbstractDataJobController`
4. Remove custom tracing, metrics, retry code
5. Configure `JobOrchestrationProperties`
6. Test thoroughly

**Q: Can I gradually migrate jobs?**

A: Yes. You can run both old and new implementations side-by-side. Migrate one job at a time, test, then move to the next.

