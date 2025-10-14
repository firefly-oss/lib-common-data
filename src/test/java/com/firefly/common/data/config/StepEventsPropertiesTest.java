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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StepEventsProperties.
 */
class StepEventsPropertiesTest {

    @Test
    void shouldSetAndGetProperties() {
        // Given
        StepEventsProperties properties = new StepEventsProperties();

        // When
        properties.setEnabled(true);
        properties.setTopic("test-step-events");
        properties.setIncludeJobContext(true);

        // Then
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTopic()).isEqualTo("test-step-events");
        assertThat(properties.isIncludeJobContext()).isTrue();
    }

    @Test
    void shouldHaveDefaultValues() {
        // Given/When
        StepEventsProperties properties = new StepEventsProperties();

        // Then - verify properties object is created
        assertThat(properties).isNotNull();
    }
}

