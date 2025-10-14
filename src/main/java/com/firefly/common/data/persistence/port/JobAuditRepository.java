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

import com.firefly.common.data.persistence.model.JobAuditEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Port interface for job audit trail persistence.
 *
 * This is a hexagonal architecture port that defines the contract for
 * persisting audit trail entries. Each microservice must provide an
 * adapter implementation using their preferred persistence technology:
 *
 * - Spring Data R2DBC (PostgreSQL, MySQL, H2, etc.) - RECOMMENDED for reactive applications
 * - Spring Data MongoDB Reactive
 * - Spring Data Redis Reactive
 * - AWS DynamoDB with async client
 * - Custom reactive implementations
 *
 * Example adapter implementation with R2DBC:
 *
 * <pre>
 * {@code
 * @Repository
 * public class R2dbcJobAuditRepositoryAdapter implements JobAuditRepository {
 *
 *     private final R2dbcJobAuditEntityRepository r2dbcRepository;
 *
 *     public R2dbcJobAuditRepositoryAdapter(R2dbcJobAuditEntityRepository r2dbcRepository) {
 *         this.r2dbcRepository = r2dbcRepository;
 *     }
 *
 *     @Override
 *     public Mono<JobAuditEntry> save(JobAuditEntry entry) {
 *         return r2dbcRepository.save(toEntity(entry))
 *                 .map(this::toDomain);
 *     }
 *
 *     @Override
 *     public Flux<JobAuditEntry> findByExecutionId(String executionId) {
 *         return r2dbcRepository.findByExecutionId(executionId)
 *                 .map(this::toDomain);
 *     }
 *
 *     // ... other methods
 * }
 *
 * // R2DBC Entity Repository
 * public interface R2dbcJobAuditEntityRepository extends ReactiveCrudRepository<JobAuditEntity, String> {
 *     Flux<JobAuditEntity> findByExecutionId(String executionId);
 *     Flux<JobAuditEntity> findByRequestId(String requestId);
 *     // ... other query methods
 * }
 * }
 * </pre>
 */
public interface JobAuditRepository {

    /**
     * Saves an audit entry.
     * 
     * @param entry the audit entry to save
     * @return a Mono emitting the saved audit entry
     */
    Mono<JobAuditEntry> save(JobAuditEntry entry);

    /**
     * Finds an audit entry by its ID.
     * 
     * @param auditId the audit entry ID
     * @return a Mono emitting the audit entry, or empty if not found
     */
    Mono<JobAuditEntry> findById(String auditId);

    /**
     * Finds all audit entries for a specific job execution.
     * 
     * @param executionId the job execution ID
     * @return a Flux emitting all audit entries for the execution
     */
    Flux<JobAuditEntry> findByExecutionId(String executionId);

    /**
     * Finds all audit entries for a specific request.
     * 
     * @param requestId the request ID
     * @return a Flux emitting all audit entries for the request
     */
    Flux<JobAuditEntry> findByRequestId(String requestId);

    /**
     * Finds all audit entries for a specific initiator.
     * 
     * @param initiator the initiator (user or system)
     * @return a Flux emitting all audit entries for the initiator
     */
    Flux<JobAuditEntry> findByInitiator(String initiator);

    /**
     * Finds all audit entries within a time range.
     * 
     * @param startTime the start of the time range
     * @param endTime the end of the time range
     * @return a Flux emitting all audit entries within the time range
     */
    Flux<JobAuditEntry> findByTimestampBetween(Instant startTime, Instant endTime);

    /**
     * Finds all audit entries for a specific job type.
     * 
     * @param jobType the job type
     * @return a Flux emitting all audit entries for the job type
     */
    Flux<JobAuditEntry> findByJobType(String jobType);

    /**
     * Finds all audit entries with a specific event type.
     * 
     * @param eventType the event type
     * @return a Flux emitting all audit entries with the event type
     */
    Flux<JobAuditEntry> findByEventType(JobAuditEntry.AuditEventType eventType);

    /**
     * Finds all audit entries for a specific trace ID.
     * 
     * @param traceId the trace ID
     * @return a Flux emitting all audit entries for the trace
     */
    Flux<JobAuditEntry> findByTraceId(String traceId);

    /**
     * Deletes audit entries older than the specified timestamp.
     * This is useful for implementing data retention policies.
     * 
     * @param timestamp the cutoff timestamp
     * @return a Mono emitting the number of deleted entries
     */
    Mono<Long> deleteByTimestampBefore(Instant timestamp);

    /**
     * Counts all audit entries for a specific execution.
     * 
     * @param executionId the job execution ID
     * @return a Mono emitting the count
     */
    Mono<Long> countByExecutionId(String executionId);

    /**
     * Checks if any audit entries exist for a specific execution.
     * 
     * @param executionId the job execution ID
     * @return a Mono emitting true if entries exist, false otherwise
     */
    Mono<Boolean> existsByExecutionId(String executionId);
}

