# Getting Started

This guide will walk you through setting up and using the `lib-common-data` library in your microservice for both **Data Jobs** (orchestrated workflows) and **Data Enrichment** (third-party provider integration).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Basic Setup - Data Jobs](#basic-setup---data-jobs)
- [Basic Setup - Data Enrichment](#basic-setup---data-enrichment)
- [Complete Example - Data Jobs](#complete-example---data-jobs)
- [Complete Example - Data Enrichment](#complete-example---data-enrichment)
- [Running the Application](#running-the-application)
- [Testing Your Implementation](#testing-your-implementation)
- [Next Steps](#next-steps)

---

## Prerequisites

Before you begin, ensure you have:

- **Java 17+** installed
- **Maven 3.8+** or Gradle 7+
- **Spring Boot 3.x** knowledge
- **Reactive programming** familiarity (Project Reactor)

### For Data Jobs

- Access to an **orchestrator** (Apache Airflow, AWS Step Functions, or mock for development)
- **Kafka** or **RabbitMQ** (optional, for event publishing)
- **Apache Airflow** instance (if using Airflow orchestrator)
- **AWS credentials** (if using AWS Step Functions orchestrator)

### For Data Enrichment

- Access to **third-party provider APIs** (financial data, credit bureaus, etc.)
- **API credentials** for the providers you want to integrate
- **lib-common-client** dependency (automatically included with lib-common-data)

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

## Basic Setup - Data Jobs

### Step 1: Create Domain Models

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

### Step 2: Implement JobOrchestrator Adapter

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

### Step 3: Create MapStruct Mapper

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

### Step 4: Implement DataJobService

> **💡 Recommended Approach**: Use `AbstractResilientDataJobService` for automatic observability, resiliency, and persistence features.
>
> See the [Multiple Jobs Example](multiple-jobs-example.md) for a complete example with multiple services and controllers.

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
            JobOrchestrator jobOrchestrator) {
        super(tracingService, metricsService, resiliencyService);
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

## Basic Setup - Data Enrichment

### Step 1: Create Domain Models

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

### Step 2: Implement Data Enricher

> **💡 Recommended Approach**: Use `TypedDataEnricher` for automatic strategy application and 67% less code.

```java
package com.example.myservice.enricher;

import com.firefly.common.client.ServiceClient;
import com.firefly.common.client.rest.RestClient;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.TypedDataEnricher;
import com.example.myservice.dto.CompanyProfileDTO;
import com.example.myservice.dto.FinancialDataResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
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

**What happens automatically:**
- ✅ Distributed tracing with Micrometer
- ✅ Metrics collection (execution time, success/failure rates, data sizes)
- ✅ Circuit breaker, retry, rate limiting, and bulkhead patterns
- ✅ Automatic enrichment strategy application (ENHANCE/MERGE/REPLACE/RAW)
- ✅ Automatic response building with metadata and field counting
- ✅ Event publishing for enrichment lifecycle events
- ✅ Comprehensive logging for all enrichment phases

### Step 3: Implement Data Enricher Controller

> **💡 Recommended Approach**: Use `AbstractDataEnricherController` for automatic comprehensive logging.

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataEnricherController;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
```

**Benefits**: Automatic logging of all HTTP requests/responses with parameters, enrichment details, errors, and timing.

**URL Pattern**: `/api/v1/enrichment/{provider}-{region}-{type}`
- **provider**: financial-data
- **region**: (optional, e.g., spain, usa)
- **type**: company

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
      "supportedTypes": ["company-profile", "company-financials"],
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
  "supportedTypes": ["company-profile", "company-financials"]
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
   - [Synchronous Jobs](sync-jobs.md) for quick operations (< 30 seconds)
   - [SAGA Integration](saga-integration.md) for distributed transactions
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

