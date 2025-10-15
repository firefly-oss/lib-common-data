# Multiple Data Jobs and Controllers Example

This guide demonstrates how to build a complete microservice with multiple data job types and controllers using the abstract base classes.

## Overview

This example shows a **Customer Data Service** that handles three different types of data jobs:
1. **Customer Profile Extraction** - Extract customer profile data
2. **Customer Orders Extraction** - Extract customer order history
3. **Customer Analytics** - Generate customer analytics reports

Each job type has its own service and controller, all using the recommended abstract base classes.

---

## Project Structure

```
customer-data-service/
├── src/main/java/com/example/customer/
│   ├── CustomerDataServiceApplication.java
│   ├── config/
│   │   └── OrchestratorConfig.java
│   ├── controller/
│   │   ├── CustomerProfileController.java
│   │   ├── CustomerOrdersController.java
│   │   └── CustomerAnalyticsController.java
│   ├── service/
│   │   ├── CustomerProfileJobService.java
│   │   ├── CustomerOrdersJobService.java
│   │   └── CustomerAnalyticsJobService.java
│   ├── dto/
│   │   ├── CustomerProfileDTO.java
│   │   ├── CustomerOrderDTO.java
│   │   └── CustomerAnalyticsDTO.java
│   └── mapper/
│       ├── CustomerProfileMapper.java
│       ├── CustomerOrderMapper.java
│       └── CustomerAnalyticsMapper.java
└── src/main/resources/
    └── application.yml
```

---

## 1. Application Configuration

**application.yml**

```yaml
spring:
  application:
    name: customer-data-service

server:
  port: 8080

firefly:
  data:
    eda:
      enabled: true
    cqrs:
      enabled: true
    orchestration:
      enabled: true
      orchestrator-type: AWS_STEP_FUNCTIONS
      publish-job-events: true
      job-events-topic: customer-job-events
      aws-step-functions:
        region: us-east-1
        state-machine-arn: arn:aws:states:us-east-1:123456789012:stateMachine:CustomerDataStateMachine
    transactional:
      enabled: true

  eda:
    publishers:
      - id: default
        type: KAFKA
        connection-id: kafka-default
    connections:
      kafka:
        - id: kafka-default
          bootstrap-servers: localhost:9092

# JSON logging by default (can be changed to plain for development)
logging:
  format: json  # Use "plain" for development
  level:
    com.example.customer: INFO
    com.firefly: INFO
```

---

## 2. DTOs

**CustomerProfileDTO.java**

```java
package com.example.customer.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CustomerProfileDTO {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
}
```

**CustomerOrderDTO.java**

```java
package com.example.customer.dto;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CustomerOrderDTO {
    private String customerId;
    private List<Order> orders;
    private Integer totalOrders;
    private Double totalSpent;
    
    @Data
    @Builder
    public static class Order {
        private String orderId;
        private Instant orderDate;
        private Double amount;
        private String status;
    }
}
```

**CustomerAnalyticsDTO.java**

```java
package com.example.customer.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CustomerAnalyticsDTO {
    private String customerId;
    private Double lifetimeValue;
    private Integer purchaseFrequency;
    private String segment;
    private Double churnProbability;
}
```

---

## 3. Services (Using AbstractResilientDataJobService)

**CustomerProfileJobService.java**

```java
package com.example.customer.service;

import com.example.customer.dto.CustomerProfileDTO;
import com.firefly.common.data.model.*;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@Slf4j
public class CustomerProfileJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public CustomerProfileJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobOrchestrator jobOrchestrator) {
        super(tracingService, metricsService, resiliencyService);
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        log.debug("Starting customer profile extraction for customer: {}", 
                request.getParameters().get("customerId"));

        JobExecutionRequest executionRequest = JobExecutionRequest.builder()
                .jobDefinition("customer-profile-extraction")
                .input(request.getParameters())
                .build();

        return jobOrchestrator.startJob(executionRequest)
                .map(execution -> JobStageResponse.success(
                        JobStage.START,
                        execution.getExecutionId(),
                        "Customer profile extraction started"
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
                        .message("Profile data collected")
                        .build());
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        // In a real implementation, you would use MapStruct mappers here
        return doCollectJobResults(request);
    }

    @Override
    protected String getOrchestratorType() {
        return jobOrchestrator.getOrchestratorType();
    }

    @Override
    protected String getJobDefinition() {
        return "customer-profile-extraction";
    }
}
```

