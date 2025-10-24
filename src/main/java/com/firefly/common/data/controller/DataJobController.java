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

import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.model.JobStartRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controller interface that core-data microservices should implement for job stage endpoints.
 *
 * This interface defines the standard REST API endpoints for managing data processing jobs:
 * - POST /jobs/start: Start a new job
 * - GET /jobs/{executionId}/check: Check job status
 * - GET /jobs/{executionId}/collect: Collect job results
 * - GET /jobs/{executionId}/result: Get final results
 * - POST /jobs/{executionId}/stop: Stop a running job
 *
 * Implementations should delegate to the DataJobService for business logic.
 *
 * IMPORTANT: Implementations MUST add their own @Tag annotation to specify the Swagger tag.
 * Example:
 * <pre>
 * {@code
 * @RestController
 * @Tag(name = "Data Job - CustomerData", description = "Customer data processing job management endpoints")
 * public class CustomerDataJobController extends AbstractDataJobController {
 *     // ...
 * }
 * }
 * </pre>
 */
@RequestMapping("/api/v1/jobs")

public interface DataJobController {

    /**
     * Starts a new data processing job.
     *
     * @param request the job start request containing parameters and metadata
     * @return a Mono emitting the response with execution details
     */
    @Operation(
        summary = "Start a new data processing job",
        description = "Initiates a new data processing job with the provided parameters. " +
                     "The job type and stage are determined by the endpoint and service implementation."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job started successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/start")
    Mono<JobStageResponse> startJob(
        @Valid @RequestBody JobStartRequest request
    );

    /**
     * Checks the status of a running job.
     * 
     * @param executionId the execution ID
     * @param requestId optional request ID for tracing
     * @return a Mono emitting the response with status information
     */
    @Operation(
        summary = "Check job status",
        description = "Retrieves the current status and progress of a running job"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Job execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{executionId}/check")
    Mono<JobStageResponse> checkJob(
        @Parameter(description = "The job execution ID", required = true)
        @PathVariable String executionId,
        
        @Parameter(description = "Optional request ID for tracing")
        @RequestParam(required = false) String requestId
    );

    /**
     * Collects results from a job.
     * 
     * @param executionId the execution ID
     * @param requestId optional request ID for tracing
     * @return a Mono emitting the response with collected data
     */
    @Operation(
        summary = "Collect job results",
        description = "Gathers intermediate or final results from a job execution"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Results collected successfully"),
        @ApiResponse(responseCode = "404", description = "Job execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{executionId}/collect")
    Mono<JobStageResponse> collectJobResults(
        @Parameter(description = "The job execution ID", required = true)
        @PathVariable String executionId,
        
        @Parameter(description = "Optional request ID for tracing")
        @RequestParam(required = false) String requestId
    );

    /**
     * Retrieves final results from a job.
     * 
     * @param executionId the execution ID
     * @param requestId optional request ID for tracing
     * @return a Mono emitting the response with final results
     */
    @Operation(
        summary = "Get job final results",
        description = "Retrieves the final results from a completed job and performs cleanup"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Results retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Job execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{executionId}/result")
    Mono<JobStageResponse> getJobResult(
        @Parameter(description = "The job execution ID", required = true)
        @PathVariable String executionId,

        @Parameter(description = "Optional request ID for tracing")
        @RequestParam(required = false) String requestId,

        @Parameter(description = "Target DTO class for result mapping")
        @RequestParam(required = false) String targetDtoClass
    );

    /**
     * Stops a running job execution.
     *
     * @param executionId the execution ID
     * @param requestId optional request ID for tracing
     * @param reason optional reason for stopping the job
     * @return a Mono emitting the response with stop confirmation
     */
    @Operation(
        summary = "Stop a running job",
        description = "Stops a running job execution. The job will be terminated and cannot be resumed."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job stopped successfully"),
        @ApiResponse(responseCode = "404", description = "Job execution not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/{executionId}/stop")
    Mono<JobStageResponse> stopJob(
        @Parameter(description = "The job execution ID", required = true)
        @PathVariable String executionId,

        @Parameter(description = "Optional request ID for tracing")
        @RequestParam(required = false) String requestId,

        @Parameter(description = "Optional reason for stopping the job")
        @RequestParam(required = false) String reason
    );
}
