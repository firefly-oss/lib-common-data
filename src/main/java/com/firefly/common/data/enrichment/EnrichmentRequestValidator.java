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
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fluent validator for enrichment requests.
 * 
 * <p>This class provides a declarative DSL for validating enrichment requests,
 * making it easy to ensure required parameters are present and valid.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // In your enricher's doEnrich method:
 * EnrichmentRequestValidator.of(request)
 *     .requireParam("companyId")
 *     .requireParamMatching("email", EMAIL_PATTERN)
 *     .requireSourceDto()
 *     .requireStrategy(EnrichmentStrategy.ENHANCE, EnrichmentStrategy.MERGE)
 *     .validate();
 * 
 * // Then safely extract parameters:
 * String companyId = request.param("companyId");
 * }</pre>
 */
@Slf4j
public class EnrichmentRequestValidator {
    
    private final EnrichmentRequest request;
    private final List<String> errors = new ArrayList<>();
    
    private EnrichmentRequestValidator(EnrichmentRequest request) {
        this.request = request;
    }
    
    /**
     * Creates a new validator for the given request.
     *
     * @param request the enrichment request to validate
     * @return a new validator instance
     */
    public static EnrichmentRequestValidator of(EnrichmentRequest request) {
        return new EnrichmentRequestValidator(request);
    }
    
    /**
     * Requires that a parameter exists and is not null.
     *
     * @param paramName the parameter name
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireParam(String paramName) {
        if (request.getParameters() == null || !request.getParameters().containsKey(paramName)) {
            errors.add("Required parameter '" + paramName + "' is missing");
        } else if (request.getParameters().get(paramName) == null) {
            errors.add("Required parameter '" + paramName + "' is null");
        }
        return this;
    }
    
    /**
     * Requires that a parameter exists and matches the given pattern.
     *
     * @param paramName the parameter name
     * @param pattern the regex pattern to match
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireParamMatching(String paramName, Pattern pattern) {
        requireParam(paramName);
        
        if (errors.isEmpty() && request.getParameters() != null) {
            Object value = request.getParameters().get(paramName);
            if (value != null && !pattern.matcher(value.toString()).matches()) {
                errors.add("Parameter '" + paramName + "' does not match required pattern: " + pattern.pattern());
            }
        }
        
        return this;
    }
    
    /**
     * Requires that a parameter exists and is of the specified type.
     *
     * @param paramName the parameter name
     * @param expectedType the expected type
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireParamOfType(String paramName, Class<?> expectedType) {
        requireParam(paramName);
        
        if (errors.isEmpty() && request.getParameters() != null) {
            Object value = request.getParameters().get(paramName);
            if (value != null && !expectedType.isInstance(value)) {
                errors.add("Parameter '" + paramName + "' must be of type " + expectedType.getSimpleName() 
                        + " but was " + value.getClass().getSimpleName());
            }
        }
        
        return this;
    }
    
    /**
     * Requires that the source DTO is present.
     *
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireSourceDto() {
        if (request.getSourceDto() == null) {
            errors.add("Source DTO is required but was null");
        }
        return this;
    }
    
    /**
     * Requires that the enrichment type is present.
     *
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireEnrichmentType() {
        if (request.getEnrichmentType() == null || request.getEnrichmentType().trim().isEmpty()) {
            errors.add("Enrichment type is required");
        }
        return this;
    }
    
    /**
     * Requires that the strategy is one of the allowed strategies.
     *
     * @param allowedStrategies the allowed strategies
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireStrategy(EnrichmentStrategy... allowedStrategies) {
        if (request.getStrategy() == null) {
            errors.add("Enrichment strategy is required");
        } else if (allowedStrategies.length > 0) {
            boolean found = Arrays.asList(allowedStrategies).contains(request.getStrategy());
            if (!found) {
                errors.add("Enrichment strategy must be one of " + Arrays.toString(allowedStrategies) 
                        + " but was " + request.getStrategy());
            }
        }
        return this;
    }
    
    /**
     * Requires that the tenant ID is present.
     *
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator requireTenantId() {
        if (request.getTenantId() == null || request.getTenantId().trim().isEmpty()) {
            errors.add("Tenant ID is required");
        }
        return this;
    }
    
    /**
     * Adds a custom validation.
     *
     * @param condition the condition that must be true
     * @param errorMessage the error message if condition is false
     * @return this validator for chaining
     */
    public EnrichmentRequestValidator require(boolean condition, String errorMessage) {
        if (!condition) {
            errors.add(errorMessage);
        }
        return this;
    }
    
    /**
     * Validates the request and throws an exception if validation fails.
     *
     * @throws EnrichmentValidationException if validation fails
     */
    public void validate() {
        if (!errors.isEmpty()) {
            String errorMessage = "Enrichment request validation failed: " + String.join("; ", errors);
            log.error(errorMessage);
            throw new EnrichmentValidationException(errorMessage, errors);
        }
    }
    
    /**
     * Validates the request and returns whether it's valid.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    /**
     * Gets the validation errors.
     *
     * @return the list of validation errors
     */
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    /**
     * Exception thrown when enrichment request validation fails.
     */
    public static class EnrichmentValidationException extends RuntimeException {
        private final List<String> errors;
        
        public EnrichmentValidationException(String message, List<String> errors) {
            super(message);
            this.errors = new ArrayList<>(errors);
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
    }
}