**CustomerOrdersJobService.java**

```java
package com.example.customer.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CustomerOrdersJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public CustomerOrdersJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobOrchestrator jobOrchestrator) {
        super(tracingService, metricsService, resiliencyService);
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        log.debug("Starting customer orders extraction");

        JobExecutionRequest executionRequest = JobExecutionRequest.builder()
                .jobDefinition("customer-orders-extraction")
                .input(request.getParameters())
                .build();

        return jobOrchestrator.startJob(executionRequest)
                .map(execution -> JobStageResponse.success(
                        JobStage.START,
                        execution.getExecutionId(),
                        "Customer orders extraction started"
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
                        .message("Orders data collected")
                        .build());
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        return doCollectJobResults(request);
    }

    @Override
    protected String getOrchestratorType() {
        return jobOrchestrator.getOrchestratorType();
    }

    @Override
    protected String getJobDefinition() {
        return "customer-orders-extraction";
    }
}
```

**CustomerAnalyticsJobService.java**

```java
package com.example.customer.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CustomerAnalyticsJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public CustomerAnalyticsJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobOrchestrator jobOrchestrator) {
        super(tracingService, metricsService, resiliencyService);
        this.jobOrchestrator = jobOrchestrator;
    }

    @Override
    protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
        log.debug("Starting customer analytics generation");

        JobExecutionRequest executionRequest = JobExecutionRequest.builder()
                .jobDefinition("customer-analytics-generation")
                .input(request.getParameters())
                .build();

        return jobOrchestrator.startJob(executionRequest)
                .map(execution -> JobStageResponse.success(
                        JobStage.START,
                        execution.getExecutionId(),
                        "Customer analytics generation started"
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
                        .message("Analytics data collected")
                        .build());
    }

    @Override
    protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
        return doCollectJobResults(request);
    }

    @Override
    protected String getOrchestratorType() {
        return jobOrchestrator.getOrchestratorType();
    }

    @Override
    protected String getJobDefinition() {
        return "customer-analytics-generation";
    }
}
```

---

## 4. Controllers (Using AbstractDataJobController)

**CustomerProfileController.java**

```java
package com.example.customer.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/profile")
public class CustomerProfileController extends AbstractDataJobController {

    public CustomerProfileController(
            @Qualifier("customerProfileJobService") DataJobService dataJobService) {
        super(dataJobService);
    }

    // All endpoints are automatically implemented with comprehensive logging:
    // POST   /api/v1/customer/profile/start
    // GET    /api/v1/customer/profile/{executionId}/check
    // GET    /api/v1/customer/profile/{executionId}/collect
    // GET    /api/v1/customer/profile/{executionId}/result
}
```

**CustomerOrdersController.java**

```java
package com.example.customer.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/orders")
public class CustomerOrdersController extends AbstractDataJobController {

    public CustomerOrdersController(
            @Qualifier("customerOrdersJobService") DataJobService dataJobService) {
        super(dataJobService);
    }

    // All endpoints are automatically implemented with comprehensive logging:
    // POST   /api/v1/customer/orders/start
    // GET    /api/v1/customer/orders/{executionId}/check
    // GET    /api/v1/customer/orders/{executionId}/collect
    // GET    /api/v1/customer/orders/{executionId}/result
}
```

**CustomerAnalyticsController.java**

```java
package com.example.customer.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/analytics")
public class CustomerAnalyticsController extends AbstractDataJobController {

    public CustomerAnalyticsController(
            @Qualifier("customerAnalyticsJobService") DataJobService dataJobService) {
        super(dataJobService);
    }

    // All endpoints are automatically implemented with comprehensive logging:
    // POST   /api/v1/customer/analytics/start
    // GET    /api/v1/customer/analytics/{executionId}/check
    // GET    /api/v1/customer/analytics/{executionId}/collect
    // GET    /api/v1/customer/analytics/{executionId}/result
}
```

---

## 5. Main Application

**CustomerDataServiceApplication.java**

```java
package com.example.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomerDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerDataServiceApplication.class, args);
    }
}
```

---

## 6. Usage Examples

### Start Customer Profile Extraction

