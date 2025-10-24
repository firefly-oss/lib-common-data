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

The `lib-common-data` library provides a unified approach to building data processing microservices within the Firefly ecosystem. It offers two main capabilities:

### 🔧 1. Data Jobs - Orchestrated Workflows

**Data Jobs** are for executing complex, multi-step workflows that interact with external systems (databases, APIs, file systems, etc.). Think of them as "tasks" that your microservice needs to perform.

**Use Cases:**
- Processing large datasets from external sources
- Running ETL (Extract, Transform, Load) operations
- Coordinating multi-step business processes
- Batch processing and scheduled tasks

**Two Types:**
- **Asynchronous Jobs** (for long-running tasks > 30 seconds)
  - Multi-stage lifecycle: START → CHECK → COLLECT → RESULT
  - Integrated with orchestrators (AWS Step Functions, Apache Airflow)
  - Example: Processing millions of records from a data warehouse

- **Synchronous Jobs** (for quick operations < 30 seconds)
  - Single-stage: EXECUTE
  - Returns results immediately
  - Example: Validating a single customer record

### 🌐 2. Data Enrichers - Third-Party Provider Integration

**Data Enrichers** are for fetching and integrating data from external third-party providers (credit bureaus, financial data providers, business intelligence services, etc.). They standardize how you call external APIs and merge their data with yours.

**Use Cases:**
- Enriching customer data with credit scores from credit bureaus
- Adding financial metrics from market data providers
- Augmenting company profiles with business intelligence data
- Validating addresses or tax IDs with government services

**Key Features:**
- Standardized REST API for all providers
- Automatic data merging strategies (ENHANCE, MERGE, REPLACE, RAW)
- Provider-specific operations (search, validate, quick-lookup)
- Built-in caching, retry, and circuit breaker patterns

### 🎯 Additional Capabilities

- **Event-Driven Architecture**: Seamless integration with `lib-common-eda`
- **CQRS Support**: Built-in CQRS pattern integration via `lib-common-cqrs`
- **Transactional Workflows**: Full SAGA support through `lib-transactional-engine`

### Why Use This Library?

- ✅ **Standardization** - Consistent patterns across all data processing microservices
- ✅ **Flexibility** - Pluggable orchestrators and providers via port/adapter architecture
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

The library supports two types of data jobs:

#### Asynchronous Jobs (Multi-Stage)
For long-running workflows (> 30 seconds):

1. **START**: Initialize and trigger data processing jobs
2. **CHECK**: Monitor job progress and status
3. **COLLECT**: Gather intermediate or final results
4. **RESULT**: Retrieve final results and perform cleanup

#### Synchronous Jobs (Single-Stage)
For quick operations (< 30 seconds):

1. **EXECUTE**: Single operation that returns results immediately

See [Synchronous Jobs Guide](docs/sync-jobs.md) for detailed documentation.

### 🎨 Service & Controller Interfaces

#### Asynchronous Jobs
- **DataJobService**: Business logic interface for multi-stage job operations
- **DataJobController**: REST API interface with OpenAPI documentation
- **AbstractResilientDataJobService**: Base class with observability, resiliency, and persistence

#### Synchronous Jobs
- **SyncDataJobService**: Business logic interface for single-stage execution
- **SyncDataJobController**: REST API interface for synchronous operations
- **AbstractResilientSyncDataJobService**: Base class with automatic features

Consistent API contracts across all core-data microservices

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

### 🔍 Data Enrichment

Standardized abstraction for enriching data from third-party providers (financial data, credit bureaus, business intelligence, etc.).

#### Core Features

- **Type-Safe Enrichers** - Generic base class `TypedDataEnricher<TSource, TProvider, TTarget>` with automatic strategy application (67% less code!)
- **Fluent Validation DSL** - Declarative parameter validation with `EnrichmentRequestValidator`
  - Required/optional parameters
  - Type validation (String, Integer, Boolean, etc.)
  - Pattern matching (regex)
  - Custom validators
- **Automatic Strategy Application** - Four strategies for merging data:
  - **ENHANCE**: Fill only null fields (preserves existing data)
  - **MERGE**: Combine source + provider (provider wins conflicts)
  - **REPLACE**: Use only provider data (ignore source)
  - **RAW**: Return provider data as-is without mapping
- **Tenant-Isolated Caching** ⭐ **NEW!** - Built-in caching with complete tenant isolation
  - Automatic cache key generation with tenant ID
  - Configurable TTL (time-to-live)
  - Cache hit/miss logging
  - Pattern-based eviction (by tenant, provider, or type)
  - 99% performance improvement for cached requests
- **Batch Enrichment** ⭐ **NEW!** - Process multiple requests in parallel
  - Configurable parallelism (default: 10 concurrent requests)
  - Automatic cache utilization for duplicates
  - Configurable batch size limits
  - Fail-fast or continue-on-error modes
  - 10x throughput improvement for bulk operations
- **Multiple Provider Support** - Financial data providers, credit bureaus, business intelligence, and custom providers
- **Multi-Region Architecture** - One microservice per provider with multiple regional implementations

#### Provider-Specific Custom Operations

