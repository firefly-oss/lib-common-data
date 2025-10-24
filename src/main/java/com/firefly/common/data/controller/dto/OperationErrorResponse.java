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

package com.firefly.common.data.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Error response for provider operation execution failures.
 * 
 * <p>This DTO is returned when a provider operation fails or cannot be found.</p>
 * 
 * @see com.firefly.common.data.controller.AbstractDataEnricherController#executeProviderOperation
 */
@Value
@Builder
@Schema(description = "Error response for provider operation failures")
public class OperationErrorResponse {
    
    @Schema(description = "Error message describing what went wrong", example = "Operation not found: invalid-operation")
    String error;
    
    @Schema(description = "The operation ID that was requested", example = "search-company")
    String operationId;
    
    @Schema(description = "The provider name", example = "Credit Bureau Provider")
    String providerName;
    
    @Schema(description = "List of available operation IDs (only included when operation not found)", 
            example = "[\"search-company\", \"validate-id\"]")
    List<String> availableOperations;
}

