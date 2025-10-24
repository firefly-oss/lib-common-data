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

package com.firefly.common.data.controller;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Interface for data enrichers that expose provider-specific operations.
 *
 * <p>This interface allows enrichers to declare a catalog of auxiliary operations
 * that are specific to the provider's API. These operations are automatically
 * exposed as REST endpoints by {@link AbstractDataEnricherController}.</p>
 *
 * <p><b>Common Use Cases:</b></p>
 * <ul>
 *   <li><b>ID Lookup:</b> Search for internal provider IDs before enrichment</li>
 *   <li><b>Entity Matching:</b> Fuzzy match companies/individuals in provider's database</li>
 *   <li><b>Validation:</b> Validate identifiers (CIF, DUNS, VAT, etc.)</li>
 *   <li><b>Metadata:</b> Retrieve provider-specific metadata or configuration</li>
 * </ul>
 *
 * <p><b>Example - Equifax Spain Enricher:</b></p>
 * <pre>{@code
 * @Service
 * public class EquifaxSpainEnricher 
 *         extends TypedDataEnricher<CompanyDTO, EquifaxResponse, CompanyDTO>
 *         implements ProviderOperationCatalog {
 *
 *     @Override
 *     public List<ProviderOperation> getOperationCatalog() {
 *         return List.of(
 *             // Search company by name/CIF to get Equifax internal ID
 *             ProviderOperation.builder()
 *                 .operationId("search-company")
 *                 .path("/search-company")
 *                 .method(RequestMethod.GET)
 *                 .description("Search for a company by name or CIF to obtain Equifax internal ID")
 *                 .requestExample(Map.of(
 *                     "companyName", "Acme Corporation",
 *                     "cif", "A12345678"
 *                 ))
 *                 .responseExample(Map.of(
 *                     "equifaxId", "ES-12345",
 *                     "companyName", "ACME CORPORATION SL",
 *                     "cif", "A12345678",
 *                     "confidence", 0.95
 *                 ))
 *                 .tags(new String[]{"lookup", "search"})
 *                 .build(),
 *
 *             // Validate CIF exists in Equifax
 *             ProviderOperation.builder()
 *                 .operationId("validate-cif")
 *                 .path("/validate-cif")
 *                 .method(RequestMethod.GET)
 *                 .description("Validate that a CIF exists in Equifax database")
 *                 .requestExample(Map.of("cif", "A12345678"))
 *                 .responseExample(Map.of(
 *                     "valid", true,
 *                     "equifaxId", "ES-12345"
 *                 ))
 *                 .tags(new String[]{"validation"})
 *                 .build()
 *         );
 *     }
 *
 *     @Override
 *     public Mono<Map<String, Object>> executeOperation(
 *             String operationId, 
 *             Map<String, Object> parameters) {
 *         
 *         return switch (operationId) {
 *             case "search-company" -> searchCompany(parameters);
 *             case "validate-cif" -> validateCif(parameters);
 *             default -> Mono.error(new IllegalArgumentException(
 *                 "Unknown operation: " + operationId));
 *         };
 *     }
 *
 *     private Mono<Map<String, Object>> searchCompany(Map<String, Object> params) {
 *         String companyName = (String) params.get("companyName");
 *         String cif = (String) params.get("cif");
 *         
 *         // Call Equifax search API
 *         return equifaxClient.searchCompany(companyName, cif)
 *             .map(result -> Map.of(
 *                 "equifaxId", result.getId(),
 *                 "companyName", result.getName(),
 *                 "cif", result.getCif(),
 *                 "confidence", result.getMatchScore()
 *             ));
 *     }
 *
 *     private Mono<Map<String, Object>> validateCif(Map<String, Object> params) {
 *         String cif = (String) params.get("cif");
 *         
 *         // Call Equifax validation API
 *         return equifaxClient.validateCif(cif)
 *             .map(result -> Map.of(
 *                 "valid", result.isValid(),
 *                 "equifaxId", result.getId()
 *             ));
 *     }
 * }
 * }</pre>
 *
 * <p><b>Resulting REST Endpoints:</b></p>
 * <pre>
 * POST /api/v1/enrichment/equifax-spain/enrich          (standard enrichment)
 * GET  /api/v1/enrichment/equifax-spain/search-company  (provider-specific)
 * GET  /api/v1/enrichment/equifax-spain/validate-cif    (provider-specific)
 * GET  /api/v1/enrichment/equifax-spain/health          (standard health check)
 * </pre>
 *
 * <p><b>Discovery Response:</b></p>
 * <pre>{@code
 * {
 *   "providerName": "Equifax Spain",
 *   "supportedTypes": ["credit-report", "company-profile"],
 *   "description": "Equifax Spain data enrichment services",
 *   "endpoints": [
 *     "/api/v1/enrichment/equifax-spain/enrich"
 *   ],
 *   "operations": [
 *     {
 *       "operationId": "search-company",
 *       "path": "/api/v1/enrichment/equifax-spain/search-company",
 *       "method": "GET",
 *       "description": "Search for a company by name or CIF to obtain Equifax internal ID",
 *       "tags": ["lookup", "search"]
 *     },
 *     {
 *       "operationId": "validate-cif",
 *       "path": "/api/v1/enrichment/equifax-spain/validate-cif",
 *       "method": "GET",
 *       "description": "Validate that a CIF exists in Equifax database",
 *       "tags": ["validation"]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @see ProviderOperation
 * @see AbstractDataEnricherController
 */
public interface ProviderOperationCatalog {

    /**
     * Gets the catalog of provider-specific operations.
     *
     * <p>Each operation will be automatically exposed as a REST endpoint
     * by the controller.</p>
     *
     * @return list of provider operations, or empty list if none
     */
    default List<ProviderOperation> getOperationCatalog() {
        return List.of();
    }

    /**
     * Executes a provider-specific operation.
     *
     * <p>This method is called by the controller when a provider-specific
     * endpoint is invoked. The implementation should route to the appropriate
     * handler based on the operationId.</p>
     *
     * <p><b>Example Implementation:</b></p>
     * <pre>{@code
     * @Override
     * public Mono<Map<String, Object>> executeOperation(
     *         String operationId, 
     *         Map<String, Object> parameters) {
     *     
     *     return switch (operationId) {
     *         case "search-company" -> searchCompany(parameters);
     *         case "validate-cif" -> validateCif(parameters);
     *         case "duns-lookup" -> dunsLookup(parameters);
     *         default -> Mono.error(new IllegalArgumentException(
     *             "Unknown operation: " + operationId));
     *     };
     * }
     * }</pre>
     *
     * @param operationId the operation identifier
     * @param parameters the operation parameters (from query params or request body)
     * @return a Mono emitting the operation result
     */
    default Mono<Map<String, Object>> executeOperation(
            String operationId, 
            Map<String, Object> parameters) {
        return Mono.error(new UnsupportedOperationException(
            "Operation not implemented: " + operationId));
    }
}

