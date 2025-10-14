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

package com.firefly.common.data.observability;

import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.model.JobStage;
import com.firefly.common.data.orchestration.model.JobExecutionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for recording job metrics.
 */
@Service
@Slf4j
public class JobMetricsService {

    private final MeterRegistry meterRegistry;
    private final JobOrchestrationProperties properties;
    private final ConcurrentMap<String, Long> executionStartTimes = new ConcurrentHashMap<>();

    public JobMetricsService(MeterRegistry meterRegistry, JobOrchestrationProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    /**
     * Records a job stage execution.
     */
    public void recordJobStageExecution(JobStage stage, String status, Duration duration) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".stage.execution";
        
        Timer.builder(metricName)
                .tag("stage", stage.name())
                .tag("status", status)
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Job stage execution time")
                .register(meterRegistry)
                .record(duration);

        log.debug("Recorded metric for stage {} with status {} and duration {}", stage, status, duration);
    }

    /**
     * Increments the job stage counter.
     */
    public void incrementJobStageCounter(JobStage stage, String status) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".stage.count";
        
        Counter.builder(metricName)
                .tag("stage", stage.name())
                .tag("status", status)
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Job stage execution count")
                .register(meterRegistry)
                .increment();

        log.debug("Incremented counter for stage {} with status {}", stage, status);
    }

    /**
     * Records job execution start.
     */
    public void recordJobExecutionStart(String executionId) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        executionStartTimes.put(executionId, System.currentTimeMillis());
        
        String metricName = properties.getObservability().getMetricPrefix() + ".execution.started";
        Counter.builder(metricName)
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Job executions started")
                .register(meterRegistry)
                .increment();

        log.debug("Recorded job execution start for {}", executionId);
    }

    /**
     * Records job execution completion.
     */
    public void recordJobExecutionCompletion(String executionId, JobExecutionStatus status) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        Long startTime = executionStartTimes.remove(executionId);
        if (startTime != null) {
            Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
            
            String metricName = properties.getObservability().getMetricPrefix() + ".execution.duration";
            Timer.builder(metricName)
                    .tag("status", status.name())
                    .tag("orchestrator", properties.getOrchestratorType())
                    .description("Job execution duration")
                    .register(meterRegistry)
                    .record(duration);

            log.debug("Recorded job execution completion for {} with status {} and duration {}", 
                    executionId, status, duration);
        }

        String counterName = properties.getObservability().getMetricPrefix() + ".execution.completed";
        Counter.builder(counterName)
                .tag("status", status.name())
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Job executions completed")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Records a job error.
     */
    public void recordJobError(JobStage stage, String errorType) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".error";
        Counter.builder(metricName)
                .tag("stage", stage.name())
                .tag("error.type", errorType)
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Job errors")
                .register(meterRegistry)
                .increment();

        log.debug("Recorded error for stage {} with type {}", stage, errorType);
    }

    /**
     * Records mapper execution.
     */
    public void recordMapperExecution(String mapperName, boolean success, Duration duration) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".mapper.execution";
        Timer.builder(metricName)
                .tag("mapper", mapperName)
                .tag("status", success ? "success" : "failure")
                .description("Mapper execution time")
                .register(meterRegistry)
                .record(duration);

        log.debug("Recorded mapper execution for {} with status {} and duration {}", 
                mapperName, success ? "success" : "failure", duration);
    }

    /**
     * Records orchestrator operation.
     */
    public void recordOrchestratorOperation(String operation, boolean success, Duration duration) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".orchestrator.operation";
        Timer.builder(metricName)
                .tag("operation", operation)
                .tag("status", success ? "success" : "failure")
                .tag("orchestrator", properties.getOrchestratorType())
                .description("Orchestrator operation time")
                .register(meterRegistry)
                .record(duration);

        log.debug("Recorded orchestrator operation {} with status {} and duration {}", 
                operation, success ? "success" : "failure", duration);
    }

    /**
     * Records active job count.
     */
    public void recordActiveJobCount(int count) {
        if (!properties.getObservability().isMetricsEnabled()) {
            return;
        }

        String metricName = properties.getObservability().getMetricPrefix() + ".active.count";
        meterRegistry.gauge(metricName, count);

        log.debug("Recorded active job count: {}", count);
    }
}

