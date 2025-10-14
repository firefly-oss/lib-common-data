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

package com.firefly.common.data.orchestration.port;

import com.firefly.common.data.orchestration.model.JobExecution;
import com.firefly.common.data.orchestration.model.JobExecutionRequest;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import reactor.core.publisher.Mono;

/**
 * Port interface for job orchestrators (e.g., AWS Step Functions, Azure Durable Functions, etc.).
 * 
 * This interface defines the contract for interacting with workflow orchestration services
 * to manage long-running data processing jobs in core-data microservices.
 * 
 * Implementations should handle the specific details of the underlying orchestration service.
 */
public interface JobOrchestrator {

    /**
     * Starts a new job execution.
     * 
     * @param request the job execution request containing job definition and input parameters
     * @return a Mono emitting the job execution information including execution ID
     */
    Mono<JobExecution> startJob(JobExecutionRequest request);

    /**
     * Checks the status of a running job execution.
     * 
     * @param executionId the unique identifier of the job execution
     * @return a Mono emitting the current status of the job execution
     */
    Mono<JobExecutionStatus> checkJobStatus(String executionId);

    /**
     * Stops a running job execution.
     * 
     * @param executionId the unique identifier of the job execution to stop
     * @param reason optional reason for stopping the execution
     * @return a Mono emitting the final status of the stopped job
     */
    Mono<JobExecutionStatus> stopJob(String executionId, String reason);

    /**
     * Retrieves the execution history of a job.
     * 
     * @param executionId the unique identifier of the job execution
     * @return a Mono emitting the complete job execution details including history
     */
    Mono<JobExecution> getJobExecution(String executionId);

    /**
     * Gets the type of orchestrator (e.g., "AWS_STEP_FUNCTIONS", "AZURE_DURABLE_FUNCTIONS").
     * 
     * @return the orchestrator type identifier
     */
    String getOrchestratorType();
}
