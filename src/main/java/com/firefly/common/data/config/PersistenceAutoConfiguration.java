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

package com.firefly.common.data.config;

import com.firefly.common.data.persistence.port.JobAuditRepository;
import com.firefly.common.data.persistence.port.JobExecutionResultRepository;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Auto-configuration for job persistence features.
 *
 * This configuration creates beans for audit trail and execution result persistence.
 * The actual persistence adapters must be provided by the implementing microservice.
 *
 * RECOMMENDED: Use Spring Data R2DBC for reactive persistence.
 *
 * Example adapter implementation in your microservice with R2DBC:
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
 *         return r2dbcRepository.save(toEntity(entry)).map(this::toDomain);
 *     }
 *
 *     // ... implement other methods
 * }
 *
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
 *         return r2dbcRepository.save(toEntity(result)).map(this::toDomain);
 *     }
 *
 *     // ... implement other methods
 * }
 *
 * // R2DBC Entity Repositories
 * public interface R2dbcJobAuditEntityRepository extends ReactiveCrudRepository<JobAuditEntity, String> {
 *     Flux<JobAuditEntity> findByExecutionId(String executionId);
 * }
 *
 * public interface R2dbcJobExecutionResultEntityRepository extends ReactiveCrudRepository<JobExecutionResultEntity, String> {
 *     Mono<JobExecutionResultEntity> findByExecutionId(String executionId);
 * }
 * }
 * </pre>
 */
@Configuration
@ConditionalOnProperty(prefix = "firefly.data.orchestration", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({JobOrchestrationProperties.class, DataConfiguration.class})
@Slf4j
public class PersistenceAutoConfiguration {

    /**
     * Creates the JobAuditService bean.
     * 
     * This service will work even if no JobAuditRepository adapter is provided,
     * but audit operations will be no-ops in that case.
     */
    @Bean
    public JobAuditService jobAuditService(
            Optional<JobAuditRepository> auditRepository,
            JobOrchestrationProperties properties,
            ObservationRegistry observationRegistry) {
        
        if (auditRepository.isEmpty()) {
            log.warn("No JobAuditRepository implementation found. Audit trail persistence will be disabled. " +
                    "To enable audit persistence, implement the JobAuditRepository interface in your microservice.");
        } else if (!properties.getPersistence().isAuditEnabled()) {
            log.info("Audit trail persistence is disabled via configuration (firefly.data.orchestration.persistence.audit-enabled=false)");
        } else {
            log.info("JobAuditService configured with repository: {}", auditRepository.get().getClass().getSimpleName());
        }

        return new JobAuditService(auditRepository, properties, observationRegistry);
    }

    /**
     * Creates the JobExecutionResultService bean.
     * 
     * This service will work even if no JobExecutionResultRepository adapter is provided,
     * but result persistence operations will be no-ops in that case.
     */
    @Bean
    public JobExecutionResultService jobExecutionResultService(
            Optional<JobExecutionResultRepository> resultRepository,
            JobOrchestrationProperties properties,
            ObservationRegistry observationRegistry) {
        
        if (resultRepository.isEmpty()) {
            log.warn("No JobExecutionResultRepository implementation found. Result persistence will be disabled. " +
                    "To enable result persistence, implement the JobExecutionResultRepository interface in your microservice.");
        } else if (!properties.getPersistence().isResultPersistenceEnabled()) {
            log.info("Result persistence is disabled via configuration (firefly.data.orchestration.persistence.result-persistence-enabled=false)");
        } else {
            log.info("JobExecutionResultService configured with repository: {}", resultRepository.get().getClass().getSimpleName());
        }

        return new JobExecutionResultService(resultRepository, properties, observationRegistry);
    }
}

