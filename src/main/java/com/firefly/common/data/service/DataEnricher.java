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

package com.firefly.common.data.service;

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service interface for data enrichment operations.
 * 
 * <p>This interface defines the contract for enriching data from third-party providers
 * such as financial data providers, credit bureaus, or other data enrichment services.</p>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Enriching company profiles with financial and corporate data</li>
 *   <li>Obtaining credit reports for individuals or companies</li>
 *   <li>Validating and standardizing addresses</li>
 *   <li>Enriching director/officer information</li>
 *   <li>Verifying business identities</li>
 * </ul>
 * 
 * <p><b>Example Implementation:</b></p>
 * <pre>{@code
 * @Service
 * public class FinancialDataEnricher extends AbstractResilientDataEnricher {
 *
 *     private final RestClient financialDataClient;
 *
 *     public FinancialDataEnricher(
 *             JobTracingService tracingService,
 *             JobMetricsService metricsService,
 *             ResiliencyDecoratorService resiliencyService,
 *             EnrichmentEventPublisher eventPublisher,
 *             RestClient financialDataClient) {
 *         super(tracingService, metricsService, resiliencyService, eventPublisher);
 *         this.financialDataClient = financialDataClient;
 *     }
 *
 *     @Override
 *     protected Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
 *         String companyId = (String) request.getParameters().get("companyId");
 *
 *         return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
 *             .withPathParam("id", companyId)
 *             .execute()
 *             .map(financialData -> {
 *                 CompanyProfileDTO enriched = applyStrategy(
 *                     request.getStrategy(),
 *                     request.getSourceDto(),
 *                     financialData
 *                 );
 *
 *                 return EnrichmentResponse.success(
 *                     enriched,
 *                     getProviderName(),
 *                     request.getEnrichmentType(),
 *                     "Company data enriched successfully"
 *                 );
 *             });
 *     }
 *
 *     @Override
 *     public String getProviderName() {
 *         return "Financial Data Provider";
 *     }
 *
 *     @Override
 *     public String getEnricherDescription() {
 *         return "Enriches company data with financial and corporate information";
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Integration with ServiceClient:</b></p>
 * <p>Data enrichers can use the ServiceClient from lib-common-client for REST/SOAP/gRPC calls:</p>
 * <pre>{@code
 * // Using REST client
 * private final RestClient providerClient = ServiceClient.rest("provider-service")
 *     .baseUrl("https://api.provider.com")
 *     .defaultHeader("Authorization", "Bearer ${token}")
 *     .build();
 * 
 * // Using SOAP client
 * private final SoapClient soapClient = ServiceClient.soap("legacy-provider")
 *     .wsdlUrl("https://legacy.provider.com/service?wsdl")
 *     .credentials("username", "password")
 *     .build();
 * 
 * // Using gRPC client
 * private final GrpcClient<ProviderServiceStub> grpcClient = 
 *     ServiceClient.grpc("grpc-provider", ProviderServiceStub.class)
 *         .address("provider:9090")
 *         .stubFactory(channel -> ProviderServiceGrpc.newStub(channel))
 *         .build();
 * }</pre>
 * 
 * <p><b>Automatic Features (when extending AbstractResilientDataEnricher):</b></p>
 * <ul>
 *   <li>Distributed tracing via Micrometer</li>
 *   <li>Metrics collection (enrichment time, success/failure rates, provider costs)</li>
 *   <li>Circuit breaker, retry, rate limiting, and bulkhead patterns</li>
 *   <li>Audit trail persistence</li>
 *   <li>Event publishing (enrichment started, completed, failed)</li>
 *   <li>Comprehensive logging</li>
 * </ul>
 * 
 * @see com.firefly.common.data.enrichment.service.AbstractResilientDataEnricher
 * @see com.firefly.common.data.enrichment.model.EnrichmentRequest
 * @see com.firefly.common.data.enrichment.model.EnrichmentResponse
 * @see com.firefly.common.data.enrichment.model.EnrichmentStrategy
 */
public interface DataEnricher {
    
    /**
     * Enriches data using the specified enrichment request.
     *
     * <p>This method performs the data enrichment operation by:</p>
     * <ol>
     *   <li>Calling the third-party provider with the request parameters</li>
     *   <li>Receiving the provider's response</li>
     *   <li>Applying the enrichment strategy (ENHANCE, MERGE, REPLACE, or RAW)</li>
     *   <li>Returning the enriched data in the response</li>
     * </ol>
     *
     * <p>The method is reactive and returns a Mono for non-blocking execution.</p>
     *
     * @param request the enrichment request containing source data, parameters, and strategy
     * @return a Mono emitting the enrichment response with enriched data
     */
    Mono<EnrichmentResponse> enrich(EnrichmentRequest request);

    /**
     * Enriches multiple data items in a batch operation.
     *
     * <p>This method performs batch enrichment with the following features:</p>
     * <ul>
     *   <li><b>Parallel Processing</b> - Processes multiple requests concurrently</li>
     *   <li><b>Cache Integration</b> - Checks cache before calling provider</li>
     *   <li><b>Error Handling</b> - Individual failures don't fail the entire batch</li>
     *   <li><b>Rate Limiting</b> - Respects provider rate limits via parallelism control</li>
     * </ul>
     *
     * <p><b>Default Implementation:</b> Processes requests sequentially using {@link #enrich(EnrichmentRequest)}.
     * Implementations should override this for better performance with parallel processing.</p>
     *
     * <p><b>Example Usage:</b></p>
     * <pre>{@code
     * List<EnrichmentRequest> requests = List.of(
     *     EnrichmentRequest.builder()
     *         .enrichmentType("company-profile")
     *         .strategy(EnrichmentStrategy.ENHANCE)
     *         .parameters(Map.of("companyId", "12345"))
     *         .build(),
     *     EnrichmentRequest.builder()
     *         .enrichmentType("company-profile")
     *         .strategy(EnrichmentStrategy.ENHANCE)
     *         .parameters(Map.of("companyId", "67890"))
     *         .build()
     * );
     *
     * Flux<EnrichmentResponse> responses = enricher.enrichBatch(requests);
     * }</pre>
     *
     * @param requests the list of enrichment requests to process
     * @return a Flux emitting enrichment responses in the same order as requests
     */
    default Flux<EnrichmentResponse> enrichBatch(List<EnrichmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Flux.empty();
        }

        // Default implementation: sequential processing
        return Flux.fromIterable(requests)
            .flatMapSequential(this::enrich);
    }
    
    /**
     * Gets the name of the enrichment provider.
     * 
     * <p>This identifies the third-party provider being used for enrichment.</p>
     * 
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"Financial Data Provider"</li>
     *   <li>"Credit Bureau Provider"</li>
     *   <li>"Business Data Provider"</li>
     * </ul>
     * 
     * @return the provider name
     */
    String getProviderName();
    
    /**
     * Gets the types of enrichment this enricher supports.
     *
     * <p><b>Primary Purpose:</b> Used by the {@code listProviders()} endpoint in
     * {@code AbstractDataEnricherController} to find all providers that support
     * the same enrichment types.</p>
     *
     * <p><b>Secondary Purpose:</b> Can be used for programmatic lookup via {@link DataEnricherRegistry}
     * when you need to dynamically select an enricher at runtime (optional use case).</p>
     *
     * <p><b>Important:</b> In the standard architecture, each enricher has its own dedicated
     * REST API endpoint (e.g., {@code /api/v1/enrichment/company-profile/enrich}), and the
     * controller already knows which enricher to use (injected in constructor). The
     * {@code enrichmentType} field in {@link EnrichmentRequest} is used for logging and
     * metadata, NOT for routing.</p>
     *
     * <p><b>Example - listProviders() Endpoint:</b></p>
     * <pre>{@code
     * // FinancialDataEnricher declares it handles these types:
     * @Override
     * public String[] getSupportedEnrichmentTypes() {
     *     return new String[]{"company-profile", "company-financials"};
     * }
     *
     * // When client calls GET /api/v1/enrichment/company-profile/providers
     * // The controller uses this method to find all enrichers that support "company-profile"
     * // and returns their provider names: ["Financial Data Provider", "Business Data Provider"]
     * }</pre>
     *
     * <p><b>Example - Optional Programmatic Lookup:</b></p>
     * <pre>{@code
     * // If you need to dynamically select an enricher at runtime:
     * DataEnricher enricher = registry.getEnricherForType("company-profile")
     *     .orElseThrow(() -> new EnricherNotFoundException("company-profile"));
     *
     * // This is optional - most applications use dedicated controllers instead
     * }</pre>
     *
     * <p><b>Multi-Type Enrichers:</b> An enricher can support multiple related types.
     * For example, a financial data provider might support both "company-profile" and
     * "company-financials" using the same underlying API.</p>
     *
     * <p><b>Common Enrichment Types:</b></p>
     * <ul>
     *   <li>{@code "company-profile"} - Company information and corporate data</li>
     *   <li>{@code "company-financials"} - Financial statements and metrics</li>
     *   <li>{@code "credit-report-individual"} - Personal credit reports</li>
     *   <li>{@code "credit-report-business"} - Business credit reports</li>
     *   <li>{@code "address-verification"} - Address validation and standardization</li>
     *   <li>{@code "director-information"} - Director and officer details</li>
     * </ul>
     *
     * @return array of supported enrichment types, or empty array if none
     */
    default String[] getSupportedEnrichmentTypes() {
        return new String[0];
    }
    
    /**
     * Gets a description of what this enricher does.
     *
     * <p>This description is used for:</p>
     * <ul>
     *   <li>Documentation and API specs</li>
     *   <li>Enricher discovery and cataloging</li>
     *   <li>Developer onboarding</li>
     *   <li>Operational dashboards</li>
     * </ul>
     *
     * @return the enricher description
     */
    default String getEnricherDescription() {
        return "Data enrichment service";
    }

    /**
     * Gets the REST API endpoint path for this enricher.
     *
     * <p>This is used by the discovery endpoint to provide clients with the
     * direct URL to call for enrichment operations.</p>
     *
     * <p><b>Important:</b> This should return the path relative to the base URL,
     * including the /enrich suffix. For example:</p>
     * <pre>
     * /api/v1/enrichment/provider-a-credit/enrich
     * /api/v1/enrichment/provider-b-company/enrich
     * </pre>
     *
     * <p><b>Default Implementation:</b> Returns null, which means the endpoint
     * is not exposed in discovery. Enrichers should override this to provide
     * their endpoint path.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Override
     * public String getEnrichmentEndpoint() {
     *     return "/api/v1/enrichment/provider-a-credit/enrich";
     * }
     * }</pre>
     *
     * @return the REST API endpoint path, or null if not applicable
     */
    default String getEnrichmentEndpoint() {
        return null;
    }
    
    /**
     * Checks if this enricher supports the specified enrichment type.
     * 
     * @param enrichmentType the enrichment type to check
     * @return true if this enricher supports the type, false otherwise
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
     * Checks if this enricher is available and ready to process requests.
     * 
     * <p>This can be used for health checks and circuit breaker status.</p>
     * 
     * @return a Mono emitting true if the enricher is ready, false otherwise
     */
    default Mono<Boolean> isReady() {
        return Mono.just(true);
    }
}

