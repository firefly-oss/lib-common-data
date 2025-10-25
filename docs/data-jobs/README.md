# Data Jobs Documentation (Internal Index)

This folder contains the canonical documentation for Data Jobs in lib-common-data.

- Canonical guide: [guide.md](guide.md)
- This README exists to help you quickly find the right entry point and understand scope based on the actual code in this repository.

## What are Data Jobs?
Data Jobs are orchestrated workflows for data processing in core-data services. The library supports two execution models, both implemented in this repository:
- Asynchronous jobs (multi-stage): START → CHECK → COLLECT → RESULT → STOP
- Synchronous jobs (single-stage): EXECUTE (returns immediately)

See Architecture and API details in the guide: [guide.md](guide.md)

## What the Code Actually Provides
The documentation reflects the real implementation in the codebase. Key components and packages:

- Services
  - Async: `com.firefly.common.data.service.AbstractResilientDataJobService`
  - Sync: `com.firefly.common.data.service.AbstractResilientSyncDataJobService`

- Controllers (REST contracts and base classes)
  - Async: `com.firefly.common.data.controller.DataJobController` (interface)
  - Async base impl: `com.firefly.common.data.controller.AbstractDataJobController`
  - Sync: `com.firefly.common.data.controller.SyncDataJobController` (interface)
  - Sync base impl: `com.firefly.common.data.controller.AbstractSyncDataJobController`

- Configuration
  - `com.firefly.common.data.config.JobOrchestrationProperties` (prefix: `firefly.data.orchestration`)

These are referenced throughout the guide and in tests under `src/test/java/com/firefly/common/data/...`.

## Endpoints (As Implemented)

- Async (provided via controllers you create extending AbstractDataJobController):
  - POST `/api/v1/jobs/start`
  - GET `/api/v1/jobs/{executionId}/check`
  - GET `/api/v1/jobs/{executionId}/collect`
  - GET `/api/v1/jobs/{executionId}/result`
  - POST `/api/v1/jobs/{executionId}/stop`

- Sync (provided via controllers you create extending AbstractSyncDataJobController):
  - POST `{base-path}/execute`
    - base-path is defined by your controller’s `@RequestMapping`, e.g., `/api/v1/customer-validation` → POST `/api/v1/customer-validation/execute`

Note: Unlike Data Enrichers, Data Job controllers are NOT auto-registered. You must create concrete `@RestController` classes in your service that extend the abstract controllers.

## Configuration (Matches Code)
Centralized under `firefly.data.orchestration.*` → `JobOrchestrationProperties`:
- `enabled` (boolean)
- `orchestrator-type` (APACHE_AIRFLOW | AWS_STEP_FUNCTIONS | CUSTOM)
- `default-timeout`
- `max-retries`, `retry-delay`
- `publish-job-events`, `job-events-topic`
- `airflow.*`, `aws-step-functions.*`
- `resiliency.*`, `observability.*`, `health-check.*`, `persistence.*`

See examples in the guide and in docs/common/configuration.md.

## Migration Notice
This guide replaces older fragmented docs. The following legacy files have been removed and consolidated into [guide.md](guide.md):
- step-by-step-guide.md
- sync-jobs.md
- multiple-jobs-example.md
- job-lifecycle.md
- saga-integration.md
- README.md (old index)

If you encounter references to those files, update links to point to this folder’s [guide.md](guide.md) instead.

## Quick Links
- Complete Guide: [guide.md](guide.md)
- API Reference: ../common/api-reference.md
- Architecture Overview: ../common/architecture.md
- Getting Started: ../common/getting-started.md
- Observability: ../common/observability.md
- Resiliency: ../common/resiliency.md
- Logging: ../common/logging.md
- Examples: ../common/examples.md

## Verification
- The endpoint descriptions and controller responsibilities here align with:
  - `AbstractDataJobController` and `DataJobController` for async
  - `AbstractSyncDataJobController` and `SyncDataJobController` for sync
- The sync endpoint path uses `{base-path}/execute`, matching the interface and base controller.

If something drifts, please open an issue and reference the class names above to keep docs exact. Thanks!