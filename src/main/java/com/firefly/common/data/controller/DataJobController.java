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

import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

/**
 * Controller interface that core-data microservices should implement for job stage endpoints.
 * 
 * This interface defines the standard REST API endpoints for managing data processing jobs:
 * - POST /jobs/start: Start a new job
 * - GET /jobs/{executionId}/check: Check job status
 * - GET /jobs/{executionId}/collect: Collect job results
 * - GET /jobs/{executionId}/result: Get final results
 * 
 * Implementations should delegate to the DataJobService for business logic.
 */
@Tag(name = "Data Jobs", description = "Data processing job management endpoints")
@RequestMapping("/api/v1/jobs")
public interface DataJobController {

    /**
     * Starts a new data processing job.
     * 
     * @param request the job start request
     * @return a Mono emitting the response with execution details
     */
    @Operation(
        summary = "Start a new data processing job",
        description = "Initiates a new data processing job with the provided parameters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job started successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/start")
    Mono<JobStageResponse> startJob(
        @Valid @RequestBody JobStageRequest request
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
        @RequestParam(required = false) String requestId
    );
}
