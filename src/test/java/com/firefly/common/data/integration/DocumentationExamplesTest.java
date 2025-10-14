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

import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class that validates all examples from the documentation work correctly.
 * This ensures that the code examples in the docs are accurate and functional.
 */
class DocumentationExamplesTest {

    @Test
    void jobStageRequest_ExamplesFromDocumentation_ShouldCompileAndWork() {
        // Example from API Reference documentation - startJob
        JobStageRequest startRequest = JobStageRequest.builder()
                .stage(JobStage.START)
                .jobType("customer-data-extraction")
                .parameters(Map.of("customerId", "12345"))
                .build();

        assertNotNull(startRequest);
        assertEquals(JobStage.START, startRequest.getStage());
        assertEquals("customer-data-extraction", startRequest.getJobType());
        assertNotNull(startRequest.getParameters());
        assertEquals("12345", startRequest.getParameters().get("customerId"));

        // Example from API Reference documentation - checkJob
        JobStageRequest checkRequest = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-abc123")
                .build();

        assertNotNull(checkRequest);
        assertEquals(JobStage.CHECK, checkRequest.getStage());
        assertEquals("exec-abc123", checkRequest.getExecutionId());

        // Example from API Reference documentation - collectJobResults
        JobStageRequest collectRequest = JobStageRequest.builder()
                .stage(JobStage.COLLECT)
                .executionId("exec-abc123")
                .build();

        assertNotNull(collectRequest);
        assertEquals(JobStage.COLLECT, collectRequest.getStage());
        assertEquals("exec-abc123", collectRequest.getExecutionId());

        // Example from API Reference documentation - getJobResult
        JobStageRequest resultRequest = JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId("exec-abc123")
                .targetDtoClass("com.example.dto.CustomerDTO")
                .build();

        assertNotNull(resultRequest);
        assertEquals(JobStage.RESULT, resultRequest.getStage());
        assertEquals("exec-abc123", resultRequest.getExecutionId());
        assertEquals("com.example.dto.CustomerDTO", resultRequest.getTargetDtoClass());
    }

    @Test
    void jobStageResponse_StaticFactoryMethods_ShouldWorkAsDocumented() {
        // Test success response factory method
        JobStageResponse successResponse = JobStageResponse.success(
                JobStage.START, 
                "exec-abc123", 
                "Job started successfully"
        );

        assertNotNull(successResponse);
        assertTrue(successResponse.isSuccess());
        assertEquals(JobStage.START, successResponse.getStage());
        assertEquals("exec-abc123", successResponse.getExecutionId());
        assertEquals("Job started successfully", successResponse.getMessage());
        assertEquals(JobExecutionStatus.SUCCEEDED, successResponse.getStatus());
        assertNotNull(successResponse.getTimestamp());

        // Test failure response factory method
        JobStageResponse failureResponse = JobStageResponse.failure(
                JobStage.CHECK, 
                "exec-abc123", 
                "Job execution not found"
        );

        assertNotNull(failureResponse);
        assertFalse(failureResponse.isSuccess());
        assertEquals(JobStage.CHECK, failureResponse.getStage());
        assertEquals("exec-abc123", failureResponse.getExecutionId());
        assertEquals("Job execution not found", failureResponse.getError());
        assertEquals(JobExecutionStatus.FAILED, failureResponse.getStatus());
        assertNotNull(failureResponse.getTimestamp());
    }

    @Test
    void jobStage_EnumValues_ShouldMatchDocumentation() {
        // Verify all documented job stages exist
        assertEquals("START", JobStage.START.name());
        assertEquals("CHECK", JobStage.CHECK.name());
        assertEquals("COLLECT", JobStage.COLLECT.name());
        assertEquals("RESULT", JobStage.RESULT.name());
        assertEquals("ALL", JobStage.ALL.name());
    }

