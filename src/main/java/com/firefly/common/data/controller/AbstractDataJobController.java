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

import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStartRequest;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.service.AbstractResilientDataJobService;
import com.firefly.common.data.service.DataJobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

/**
 * Abstract base controller implementation that provides comprehensive logging
 * for all job lifecycle phases.
 *
 * This class logs:
 * - Incoming HTTP requests with parameters
 * - Successful responses with execution details
 * - Error responses with error details
 * - Request/response timing
 *
 * Subclasses should inject the DataJobService implementation.
 *
 */
@Slf4j
@RestController
public abstract class AbstractDataJobController implements DataJobController {

    private final DataJobService dataJobService;

    protected AbstractDataJobController(DataJobService dataJobService) {
        this.dataJobService = dataJobService;
    }

    @Override
    public Mono<JobStageResponse> startJob(JobStartRequest request) {
        log.info("Received START job request - parameters: {}, requestId: {}, initiator: {}",
                request.getParameters() != null ? request.getParameters().keySet() : "none",
                request.getRequestId(),
                request.getInitiator());
        log.debug("Full START job request: {}", request);

        // Convert JobStartRequest to JobStageRequest for the service layer
        JobStageRequest stageRequest = JobStageRequest.builder()
                .stage(JobStage.START)
                .parameters(request.getParameters())
                .requestId(request.getRequestId())
                .initiator(request.getInitiator())
                .metadata(request.getMetadata())
                .build();

        return dataJobService.startJob(stageRequest)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("START job completed successfully - executionId: {}, status: {}",
                                response.getExecutionId(), response.getStatus());
                    } else {
                        log.warn("START job completed with failure - executionId: {}, status: {}, message: {}",
                                response.getExecutionId(), response.getStatus(), response.getMessage());
                    }
                    log.debug("Full START job response: {}", response);
                })
                .doOnError(error -> {
                    log.error("START job failed with error: {}", error.getMessage(), error);
                });
    }

    @Override
    public Mono<JobStageResponse> checkJob(String executionId, String requestId) {
        log.info("Received CHECK job request - executionId: {}, requestId: {}", executionId, requestId);

        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.CHECK)
                .executionId(executionId)
                .requestId(requestId)
                .build();

        return dataJobService.checkJob(request)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("CHECK job completed successfully - executionId: {}, status: {}", 
                                executionId, response.getStatus());
                    } else {
                        log.warn("CHECK job completed with failure - executionId: {}, status: {}, message: {}", 
                                executionId, response.getStatus(), response.getMessage());
                    }
                    log.debug("Full CHECK job response for executionId {}: {}", executionId, response);
                })
                .doOnError(error -> {
                    log.error("CHECK job failed for executionId {} with error: {}", 
                            executionId, error.getMessage(), error);
                });
    }

    @Override
    public Mono<JobStageResponse> collectJobResults(String executionId, String requestId) {
        log.info("Received COLLECT job results request - executionId: {}, requestId: {}", executionId, requestId);

        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.COLLECT)
                .executionId(executionId)
                .requestId(requestId)
                .build();

        return dataJobService.collectJobResults(request)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("COLLECT job results completed successfully - executionId: {}, status: {}, dataKeys: {}", 
                                executionId, response.getStatus(), 
                                response.getData() != null ? response.getData().keySet() : "none");
                    } else {
                        log.warn("COLLECT job results completed with failure - executionId: {}, status: {}, message: {}", 
                                executionId, response.getStatus(), response.getMessage());
                    }
                    log.debug("Full COLLECT job results response for executionId {}: {}", executionId, response);
                })
                .doOnError(error -> {
                    log.error("COLLECT job results failed for executionId {} with error: {}", 
                            executionId, error.getMessage(), error);
                });
    }

    @Override
    public Mono<JobStageResponse> getJobResult(String executionId, String requestId, String targetDtoClass) {
        log.info("Received GET job result request - executionId: {}, requestId: {}, targetDtoClass: {}",
                executionId, requestId, targetDtoClass);

        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.RESULT)
                .executionId(executionId)
                .requestId(requestId)
                .targetDtoClass(targetDtoClass)
                .build();

        return dataJobService.getJobResult(request)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("GET job result completed successfully - executionId: {}, status: {}, dataKeys: {}",
                                executionId, response.getStatus(),
                                response.getData() != null ? response.getData().keySet() : "none");
                    } else {
                        log.warn("GET job result completed with failure - executionId: {}, status: {}, message: {}",
                                executionId, response.getStatus(), response.getMessage());
                    }
                    log.debug("Full GET job result response for executionId {}: {}", executionId, response);
                })
                .doOnError(error -> {
                    log.error("GET job result failed for executionId {} with error: {}",
                            executionId, error.getMessage(), error);
                });
    }

    @Override
    public Mono<JobStageResponse> stopJob(String executionId, String requestId, String reason) {
        log.info("Received STOP job request - executionId: {}, requestId: {}, reason: {}",
                executionId, requestId, reason);

        JobStageRequest request = JobStageRequest.builder()
                .stage(JobStage.STOP)
                .executionId(executionId)
                .requestId(requestId)
                .reason(reason)
                .build();

        return dataJobService.stopJob(request, reason)
                .doOnSuccess(response -> {
                    if (response.isSuccess()) {
                        log.info("STOP job completed successfully - executionId: {}, status: {}",
                                executionId, response.getStatus());
                    } else {
                        log.warn("STOP job completed with failure - executionId: {}, status: {}, message: {}",
                                executionId, response.getStatus(), response.getMessage());
                    }
                    log.debug("Full STOP job response for executionId {}: {}", executionId, response);
                })
                .doOnError(error -> {
                    log.error("STOP job failed for executionId {} with error: {}",
                            executionId, error.getMessage(), error);
                });
    }

    /**
     * Gets the data job service.
     * @return the data job service
     */
    protected DataJobService getDataJobService() {
        return dataJobService;
    }
}

