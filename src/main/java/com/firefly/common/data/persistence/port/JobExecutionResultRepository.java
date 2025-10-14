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

package com.firefly.common.data.persistence.port;

import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import com.firefly.common.data.persistence.model.JobExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Port interface for job execution result persistence.
 *
 * This is a hexagonal architecture port that defines the contract for
 * persisting job execution results. Each microservice must provide an
 * adapter implementation using their preferred persistence technology:
 *
 * - Spring Data R2DBC (PostgreSQL, MySQL, H2, etc.) - RECOMMENDED for reactive applications
 * - Spring Data MongoDB Reactive
 * - Spring Data Redis Reactive (for caching)
 * - AWS DynamoDB with async client
 * - Custom reactive implementations
 *
 * Example adapter implementation with R2DBC:
 *
 * <pre>
 * {@code
 * @Repository
 * public class R2dbcJobExecutionResultRepositoryAdapter implements JobExecutionResultRepository {
 *
 *     private final R2dbcJobExecutionResultEntityRepository r2dbcRepository;
 *
 *     public R2dbcJobExecutionResultRepositoryAdapter(R2dbcJobExecutionResultEntityRepository r2dbcRepository) {
 *         this.r2dbcRepository = r2dbcRepository;
 *     }
 *
 *     @Override
 *     public Mono<JobExecutionResult> save(JobExecutionResult result) {
 *         return r2dbcRepository.save(toEntity(result))
 *                 .map(this::toDomain);
 *     }
 *
 *     @Override
 *     public Mono<JobExecutionResult> findByExecutionId(String executionId) {
 *         return r2dbcRepository.findByExecutionId(executionId)
 *                 .map(this::toDomain);
 *     }
 *
 *     // ... other methods
 * }
 *
 * // R2DBC Entity Repository
 * public interface R2dbcJobExecutionResultEntityRepository extends ReactiveCrudRepository<JobExecutionResultEntity, String> {
 *     Mono<JobExecutionResultEntity> findByExecutionId(String executionId);
 *     Mono<JobExecutionResultEntity> findByRequestId(String requestId);
 *     Flux<JobExecutionResultEntity> findByJobType(String jobType);
 *     // ... other query methods
 * }
 * }
 * </pre>
 */
public interface JobExecutionResultRepository {

    /**
     * Saves a job execution result.
     * 
     * @param result the result to save
     * @return a Mono emitting the saved result
     */
    Mono<JobExecutionResult> save(JobExecutionResult result);

    /**
     * Finds a result by its ID.
     * 
     * @param resultId the result ID
     * @return a Mono emitting the result, or empty if not found
     */
    Mono<JobExecutionResult> findById(String resultId);

    /**
     * Finds a result by execution ID.
     * 
     * @param executionId the job execution ID
     * @return a Mono emitting the result, or empty if not found
     */
    Mono<JobExecutionResult> findByExecutionId(String executionId);

    /**
     * Finds a result by request ID.
     * 
     * @param requestId the request ID
     * @return a Mono emitting the result, or empty if not found
     */
    Mono<JobExecutionResult> findByRequestId(String requestId);

    /**
     * Finds all results for a specific job type.
     * 
     * @param jobType the job type
     * @return a Flux emitting all results for the job type
     */
    Flux<JobExecutionResult> findByJobType(String jobType);

    /**
     * Finds all results with a specific status.
     * 
     * @param status the job execution status
     * @return a Flux emitting all results with the status
     */
    Flux<JobExecutionResult> findByStatus(JobExecutionStatus status);

    /**
     * Finds all results for a specific initiator.
     * 
     * @param initiator the initiator (user or system)
     * @return a Flux emitting all results for the initiator
     */
    Flux<JobExecutionResult> findByInitiator(String initiator);

    /**
     * Finds all results within a time range.
     * 
     * @param startTime the start of the time range
     * @param endTime the end of the time range
     * @return a Flux emitting all results within the time range
     */
    Flux<JobExecutionResult> findByStartTimeBetween(Instant startTime, Instant endTime);

    /**
     * Finds all cacheable and valid results.
     * This is useful for implementing result caching strategies.
     * 
     * @return a Flux emitting all cacheable and valid results
     */
    Flux<JobExecutionResult> findCacheableAndValid();

    /**
     * Finds all results for a specific trace ID.
     * 
     * @param traceId the trace ID
     * @return a Flux emitting all results for the trace
     */
    Flux<JobExecutionResult> findByTraceId(String traceId);

    /**
     * Deletes results older than the specified timestamp.
     * This is useful for implementing data retention policies.
     * 
     * @param timestamp the cutoff timestamp
     * @return a Mono emitting the number of deleted results
     */
    Mono<Long> deleteByEndTimeBefore(Instant timestamp);

    /**
     * Deletes expired results.
     * This is useful for cleaning up cached results.
     * 
     * @return a Mono emitting the number of deleted results
     */
    Mono<Long> deleteExpired();

    /**
     * Updates the status of a result.
     * 
     * @param executionId the job execution ID
     * @param status the new status
     * @return a Mono emitting the updated result
     */
    Mono<JobExecutionResult> updateStatus(String executionId, JobExecutionStatus status);

    /**
     * Checks if a result exists for a specific execution.
     * 
     * @param executionId the job execution ID
     * @return a Mono emitting true if a result exists, false otherwise
     */
    Mono<Boolean> existsByExecutionId(String executionId);

    /**
     * Counts all results for a specific job type.
     * 
     * @param jobType the job type
     * @return a Mono emitting the count
     */
    Mono<Long> countByJobType(String jobType);

    /**
     * Counts all results with a specific status.
     * 
     * @param status the job execution status
     * @return a Mono emitting the count
     */
    Mono<Long> countByStatus(JobExecutionStatus status);
}

