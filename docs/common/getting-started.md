# Getting Started

This guide will walk you through setting up and using the `lib-common-data` library in your microservice.

## Overview

The `lib-common-data` library provides **two distinct architecture patterns** for building data processing microservices:

### 1. **Data Jobs** - Orchestrated Workflow Pattern

For executing **complex, multi-step workflows** that interact with external systems (databases, APIs, file systems, etc.).

**Architecture:**
- **Orchestrator-driven** - External orchestrator (Airflow, AWS Step Functions) manages workflow
- **Multi-stage** - START → CHECK → COLLECT → RESULT → STOP (async) or EXECUTE (sync)
- **Long-running** - Can take minutes or hours (async) or seconds (sync)
- **Stateful** - Orchestrator tracks job state and progress

**Use Cases:**
- Processing large datasets from external sources
- Running ETL (Extract, Transform, Load) operations
- Coordinating multi-step business processes
- Batch processing and scheduled tasks
- Quick data validation and transformation (sync jobs)

**Example:** Import customer data from external CRM → Validate → Transform → Load into database

### 2. **Data Enrichers** - Provider Integration Pattern

For **fetching and integrating data** from third-party providers (credit bureaus, financial data providers, business intelligence services, etc.).

**Architecture:**
- **Request-driven** - Client sends enrichment request with partial data
- **Single-stage** - Fetch provider data → Map to DTO → Merge with source data
- **Quick** - Typically completes in seconds
- **Stateless** - No workflow state, just request/response

**Use Cases:**
- Enriching customer data with credit scores from credit bureaus
- Adding financial metrics from market data providers
- Augmenting company profiles with business intelligence data
- Validating addresses or tax IDs with government services
- Fetching real-time market data or exchange rates

**Example:** Receive partial company data → Fetch complete data from credit bureau → Merge and return enriched data

---

### Which Pattern Should I Use?

| Scenario | Pattern | Why |
|----------|---------|-----|
| Import data from external database | **Data Jobs** | Multi-step workflow with orchestration |
| Enrich customer with credit score | **Data Enrichers** | Single provider call with data merging |
| Run nightly ETL process | **Data Jobs** | Long-running, scheduled workflow |
| Validate tax ID with government API | **Data Enrichers** | Quick provider lookup |
| Process large CSV file | **Data Jobs** | Multi-stage processing with state tracking |
| Add market data to portfolio | **Data Enrichers** | Real-time provider integration |

**Can I use both?** Yes! You can implement both patterns in the same microservice.

---

