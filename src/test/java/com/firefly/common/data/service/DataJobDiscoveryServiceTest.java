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

import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.model.JobStageRequest;
import com.firefly.common.data.model.JobStageResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.persistence.service.JobAuditService;
import com.firefly.common.data.persistence.service.JobExecutionResultService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit tests for DataJobDiscoveryService.
 */
class DataJobDiscoveryServiceTest {

    @Test
    void shouldDiscoverAndLogDataJobs() {
        // Given
        ApplicationContext context = mock(ApplicationContext.class);
        
        // Create a test job service
        TestDataJobService testService = new TestDataJobService(
                mock(JobTracingService.class),
                mock(JobMetricsService.class),
                mock(ResiliencyDecoratorService.class),
                mock(JobEventPublisher.class),
                mock(JobAuditService.class),
                mock(JobExecutionResultService.class)
        );
        
        Map<String, DataJobService> services = new HashMap<>();
        services.put("testDataJobService", testService);
        
        when(context.getBeansOfType(DataJobService.class)).thenReturn(services);
        when(context.getBeansOfType(com.firefly.common.data.controller.DataJobController.class))
                .thenReturn(new HashMap<>());
        
        DataJobDiscoveryService discoveryService = new DataJobDiscoveryService(context);
        
        // When
        discoveryService.discoverAndLogDataJobs();
        
        // Then - verify that the method completes without errors
        // The actual logging is verified manually by checking the logs
        verify(context).getBeansOfType(DataJobService.class);
    }

    @Test
    void shouldHandleNoDataJobsGracefully() {
        // Given
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBeansOfType(DataJobService.class)).thenReturn(new HashMap<>());
        when(context.getBeansOfType(com.firefly.common.data.controller.DataJobController.class))
                .thenReturn(new HashMap<>());
        
        DataJobDiscoveryService discoveryService = new DataJobDiscoveryService(context);
        
        // When
        discoveryService.discoverAndLogDataJobs();
        
        // Then - should complete without errors
        verify(context).getBeansOfType(DataJobService.class);
    }

    /**
     * Test implementation of AbstractResilientDataJobService for testing.
     */
    static class TestDataJobService extends AbstractResilientDataJobService {

        public TestDataJobService(JobTracingService tracingService,
                                 JobMetricsService metricsService,
                                 ResiliencyDecoratorService resiliencyService,
                                 JobEventPublisher eventPublisher,
                                 JobAuditService auditService,
                                 JobExecutionResultService resultService) {
            super(tracingService, metricsService, resiliencyService, eventPublisher, auditService, resultService);
        }

        @Override
        protected Mono<JobStageResponse> doStartJob(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.START, "test-exec-123", "Test job started"));
        }

        @Override
        protected Mono<JobStageResponse> doCheckJob(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.CHECK, request.getExecutionId(), "Running"));
        }

        @Override
        protected Mono<JobStageResponse> doCollectJobResults(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.COLLECT, request.getExecutionId(), "Results collected"));
        }

        @Override
        protected Mono<JobStageResponse> doGetJobResult(JobStageRequest request) {
            return Mono.just(JobStageResponse.success(JobStage.RESULT, request.getExecutionId(), "Results retrieved"));
        }

        @Override
        protected Mono<JobStageResponse> doStopJob(JobStageRequest request, String reason) {
            return Mono.just(JobStageResponse.success(JobStage.STOP, request.getExecutionId(), "Job stopped"));
        }

        @Override
        protected String getJobName() {
            return "TestDataJob";
        }

        @Override
        protected String getJobDescription() {
            return "Test data processing job for unit tests";
        }

        @Override
        protected String getOrchestratorType() {
            return "MOCK_ORCHESTRATOR";
        }

        @Override
        protected String getJobDefinition() {
            return "test-job-definition";
        }
    }
}

