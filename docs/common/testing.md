# Testing Guide

Comprehensive testing strategies for `lib-common-data`.

## Table of Contents

- [Overview](#overview)
- [Unit Testing](#unit-testing)
- [Integration Testing](#integration-testing)
- [Mocking Strategies](#mocking-strategies)
- [Reactor Testing](#reactor-testing)
- [Best Practices](#best-practices)

---

## Overview

Testing data jobs requires different strategies depending on what you're testing:

- **Unit Tests** - Test individual components in isolation
- **Integration Tests** - Test complete workflows with real dependencies
- **Contract Tests** - Verify API contracts
- **End-to-End Tests** - Test complete user scenarios

---

## Unit Testing

### Testing Services

```java
@ExtendWith(MockitoExtension.class)
class CustomerDataJobServiceTest {

    @Mock
    private DataJobService dataJobService;

    @InjectMocks
    private CustomerDataJobService customerDataJobService;

    @Test
    void shouldExtractCustomerDataSuccessfully() {
        // Given
        String customerId = "12345";
        String executionId = "exec-abc123";
        
        JobStageResponse startResponse = JobStageResponse.builder()
            .stage(JobStage.START)
            .executionId(executionId)
            .status(JobExecutionStatus.RUNNING)
            .success(true)
            .build();
        
        JobStageResponse checkResponse = JobStageResponse.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .status(JobExecutionStatus.SUCCEEDED)
            .success(true)
            .build();
        
        CustomerDTO expectedCustomer = CustomerDTO.builder()
            .customerId(customerId)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .build();
        
        JobStageResponse resultResponse = JobStageResponse.builder()
            .stage(JobStage.RESULT)
            .executionId(executionId)
            .status(JobExecutionStatus.SUCCEEDED)
            .data(Map.of("result", expectedCustomer))
            .success(true)
            .build();
        
        when(dataJobService.startJob(any(JobStageRequest.class)))
            .thenReturn(Mono.just(startResponse));
        when(dataJobService.checkJob(any(JobStageRequest.class)))
            .thenReturn(Mono.just(checkResponse));
        when(dataJobService.getJobResult(any(JobStageRequest.class)))
            .thenReturn(Mono.just(resultResponse));
        
        // When
        CustomerDTO result = customerDataJobService
            .extractCustomerData(customerId)
            .block();
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        
        verify(dataJobService).startJob(argThat(request -> 
            request.getStage() == JobStage.START &&
            request.getJobType().equals("customer-data-extraction")
        ));
        verify(dataJobService).checkJob(argThat(request ->
            request.getExecutionId().equals(executionId)
        ));
        verify(dataJobService).getJobResult(argThat(request ->
            request.getExecutionId().equals(executionId)
        ));
    }

    @Test
    void shouldHandleJobFailure() {
        // Given
        String customerId = "12345";
        String executionId = "exec-abc123";
        
        JobStageResponse startResponse = JobStageResponse.builder()
            .stage(JobStage.START)
            .executionId(executionId)
            .status(JobExecutionStatus.RUNNING)
            .success(true)
            .build();
        
        JobStageResponse checkResponse = JobStageResponse.builder()
            .stage(JobStage.CHECK)
            .executionId(executionId)
            .status(JobExecutionStatus.FAILED)
            .success(false)
            .message("Job execution failed")
            .build();
        
        when(dataJobService.startJob(any(JobStageRequest.class)))
            .thenReturn(Mono.just(startResponse));
        when(dataJobService.checkJob(any(JobStageRequest.class)))
            .thenReturn(Mono.just(checkResponse));
        
        // When/Then
        assertThatThrownBy(() -> 
            customerDataJobService.extractCustomerData(customerId).block())
            .isInstanceOf(JobExecutionException.class)
            .hasMessageContaining("Job execution failed");
    }
}
```

### Testing Mappers

```java
@SpringBootTest
class CustomerDataMapperTest {

    @Autowired
    private CustomerDataMapper mapper;

    @Test
    void shouldMapCustomerDataCorrectly() {
        // Given
        Map<String, Object> rawData = Map.of(
            "customer_id", "12345",
            "first_name", "John",
            "last_name", "Doe",
            "email_address", "john@example.com",
            "phone", "+1-555-0100"
        );

        // When
        CustomerDTO result = mapper.mapToTarget(rawData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo("12345");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getPhoneNumber()).isEqualTo("+1-555-0100");
    }

    @Test
    void shouldHandleMissingFields() {
        // Given
        Map<String, Object> rawData = Map.of(
            "customer_id", "12345",
            "first_name", "John"
            // Missing other fields
        );

        // When
        CustomerDTO result = mapper.mapToTarget(rawData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo("12345");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isNull();
        assertThat(result.getEmail()).isNull();
    }

    @Test
    void shouldHandleTypeConversions() {
        // Given
        Map<String, Object> rawData = Map.of(
            "customer_id", "12345",
            "first_name", "John",
            "last_name", "Doe",
            "email_address", "john@example.com",
            "phone", 5550100  // Integer instead of String
        );

        // When
        CustomerDTO result = mapper.mapToTarget(rawData);

        // Then
        assertThat(result.getPhoneNumber()).isEqualTo("5550100");
    }
}
```

### Testing Controllers

```java
@WebFluxTest(CustomerDataController.class)
class CustomerDataControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CustomerDataJobService customerDataJobService;

    @Test
    void shouldReturnCustomerData() {
        // Given
        String customerId = "12345";
        CustomerDTO expectedCustomer = CustomerDTO.builder()
            .customerId(customerId)
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .build();

        when(customerDataJobService.extractCustomerData(customerId))
            .thenReturn(Mono.just(expectedCustomer));

        // When/Then
        webTestClient.get()
            .uri("/api/v1/customers/{customerId}/data", customerId)
            .exchange()
            .expectStatus().isOk()
            .expectBody(CustomerDTO.class)
            .value(customer -> {
                assertThat(customer.getCustomerId()).isEqualTo(customerId);
                assertThat(customer.getFirstName()).isEqualTo("John");
                assertThat(customer.getLastName()).isEqualTo("Doe");
            });
    }

    @Test
    void shouldHandleNotFound() {
        // Given
        String customerId = "99999";
        when(customerDataJobService.extractCustomerData(customerId))
            .thenReturn(Mono.error(new CustomerNotFoundException(customerId)));

        // When/Then
        webTestClient.get()
            .uri("/api/v1/customers/{customerId}/data", customerId)
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

---

## Integration Testing

### Full Integration Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "firefly.data.orchestration.orchestrator-type=MOCK",
    "firefly.data.eda.enabled=false",
    "firefly.data.cqrs.enabled=false"
})
class CustomerDataIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DataJobService dataJobService;

    @Test
    void shouldProcessCustomerDataEndToEnd() {
        // Given
        String customerId = "12345";

        // When - Start job
        JobStageResponse startResponse = dataJobService.startJob(
            JobStageRequest.builder()
                .stage(JobStage.START)
                .jobType("customer-data-extraction")
                .parameters(Map.of("customerId", customerId))
                .build()
        ).block();

        // Then - Job started
        assertThat(startResponse).isNotNull();
        assertThat(startResponse.getExecutionId()).isNotNull();
        String executionId = startResponse.getExecutionId();

        // When - Check job status
        JobStageResponse checkResponse = dataJobService.checkJob(
            JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId(executionId)
                .build()
        ).block();

        // Then - Job completed
        assertThat(checkResponse).isNotNull();
        assertThat(checkResponse.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);

        // When - Get results
        JobStageResponse resultResponse = dataJobService.getJobResult(
            JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId(executionId)
                .targetDtoClass(CustomerDTO.class.getName())
                .build()
        ).block();

        // Then - Results returned
        assertThat(resultResponse).isNotNull();
        assertThat(resultResponse.getData()).containsKey("result");
        CustomerDTO customer = (CustomerDTO) resultResponse.getData().get("result");
        assertThat(customer.getCustomerId()).isEqualTo(customerId);
    }
}
```

---

## Mocking Strategies

### Mock JobOrchestrator

```java
@TestConfiguration
static class TestConfig {

    @Bean
    @Primary
    public JobOrchestrator mockJobOrchestrator() {
        JobOrchestrator mock = mock(JobOrchestrator.class);

        // Mock startJob
        when(mock.startJob(any(JobExecutionRequest.class)))
            .thenAnswer(invocation -> {
                JobExecutionRequest request = invocation.getArgument(0);
                JobExecution execution = JobExecution.builder()
                    .executionId("exec-" + UUID.randomUUID())
                    .jobDefinition(request.getJobDefinition())
                    .status(JobExecutionStatus.RUNNING)
                    .input(request.getInput())
                    .build();
                return Mono.just(execution);
            });

        // Mock checkJobStatus
        when(mock.checkJobStatus(anyString()))
            .thenReturn(Mono.just(JobExecutionStatus.SUCCEEDED));

        // Mock getJobExecution
        when(mock.getJobExecution(anyString()))
            .thenAnswer(invocation -> {
                String executionId = invocation.getArgument(0);
                JobExecution execution = JobExecution.builder()
                    .executionId(executionId)
                    .status(JobExecutionStatus.SUCCEEDED)
                    .output(Map.of(
                        "customer_id", "12345",
                        "first_name", "John",
                        "last_name", "Doe",
                        "email_address", "john@example.com"
                    ))
                    .build();
                return Mono.just(execution);
            });

        return mock;
    }
}
```

### Mock Event Publisher

```java
@TestConfiguration
static class TestConfig {

    @Bean
    @Primary
    public EventPublisher mockEventPublisher() {
        return mock(EventPublisher.class);
    }
}

@Test
void shouldPublishJobStartedEvent() {
    // Given
    String customerId = "12345";

    // When
    customerDataJobService.processCustomerDataAsync(customerId).block();

    // Then
    verify(eventPublisher).publish(
        argThat(event -> 
            event.get("customerId").equals(customerId) &&
            event.containsKey("executionId")
        ),
        eq("customer.data.job.started")
    );
}
```

---

## Reactor Testing

### Using StepVerifier

```java
@Test
void shouldExtractCustomerDataReactively() {
    // Given
    String customerId = "12345";
    CustomerDTO expectedCustomer = CustomerDTO.builder()
        .customerId(customerId)
        .firstName("John")
        .build();

    when(dataJobService.startJob(any()))
        .thenReturn(Mono.just(createStartResponse()));
    when(dataJobService.checkJob(any()))
        .thenReturn(Mono.just(createCheckResponse()));
    when(dataJobService.getJobResult(any()))
        .thenReturn(Mono.just(createResultResponse(expectedCustomer)));

    // When/Then
    StepVerifier.create(customerDataJobService.extractCustomerData(customerId))
        .expectNextMatches(customer -> 
            customer.getCustomerId().equals(customerId) &&
            customer.getFirstName().equals("John")
        )
        .verifyComplete();
}

@Test
void shouldHandleErrorsReactively() {
    // Given
    String customerId = "12345";
    when(dataJobService.startJob(any()))
        .thenReturn(Mono.error(new RuntimeException("Job failed")));

    // When/Then
    StepVerifier.create(customerDataJobService.extractCustomerData(customerId))
        .expectErrorMatches(error -> 
            error instanceof RuntimeException &&
            error.getMessage().contains("Job failed")
        )
        .verify();
}

@Test
void shouldTestPollingWithVirtualTime() {
    // Given
    String executionId = "exec-123";
    
    when(dataJobService.checkJob(any()))
        .thenReturn(
            Mono.just(createRunningResponse()),
            Mono.just(createRunningResponse()),
            Mono.just(createSucceededResponse())
        );

    // When/Then
    StepVerifier.withVirtualTime(() -> 
            pollingService.pollWithExponentialBackoff(executionId))
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(5))  // First poll
        .thenAwait(Duration.ofSeconds(10)) // Second poll
        .expectNextMatches(response -> 
            response.getStatus() == JobExecutionStatus.SUCCEEDED)
        .verifyComplete();
}
```

### Testing Flux Streams

```java
@Test
void shouldStreamProgressUpdates() {
    // Given
    String executionId = "exec-123";
    
    when(dataJobService.checkJob(any()))
        .thenReturn(
            Mono.just(createProgressResponse(25)),
            Mono.just(createProgressResponse(50)),
            Mono.just(createProgressResponse(75)),
            Mono.just(createProgressResponse(100))
        );

    // When/Then
    StepVerifier.create(progressService.streamProgress(executionId))
        .expectNextMatches(r -> r.getProgressPercentage() == 25)
        .expectNextMatches(r -> r.getProgressPercentage() == 50)
        .expectNextMatches(r -> r.getProgressPercentage() == 75)
        .expectNextMatches(r -> r.getProgressPercentage() == 100)
        .verifyComplete();
}
```

---

## Best Practices

### 1. Use Test Slices

```java
@WebFluxTest(CustomerDataController.class)  // Only web layer
@DataJpaTest  // Only JPA layer
@SpringBootTest  // Full application context
```

### 2. Isolate External Dependencies

```java
@TestPropertySource(properties = {
    "firefly.data.orchestration.orchestrator-type=MOCK",
    "firefly.data.eda.enabled=false"
})
```

### 3. Use Test Fixtures

```java
@TestConfiguration
static class TestFixtures {

    @Bean
    public CustomerDTO testCustomer() {
        return CustomerDTO.builder()
            .customerId("12345")
            .firstName("John")
            .lastName("Doe")
            .build();
    }

    @Bean
    public JobStageResponse testStartResponse() {
        return JobStageResponse.builder()
            .stage(JobStage.START)
            .executionId("exec-test")
            .status(JobExecutionStatus.RUNNING)
            .build();
    }
}
```

### 4. Test Error Scenarios

```java
@Test
void shouldHandleTimeout() {
    when(dataJobService.checkJob(any()))
        .thenReturn(Mono.delay(Duration.ofMinutes(35))
            .then(Mono.just(createResponse())));

    StepVerifier.create(
            customerDataJobService.extractCustomerData("12345")
                .timeout(Duration.ofMinutes(30))
        )
        .expectError(TimeoutException.class)
        .verify();
}
```

### 5. Verify Interactions

```java
@Test
void shouldRetryOnTransientFailure() {
    when(dataJobService.startJob(any()))
        .thenReturn(
            Mono.error(new TransientException()),
            Mono.error(new TransientException()),
            Mono.just(createStartResponse())
        );

    customerDataJobService.extractCustomerDataWithRetry("12345").block();

    verify(dataJobService, times(3)).startJob(any());
}
```

---

## See Also

- [Examples](examples.md) - Complete code examples
- [Getting Started](getting-started.md) - Setup guide
- [Reactor Testing Documentation](https://projectreactor.io/docs/test/release/reference/) - Official Reactor testing docs

