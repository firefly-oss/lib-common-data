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
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response from a job stage operation")
public class JobStageResponse {

    /**
     * The stage that was executed.
     */
    @Schema(description = "The job stage that was executed", example = "START")
    private JobStage stage;

    /**
     * The execution ID.
     */
    @Schema(description = "Unique identifier for the job execution", example = "exec-123-456")
    private String executionId;

    /**
     * The current status of the job.
     */
    @Schema(description = "Current status of the job execution", example = "RUNNING")
    private JobExecutionStatus status;

    /**
     * Success indicator.
     */
    @Schema(description = "Whether the operation was successful", example = "true")
    private boolean success;

    /**
     * Response message.
     */
    @Schema(description = "Human-readable message about the operation", example = "Job started successfully")
    private String message;

    /**
     * Progress percentage (0-100) for CHECK stage.
     */
    @Schema(description = "Progress percentage (0-100), primarily used in CHECK stage", example = "75")
    private Integer progressPercentage;

    /**
     * Result data.
     */
    @Schema(description = "Result data from the job operation")
    private Map<String, Object> data;

    /**
     * Error details if operation failed.
     */
    @Schema(description = "Error message if the operation failed", example = "Job execution not found")
    private String error;

    /**
     * Timestamp of the response.
     */
    @Schema(description = "Timestamp when the response was generated", example = "2025-10-15T10:30:00Z")
    private Instant timestamp;

    /**
     * Additional metadata.
     */
    @Schema(description = "Additional metadata about the operation")
    private Map<String, String> metadata;

    /**
     * Gets the status, deriving from success flag if not set.
     */
    public JobExecutionStatus getStatus() {
        if (status != null) {
            return status;
        }
        return success ? JobExecutionStatus.SUCCEEDED : JobExecutionStatus.FAILED;
    }
    
    /**
     * Creates a success response.
     */
    public static JobStageResponse success(JobStage stage, String executionId, String message) {
        return JobStageResponse.builder()
                .stage(stage)
                .executionId(executionId)
                .success(true)
                .status(JobExecutionStatus.SUCCEEDED)
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