> **💡 Looking for a complete step-by-step guide?**
> - For **Data Jobs**: See [Data Jobs — Complete Guide](../data-jobs/guide.md)
> - For **Data Enrichers**: See [Step-by-Step Guide: Data Enricher Microservice](../data-enrichers/enricher-microservice-guide.md) with multi-module Maven structure

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Guide 1: Data Jobs (Asynchronous)](#guide-1-data-jobs-asynchronous)
- [Guide 2: Data Jobs (Synchronous)](#guide-2-data-jobs-synchronous)
- [Guide 3: Data Enrichers](#guide-3-data-enrichers)
- [Running the Application](#running-the-application)
- [Testing Your Implementation](#testing-your-implementation)
- [Next Steps](#next-steps)

---

## Prerequisites

**Required for all use cases:**
- Java 21+ installed
- Maven 3.8+ or Gradle 7+
- Spring Boot 3.x knowledge
- Reactive programming familiarity (Project Reactor)

**Additional requirements by use case:**

| Use Case | Additional Requirements |
|----------|------------------------|
| **Async Data Jobs** | Access to orchestrator (Apache Airflow, AWS Step Functions, or mock for dev) |
| **Sync Data Jobs** | None (all included in lib-common-data) |
| **Data Enrichers** | Access to third-party provider APIs + API credentials |

---

## Installation

### Step 1: Add Maven Dependency

Add the following to your `pom.xml`:

```xml
<dependencies>
    <!-- Firefly Common Data Library -->
    <dependency>
        <groupId>com.firefly</groupId>
        <artifactId>lib-common-data</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Spring Boot WebFlux (if not already included) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Configure Application Properties

Create or update `src/main/resources/application.yml`:

#### For Data Jobs

```yaml
spring:
  application:
    name: my-data-service

firefly:
  data:
    # Enable EDA integration
    eda:
      enabled: true

    # Enable CQRS integration
    cqrs:
      enabled: true

    # Configure job orchestration
    orchestration:
      enabled: true
      orchestrator-type: APACHE_AIRFLOW
      publish-job-events: true
      job-events-topic: my-service-job-events
      airflow:
        base-url: http://localhost:8080
        api-version: v1
        authentication-type: BASIC
        username: airflow
        password: airflow
        dag-id-prefix: my_service

    # Enable transactional engine (optional)
    transactional:
      enabled: false

  # Step events configuration (if using SAGAs)
  stepevents:
    enabled: true
    topic: my-service-step-events
    include-job-context: true

# Logging (JSON by default, use "plain" for development)
logging:
  format: json  # or "plain" for human-readable logs
  level:
    com.firefly: DEBUG
    reactor: INFO
```

#### For Data Enrichment

```yaml
spring:
  application:
    name: my-enrichment-service

firefly:
  data:
    # Enable data enrichment
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30

    # Resiliency configuration (optional - has sensible defaults)
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s
        rate-limiter-enabled: false

# Provider-specific configuration
financial-data:
  base-url: https://api.financial-data-provider.example
  api-key: ${FINANCIAL_DATA_API_KEY}

# Logging
logging:
  format: json
  level:
    com.firefly: DEBUG
    reactor: INFO
```

---

## Guide 1: Data Jobs (Asynchronous)

**Use this guide if you need to:**
- Execute long-running workflows (> 30 seconds)
- Integrate with orchestrators (AWS Step Functions, Apache Airflow)
- Process large datasets with multiple stages (START → CHECK → COLLECT → RESULT)

### Step 1: Configure Application

Add to `application.yml`:

```yaml
firefly:
  data:
    orchestration:
      enabled: true
      orchestrator-type: APACHE_AIRFLOW  # or AWS_STEP_FUNCTIONS
      airflow:
        base-url: http://localhost:8080
        username: airflow
        password: airflow
```

### Step 2: Create Domain Models

Define your DTOs for job results:

```java
package com.example.myservice.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CustomerDataDTO {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
}
```

### Step 3: Implement JobOrchestrator Adapter

> **Important**: The library provides the `JobOrchestrator` interface, but you must implement the adapter for your chosen orchestrator.

Create an orchestrator adapter (example for a mock/test implementation):

```java
package com.example.myservice.orchestration;

import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock implementation of JobOrchestrator for development/testing.
 * Replace this with actual Airflow or AWS Step Functions adapter in production.
 */
@Component
@Slf4j
public class MockJobOrchestrator implements JobOrchestrator {

    private final Map<String, JobExecution> executions = new ConcurrentHashMap<>();

    @Override
    public Mono<JobExecution> startJob(JobExecutionRequest request) {
        String executionId = UUID.randomUUID().toString();

        JobExecution execution = JobExecution.builder()
            .executionId(executionId)
            .jobDefinition(request.getJobDefinition())
            .status(JobExecutionStatus.RUNNING)
            .input(request.getInput())
            .startTime(Instant.now())
            .build();

        executions.put(executionId, execution);
        log.info("Mock: Started job {} with executionId {}", request.getJobDefinition(), executionId);

        return Mono.just(execution);
    }

    @Override
    public Mono<JobExecutionStatus> checkJobStatus(String executionId) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException("Execution not found: " + executionId));
        }

        // Simulate job completion after some time
        JobExecutionStatus status = execution.getStatus();
        log.info("Mock: Checking status for {} - {}", executionId, status);

        return Mono.just(status);
    }

    @Override
    public Mono<JobExecutionStatus> stopJob(String executionId, String reason) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException("Execution not found: " + executionId));
        }

        log.info("Mock: Stopping job {} - reason: {}", executionId, reason);
        return Mono.just(JobExecutionStatus.STOPPED);
    }

    @Override
    public Mono<JobExecution> getJobExecution(String executionId) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException("Execution not found: " + executionId));
        }

        // Simulate completed job with output
        JobExecution completed = execution.toBuilder()
            .status(JobExecutionStatus.SUCCEEDED)
            .endTime(Instant.now())
            .output(Map.of(
                "customer_id", "12345",
                "first_name", "John",
                "last_name", "Doe",
                "email_address", "john.doe@example.com",
                "phone", "555-1234",
                "mailing_address", "123 Main St"
            ))
            .build();

        executions.put(executionId, completed);
        log.info("Mock: Retrieved execution {}", executionId);

        return Mono.just(completed);
    }

    @Override
    public String getOrchestratorType() {
        return "MOCK";
    }
}
```

> **For Production**: Replace the mock implementation with actual adapters:
> - **Apache Airflow**: Use Airflow REST API client to trigger DAGs and check status
> - **AWS Step Functions**: Use AWS SDK to start executions and describe execution status
> - See [Configuration Guide](configuration.md) for orchestrator-specific settings

### Step 4: Create MapStruct Mapper

Create a mapper to transform raw job results to your DTO:

```java
package com.example.myservice.mapper;

