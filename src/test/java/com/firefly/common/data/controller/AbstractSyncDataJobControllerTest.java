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

package com.firefly.common.data.controller;

import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.service.SyncDataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AbstractSyncDataJobController.
 */
@ExtendWith(MockitoExtension.class)
class AbstractSyncDataJobControllerTest {

    @Mock
    private SyncDataJobService syncDataJobService;

    private TestSyncDataJobController controller;

    @BeforeEach
    void setUp() {
        controller = new TestSyncDataJobController(syncDataJobService);

        lenient().when(syncDataJobService.getJobName()).thenReturn("TestSyncJob");
    }

    @Test
    void execute_shouldCallServiceWithCorrectRequest() {
        // Given
        Map<String, Object> parameters = Map.of("key", "value");
        String requestId = "req-001";
        String initiator = "test-user";
        
        JobStageResponse expectedResponse = JobStageResponse.builder()
                .success(true)
                .executionId("sync-123")
                .message("Success")
                .data(Map.of("result", "data"))
                .build();
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));
        
        // When
        Mono<JobStageResponse> result = controller.execute(parameters, requestId, initiator, null);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo("sync-123");
                    assertThat(response.getMessage()).isEqualTo("Success");
                })
                .verifyComplete();
        
        // Verify service was called with correct request
        ArgumentCaptor<JobStageRequest> requestCaptor = ArgumentCaptor.forClass(JobStageRequest.class);
        verify(syncDataJobService).execute(requestCaptor.capture());
        
        JobStageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getParameters()).isEqualTo(parameters);
        assertThat(capturedRequest.getRequestId()).isEqualTo(requestId);
        assertThat(capturedRequest.getInitiator()).isEqualTo(initiator);
    }

    @Test
    void execute_shouldHandleNullParameters() {
        // Given
        JobStageResponse expectedResponse = JobStageResponse.builder()
                .success(true)
                .executionId("sync-456")
                .message("Success with null parameters")
                .build();
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));
        
        // When
        Mono<JobStageResponse> result = controller.execute(null, null, null, null);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getExecutionId()).isEqualTo("sync-456");
                })
                .verifyComplete();
    }

    @Test
    void execute_shouldParseMetadataJson() {
        // Given
        Map<String, Object> parameters = Map.of("key", "value");
        String metadata = "{\"env\":\"prod\",\"version\":\"1.0\"}";
        
        JobStageResponse expectedResponse = JobStageResponse.builder()
                .success(true)
                .executionId("sync-789")
                .message("Success")
                .build();
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));
        
        // When
        Mono<JobStageResponse> result = controller.execute(parameters, null, null, metadata);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                })
                .verifyComplete();
        
        // Verify metadata was parsed
        ArgumentCaptor<JobStageRequest> requestCaptor = ArgumentCaptor.forClass(JobStageRequest.class);
        verify(syncDataJobService).execute(requestCaptor.capture());
        
        JobStageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNotNull();
        assertThat(capturedRequest.getMetadata()).containsEntry("env", "prod");
        assertThat(capturedRequest.getMetadata()).containsEntry("version", "1.0");
    }

    @Test
    void execute_shouldHandleInvalidMetadataJson() {
        // Given
        Map<String, Object> parameters = Map.of("key", "value");
        String invalidMetadata = "not-valid-json";
        
        JobStageResponse expectedResponse = JobStageResponse.builder()
                .success(true)
                .executionId("sync-999")
                .message("Success")
                .build();
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.just(expectedResponse));
        
        // When
        Mono<JobStageResponse> result = controller.execute(parameters, null, null, invalidMetadata);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                })
                .verifyComplete();
        
        // Verify metadata is empty map due to parse error
        ArgumentCaptor<JobStageRequest> requestCaptor = ArgumentCaptor.forClass(JobStageRequest.class);
        verify(syncDataJobService).execute(requestCaptor.capture());
        
        JobStageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isEmpty();
    }

    @Test
    void execute_shouldHandleServiceError() {
        // Given
        Map<String, Object> parameters = Map.of("key", "value");
        RuntimeException error = new RuntimeException("Service error");
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.error(error));
        
        // When
        Mono<JobStageResponse> result = controller.execute(parameters, null, null, null);
        
        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void execute_shouldHandleFailureResponse() {
        // Given
        Map<String, Object> parameters = Map.of("key", "value");
        
        JobStageResponse failureResponse = JobStageResponse.builder()
                .success(false)
                .executionId("sync-error")
                .error("Something went wrong")
                .message("Job failed")
                .build();
        
        when(syncDataJobService.execute(any(JobStageRequest.class)))
                .thenReturn(Mono.just(failureResponse));
        
        // When
        Mono<JobStageResponse> result = controller.execute(parameters, null, null, null);
        
        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isFalse();
                    assertThat(response.getError()).isEqualTo("Something went wrong");
                    assertThat(response.getMessage()).isEqualTo("Job failed");
                })
                .verifyComplete();
    }

    @Test
    void getSyncDataJobService_shouldReturnService() {
        // When
        SyncDataJobService service = controller.getSyncDataJobService();
        
        // Then
        assertThat(service).isEqualTo(syncDataJobService);
    }

    @Test
    void getObjectMapper_shouldReturnObjectMapper() {
        // When
        var objectMapper = controller.getObjectMapper();
        
        // Then
        assertThat(objectMapper).isNotNull();
    }

    /**
     * Test implementation of AbstractSyncDataJobController.
     */
    @RestController
    @RequestMapping("/api/v1/test-sync-job")
    @Tag(name = "Test Sync Job", description = "Test synchronous job endpoints")
    static class TestSyncDataJobController extends AbstractSyncDataJobController {

        public TestSyncDataJobController(SyncDataJobService syncDataJobService) {
            super(syncDataJobService);
        }
    }
}