    @Test
    void jobExecutionStatus_EnumValues_ShouldMatchDocumentation() {
        // Verify documented status values exist
        assertNotNull(JobExecutionStatus.RUNNING);
        assertNotNull(JobExecutionStatus.SUCCEEDED);
        assertNotNull(JobExecutionStatus.FAILED);
    }

    @Test
    void jobStageRequest_AllDocumentedFields_ShouldBeAccessible() {
        // Create request with all documented fields
        JobStageRequest fullRequest = JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId("exec-123")
                .jobType("data-processing")
                .parameters(Map.of("param1", "value1", "param2", 42))
                .requestId("req-456")
                .initiator("user123")
                .metadata(Map.of("meta1", "metaValue1"))
                .targetDtoClass("com.example.CustomerDTO")
                .mapperName("CustomerDataMapperV2")
                .build();

        // Verify all fields are accessible as documented
        assertEquals(JobStage.RESULT, fullRequest.getStage());
        assertEquals("exec-123", fullRequest.getExecutionId());
        assertEquals("data-processing", fullRequest.getJobType());
        
        assertNotNull(fullRequest.getParameters());
        assertEquals("value1", fullRequest.getParameters().get("param1"));
        assertEquals(42, fullRequest.getParameters().get("param2"));
        
        assertEquals("req-456", fullRequest.getRequestId());
        assertEquals("user123", fullRequest.getInitiator());
        
        assertNotNull(fullRequest.getMetadata());
        assertEquals("metaValue1", fullRequest.getMetadata().get("meta1"));
        
        assertEquals("com.example.CustomerDTO", fullRequest.getTargetDtoClass());
        assertEquals("CustomerDataMapperV2", fullRequest.getMapperName());
    }

    @Test
    void jobStageResponse_AllDocumentedFields_ShouldBeAccessible() {
        // Create response with all documented fields
        JobStageResponse fullResponse = JobStageResponse.builder()
                .stage(JobStage.CHECK)
                .executionId("exec-789")
                .status(JobExecutionStatus.RUNNING)
                .success(true)
                .message("Job is running")
                .progressPercentage(75)
                .data(Map.of("processedRecords", 1500, "remainingRecords", 500))
                .error(null)
                .metadata(Map.of("nodeId", "worker-1", "region", "us-east-1"))
                .build();

        // Verify all fields are accessible as documented
        assertEquals(JobStage.CHECK, fullResponse.getStage());
        assertEquals("exec-789", fullResponse.getExecutionId());
        assertEquals(JobExecutionStatus.RUNNING, fullResponse.getStatus());
        assertTrue(fullResponse.isSuccess());
        assertEquals("Job is running", fullResponse.getMessage());
        assertEquals(Integer.valueOf(75), fullResponse.getProgressPercentage());
        
        assertNotNull(fullResponse.getData());
        assertEquals(1500, fullResponse.getData().get("processedRecords"));
        assertEquals(500, fullResponse.getData().get("remainingRecords"));
        
        assertNull(fullResponse.getError());
        
        assertNotNull(fullResponse.getMetadata());
        assertEquals("worker-1", fullResponse.getMetadata().get("nodeId"));
        assertEquals("us-east-1", fullResponse.getMetadata().get("region"));
    }

    @Test
    void jobStageResponse_GetStatusMethod_ShouldWorkAsDocumented() {
        // Test status derivation when status field is null
        JobStageResponse successResponseWithoutStatus = JobStageResponse.builder()
                .success(true)
                .build();
        assertEquals(JobExecutionStatus.SUCCEEDED, successResponseWithoutStatus.getStatus());

        JobStageResponse failureResponseWithoutStatus = JobStageResponse.builder()
                .success(false)
                .build();
        assertEquals(JobExecutionStatus.FAILED, failureResponseWithoutStatus.getStatus());

        // Test status field takes precedence when set
        JobStageResponse responseWithExplicitStatus = JobStageResponse.builder()
                .success(false)
                .status(JobExecutionStatus.RUNNING)
                .build();
        assertEquals(JobExecutionStatus.RUNNING, responseWithExplicitStatus.getStatus());
    }
}