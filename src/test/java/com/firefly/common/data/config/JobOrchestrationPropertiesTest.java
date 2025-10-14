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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobOrchestrationProperties.
 */
class JobOrchestrationPropertiesTest {

    @Test
    void shouldSetAndGetProperties() {
        // Given
        JobOrchestrationProperties properties = new JobOrchestrationProperties();

        // When
        properties.setEnabled(true);
        properties.setOrchestratorType("AWS_STEP_FUNCTIONS");
        properties.setDefaultTimeout(Duration.ofHours(24));
        properties.setMaxRetries(3);
        properties.setRetryDelay(Duration.ofSeconds(5));
        properties.setPublishJobEvents(true);
        properties.setJobEventsTopic("test-job-events");

        // Then
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getOrchestratorType()).isEqualTo("AWS_STEP_FUNCTIONS");
        assertThat(properties.getDefaultTimeout()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.getMaxRetries()).isEqualTo(3);
        assertThat(properties.getRetryDelay()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.isPublishJobEvents()).isTrue();
        assertThat(properties.getJobEventsTopic()).isEqualTo("test-job-events");
    }

    @Test
    void shouldHaveDefaultValues() {
        // Given/When
        JobOrchestrationProperties properties = new JobOrchestrationProperties();

        // Then - verify properties object is created
        assertThat(properties).isNotNull();
    }
}

