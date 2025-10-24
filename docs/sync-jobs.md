# Synchronous Data Jobs

This guide explains how to implement **synchronous data jobs** using the `lib-common-data` library.

## Overview

While `DataJobService` is designed for **asynchronous jobs** with multiple stages (START → CHECK → COLLECT → RESULT → STOP), `SyncDataJobService` is designed for jobs that execute **synchronously** and return results immediately in a single operation.

### When to Use Synchronous Jobs

✅ **Use `SyncDataJobService` for:**
- Simple data transformations that complete quickly (< 30 seconds)
- Database queries that return results immediately
- API calls to external services with synchronous responses
- In-memory data processing
- Validation or enrichment operations
- Real-time data lookups

❌ **Do NOT use `SyncDataJobService` for:**
- Long-running jobs (> 30 seconds) - use `DataJobService` instead
- Jobs that require polling or status checking
- Jobs that need to be stopped/cancelled mid-execution
- Jobs with complex multi-stage workflows
- Jobs that interact with orchestrators (AWS Step Functions, etc.)

---

## Quick Start

### 1. Add Dependency

Add `lib-common-data` to your `pom.xml`:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-data</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Implement Your Synchronous Job

Extend `AbstractResilientSyncDataJobService` and implement the `doExecute()` method:

```java
package com.example.service;

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

@Slf4j
@Service
public class CustomerEnrichmentService extends AbstractResilientSyncDataJobService {

    private final CustomerRepository customerRepository;
    private final EnrichmentService enrichmentService;

    public CustomerEnrichmentService(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            JobEventPublisher eventPublisher,
            JobAuditService auditService,
            JobExecutionResultService resultService,
            CustomerRepository customerRepository,
            EnrichmentService enrichmentService) {
        super(tracingService, metricsService, resiliencyService, 
              eventPublisher, auditService, resultService);
        this.customerRepository = customerRepository;
        this.enrichmentService = enrichmentService;
    }

    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        // Extract parameters
        String customerId = (String) request.getParameters().get("customerId");
        
        // Execute business logic
        return customerRepository.findById(customerId)
            .flatMap(customer -> enrichmentService.enrich(customer))
            .map(enrichedCustomer -> JobStageResponse.builder()
                .success(true)
                .executionId(request.getExecutionId())
                .data(Map.of("customer", enrichedCustomer))
                .message("Customer enriched successfully")
                .build())
            .switchIfEmpty(Mono.just(JobStageResponse.builder()
                .success(false)
                .executionId(request.getExecutionId())
                .error("Customer not found: " + customerId)
                .message("Customer enrichment failed")
                .build()));
    }

    @Override
    public String getJobName() {
        return "CustomerEnrichment";
    }

    @Override
    public String getJobDescription() {
        return "Enriches customer data with additional information from external sources";
    }
}
```

### 3. Use the Service

```java
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerEnrichmentService enrichmentService;

    @PostMapping("/{customerId}/enrich")
    public Mono<JobStageResponse> enrichCustomer(@PathVariable String customerId) {
        JobStageRequest request = JobStageRequest.builder()
            .parameters(Map.of("customerId", customerId))
            .requestId(UUID.randomUUID().toString())
            .initiator("api-user")
            .build();
        
        return enrichmentService.execute(request);
    }
}
```

---

## Features

`AbstractResilientSyncDataJobService` automatically provides:

### 🔍 Observability
- **Distributed Tracing**: Automatic trace propagation via Micrometer
- **Metrics Collection**: Execution time, success/failure counts, error types
- **Comprehensive Logging**: Structured logs with execution context

### 🛡️ Resiliency
- **Circuit Breaker**: Prevents cascading failures
- **Retry Logic**: Automatic retries with exponential backoff
- **Rate Limiting**: Controls request rate to prevent overload
- **Bulkhead**: Isolates resources to prevent resource exhaustion

### 💾 Persistence
- **Audit Trail**: Records all job executions with timestamps
- **Result Storage**: Persists execution results for analysis
- **Error Tracking**: Stores error details for debugging

### 📢 Event Publishing
- **Job Started**: Published when job begins execution
- **Job Completed**: Published when job finishes successfully
- **Job Failed**: Published when job encounters an error

---

## Comparison: Sync vs Async Jobs

| Feature | SyncDataJobService | DataJobService |
|---------|-------------------|----------------|
| **Execution Model** | Single operation | Multi-stage (START → CHECK → COLLECT → RESULT → STOP) |
| **Response Time** | Immediate (< 30s) | Long-running (minutes to hours) |
| **Orchestrator** | Not required | Required (AWS Step Functions, etc.) |
| **Status Polling** | Not supported | Supported via CHECK stage |
| **Cancellation** | Not supported | Supported via STOP stage |
| **Use Case** | Quick transformations, lookups | Complex workflows, batch processing |
| **Complexity** | Low | High |

---

## Advanced Examples

### Example 1: Data Validation Service

```java
@Service
public class OrderValidationService extends AbstractResilientSyncDataJobService {

    private final OrderRepository orderRepository;
    private final ValidationRules validationRules;

    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        String orderId = (String) request.getParameters().get("orderId");
        
        return orderRepository.findById(orderId)
            .flatMap(order -> {
                ValidationResult result = validationRules.validate(order);
                
                return Mono.just(JobStageResponse.builder()
                    .success(result.isValid())
                    .executionId(request.getExecutionId())
                    .data(Map.of(
                        "orderId", orderId,
                        "isValid", result.isValid(),
                        "errors", result.getErrors()
                    ))
                    .message(result.isValid() ? "Order is valid" : "Order validation failed")
                    .build());
            });
    }

    @Override
    public String getJobName() {
        return "OrderValidation";
    }
}
```

