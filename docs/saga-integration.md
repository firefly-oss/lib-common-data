# SAGA Integration

Complete guide to SAGA pattern integration with `lib-transactional-engine` in `lib-common-data`.

## Table of Contents

- [Overview](#overview)
- [StepEventPublisherBridge](#stepeventpublisherbridge)
- [Event Flow](#event-flow)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

The `lib-common-data` library integrates with `lib-transactional-engine` to support **distributed transactions** using the **SAGA pattern**.

### What is SAGA?

SAGA is a pattern for managing distributed transactions across multiple services:

- **Long-running transactions** - Spans multiple services
- **Eventual consistency** - Not ACID, but eventually consistent
- **Compensation** - Rollback via compensating transactions
- **Event-driven** - Coordinated via events

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ lib-common-data                                             │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ DataJobService                                       │   │
│  │   ├─> startJob()                                     │   │
│  │   ├─> checkJob()                                     │   │
│  │   ├─> collectJobResults()                            │   │
│  │   └─> getJobResult()                                 │   │
│  └────────────────┬─────────────────────────────────────┘   │
│                   │                                         │
│                   ▼                                         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ StepEventPublisherBridge                             │   │
│  │   ├─> Listens to SAGA step events                    │   │
│  │   ├─> Enriches with job context                      │   │
│  │   └─> Publishes to EDA                               │   │
│  └────────────────┬─────────────────────────────────────┘   │
│                   │                                         │
└───────────────────┼─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│ lib-transactional-engine                                    │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ SAGA Orchestrator                                    │   │
│  │   ├─> Manages SAGA lifecycle                         │   │
│  │   ├─> Executes steps                                 │   │
│  │   ├─> Handles compensation                           │   │
│  │   └─> Publishes step events                          │   │
│  └────────────────┬─────────────────────────────────────┘   │
│                   │                                         │
└───────────────────┼─────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────┐
│ lib-common-eda                                              │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Event Publishers                                     │   │
│  │   ├─> Kafka                                          │   │
│  │   ├─> RabbitMQ                                       │   │
│  │   ├─> AWS SQS/SNS                                    │   │
│  │   └─> Google Pub/Sub                                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## StepEventPublisherBridge

### Component Overview

The `StepEventPublisherBridge` is the **bridge** between SAGA step events and the EDA platform.

### Class Definition

```java
package com.firefly.common.data.stepevents;

import com.firefly.common.eda.EventPublisher;
import com.firefly.transactional.saga.StepEvent;
import com.firefly.transactional.saga.StepEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bridges SAGA step events to the EDA platform.
 * 
 * Listens to step events from lib-transactional-engine and publishes
 * them to the configured EDA platform (Kafka, RabbitMQ, etc.).
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "firefly.stepevents",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class StepEventPublisherBridge implements StepEventListener {

    private final EventPublisher eventPublisher;
    private final StepEventsProperties properties;

    public StepEventPublisherBridge(
            EventPublisher eventPublisher,
            StepEventsProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    public void onStepEvent(StepEvent event) {
        try {
            // Enrich event with context
            Map<String, Object> enrichedEvent = enrichEvent(event);
            
            // Publish to EDA
            String topic = determineTopic(event);
            eventPublisher.publish(enrichedEvent, topic);
            
            log.debug("Published step event: saga={}, step={}, type={}",
                event.getSagaName(), event.getStepId(), event.getType());
                
        } catch (Exception e) {
            log.error("Failed to publish step event", e);
        }
    }

    private Map<String, Object> enrichEvent(StepEvent event) {
        Map<String, Object> enriched = new HashMap<>();
        
        // Core event data
        enriched.put("sagaName", event.getSagaName());
        enriched.put("sagaId", event.getSagaId());
        enriched.put("stepId", event.getStepId());
        enriched.put("type", event.getType());
        enriched.put("timestamp", event.getTimestamp());
        
        // Optional fields
        if (event.getResult() != null) {
            enriched.put("result", event.getResult());
        }
        if (event.getError() != null) {
            enriched.put("error", event.getError());
        }
        
        // Context enrichment
        if (properties.isIncludeJobContext()) {
            enriched.put("context", "data-processing");
            enriched.put("library", "lib-common-data");
        }
        
        return enriched;
    }

    private String determineTopic(StepEvent event) {
        // Use configured topic or default
        return properties.getTopic() != null 
            ? properties.getTopic() 
            : "data-processing-step-events";
    }
}
```

### Properties Configuration

```java
package com.firefly.common.data.stepevents;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "firefly.stepevents")
public class StepEventsProperties {
    
    /**
     * Enable/disable step event publishing
     */
    private boolean enabled = true;
    
    /**
     * Topic for step events
     */
    private String topic = "data-processing-step-events";
    
    /**
     * Include job context in events
     */
    private boolean includeJobContext = true;
}
```

---

## Event Flow

### Step Event Types

The bridge publishes the following event types:

| Event Type | Description | When Published |
|------------|-------------|----------------|
| `STEP_STARTED` | Step execution started | Before step logic executes |
| `STEP_COMPLETED` | Step completed successfully | After successful execution |
| `STEP_FAILED` | Step failed | After execution error |
| `STEP_COMPENSATING` | Compensation started | Before compensation logic |
| `STEP_COMPENSATED` | Compensation completed | After successful compensation |
| `STEP_COMPENSATION_FAILED` | Compensation failed | After compensation error |

### Event Structure

```json
{
  "sagaName": "customer-data-processing",
  "sagaId": "saga-12345",
  "stepId": "extract-customer-data",
  "type": "STEP_COMPLETED",
  "timestamp": "2025-01-15T10:30:00Z",
  "result": {
    "customerId": "12345",
    "recordsProcessed": 150
  },
  "context": "data-processing",
  "library": "lib-common-data"
}
```

### Event Headers

When published to EDA, events include headers:

```yaml
Headers:
  step.saga_name: "customer-data-processing"
  step.saga_id: "saga-12345"
  step.step_id: "extract-customer-data"
  step.type: "STEP_COMPLETED"
  step.timestamp: "2025-01-15T10:30:00Z"
  context: "data-processing"
  library: "lib-common-data"
  routing_key: "customer-data-processing:saga-12345"
```

---

## Configuration

### Basic Configuration

```yaml
firefly:
  stepevents:
    enabled: true
    topic: my-step-events
    include-job-context: true
```

### Integration with SAGA

```yaml
firefly:
  data:
    transactional:
      enabled: true
  
  transactional:
    saga:
      enabled: true
      default-timeout: 30m
      max-retries: 3
  
  stepevents:
    enabled: true
    topic: customer-data-step-events
    include-job-context: true
```

### Integration with EDA

```yaml
firefly:
  eda:
    publishers:
      - id: step-events-publisher
        type: KAFKA
        connection-id: kafka-main
    
    connections:
      kafka:
        - id: kafka-main
          bootstrap-servers: kafka:9092
          properties:
            acks: all
            retries: 3

  stepevents:
    enabled: true
    topic: step-events
```

---

## Usage Examples

### Example 1: Basic SAGA with Data Job

```java
@Service
public class CustomerDataSagaService {

    @Autowired
    private SagaOrchestrator sagaOrchestrator;
    
    @Autowired
    private DataJobService dataJobService;
    
    public Mono<String> processCustomerData(String customerId) {
        // Define SAGA
        Saga saga = Saga.builder()
            .name("customer-data-processing")
            .step(Step.builder()
                .id("extract-data")
                .action(() -> extractCustomerData(customerId))
                .compensation(() -> cleanupExtractedData(customerId))
                .build())
            .step(Step.builder()
                .id("transform-data")
                .action(() -> transformCustomerData(customerId))
                .compensation(() -> revertTransformation(customerId))
                .build())
            .step(Step.builder()
                .id("load-data")
                .action(() -> loadCustomerData(customerId))
                .compensation(() -> deleteLoadedData(customerId))
                .build())
            .build();
        
        // Execute SAGA
        return sagaOrchestrator.execute(saga)
            .map(result -> result.getSagaId());
    }
    
    private Mono<Void> extractCustomerData(String customerId) {
        JobStageRequest request = JobStageRequest.builder()
            .stage(JobStage.START)
            .jobType("customer-extraction")
            .parameters(Map.of("customerId", customerId))
            .build();
        
        return dataJobService.startJob(request)
            .then();
    }
    
    // ... other methods
}
```

### Example 2: Listening to Step Events

```java
@Component
public class StepEventMonitor implements StepEventListener {

    @Override
    public void onStepEvent(StepEvent event) {
        switch (event.getType()) {
            case STEP_STARTED:
                log.info("Step started: {}", event.getStepId());
                break;
            case STEP_COMPLETED:
                log.info("Step completed: {}", event.getStepId());
                break;
            case STEP_FAILED:
                log.error("Step failed: {}, error: {}", 
                    event.getStepId(), event.getError());
                break;
            case STEP_COMPENSATING:
                log.warn("Compensating step: {}", event.getStepId());
                break;
        }
    }
}
```

### Example 3: Custom Event Enrichment

```java
@Component
public class CustomStepEventBridge extends StepEventPublisherBridge {

    public CustomStepEventBridge(
            EventPublisher eventPublisher,
            StepEventsProperties properties) {
        super(eventPublisher, properties);
    }

    @Override
    protected Map<String, Object> enrichEvent(StepEvent event) {
        Map<String, Object> enriched = super.enrichEvent(event);
        
        // Add custom metadata
        enriched.put("environment", System.getenv("ENV"));
        enriched.put("region", System.getenv("AWS_REGION"));
        enriched.put("service", "customer-service");
        
        // Add performance metrics
        if (event.getStartedAt() != null && event.getCompletedAt() != null) {
            long latency = Duration.between(
                event.getStartedAt(), 
                event.getCompletedAt()
            ).toMillis();
            enriched.put("latency_ms", latency);
        }
        
        return enriched;
    }
}
```

---

## Best Practices

### 1. Enable Step Events in Production

```yaml
firefly:
  stepevents:
    enabled: true  # Always enable for observability
    include-job-context: true
```

### 2. Use Meaningful SAGA Names

```java
Saga saga = Saga.builder()
    .name("customer-data-processing")  // Clear, descriptive name
    .build();
```

### 3. Handle Event Publishing Failures

```java
@Override
public void onStepEvent(StepEvent event) {
    try {
        eventPublisher.publish(enrichEvent(event), topic);
    } catch (Exception e) {
        log.error("Failed to publish step event", e);
        // Don't throw - event publishing should not break SAGA
    }
}
```

### 4. Monitor Step Event Latency

```java
private void publishWithMetrics(StepEvent event) {
    long startTime = System.currentTimeMillis();
    try {
        eventPublisher.publish(enrichEvent(event), topic);
    } finally {
        long latency = System.currentTimeMillis() - startTime;
        metrics.recordLatency("step.event.publish", latency);
    }
}
```

### 5. Use Structured Logging

```java
log.info("Step event published: saga={}, step={}, type={}, latency={}ms",
    event.getSagaName(),
    event.getStepId(),
    event.getType(),
    latency);
```

---

## Troubleshooting

### Issue 1: Events Not Publishing

**Symptoms:**
- SAGA executes but no events appear in EDA

**Solution:**
```yaml
# Check configuration
firefly:
  stepevents:
    enabled: true  # Must be true
  
  eda:
    publishers:
      - id: default
        type: KAFKA
        connection-id: kafka-main  # Must be configured

# Enable debug logging
logging:
  level:
    com.firefly.common.data.stepevents: DEBUG
```

### Issue 2: Event Publishing Delays

**Symptoms:**
- Events arrive late or out of order

**Solution:**
```java
// Use async publishing
@Async
public void onStepEvent(StepEvent event) {
    eventPublisher.publish(enrichEvent(event), topic);
}
```

### Issue 3: Missing Event Data

**Symptoms:**
- Events missing expected fields

**Solution:**
```java
// Ensure all fields are populated
private Map<String, Object> enrichEvent(StepEvent event) {
    Map<String, Object> enriched = new HashMap<>();
    
    // Required fields
    enriched.put("sagaName", Objects.requireNonNull(event.getSagaName()));
    enriched.put("sagaId", Objects.requireNonNull(event.getSagaId()));
    enriched.put("stepId", Objects.requireNonNull(event.getStepId()));
    
    // Optional fields with null checks
    if (event.getResult() != null) {
        enriched.put("result", event.getResult());
    }
    
    return enriched;
}
```

---

## See Also

- [Architecture](architecture.md) - SAGA integration architecture
- [Configuration](configuration.md) - SAGA configuration options
- [Examples](examples.md) - Complete SAGA examples
- [lib-transactional-engine Documentation](https://github.com/firefly-oss/lib-transactional-engine) - SAGA engine docs

