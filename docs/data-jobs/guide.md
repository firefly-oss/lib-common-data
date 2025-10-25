# Data Jobs — Complete Guide

This is the complete, canonical guide for implementing Data Jobs with lib-common-data. It consolidates and replaces all previous Data Jobs documents (step-by-step, lifecycle, sync jobs, multiple jobs example, SAGA integration, etc.).

If you need orchestrated workflows (async or quick sync operations) in your core-data microservice, this guide has everything you need.

---

## What Are Data Jobs?

Data Jobs are standardized, orchestrated workflows for data processing in core-data services. They cover two execution models:

- Asynchronous jobs (multi-stage, long-running): START → CHECK → COLLECT → RESULT → STOP
- Synchronous jobs (single-stage, quick): EXECUTE (returns immediately)

Typical use cases:
- ETL and batch processing
- Large dataset processing with external systems (DBs, files, APIs)
- Coordinated multi-step business processes
- Quick validations, transformations, or lookups (sync)

---

## Architecture Overview

lib-common-data follows a hexagonal architecture:

- Ports (interfaces): DataJobService, SyncDataJobService, JobOrchestrator
- Adapters (implementations): your concrete job services and orchestrators
- Controllers: REST layer using DataJobController and SyncDataJobController (with abstract base classes)
- Cross-cutting: observability (tracing/metrics/logging), resiliency (retry/circuit breaker/rate limit), persistence (audit + execution results), EDA events

Key classes you will use:
- com.firefly.common.data.service.AbstractResilientDataJobService (async)
- com.firefly.common.data.service.AbstractResilientSyncDataJobService (sync)
- com.firefly.common.data.controller.AbstractDataJobController and DataJobController (async HTTP API)
- com.firefly.common.data.controller.AbstractSyncDataJobController and SyncDataJobController (sync HTTP API)
- com.firefly.common.data.config.JobOrchestrationProperties (central configuration)
- com.firefly.common.data.orchestration.port.JobOrchestrator (orchestrator port)

Observability and resiliency are automatically woven in by the abstract services. You implement the actual business logic in protected doXxx methods.

---

## Job Lifecycle (Async)

Async jobs are designed for tasks that take longer than ~30 seconds and/or require orchestration. Standard stages:

1. START — Initialize and trigger the external job or process
2. CHECK — Poll status to monitor progress
3. COLLECT — Gather intermediate or final data
4. RESULT — Retrieve the final results and optionally clean resources
5. STOP — Terminate/cancel the running job

The library enforces consistent stage semantics and supplies rich instrumentation around them (events, metrics, traces, logs, persistence of audit and results if configured).

---

## Quick Start (Async)

1) Add dependency (Maven snippet):

```xml
<dependency>
  <groupId>com.firefly</groupId>
  <artifactId>lib-common-data</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

2) Implement your async job service by extending AbstractResilientDataJobService and overriding the protected methods for each stage you need:

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

3) Expose endpoints via a controller by extending AbstractDataJobController and delegating to your service:

```java
package com.example.jobs;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Data Job - CustomerImport", description = "Customer import job endpoints")
public class CustomerImportJobController extends AbstractDataJobController {
  public CustomerImportJobController(DataJobService dataJobService) {
    super(dataJobService);
  }
}
```

That’s it. You now have standardized endpoints (see API Endpoints below).

---

## Quick Start (Sync)

Use synchronous jobs for quick, single-step operations (< ~30 seconds).

1) Implement your sync job service by extending AbstractResilientSyncDataJobService and overriding doExecute:

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

2) Expose endpoint via a sync controller by extending AbstractSyncDataJobController:

```java
package com.example.jobs;

