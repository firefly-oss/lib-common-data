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

package com.firefly.common.data.enrichment.port;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Port interface for third-party enrichment providers.
 * 
 * <p>This interface defines the contract for communicating with external data enrichment
 * providers. Implementations handle the specific details of each provider's API,
 * authentication, and data formats.</p>
 * 
 * <p>This follows the Hexagonal Architecture (Ports and Adapters) pattern where:</p>
 * <ul>
 *   <li><b>Port</b>: This interface defines what the application needs from providers</li>
 *   <li><b>Adapter</b>: Implementations adapt specific provider APIs to this interface</li>
 * </ul>
 * 
 * <p><b>Example Implementation:</b></p>
 * <pre>{@code
 * @Component
 * public class FinancialDataProvider implements EnrichmentProvider {
 *
 *     private final RestClient financialDataClient;
 *
 *     public FinancialDataProvider(RestClient financialDataClient) {
 *         this.financialDataClient = financialDataClient;
 *     }
 *     
 *     @Override
 *     public Mono<Map<String, Object>> fetchEnrichmentData(
 *             String enrichmentType, 
 *             Map<String, Object> parameters) {
 *         
 *         String companyId = (String) parameters.get("companyId");
 *         
 *         return financialDataClient.get("/api/v1/companies/{id}", FinancialDataResponse.class)
 *             .withPathParam("id", companyId)
 *             .withQueryParam("includeFinancials",
 *                 parameters.getOrDefault("includeFinancials", true))
 *             .execute()
 *             .map(this::convertToStandardFormat);
 *     }
 *
 *     @Override
 *     public String getProviderName() {
 *         return "Financial Data Provider";
 *     }
 *
 *     @Override
 *     public String[] getSupportedEnrichmentTypes() {
 *         return new String[]{"company-profile", "company-financials"};
 *     }
 *     
 *     private Map<String, Object> convertToStandardFormat(OrbisResponse response) {
 *         // Convert provider-specific response to standard format
 *         return Map.of(
 *             "companyName", response.getName(),
 *             "address", response.getAddress(),
 *             "revenue", response.getFinancials().getRevenue(),
 *             // ... more fields
 *         );
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Integration with ServiceClient:</b></p>
 * <pre>{@code
 * // REST provider
 * @Component
 * public class RestBasedProvider implements EnrichmentProvider {
 *     private final RestClient client = ServiceClient.rest("provider")
 *         .baseUrl("https://api.provider.com")
 *         .defaultHeader("Authorization", "Bearer ${token}")
 *         .build();
 *     // ... implementation
 * }
 * 
 * // SOAP provider
 * @Component
 * public class SoapBasedProvider implements EnrichmentProvider {
 *     private final SoapClient client = ServiceClient.soap("legacy-provider")
 *         .wsdlUrl("https://legacy.provider.com/service?wsdl")
 *         .credentials("username", "password")
 *         .build();
 *     // ... implementation
 * }
 * 
 * // SDK-based provider
 * @Component
 * public class SdkBasedProvider implements EnrichmentProvider {
 *     private final ProviderSDK sdk = new ProviderSDK(apiKey);
 *     // ... implementation using provider's SDK
 * }
 * }</pre>
 */
public interface EnrichmentProvider {
    
    /**
     * Fetches enrichment data from the provider.
     * 
     * <p>This method calls the provider's API with the specified parameters
     * and returns the enrichment data in a standardized format.</p>
     * 
     * <p>The returned Map should contain the enrichment data in a format
     * that can be easily mapped to DTOs. Common keys might include:</p>
     * <ul>
     *   <li>companyName, address, taxId (for company enrichment)</li>
     *   <li>creditScore, paymentHistory (for credit reports)</li>
     *   <li>standardizedAddress, coordinates (for address verification)</li>
     * </ul>
     * 
     * @param enrichmentType the type of enrichment to perform
     * @param parameters provider-specific parameters for the request
     * @return a Mono emitting a Map with the enrichment data
     */
    Mono<Map<String, Object>> fetchEnrichmentData(String enrichmentType, 
                                                   Map<String, Object> parameters);
    
    /**
     * Gets the name of this provider.
     * 
     * @return the provider name (e.g., "Financial Data Provider", "Credit Bureau Provider")
     */
    String getProviderName();
    
    /**
     * Gets the types of enrichment this provider supports.
     * 
     * @return array of supported enrichment types
     */
    String[] getSupportedEnrichmentTypes();
    
    /**
     * Checks if this provider supports the specified enrichment type.
     * 
     * @param enrichmentType the enrichment type to check
     * @return true if supported, false otherwise
     */
    default boolean supportsEnrichmentType(String enrichmentType) {
        if (enrichmentType == null) {
            return false;
        }
        String[] supportedTypes = getSupportedEnrichmentTypes();
        if (supportedTypes == null || supportedTypes.length == 0) {
            return false;
        }
        for (String type : supportedTypes) {
            if (enrichmentType.equals(type)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Performs a health check on the provider.
     * 
     * <p>This can be used to verify that the provider is reachable
     * and properly configured.</p>
     * 
     * @return a Mono emitting true if healthy, false otherwise
     */
    default Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }
    
    /**
     * Gets the estimated cost for an enrichment operation (optional).
     * 
     * <p>Some providers charge per API call or per data point.
     * This method can return the estimated cost for billing purposes.</p>
     * 
     * @param enrichmentType the type of enrichment
     * @param parameters the request parameters
     * @return a Mono emitting the estimated cost, or empty if not applicable
     */
    default Mono<Double> getEstimatedCost(String enrichmentType, 
                                          Map<String, Object> parameters) {
        return Mono.empty();
    }
}

