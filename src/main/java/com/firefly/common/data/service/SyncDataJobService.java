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

import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import reactor.core.publisher.Mono;

/**
 * Service interface for synchronous data processing jobs.
 * 
 * Unlike {@link DataJobService} which handles asynchronous jobs with multiple stages
 * (START, CHECK, COLLECT, RESULT, STOP), this interface is designed for jobs that
 * execute synchronously and return results immediately in a single operation.
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Simple data transformations that complete quickly (< 30 seconds)</li>
 *   <li>Database queries that return results immediately</li>
 *   <li>API calls to external services with synchronous responses</li>
 *   <li>In-memory data processing</li>
 *   <li>Validation or enrichment operations</li>
 * </ul>
 * 
 * <p><b>When NOT to use:</b></p>
 * <ul>
 *   <li>Long-running jobs (> 30 seconds) - use {@link DataJobService} instead</li>
 *   <li>Jobs that require polling or status checking</li>
 *   <li>Jobs that need to be stopped/cancelled mid-execution</li>
 *   <li>Jobs with complex multi-stage workflows</li>
 * </ul>
 * 
 * <p><b>Example Implementation:</b></p>
 * <pre>{@code
 * @Service
 * public class CustomerEnrichmentService extends AbstractResilientSyncDataJobService {
 *     
 *     @Override
 *     protected Mono<JobStageResponse> doExecute(JobStageRequest request) {
 *         String customerId = (String) request.getParameters().get("customerId");
 *         
 *         return customerRepository.findById(customerId)
 *             .map(customer -> enrichCustomerData(customer))
 *             .map(enrichedData -> JobStageResponse.builder()
 *                 .success(true)
 *                 .executionId(request.getExecutionId())
 *                 .data(Map.of("customer", enrichedData))
 *                 .message("Customer data enriched successfully")
 *                 .build());
 *     }
 *     
 *     @Override
 *     protected String getJobName() {
 *         return "CustomerEnrichment";
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Features provided by {@link AbstractResilientSyncDataJobService}:</b></p>
 * <ul>
 *   <li>Distributed tracing with Micrometer</li>
 *   <li>Metrics collection (execution time, success/failure counts)</li>
 *   <li>Resiliency patterns (circuit breaker, retry, rate limiting, bulkhead)</li>
 *   <li>Audit trail persistence</li>
 *   <li>Execution result persistence</li>
 *   <li>Event publishing</li>
 *   <li>Comprehensive logging</li>
 * </ul>
 * 
 * @see AbstractResilientSyncDataJobService
 * @see DataJobService
 */
public interface SyncDataJobService {

    /**
     * Executes a synchronous data processing job and returns the result immediately.
     * 
     * <p>This method should:</p>
     * <ul>
     *   <li>Validate input parameters from the request</li>
     *   <li>Execute the data processing logic</li>
     *   <li>Return the results in the response</li>
     *   <li>Complete within a reasonable time (typically < 30 seconds)</li>
     * </ul>
     * 
     * <p>The implementation should be reactive and non-blocking, using Reactor's
     * {@link Mono} to represent the asynchronous computation, even though the
     * job itself executes synchronously from the caller's perspective.</p>
     * 
     * <p><b>Request Parameters:</b></p>
     * <ul>
     *   <li>{@code executionId} - Optional unique identifier for this execution</li>
     *   <li>{@code jobType} - Optional job type/category for classification</li>
     *   <li>{@code parameters} - Input parameters for the job</li>
     *   <li>{@code requestId} - Optional request ID for tracing</li>
     *   <li>{@code initiator} - Optional user/system that initiated the request</li>
     *   <li>{@code metadata} - Optional additional metadata</li>
     * </ul>
     * 
     * <p><b>Response Fields:</b></p>
     * <ul>
     *   <li>{@code success} - Whether the job completed successfully</li>
     *   <li>{@code executionId} - The execution identifier</li>
     *   <li>{@code data} - The result data (if successful)</li>
     *   <li>{@code message} - Human-readable message</li>
     *   <li>{@code error} - Error message (if failed)</li>
     *   <li>{@code timestamp} - When the response was generated</li>
     * </ul>
     * 
     * @param request the job execution request containing input parameters
     * @return a Mono emitting the response with execution results
     * 
     * @throws IllegalArgumentException if required parameters are missing or invalid
     */
    Mono<JobStageResponse> execute(JobStageRequest request);

    /**
     * Gets the name of this synchronous job for identification and logging.
     * 
     * <p>This name is used in:</p>
     * <ul>
     *   <li>Log messages for debugging and monitoring</li>
     *   <li>Metrics tags for observability</li>
     *   <li>Audit trail records</li>
     *   <li>Error messages and alerts</li>
     * </ul>
     * 
     * <p>The name should be:</p>
     * <ul>
     *   <li>Descriptive and meaningful (e.g., "CustomerEnrichment", "OrderValidation")</li>
     *   <li>Unique within your application</li>
     *   <li>CamelCase or kebab-case format</li>
     *   <li>Consistent across environments</li>
     * </ul>
     * 
     * @return the job name (e.g., "CustomerEnrichment", "DataValidation")
     */
    default String getJobName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Gets a description of what this synchronous job does.
     * 
     * <p>This description is used for:</p>
     * <ul>
     *   <li>Documentation and API specs</li>
     *   <li>Job discovery and cataloging</li>
     *   <li>Developer onboarding</li>
     *   <li>Operational dashboards</li>
     * </ul>
     * 
     * <p>The description should:</p>
     * <ul>
     *   <li>Be concise but informative (1-2 sentences)</li>
     *   <li>Explain what the job does and why</li>
     *   <li>Mention key inputs and outputs</li>
     *   <li>Note any important constraints or requirements</li>
     * </ul>
     * 
     * @return the job description
     */
    default String getJobDescription() {
        return "Synchronous data processing job";
    }
}

