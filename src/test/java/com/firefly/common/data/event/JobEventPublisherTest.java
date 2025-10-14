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

package com.firefly.common.data.event;

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobEventPublisherTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private JobOrchestrationProperties properties;

    private JobEventPublisher jobEventPublisher;

    @BeforeEach
    void setUp() {
        jobEventPublisher = new JobEventPublisher(eventPublisher, properties);
    }

    @Test
    void publishJobStarted_WhenEventsEnabled_ShouldPublishEvent() {
        // Given
        when(properties.isPublishJobEvents()).thenReturn(true);
        when(properties.getJobEventsTopic()).thenReturn("test-topic");
        
        JobStageRequest request = JobStageRequest.builder()
                .jobType("test-job")
                .executionId("exec-123")
                .parameters(Map.of("key", "value"))
                .build();

        // When
        jobEventPublisher.publishJobStarted(request);

        // Then
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        JobEvent capturedEvent = eventCaptor.getValue();
        assertEquals("JOB_STARTED", capturedEvent.getEventType());
        assertEquals("test-job", capturedEvent.getJobType());
        assertEquals("exec-123", capturedEvent.getExecutionId());
        assertEquals(JobExecutionStatus.RUNNING, capturedEvent.getStatus());
        assertEquals("test-topic", capturedEvent.getTopic());
        assertNotNull(capturedEvent.getTimestamp());
    }

    @Test
    void publishJobStarted_WhenEventsDisabled_ShouldNotPublishEvent() {
        // Given
        when(properties.isPublishJobEvents()).thenReturn(false);
        
        JobStageRequest request = JobStageRequest.builder()
                .jobType("test-job")
                .executionId("exec-123")
                .build();

        // When
        jobEventPublisher.publishJobStarted(request);

        // Then
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishJobStageCompleted_WhenEventsEnabled_ShouldPublishEvent() {
        // Given
        when(properties.isPublishJobEvents()).thenReturn(true);
        when(properties.getJobEventsTopic()).thenReturn("test-topic");
        
        JobStageResponse response = JobStageResponse.builder()
                .executionId("exec-123")
                .success(true)
                .status(JobExecutionStatus.SUCCEEDED)
                .data(Map.of("result", "success"))
                .build();

        // When
        jobEventPublisher.publishJobStageCompleted(JobStage.START, response);

        // Then
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        JobEvent capturedEvent = eventCaptor.getValue();
        assertEquals("JOB_STAGE_START_COMPLETED", capturedEvent.getEventType());
        assertEquals("exec-123", capturedEvent.getExecutionId());
        assertEquals(JobExecutionStatus.SUCCEEDED, capturedEvent.getStatus());
        assertNotNull(capturedEvent.getData());
    }

    @Test
    void publishJobFailed_WhenEventsEnabled_ShouldPublishEvent() {
        // Given
        when(properties.isPublishJobEvents()).thenReturn(true);
        when(properties.getJobEventsTopic()).thenReturn("test-topic");
        
        RuntimeException exception = new RuntimeException("Test error");

        // When
        jobEventPublisher.publishJobFailed("exec-123", "test-job", "Error occurred", exception);

        // Then
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        JobEvent capturedEvent = eventCaptor.getValue();
        assertEquals("JOB_FAILED", capturedEvent.getEventType());
        assertEquals("test-job", capturedEvent.getJobType());
        assertEquals("exec-123", capturedEvent.getExecutionId());
        assertEquals(JobExecutionStatus.FAILED, capturedEvent.getStatus());
        assertTrue(capturedEvent.getMessage().contains("Error occurred"));
        
        Map<String, Object> data = capturedEvent.getData();
        assertNotNull(data);
        assertEquals("Error occurred", data.get("error"));
        assertEquals("RuntimeException", data.get("exceptionType"));
        assertEquals("Test error", data.get("exceptionMessage"));
    }

    @Test
    void publishJobCompleted_WhenEventsEnabled_ShouldPublishEvent() {
        // Given
        when(properties.isPublishJobEvents()).thenReturn(true);
        when(properties.getJobEventsTopic()).thenReturn("test-topic");
        
        Map<String, Object> results = Map.of("processed", 100, "status", "complete");

        // When
        jobEventPublisher.publishJobCompleted("exec-123", "test-job", results);

        // Then
        ArgumentCaptor<JobEvent> eventCaptor = ArgumentCaptor.forClass(JobEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        JobEvent capturedEvent = eventCaptor.getValue();
        assertEquals("JOB_COMPLETED", capturedEvent.getEventType());
        assertEquals("test-job", capturedEvent.getJobType());
        assertEquals("exec-123", capturedEvent.getExecutionId());
        assertEquals(JobExecutionStatus.SUCCEEDED, capturedEvent.getStatus());
        assertEquals("Job completed successfully", capturedEvent.getMessage());
        assertEquals(results, capturedEvent.getData());
    }
}