Many providers require auxiliary operations before enrichment (e.g., searching for internal IDs, validating identifiers). The library provides a **class-based operation system** with automatic discovery and REST endpoint exposure:

**Example: Credit Bureau Provider**
```java
// 1. Define your request/response DTOs
public record CompanySearchRequest(
    String companyName,
    String taxId,
    Double minConfidence
) {}

public record CompanySearchResponse(
    String providerId,
    String companyName,
    String taxId,
    Double confidence
) {}

// 2. Create operation class with @ProviderCustomOperation annotation
@ProviderCustomOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.GET,
    tags = {"lookup", "search"}
)
public class SearchCompanyOperation
        extends AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {

    private final RestClient providerClient;

    public SearchCompanyOperation(RestClient providerClient) {
        this.providerClient = providerClient;
    }

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        return providerClient.get("/search", CompanySearchResponse.class)
            .withQueryParam("name", request.companyName())
            .withQueryParam("taxId", request.taxId())
            .execute();
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.companyName() == null && request.taxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
    }
}

// 3. Register operations in your enricher
@Service
public class CreditBureauEnricher extends TypedDataEnricher<...> {

    private final SearchCompanyOperation searchCompanyOperation;
    private final ValidateTaxIdOperation validateTaxIdOperation;

    @Override
    public List<ProviderOperation<?, ?>> getOperations() {
        return List.of(searchCompanyOperation, validateTaxIdOperation);
    }
}
```

**What You Get Automatically:**
- ✅ **REST Endpoints** - `GET /api/v1/enrichment/credit-bureau/operation/search-company`
- ✅ **JSON Schema Generation** - Request/response schemas auto-generated from DTOs
- ✅ **Type Safety** - Compile-time type checking for request/response
- ✅ **Validation** - Automatic request validation
- ✅ **Discovery** - `GET /api/v1/enrichment/credit-bureau/operations` lists all operations
- ✅ **OpenAPI Docs** - Full Swagger documentation

**Typical Workflow:**
1. **Search** for company → Get provider's internal ID
2. **Validate** identifier → Confirm it exists
3. **Enrich** data → Use internal ID to fetch full data

#### Integration & Observability

- **ServiceClient Integration** - Use REST, SOAP, or gRPC clients from `lib-common-client`
- **Automatic Observability** - Built-in tracing, metrics, and event publishing for enrichment operations
  - Distributed tracing with trace/span IDs
  - Metrics for success/failure rates, duration, data size
  - Enrichment lifecycle events (started, completed, failed)
- **Resiliency Patterns** - Circuit breaker, retry, rate limiting, and bulkhead for provider calls
- **Error Handling** - Comprehensive error handling with detailed error messages and metadata

#### REST API & Discovery

- **Provider Discovery** - Global discovery endpoint to list available providers and enrichment types
  - `GET /api/v1/enrichment/providers` - List all providers
  - `GET /api/v1/enrichment/providers?enrichmentType=credit-report` - Filter by type
  - Returns provider names, supported types, descriptions, and REST endpoints
- **REST API** - Standardized REST endpoints for enrichment operations
  - `POST /api/v1/enrichment/{provider-name}/enrich` - Enrich data
  - `GET /api/v1/enrichment/{provider-name}/health` - Health check
  - `GET /api/v1/enrichment/{provider-name}/operations` - List provider-specific operations
  - `GET|POST /api/v1/enrichment/{provider-name}/{operation-id}` - Execute provider operation
- **Automatic Endpoint Registration** - Controllers automatically register their endpoints with enrichers
- **OpenAPI Documentation** - Full Swagger/OpenAPI documentation for all endpoints

#### Configuration

- **Auto-Configuration** - Automatic Spring Boot configuration with `DataEnrichmentAutoConfiguration`
- **Configurable Properties** - Comprehensive configuration via `application.yml`:
  ```yaml
  firefly:
    data:
      enrichment:
        enabled: true
        publish-events: true
        cache-enabled: false
        default-timeout-seconds: 30
        retry-enabled: true
        max-retry-attempts: 3
  ```

See [Data Enrichment Guide](docs/data-enrichment.md) for detailed documentation.

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