```bash
curl -X POST http://localhost:8080/api/v1/customer/profile/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-12345"
    }
  }'
```

**Response:**
```json
{
  "stage": "START",
  "executionId": "exec-abc123",
  "status": "RUNNING",
  "success": true,
  "message": "Customer profile extraction started",
  "timestamp": "2025-10-15T10:30:00Z"
}
```

### Check Job Status

```bash
curl http://localhost:8080/api/v1/customer/profile/exec-abc123/check
```

### Start Customer Orders Extraction

```bash
curl -X POST http://localhost:8080/api/v1/customer/orders/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-12345",
      "startDate": "2024-01-01",
      "endDate": "2024-12-31"
    }
  }'
```

### Start Customer Analytics Generation

```bash
curl -X POST http://localhost:8080/api/v1/customer/analytics/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-12345",
      "analysisType": "lifetime-value"
    }
  }'
```

---

## 7. Key Benefits of This Approach

### ✅ Minimal Boilerplate Code

Each controller is just **5 lines of code** (excluding comments):
```java
@RestController
@RequestMapping("/api/v1/customer/profile")
public class CustomerProfileController extends AbstractDataJobController {
    public CustomerProfileController(@Qualifier("customerProfileJobService") DataJobService dataJobService) {
        super(dataJobService);
    }
}
```

### ✅ Automatic Features for All Jobs

Every job automatically gets:
- **Distributed Tracing**: Full trace/span IDs in logs and metrics
- **Metrics**: Execution time, success/failure rates, data sizes
- **Resiliency**: Circuit breaker, retry, rate limiting, bulkhead
- **Logging**: Comprehensive JSON logging (or plain text for dev)
- **Audit Trail**: Automatic persistence of all operations
- **Health Checks**: Orchestrator availability monitoring

### ✅ Consistent API Across All Jobs

All jobs expose the same standard endpoints:
- `POST /api/v1/{job-type}/start` - Start a job
- `GET /api/v1/{job-type}/{executionId}/check` - Check status
- `GET /api/v1/{job-type}/{executionId}/collect` - Collect raw results
- `GET /api/v1/{job-type}/{executionId}/result` - Get final results

### ✅ Easy to Add New Jobs

To add a new job type:
1. Create a new service extending `AbstractResilientDataJobService`
2. Create a new controller extending `AbstractDataJobController`
3. That's it! All features are automatically included.

### ✅ JSON Logging by Default

All logs are in JSON format for easy parsing by log aggregation tools (ELK, Splunk, etc.):

```json
{
  "timestamp": "2025-10-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.example.customer.controller.CustomerProfileController",
  "message": "Received START job request - parameters: [customerId]",
  "application": "customer-data-service",
  "traceId": "abc123def456",
  "spanId": "789ghi012",
  "executionId": "exec-abc123",
  "stage": "START"
}
```

To use plain text logging for development, set in `application.yml`:
```yaml
logging:
  format: plain
```

---

## 8. Testing

All services and controllers can be easily tested:

```java
@SpringBootTest
class CustomerProfileJobServiceTest {

    @MockBean
    private JobOrchestrator jobOrchestrator;

    @Autowired
    private CustomerProfileJobService service;

    @Test
    void shouldStartProfileExtractionSuccessfully() {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .parameters(Map.of("customerId", "CUST-12345"))
            .build();

        JobExecution execution = JobExecution.builder()
            .executionId("exec-123")
            .status(JobExecutionStatus.RUNNING)
            .build();

        when(jobOrchestrator.startJob(any()))
            .thenReturn(Mono.just(execution));

        StepVerifier.create(service.startJob(request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getExecutionId()).isEqualTo("exec-123");
            })
            .verifyComplete();
    }
}
```

---

## Summary

This example demonstrates:
- ✅ Multiple data job types in a single microservice
- ✅ Using `AbstractResilientDataJobService` for all services
- ✅ Using `AbstractDataJobController` for all controllers
- ✅ Minimal boilerplate code
- ✅ Automatic observability, resiliency, and persistence
- ✅ JSON logging by default (configurable to plain text)
- ✅ Consistent API across all job types
- ✅ Easy to test and maintain

For more details, see:
- [Getting Started Guide](getting-started.md)
- [Observability Documentation](observability.md)
- [Resiliency Documentation](resiliency.md)
- [Logging Documentation](logging.md)


