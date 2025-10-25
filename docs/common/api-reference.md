# API Reference

Complete API reference for `lib-common-data`.

## Table of Contents

- [Service Interfaces](#service-interfaces)
  - [Asynchronous Jobs](#asynchronous-jobs)
  - [Synchronous Jobs](#synchronous-jobs)
- [Controller Interfaces](#controller-interfaces)
  - [Asynchronous Controllers](#asynchronous-controllers)
  - [Synchronous Controllers](#synchronous-controllers)
- [Model Classes](#model-classes)
- [Orchestration Interfaces](#orchestration-interfaces)
- [Mapper Interfaces](#mapper-interfaces)
- [Utility Classes](#utility-classes)

---

## Service Interfaces

### Asynchronous Jobs

#### DataJobService

Business logic interface for multi-stage job operations.

**Package:** `com.firefly.common.data.service`

```java
public interface DataJobService {
    Mono<JobStageResponse> startJob(JobStageRequest request);
    Mono<JobStageResponse> checkJob(JobStageRequest request);
    Mono<JobStageResponse> collectJobResults(JobStageRequest request);
    Mono<JobStageResponse> getJobResult(JobStageRequest request);
    Mono<JobStageResponse> stopJob(JobStageRequest request, String reason);
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

**Note:** The response status will be `SUCCEEDED` for successful operations, `FAILED` for failures, or `RUNNING` if still in progress.

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

### Synchronous Jobs

#### SyncDataJobService

Business logic interface for single-stage synchronous job operations.

**Package:** `com.firefly.common.data.service`

```java
public interface SyncDataJobService {
    Mono<JobStageResponse> execute(JobStageRequest request);
    default String getJobName() { return getClass().getSimpleName(); }
    default String getJobDescription() { return "Synchronous data job"; }
}
```

#### Methods

##### execute

```java
Mono<JobStageResponse> execute(JobStageRequest request)
```

Executes a synchronous data job in a single operation.

**Parameters:**
- `request` - Job execution request containing input parameters

**Returns:**
- `Mono<JobStageResponse>` - Response with execution results

**Responsibilities:**
- Validate input parameters
- Execute the job logic synchronously
- Return results immediately (< 30 seconds)
- Handle errors gracefully

**Example:**
```java
JobStageRequest request = JobStageRequest.builder()
    .stage(JobStage.ALL)
    .parameters(Map.of("customerId", "12345"))
    .build();

Mono<JobStageResponse> response = syncDataJobService.execute(request);
```

**Use Cases:**
- Customer data enrichment
- Real-time validation
- Credit checks
- Quick transformations
- Synchronous lookups

**When to Use:**
- Operations complete in < 30 seconds
- Real-time response required
- No need for progress tracking
- Simple, single-step operations

See [Data Jobs — Complete Guide](../data-jobs/guide.md#quick-start-sync) for complete documentation and examples.

---

## Controller Interfaces

### Asynchronous Controllers

#### DataJobController

REST API interface for job stage endpoints.

**Package:** `com.firefly.common.data.controller`

**Base Path:** `/api/v1/jobs`

```java
@Tag(name = "Data Jobs", description = "Data processing job management endpoints")
@RequestMapping("/api/v1/jobs")
public interface DataJobController {
    Mono<JobStageResponse> startJob(@Valid @RequestBody JobStartRequest request);
    Mono<JobStageResponse> checkJob(@PathVariable String executionId, @RequestParam(required = false) String requestId);
    Mono<JobStageResponse> collectJobResults(@PathVariable String executionId, @RequestParam(required = false) String requestId);
    Mono<JobStageResponse> getJobResult(@PathVariable String executionId, @RequestParam(required = false) String requestId, @RequestParam(required = false) String targetDtoClass);
    Mono<JobStageResponse> stopJob(@PathVariable String executionId, @RequestParam(required = false) String requestId, @RequestParam(required = false) String reason);
}
```

#### Endpoints

##### POST /api/v1/jobs/start

Start a new data processing job.

**Request Body (JobStartRequest):**
```json
{
  "parameters": {
    "customerId": "12345"
  },
  "requestId": "req-001",
  "initiator": "user@example.com",
  "metadata": {
    "department": "sales"
  }
}
```

**Request Fields:**
- `parameters` (Map<String, Object>, required): Input parameters for the job
- `requestId` (String, optional): Request ID for tracing and correlation
- `initiator` (String, optional): User or system that initiated the request
- `metadata` (Map<String, String>, optional): Additional metadata

**Note:** The `stage`, `executionId`, and `jobType` fields are NOT part of the request body. These are managed internally by the controller and service layer.

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

##### POST /api/v1/jobs/{executionId}/stop

Stop a running job execution.

**Path Parameters:**
- `executionId` - The job execution ID (required)

**Query Parameters:**
- `requestId` - Optional request ID for tracing
- `reason` - Optional reason for stopping the job

**Response:**
```json
{
  "stage": "STOP",
  "executionId": "exec-abc123",
  "status": "ABORTED",
  "success": true,
  "message": "Job stopped: User requested cancellation",
  "timestamp": "2025-01-15T10:45:00Z"
}
```

**Status Codes:**
- `200` - Job stopped successfully
- `404` - Job execution not found
- `500` - Internal server error

---

### Synchronous Controllers

#### SyncDataJobController

REST API interface for synchronous job operations.

**Package:** `com.firefly.common.data.controller`

```java
@Tag(name = "Sync Data Jobs")
@RequestMapping("/api/v1")
public interface SyncDataJobController {
    @PostMapping("/execute")
    Mono<JobStageResponse> execute(
        @RequestParam Map<String, Object> parameters,
        @RequestParam(required = false) String requestId,
        @RequestParam(required = false) String initiator,
        @RequestParam(required = false) String metadata
    );
}
```

#### Endpoints

##### POST /api/v1/execute

Execute a synchronous data job (single operation, returns results immediately).

**Request Parameters:**
- `parameters` (Map<String, Object>, required): Input parameters for the job
- `requestId` (String, optional): Request ID for tracing and correlation
- `initiator` (String, optional): User or system that initiated the request
- `metadata` (String, optional): Additional metadata as JSON string

**Example Request:**
```bash
curl -X POST "http://localhost:8080/api/v1/execute" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "parameters[customerId]=12345" \
  -d "requestId=req-001" \
  -d "initiator=user@example.com" \
  -d 'metadata={"department":"sales"}'
```

**Response:**
```json
{
  "stage": "ALL",
  "executionId": "sync-abc123",
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
  "message": "Job executed successfully",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

**Status Codes:**
- `200` - Job executed successfully
- `400` - Invalid request parameters
- `500` - Internal server error

**Use Cases:**
- Quick data enrichment (< 30 seconds)
- Real-time validation
- Synchronous transformations
- Credit checks
- Customer lookups

**When to Use:**
- Operations that complete in < 30 seconds
- Real-time API responses required
- No need for progress tracking
- Simple, single-step operations

**When NOT to Use:**
- Long-running operations (> 30 seconds)
- Complex multi-step workflows
- Operations requiring progress monitoring
- Jobs that may timeout

See [Data Jobs — Complete Guide](../data-jobs/guide.md#quick-start-sync) for complete documentation.

---

### Data Enrichment Controllers

#### EnrichmentDiscoveryController

Global controller for discovering available data enrichment providers.

**Package:** `com.firefly.common.data.controller`

**Purpose:**
- Provides endpoints to discover which data enrichers are available in the microservice
- Useful for service discovery, multi-region support, and dynamic configuration
- Lists providers with their supported types, descriptions, and REST endpoints
- Can filter providers by enrichment type

**Configuration:**
```yaml
# Enabled by default. To disable:
firefly.data.enrichment.discovery.enabled: false
```

##### Endpoints

###### GET /api/v1/enrichment/providers

Lists all available data enrichment providers in this microservice.

**Query Parameters:**
- `enrichmentType` (optional) - Filter to only return providers that support this enrichment type

**Response:** `List<ProviderInfo>`

**ProviderInfo Structure:**
```json
{
  "providerName": "Financial Data Provider",
  "type": "company-profile",
  "description": "Enriches company data with financial and corporate information",
  "endpoints": [
    "/api/v1/enrichment/financial-data/enrich"
  ],
  "operations": [
    {
      "operationId": "search-company",
      "path": "/api/v1/enrichment/financial-data/operation/search-company",
      "method": "POST",
      "description": "Search for a company by name or tax ID",
      "tags": ["search", "lookup"],
      "requiresAuth": true
    }
  ]
}
```

**Example - List All Providers:**
```bash
GET /api/v1/enrichment/providers

Response:
[
  {
    "providerName": "Financial Data Provider",
    "type": "company-profile",
    "description": "Enriches company data with financial and corporate information",
    "endpoints": ["/api/v1/enrichment/financial-data/enrich"]
  },
  {
    "providerName": "Credit Bureau",
    "type": "credit-report",
    "description": "Credit Bureau data enrichment services",
    "endpoints": ["/api/v1/enrichment/credit-bureau/enrich"]
  }
]
```

**Example - Filter by Enrichment Type:**
```bash
GET /api/v1/enrichment/providers?enrichmentType=company-profile

Response:
[
  {
    "providerName": "Financial Data Provider",
    "type": "company-profile",
    "description": "Enriches company data with financial and corporate information",
    "endpoints": ["/api/v1/enrichment/financial-data/enrich"]
  }
]
```

**Use Cases:**
- **Service Discovery:** Finding which providers and enrichment types are available
- **Multi-Region Support:** Discovering regional implementations (e.g., Provider A Spain vs Provider A USA)
- **Dynamic Configuration:** Building UIs or configurations based on available providers
- **Health Monitoring:** Checking which enrichers are registered and active

**Architecture Context:**

In a typical deployment, you might have multiple microservices, each dedicated to a specific provider:

```
Microservice: core-data-provider-a-enricher
├── ProviderASpainCreditReportEnricher
│   ├── providerName: "Provider A Spain"
│   ├── type: "credit-report"
│   └── endpoint: POST /api/v1/enrichment/provider-a-spain-credit/enrich
├── ProviderAUSACreditReportEnricher
│   ├── providerName: "Provider A USA"
│   ├── type: "credit-report"
│   └── endpoint: POST /api/v1/enrichment/provider-a-usa-credit/enrich
└── ProviderASpainCompanyProfileEnricher
    ├── providerName: "Provider A Spain"
    ├── type: "company-profile"
    └── endpoint: POST /api/v1/enrichment/provider-a-spain-company/enrich

Microservice: core-data-provider-b-enricher
├── ProviderBSpainCreditReportEnricher
└── ProviderBUSACreditReportEnricher
```

Each microservice exposes its own discovery endpoint that lists only the providers available in that microservice.

**Implementation:**

The controller uses `DataEnricherRegistry` to discover all enrichers:

```java
@RestController
@RequestMapping("/api/v1/enrichment")
@Tag(name = "Data Enrichment - Discovery")
@ConditionalOnProperty(
    prefix = "firefly.data.enrichment.discovery",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class EnrichmentDiscoveryController {

    private final DataEnricherRegistry enricherRegistry;

    @GetMapping("/providers")
    public Mono<List<ProviderInfo>> listProviders(
            @RequestParam(required = false) String enrichmentType) {
        // Implementation uses enricherRegistry.getAllEnrichers()
        // and filters by enrichmentType if specified
    }
}
```

**Note:** All enrichers extend `DataEnricher<TSource, TProvider, TTarget>` which provides automatic observability, resiliency, caching, and strategy application.

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
    STOP,     // Stop a running job execution
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
    private Map<String, String> metadata;
    private String targetDtoClass;
    private String mapperName;
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
| `metadata` | `Map<String, String>` | No | Additional metadata |
| `targetDtoClass` | `String` | For RESULT | Fully qualified class name of target DTO |
| `mapperName` | `String` | No | Specific mapper name to use (optional, auto-selected if not specified) |

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
    private boolean success;
    private String message;
    private Integer progressPercentage;
    private Map<String, Object> data;
    private String error;
    private Instant timestamp;
    private Map<String, String> metadata;
}
```

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `stage` | `JobStage` | The stage that was executed |
| `executionId` | `String` | Unique job execution identifier |
| `status` | `JobExecutionStatus` | Current job status |
| `success` | `boolean` | Whether operation succeeded |
| `message` | `String` | Human-readable message |
| `progressPercentage` | `Integer` | Progress percentage (0-100) |
| `data` | `Map<String, Object>` | Result data (raw or transformed) |
| `error` | `String` | Error message if failed |
| `timestamp` | `Instant` | Response timestamp |
| `metadata` | `Map<String, String>` | Additional metadata |

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

### JobExecutionRequest

Request model for starting a job execution in the orchestrator.

**Package:** `com.firefly.common.data.orchestration.model`

```java
@Data
@Builder
public class JobExecutionRequest {
    private String jobDefinition;      // Required: Job definition identifier (e.g., state machine ARN)
    private String executionName;      // Optional: Unique name for this execution
    private Map<String, Object> input; // Input parameters for the job
    private String requestId;          // Request ID for tracing and correlation
    private String initiator;          // User or system that initiated the job
    private String traceHeader;        // Optional trace header for distributed tracing
    private Map<String, String> metadata; // Additional metadata
}
```

**Fields:**
- `jobDefinition` (String, required) - The job definition identifier (e.g., state machine ARN for AWS Step Functions)
- `executionName` (String, optional) - Unique name for this execution (auto-generated if not provided)
- `input` (Map<String, Object>) - Input parameters for the job execution
- `requestId` (String) - Request ID for tracing and correlation across distributed systems
- `initiator` (String) - User or system that initiated the job execution
- `traceHeader` (String) - Optional trace header for distributed tracing (e.g., X-Amzn-Trace-Id)
- `metadata` (Map<String, String>) - Additional metadata, tags, or contextual information

**Best Practice:**
Use the `buildJobExecutionRequest()` helper method in `AbstractResilientDataJobService` to automatically populate all fields from `JobStageRequest`:

```java
@Override
protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
    JobExecutionRequest executionRequest = buildJobExecutionRequest(
        request,
        "my-job-definition"
    );
    return jobOrchestrator.startJob(executionRequest)
        .map(execution -> JobStageResponse.success(...));
}
```

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

### DataEnricherRegistry

Registry for managing and discovering data enrichers.

**Package:** `com.firefly.common.data.service`

**Purpose:**
- Auto-discovers all `DataEnricher` implementations in the Spring context
- Provides lookup by provider name or enrichment type
- Used by the discovery endpoint to list available providers
- Follows the Registry pattern (similar to JobResultMapperRegistry)

```java
public class DataEnricherRegistry {
    public DataEnricherRegistry(List<DataEnricher<?, ?, ?>> enrichers);

    // Lookup by provider
    public Optional<DataEnricher<?, ?, ?>> getEnricherByProvider(String providerName);
    public Optional<DataEnricher<?, ?, ?>> getEnricherByProviderAndTenant(String providerName, UUID tenantId);

    // Lookup by type
    public Optional<DataEnricher<?, ?, ?>> getEnricherForType(String enrichmentType);
    public Optional<DataEnricher<?, ?, ?>> getEnricherForTypeAndTenant(String enrichmentType, UUID tenantId);
    public List<DataEnricher<?, ?, ?>> getAllEnrichersForType(String enrichmentType);
    public List<DataEnricher<?, ?, ?>> getAllEnrichersForTypeAndTenant(String enrichmentType, UUID tenantId);

    // Lookup by tenant
    public List<DataEnricher<?, ?, ?>> getEnrichersByTenant(UUID tenantId);

    // Lookup by tag
    public List<DataEnricher<?, ?, ?>> getEnrichersByTag(String tag);

    // Get all
    public List<DataEnricher<?, ?, ?>> getAllEnrichers();
    public List<String> getAllProviderNames();
    public List<String> getAllEnrichmentTypes();
    public Set<UUID> getAllTenantIds();

    // Existence checks
    public boolean hasEnricherForProvider(String providerName);
    public boolean hasEnricherForType(String enrichmentType);
    public int getEnricherCount();
}
```

#### Methods

##### getEnricherByProvider

```java
Optional<DataEnricher<?, ?, ?>> getEnricherByProvider(String providerName)
```

Gets an enricher by provider name (case-insensitive).

**Parameters:**
- `providerName` - The provider name (e.g., "Financial Data Provider", "Credit Bureau")

**Returns:**
- `Optional<DataEnricher<?, ?, ?>>` - Enricher if found, empty otherwise

**Example:**
```java
DataEnricher<?, ?, ?> enricher = enricherRegistry
    .getEnricherByProvider("Financial Data Provider")
    .orElseThrow(() -> new EnricherNotFoundException("Financial Data Provider"));

EnrichmentResponse response = enricher.enrich(request).block();
```

##### getEnricherForType

```java
Optional<DataEnricher<?, ?, ?>> getEnricherForType(String enrichmentType)
```

Gets an enricher that supports the specified enrichment type.

**Parameters:**
- `enrichmentType` - The enrichment type (e.g., "company-profile", "credit-report")

**Returns:**
- `Optional<DataEnricher<?, ?, ?>>` - First enricher that supports this type, empty if none found

**Example:**
```java
// Dynamically select enricher based on enrichment type
DataEnricher<?, ?, ?> enricher = enricherRegistry
    .getEnricherForType("company-profile")
    .orElseThrow(() -> new EnricherNotFoundException("company-profile"));

EnrichmentResponse response = enricher.enrich(request).block();
```

**Note:** In the standard architecture, each enricher has its own dedicated REST endpoint, so this method is typically used for programmatic lookup in advanced scenarios.

##### getAllEnrichers

```java
List<DataEnricher<?, ?, ?>> getAllEnrichers()
```

Gets all registered enrichers.

**Returns:**
- `List<DataEnricher<?, ?, ?>>` - List of all enrichers in the registry

**Example:**
```java
List<DataEnricher<?, ?, ?>> enrichers = enricherRegistry.getAllEnrichers();
log.info("Found {} enrichers", enrichers.size());

for (DataEnricher<?, ?, ?> enricher : enrichers) {
    log.info("Provider: {}, Types: {}, Tenant: {}",
        enricher.getProviderName(),
        enricher.getSupportedEnrichmentTypes(),
        enricher.getTenantId());
}
```

##### getAllProviderNames

```java
List<String> getAllProviderNames()
```

Gets all registered provider names (distinct).

**Returns:**
- `List<String>` - List of unique provider names

**Example:**
```java
List<String> providers = enricherRegistry.getAllProviderNames();
// ["Financial Data Provider", "Credit Bureau", "Business Data Provider"]
```

##### getAllEnrichmentTypes

```java
List<String> getAllEnrichmentTypes()
```

Gets all registered enrichment types (sorted).

**Returns:**
- `List<String>` - List of unique enrichment types

**Example:**
```java
List<String> types = enricherRegistry.getAllEnrichmentTypes();
// ["company-financials", "company-profile", "credit-report", "credit-score"]
```

##### getAllTenantIds

```java
Set<UUID> getAllTenantIds()
```

Gets all registered tenant IDs.

**Returns:**
- `Set<UUID>` - Set of unique tenant IDs

**Example:**
```java
Set<UUID> tenants = enricherRegistry.getAllTenantIds();
// [00000000-0000-0000-0000-000000000000, 550e8400-e29b-41d4-a716-446655440001]
```

##### getEnricherByProviderAndTenant

```java
Optional<DataEnricher<?, ?, ?>> getEnricherByProviderAndTenant(String providerName, UUID tenantId)
```

Gets an enricher by provider name and tenant ID.

**Parameters:**
- `providerName` - The provider name
- `tenantId` - The tenant UUID

**Returns:**
- `Optional<DataEnricher<?, ?, ?>>` - Enricher if found, empty otherwise

**Example:**
```java
UUID tenantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
DataEnricher<?, ?, ?> enricher = enricherRegistry
    .getEnricherByProviderAndTenant("Financial Data Provider", tenantId)
    .orElseThrow(() -> new EnricherNotFoundException("Financial Data Provider for tenant " + tenantId));
```

##### getEnricherForTypeAndTenant

```java
Optional<DataEnricher<?, ?, ?>> getEnricherForTypeAndTenant(String enrichmentType, UUID tenantId)
```

Gets the highest-priority enricher for a specific type and tenant.

**Parameters:**
- `enrichmentType` - The enrichment type
- `tenantId` - The tenant UUID

**Returns:**
- `Optional<DataEnricher<?, ?, ?>>` - Highest-priority enricher if found, empty otherwise

**Example:**
```java
UUID tenantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
DataEnricher<?, ?, ?> enricher = enricherRegistry
    .getEnricherForTypeAndTenant("credit-report", tenantId)
    .orElseThrow(() -> new EnricherNotFoundException("credit-report for tenant " + tenantId));
```

##### getAllEnrichersForTypeAndTenant

```java
List<DataEnricher<?, ?, ?>> getAllEnrichersForTypeAndTenant(String enrichmentType, UUID tenantId)
```

Gets all enrichers for a specific type and tenant, sorted by priority (descending).

**Parameters:**
- `enrichmentType` - The enrichment type
- `tenantId` - The tenant UUID

**Returns:**
- `List<DataEnricher<?, ?, ?>>` - List of enrichers sorted by priority

**Example:**
```java
UUID tenantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
List<DataEnricher<?, ?, ?>> enrichers = enricherRegistry
    .getAllEnrichersForTypeAndTenant("credit-report", tenantId);

// Use the highest priority enricher
DataEnricher primary = enrichers.get(0);

// Or implement fallback logic
for (DataEnricher<?, ?, ?> enricher : enrichers) {
    try {
        return enricher.enrich(request).block();
    } catch (Exception e) {
        log.warn("Enricher {} failed, trying next", enricher.getProviderName());
    }
}
```

##### getEnrichersByTenant

```java
List<DataEnricher<?, ?, ?>> getEnrichersByTenant(UUID tenantId)
```

Gets all enrichers for a specific tenant.

**Parameters:**
- `tenantId` - The tenant UUID

**Returns:**
- `List<DataEnricher<?, ?, ?>>` - List of enrichers for this tenant

**Example:**
```java
UUID tenantId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
List<DataEnricher<?, ?, ?>> enrichers = enricherRegistry.getEnrichersByTenant(tenantId);
log.info("Found {} enrichers for tenant {}", enrichers.size(), tenantId);
```

##### getEnrichersByTag

```java
List<DataEnricher<?, ?, ?>> getEnrichersByTag(String tag)
```

Gets all enrichers with a specific tag.

**Parameters:**
- `tag` - The tag to search for

**Returns:**
- `List<DataEnricher<?, ?, ?>>` - List of enrichers with this tag

**Example:**
```java
List<DataEnricher<?, ?, ?>> financialEnrichers = enricherRegistry.getEnrichersByTag("financial");
List<DataEnricher<?, ?, ?>> premiumEnrichers = enricherRegistry.getEnrichersByTag("premium");
```

##### hasEnricherForProvider

```java
boolean hasEnricherForProvider(String providerName)
```

Checks if an enricher exists for the specified provider.

**Parameters:**
- `providerName` - The provider name

**Returns:**
- `boolean` - true if enricher exists, false otherwise

##### hasEnricherForType

```java
boolean hasEnricherForType(String enrichmentType)
```

Checks if an enricher exists for the specified enrichment type.

**Parameters:**
- `enrichmentType` - The enrichment type

**Returns:**
- `boolean` - true if enricher exists, false otherwise

##### getEnricherCount

```java
int getEnricherCount()
```

Gets the number of registered enrichers.

**Returns:**
- `int` - Count of enrichers

#### Auto-Discovery

All Spring beans implementing `DataEnricher` are automatically registered when the application context starts.

**Example:**
```java
@EnricherMetadata(
    providerName = "Financial Data Provider",
    tenantId = "550e8400-e29b-41d4-a716-446655440001",
    type = "company-profile",
    description = "Provides financial data enrichment",
    version = "1.0.0",
    tags = {"financial", "company-data"},
    priority = 100
)
public class FinancialDataEnricher extends DataEnricher<...> {
    // Automatically registered in DataEnricherRegistry
    // Metadata is read from @EnricherMetadata annotation
}
```

**Metadata Annotation:**

The `@EnricherMetadata` annotation is **required** for all enrichers and provides:

- **providerName** (required): Unique identifier for the provider
- **tenantId** (optional): UUID of the tenant this enricher serves (default: global tenant `00000000-0000-0000-0000-000000000000`)
- **type** (required): The single enrichment type this enricher supports (one enricher = one type)
- **description** (optional): Human-readable description
- **version** (optional): Version string (default: "1.0.0")
- **tags** (optional): Array of tags for categorization
- **priority** (optional): Priority for selection when multiple enrichers match (default: 50, higher = higher priority)
- **enabled** (optional): Whether the enricher is enabled (default: true)

**Multi-Tenancy:**

Enrichers can be tenant-specific or global:

```java
// Global enricher (available to all tenants)
@EnricherMetadata(
    providerName = "Global Credit Provider",
    tenantId = "00000000-0000-0000-0000-000000000000",  // Global tenant
    type = "credit-report"
)

// Tenant-specific enricher
@EnricherMetadata(
    providerName = "Spain Credit Provider",
    tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Spain tenant
    type = "credit-report"
)
```

**Registration Process:**
1. Spring creates all `DataEnricher` beans
2. `DataEnricherRegistry` receives them via constructor injection
3. Registry reads `@EnricherMetadata` annotation from each enricher
4. Registry indexes them by:
   - Provider name (case-insensitive)
   - Tenant ID
   - Enrichment type
   - Provider + Tenant combination
   - Tenant + Type combination
5. Lists are sorted by priority (descending)
6. Disabled enrichers are skipped

**Logging:**
```
INFO  DataEnricherRegistry - Registering 3 data enrichers
DEBUG DataEnricherRegistry - Registered enricher for provider: Financial Data Provider (tenant: 550e8400-e29b-41d4-a716-446655440001)
DEBUG DataEnricherRegistry - Registered enricher 'Financial Data Provider' for type: company-profile
DEBUG DataEnricherRegistry - Registered enricher 'Financial Data Provider' for type: company-financials
INFO  DataEnricherRegistry - Data enricher registration complete. Providers: 3, Types: 5, Tenants: 2
```

---

## Utility Classes

### TracingContextExtractor

Utility class for extracting trace IDs and span IDs from Micrometer Observation.

**Package:** `com.firefly.common.data.util`

**Features:**
- ✅ Real trace ID and span ID extraction from Micrometer Tracing
- ✅ Support for Brave (Zipkin) and OpenTelemetry backends
- ✅ Multiple extraction strategies with fallbacks
- ✅ Automatic configuration via Spring Boot

#### Methods

##### setTracer

```java
public static void setTracer(Tracer tracerInstance)
```

Sets the tracer instance to use for extraction. This is automatically called by `ObservabilityAutoConfiguration`.

**Parameters:**
- `tracerInstance` - The Micrometer Tracer instance

**Note:** This method is called automatically during Spring Boot startup. Manual invocation is rarely needed.

##### extractTraceId

```java
public static String extractTraceId(Observation observation)
```

Extracts the trace ID from the current observation.

**Parameters:**
- `observation` - The Micrometer Observation

**Returns:**
- `String` - The trace ID (16-character hex for Brave), or `null` if not available

**Example:**
```java
Observation observation = observationRegistry.getCurrentObservation();
String traceId = TracingContextExtractor.extractTraceId(observation);
// traceId = "59e63de2fc596870"
```

##### extractSpanId

```java
public static String extractSpanId(Observation observation)
```

Extracts the span ID from the current observation.

**Parameters:**
- `observation` - The Micrometer Observation

**Returns:**
- `String` - The span ID (16-character hex for Brave), or `null` if not available

**Example:**
```java
Observation observation = observationRegistry.getCurrentObservation();
String spanId = TracingContextExtractor.extractSpanId(observation);
// spanId = "59e63de2fc596870"
```

##### hasTracingContext

```java
public static boolean hasTracingContext(Observation observation)
```

Checks if the observation has an active tracing context.

**Parameters:**
- `observation` - The Micrometer Observation

**Returns:**
- `boolean` - `true` if tracing context is available, `false` otherwise

**Example:**
```java
if (TracingContextExtractor.hasTracingContext(observation)) {
    String traceId = TracingContextExtractor.extractTraceId(observation);
    // Use trace ID...
}
```

---

### DataSizeCalculator

Utility class for calculating data sizes by JSON serialization.

**Package:** `com.firefly.common.data.util`

**Features:**
- ✅ Precise byte size calculation via JSON serialization
- ✅ UTF-8 encoding for accurate byte count
- ✅ Support for complex objects and nested structures
- ✅ Human-readable size formatting
- ✅ Size validation utilities

#### Methods

##### calculateSize

```java
public static long calculateSize(Object data)
```

Calculates the size of a data object in bytes by serializing it to JSON.

**Parameters:**
- `data` - The object to measure

**Returns:**
- `long` - Size in bytes, or `0` if data is `null`

**Example:**
```java
Map<String, Object> data = Map.of("key", "value", "count", 42);
long size = DataSizeCalculator.calculateSize(data);
// size = 27 bytes
```

##### calculateCombinedSize

```java
public static long calculateCombinedSize(Object... dataObjects)
```

Calculates the combined size of multiple data objects.

**Parameters:**
- `dataObjects` - Variable number of objects to measure

**Returns:**
- `long` - Total size in bytes

**Example:**
```java
long totalSize = DataSizeCalculator.calculateCombinedSize(
    rawOutput,
    transformedOutput,
    metadata
);
// totalSize = 1247 bytes
```

##### calculateMapSize

```java
public static long calculateMapSize(Map<?, ?> map)
```

Calculates the size of a Map by serializing it to JSON.

**Parameters:**
- `map` - The Map to measure

**Returns:**
- `long` - Size in bytes, or `0` if map is `null` or empty

**Example:**
```java
Map<String, String> params = Map.of("param1", "value1", "param2", "value2");
long size = DataSizeCalculator.calculateMapSize(params);
```

##### formatSize

```java
public static String formatSize(long bytes)
```

Formats a byte size into a human-readable string.

**Parameters:**
- `bytes` - Size in bytes

**Returns:**
- `String` - Formatted size (e.g., "1.2 KB", "3.5 MB")

**Example:**
```java
String formatted = DataSizeCalculator.formatSize(1247);
// formatted = "1.2 KB"

String formatted2 = DataSizeCalculator.formatSize(5242880);
// formatted2 = "5.0 MB"
```

##### exceedsSize

```java
public static boolean exceedsSize(Object data, long maxSizeBytes)
```

Checks if a data object exceeds a specified size limit.

**Parameters:**
- `data` - The object to check
- `maxSizeBytes` - Maximum allowed size in bytes

**Returns:**
- `boolean` - `true` if data exceeds the limit, `false` otherwise

**Example:**
```java
// Check if data exceeds 1MB
boolean tooLarge = DataSizeCalculator.exceedsSize(myData, 1024 * 1024);
if (tooLarge) {
    throw new DataTooLargeException("Data exceeds 1MB limit");
}
```

---

## See Also

- [Getting Started](getting-started.md) - Setup guide
- [Job Lifecycle](../data-jobs/guide.md#job-lifecycle-async) - Stage details
- [Mappers](mappers.md) - Transformation patterns
- [Examples](examples.md) - Usage examples
- [Observability](observability.md) - Tracing and metrics
- [Persistence](persistence.md) - Audit trail and result storage