Choose your use case:
- **[Quick Start: Data Jobs](#quick-start-data-jobs)** - For orchestrated workflows
- **[Quick Start: Data Enrichers](#quick-start-data-enrichers)** - For third-party provider integration

---

## Quick Start: Data Jobs

### 1. Configure Your Application

Add configuration to `application.yml`:

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS  # or APACHE_AIRFLOW
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:MyWorkflow
```

### 2A. Implement Asynchronous Data Job (Multi-Stage)

**Option A: Extend AbstractResilientDataJobService (Recommended)**

This approach provides built-in observability, resiliency, persistence, and comprehensive logging:

```java
@Service
@Slf4j
public class MyDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public MyDataJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobEventPublisher eventPublisher,
            JobAuditService auditService,
            JobExecutionResultService resultService,
            JobOrchestrator jobOrchestrator) {
        super(tracingService, metricsService, resiliencyService,
              eventPublisher, auditService, resultService);
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        log.debug("Starting job with parameters: {}", request.getParameters());

        // Use helper method to build JobExecutionRequest with all fields
        JobExecutionRequest executionRequest = buildJobExecutionRequest(
            request,
            "my-data-processing-workflow"
        );

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.success(
                JobStage.START,
                execution.getExecutionId(),
                "Job started successfully"
            ));
    }

    @Override
    protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
        return jobOrchestrator.checkJobStatus(request.getExecutionId())
            .map(status -> JobStageResponse.success(
                JobStage.CHECK,
                request.getExecutionId(),
                "Job status: " + status
            ));
    }

    @Override
    protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
        return jobOrchestrator.getJobExecution(request.getExecutionId())
            .map(execution -> {
                Map<String, Object> rawData = execution.getOutput();
                return JobStageResponse.builder()
                    .stage(JobStage.COLLECT)
                    .executionId(execution.getExecutionId())
                    .status(execution.getStatus())
                    .data(rawData)
                    .success(true)
                    .message("Raw results collected successfully")
                    .timestamp(Instant.now())
                    .build();
            });
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        // Implement result transformation logic here
        return doCollectJobResults(request);
    }

    @Override
    protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
        return jobOrchestrator.stopJob(request.getExecutionId(), reason)
            .map(status -> JobStageResponse.success(
                JobStage.STOP,
                request.getExecutionId(),
                "Job stopped: " + reason
            ));
    }

    @Override
    protected String getOrchestratorType() {
        return jobOrchestrator.getOrchestratorType();
    }

    @Override
    protected String getJobDefinition() {
        return "my-data-processing-workflow";
    }

    @Override
    protected String getJobName() {
        return "MyDataJob";
    }

    @Override
    protected String getJobDescription() {
        return "Processes customer data using workflow orchestration";
    }
}
```

**Benefits of using AbstractResilientDataJobService:**
- ✅ Automatic distributed tracing with Micrometer
- ✅ Metrics collection (execution time, success/failure rates, data sizes)
- ✅ Circuit breaker, retry, rate limiting, and bulkhead patterns
- ✅ Audit trail persistence
- ✅ Execution result persistence with caching
- ✅ Comprehensive logging for all lifecycle phases
- ✅ Event publishing for job lifecycle events
- ✅ Automatic job discovery and registration logging at startup

**Required Methods to Implement:**
- `doStartJob(JobStageRequest)` - Start a new job execution
- `doCheckJob(JobStageRequest)` - Check job status
- `doCollectJobResults(JobStageRequest)` - Collect raw job results
- `doGetJobResult(JobStageRequest)` - Get transformed job results
- `doStopJob(JobStageRequest, String)` - Stop a running job

**Recommended Methods to Override:**
- `getJobName()` - Return a meaningful job name (default: class name)
- `getJobDescription()` - Return a description of what the job does
- `getOrchestratorType()` - Return the orchestrator type (e.g., "AWS_STEP_FUNCTIONS")
- `getJobDefinition()` - Return the job definition identifier (e.g., state machine ARN)

**Option B: Implement DataJobService Interface (Manual approach)**

Only use this if you need full control and don't want the built-in features:

```java
@Service
@Slf4j
public class MyDataJobService implements DataJobService {
    // Implementation similar to Option A but without automatic features
    // See full example in docs/examples.md
}
```

### 3. Implement DataJobController (Recommended: Use AbstractDataJobController)

**Option A: Extend AbstractDataJobController (Recommended)**

This approach provides automatic comprehensive logging for all HTTP requests/responses:

```java
@RestController
@Tag(name = "Data Job - CustomerData", description = "Customer data processing job management endpoints")
public class CustomerDataJobController extends AbstractDataJobController {

    public CustomerDataJobController(DataJobService dataJobService) {
        super(dataJobService);
    }

    // That's it! All endpoints are implemented with automatic logging
}
```

**Swagger Tags:**

The `@Tag` annotation should be added to specify the Swagger documentation tag:

```java
@RestController
@Tag(name = "Data Job - CustomerData", description = "Customer data processing job management endpoints")
public class CustomerDataJobController extends AbstractDataJobController {

    public CustomerDataJobController(DataJobService dataJobService) {
        super(dataJobService);
    }
}
```

**Benefits of using AbstractDataJobController:**
- ✅ Automatic logging of all HTTP requests with parameters
- ✅ Automatic logging of successful responses with execution details
- ✅ Automatic logging of error responses with error details
- ✅ Request/response timing information
- ✅ All standard endpoints already implemented
- ✅ Dynamic Swagger tag generation based on job name

**Option B: Implement DataJobController Interface (Manual approach)**

Only use this if you need custom endpoint behavior:

```java
@RestController
@Slf4j
public class MyDataJobController implements DataJobController {

    private final DataJobService dataJobService;

    public MyDataJobController(DataJobService dataJobService) {
        this.dataJobService = dataJobService;
    }

    @Override
    public Mono<JobStageResponse> startJob(JobStartRequest request) {
        log.info("Starting job with parameters: {}", request.getParameters());

        // Convert JobStartRequest to JobStageRequest for service layer
        JobStageRequest stageRequest = JobStageRequest.builder()
            .stage(JobStage.START)
            .parameters(request.getParameters())
            .requestId(request.getRequestId())
            .initiator(request.getInitiator())
            .metadata(request.getMetadata())
            .build();

        return dataJobService.startJob(stageRequest);
    }

