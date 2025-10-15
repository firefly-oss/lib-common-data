/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.data.integration;

import com.firefly.common.data.mapper.JobResultMapper;
import com.firefly.common.data.mapper.JobResultMapperRegistry;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.orchestration.model.JobExecution;
import com.firefly.common.data.orchestration.model.JobExecutionRequest;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.orchestration.port.JobOrchestrator;
import com.firefly.common.data.service.DataJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DataJobService with JobOrchestrator and MapperRegistry.
 */
class DataJobServiceIntegrationTest {

    private DataJobService dataJobService;
    private JobOrchestrator mockOrchestrator;
    private JobResultMapperRegistry mapperRegistry;

    // Test DTO
    static class CustomerDTO {
        private String customerId;
        private String name;
        private String email;

        public CustomerDTO() {}

        public CustomerDTO(String customerId, String name, String email) {
            this.customerId = customerId;
            this.name = name;
            this.email = email;
        }

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    // Test Mapper
    static class CustomerDataMapper implements JobResultMapper<Map<String, Object>, CustomerDTO> {
        @Override
        public CustomerDTO mapToTarget(Map<String, Object> source) {
            return new CustomerDTO(
                    (String) source.get("customerId"),
                    (String) source.get("name"),
                    (String) source.get("email")
            );
        }

        @Override
        public Class<CustomerDTO> getTargetType() {
            return CustomerDTO.class;
        }
    }

    // Mock Orchestrator
    static class MockJobOrchestrator implements JobOrchestrator {
        @Override
        public Mono<JobExecution> startJob(JobExecutionRequest request) {
            return Mono.just(JobExecution.builder()
                    .executionId("exec-123")
                    .executionName(request.getExecutionName())
                    .jobDefinition(request.getJobDefinition())
                    .status(JobExecutionStatus.RUNNING)
                    .startTime(Instant.now())
                    .input(request.getInput())
                    .build());
        }

        @Override
        public Mono<JobExecutionStatus> checkJobStatus(String executionId) {
            return Mono.just(JobExecutionStatus.SUCCEEDED);
        }

        @Override
        public Mono<JobExecutionStatus> stopJob(String executionId, String reason) {
            return Mono.just(JobExecutionStatus.ABORTED);
        }

        @Override
        public Mono<JobExecution> getJobExecution(String executionId) {
            return Mono.just(JobExecution.builder()
                    .executionId(executionId)
                    .status(JobExecutionStatus.SUCCEEDED)
                    .output(Map.of(
                            "customerId", "12345",
                            "name", "John Doe",
                            "email", "john@example.com"
                    ))
                    .build());
        }

        @Override
        public String getOrchestratorType() {
            return "MOCK";
        }
    }

    // Test Implementation of DataJobService
    @Service
    static class TestDataJobService implements DataJobService {
        private final JobOrchestrator orchestrator;
        private final JobResultMapperRegistry mapperRegistry;

        public TestDataJobService(JobOrchestrator orchestrator, JobResultMapperRegistry mapperRegistry) {
            this.orchestrator = orchestrator;
            this.mapperRegistry = mapperRegistry;
        }

        @Override
        public Mono<JobStageResponse> startJob(JobStageRequest request) {
            // Build JobExecutionRequest with all fields from JobStageRequest
            JobExecutionRequest execRequest = JobExecutionRequest.builder()
                    .jobDefinition(request.getJobType())
                    .executionName(request.getJobType() + "-" + System.currentTimeMillis())
                    .input(request.getParameters())
                    .requestId(request.getRequestId())
                    .initiator(request.getInitiator())
                    .metadata(request.getMetadata())
                    .build();

            return orchestrator.startJob(execRequest)
                    .map(execution -> JobStageResponse.builder()
                            .stage(JobStage.START)
                            .executionId(execution.getExecutionId())
                            .status(execution.getStatus())
                            .success(true)
                            .message("Job started successfully")
                            .timestamp(Instant.now())
                            .build());
        }

        @Override
        public Mono<JobStageResponse> checkJob(JobStageRequest request) {
            return orchestrator.checkJobStatus(request.getExecutionId())
                    .map(status -> JobStageResponse.builder()
                            .stage(JobStage.CHECK)
                            .executionId(request.getExecutionId())
                            .status(status)
                            .success(status != JobExecutionStatus.FAILED)
                            .progressPercentage(status == JobExecutionStatus.SUCCEEDED ? 100 : 50)
                            .timestamp(Instant.now())
                            .build());
        }

