# Examples

Real-world usage examples for `lib-common-data`.

> **Note**: These examples demonstrate usage patterns and integration scenarios. Some examples reference external services (cache, database, SAGA orchestrator) that are not part of this library but show how to integrate lib-common-data with other components in your application.

## Table of Contents

- [Basic Job Execution](#basic-job-execution)
- [Polling Patterns](#polling-patterns)
- [Error Handling](#error-handling)
- [Event-Driven Workflows](#event-driven-workflows)
- [SAGA Integration](#saga-integration)
- [Advanced Scenarios](#advanced-scenarios)

---

## Basic Job Execution

### Example 1: Simple Data Extraction

Complete example of extracting customer data:

**1. Define DTO**

```java
package com.example.dto;

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

**2. Create Mapper**

```java
package com.example.mapper;

import com.example.dto.CustomerDTO;
import com.firefly.common.data.mapper.JobResultMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface CustomerDataMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    
    @Override
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    @Mapping(source = "last_name", target = "lastName")
    @Mapping(source = "email_address", target = "email")
    @Mapping(source = "phone", target = "phoneNumber")
    CustomerDTO mapToTarget(Map<String, Object> source);
    
    @Override
    default Class<CustomerDTO> getTargetType() {
        return CustomerDTO.class;
    }
}
```

**3. Create Service**

```java
package com.example.service;

import com.example.dto.CustomerDTO;
import com.firefly.common.data.model.*;
import com.firefly.common.data.service.DataJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerDataJobService {

    private final DataJobService dataJobService;

    public Mono<CustomerDTO> extractCustomerData(String customerId) {
        // 1. Start job
        JobStageRequest startRequest = JobStageRequest.builder()
            .stage(JobStage.START)
            .jobType("customer-data-extraction")
            .parameters(Map.of("customerId", customerId))
            .build();

        return dataJobService.startJob(startRequest)
            .flatMap(startResponse -> {
                String executionId = startResponse.getExecutionId();
                
                // 2. Poll until complete
                return pollUntilComplete(executionId)
                    .flatMap(checkResponse -> {
                        // 3. Get transformed result
                        JobStageRequest resultRequest = JobStageRequest.builder()
                            .stage(JobStage.RESULT)
                            .executionId(executionId)
                            .targetDtoClass(CustomerDTO.class.getName())
                            .build();
                        
                        return dataJobService.getJobResult(resultRequest);
                    })
                    .map(resultResponse -> {
                        Map<String, Object> data = resultResponse.getData();
                        return (CustomerDTO) data.get("result");
                    });
            });
    }

    private Mono<JobStageResponse> pollUntilComplete(String executionId) {
        JobStageRequest checkRequest = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .build();

        return dataJobService.checkJob(checkRequest)
            .flatMap(response -> {
                if (response.getStatus() == JobExecutionStatus.RUNNING) {
                    return Mono.delay(Duration.ofSeconds(5))
                        .then(pollUntilComplete(executionId));
                }
                return Mono.just(response);
            })
            .timeout(Duration.ofMinutes(30));
    }
}
```

**4. Create Controller**

```java
package com.example.controller;

import com.example.dto.CustomerDTO;
import com.example.service.CustomerDataJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerDataController {

    private final CustomerDataJobService customerDataJobService;

    @GetMapping("/{customerId}/data")
    public Mono<CustomerDTO> getCustomerData(@PathVariable String customerId) {
        return customerDataJobService.extractCustomerData(customerId);
    }
}
```

**5. Test**

```bash
curl http://localhost:8080/api/v1/customers/12345/data
```

**Response:**
```json
{
  "customerId": "12345",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phoneNumber": "+1-555-0100"
}
```

---

## Polling Patterns

### Example 2: Exponential Backoff Polling

```java
@Service
public class SmartPollingService {

    private final DataJobService dataJobService;

    public Mono<JobStageResponse> pollWithExponentialBackoff(String executionId) {
        return pollWithBackoff(executionId, 1, 5, 32);
    }

    private Mono<JobStageResponse> pollWithBackoff(
            String executionId, 
            int attempt, 
            int initialDelay, 
            int maxDelay) {
        
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .build();

        return dataJobService.checkJob(request)
            .flatMap(response -> {
                if (response.getStatus() == JobExecutionStatus.RUNNING) {
                    int delay = Math.min(initialDelay * (int) Math.pow(2, attempt - 1), maxDelay);
                    
                    log.info("Job still running, polling again in {}s (attempt {})", 
                        delay, attempt);
                    
                    return Mono.delay(Duration.ofSeconds(delay))
                        .then(pollWithBackoff(executionId, attempt + 1, initialDelay, maxDelay));
                }
                return Mono.just(response);
            });
    }
}
```

### Example 3: Progress-Based Polling

```java
@Service
public class ProgressAwarePollingService {

    private final DataJobService dataJobService;

    public Flux<JobStageResponse> streamProgress(String executionId) {
        return Flux.interval(Duration.ofSeconds(5))
            .flatMap(tick -> checkJobStatus(executionId))
            .takeUntil(response -> 
                response.getStatus() != JobExecutionStatus.RUNNING)
            .doOnNext(response -> 
                log.info("Job progress: {}%", response.getProgressPercentage()));
    }

    private Mono<JobStageResponse> checkJobStatus(String executionId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .build();

        return dataJobService.checkJob(request);
    }
}
```

---

## Error Handling

### Example 4: Comprehensive Error Handling

```java
@Service
@Slf4j
public class RobustDataJobService {

    private final DataJobService dataJobService;

    public Mono<CustomerDTO> extractCustomerDataSafely(String customerId) {
        return extractCustomerData(customerId)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> 
                    log.warn("Retrying job execution, attempt: {}", 
                        signal.totalRetries() + 1)))
            .onErrorResume(JobNotFoundException.class, error -> {
                log.error("Job not found: {}", error.getMessage());
                return Mono.error(new CustomerDataNotFoundException(customerId));
            })
            .onErrorResume(JobExecutionException.class, error -> {
                log.error("Job execution failed: {}", error.getMessage());
                return Mono.error(new CustomerDataExtractionException(customerId, error));
            })
            .onErrorResume(MapperNotFoundException.class, error -> {
                log.error("Mapper not found: {}", error.getMessage());
                return Mono.error(new DataTransformationException(error));
            })
            .timeout(Duration.ofMinutes(30))
            .onErrorResume(TimeoutException.class, error -> {
                log.error("Job execution timed out after 30 minutes");
                return Mono.error(new JobTimeoutException(customerId));
            });
    }

    private boolean isRetryable(Throwable error) {
        return error instanceof TransientException 
            || error instanceof TimeoutException
            || error.getCause() instanceof IOException;
    }

    private Mono<CustomerDTO> extractCustomerData(String customerId) {
        // Implementation from Example 1
        return Mono.empty();
    }
}
```

### Example 5: Graceful Degradation

> **Note**: This example shows integration with external services (cache, database) that you would implement in your application.

```java
@Service
public class FallbackDataService {

    private final DataJobService dataJobService;
    // These are your application services
    private final CacheService cacheService;
    private final DatabaseService databaseService;

    public Mono<CustomerDTO> getCustomerData(String customerId) {
        return extractFromJob(customerId)
            .onErrorResume(error -> {
                log.warn("Job extraction failed, trying cache", error);
                return getFromCache(customerId);
            })
            .onErrorResume(error -> {
                log.warn("Cache miss, trying database", error);
                return getFromDatabase(customerId);
            })
            .onErrorResume(error -> {
                log.error("All sources failed, returning default", error);
                return Mono.just(createDefaultCustomer(customerId));
            });
    }

    private Mono<CustomerDTO> extractFromJob(String customerId) {
        // Primary: Extract from job
        return dataJobService.startJob(createRequest(customerId))
            .flatMap(response -> pollAndGetResult(response.getExecutionId()));
    }

    private Mono<CustomerDTO> getFromCache(String customerId) {
        // Fallback 1: Get from cache
        return cacheService.get("customer:" + customerId);
    }

    private Mono<CustomerDTO> getFromDatabase(String customerId) {
        // Fallback 2: Get from database
        return databaseService.findCustomer(customerId);
    }

    private CustomerDTO createDefaultCustomer(String customerId) {
        // Fallback 3: Return default
        return CustomerDTO.builder()
            .customerId(customerId)
            .firstName("Unknown")
            .lastName("Unknown")
            .email("unknown@example.com")
            .build();
    }
}
```

---

## Event-Driven Workflows

> **Note**: These examples show integration with event publishing. The `EventPublisher` would be from `lib-common-eda` or your own event publishing implementation.

### Example 6: Event-Driven Job Processing

```java
@Service
@Slf4j
public class EventDrivenJobService {

    private final DataJobService dataJobService;
    // EventPublisher is from lib-common-eda
    private final EventPublisher eventPublisher;

    public Mono<Void> processCustomerDataAsync(String customerId) {
        // 1. Start job
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .jobType("customer-data-extraction")
            .parameters(Map.of("customerId", customerId))
            .build();

        return dataJobService.startJob(request)
            .doOnSuccess(response -> {
                // 2. Publish job started event
                publishEvent("customer.data.job.started", Map.of(
                    "customerId", customerId,
                    "executionId", response.getExecutionId()
                ));
                
                // 3. Start async polling
                pollAndNotify(response.getExecutionId(), customerId)
                    .subscribe();
            })
            .then();
    }

    private Mono<Void> pollAndNotify(String executionId, String customerId) {
        return pollUntilComplete(executionId)
            .flatMap(response -> {
                if (response.getStatus() == JobExecutionStatus.SUCCEEDED) {
                    // Job succeeded - get results
                    return getResultAndPublish(executionId, customerId);
                } else {
                    // Job failed - publish error event
                    publishEvent("customer.data.job.failed", Map.of(
                        "customerId", customerId,
                        "executionId", executionId,
                        "error", response.getMessage()
                    ));
                    return Mono.empty();
                }
            });
    }

    private Mono<Void> getResultAndPublish(String executionId, String customerId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.RESULT)
            .executionId(executionId)
            .targetDtoClass(CustomerDTO.class.getName())
            .build();

        return dataJobService.getJobResult(request)
            .doOnSuccess(response -> {
                Map<String, Object> data = response.getData();
                CustomerDTO customer = (CustomerDTO) data.get("result");
                
                // Publish completion event with data
                publishEvent("customer.data.job.completed", Map.of(
                    "customerId", customerId,
                    "executionId", executionId,
                    "data", customer
                ));
            })
            .then();
    }

    private void publishEvent(String eventType, Map<String, Object> payload) {
        eventPublisher.publish(payload, eventType);
        log.info("Published event: {}", eventType);
    }

    private Mono<JobStageResponse> pollUntilComplete(String executionId) {
        // Implementation from Example 1
        return Mono.empty();
    }
}
```

### Example 7: Event Listener

```java
@Component
@Slf4j
public class CustomerDataEventListener {

    @EventListener
    public void onJobStarted(CustomerDataJobStartedEvent event) {
        log.info("Customer data job started: customerId={}, executionId={}", 
            event.getCustomerId(), event.getExecutionId());
        
        // Update UI, send notification, etc.
    }

    @EventListener
    public void onJobCompleted(CustomerDataJobCompletedEvent event) {
        log.info("Customer data job completed: customerId={}, data={}", 
            event.getCustomerId(), event.getData());
        
        // Process results, update cache, etc.
    }

    @EventListener
    public void onJobFailed(CustomerDataJobFailedEvent event) {
        log.error("Customer data job failed: customerId={}, error={}", 
            event.getCustomerId(), event.getError());
        
        // Send alert, retry, etc.
    }
}
```

---

## SAGA Integration

> **Note**: This example demonstrates integration with `lib-transactional-engine` (SAGA orchestrator). The `SagaOrchestrator`, `Saga`, and `Step` classes are from that library, not from lib-common-data. This library provides `StepEventPublisherBridge` to publish step events to the SAGA engine.

### Example 8: Multi-Step Data Processing SAGA

```java
@Service
@Slf4j
public class DataProcessingSagaService {

    // SagaOrchestrator is from lib-transactional-engine
    private final SagaOrchestrator sagaOrchestrator;
    private final DataJobService dataJobService;
    // These are your application services
    private final DataTransformationService transformationService;
    private final DataStorageService storageService;

    public Mono<String> processCustomerDataWithSaga(String customerId) {
        Saga saga = Saga.builder()
            .name("customer-data-processing")
            .step(Step.builder()
                .id("extract-data")
                .action(() -> extractData(customerId))
                .compensation(() -> cleanupExtractedData(customerId))
                .build())
            .step(Step.builder()
                .id("transform-data")
                .action(() -> transformData(customerId))
                .compensation(() -> revertTransformation(customerId))
                .build())
            .step(Step.builder()
                .id("store-data")
                .action(() -> storeData(customerId))
                .compensation(() -> deleteStoredData(customerId))
                .build())
            .step(Step.builder()
                .id("notify-completion")
                .action(() -> notifyCompletion(customerId))
                .compensation(() -> Mono.empty()) // No compensation needed
                .build())
            .build();

        return sagaOrchestrator.execute(saga)
            .map(result -> result.getSagaId())
            .doOnSuccess(sagaId ->
                log.info("SAGA completed successfully: {}", sagaId))
            .doOnError(error ->
                log.error("SAGA failed, compensations executed", error));
    }

    private Mono<Void> extractData(String customerId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .jobType("customer-extraction")
            .parameters(Map.of("customerId", customerId))
            .build();

        return dataJobService.startJob(request)
            .flatMap(response -> pollUntilComplete(response.getExecutionId()))
            .then();
    }

    private Mono<Void> transformData(String customerId) {
        return transformationService.transform(customerId);
    }

    private Mono<Void> storeData(String customerId) {
        return storageService.store(customerId);
    }

    private Mono<Void> notifyCompletion(String customerId) {
        return Mono.fromRunnable(() -> 
            log.info("Customer data processing completed: {}", customerId));
    }

    // Compensation methods
    private Mono<Void> cleanupExtractedData(String customerId) {
        log.warn("Compensating: cleaning up extracted data for {}", customerId);
        return Mono.empty();
    }

    private Mono<Void> revertTransformation(String customerId) {
        log.warn("Compensating: reverting transformation for {}", customerId);
        return transformationService.revert(customerId);
    }

    private Mono<Void> deleteStoredData(String customerId) {
        log.warn("Compensating: deleting stored data for {}", customerId);
        return storageService.delete(customerId);
    }

    private Mono<JobStageResponse> pollUntilComplete(String executionId) {
        // Implementation from Example 1
        return Mono.empty();
    }
}
```

---

## Advanced Scenarios

### Example 9: Batch Processing

```java
@Service
public class BatchDataJobService {

    private final DataJobService dataJobService;

    public Flux<CustomerDTO> processBatch(List<String> customerIds) {
        return Flux.fromIterable(customerIds)
            .flatMap(this::extractCustomerData, 5) // Concurrency: 5
            .onErrorContinue((error, customerId) -> 
                log.error("Failed to process customer: {}", customerId, error));
    }

    private Mono<CustomerDTO> extractCustomerData(String customerId) {
        // Implementation from Example 1
        return Mono.empty();
    }
}
```

### Example 10: Caching Results

> **Note**: This example uses Spring's caching abstraction. Configure your cache provider (Redis, Caffeine, etc.) separately.

```java
@Service
public class CachedDataJobService {

    private final DataJobService dataJobService;
    // Spring's CacheManager
    private final CacheManager cacheManager;

    @Cacheable(value = "customerData", key = "#customerId")
    public Mono<CustomerDTO> getCustomerDataCached(String customerId) {
        return extractCustomerData(customerId)
            .doOnSuccess(data -> 
                log.info("Cached customer data: {}", customerId));
    }

    @CacheEvict(value = "customerData", key = "#customerId")
    public Mono<Void> invalidateCache(String customerId) {
        log.info("Invalidated cache for customer: {}", customerId);
        return Mono.empty();
    }

    private Mono<CustomerDTO> extractCustomerData(String customerId) {
        // Implementation from Example 1
        return Mono.empty();
    }
}
```

---

## See Also

- [Getting Started](getting-started.md) - Basic setup
- [Job Lifecycle](job-lifecycle.md) - Stage details
- [Mappers](mappers.md) - Transformation patterns
- [SAGA Integration](saga-integration.md) - SAGA examples
- [Testing](testing.md) - Testing strategies

