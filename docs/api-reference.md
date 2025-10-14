# API Reference

Complete API reference for `lib-common-data`.

## Table of Contents

- [Service Interfaces](#service-interfaces)
- [Controller Interfaces](#controller-interfaces)
- [Model Classes](#model-classes)
- [Orchestration Interfaces](#orchestration-interfaces)
- [Mapper Interfaces](#mapper-interfaces)

---

## Service Interfaces

### DataJobService

Business logic interface for job stage operations.

**Package:** `com.firefly.common.data.service`

```java
public interface DataJobService {
    Mono<JobStageResponse> startJob(JobStageRequest request);
    Mono<JobStageResponse> checkJob(JobStageRequest request);
    Mono<JobStageResponse> collectJobResults(JobStageRequest request);
    Mono<JobStageResponse> getJobResult(JobStageRequest request);
    default JobStage getSupportedStage() { return JobStage.ALL; }
}
```

#### Methods

##### startJob

```java
Mono<JobStageResponse> startJob(JobStageRequest request)
```

Starts a new data processing job.

**Parameters:**
- `request` - Job start request containing input parameters

**Returns:**
- `Mono<JobStageResponse>` - Response with execution details

**Responsibilities:**
- Validate input parameters
- Initialize resources needed for the job
- Trigger job execution via orchestrator
- Return job metadata for tracking

**Example:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.START)
    .jobType("customer-data-extraction")
    .parameters(Map.of("customerId", "12345"))
    .build();

Mono<JobStageResponse> response = dataJobService.startJob(request);
```

---

##### checkJob

```java
Mono<JobStageResponse> checkJob(JobStageRequest request)
```

Checks the status and progress of a running job.

**Parameters:**
- `request` - Check request containing execution ID

**Returns:**
- `Mono<JobStageResponse>` - Response with status and progress

**Responsibilities:**
- Query orchestrator for job status
- Gather progress metrics
- Check for errors or issues
- Return current state information

**Example:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.CHECK)
    .executionId("exec-abc123")
    .build();

Mono<JobStageResponse> response = dataJobService.checkJob(request);
```

---

##### collectJobResults

```java
Mono<JobStageResponse> collectJobResults(JobStageRequest request)
```

Collects intermediate or final raw results from a job.

**Parameters:**
- `request` - Collect request containing execution ID

**Returns:**
- `Mono<JobStageResponse>` - Response with raw collected data

**Responsibilities:**
- Retrieve raw processed data from storage or job execution
- Validate data quality and completeness
- Return raw/unprocessed data in original format
- **NO transformation or mapping applied**

**Example:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.COLLECT)
    .executionId("exec-abc123")
    .build();

Mono<JobStageResponse> response = dataJobService.collectJobResults(request);
```

---

##### getJobResult

```java
Mono<JobStageResponse> getJobResult(JobStageRequest request)
```

Retrieves final results, performs mapping/transformation, and cleanup.

**Parameters:**
- `request` - Result request containing execution ID and target DTO class info

**Returns:**
- `Mono<JobStageResponse>` - Response with transformed/mapped final results

**Responsibilities:**
1. Retrieve raw results (possibly by calling collectJobResults internally)
2. Apply transformation using configured mapper (MapStruct)
3. Map raw data to target DTO specified in request
4. Clean up temporary resources
5. Return mapped/transformed final results

**Example:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.RESULT)
    .executionId("exec-abc123")
    .targetDtoClass("com.example.dto.CustomerDTO")
    .build();

Mono<JobStageResponse> response = dataJobService.getJobResult(request);
```

---

## Controller Interfaces

### DataJobController

REST API interface for job stage endpoints.

**Package:** `com.firefly.common.data.controller`

**Base Path:** `/api/v1/jobs`

```java
@Tag(name = "Data Jobs", description = "Data processing job management endpoints")
@RequestMapping("/api/v1/jobs")
public interface DataJobController {
    Mono<JobStageResponse> startJob(@Valid @RequestBody JobStageRequest request);
    Mono<JobStageResponse> checkJob(@PathVariable String executionId, @RequestParam(required = false) String requestId);
    Mono<JobStageResponse> collectJobResults(@PathVariable String executionId, @RequestParam(required = false) String requestId);
    Mono<JobStageResponse> getJobResult(@PathVariable String executionId, @RequestParam(required = false) String requestId, @RequestParam(required = false) String targetDtoClass);
}
```

#### Endpoints

##### POST /api/v1/jobs/start

Start a new data processing job.

**Request Body:**
```json
{
  "stage": "START",
  "jobType": "customer-data-extraction",
  "parameters": {
    "customerId": "12345"
  },
  "requestId": "req-001",
  "initiator": "user@example.com"
}
```

**Response:**
```json
{
  "stage": "START",
  "executionId": "exec-abc123",
  "status": "RUNNING",
  "success": true,
  "message": "Job started successfully",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Status Codes:**
- `200` - Job started successfully
- `400` - Invalid request parameters
- `500` - Internal server error

---

##### GET /api/v1/jobs/{executionId}/check

Check job status.

**Path Parameters:**
- `executionId` - The job execution ID (required)

**Query Parameters:**
- `requestId` - Optional request ID for tracing

**Response:**
```json
{
  "stage": "CHECK",
  "executionId": "exec-abc123",
  "status": "SUCCEEDED",
  "progressPercentage": 100,
  "success": true,
  "message": "Job completed successfully",
  "timestamp": "2025-01-15T10:35:00Z"
}
```

**Status Codes:**
- `200` - Status retrieved successfully
- `404` - Job execution not found
- `500` - Internal server error

---

##### GET /api/v1/jobs/{executionId}/collect

Collect job results (raw data).

**Path Parameters:**
- `executionId` - The job execution ID (required)

**Query Parameters:**
- `requestId` - Optional request ID for tracing

**Response:**
```json
{
  "stage": "COLLECT",
  "executionId": "exec-abc123",
  "status": "SUCCEEDED",
  "data": {
    "customer_id": "12345",
    "first_name": "John",
    "last_name": "Doe",
    "email_address": "john@example.com"
  },
  "success": true,
  "timestamp": "2025-01-15T10:40:00Z"
}
```

**Status Codes:**
- `200` - Results collected successfully
- `404` - Job execution not found
- `500` - Internal server error

---

##### GET /api/v1/jobs/{executionId}/result

Get final results (transformed data).

**Path Parameters:**
- `executionId` - The job execution ID (required)

**Query Parameters:**
- `requestId` - Optional request ID for tracing
- `targetDtoClass` - Target DTO class name for transformation

**Response:**
```json
{
  "stage": "RESULT",
  "executionId": "exec-abc123",
  "status": "SUCCEEDED",
  "data": {
    "result": {
      "customerId": "12345",
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com"
    }
  },
  "success": true,
  "timestamp": "2025-01-15T10:40:00Z"
}
```

**Status Codes:**
- `200` - Results retrieved successfully
- `404` - Job execution not found
- `500` - Internal server error

---

## Model Classes

### JobStage

Enum defining job lifecycle stages.

**Package:** `com.firefly.common.data.model`

```java
public enum JobStage {
    START,    // Initialize and trigger the job
    CHECK,    // Monitor job progress and status
    COLLECT,  // Gather raw results (no transformation)
    RESULT,   // Transform and return final results
    ALL       // Used for services handling all stages
}
```

---

### JobStageRequest

Request model for job operations.

**Package:** `com.firefly.common.data.model`

```java
@Data
@Builder
public class JobStageRequest {
    private JobStage stage;
    private String jobType;
    private Map<String, Object> parameters;
    private String executionId;
    private String requestId;
    private String initiator;
    private String targetDtoClass;
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `stage` | `JobStage` | Yes | The job stage to execute |
| `jobType` | `String` | For START | Type of job to execute |
| `parameters` | `Map<String, Object>` | For START | Input parameters for the job |
| `executionId` | `String` | For CHECK/COLLECT/RESULT | Job execution ID |
| `requestId` | `String` | No | Request ID for tracing |
| `initiator` | `String` | No | User/system initiating the request |
| `targetDtoClass` | `String` | For RESULT | Fully qualified class name of target DTO |

---

### JobStageResponse

Response model with execution details and status.

**Package:** `com.firefly.common.data.model`

```java
@Data
@Builder
public class JobStageResponse {
    private JobStage stage;
    private String executionId;
    private JobExecutionStatus status;
    private Integer progressPercentage;
    private Map<String, Object> data;
    private Boolean success;
    private String message;
    private String error;
    private Instant timestamp;
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `stage` | `JobStage` | The stage that was executed |
| `executionId` | `String` | Unique job execution identifier |
| `status` | `JobExecutionStatus` | Current job status |
| `progressPercentage` | `Integer` | Progress percentage (0-100) |
| `data` | `Map<String, Object>` | Result data (raw or transformed) |
| `success` | `Boolean` | Whether operation succeeded |
| `message` | `String` | Human-readable message |
| `error` | `String` | Error message if failed |
| `timestamp` | `Instant` | Response timestamp |

---

## Orchestration Interfaces

### JobOrchestrator

Port interface for workflow orchestrators.

**Package:** `com.firefly.common.data.orchestration.port`

```java
public interface JobOrchestrator {
    Mono<JobExecution> startJob(JobExecutionRequest request);
    Mono<JobExecutionStatus> checkJobStatus(String executionId);
    Mono<JobExecutionStatus> stopJob(String executionId, String reason);
    Mono<JobExecution> getJobExecution(String executionId);
    String getOrchestratorType();
}
```

#### Methods

##### startJob

```java
Mono<JobExecution> startJob(JobExecutionRequest request)
```

Starts a new job execution.

**Parameters:**
- `request` - Job execution request containing job definition and input parameters

**Returns:**
- `Mono<JobExecution>` - Job execution information including execution ID

---

##### checkJobStatus

```java
Mono<JobExecutionStatus> checkJobStatus(String executionId)
```

Checks the status of a running job execution.

**Parameters:**
- `executionId` - Unique identifier of the job execution

**Returns:**
- `Mono<JobExecutionStatus>` - Current status of the job execution

---

##### stopJob

```java
Mono<JobExecutionStatus> stopJob(String executionId, String reason)
```

Stops a running job execution.

**Parameters:**
- `executionId` - Unique identifier of the job execution to stop
- `reason` - Optional reason for stopping the execution

**Returns:**
- `Mono<JobExecutionStatus>` - Final status of the stopped job

---

##### getJobExecution

```java
Mono<JobExecution> getJobExecution(String executionId)
```

Retrieves the execution history of a job.

**Parameters:**
- `executionId` - Unique identifier of the job execution

**Returns:**
- `Mono<JobExecution>` - Complete job execution details including history

---

### JobExecutionStatus

Enum of execution states.

**Package:** `com.firefly.common.data.orchestration.model`

```java
public enum JobExecutionStatus {
    RUNNING,      // Job is currently executing
    SUCCEEDED,    // Job completed successfully
    FAILED,       // Job failed with error
    TIMED_OUT,    // Job exceeded timeout
    ABORTED       // Job was manually stopped
}
```

---

## Mapper Interfaces

### JobResultMapper

Generic mapper interface for result transformation.

**Package:** `com.firefly.common.data.mapper`

```java
public interface JobResultMapper<S, T> {
    T mapToTarget(S source);
    default Class<S> getSourceType() { ... }
    default Class<T> getTargetType() { ... }
}
```

**Type Parameters:**
- `S` - Source type (raw data from job execution)
- `T` - Target type (transformed DTO)

#### Methods

##### mapToTarget

```java
T mapToTarget(S source)
```

Maps raw job result data to the target DTO.

**Parameters:**
- `source` - Raw data from job execution

**Returns:**
- `T` - Transformed target DTO

**Example:**
```java
@Mapper(componentModel = "spring")
public interface CustomerDataMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    
    @Override
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    CustomerDTO mapToTarget(Map<String, Object> source);
    
    @Override
    default Class<CustomerDTO> getTargetType() {
        return CustomerDTO.class;
    }
}
```

---

### JobResultMapperRegistry

Registry for managing job result mappers.

**Package:** `com.firefly.common.data.mapper`

```java
@Component
public class JobResultMapperRegistry {
    public JobResultMapperRegistry(List<JobResultMapper<?, ?>> mappers);
    public Optional<JobResultMapper<?, ?>> getMapper(Class<?> targetType);
    public boolean hasMapper(Class<?> targetType);
}
```

#### Methods

##### getMapper

```java
Optional<JobResultMapper<?, ?>> getMapper(Class<?> targetType)
```

Retrieves a mapper for the specified target type.

**Parameters:**
- `targetType` - Target DTO class

**Returns:**
- `Optional<JobResultMapper<?, ?>>` - Mapper if found

**Example:**
```java
JobResultMapper mapper = mapperRegistry.getMapper(CustomerDTO.class)
    .orElseThrow(() -> new MapperNotFoundException(CustomerDTO.class));

CustomerDTO result = mapper.mapToTarget(rawData);
```

---

## See Also

- [Getting Started](getting-started.md) - Setup guide
- [Job Lifecycle](job-lifecycle.md) - Stage details
- [Mappers](mappers.md) - Transformation patterns
- [Examples](examples.md) - Usage examples