import com.firefly.common.data.controller.AbstractSyncDataJobController;
import com.firefly.common.data.service.SyncDataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Sync Data Job - CustomerValidation", description = "Customer validation endpoints")
public class CustomerValidationJobController extends AbstractSyncDataJobController {
  public CustomerValidationJobController(SyncDataJobService syncDataJobService) {
    super(syncDataJobService);
  }
}
```

---

## API Endpoints

Async (DataJobController — inherited by AbstractDataJobController):
- POST /api/v1/jobs/start — Start a job
- GET /api/v1/jobs/{executionId}/check — Check job status
- GET /api/v1/jobs/{executionId}/collect — Collect results
- GET /api/v1/jobs/{executionId}/result — Get final result
- POST /api/v1/jobs/{executionId}/stop — Stop a running job

Sync (SyncDataJobController — inherited by AbstractSyncDataJobController):
- POST {base-path}/execute — Execute and return immediately
  Notes:
  - base-path is defined by your concrete controller's @RequestMapping (e.g., /api/v1/customer-validation → POST /api/v1/customer-validation/execute)
  - The library does not auto-register sync job controllers; you must create and annotate them in your service

All endpoints are instrumented with tracing, metrics, detailed logging and error handling via the abstract base classes and services.

---

## Configuration

All job-related configuration is centralized in JobOrchestrationProperties (prefix firefly.data.orchestration). Common properties:

- firefly.data.orchestration.enabled: boolean (default true)
- firefly.data.orchestration.orchestrator-type: APACHE_AIRFLOW | AWS_STEP_FUNCTIONS | CUSTOM
- firefly.data.orchestration.default-timeout
- firefly.data.orchestration.max-retries
- firefly.data.orchestration.retry-delay
- firefly.data.orchestration.publish-job-events: boolean
- firefly.data.orchestration.job-events-topic: string
- firefly.data.orchestration.airflow.* (baseUrl, auth, dagIdPrefix, timeouts, etc.)
- firefly.data.orchestration.aws-step-functions.* (region, stateMachineArn, timeouts, etc.)
- firefly.data.orchestration.resiliency.* (circuit breaker, retry, rate limiting)
- firefly.data.orchestration.observability.* (metric prefix, tracing)
- firefly.data.orchestration.health-check.*
- firefly.data.orchestration.persistence.* (audit and results persistence)

These map directly to com.firefly.common.data.config.JobOrchestrationProperties.

Example application.yml fragment:

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      publish-job-events: true
      job-events-topic: customer-job-events
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:CustomerDataStateMachine
      resiliency:
        retry:
          max-attempts: 3
          backoff: 500ms
```

---

## Orchestrators

You can implement com.firefly.common.data.orchestration.port.JobOrchestrator to integrate with any workflow engine. The library ships with health indicator support (JobOrchestratorHealthIndicator) and instrumentation hooks. Choose between:
- Mock or simple in-process orchestration (dev/testing)
- AWS Step Functions
- Apache Airflow
- Custom orchestrator

---

## Multiple Jobs in One Service

It’s common to create several job services and controllers in a single microservice (e.g., ProfileImport, OrdersImport, AnalyticsReport). Create one service class per job, and one controller per job exposing the standard endpoints. This keeps responsibilities clear while reusing the shared infrastructure provided by the library.

---

## SAGA and Step Events

For workflows that require distributed transactions or step-level coordination, integrate with Firefly’s transactional engine and event-driven architecture:
- Publish and consume job step events via the built-in JobEventPublisher
- Use transactional patterns (SAGA) to coordinate across services
- Audit trail and execution result persistence are supported via JobAuditService and JobExecutionResultService

---

## Observability and Resiliency

AbstractResilientDataJobService and AbstractResilientSyncDataJobService provide:
- Distributed tracing via Micrometer/Tracing
- Metrics per operation and stage
- Structured logging for each stage and request/response
- Resiliency decorators: circuit breaker, retry, rate limiting, bulkhead, and timeout

You only focus on business logic. The framework wraps your logic with standardized behaviors.

---

## Testing

Recommended strategies:
- Unit test your service implementations by invoking doXxx methods via public API methods and asserting JobStageResponse
- Mock orchestrator and external dependencies
- Use reactor-test for reactive flows
- Controller tests can use WebTestClient to call endpoints and validate responses, statuses, and payloads

See the test sources in this repository for working examples:
- src/test/java/com/firefly/common/data/controller/AbstractDataJobControllerTest.java
- src/test/java/com/firefly/common/data/controller/AbstractSyncDataJobControllerTest.java
- src/test/java/com/firefly/common/data/service/AbstractResilientSyncDataJobServiceTest.java

---

## Troubleshooting

- No beans of DataJobService/SyncDataJobService type found: ensure your concrete @Service implementations are picked up by component scanning
- Endpoints not exposed: ensure your controllers are concrete classes, annotated with @RestController, and your component scan covers their package
- Orchestrator timeouts: adjust firefly.data.orchestration.* timeouts; verify orchestrator connectivity and credentials
- Missing metrics or traces: verify Micrometer/Tracing configuration and dependencies

---

## FAQ

- Do I need to create controllers? Yes, for Data Jobs you should create REST controllers in your microservice. The library provides DataJobController/SyncDataJobController interfaces and abstract base classes to drastically reduce boilerplate. Unlike Data Enrichers, Data Job controllers are not auto-registered because they are domain-specific.
- Can I run without an external orchestrator? For sync jobs, yes. For async jobs, you typically integrate with an orchestrator (AWS Step Functions, Airflow). A mock or simplified orchestrator can be used for development.
- Where do results and audit data go? If you enable persistence in JobOrchestrationProperties, the library’s persistence services will store audit trail and execution results.