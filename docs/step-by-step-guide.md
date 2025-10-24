# Step-by-Step Guide: Building a Microservice with lib-common-data

This comprehensive guide walks you through creating a complete microservice with multiple data jobs and orchestrators.

## Table of Contents

1. [Project Setup](#1-project-setup)
2. [Configuration](#2-configuration)
3. [Creating Job Orchestrators](#3-creating-job-orchestrators)
4. [Creating Data Job Services](#4-creating-data-job-services)
5. [Creating Controllers](#5-creating-controllers)
6. [Running and Testing](#6-running-and-testing)
7. [Advanced: Multiple Orchestrators](#7-advanced-multiple-orchestrators)

---

## 1. Project Setup

### Step 1.1: Create Spring Boot Project

Create a new Spring Boot 3.x project with the following structure:

```
my-data-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/myservice/
    │   │       └── MyDataServiceApplication.java
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/
```

### Step 1.2: Add Dependencies

**pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.2</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-data-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Firefly Common Data Library -->
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>lib-common-data</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>

        <!-- Spring Boot WebFlux (already included in lib-common-data) -->
        <!-- No need to add separately -->

        <!-- Lombok (optional, for reducing boilerplate) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- MapStruct for result transformation -->
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
            <version>1.5.5.Final</version>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
            <version>1.5.5.Final</version>
            <scope>provided</scope>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 1.3: Create Main Application Class

**MyDataServiceApplication.java**

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

## 2. Configuration

### Step 2.1: Basic Configuration

**src/main/resources/application.yml**

```yaml
spring:
  application:
    name: my-data-service

server:
  port: 8080

# Firefly Data Configuration
firefly:
  data:
    # Enable EDA integration
    eda:
      enabled: true

    # Enable CQRS integration
    cqrs:
      enabled: true

    # Job orchestration configuration
    orchestration:
      enabled: true
      orchestrator-type: MOCK  # Start with MOCK for development
      publish-job-events: true
      job-events-topic: my-service-job-events

      # Observability settings
      observability:
        tracing-enabled: true
        metrics-enabled: true
        metric-prefix: myservice.jobs

      # Health check settings
      health-check:
        enabled: true
        timeout: 5s

      # Resiliency settings
      resiliency:
        circuit-breaker-enabled: true
        retry-enabled: true
        rate-limiter-enabled: true
        bulkhead-enabled: true

    # Transactional engine (optional)
    transactional:
      enabled: false

# EDA Configuration (if using Kafka - optional)
# Uncomment and configure if you need event publishing
#  eda:
#    publishers:
#      - id: default
#        type: KAFKA
#        connection-id: kafka-default
#    connections:
#      kafka:
#        - id: kafka-default
#          bootstrap-servers: localhost:9092

# Disable health checks for unused messaging systems
management:
  health:
    rabbit:
      enabled: false  # Disable RabbitMQ health check
    kafka:
      enabled: false  # Enable only if using Kafka
    mongo:
      enabled: false
    redis:
      enabled: false
  endpoint:
    health:
      show-details: always
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

# Logging Configuration
logging:
  format: json  # Use "plain" for development
  level:
    com.example.myservice: DEBUG
    com.firefly: INFO
    reactor: INFO

# Resilience4j Configuration (optional customization)
resilience4j:
  circuitbreaker:
    instances:
      jobOrchestrator:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
  retry:
    instances:
      jobOrchestrator:
        max-attempts: 3
        wait-duration: 2s
  ratelimiter:
    instances:
      jobOrchestrator:
        limit-for-period: 10
        limit-refresh-period: 1s
  bulkhead:
    instances:
      jobOrchestrator:
        max-concurrent-calls: 5
```

### Step 2.2: Development vs Production Profiles

**application-dev.yml** (for development)

```yaml
logging:
  format: plain  # Human-readable logs for development
  level:
    com.example.myservice: DEBUG
    com.firefly: DEBUG

firefly:
  data:
    orchestration:
      orchestrator-type: MOCK  # Use mock orchestrator
```

**application-prod.yml** (for production)

```yaml
logging:
  format: json  # JSON logs for production
  level:
    com.example.myservice: INFO
    com.firefly: INFO

firefly:
  data:
    orchestration:
      orchestrator-type: AWS_STEP_FUNCTIONS  # Use real orchestrator
      aws-step-functions:
        region: ${AWS_REGION}
        state-machine-arn: ${STATE_MACHINE_ARN}
```

---

## 3. Creating Job Orchestrators

### Step 3.1: Understanding the JobOrchestrator Interface

The library provides the `JobOrchestrator` interface, but **you must implement** the adapter for your chosen orchestrator.

### Step 3.2: Create a Mock Orchestrator (for Development)

**MockJobOrchestrator.java**

```java
package com.example.myservice.orchestration;

import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(
    prefix = "firefly.data.orchestration",
    name = "orchestrator-type",
    havingValue = "MOCK"
)
@Slf4j
public class MockJobOrchestrator implements JobOrchestrator {

    private final Map<String, JobExecution> executions = new ConcurrentHashMap<>();

    @Override
    public Mono<JobExecution> startJob(JobExecutionRequest request) {
        String executionId = "exec-" + UUID.randomUUID().toString();

        JobExecution execution = JobExecution.builder()
            .executionId(executionId)
            .jobDefinition(request.getJobDefinition())
            .status(JobExecutionStatus.RUNNING)
            .input(request.getInput())
            .startTime(Instant.now())
            .build();

        executions.put(executionId, execution);
        log.info("MOCK: Started job '{}' with executionId '{}'",
                request.getJobDefinition(), executionId);

        return Mono.just(execution);
    }

    @Override
    public Mono<JobExecutionStatus> checkJobStatus(String executionId) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException(
                "Execution not found: " + executionId));
        }

        log.info("MOCK: Checking status for '{}' - {}",
                executionId, execution.getStatus());
        return Mono.just(execution.getStatus());
    }

    @Override
    public Mono<JobExecution> getJobExecution(String executionId) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException(
                "Execution not found: " + executionId));
        }

        // Simulate completed job with mock output
        JobExecution completed = execution.toBuilder()
            .status(JobExecutionStatus.SUCCEEDED)
            .endTime(Instant.now())
            .output(generateMockOutput(execution.getJobDefinition()))
            .build();

        executions.put(executionId, completed);
        log.info("MOCK: Retrieved execution '{}'", executionId);

        return Mono.just(completed);
    }

    @Override
    public Mono<JobExecutionStatus> stopJob(String executionId, String reason) {
        JobExecution execution = executions.get(executionId);
        if (execution == null) {
            return Mono.error(new IllegalArgumentException(
                "Execution not found: " + executionId));
        }

        log.info("MOCK: Stopping job '{}' - reason: {}", executionId, reason);
        return Mono.just(JobExecutionStatus.STOPPED);
    }

    @Override
    public String getOrchestratorType() {
        return "MOCK";
    }

    private Map<String, Object> generateMockOutput(String jobDefinition) {
        // Generate mock data based on job type
        // In a real implementation, this would come from the actual workflow execution
        return switch (jobDefinition) {
            case "customer-data-extraction" -> Map.of(
                "customer_id", "CUST-12345",
                "first_name", "John",
                "last_name", "Doe",
                "email_address", "john.doe@example.com",
                "phone", "+1-555-0100",
                "mailing_address", "123 Main St, Springfield, IL 62701"
            );
            case "order-data-extraction" -> Map.of(
                "order_id", "ORD-98765",
                "customer_id", "CUST-12345",
                "order_date", Instant.now().toString(),
                "total_amount", 299.99,
                "status", "completed"
            );
            case "analytics-data-extraction" -> Map.of(
                "customer_id", "CUST-12345",
                "lifetime_value", 5432.10,
                "total_orders", 42,
                "average_order_value", 129.34,
                "last_order_date", Instant.now().toString()
            );
            default -> Map.of(
                "jobDefinition", jobDefinition,
                "status", "completed",
                "timestamp", Instant.now().toString(),
                "message", "Mock output for " + jobDefinition
            );
        };
    }
}
```

### Step 3.3: Create an AWS Step Functions Orchestrator (for Production)

**AwsStepFunctionsOrchestrator.java**

```java
package com.example.myservice.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sfn.SfnAsyncClient;
import software.amazon.awssdk.services.sfn.model.*;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(
    prefix = "firefly.data.orchestration",
    name = "orchestrator-type",
    havingValue = "AWS_STEP_FUNCTIONS"
)
@Slf4j
public class AwsStepFunctionsOrchestrator implements JobOrchestrator {

    private final SfnAsyncClient sfnClient;
    private final JobOrchestrationProperties properties;
    private final ObjectMapper objectMapper;

    public AwsStepFunctionsOrchestrator(
            SfnAsyncClient sfnClient,
            JobOrchestrationProperties properties,
            ObjectMapper objectMapper) {
        this.sfnClient = sfnClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<JobExecution> startJob(JobExecutionRequest request) {
        return Mono.fromFuture(() -> {
            try {
                String inputJson = objectMapper.writeValueAsString(request.getInput());

                StartExecutionRequest sfnRequest = StartExecutionRequest.builder()
                    .stateMachineArn(properties.getAwsStepFunctions().getStateMachineArn())
                    .input(inputJson)
                    .name(request.getJobDefinition() + "-" + System.currentTimeMillis())
                    .build();

                return sfnClient.startExecution(sfnRequest);
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Step Functions execution", e);
            }
        }).map(response -> JobExecution.builder()
            .executionId(response.executionArn())
            .jobDefinition(request.getJobDefinition())
            .status(JobExecutionStatus.RUNNING)
            .input(request.getInput())
            .startTime(Instant.now())
            .build())
        .doOnSuccess(exec -> log.info("Started AWS Step Functions execution: {}",
                exec.getExecutionId()));
    }

    @Override
    public Mono<JobExecutionStatus> checkJobStatus(String executionId) {
        return Mono.fromFuture(() -> {
            DescribeExecutionRequest request = DescribeExecutionRequest.builder()
                .executionArn(executionId)
                .build();
            return sfnClient.describeExecution(request);
        }).map(response -> mapStatus(response.status()));
    }

    @Override
    public Mono<JobExecution> getJobExecution(String executionId) {
        return Mono.fromFuture(() -> {
            DescribeExecutionRequest request = DescribeExecutionRequest.builder()
                .executionArn(executionId)
                .build();
            return sfnClient.describeExecution(request);
        }).map(response -> {
            try {
                Map<String, Object> output = response.output() != null
                    ? objectMapper.readValue(response.output(), Map.class)
                    : Map.of();

                return JobExecution.builder()
                    .executionId(executionId)
                    .status(mapStatus(response.status()))
                    .startTime(response.startDate())
                    .endTime(response.stopDate())
                    .output(output)
                    .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse execution output", e);
            }
        });
    }

    @Override
    public Mono<JobExecutionStatus> stopJob(String executionId, String reason) {
        return Mono.fromFuture(() -> {
            StopExecutionRequest request = StopExecutionRequest.builder()
                .executionArn(executionId)
                .cause(reason)
                .build();
            return sfnClient.stopExecution(request);
        }).map(response -> JobExecutionStatus.STOPPED);
    }

    @Override
    public String getOrchestratorType() {
        return "AWS_STEP_FUNCTIONS";
    }

    private JobExecutionStatus mapStatus(ExecutionStatus status) {
        return switch (status) {
            case RUNNING -> JobExecutionStatus.RUNNING;
            case SUCCEEDED -> JobExecutionStatus.SUCCEEDED;
            case FAILED -> JobExecutionStatus.FAILED;
            case TIMED_OUT -> JobExecutionStatus.TIMED_OUT;
            case ABORTED -> JobExecutionStatus.STOPPED;
            default -> JobExecutionStatus.UNKNOWN;
        };
    }
}
```

**AWS Configuration Class**

```java
package com.example.myservice.config;

import com.firefly.common.data.config.JobOrchestrationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnAsyncClient;

@Configuration
@ConditionalOnProperty(
    prefix = "firefly.data.orchestration",
    name = "orchestrator-type",
    havingValue = "AWS_STEP_FUNCTIONS"
)
public class AwsConfig {

    @Bean
    public SfnAsyncClient sfnAsyncClient(JobOrchestrationProperties properties) {
        return SfnAsyncClient.builder()
            .region(Region.of(properties.getAwsStepFunctions().getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

---

## 4. Creating Data Job Services

### Step 4.1: Create DTOs

**CustomerDTO.java**

```java
package com.example.myservice.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CustomerDTO {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
}
```

**OrderDTO.java**

```java
package com.example.myservice.dto;

import lombok.Data;
import lombok.Builder;
import java.time.Instant;

@Data
@Builder
public class OrderDTO {
    private String orderId;
    private String customerId;
    private Double amount;
    private String status;
    private Instant orderDate;
}
```

### Step 4.2: Create Your First Data Job Service

**CustomerDataJobService.java**

```java
package com.example.myservice.service;

import com.example.myservice.dto.CustomerDTO;
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

import java.util.Map;

@Service("customerDataJobService")  // Named bean for multiple services
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
        log.debug("Starting customer data extraction");

        // Use helper method to build JobExecutionRequest with all fields
        JobExecutionRequest executionRequest = buildJobExecutionRequest(
            request,
            "customer-data-extraction"
        );

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.success(
                JobStage.START,
                execution.getExecutionId(),
                "Customer data extraction started"
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
        // In a real implementation, use MapStruct mappers here
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


### Step 4.3: Create Additional Data Job Services

**OrderDataJobService.java**

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

@Service("orderDataJobService")
@Slf4j
public class OrderDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public OrderDataJobService(
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
        log.debug("Starting order data extraction");

        // Use helper method to build JobExecutionRequest with all fields
        JobExecutionRequest executionRequest = buildJobExecutionRequest(
            request,
            "order-data-extraction"
        );

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.success(
                JobStage.START,
                execution.getExecutionId(),
                "Order data extraction started"
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
                .message("Order data collected")
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
        return "order-data-extraction";
    }
}
```

**AnalyticsDataJobService.java**

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

@Service("analyticsDataJobService")
@Slf4j
public class AnalyticsDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator jobOrchestrator;

    public AnalyticsDataJobService(
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
        log.debug("Starting analytics data extraction");

        // Use helper method to build JobExecutionRequest with all fields
        JobExecutionRequest executionRequest = buildJobExecutionRequest(
            request,
            "analytics-data-extraction"
        );

        return jobOrchestrator.startJob(executionRequest)
            .map(execution -> JobStageResponse.success(
                JobStage.START,
                execution.getExecutionId(),
                "Analytics data extraction started"
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
        return "analytics-data-extraction";
    }
}
```

---

## 5. Creating Controllers

### Step 5.1: Create Your First Controller

**CustomerDataJobController.java**

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/jobs")
public class CustomerDataJobController extends AbstractDataJobController {

    public CustomerDataJobController(
            @Qualifier("customerDataJobService") DataJobService dataJobService) {
        super(dataJobService);
    }

    // That's it! All endpoints are automatically available:
    // POST   /api/v1/customers/jobs/start
    // GET    /api/v1/customers/jobs/{executionId}/check
    // GET    /api/v1/customers/jobs/{executionId}/collect
    // GET    /api/v1/customers/jobs/{executionId}/result
}
```

### Step 5.2: Create Additional Controllers

**OrderDataJobController.java**

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/jobs")
public class OrderDataJobController extends AbstractDataJobController {

    public OrderDataJobController(
            @Qualifier("orderDataJobService") DataJobService dataJobService) {
        super(dataJobService);
    }
}
```

**AnalyticsDataJobController.java**

```java
package com.example.myservice.controller;

import com.firefly.common.data.controller.AbstractDataJobController;
import com.firefly.common.data.service.DataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/jobs")
public class AnalyticsDataJobController extends AbstractDataJobController {

    public AnalyticsDataJobController(
            @Qualifier("analyticsDataJobService") DataJobService dataJobService) {
        super(dataJobService);
    }
}
```

### Step 5.3: Understanding the Endpoints

Each controller automatically provides these endpoints:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/{resource}/jobs/start` | Start a new data extraction job |
| GET | `/api/v1/{resource}/jobs/{executionId}/check` | Check job status |
| GET | `/api/v1/{resource}/jobs/{executionId}/collect` | Collect raw job results |
| GET | `/api/v1/{resource}/jobs/{executionId}/result` | Get transformed job results |

**Example Requests:**

```bash
# Start a customer data job
curl -X POST http://localhost:8080/api/v1/customers/jobs/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-123",
      "includeOrders": true
    },
    "requestId": "req-001"
  }'

# Check job status
curl http://localhost:8080/api/v1/customers/jobs/exec-abc-123/check?requestId=req-001

# Collect results
curl http://localhost:8080/api/v1/customers/jobs/exec-abc-123/collect?requestId=req-001

# Get transformed results
curl http://localhost:8080/api/v1/customers/jobs/exec-abc-123/result?requestId=req-001
```


---

## 6. Running and Testing

### Step 6.1: Run the Application

```bash
# Development mode (with MOCK orchestrator)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Production mode (with AWS Step Functions)
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Step 6.2: Verify Health Checks

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "jobOrchestrator": {
      "status": "UP",
      "details": {
        "orchestratorType": "MOCK"
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

### Step 6.3: Test the Endpoints

**Start a job:**

```bash
curl -X POST http://localhost:8080/api/v1/customers/jobs/start \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": {
      "customerId": "CUST-123"
    },
    "requestId": "req-001"
  }'
```

Response:
```json
{
  "stage": "START",
  "executionId": "exec-abc-123",
  "status": "RUNNING",
  "success": true,
  "message": "Customer data extraction started",
  "timestamp": "2025-10-15T14:00:00Z"
}
```

**Check job status:**

```bash
curl http://localhost:8080/api/v1/customers/jobs/exec-abc-123/check?requestId=req-001
```

**Collect results:**

```bash
curl http://localhost:8080/api/v1/customers/jobs/exec-abc-123/collect?requestId=req-001
```

### Step 6.4: Write Unit Tests

**CustomerDataJobServiceTest.java**

```java
package com.example.myservice.service;

import com.firefly.common.data.model.*;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.model.*;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerDataJobServiceTest {

    @Mock
    private JobOrchestrator jobOrchestrator;

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    private CustomerDataJobService service;

    @BeforeEach
    void setUp() {
        service = new CustomerDataJobService(
            tracingService,
            metricsService,
            resiliencyService,
            jobOrchestrator
        );

        // Mock resiliency decorator to pass through
        when(resiliencyService.decorateSupplier(any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void shouldStartJobSuccessfully() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .parameters(Map.of("customerId", "CUST-123"))
            .requestId("req-001")
            .build();

        JobExecution execution = JobExecution.builder()
            .executionId("exec-123")
            .status(JobExecutionStatus.RUNNING)
            .build();

        when(jobOrchestrator.startJob(any())).thenReturn(Mono.just(execution));

        // When & Then
        StepVerifier.create(service.startJob(request))
            .expectNextMatches(response ->
                response.getStage() == JobStage.START &&
                response.getExecutionId().equals("exec-123") &&
                response.isSuccess()
            )
            .verifyComplete();

        verify(jobOrchestrator).startJob(any());
    }

    @Test
    void shouldCheckJobStatus() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId("exec-123")
            .requestId("req-001")
            .build();

        when(jobOrchestrator.checkJobStatus("exec-123"))
            .thenReturn(Mono.just(JobExecutionStatus.SUCCEEDED));

        // When & Then
        StepVerifier.create(service.checkJob(request))
            .expectNextMatches(response ->
                response.getStage() == JobStage.CHECK &&
                response.isSuccess()
            )
            .verifyComplete();
    }
}
```

### Step 6.5: Write Integration Tests

**CustomerDataJobControllerIntegrationTest.java**

```java
package com.example.myservice.controller;

import com.example.myservice.MyDataServiceApplication;
import com.firefly.common.data.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = MyDataServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("dev")
class CustomerDataJobControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldStartJobSuccessfully() {
        // Given
        JobStageRequest request = JobStageRequest.builder()
            .parameters(Map.of("customerId", "CUST-123"))
            .requestId("req-001")
            .build();

        // When
        ResponseEntity<JobStageResponse> response = restTemplate.postForEntity(
            "/api/v1/customers/jobs/start",
            request,
            JobStageResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getExecutionId()).isNotNull();
    }
}
```

---

## 7. Advanced: Multiple Orchestrators

### Step 7.1: Why Multiple Orchestrators?

You might need different orchestrators for:
- Different job types (batch vs real-time)
- Different cloud providers (AWS, Azure, GCP)
- Different environments (dev uses MOCK, prod uses real orchestrator)
- Different SLAs (critical jobs use premium orchestrator)

### Step 7.2: Create Multiple Orchestrator Beans

**OrchestratorConfig.java**

```java
package com.example.myservice.config;

import com.example.myservice.orchestration.MockJobOrchestrator;
import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class OrchestratorConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(
        prefix = "firefly.data.orchestration",
        name = "primary-orchestrator",
        havingValue = "MOCK",
        matchIfMissing = true
    )
    public JobOrchestrator primaryOrchestrator() {
        return new MockJobOrchestrator();
    }

    @Bean("batchOrchestrator")
    public JobOrchestrator batchOrchestrator(
            JobOrchestrationProperties properties) {
        // Create AWS Batch orchestrator
        return new AwsBatchOrchestrator(properties);
    }

    @Bean("realtimeOrchestrator")
    public JobOrchestrator realtimeOrchestrator(
            JobOrchestrationProperties properties) {
        // Create AWS Step Functions orchestrator
        return new AwsStepFunctionsOrchestrator(properties);
    }
}
```

### Step 7.3: Use Different Orchestrators in Services

**BatchDataJobService.java**

```java
package com.example.myservice.service;

import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("batchDataJobService")
public class BatchDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator batchOrchestrator;

    public BatchDataJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobEventPublisher eventPublisher,
            JobAuditService auditService,
            JobExecutionResultService resultService,
            @Qualifier("batchOrchestrator") JobOrchestrator batchOrchestrator) {
        super(tracingService, metricsService, resiliencyService,
              eventPublisher, auditService, resultService);
        this.batchOrchestrator = batchOrchestrator;
    }

    // Implementation using batchOrchestrator...
}
```

**RealtimeDataJobService.java**

```java
package com.example.myservice.service;

import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("realtimeDataJobService")
public class RealtimeDataJobService extends AbstractResilientDataJobService {

    private final JobOrchestrator realtimeOrchestrator;

    public RealtimeDataJobService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobEventPublisher eventPublisher,
            JobAuditService auditService,
            JobExecutionResultService resultService,
            @Qualifier("realtimeOrchestrator") JobOrchestrator realtimeOrchestrator) {
        super(tracingService, metricsService, resiliencyService,
              eventPublisher, auditService, resultService);
        this.realtimeOrchestrator = realtimeOrchestrator;
    }

    // Implementation using realtimeOrchestrator...
}
```

---

## Summary

You now have a complete microservice with:

- ✅ **Multiple Job Orchestrators** - MOCK for dev, AWS Step Functions for prod, plus batch and realtime
- ✅ **Multiple Data Job Services** - Customer, Order, Analytics services
- ✅ **Multiple Controllers** - Each with automatic endpoints and logging
- ✅ **Automatic Features** - Tracing, metrics, circuit breaker, retry, rate limiting, bulkhead
- ✅ **JSON Logging** - Structured logs by default
- ✅ **Health Checks** - Orchestrator health monitoring
- ✅ **Reactive Stack** - Netty + WebFlux for non-blocking I/O
- ✅ **Tests** - Unit and integration tests

### Next Steps

1. **Add MapStruct mappers** for result transformation
2. **Configure AWS credentials** for production
3. **Set up distributed tracing** with Zipkin/Jaeger
4. **Add custom metrics** for business KPIs
5. **Implement SAGA patterns** with lib-transactional-engine
6. **Add event publishing** with lib-common-eda

### Troubleshooting

**Problem: RabbitMQ health check failing**
- Solution: Already disabled in `application.yml` with `management.health.rabbit.enabled: false`

**Problem: Multiple orchestrators conflicting**
- Solution: Use `@Qualifier` to specify which orchestrator to inject

**Problem: JSON logs not appearing**
- Solution: Check `logging.format: json` in application.yml

**Problem: Circuit breaker always open**
- Solution: Adjust `resilience4j.circuitbreaker.failure-rate-threshold` in configuration

For more examples, see:
- [Multiple Jobs Example](multiple-jobs-example.md)
- [Getting Started Guide](getting-started.md)
- [README](../README.md)



