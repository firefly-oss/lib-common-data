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

package com.firefly.common.data.service;

import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import reactor.core.publisher.Mono;

/**
 * Service interface that core-data microservices must implement to handle job stages.
 * 
 * This interface defines the standard lifecycle methods for data processing jobs:
 * - START: Initialize and start a data processing job
 * - CHECK: Monitor the progress of a running job
 * - COLLECT: Gather intermediate or final results from a job
 * - RESULT: Retrieve the final results and cleanup
 * 
 * Implementations should handle the specific business logic for each stage
 * and coordinate with the job orchestrator as needed.
 */
public interface DataJobService {

    /**
     * Starts a new data processing job.
     * 
     * This method should:
     * - Validate input parameters
     * - Initialize resources needed for the job
     * - Trigger the job execution via the orchestrator
     * - Return job metadata for tracking
     * 
     * @param request the job start request containing input parameters
     * @return a Mono emitting the response with execution details
     */
    Mono<JobStageResponse> startJob(JobStageRequest request);

    /**
     * Checks the status and progress of a running job.
     * 
     * This method should:
     * - Query the orchestrator for job status
     * - Gather progress metrics
     * - Check for any errors or issues
     * - Return current state information
     * 
     * @param request the check request containing execution ID
     * @return a Mono emitting the response with status and progress
     */
    Mono<JobStageResponse> checkJob(JobStageRequest request);

    /**
     * Collects intermediate or final raw results from a job.
     * 
     * This method should:
     * - Retrieve raw processed data from temporary storage or job execution
     * - Validate data quality and completeness
     * - Return the raw/unprocessed data in its original format
     * - No transformation or mapping applied at this stage
     * 
     * The raw data is typically stored in the response's data map with a key
     * indicating the data format (e.g., "rawData", "jobOutput").
     * 
     * @param request the collect request containing execution ID
     * @return a Mono emitting the response with raw collected data
     */
    Mono<JobStageResponse> collectJobResults(JobStageRequest request);

    /**
     * Retrieves final results, performs mapping/transformation, and cleanup.
     *
     * This method should:
     * 1. Retrieve the raw results (possibly by calling collectJobResults internally)
     * 2. Apply transformation using the configured mapper (MapStruct)
     * 3. Map raw data to target DTO specified in the request
     * 4. Clean up temporary resources
     * 5. Return the mapped/transformed final results
     *
     * This is the final stage where business logic transformation happens.
     * The response should contain the mapped DTO in the data map.
     *
     * @param request the result request containing execution ID and target DTO class info
     * @return a Mono emitting the response with transformed/mapped final results
     */
    Mono<JobStageResponse> getJobResult(JobStageRequest request);

    /**
     * Stops a running job execution.
     *
     * This method should:
     * - Request the orchestrator to stop the job execution
     * - Clean up any resources associated with the job
     * - Return confirmation of the stop operation
     *
     * @param request the stop request containing execution ID
     * @param reason optional reason for stopping the job
     * @return a Mono emitting the response with stop confirmation
     */
    Mono<JobStageResponse> stopJob(JobStageRequest request, String reason);

    /**
     * Gets the job stage this service implementation is designed for.
     *
     * @return the job stage
     */
    default JobStage getSupportedStage() {
        return JobStage.ALL;
    }
}
