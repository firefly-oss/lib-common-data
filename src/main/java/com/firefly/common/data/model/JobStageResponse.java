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

package com.firefly.common.data.model;

import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response model for job stage operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStageResponse {

    /**
     * The stage that was executed.
     */
    private JobStage stage;

    /**
     * The execution ID.
     */
    private String executionId;

    /**
     * The current status of the job.
     */
    private JobExecutionStatus status;

    /**
     * Success indicator.
     */
    private boolean success;

    /**
     * Response message.
     */
    private String message;

    /**
     * Progress percentage (0-100) for CHECK stage.
     */
    private Integer progressPercentage;

    /**
     * Result data.
     */
    private Map<String, Object> data;

    /**
     * Error details if operation failed.
     */
    private String error;

    /**
     * Timestamp of the response.
     */
    private Instant timestamp;

    /**
     * Additional metadata.
     */
    private Map<String, String> metadata;

    /**
     * Gets the status, deriving from success flag if not set.
     */
    public JobExecutionStatus getStatus() {
        if (status != null) {
            return status;
        }
        return success ? JobExecutionStatus.COMPLETED : JobExecutionStatus.FAILED;
    }
    
    /**
     * Creates a success response.
     */
    public static JobStageResponse success(JobStage stage, String executionId, String message) {
        return JobStageResponse.builder()
                .stage(stage)
                .executionId(executionId)
                .success(true)
                .status(JobExecutionStatus.COMPLETED)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a failure response.
     */
    public static JobStageResponse failure(JobStage stage, String executionId, String error) {
        return JobStageResponse.builder()
                .stage(stage)
                .executionId(executionId)
                .success(false)
                .status(JobExecutionStatus.FAILED)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }
}