    @Override
    public Mono<JobStageResponse> checkJob(String executionId, String requestId) {
        log.info("Checking job: {}", executionId);
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .requestId(requestId)
            .build();
        return dataJobService.checkJob(request);
    }

    @Override
    public Mono<JobStageResponse> collectJobResults(String executionId, String requestId) {
        log.info("Collecting results: {}", executionId);
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.COLLECT)
            .executionId(executionId)
            .requestId(requestId)
            .build();
        return dataJobService.collectJobResults(request);
    }

    @Override
    public Mono<JobStageResponse> getJobResult(String executionId, String requestId, String targetDtoClass) {
        log.info("Getting result: {}", executionId);
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.RESULT)
            .executionId(executionId)
            .requestId(requestId)
            .targetDtoClass(targetDtoClass)
            .build();
        return dataJobService.getJobResult(request);
    }
}
```

### 4. Job Auto-Discovery

When your application starts, `lib-common-data` automatically discovers and logs all registered DataJobs:

```
================================================================================
DATA JOB DISCOVERY - Scanning for registered DataJobs...
================================================================================
Found 2 DataJobService implementation(s):

  ✓ Job: CustomerDataJob
    ├─ Bean Name: customerDataJobService
    ├─ Class: CustomerDataJobService
    ├─ Description: Processes customer data using workflow orchestration
    ├─ Orchestrator: AWS_STEP_FUNCTIONS
    └─ Job Definition: arn:aws:states:us-east-1:123456789012:stateMachine:customer-data-extraction

  ✓ Job: OrderDataJob
    ├─ Bean Name: orderDataJobService
    ├─ Class: OrderDataJobService
    ├─ Description: Processes order data using workflow orchestration
    ├─ Orchestrator: AWS_STEP_FUNCTIONS
    └─ Job Definition: arn:aws:states:us-east-1:123456789012:stateMachine:order-data-extraction

Found 2 DataJobController implementation(s):
  ✓ Controller: customerDataJobController (CustomerDataJobController)
  ✓ Controller: orderDataJobController (OrderDataJobController)

================================================================================
DATA JOB DISCOVERY COMPLETE - 2 job(s) registered and ready
================================================================================
```

**Benefits:**
- ✅ Verify all jobs are correctly registered at startup
- ✅ Identify configuration issues early
- ✅ Document available jobs in logs
- ✅ Useful for debugging and monitoring

**To get better discovery information, override these methods in your service:**
```java
@Override
protected String getJobName() {
    return "CustomerDataJob";
}

@Override
protected String getJobDescription() {
    return "Processes customer data using workflow orchestration";
}

@Override
protected String getOrchestratorType() {
    return "AWS_STEP_FUNCTIONS";
}

@Override
protected String getJobDefinition() {
    return "arn:aws:states:us-east-1:123456789012:stateMachine:customer-data-extraction";
}
```

### 2B. Implement Synchronous Data Job (Single-Stage)

For quick operations that complete in < 30 seconds:

```java
@Service
@Slf4j
public class DataValidationSyncJob extends AbstractResilientSyncDataJobService {

    private final ValidationService validationService;

    public DataValidationSyncJob(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobEventPublisher eventPublisher,
            JobAuditService auditService,
            JobExecutionResultService resultService,
            ValidationService validationService) {
        super(tracingService, metricsService, resiliencyService,
              eventPublisher, auditService, resultService);
        this.validationService = validationService;
    }

    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        String customerId = (String) request.getParameters().get("customerId");

        return validationService.validateCustomer(customerId)
            .map(result -> JobStageResponse.builder()
                .success(true)
                .executionId(request.getExecutionId())
                .data(Map.of("validationResult", result))
                .message("Customer validation completed successfully")
                .build());
    }

    @Override
    protected String getJobName() {
        return "DataValidationJob";
    }

    @Override
    protected String getJobDescription() {
        return "Validates customer data synchronously";
    }
}
```

**Controller for Sync Job:**

```java
@RestController
@Tag(name = "Sync Job - Data Validation")
public class DataValidationSyncJobController extends AbstractSyncDataJobController {

    public DataValidationSyncJobController(SyncDataJobService syncJobService) {
        super(syncJobService);
    }

    // That's it! Endpoint is automatically exposed:
    // POST /api/v1/sync-jobs/execute
}
```

**See [Synchronous Jobs Guide](docs/sync-jobs.md) for complete documentation.**

---

## Quick Start: Data Enrichers

### 1. Configure Your Application

Add configuration to `application.yml`:

```yaml
firefly:
  data:
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30
      retry-enabled: true
      max-retry-attempts: 3

# Provider-specific configuration
credit:
  bureau:
    base-url: https://api.credit-bureau-provider.com
    api-key: ${CREDIT_BUREAU_API_KEY}
