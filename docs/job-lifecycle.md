# Job Lifecycle

Comprehensive guide to the standardized job lifecycle in `lib-common-data`.

## Table of Contents

- [Overview](#overview)
- [Job Stages](#job-stages)
- [Data Flow](#data-flow)
- [Stage Details](#stage-details)
- [Best Practices](#best-practices)
- [Error Handling](#error-handling)
- [Examples](#examples)

---

## Overview

The `lib-common-data` library defines a standardized **4-stage lifecycle** for all data processing jobs:

```
START → CHECK → COLLECT → RESULT
```

### Why This Pattern?

✅ **Consistency** - All data services follow the same pattern  
✅ **Separation of Concerns** - Raw data vs. transformed data  
✅ **Flexibility** - Clients can choose raw or mapped results  
✅ **Observability** - Clear stages for monitoring  
✅ **Testability** - Each stage can be tested independently  

---

## Job Stages

### Stage Enum

```java
public enum JobStage {
    START,    // Initialize and trigger the job
    CHECK,    // Monitor job progress and status
    COLLECT,  // Gather raw results (no transformation)
    RESULT,   // Transform and return final results
    ALL       // Used for services handling all stages
}
```

### Stage Characteristics

| Stage | Purpose | Input | Output | Idempotent | Transformation |
|-------|---------|-------|--------|------------|----------------|
| **START** | Initiate job | Parameters | Execution ID | ✅ Yes | None |
| **CHECK** | Monitor progress | Execution ID | Status, Progress | ✅ Yes | None |
| **COLLECT** | Get raw data | Execution ID | Raw Map | ✅ Yes | None |
| **RESULT** | Get final data | Execution ID + DTO class | Mapped DTO | ✅ Yes | MapStruct |

---

## Data Flow

### Complete Lifecycle Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ Client                                                          │
└────┬──────────────────────────────────────────────────────────┬─┘
     │                                                          │
     │ 1. START                                                 │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ POST /api/v1/jobs/start                                     │ │
│ {                                                           │ │
│   "parameters": {                                           │ │
│     "customerId": "12345"                                   │ │
│   },                                                        │ │
│   "requestId": "req-001"                                    │ │
│ }                                                           │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ DataJobService.startJob()                                   │ │
│   ├─> Validate parameters                                   │ │
│   ├─> JobOrchestrator.startJob()                            │ │
│   │     └─> AWS Step Functions / Azure / etc.               │ │
│   └─> Publish "job.started" event                           │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ Response:                                                   │ │
│ {                                                           │ │
│   "stage": "START",                                         │ │
│   "executionId": "exec-abc123",                             │ │
│   "status": "RUNNING",                                      │ │
│   "success": true,                                          │ │
│   "message": "Job started successfully"                     │ │
│ }                                                           │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     │ 2. CHECK (poll until complete)                           │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ GET /api/v1/jobs/exec-abc123/check                          │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ DataJobService.checkJob()                                   │ │
│   └─> JobOrchestrator.checkJobStatus()                      │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ Response:                                                   │ │
│ {                                                           │ │
│   "stage": "CHECK",                                         │ │
│   "executionId": "exec-abc123",                             │ │
│   "status": "SUCCEEDED",                                    │ │
│   "progressPercentage": 100,                                │ │
│   "success": true                                           │ │
│ }                                                           │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     │ 3. COLLECT (get raw data)                                │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ GET /api/v1/jobs/exec-abc123/collect                        │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ DataJobService.collectJobResults()                          │ │
│   └─> JobOrchestrator.getJobExecution()                     │ │
│         └─> execution.getOutput() // RAW DATA               │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ Response:                                                   │ │
│ {                                                           │ │
│   "stage": "COLLECT",                                       │ │
│   "executionId": "exec-abc123",                             │ │
│   "status": "SUCCEEDED",                                    │ │
│   "data": {                                                 │ │
│     "customer_id": "12345",                                 │ │
│     "first_name": "John",                                   │ │
│     "last_name": "Doe",                                     │ │
│     "email_address": "john@example.com"                     │ │
│   },                                                        │ │
│   "success": true                                           │ │
│ }                                                           │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     │ 4. RESULT (get mapped data)                              │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ GET /api/v1/jobs/exec-abc123/result                         │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ DataJobService.getJobResult()                               │ │
│   ├─> 1. collectJobResults() // Get raw data                │ │
│   ├─> 2. mapperRegistry.getMapper(targetClass)              │ │
│   ├─> 3. mapper.mapToTarget(rawData) // MapStruct           │ │
│   └─> 4. Return mapped DTO                                  │ │
└────┬────────────────────────────────────────────────────────┘ │
     │                                                          │
     ▼                                                          │
┌─────────────────────────────────────────────────────────────┐ │
│ Response:                                                   │ │
│ {                                                           │ │
│   "stage": "RESULT",                                        │ │
│   "executionId": "exec-abc123",                             │ │
│   "status": "SUCCEEDED",                                    │ │
│   "data": {                                                 │ │
│     "result": {                                             │ │
│       "customerId": "12345",                                │ │
│       "firstName": "John",                                  │ │
│       "lastName": "Doe",                                    │ │
│       "email": "john@example.com"                           │ │
│     }                                                       │ │
│   },                                                        │ │
│   "success": true                                           │ │
│ }                                                           │ │
└─────────────────────────────────────────────────────────────┘ │
     │                                                          │
     └──────────────────────────────────────────────────────────┘
```

---

## Stage Details

### 1. START Stage

**Purpose:** Initialize and trigger a data processing job

**HTTP Request:**
```bash
curl -X POST http://localhost:8080/api/v1/jobs/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "12345",
      "includeHistory": true,
      "dateRange": "2024-01-01:2024-12-31"
    },
    "requestId": "req-001",
    "initiator": "user@example.com"
  }'
```

**Internal Request Model (used by service layer):**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.START)
    .parameters(Map.of(
        "customerId", "12345",
        "includeHistory", true,
        "dateRange", "2024-01-01:2024-12-31"
    ))
    .requestId("req-001")
    .initiator("user@example.com")
    .build();
```

**Response Model:**
```java
JobStageResponse {
    stage: START
    executionId: "exec-abc123"
    status: RUNNING
    success: true
    message: "Job started successfully"
    timestamp: 2025-01-15T10:30:00Z
}
```

**Implementation:**
```java
@Override
public Mono<JobStageResponse> startJob(JobStageRequest request) {
    // 1. Validate parameters
    validateParameters(request.getParameters());
    
    // 2. Create orchestrator request
    JobExecutionRequest executionRequest = JobExecutionRequest.builder()
        .jobDefinition(request.getJobType())
        .input(request.getParameters())
        .build();
    
    // 3. Start job via orchestrator
    return jobOrchestrator.startJob(executionRequest)
        .map(execution -> JobStageResponse.builder()
            .stage(JobStage.START)
            .executionId(execution.getExecutionId())
            .status(execution.getStatus())
            .success(true)
            .message("Job started successfully")
            .timestamp(Instant.now())
            .build())
        .doOnSuccess(response -> publishJobEvent("job.started", response));
}
```

**Key Points:**
- ✅ Must be idempotent (check for existing job)
- ✅ Validate all input parameters
- ✅ Return execution ID for tracking
- ✅ Publish job started event

---

### 2. CHECK Stage

**Purpose:** Monitor job progress and status

**Request:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.CHECK)
    .executionId("exec-abc123")
    .requestId("req-002")
    .build();
```

**Response:**
```java
JobStageResponse {
    stage: CHECK
    executionId: "exec-abc123"
    status: RUNNING | SUCCEEDED | FAILED
    progressPercentage: 75
    success: true
    message: "Job is 75% complete"
    timestamp: 2025-01-15T10:35:00Z
}
```

**Implementation:**
```java
@Override
public Mono<JobStageResponse> checkJob(JobStageRequest request) {
    return jobOrchestrator.checkJobStatus(request.getExecutionId())
        .map(status -> {
            int progress = calculateProgress(status);
            
            return JobStageResponse.builder()
                .stage(JobStage.CHECK)
                .executionId(request.getExecutionId())
                .status(status)
                .progressPercentage(progress)
                .success(true)
                .message(formatStatusMessage(status, progress))
                .timestamp(Instant.now())
                .build();
        })
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)));
}
```

**Status Values:**
- `RUNNING` - Job is in progress
- `SUCCEEDED` - Job completed successfully
- `FAILED` - Job failed
- `TIMED_OUT` - Job exceeded timeout
- `ABORTED` - Job was manually stopped

**Polling Pattern:**
```java
// Client-side polling
public Mono<JobStageResponse> pollUntilComplete(String executionId) {
    return Mono.defer(() -> checkJob(executionId))
        .flatMap(response -> {
            if (response.getStatus() == JobExecutionStatus.RUNNING) {
                return Mono.delay(Duration.ofSeconds(5))
                    .then(pollUntilComplete(executionId));
            }
            return Mono.just(response);
        })
        .timeout(Duration.ofMinutes(30));
}
```

---

### 3. COLLECT Stage

**Purpose:** Gather raw, unprocessed results from job execution

**Request:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.COLLECT)
    .executionId("exec-abc123")
    .requestId("req-003")
    .build();
```

**Response:**
```java
JobStageResponse {
    stage: COLLECT
    executionId: "exec-abc123"
    status: SUCCEEDED
    data: {
        "customer_id": "12345",
        "first_name": "John",
        "last_name": "Doe",
        "email_address": "john@example.com",
        "phone": "+1-555-0100",
        "created_at": "2024-01-15T10:30:00Z"
    }
    success: true
    message: "Raw results collected"
    timestamp: 2025-01-15T10:40:00Z
}
```

**Implementation:**
```java
@Override
public Mono<JobStageResponse> collectJobResults(JobStageRequest request) {
    return jobOrchestrator.getJobExecution(request.getExecutionId())
        .map(execution -> {
            // Get RAW output - NO TRANSFORMATION
            Map<String, Object> rawData = execution.getOutput();
            
            return JobStageResponse.builder()
                .stage(JobStage.COLLECT)
                .executionId(execution.getExecutionId())
                .status(execution.getStatus())
                .data(rawData)  // Raw data as-is
                .success(true)
                .message("Raw results collected successfully")
                .timestamp(Instant.now())
                .build();
        });
}
```

**Key Points:**
- ❌ **NO transformation** - return data as-is
- ✅ Data is `Map<String, Object>` (raw format)
- ✅ Useful for debugging or custom processing
- ✅ Can be called multiple times (idempotent)

---

### 4. RESULT Stage

**Purpose:** Transform raw data to typed DTO and return final results

**Request:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.RESULT)
    .executionId("exec-abc123")
    .targetDtoClass("com.example.dto.CustomerDTO")
    .requestId("req-004")
    .build();