import com.example.myservice.dto.CustomerDataDTO;
import com.firefly.common.data.mapper.JobResultMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface CustomerDataMapper extends JobResultMapper<Map<String, Object>, CustomerDataDTO> {

    @Override
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    @Mapping(source = "last_name", target = "lastName")
    @Mapping(source = "email_address", target = "email")
    @Mapping(source = "phone", target = "phoneNumber")
    @Mapping(source = "mailing_address", target = "address")
    CustomerDataDTO mapToTarget(Map<String, Object> source);

    @Override
    default Class<CustomerDataDTO> getTargetType() {
        return CustomerDataDTO.class;
    }
}
```

### Step 5: Implement DataJobService

> **💡 Recommended Approach**: Use `AbstractResilientDataJobService` for automatic observability, resiliency, and persistence features.
>
> See [Multiple Jobs in One Service](../data-jobs/guide.md#multiple-jobs-in-one-service) for a complete example with multiple services and controllers.

**Option A: Using AbstractResilientDataJobService (Recommended)**

```java
package com.example.myservice.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CustomerDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public CustomerDataJobService(
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
        log.debug("Starting customer data job with parameters: {}", request.getParameters());

        // Use helper method to build JobExecutionRequest with all fields
        JobExecutionRequest executionRequest = buildJobExecutionRequest(
            request,
            "customer-data-extraction"
        );

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.success(
                JobStage.START,
                execution.getExecutionId(),
                "Customer data job started successfully"
            ));
    }

    @Override
    protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
        return jobOrchestrator.checkJobStatus(request.getExecutionId())
            .map(status -> JobStageResponse.success(
                JobStage.CHECK,
                request.getExecutionId(),
                "Status: " + status
            ));
    }

    @Override
    protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
        return jobOrchestrator.getJobExecution(request.getExecutionId())
            .map(execution -> JobStageResponse.builder()
                .stage(JobStage.COLLECT)
                .executionId(execution.getExecutionId())
                .status(execution.getStatus())
                .data(execution.getOutput())
                .success(true)
                .message("Customer data collected")
                .build());
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        // Implement result transformation logic here
        return doCollectJobResults(request);
    }

    @Override
    protected String getOrchestratorType() {
        return jobOrchestrator.getOrchestratorType();
    }

    @Override
    protected String getJobDefinition() {
        return "customer-data-extraction";
    }
}
```

**Benefits**: Automatic tracing, metrics, circuit breaker, retry, rate limiting, bulkhead, audit trail, and comprehensive logging.

**Option B: Implementing DataJobService Interface (Manual approach)**

Only use this if you need full control and don't want the built-in features:

```java
package com.example.myservice.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.service.DataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@Slf4j
public class CustomerDataJobService implements DataJobService {