```

### 2. Implement Data Enricher (Recommended: Use TypedDataEnricher)

**Option A: Extend TypedDataEnricher (Recommended)**

This approach provides built-in observability, resiliency, and automatic strategy application:

```java
@Service
public class FinancialDataEnricher
        extends TypedDataEnricher<CompanyProfileDTO, FinancialDataResponse, CompanyProfileDTO> {

    private final RestClient financialDataClient;

    public FinancialDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            @Value("${financial-data.base-url}") String baseUrl,
            @Value("${financial-data.api-key}") String apiKey) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);

        // Create REST client using lib-common-client
        this.financialDataClient = ServiceClient.rest("financial-data-provider")
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Step 1: Fetch data from the third-party provider.
     * The framework handles tracing, metrics, circuit breaker, and retry automatically.
     */
    @Override
    protected Mono<FinancialDataResponse> fetchProviderData(EnrichmentRequest request) {
        String companyId = request.requireParam("companyId");

        return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
            .withPathParam("id", companyId)
            .execute();
    }

    /**
     * Step 2: Map provider data to your target DTO format.
     * The framework automatically applies the enrichment strategy (ENHANCE/MERGE/REPLACE/RAW)
     * to combine this with the sourceDto.
     */
    @Override
    protected CompanyProfileDTO mapToTarget(FinancialDataResponse providerData) {
        return CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .annualRevenue(providerData.getRevenue())
                .employeeCount(providerData.getTotalEmployees())
                .build();
    }

    @Override
    public String getProviderName() {
        return "Financial Data Provider";
    }

    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"company-profile", "company-financials"};
    }

    @Override
    public String getEnricherDescription() {
        return "Enriches company data with financial and corporate information";
    }
}
```

### 3. Add Provider-Specific Custom Operations (Optional but Recommended)

Many providers require auxiliary operations (search, validate, lookup). Create operation classes with `@ProviderCustomOperation` annotation:

```java
// Step 1: Define DTOs for your operation
public record CompanySearchRequest(
    String companyName,
    String taxId,
    Double minConfidence
) {}

public record CompanySearchResponse(
    String providerId,
    String companyName,
    String taxId,
    Double confidence
) {}

// Step 2: Create operation class
@ProviderCustomOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.GET,
    tags = {"lookup", "search"}
)
public class SearchCompanyOperation
        extends AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {

    private final RestClient bureauClient;

    public SearchCompanyOperation(RestClient bureauClient) {
        this.bureauClient = bureauClient;
    }

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        return bureauClient.get("/search", CompanySearchResponse.class)
            .withQueryParam("name", request.companyName())
            .withQueryParam("taxId", request.taxId())
            .execute()
            .map(result -> new CompanySearchResponse(
                result.getId(),
                result.getName(),
                result.getTaxId(),
                result.getMatchScore()
            ));
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.companyName() == null && request.taxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
    }
}

// Step 3: Register operations in your enricher
@Service
public class CreditBureauEnricher
        extends TypedDataEnricher<CreditReportDTO, CreditBureauReportResponse, CreditReportDTO> {

    private final SearchCompanyOperation searchCompanyOperation;
    private final ValidateTaxIdOperation validateTaxIdOperation;

    public CreditBureauEnricher(
            RestClient bureauClient,
            SearchCompanyOperation searchCompanyOperation,
            ValidateTaxIdOperation validateTaxIdOperation) {
        this.bureauClient = bureauClient;
        this.searchCompanyOperation = searchCompanyOperation;
        this.validateTaxIdOperation = validateTaxIdOperation;
    }

    @Override
    public List<ProviderOperation<?, ?>> getOperations() {
        return List.of(searchCompanyOperation, validateTaxIdOperation);
    }

    // ... enrichment methods ...
}
```

**What You Get Automatically:**
- ✅ **REST Endpoints** - `GET /api/v1/enrichment/credit-bureau/operation/search-company`
- ✅ **JSON Schema Generation** - Request/response schemas auto-generated from DTOs
- ✅ **Type Safety** - Compile-time type checking for request/response
- ✅ **Validation** - Automatic request validation via `validateRequest()` method
- ✅ **Discovery** - `GET /api/v1/enrichment/credit-bureau/operations` lists all operations with schemas
- ✅ **OpenAPI Docs** - Full Swagger documentation with examples

**Typical Workflow:**
```bash
# Step 1: Discover available operations
GET /api/v1/enrichment/credit-bureau/operations
→ Returns: List of operations with JSON schemas and examples

# Step 2: Search for company to get provider's internal ID
POST /api/v1/enrichment/credit-bureau/operation/search-company
{
  "companyName": "Acme Corp",
  "taxId": "TAX-123",
  "minConfidence": 0.8
}
→ Returns: {"providerId": "PROV-12345", "companyName": "ACME CORP", "taxId": "TAX-123", "confidence": 0.95}