### Example 2: External API Integration

```java
@Service
public class CreditCheckService extends AbstractResilientSyncDataJobService {

    private final WebClient creditBureauClient;

    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        String customerId = (String) request.getParameters().get("customerId");
        Integer requestedAmount = (Integer) request.getParameters().get("amount");
        
        return creditBureauClient.get()
            .uri("/credit-score/{customerId}", customerId)
            .retrieve()
            .bodyToMono(CreditScore.class)
            .map(creditScore -> {
                boolean approved = creditScore.getScore() >= 650 && 
                                  creditScore.getAvailableCredit() >= requestedAmount;
                
                return JobStageResponse.builder()
                    .success(true)
                    .executionId(request.getExecutionId())
                    .data(Map.of(
                        "customerId", customerId,
                        "creditScore", creditScore.getScore(),
                        "approved", approved,
                        "reason", approved ? "Credit approved" : "Insufficient credit"
                    ))
                    .message("Credit check completed")
                    .build();
            })
            .onErrorResume(error -> Mono.just(JobStageResponse.builder()
                .success(false)
                .executionId(request.getExecutionId())
                .error("Credit check failed: " + error.getMessage())
                .message("Unable to complete credit check")
                .build()));
    }

    @Override
    public String getJobName() {
        return "CreditCheck";
    }
}
```

### Example 3: Database Query Service

```java
@Service
public class CustomerSearchService extends AbstractResilientSyncDataJobService {

    private final R2dbcEntityTemplate template;

    @Override
    protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
        String searchTerm = (String) request.getParameters().get("searchTerm");
        Integer limit = (Integer) request.getParameters().getOrDefault("limit", 10);
        
        return template.getDatabaseClient()
            .sql("SELECT * FROM customers WHERE name ILIKE :searchTerm LIMIT :limit")
            .bind("searchTerm", "%" + searchTerm + "%")
            .bind("limit", limit)
            .fetch()
            .all()
            .collectList()
            .map(customers -> JobStageResponse.builder()
                .success(true)
                .executionId(request.getExecutionId())
                .data(Map.of(
                    "customers", customers,
                    "count", customers.size(),
                    "searchTerm", searchTerm
                ))
                .message("Found " + customers.size() + " customers")
                .build());
    }

    @Override
    public String getJobName() {
        return "CustomerSearch";
    }
}
```

---

## Configuration

Synchronous jobs use the same configuration as asynchronous jobs. See [Configuration Guide](configuration.md) for details.

### Key Configuration Properties

```yaml
firefly:
  data:
    orchestration:
      # Resiliency settings
      resiliency:
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 1s
        rate-limiter-enabled: true
        rate-limiter-limit-for-period: 100
        rate-limiter-limit-refresh-period: 1s
        bulkhead-enabled: true
        bulkhead-max-concurrent-calls: 25
        bulkhead-max-wait-duration: 0s

      # Observability settings
      observability:
        tracing-enabled: true
        metrics-enabled: true
```

---

## Testing

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
class CustomerEnrichmentServiceTest {

    @Mock
    private JobTracingService tracingService;
    @Mock
    private JobMetricsService metricsService;
    @Mock
    private ResiliencyDecoratorService resiliencyService;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private EnrichmentService enrichmentService;

    private CustomerEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new CustomerEnrichmentService(
            tracingService, metricsService, resiliencyService,
            null, null, null,
            customerRepository, enrichmentService
        );
        
        // Setup mocks
        lenient().when(tracingService.traceJobOperation(any(), any(), any()))
            .thenAnswer(inv -> inv.getArgument(2));
        lenient().when(resiliencyService.decorate(any()))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldEnrichCustomerSuccessfully() {
        // Given
        Customer customer = new Customer("123", "John Doe");
        EnrichedCustomer enriched = new EnrichedCustomer(customer, "additional-data");
        
        when(customerRepository.findById("123")).thenReturn(Mono.just(customer));
        when(enrichmentService.enrich(customer)).thenReturn(Mono.just(enriched));
        
        JobStageRequest request = JobStageRequest.builder()
            .executionId("test-123")
            .parameters(Map.of("customerId", "123"))
            .build();
        
        // When
        Mono<JobStageResponse> result = service.execute(request);
        
        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getData()).containsKey("customer");
            })
            .verifyComplete();
    }
}
```

---

## Best Practices

1. **Keep It Fast**: Synchronous jobs should complete in < 30 seconds
2. **Handle Errors Gracefully**: Always return a `JobStageResponse`, even on errors
3. **Use Reactive Patterns**: Leverage Reactor's `Mono` and `Flux` for non-blocking operations
4. **Validate Input**: Check required parameters early in `doExecute()`
5. **Provide Meaningful Names**: Override `getJobName()` and `getJobDescription()`
6. **Log Appropriately**: Use structured logging with context
7. **Test Thoroughly**: Write unit tests for success and failure scenarios

---

## See Also

- [Getting Started Guide](getting-started.md)
- [Asynchronous Jobs](job-lifecycle.md)
- [Configuration](configuration.md)
- [Observability](observability.md)
- [Resiliency](resiliency.md)
- [Testing](testing.md)