    private final JobOrchestrator jobOrchestrator;

    public CustomerDataJobService(JobOrchestrator jobOrchestrator) {
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    public Mono<JobStageResponse> startJob(JobStageRequest request) {
        log.info("Starting customer data job with parameters: {}", request.getParameters());

        // Build JobExecutionRequest with all fields from the request
        JobExecutionRequest executionRequest = JobExecutionRequest.builder()
            .jobDefinition("customer-data-extraction")
            .input(request.getParameters())
            .requestId(request.getRequestId())
            .initiator(request.getInitiator())
            .metadata(request.getMetadata())
            .build();

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.builder()
                .stage(JobStage.START)
                .executionId(execution.getExecutionId())
                .status(execution.getStatus())
                .success(true)
                .message("Customer data job started successfully")
                .timestamp(Instant.now())
                .build())
            .doOnSuccess(response -> log.info("Job started: {}", response.getExecutionId()))
            .doOnError(error -> log.error("Failed to start job", error));
    }

    @Override
    public Mono<JobStageResponse> checkJob(JobStageRequest request) {
        log.info("Checking job status: {}", request.getExecutionId());
        
        return jobOrchestrator.checkJobStatus(request.getExecutionId())
            .map(status -> JobStageResponse.builder()
                .stage(JobStage.CHECK)
                .executionId(request.getExecutionId())
                .status(status)
                .success(true)
                .message("Job status retrieved")
                .timestamp(Instant.now())
                .build());
    }