# Step 3: Enrich data using the provider ID
POST /api/v1/enrichment/credit-bureau/enrich
{
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "sourceDto": {"companyId": "123", "name": "Acme Corp"},
  "parameters": {"providerId": "PROV-12345"}
}
```

**Benefits:**
- ✅ Automatic distributed tracing with Micrometer
- ✅ Metrics collection (execution time, success/failure rates, data sizes)
- ✅ Circuit breaker, retry, rate limiting, and bulkhead patterns
- ✅ Automatic enrichment strategy application (ENHANCE/MERGE/REPLACE/RAW)
- ✅ Automatic response building with metadata and field counting
- ✅ Event publishing for enrichment lifecycle events
- ✅ Comprehensive logging for all enrichment phases
- ✅ Type safety with generics (prevents runtime casting errors)

**Required Methods to Implement:**
- `fetchProviderData(EnrichmentRequest)` - Fetch data from the provider's API
- `mapToTarget(TProvider)` - Map provider response to your target DTO format

**Recommended Methods to Override:**
- `getProviderName()` - Return the provider name (e.g., "Credit Bureau Provider")
- `getSupportedEnrichmentTypes()` - Return enrichment types this enricher supports
- `getEnricherDescription()` - Return a description of what this enricher does

### 5. Implement Data Enricher Controller (Recommended: Use AbstractDataEnricherController)

**Option A: Extend AbstractDataEnricherController (Recommended)**

This approach provides automatic comprehensive logging for all HTTP requests/responses:

```java
@RestController
@RequestMapping("/api/v1/enrichment/financial-data-company")
@Tag(name = "Data Enrichment - Financial Data Company",
     description = "Financial data company profile enrichment")
public class FinancialDataCompanyController extends AbstractDataEnricherController {

    public FinancialDataCompanyController(
            @Qualifier("financialDataEnricher") DataEnricher enricher,
            DataEnricherRegistry registry) {
        super(enricher, registry);
    }

    // That's it! All endpoints are implemented with automatic logging:
    // POST /api/v1/enrichment/financial-data-company/enrich
    // GET  /api/v1/enrichment/financial-data-company/health
    // GET  /api/v1/enrichment/financial-data-company/operations (if ProviderOperationCatalog is implemented)
    // GET|POST /api/v1/enrichment/financial-data-company/operation/{operationId} (for provider-specific operations)
}
```

**URL Pattern:** `/api/v1/enrichment/{provider}-{region}-{type}`
- **provider**: financial-data
- **region**: (optional, e.g., spain, usa)
- **type**: company

**Benefits of using AbstractDataEnricherController:**
- ✅ Automatic logging of all HTTP requests with parameters
- ✅ Automatic logging of successful responses with enrichment details
- ✅ Automatic logging of error responses with error details
- ✅ Request/response timing information
- ✅ All standard endpoints already implemented
- ✅ Automatic endpoint registration with the enricher
- ✅ Support for provider-specific operations (if enricher implements ProviderOperationCatalog)

**Option B: Implement Custom Controller (Manual approach)**

Only use this if you need custom endpoint behavior:

```java
@RestController
@RequestMapping("/api/v1/enrichment/custom-provider")
@Slf4j
public class CustomEnricherController {

    private final DataEnricher enricher;

    public CustomEnricherController(DataEnricher enricher) {
        this.enricher = enricher;
    }

    @PostMapping("/enrich")
    public Mono<EnrichmentApiResponse> enrich(@RequestBody EnrichmentApiRequest request) {
        log.info("Received enrichment request: type={}, strategy={}",
                request.getEnrichmentType(), request.getStrategy());

        return enricher.enrich(request.toEnrichmentRequest())
            .map(EnrichmentApiResponse::fromEnrichmentResponse);
    }
}
```

### 6. Provider Discovery

The library automatically provides a global discovery endpoint to list all available enrichers:

```bash
# List all providers in this microservice
GET /api/v1/enrichment/providers

Response:
{
  "providers": [
    {
      "providerName": "Financial Data Provider",
      "supportedTypes": ["company-profile", "company-financials"],
      "description": "Enriches company data with financial and corporate information",
      "endpoints": [
        "/api/v1/enrichment/financial-data-company/enrich"
      ],
      "operations": null
    }
  ]
}

# Filter by enrichment type
GET /api/v1/enrichment/providers?enrichmentType=company-profile

Response:
{
  "providers": [
    {
      "providerName": "Financial Data Provider",
      "supportedTypes": ["company-profile", "company-financials"],
      ...
    }
  ]
}
```

**Benefits:**
- ✅ Discover all available enrichers at runtime
- ✅ Filter by enrichment type
- ✅ Get provider metadata (name, description, supported types)
- ✅ Get REST endpoint URLs
- ✅ Useful for dynamic routing and service discovery

**See [Data Enrichment Guide](docs/data-enrichment.md) for complete documentation including:**
- Provider-specific operations catalog
- Advanced validation with EnrichmentRequestValidator
- Custom enrichment strategies
- Caching and performance optimization
- Complete tutorial for building a professional enricher

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
    @Mapping(source = "customer_id", target = "id")
    @Mapping(source = "first_name", target = "givenName")
    @Mapping(source = "last_name", target = "familyName")
    @Mapping(source = "email_address", target = "emailAddress")
    @Mapping(source = "created_at", target = "registrationDate")
    CustomerDTO mapToTarget(Map<String, Object> rawData);

    @Override
    default Class<CustomerDTO> getTargetType() {
        return CustomerDTO.class;
    }
}
```

**Note:** The source field names use snake_case as they typically come from database queries or external APIs. The target DTO uses camelCase following Java conventions.

**Complex Mapping Example:**

