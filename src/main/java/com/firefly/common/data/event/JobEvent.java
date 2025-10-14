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

import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Job lifecycle event model.
 * 
 * This event is published through Spring's event mechanism for job lifecycle changes
 * such as job started, stage completed, job failed, and job completed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent {

    /**
     * Type of the event (e.g., "JOB_STARTED", "JOB_STAGE_START_COMPLETED", "JOB_FAILED").
     */
    private String eventType;

    /**
     * Type of the job being executed.
     */
    private String jobType;

    /**
     * Unique execution ID for the job.
     */
    private String executionId;

    /**
     * Current status of the job execution.
     */
    private JobExecutionStatus status;

    /**
     * Human-readable message describing the event.
     */
    private String message;

    /**
     * Additional data associated with the event.
     * This can contain job parameters, results, or error details.
     */
    private Map<String, Object> data;

    /**
     * When the event occurred.
     */
    private Instant timestamp;

    /**
     * Target topic for the event (if published via EDA).
     */
    private String topic;
}