    @Override
    public Mono<JobStageResponse> collectJobResults(JobStageRequest request) {
        log.info("Collecting raw results for job: {}", request.getExecutionId());
        
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
        log.info("Getting final results for job: {}", request.getExecutionId());
        
        // 1. Collect raw data
        return collectJobResults(request)
            .flatMap(collectResponse -> {
                try {
                    // 2. Load target DTO class
                    Class<?> targetClass = Class.forName(request.getTargetDtoClass());
                    
                    // 3. Get appropriate mapper from registry
                    var mapper = mapperRegistry.getMapper(targetClass)
                        .orElseThrow(() -> new IllegalArgumentException(
                            "No mapper found for: " + targetClass.getSimpleName()));
                    
                    // 4. Extract raw data
                    Map<String, Object> rawData = collectResponse.getData();
                    
                    // 5. Transform using MapStruct
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

### Step 5: Implement DataJobController

> **💡 Recommended Approach**: Use `AbstractDataJobController` for automatic comprehensive logging.

**Option A: Using AbstractDataJobController (Recommended)**

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerDataJobController extends AbstractDataJobController {

    public CustomerDataJobController(DataJobService dataJobService) {
        super(dataJobService);
    }

    // That's it! All endpoints are implemented with automatic logging:
    // POST   /api/v1/jobs/start
    // GET    /api/v1/jobs/{executionId}/check
    // GET    /api/v1/jobs/{executionId}/collect
    // GET    /api/v1/jobs/{executionId}/result
}
```

**Benefits**: Automatic logging of all HTTP requests/responses with parameters, execution details, errors, and timing.

**Option B: Extending AbstractDataJobController (Recommended)**

This is the simplest approach with built-in logging:

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class CustomerDataJobController extends AbstractDataJobController {

    public CustomerDataJobController(DataJobService dataJobService) {
        super(dataJobService);
    }
}
```

---

## Guide 2: Data Jobs (Synchronous)

**Use this guide if you need to:**
- Execute quick operations (< 30 seconds)
- Return results immediately without orchestration
- Validate data, perform lookups, or run simple transformations

### Step 1: Configure Application

Add to `application.yml`:

```yaml
firefly:
  data:
    orchestration:
      enabled: true  # Enables resiliency features
```

### Step 2: Implement SyncDataJobService

Extend `AbstractResilientSyncDataJobService` for automatic observability and resiliency:

```java
package com.example.myservice.service;

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

import java.util.Map;

@Service
@Slf4j
public class CustomerValidationSyncJob extends AbstractResilientSyncDataJobService {

    private final ValidationService validationService;

    public CustomerValidationSyncJob(
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

        log.info("Validating customer: {}", customerId);

        return validationService.validateCustomer(customerId)
            .map(validationResult -> JobStageResponse.builder()
                .success(true)
                .executionId(request.getExecutionId())
                .data(Map.of("validationResult", validationResult))
                .message("Customer validation completed successfully")
                .build())
            .onErrorResume(error -> {
                log.error("Validation failed for customer: {}", customerId, error);
                return Mono.just(JobStageResponse.builder()
                    .success(false)
                    .executionId(request.getExecutionId())
                    .error("Validation failed: " + error.getMessage())
                    .message("Customer validation failed")
                    .build());
            });
    }

    @Override
    protected String getJobName() {
        return "CustomerValidationJob";
    }

    @Override
    protected String getJobDescription() {
        return "Validates customer data synchronously";
    }
}
```

### Step 3: Implement Controller

Extend `AbstractSyncDataJobController`:

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractSyncDataJobController;
import com.firefly.common.data.service.SyncDataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer-validation")
@Tag(name = "Sync Job - Customer Validation")
public class CustomerValidationController extends AbstractSyncDataJobController {

    public CustomerValidationController(SyncDataJobService syncJobService) {
        super(syncJobService);
    }

    // That's it! Endpoint is automatically exposed:
    // POST /api/v1/customer-validation/execute
}
```

### Step 4: Test Your Sync Job

```bash
curl -X POST http://localhost:8080/api/v1/customer-validation/execute \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-12345"
    },
    "requestId": "req-001",
    "initiator": "api-user"
  }'
```

**Response:**
```json
{
  "success": true,
  "message": "Customer validation completed successfully",
  "data": {
    "customerId": "CUST-12345",
    "valid": true,
    "validationErrors": []
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**See [Data Jobs — Complete Guide](../data-jobs/guide.md#quick-start-sync) for complete documentation.**

---

## Guide 3: Data Enrichers

**Use this guide if you need to:**
- Integrate with third-party data providers (credit bureaus, financial data, business intelligence)
- Enrich your application data with external information
- Expose provider-specific operations (search, validate, lookup)

### Step 1: Configure Application

Add to `application.yml`:

```yaml
firefly:
  data:
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30

# Provider-specific configuration
credit:
  bureau:
    base-url: https://api.credit-bureau-provider.com
    api-key: ${CREDIT_BUREAU_API_KEY}
```

### Step 2: Create Domain Models

Define your DTOs for enrichment:

```java
package com.example.myservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Company profile DTO - your application's format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfileDTO {
    private String companyId;
    private String name;
    private String registeredAddress;
    private String industry;
    private Double annualRevenue;
    private Integer employeeCount;
}

/**
 * Financial data provider response - provider's format.
 */
@Data
public class FinancialDataResponse {
    private String id;
    private String businessName;
    private String primaryAddress;
    private String sector;
    private Double revenue;
    private Integer totalEmployees;
}
```

### Step 3: Implement Data Enricher

> **💡 Recommended Approach**: Use `DataEnricher` for automatic strategy application and 67% less code.

```java
package com.example.myservice.enricher;

import com.firefly.common.client.ServiceClient;
import com.firefly.common.client.rest.RestClient;
import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
import com.example.myservice.dto.CompanyProfileDTO;
import com.example.myservice.dto.FinancialDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@EnricherMetadata(
    providerName = "Financial Data Provider",
    tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Your tenant UUID
    type = "company-profile",
    description = "Enriches company data with financial and corporate information",
    version = "1.0.0",
    tags = {"financial", "company-data"},
    priority = 100
)
public class FinancialDataEnricher
        extends DataEnricher<CompanyProfileDTO, FinancialDataResponse, CompanyProfileDTO> {

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

        log.info("Initialized Financial Data Enricher with base URL: {}", baseUrl);
    }

    /**
     * Step 1: Fetch data from the provider's API.
     * The framework handles tracing, metrics, circuit breaker, and retry automatically.
     */
    @Override
    protected Mono<FinancialDataResponse> fetchProviderData(EnrichmentRequest request) {
        String companyId = request.requireParam("companyId");

        log.debug("Fetching financial data for company ID: {}", companyId);

        return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
            .withPathParam("id", companyId)
            .execute()
            .doOnSuccess(response ->
                log.debug("Successfully fetched financial data for company ID: {}", companyId))
            .doOnError(error ->
                log.error("Failed to fetch financial data for company ID: {}", companyId, error));
    }

    /**
     * Step 2: Map provider data to your target DTO format.
     * The framework automatically applies the enrichment strategy (ENHANCE/MERGE/REPLACE/RAW).
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

    // No need to override getProviderName(), getSupportedEnrichmentTypes(), getEnricherDescription()
    // They are automatically read from @EnricherMetadata annotation!
}
```

**What happens automatically:**
- ✅ Distributed tracing with Micrometer
- ✅ Metrics collection (execution time, success/failure rates, data sizes)
- ✅ Circuit breaker, retry, rate limiting, and bulkhead patterns
- ✅ Automatic enrichment strategy application (ENHANCE/MERGE/REPLACE/RAW)
- ✅ Automatic response building with metadata and field counting
- ✅ Event publishing for enrichment lifecycle events
- ✅ Comprehensive logging for all enrichment phases

### Step 4: Add Provider-Specific Custom Operations (Optional but Recommended)

Many providers require auxiliary operations before enrichment (search for IDs, validate identifiers, etc.). Create operation classes with `@EnricherOperation` annotation:

```java
package com.example.myservice.enricher.operation;

import com.firefly.common.data.operation.AbstractEnricherOperation;
import com.firefly.common.data.operation.EnricherOperation;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

// Step 1: Define DTOs
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
@EnricherOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.POST,
    tags = {"lookup", "search"}
)
public class SearchCompanyOperation
        extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> {

    private final RestClient bureauClient;

    public SearchCompanyOperation(RestClient bureauClient) {
        this.bureauClient = bureauClient;
    }

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        return bureauClient.post("/search", CompanySearchResponse.class)
            .withBody(request)
            .execute();
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.companyName() == null && request.taxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
    }
}

// Step 3: Register in enricher
@Service
public class CreditBureauEnricher
        extends DataEnricher<CreditReportDTO, CreditBureauReportResponse, CreditReportDTO> {

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
- ✅ **REST Endpoints** - `POST /api/v1/enrichment/credit-bureau/operation/search-company`
- ✅ **JSON Schema Generation** - Request/response schemas auto-generated from DTOs
- ✅ **Type Safety** - Compile-time type checking
- ✅ **Validation** - Automatic request validation
- ✅ **Discovery** - `GET /api/v1/enrichment/credit-bureau/operations` lists all operations with schemas
- ✅ **OpenAPI Docs** - Full Swagger documentation

**Typical Workflow:**
```bash
# Step 1: Search for company to get provider's internal ID
POST /api/v1/enrichment/credit-bureau/operation/search-company
{
  "companyName": "Acme Corp",
  "taxId": "TAX-123",
  "minConfidence": 0.8
}
→ Returns: {"providerId": "PROV-12345", "companyName": "ACME CORP", "taxId": "TAX-123", "confidence": 0.95}

# Step 2: Enrich data using the provider ID
POST /api/v1/enrichment/credit-bureau/enrich
{
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "sourceDto": {"companyId": "123", "name": "Acme Corp"},
  "parameters": {"providerId": "PROV-12345"}
}
```

### Step 5: That's It! No Controller Needed

> **✨ NEW SIMPLIFIED ARCHITECTURE**: Your enricher is automatically available through global endpoints.

**Your enricher is now accessible via:**

```bash
# Smart Enrichment Endpoint (automatic routing)
POST /api/v1/enrichment/smart
Content-Type: application/json

{
  "type": "company-profile",
  "tenantId": "550e8400-e29b-41d4-a716-446655440001",
  "source": {
    "companyId": "123",
    "name": "Acme Corp"
  },
  "params": {
    "includeFinancials": true
  },
  "strategy": "ENHANCE"
}

# Discovery Endpoint
GET /api/v1/enrichment/providers?type=company-profile

# Global Health Endpoint
GET /api/v1/enrichment/health?type=company-profile
```

**What you get automatically:**
- ✅ **Smart routing** - Automatic selection by type + tenant + priority
- ✅ **Discovery** - List all available enrichers
- ✅ **Health checks** - Global health endpoint
- ✅ **Multi-tenancy** - Tenant-aware routing
- ✅ **Comprehensive logging** - All requests/responses logged
- ✅ **Observability** - Tracing, metrics automatically
- ✅ **Resiliency** - Circuit breaker, retry automatically

> **📖 See [Simplified Architecture Guide](../data-enrichers/SIMPLIFIED-ARCHITECTURE.md)** for complete details.

---

## Complete Example - Data Jobs

### Project Structure

```
my-data-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/myservice/
    │   │       ├── MyDataServiceApplication.java
    │   │       ├── controller/
    │   │       │   └── CustomerDataJobController.java
    │   │       ├── service/
    │   │       │   └── CustomerDataJobService.java
    │   │       ├── mapper/
    │   │       │   └── CustomerDataMapper.java
    │   │       └── dto/
    │   │           └── CustomerDataDTO.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/
            └── com/example/myservice/
                └── service/
                    └── CustomerDataJobServiceTest.java
```

### Main Application Class

```java
package com.example.myservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyDataServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyDataServiceApplication.class, args);
    }
}
```

---

## Complete Example - Data Enrichment

### Project Structure

```
my-enrichment-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myservice/
│   │   │       ├── MyEnrichmentServiceApplication.java
│   │   │       ├── dto/
│   │   │       │   ├── CompanyProfileDTO.java
│   │   │       │   └── FinancialDataResponse.java
│   │   │       ├── enricher/
│   │   │       │   └── FinancialDataEnricher.java
│   │   │       └── controller/
│   │   │           └── FinancialDataCompanyController.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/example/myservice/
│               └── enricher/
│                   └── FinancialDataEnricherTest.java
└── pom.xml
```

### Full Application Class

```java
package com.example.myservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyEnrichmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyEnrichmentServiceApplication.class, args);
    }
}
```

### Complete application.yml

```yaml
spring:
  application:
    name: my-enrichment-service

