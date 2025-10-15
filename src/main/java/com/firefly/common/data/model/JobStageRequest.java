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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request model for job stage operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStageRequest {

    /**
     * The stage being executed.
     */
    @NotNull(message = "Job stage is required")
    private JobStage stage;

    /**
     * The execution ID (required for CHECK, COLLECT, and RESULT stages).
     */
    private String executionId;

    /**
     * The job type or category.
     */
    private String jobType;

    /**
     * Input parameters for the job stage.
     */
    private Map<String, Object> parameters;

    /**
     * Request ID for tracing and correlation.
     */
    private String requestId;

    /**
     * Initiator or user who triggered the request.
     */
    private String initiator;

    /**
     * Additional metadata.
     */
    private Map<String, String> metadata;

    /**
     * The fully qualified class name of the target DTO for the RESULT stage.
     * 
     * This is used during getJobResult() to identify which MapStruct mapper
     * should be used to transform the raw data into the target DTO.
     * 
     * Example: "com.firefly.customer.dto.CustomerDataDTO"
     */
    private String targetDtoClass;

    /**
     * The name of the specific mapper to use for transformation.
     *
     * If not specified, the mapper will be auto-selected based on targetDtoClass.
     * This is useful when multiple mappers exist for the same target type.
     *
     * Example: "CustomerDataMapperV2"
     */
    private String mapperName;

    /**
     * Reason for stopping a job (used in STOP stage).
     *
     * This field is optional and provides context for why a job was stopped.
     *
     * Example: "User requested cancellation", "Timeout exceeded"
     */
    private String reason;
}