```java
@Mapper(componentModel = "spring")
public interface SalesDataMapper extends JobResultMapper<Map<String, Object>, SalesReportDTO> {

    @Override
    @Mapping(target = "reportId", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(source = "total_revenue", target = "revenue")
    @Mapping(source = "order_count", target = "totalOrders")
    @Mapping(source = "reporting_period", target = "reportingPeriod", qualifiedByName = "parsePeriod")
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

### Data Enrichment Configuration

```yaml
firefly:
  data:
    enrichment:
      # Enable/disable data enrichment feature
      enabled: true

      # Event publishing for enrichment lifecycle events
      publish-events: true

      # Caching configuration (requires lib-common-cache)
      cache-enabled: true                # Enable caching (default: false)
      cache-ttl-seconds: 3600            # Cache TTL in seconds (default: 3600 = 1 hour)

      # Batch enrichment configuration
      max-batch-size: 100                # Maximum requests per batch (default: 100)
      batch-parallelism: 10              # Parallel processing level (default: 10)
      batch-fail-fast: false             # Fail entire batch on first error (default: false)

      # Timeout configuration
      default-timeout-seconds: 30

      # Capture raw provider responses for debugging
      capture-raw-responses: false

      # Concurrency control
      max-concurrent-enrichments: 100

    # Resiliency configuration (applies to both jobs and enrichment)
    orchestration:
      resiliency:
        # Circuit breaker configuration
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        circuit-breaker-sliding-window-size: 100

        # Retry configuration
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s

        # Rate limiter configuration
        rate-limiter-enabled: false
        rate-limiter-limit-for-period: 100
        rate-limiter-limit-refresh-period: 1s
        rate-limiter-timeout-duration: 5s

        # Bulkhead configuration
        bulkhead-enabled: false
        bulkhead-max-concurrent-calls: 25
        bulkhead-max-wait-duration: 500ms
```

**Data Enrichment Properties:**

- `enabled` - Enable/disable the data enrichment feature (default: `true`)
- `publish-events` - Publish enrichment lifecycle events to EDA (default: `true`)
- `cache-enabled` - Enable caching of enrichment results with tenant isolation (default: `false`, requires `lib-common-cache`)
- `cache-ttl-seconds` - Cache time-to-live in seconds (default: `3600`)
- `max-batch-size` - Maximum number of requests per batch (default: `100`)
- `batch-parallelism` - Number of parallel requests in batch processing (default: `10`)
- `batch-fail-fast` - Stop batch processing on first error (default: `false`)
- `default-timeout-seconds` - Default timeout for provider calls (default: `30`)
- `capture-raw-responses` - Capture raw provider responses for debugging (default: `false`)
- `max-concurrent-enrichments` - Maximum concurrent enrichment operations (default: `100`)

**Resiliency Properties** (shared between jobs and enrichment):

- `circuit-breaker-enabled` - Enable circuit breaker pattern (default: `true`)
- `circuit-breaker-failure-rate-threshold` - Failure rate threshold percentage (default: `50.0`)
- `circuit-breaker-wait-duration-in-open-state` - Wait duration in open state (default: `60s`)
- `retry-enabled` - Enable retry pattern (default: `true`)
- `retry-max-attempts` - Maximum retry attempts (default: `3`)
- `retry-wait-duration` - Wait duration between retries (default: `5s`)
- `rate-limiter-enabled` - Enable rate limiter pattern (default: `false`)
- `bulkhead-enabled` - Enable bulkhead pattern (default: `false`)

See [Data Enrichment Guide](docs/data-enrichment.md) for complete configuration details.

## API Endpoints

### Job Orchestration Endpoints

When implementing `DataJobController`, the following REST endpoints are exposed:

- `POST /api/v1/jobs/start` - Start a new job
- `GET /api/v1/jobs/{executionId}/check` - Check job status
- `GET /api/v1/jobs/{executionId}/collect` - Collect job results
- `GET /api/v1/jobs/{executionId}/result` - Get final results

### Data Enrichment Endpoints

When implementing `AbstractDataEnricherController`, the following REST endpoints are exposed:

**Per-Provider Endpoints:**
- `POST /api/v1/enrichment/{provider-region-type}/enrich` - Enrich single data request
- `POST /api/v1/enrichment/{provider-region-type}/enrich/batch` ⭐ **NEW!** - Enrich multiple requests in parallel
- `GET /api/v1/enrichment/{provider-region-type}/health` - Health check for specific provider
- `GET /api/v1/enrichment/{provider-region-type}/operations` - List provider-specific operations
- `GET|POST /api/v1/enrichment/{provider-region-type}/operation/{operationId}` - Execute provider-specific operation

**Global Discovery Endpoint:**
- `GET /api/v1/enrichment/providers` - List all available providers with their operations catalog
- `GET /api/v1/enrichment/providers?enrichmentType={type}` - Filter providers by enrichment type

**Example - Standard Enrichment:**
```bash
# Discover providers
GET /api/v1/enrichment/providers?enrichmentType=credit-report