server:
  port: 8080

firefly:
  data:
    # Enable data enrichment
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30

    # Resiliency configuration (applies to both jobs and enrichment)
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s
        rate-limiter-enabled: false

# Provider-specific configuration
financial-data:
  base-url: https://api.financial-data-provider.example
  api-key: ${FINANCIAL_DATA_API_KEY}

# Logging
logging:
  format: json
  level:
    com.firefly: DEBUG
    com.example.myservice: DEBUG
    reactor: INFO
```

### Example Usage

#### 1. Discover Available Providers

```bash
curl -X GET "http://localhost:8080/api/v1/enrichment/providers"
```

Response:
```json
{
  "providers": [
    {
      "providerName": "Financial Data Provider",
      "type": "company-profile",
      "description": "Enriches company data with financial and corporate information",
      "endpoints": [
        "/api/v1/enrichment/financial-data-company/enrich"
      ],
      "operations": null
    }
  ]
}
```

#### 2. Enrich Company Data

```bash
curl -X POST "http://localhost:8080/api/v1/enrichment/financial-data-company/enrich" \
  -H "Content-Type: application/json" \
  -d '{
    "enrichmentType": "company-profile",
    "strategy": "ENHANCE",
    "sourceDto": {
      "companyId": "12345",
      "name": "Acme Corp"
    },
    "parameters": {
      "companyId": "12345"
    },
    "requestId": "req-001"
  }'
