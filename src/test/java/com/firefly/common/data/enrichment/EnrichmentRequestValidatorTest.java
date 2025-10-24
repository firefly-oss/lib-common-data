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

package com.firefly.common.data.enrichment;

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentStrategy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for EnrichmentRequestValidator.
 */
class EnrichmentRequestValidatorTest {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    @Test
    void validate_shouldPass_whenAllRequiredFieldsPresent() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .sourceDto(Map.of("name", "Acme"))
                .build();

        // When/Then - should not throw
        EnrichmentRequestValidator.of(request)
                .requireParam("companyId")
                .requireSourceDto()
                .requireEnrichmentType()
                .requireStrategy()
                .validate();
    }

    @Test
    void requireParam_shouldThrowException_whenParameterMissing() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of())
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireParam("companyId")
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("Required parameter 'companyId' is missing");
    }

    @Test
    void requireParam_shouldThrowException_whenParameterIsNull() {
        // Given
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("companyId", null);

        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(params)
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireParam("companyId")
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("Required parameter 'companyId' is null");
    }

    @Test
    void requireParamMatching_shouldPass_whenParameterMatchesPattern() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("email-verification")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("email", "test@example.com"))
                .build();

        // When/Then - should not throw
        EnrichmentRequestValidator.of(request)
                .requireParamMatching("email", EMAIL_PATTERN)
                .validate();
    }

    @Test
    void requireParamMatching_shouldThrowException_whenParameterDoesNotMatch() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("email-verification")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("email", "invalid-email"))
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireParamMatching("email", EMAIL_PATTERN)
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("does not match required pattern");
    }

    @Test
    void requireParamOfType_shouldPass_whenParameterIsCorrectType() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("includeFinancials", true))
                .build();

        // When/Then - should not throw
        EnrichmentRequestValidator.of(request)
                .requireParamOfType("includeFinancials", Boolean.class)
                .validate();
    }

    @Test
    void requireParamOfType_shouldThrowException_whenParameterIsWrongType() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("includeFinancials", "true"))  // String instead of Boolean
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireParamOfType("includeFinancials", Boolean.class)
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("must be of type Boolean");
    }

    @Test
    void requireSourceDto_shouldThrowException_whenSourceDtoIsNull() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(null)
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireSourceDto()
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("Source DTO is required");
    }

    @Test
    void requireStrategy_shouldPass_whenStrategyIsAllowed() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .build();

        // When/Then - should not throw
        EnrichmentRequestValidator.of(request)
                .requireStrategy(EnrichmentStrategy.ENHANCE, EnrichmentStrategy.MERGE)
                .validate();
    }

    @Test
    void requireStrategy_shouldThrowException_whenStrategyNotAllowed() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.RAW)
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireStrategy(EnrichmentStrategy.ENHANCE, EnrichmentStrategy.MERGE)
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    void requireTenantId_shouldThrowException_whenTenantIdIsNull() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .tenantId(null)
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireTenantId()
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("Tenant ID is required");
    }

    @Test
    void require_shouldAllowCustomValidation() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .require(request.param("companyId").toString().length() > 10, 
                                "Company ID must be longer than 10 characters")
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .hasMessageContaining("Company ID must be longer than 10 characters");
    }

    @Test
    void validate_shouldCollectMultipleErrors() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType(null)
                .strategy(null)
                .parameters(Map.of())
                .sourceDto(null)
                .build();

        // When/Then
        assertThatThrownBy(() ->
                EnrichmentRequestValidator.of(request)
                        .requireEnrichmentType()
                        .requireStrategy()
                        .requireParam("companyId")
                        .requireSourceDto()
                        .validate())
                .isInstanceOf(EnrichmentRequestValidator.EnrichmentValidationException.class)
                .satisfies(exception -> {
                    EnrichmentRequestValidator.EnrichmentValidationException validationException = 
                            (EnrichmentRequestValidator.EnrichmentValidationException) exception;
                    assertThat(validationException.getErrors()).hasSize(4);
                });
    }

    @Test
    void isValid_shouldReturnFalse_whenValidationFails() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of())
                .build();

        // When
        boolean isValid = EnrichmentRequestValidator.of(request)
                .requireParam("companyId")
                .isValid();

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    void isValid_shouldReturnTrue_whenValidationPasses() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .parameters(Map.of("companyId", "12345"))
                .build();

        // When
        boolean isValid = EnrichmentRequestValidator.of(request)
                .requireParam("companyId")
                .isValid();

        // Then
        assertThat(isValid).isTrue();
    }
}