        @Override
        public Mono<JobStageResponse> collectJobResults(JobStageRequest request) {
            return orchestrator.getJobExecution(request.getExecutionId())
                    .map(execution -> JobStageResponse.builder()
                            .stage(JobStage.COLLECT)
                            .executionId(execution.getExecutionId())
                            .status(execution.getStatus())
                            .success(execution.getStatus() == JobExecutionStatus.SUCCEEDED)
                            .data(execution.getOutput())
                            .timestamp(Instant.now())
                            .build());
        }

        @Override
        public Mono<JobStageResponse> getJobResult(JobStageRequest request) {
            return collectJobResults(request)
                    .flatMap(collectResponse -> {
                        try {
                            Class<?> targetClass = Class.forName(request.getTargetDtoClass());
                            JobResultMapper<?, ?> mapper = mapperRegistry.getMapper(targetClass)
                                    .orElseThrow(() -> new RuntimeException("Mapper not found"));

                            @SuppressWarnings("unchecked")
                            JobResultMapper<Map<String, Object>, ?> typedMapper =
                                    (JobResultMapper<Map<String, Object>, ?>) mapper;

                            Object mappedResult = typedMapper.mapToTarget(collectResponse.getData());

                            return Mono.just(JobStageResponse.builder()
                                    .stage(JobStage.RESULT)
                                    .executionId(request.getExecutionId())
                                    .status(collectResponse.getStatus())
                                    .success(true)
                                    .data(Map.of("result", mappedResult))
                                    .timestamp(Instant.now())
                                    .build());
                        } catch (Exception e) {
                            return Mono.just(JobStageResponse.failure(
                                    JobStage.RESULT,
                                    request.getExecutionId(),
                                    "Failed to map results: " + e.getMessage()
                            ));
                        }
                    });
        }

        @Override
        public Mono<JobStageResponse> stopJob(JobStageRequest request, String reason) {
            return orchestrator.stopJob(request.getExecutionId(), reason)
                    .map(status -> JobStageResponse.builder()
                            .stage(JobStage.STOP)
                            .executionId(request.getExecutionId())
                            .status(status)
                            .success(true)
                            .message("Job stopped: " + (reason != null ? reason : "No reason provided"))
                            .timestamp(Instant.now())
                            .build());
        }
    }

    @BeforeEach
    void setUp() {
        mockOrchestrator = new MockJobOrchestrator();
        mapperRegistry = new JobResultMapperRegistry(List.of(new CustomerDataMapper()));
        dataJobService = new TestDataJobService(mockOrchestrator, mapperRegistry);
    }

    @Test
    void shouldCompleteFullJobLifecycle() {
        // Given - START stage
        JobStageRequest startRequest = JobStageRequest.builder()
                .stage(JobStage.START)
                .jobType("customer-data-extraction")
                .parameters(Map.of("customerId", "12345"))
                .build();

        // When - Start job
        StepVerifier.create(dataJobService.startJob(startRequest))
                .assertNext(response -> {
                    assertThat(response.getStage()).isEqualTo(JobStage.START);
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isNotNull();
                })
                .verifyComplete();

        // Given - CHECK stage
        JobStageRequest checkRequest = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-123")
                .build();

        // When - Check job
        StepVerifier.create(dataJobService.checkJob(checkRequest))
                .assertNext(response -> {
                    assertThat(response.getStage()).isEqualTo(JobStage.CHECK);
                    assertThat(response.getStatus()).isEqualTo(JobExecutionStatus.SUCCEEDED);
                    assertThat(response.getProgressPercentage()).isEqualTo(100);
                })
                .verifyComplete();

        // Given - COLLECT stage
        JobStageRequest collectRequest = JobStageRequest.builder()
                .stage(JobStage.COLLECT)
                .executionId("exec-123")
                .build();

        // When - Collect results
        StepVerifier.create(dataJobService.collectJobResults(collectRequest))
                .assertNext(response -> {
                    assertThat(response.getStage()).isEqualTo(JobStage.COLLECT);
                    assertThat(response.getData()).isNotNull();
                    assertThat(response.getData()).containsKey("customerId");
                })
                .verifyComplete();

        // Given - RESULT stage
        JobStageRequest resultRequest = JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId("exec-123")
                .targetDtoClass(CustomerDTO.class.getName())
                .build();

        // When - Get final result
        StepVerifier.create(dataJobService.getJobResult(resultRequest))
                .assertNext(response -> {
                    assertThat(response.getStage()).isEqualTo(JobStage.RESULT);
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getData()).containsKey("result");
                    
                    Object result = response.getData().get("result");
                    assertThat(result).isInstanceOf(CustomerDTO.class);
                    
                    CustomerDTO customer = (CustomerDTO) result;
                    assertThat(customer.getCustomerId()).isEqualTo("12345");
                    assertThat(customer.getName()).isEqualTo("John Doe");
                    assertThat(customer.getEmail()).isEqualTo("john@example.com");
                })
                .verifyComplete();
    }
}