```

**Response:**
```java
JobStageResponse {
    stage: RESULT
    executionId: "exec-abc123"
    status: SUCCEEDED
    data: {
        "result": {
            "customerId": "12345",
            "firstName": "John",
            "lastName": "Doe",
            "email": "john@example.com",
            "phoneNumber": "+1-555-0100",
            "registrationDate": "2024-01-15T10:30:00Z"
        }
    }
    success: true
    message: "Results transformed successfully"
    timestamp: 2025-01-15T10:40:00Z
}
```

**Implementation:**
```java
@Override
public Mono<JobStageResponse> getJobResult(JobStageRequest request) {
    // 1. Get raw data
    return collectJobResults(request)
        .flatMap(collectResponse -> {
            try {
                // 2. Load target DTO class
                Class<?> targetClass = Class.forName(request.getTargetDtoClass());
                
                // 3. Get mapper from registry
                JobResultMapper mapper = mapperRegistry.getMapper(targetClass)
                    .orElseThrow(() -> new MapperNotFoundException(targetClass));
                
                // 4. Extract raw data
                Map<String, Object> rawData = collectResponse.getData();
                
                // 5. Transform using MapStruct
                Object mappedResult = mapper.mapToTarget(rawData);
                
                // 6. Return transformed result
                return Mono.just(JobStageResponse.builder()
                    .stage(JobStage.RESULT)
                    .executionId(request.getExecutionId())
                    .status(collectResponse.getStatus())
                    .data(Map.of("result", mappedResult))
                    .success(true)
                    .message("Results transformed successfully")
                    .timestamp(Instant.now())
                    .build());
                    
            } catch (ClassNotFoundException e) {
                return Mono.error(new IllegalArgumentException(
                    "Target DTO class not found: " + request.getTargetDtoClass(), e));
            }
        })
        .doOnSuccess(response -> publishJobEvent("job.completed", response));
}
```

**Key Points:**
- ✅ **Applies transformation** via MapStruct
- ✅ Returns typed DTO (not raw Map)
- ✅ Requires mapper to be registered
- ✅ Publishes job completed event

---

## Best Practices

### 1. Idempotency

All stages must be idempotent:

```java
@Override
public Mono<JobStageResponse> startJob(JobStageRequest request) {
    // Check if job already exists
    return findExistingJob(request)
        .switchIfEmpty(createNewJob(request));
}
```

### 2. Error Handling

Handle errors gracefully:

```java
@Override
public Mono<JobStageResponse> checkJob(JobStageRequest request) {
    return jobOrchestrator.checkJobStatus(request.getExecutionId())
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
        .onErrorResume(error -> {
            log.error("Error checking job", error);
            return Mono.just(JobStageResponse.failure(
                JobStage.CHECK,
                request.getExecutionId(),
                error.getMessage()
            ));
        });
}
```

### 3. Event Publishing

Publish events for observability:

```java
private void publishJobEvent(String eventType, JobStageResponse response) {
    if (properties.isPublishJobEvents()) {
        JobEvent event = JobEvent.builder()
            .eventType(eventType)
            .executionId(response.getExecutionId())
            .stage(response.getStage())
            .status(response.getStatus())
            .timestamp(Instant.now())
            .build();
            
        eventPublisher.publish(event, properties.getJobEventsTopic());
    }
}
```

### 4. Timeout Handling

Set appropriate timeouts:

```java
@Override
public Mono<JobStageResponse> startJob(JobStageRequest request) {
    return jobOrchestrator.startJob(executionRequest)
        .timeout(Duration.ofSeconds(30))
        .map(execution -> buildResponse(execution));
}
```

---

## Error Handling

### Common Errors

**Job Not Found:**
```java
{
  "stage": "CHECK",
  "executionId": "exec-invalid",
  "success": false,
  "error": "Job execution not found",
  "timestamp": "2025-01-15T10:40:00Z"
}
```

**Mapper Not Found:**
```java
{
  "stage": "RESULT",
  "executionId": "exec-abc123",
  "success": false,
  "error": "No mapper found for target type: UnknownDTO",
  "timestamp": "2025-01-15T10:40:00Z"
}
```

**Job Failed:**
```java
{
  "stage": "CHECK",
  "executionId": "exec-abc123",
  "status": "FAILED",
  "success": false,
  "error": "Data extraction failed: Connection timeout",
  "timestamp": "2025-01-15T10:40:00Z"
}
```

---

## Examples

See [Examples](examples.md) for complete working examples of:
- Basic job lifecycle
- Polling patterns
- Error handling
- Event-driven workflows
- SAGA integration

---

## See Also

- [Architecture](architecture.md) - Design patterns
- [MapStruct Mappers](mappers.md) - Transformation details
- [Configuration](configuration.md) - Configuration options
- [API Reference](api-reference.md) - Complete API docs