```

Response:
```json
{
  "success": true,
  "enrichedData": {
    "companyId": "12345",
    "name": "Acme Corp",
    "registeredAddress": "123 Main St, New York, NY 10001",
    "industry": "Technology",
    "annualRevenue": 5000000.0,
    "employeeCount": 50
  },
  "providerName": "Financial Data Provider",
  "enrichmentType": "company-profile",
  "strategy": "ENHANCE",
  "message": "Enrichment successful",
  "fieldsEnriched": 4,
  "requestId": "req-001"
}
```

#### 3. Check Health

```bash
curl -X GET "http://localhost:8080/api/v1/enrichment/financial-data-company/health"
```

Response:
```json
{
  "status": "UP",
  "providerName": "Financial Data Provider",
  "type": "company-profile"
}
```

---

## Running the Application

### 1. Start the Application

```bash
mvn spring-boot:run
```

### 2. Test the Endpoints

#### Start a Job

```bash
curl -X POST http://localhost:8080/api/v1/jobs/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "12345",
      "includeHistory": true
    },
    "requestId": "req-001",
    "initiator": "user@example.com"
  }'
```

Response:
```json
{
  "stage": "START",
  "executionId": "exec-abc123",
  "status": "RUNNING",
  "success": true,
  "message": "Customer data job started successfully",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

#### Check Job Status

```bash
curl http://localhost:8080/api/v1/jobs/exec-abc123/check?requestId=req-001
```

#### Collect Raw Results

```bash
curl http://localhost:8080/api/v1/jobs/exec-abc123/collect?requestId=req-001
```

#### Get Final Results (Mapped)

```bash
curl http://localhost:8080/api/v1/jobs/exec-abc123/result?requestId=req-001&targetDtoClass=com.example.myservice.dto.CustomerDataDTO
```

---

## Testing Your Implementation

Create a test class:

```java
package com.example.myservice.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class CustomerDataJobServiceTest {

    @MockBean
    private JobOrchestrator jobOrchestrator;
    
    @Autowired
    private CustomerDataJobService service;
    
    @Test
    void shouldStartJobSuccessfully() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .parameters(Map.of("customerId", "12345"))
            .build();
            
        JobExecution execution = JobExecution.builder()
            .executionId("exec-123")
            .status(JobExecutionStatus.RUNNING)
            .build();
            
        when(jobOrchestrator.startJob(any()))
            .thenReturn(Mono.just(execution));
            
        // When & Then
        StepVerifier.create(service.startJob(request))
            .assertNext(response -> {
                assert response.isSuccess();
                assert response.getExecutionId().equals("exec-123");
                assert response.getStage() == JobStage.START;
            })
            .verifyComplete();
    }
}
```

Run tests:
```bash
mvn test
```

---

## Next Steps

Now that you have a basic implementation:

1. **Explore Advanced Features**
   - [Synchronous Jobs](../data-jobs/guide.md#quick-start-sync) for quick operations (< 30 seconds)
   - [SAGA Integration](../data-jobs/guide.md#saga-and-step-events) for distributed transactions
   - [Custom Mappers](mappers.md) for complex transformations
   - [Event Publishing](../README.md#event-publishing) for observability

2. **Production Readiness**
   - Add error handling and retry logic
   - Implement monitoring and metrics
   - Configure production orchestrator (Apache Airflow or AWS Step Functions)
   - Set up proper logging and tracing
   - Enable resiliency patterns (circuit breaker, retry)

3. **Learn More**
   - [Architecture](architecture.md) - Understand the design
   - [Configuration](configuration.md) - All configuration options
   - [Examples](examples.md) - Real-world patterns
   - [API Reference](api-reference.md) - Complete API docs

---

**Congratulations!** 🎉 You've successfully set up a data processing microservice using `lib-common-data`.

