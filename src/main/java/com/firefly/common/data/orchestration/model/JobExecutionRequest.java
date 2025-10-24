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

package com.firefly.common.data.orchestration.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a request to start a job execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionRequest {

    /**
     * The job definition identifier (e.g., state machine ARN for AWS Step Functions).
     */
    @NotBlank(message = "Job definition is required")
    private String jobDefinition;

    /**
     * Optional unique name for this execution (if not provided, will be auto-generated).
     */
    private String executionName;

    /**
     * Input parameters for the job execution in JSON format.
     */
    private Map<String, Object> input;

    /**
     * Request ID for tracing and correlation across distributed systems.
     * This ID can be used to track the request through multiple services.
     */
    private String requestId;

    /**
     * Initiator or user who triggered the job execution.
     * Useful for audit trails, authorization, and tracking who started the job.
     */
    private String initiator;

    /**
     * Optional trace header for distributed tracing (e.g., X-Amzn-Trace-Id for AWS X-Ray).
     * If not provided, can be auto-generated from requestId.
     */
    private String traceHeader;

    /**
     * Additional metadata for the job execution.
     * Can include tags, custom headers, or other contextual information.
     */
    private Map<String, String> metadata;
}
