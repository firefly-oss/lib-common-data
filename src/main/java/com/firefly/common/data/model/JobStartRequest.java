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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP request model for starting a new job.
 * 
 * This is the DTO that clients send when calling POST /api/v1/jobs/start.
 * It contains only the fields that the client should provide.
 * 
 * The controller will internally convert this to a JobStageRequest for the service layer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to start a new data processing job")
public class JobStartRequest {

    /**
     * Input parameters for the job.
     * The structure depends on the specific job type being executed.
     */
    @NotNull(message = "Parameters are required")
    @Schema(
        description = "Input parameters for the job execution",
        example = "{\"customerId\": \"12345\", \"includeHistory\": true}",
        required = true
    )
    private Map<String, Object> parameters;

    /**
     * Request ID for tracing and correlation.
     * Optional but recommended for distributed tracing.
     */
    @Schema(
        description = "Optional request ID for tracing and correlation",
        example = "req-001"
    )
    private String requestId;

    /**
     * Initiator or user who triggered the request.
     * Useful for audit trails and authorization.
     */
    @Schema(
        description = "User or system that initiated the request",
        example = "user@example.com"
    )
    private String initiator;

    /**
     * Additional metadata.
     * Can be used for custom headers, tags, or other contextual information.
     */
    @Schema(
        description = "Additional metadata for the job",
        example = "{\"department\": \"sales\", \"priority\": \"high\"}"
    )
    private Map<String, String> metadata;
}

