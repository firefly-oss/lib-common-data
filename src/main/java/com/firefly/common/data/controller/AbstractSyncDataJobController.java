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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.service.SyncDataJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base controller implementation for synchronous data processing jobs.
 *
 * <p>This class provides comprehensive logging and request/response handling
 * for synchronous job executions. It automatically:</p>
 * <ul>
 *   <li>Logs incoming HTTP requests with parameters</li>
 *   <li>Converts request parameters to {@link JobStageRequest}</li>
 *   <li>Delegates execution to {@link SyncDataJobService}</li>
 *   <li>Logs successful responses with execution details</li>
 *   <li>Logs error responses with error details</li>
 *   <li>Tracks request/response timing</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/customer-validation")
 * @Tag(name = "Customer Validation", description = "Customer data validation endpoints")
 * public class CustomerValidationController extends AbstractSyncDataJobController {
 *     
 *     public CustomerValidationController(CustomerValidationService service) {
 *         super(service);
 *     }
 * }
 * }</pre>
 *
 * <p>The above example creates a REST endpoint at:</p>
 * <pre>
 * POST /api/v1/customer-validation/execute
 * </pre>
 *
 * <p><b>Request Example:</b></p>
 * <pre>{@code
 * POST /api/v1/customer-validation/execute?requestId=req-001&initiator=user@example.com
 * {
 *   "customerId": "12345",
 *   "validationType": "full"
 * }
 * }</pre>
 *
 * <p><b>Response Example:</b></p>
 * <pre>{@code
 * {
 *   "success": true,
 *   "executionId": "sync-abc-123",
 *   "data": {
 *     "isValid": true,
 *     "errors": []
 *   },
 *   "message": "Validation completed successfully",
 *   "timestamp": "2025-10-16T10:30:00Z"
 * }
 * }</pre>
 *
 * @see SyncDataJobController
 * @see SyncDataJobService
 */
@Slf4j
@RestController
public abstract class AbstractSyncDataJobController implements SyncDataJobController {

    private final SyncDataJobService syncDataJobService;
    private final ObjectMapper objectMapper;

    /**
     * Constructor with service dependency.
     *
     * @param syncDataJobService the synchronous data job service
     */
    protected AbstractSyncDataJobController(SyncDataJobService syncDataJobService) {
        this.syncDataJobService = syncDataJobService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor with service and custom ObjectMapper.
     *
     * @param syncDataJobService the synchronous data job service
     * @param objectMapper custom ObjectMapper for JSON processing
     */
    protected AbstractSyncDataJobController(SyncDataJobService syncDataJobService, ObjectMapper objectMapper) {
        this.syncDataJobService = syncDataJobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<JobStageResponse> execute(Map<String, Object> parameters,
                                         String requestId,
                                         String initiator,
                                         String metadata) {
        log.info("Received synchronous job execution request - jobName: {}, parameters: {}, requestId: {}, initiator: {}",
                syncDataJobService.getJobName(),
                parameters != null ? parameters.keySet() : "none",
                requestId,
                initiator);
        log.debug("Full synchronous job request - jobName: {}, parameters: {}, metadata: {}",
                syncDataJobService.getJobName(), parameters, metadata);

        // Parse metadata if provided
        Map<String, String> metadataMap = parseMetadata(metadata);

        // Build JobStageRequest
        JobStageRequest request = JobStageRequest.builder()
                .parameters(parameters)
                .requestId(requestId)
                .initiator(initiator)
                .metadata(metadataMap)
                .build();

        return syncDataJobService.execute(request)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("Synchronous job completed successfully - jobName: {}, executionId: {}, dataKeys: {}",
                                syncDataJobService.getJobName(),
                                response.getExecutionId(),
                                response.getData() != null ? response.getData().keySet() : "none");
                    } else {
                        log.warn("Synchronous job completed with failure - jobName: {}, executionId: {}, message: {}",
                                syncDataJobService.getJobName(),
                                response.getExecutionId(),
                                response.getMessage());
                    }
                    log.debug("Full synchronous job response - jobName: {}, executionId: {}, response: {}",
                            syncDataJobService.getJobName(), response.getExecutionId(), response);
                })
                .doOnError(error -> {
                    log.error("Synchronous job failed - jobName: {}, error: {}",
                            syncDataJobService.getJobName(), error.getMessage(), error);
                });
    }

    /**
     * Parses metadata JSON string to Map.
     *
     * @param metadata the metadata JSON string
     * @return parsed metadata map, or empty map if parsing fails
     */
    private Map<String, String> parseMetadata(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> metadataMap = objectMapper.readValue(metadata, Map.class);
            return metadataMap;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metadata JSON, using empty map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Gets the synchronous data job service.
     *
     * @return the synchronous data job service
     */
    protected SyncDataJobService getSyncDataJobService() {
        return syncDataJobService;
    }

    /**
     * Gets the ObjectMapper used for JSON processing.
     *
     * @return the ObjectMapper
     */
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

