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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for publishing job lifecycle events through Spring's event mechanism.
 * These events can be consumed by EDA components or other parts of the system.
 * 
 * This service is automatically configured when job orchestration is enabled
 * and publish-job-events is set to true.
 */
@Service
@Slf4j
public class JobEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final JobOrchestrationProperties properties;

    public JobEventPublisher(ApplicationEventPublisher eventPublisher, 
                           JobOrchestrationProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Publishes a job started event.
     */
    public void publishJobStarted(JobStageRequest request) {
        if (!properties.isPublishJobEvents()) {
            return;
        }

        JobEvent event = createJobEvent(
            "JOB_STARTED",
            request.getJobType(),
            request.getExecutionId(),
            JobExecutionStatus.RUNNING,
            "Job started",
            request.getParameters()
        );

        publishEvent(event);
        log.debug("Published job started event for execution: {}", request.getExecutionId());
    }

    /**
     * Publishes a job stage completed event.
     */
    public void publishJobStageCompleted(JobStage stage, JobStageResponse response) {
        if (!properties.isPublishJobEvents()) {
            return;
        }

        String eventType = "JOB_STAGE_" + stage.name() + "_COMPLETED";
        
        JobEvent event = createJobEvent(
            eventType,
            null, // Job type not available in response
            response.getExecutionId(),
            response.getStatus(),
            response.isSuccess() ? "Stage completed successfully" : "Stage failed: " + response.getError(),
            response.getData()
        );

        publishEvent(event);
        log.debug("Published job stage completed event for execution: {}, stage: {}, success: {}", 
                response.getExecutionId(), stage, response.isSuccess());
    }

    /**
     * Publishes a job failed event.
     */
    public void publishJobFailed(String executionId, String jobType, String error, Throwable exception) {
        if (!properties.isPublishJobEvents()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("error", error);
        if (exception != null) {
            data.put("exceptionType", exception.getClass().getSimpleName());
            data.put("exceptionMessage", exception.getMessage());
        }

        JobEvent event = createJobEvent(
            "JOB_FAILED",
            jobType,
            executionId,
            JobExecutionStatus.FAILED,
            "Job failed: " + error,
            data
        );

        publishEvent(event);
        log.debug("Published job failed event for execution: {}", executionId);
    }

    /**
     * Publishes a job completed event.
     */
    public void publishJobCompleted(String executionId, String jobType, Map<String, Object> results) {
        if (!properties.isPublishJobEvents()) {
            return;
        }

        JobEvent event = createJobEvent(
            "JOB_COMPLETED",
            jobType,
            executionId,
            JobExecutionStatus.COMPLETED,
            "Job completed successfully",
            results
        );

        publishEvent(event);
        log.debug("Published job completed event for execution: {}", executionId);
    }

    private JobEvent createJobEvent(String eventType,
                                   String jobType,
                                   String executionId,
                                   JobExecutionStatus status,
                                   String message,
                                   Map<String, Object> data) {
        
        return JobEvent.builder()
                .eventType(eventType)
                .jobType(jobType)
                .executionId(executionId)
                .status(status)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .topic(properties.getJobEventsTopic())
                .build();
    }

    private void publishEvent(JobEvent event) {
        try {
            eventPublisher.publishEvent(event);
            log.trace("Published event: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish job event: {}", event, e);
            // Don't rethrow - event publishing failures should not break the main flow
        }
    }
}