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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller interface for synchronous data processing jobs.
 *
 * <p>This interface defines a simple REST API endpoint for jobs that execute
 * synchronously and return results immediately, as opposed to {@link DataJobController}
 * which handles asynchronous jobs with multiple stages.</p>
 *
 * <p><b>API Endpoint:</b></p>
 * <ul>
 *   <li>POST /execute: Execute a synchronous job and return results immediately</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/customer-enrichment")
 * @Tag(name = "Customer Enrichment", description = "Customer data enrichment endpoints")
 * public class CustomerEnrichmentController extends AbstractSyncDataJobController {
 *     
 *     public CustomerEnrichmentController(CustomerEnrichmentService service) {
 *         super(service);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Request Format:</b></p>
 * <pre>{@code
 * POST /api/v1/customer-enrichment/execute
 * {
 *   "parameters": {
 *     "customerId": "12345",
 *     "includeHistory": true
 *   },
 *   "requestId": "req-001",
 *   "initiator": "user@example.com"
 * }
 * }</pre>
 *
 * <p><b>Response Format:</b></p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "executionId": "sync-abc-123",
 *   "data": {
 *     "customer": { ... }
 *   },
 *   "message": "Customer enriched successfully",
 *   "timestamp": "2025-10-16T10:30:00Z"
 * }
 * }</pre>
 *
 * <p><b>IMPORTANT:</b> Implementations MUST add their own @Tag annotation to specify the Swagger tag.</p>
 *
 * @see AbstractSyncDataJobController
 * @see com.firefly.common.data.service.SyncDataJobService
 */
@RequestMapping("/api/v1")
public interface SyncDataJobController {

    /**
     * Executes a synchronous data processing job and returns results immediately.
     *
     * <p>This endpoint:</p>
     * <ul>
     *   <li>Accepts job parameters in the request body</li>
     *   <li>Executes the job synchronously</li>
     *   <li>Returns results in the response (typically within seconds)</li>
     *   <li>Should complete within a reasonable timeout (< 30 seconds)</li>
     * </ul>
     *
     * <p><b>Request Body:</b></p>
     * <ul>
     *   <li>{@code parameters} - Map of input parameters for the job</li>
     *   <li>{@code requestId} - Optional request ID for tracing and correlation</li>
     *   <li>{@code initiator} - Optional user/system that initiated the request</li>
     *   <li>{@code metadata} - Optional additional metadata</li>
     * </ul>
     *
     * <p><b>Response:</b></p>
     * <ul>
     *   <li>{@code success} - Whether the job completed successfully</li>
     *   <li>{@code executionId} - Unique identifier for this execution</li>
     *   <li>{@code data} - Result data (if successful)</li>
     *   <li>{@code message} - Human-readable message</li>
     *   <li>{@code error} - Error message (if failed)</li>
     *   <li>{@code timestamp} - When the response was generated</li>
     * </ul>
     *
     * @param parameters the job parameters (required)
     * @param requestId optional request ID for tracing
     * @param initiator optional user/system that initiated the request
     * @param metadata optional additional metadata
     * @return a Mono emitting the response with execution results
     */
    @Operation(
        summary = "Execute synchronous job",
        description = "Executes a synchronous data processing job and returns results immediately. " +
                     "The job should complete within a reasonable time (typically < 30 seconds)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job executed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/execute")
    Mono<JobStageResponse> execute(
        @Parameter(description = "Job input parameters", required = true)
        @Valid @RequestBody Map<String, Object> parameters,

        @Parameter(description = "Optional request ID for tracing")
        @RequestParam(required = false) String requestId,

        @Parameter(description = "Optional user/system that initiated the request")
        @RequestParam(required = false) String initiator,

        @Parameter(description = "Optional additional metadata as JSON string")
        @RequestParam(required = false) String metadata
    );
}