# Enrich data using specific provider
POST /api/v1/enrichment/provider-a-spain-credit/enrich
{
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "sourceDto": { ... },
  "parameters": { "companyId": "12345" }
}
```

**Example - Provider-Specific Custom Operations:**
```bash
# List operations for a provider (with JSON schemas)
GET /api/v1/enrichment/credit-bureau/operations
→ Returns: {
  "providerName": "Credit Bureau Provider",
  "operations": [
    {
      "operationId": "search-company",
      "path": "/api/v1/enrichment/credit-bureau/operation/search-company",
      "method": "GET",
      "description": "Search for a company by name or tax ID to obtain provider internal ID",
      "tags": ["lookup", "search"],
      "requestSchema": { ... JSON Schema ... },
      "responseSchema": { ... JSON Schema ... },
      "requestExample": { "companyName": "Acme Corp", "taxId": "A12345678" },
      "responseExample": { "providerId": "PROV-12345", "confidence": 0.95 }
    }
  ]
}

# Search for company to get internal ID (before enrichment)
POST /api/v1/enrichment/credit-bureau/operation/search-company
{
  "companyName": "Acme Corp",
  "taxId": "A12345678",
  "minConfidence": 0.8
}
→ Returns: { "providerId": "PROV-12345", "companyName": "ACME CORP", "taxId": "A12345678", "confidence": 0.95 }

# Then use the internal ID for enrichment
POST /api/v1/enrichment/credit-bureau/enrich
{
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "sourceDto": { ... },
  "parameters": { "providerId": "PROV-12345" }
}
```

All endpoints are documented with OpenAPI/Swagger annotations.

#### Provider-Specific Custom Operations

Enrichers can expose auxiliary operations specific to the provider's API using `@ProviderCustomOperation` annotation:

```java
// 1. Define DTOs
public record CompanySearchRequest(
    String companyName,
    String taxId,
    Double minConfidence
) {}

public record CompanySearchResponse(
    String providerId,
    String companyName,
    String taxId,
    Double confidence
) {}

// 2. Create operation class
@ProviderCustomOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.GET,
    tags = {"lookup", "search"}
)
public class SearchCompanyOperation
        extends AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {

    private final RestClient providerClient;

    public SearchCompanyOperation(RestClient providerClient) {
        this.providerClient = providerClient;
    }

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        return providerClient.get("/search", CompanySearchResponse.class)
            .withQueryParam("name", request.companyName())
            .withQueryParam("taxId", request.taxId())
            .execute();
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.companyName() == null && request.taxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
    }
}

// 3. Register in enricher
@Service
public class CreditBureauEnricher extends TypedDataEnricher<...> {

    private final SearchCompanyOperation searchCompanyOperation;

    @Override
    public List<ProviderOperation<?, ?>> getOperations() {
        return List.of(searchCompanyOperation);
    }
}
```

**Common Use Cases:**
- **ID Lookups**: Search for internal provider IDs before enrichment
- **Entity Matching**: Fuzzy match companies/individuals in provider's database
- **Validation**: Validate identifiers (tax IDs, business numbers, etc.)
- **Metadata**: Retrieve provider-specific metadata or configuration

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

### 🚀 Quick Start
- **[Step-by-Step Guide](docs/step-by-step-guide.md)** - **NEW!** Complete guide to building a microservice from scratch
  - Project setup and dependencies
  - Configuration (dev vs prod)
  - Creating job orchestrators (MOCK, AWS Step Functions, multiple orchestrators)
  - Creating multiple data job services
  - Creating multiple controllers
  - Testing and troubleshooting
- **[Multiple Jobs Example](docs/multiple-jobs-example.md)** - Real-world example with 3 different job types
- **[Synchronous Jobs Guide](docs/sync-jobs.md)** - ⭐ **NEW!** Complete guide for synchronous data jobs
- **[Data Enrichment Guide](docs/data-enrichment.md)** - ⭐ **NEW!** Complete guide for data enrichment from third-party providers

### Core Documentation
- **[Architecture](docs/architecture.md)** - Deep dive into hexagonal architecture and design patterns
- **[Getting Started](docs/getting-started.md)** - Basic guide with complete examples
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
- **[Multiple Jobs Example](docs/multiple-jobs-example.md)** - ⭐ **Complete microservice with multiple controllers and data jobs**
- **[Testing Guide](docs/testing.md)** - Testing strategies and examples

### Additional Resources
- **[Documentation Cleanup Summary](DOCUMENTATION_CLEANUP_SUMMARY.md)** - Details on documentation accuracy and what's provided vs what must be implemented

### Quick Links for Common Tasks

- **Want to create a microservice with multiple data jobs?** → See [Multiple Jobs Example](docs/multiple-jobs-example.md)
- **Need synchronous jobs for quick operations?** → See [Synchronous Jobs Guide](docs/sync-jobs.md)
- **Need to enrich data from third-party providers?** → See [Data Enrichment Guide](docs/data-enrichment.md)
- **Need to understand the abstract base classes?** → See sections 2 and 3 in [Quick Start](#quick-start)
- **Want JSON logging?** → It's enabled by default! See [Logging](docs/logging.md) for configuration
- **Need to add observability?** → Use `AbstractResilientDataJobService` or `AbstractResilientSyncDataJobService` - it's automatic!

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
