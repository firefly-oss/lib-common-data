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
 * Unit tests for DataEnrichmentProperties.
 */
class DataEnrichmentPropertiesTest {

    @Test
    void shouldHaveCorrectDefaultValues() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // Then
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isPublishEvents()).isTrue();
        assertThat(properties.isCacheEnabled()).isFalse();
        assertThat(properties.getCacheTtlSeconds()).isEqualTo(3600);
        assertThat(properties.getDefaultTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.isCaptureRawResponses()).isFalse();
        assertThat(properties.getMaxConcurrentEnrichments()).isEqualTo(100);
        assertThat(properties.isRetryEnabled()).isTrue();
        assertThat(properties.getMaxRetryAttempts()).isEqualTo(3);
    }

    @Test
    void shouldAllowSettingPublishEvents() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // When
        properties.setPublishEvents(false);

        // Then
        assertThat(properties.isPublishEvents()).isFalse();
    }

    @Test
    void shouldAllowSettingCacheEnabled() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // When
        properties.setCacheEnabled(true);

        // Then
        assertThat(properties.isCacheEnabled()).isTrue();
    }

    @Test
    void shouldAllowSettingDefaultTimeoutSeconds() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // When
        properties.setDefaultTimeoutSeconds(60);

        // Then
        assertThat(properties.getDefaultTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void shouldAllowSettingMaxRetryAttempts() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // When
        properties.setMaxRetryAttempts(5);

        // Then
        assertThat(properties.getMaxRetryAttempts()).isEqualTo(5);
    }

    @Test
    void shouldAllowSettingCacheTtlSeconds() {
        // Given
        DataEnrichmentProperties properties = new DataEnrichmentProperties();

        // When
        properties.setCacheTtlSeconds(7200);

        // Then
        assertThat(properties.getCacheTtlSeconds()).isEqualTo(7200);
    }

    @Test
    void shouldAllowSettingAllProperties() {
        // Given & When
        DataEnrichmentProperties properties = new DataEnrichmentProperties();
        properties.setEnabled(false);
        properties.setPublishEvents(false);
        properties.setCacheEnabled(true);
        properties.setCacheTtlSeconds(7200);
        properties.setDefaultTimeoutSeconds(60);
        properties.setCaptureRawResponses(true);
        properties.setMaxConcurrentEnrichments(50);
        properties.setRetryEnabled(false);
        properties.setMaxRetryAttempts(5);

        // Then
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isPublishEvents()).isFalse();
        assertThat(properties.isCacheEnabled()).isTrue();
        assertThat(properties.getCacheTtlSeconds()).isEqualTo(7200);
        assertThat(properties.getDefaultTimeoutSeconds()).isEqualTo(60);
        assertThat(properties.isCaptureRawResponses()).isTrue();
        assertThat(properties.getMaxConcurrentEnrichments()).isEqualTo(50);
        assertThat(properties.isRetryEnabled()).isFalse();
        assertThat(properties.getMaxRetryAttempts()).isEqualTo(5);
    }
}

