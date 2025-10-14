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

package com.firefly.common.data.integration;

import com.firefly.common.data.config.DataConfiguration;
import com.firefly.common.data.config.JobOrchestrationProperties;
import com.firefly.common.data.config.StepEventsProperties;
import com.firefly.common.data.event.JobEventPublisher;
import com.firefly.common.data.health.JobOrchestratorHealthIndicator;
import com.firefly.common.data.mapper.JobResultMapperRegistry;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AutoConfigurationIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
    "firefly.data.orchestration.enabled=true",
    "firefly.data.orchestration.orchestrator-type=MOCK",
    "firefly.data.orchestration.publish-job-events=true",
    "firefly.data.orchestration.job-events-topic=test-job-events",
    "firefly.data.orchestration.default-timeout=PT1H",
    "firefly.data.orchestration.max-retries=3",
    "firefly.data.orchestration.retry-delay=PT5S",
    "firefly.data.orchestration.airflow.base-url=http://test-airflow:8080",
    "firefly.data.orchestration.airflow.username=test-user",
    "firefly.data.orchestration.airflow.password=test-pass",
    "firefly.data.orchestration.airflow.dag-id-prefix=test_data_job",
    "firefly.data.orchestration.observability.tracing-enabled=true",
    "firefly.data.orchestration.observability.metrics-enabled=true",
    "firefly.data.orchestration.observability.metric-prefix=test.firefly.data.job",
    "firefly.data.orchestration.health-check.enabled=true",
    "firefly.data.orchestration.health-check.timeout=PT5S",
    "firefly.stepevents.enabled=true",
    "firefly.stepevents.topic=test-step-events",
    "firefly.stepevents.include-job-context=true",
    "firefly.data.eda.enabled=true",
    "firefly.data.cqrs.enabled=true",
    "firefly.data.transactional.enabled=false"
})
class AutoConfigurationIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
        
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
        
        @Bean
        public ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private JobOrchestrationProperties jobOrchestrationProperties;

    @Autowired(required = false)
    private DataConfiguration dataConfiguration;

    @Autowired(required = false)
    private StepEventsProperties stepEventsProperties;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    void jobOrchestrationProperties_ShouldBeLoadedWithCorrectValues() {
        assertNotNull(jobOrchestrationProperties);
        assertTrue(jobOrchestrationProperties.isEnabled());
        assertEquals("MOCK", jobOrchestrationProperties.getOrchestratorType());
        assertTrue(jobOrchestrationProperties.isPublishJobEvents());
        assertEquals("test-job-events", jobOrchestrationProperties.getJobEventsTopic());
        assertEquals(Duration.ofHours(1), jobOrchestrationProperties.getDefaultTimeout());
        assertEquals(3, jobOrchestrationProperties.getMaxRetries());
        assertEquals(Duration.ofSeconds(5), jobOrchestrationProperties.getRetryDelay());

        // Test Airflow config
        assertNotNull(jobOrchestrationProperties.getAirflow());
        assertEquals("http://test-airflow:8080", jobOrchestrationProperties.getAirflow().getBaseUrl());
        assertEquals("test-user", jobOrchestrationProperties.getAirflow().getUsername());
        assertEquals("test-pass", jobOrchestrationProperties.getAirflow().getPassword());
        assertEquals("test_data_job", jobOrchestrationProperties.getAirflow().getDagIdPrefix());

        // Test observability config
        assertNotNull(jobOrchestrationProperties.getObservability());
        assertTrue(jobOrchestrationProperties.getObservability().isTracingEnabled());
        assertTrue(jobOrchestrationProperties.getObservability().isMetricsEnabled());
        assertEquals("test.firefly.data.job", jobOrchestrationProperties.getObservability().getMetricPrefix());

        // Test health check config
        assertNotNull(jobOrchestrationProperties.getHealthCheck());
        assertTrue(jobOrchestrationProperties.getHealthCheck().isEnabled());
        assertEquals(Duration.ofSeconds(5), jobOrchestrationProperties.getHealthCheck().getTimeout());
    }

    @Test
    void dataConfiguration_ShouldBeLoadedWithCorrectValues() {
        assertNotNull(dataConfiguration);
        
        assertTrue(dataConfiguration.getEda().isEnabled());
        assertTrue(dataConfiguration.getCqrs().isEnabled());
        assertFalse(dataConfiguration.getTransactional().isEnabled());
        
        // Orchestration is nested in dataConfiguration
        assertNotNull(dataConfiguration.getOrchestration());
        assertTrue(dataConfiguration.getOrchestration().isEnabled());
    }

    @Test
    void stepEventsProperties_ShouldBeLoadedWithCorrectValues() {
        assertNotNull(stepEventsProperties);
        assertTrue(stepEventsProperties.isEnabled());
        assertEquals("test-step-events", stepEventsProperties.getTopic());
        assertTrue(stepEventsProperties.isIncludeJobContext());
    }

    @Test
    void jobEventPublisher_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("jobEventPublisher"));
        JobEventPublisher jobEventPublisher = applicationContext.getBean(JobEventPublisher.class);
        assertNotNull(jobEventPublisher);
    }

    @Test
    void jobMetricsService_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("jobMetricsService"));
        JobMetricsService jobMetricsService = applicationContext.getBean(JobMetricsService.class);
        assertNotNull(jobMetricsService);
    }

    @Test
    void jobTracingService_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("jobTracingService"));
        JobTracingService jobTracingService = applicationContext.getBean(JobTracingService.class);
        assertNotNull(jobTracingService);
    }

    @Test
    void resiliencyDecoratorService_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("resiliencyDecoratorService"));
        ResiliencyDecoratorService resiliencyService = applicationContext.getBean(ResiliencyDecoratorService.class);
        assertNotNull(resiliencyService);
    }

    @Test
    void jobResultMapperRegistry_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("jobResultMapperRegistry"));
        JobResultMapperRegistry mapperRegistry = applicationContext.getBean(JobResultMapperRegistry.class);
        assertNotNull(mapperRegistry);
    }

    @Test
    void jobOrchestratorHealthIndicator_ShouldBeCreated() {
        assertTrue(applicationContext.containsBean("jobOrchestratorHealthIndicator"));
        JobOrchestratorHealthIndicator healthIndicator = applicationContext.getBean("jobOrchestratorHealthIndicator", JobOrchestratorHealthIndicator.class);
        assertNotNull(healthIndicator);
    }

    @Test
    void allBeansAreProperlyConfigured() {
        // Verify that all auto-configured beans are present
        String[] expectedBeans = {
            "jobEventPublisher",
            "jobMetricsService", 
            "jobTracingService",
            "resiliencyDecoratorService",
            "jobResultMapperRegistry",
            "jobOrchestratorHealthIndicator"
        };

        for (String beanName : expectedBeans) {
            assertTrue(applicationContext.containsBean(beanName), 
                "Bean '" + beanName + "' should be present in context");
        }
    }

    @Test
    void configurationsAreConsistent() {
        // Verify that the orchestration properties in DataConfiguration match JobOrchestrationProperties
        assertNotNull(dataConfiguration);
        assertNotNull(jobOrchestrationProperties);
        
        assertEquals(jobOrchestrationProperties.isEnabled(), 
                    dataConfiguration.getOrchestration().isEnabled());
    }
}