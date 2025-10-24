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

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * Response DTO for the operation catalog endpoint.
 * 
 * <p>This DTO contains the list of all provider-specific operations available
 * in a data enricher, along with their metadata, schemas, and examples.</p>
 * 
 * @see com.firefly.common.data.controller.AbstractDataEnricherController#listOperations
 */
@Value
@Builder
@Schema(description = "Catalog of provider-specific operations")
public class OperationCatalogResponse {
    
    @Schema(description = "The provider name", example = "Credit Bureau Provider")
    String providerName;
    
    @Schema(description = "List of available operations")
    List<OperationInfo> operations;
    
    /**
     * Information about a single provider operation.
     */
    @Value
    @Builder
    @Schema(description = "Information about a provider operation")
    public static class OperationInfo {
        
        @Schema(description = "Unique operation identifier", example = "search-company")
        String operationId;
        
        @Schema(description = "Full path to execute this operation", 
                example = "/api/v1/enrichment/credit-bureau/operation/search-company")
        String path;
        
        @Schema(description = "HTTP method", example = "GET")
        String method;
        
        @Schema(description = "Human-readable description of what this operation does",
                example = "Search for a company by name or tax ID to obtain provider internal ID")
        String description;
        
        @Schema(description = "Tags for categorizing operations", example = "[\"lookup\", \"search\"]")
        List<String> tags;
        
        @Schema(description = "Whether this operation requires authentication", example = "true")
        boolean requiresAuth;
        
        @Schema(description = "Request DTO class name", example = "CompanySearchRequest")
        String requestType;
        
        @Schema(description = "Response DTO class name", example = "CompanySearchResponse")
        String responseType;
        
        @Schema(description = "JSON Schema for request validation")
        JsonNode requestSchema;
        
        @Schema(description = "JSON Schema for response structure")
        JsonNode responseSchema;
        
        @Schema(description = "Example request object")
        Object requestExample;
        
        @Schema(description = "Example response object")
        Object responseExample;
    }
}

