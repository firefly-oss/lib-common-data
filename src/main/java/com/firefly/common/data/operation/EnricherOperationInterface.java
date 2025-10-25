package com.firefly.common.data.operation;

import reactor.core.publisher.Mono;

/**
 * Base interface for enricher-specific operations.
 *
 * <p>Enricher operations are auxiliary operations that enrichers expose to support
 * their enrichment workflow. Common use cases include:</p>
 * <ul>
 *   <li><b>ID Lookup:</b> Search for internal provider IDs before enrichment</li>
 *   <li><b>Entity Matching:</b> Fuzzy match companies/individuals in provider's database</li>
 *   <li><b>Validation:</b> Validate identifiers (tax IDs, business numbers, etc.)</li>
 *   <li><b>Quick Queries:</b> Get specific data points without full enrichment</li>
 * </ul>
 *
 * <p><b>Key Benefits of Class-Based Operations:</b></p>
 * <ul>
 *   <li><b>Separation of Concerns:</b> Each operation is an independent, testable class</li>
 *   <li><b>Dependency Injection:</b> Operations can have their own dependencies</li>
 *   <li><b>Reusability:</b> Operations can be shared across multiple enrichers</li>
 *   <li><b>Type Safety:</b> Strong typing for request/response DTOs</li>
 *   <li><b>Easy Testing:</b> Mock dependencies and test operations in isolation</li>
 * </ul>
 *
 * <p><b>Example - Company Search Operation:</b></p>
 * <pre>{@code
 * @EnricherOperation(
 *     operationId = "search-company",
 *     description = "Search for a company by name or tax ID to obtain provider internal ID",
 *     method = RequestMethod.GET,
 *     tags = {"lookup", "search"}
 * )
 * public class SearchCompanyOperation 
 *         extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> {
 *
 *     private final RestClient bureauClient;
 *
 *     public SearchCompanyOperation(RestClient bureauClient) {
 *         this.bureauClient = bureauClient;
 *     }
 *
 *     @Override
 *     protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
 *         return bureauClient.get("/search", CompanySearchResponse.class)
 *             .withQueryParam("name", request.getCompanyName())
 *             .withQueryParam("taxId", request.getTaxId())
 *             .execute()
 *             .map(result -> CompanySearchResponse.builder()
 *                 .providerId(result.getId())
 *                 .companyName(result.getName())
 *                 .taxId(result.getTaxId())
 *                 .confidence(result.getMatchScore())
 *                 .build());
 *     }
 *
 *     @Override
 *     protected void validateRequest(CompanySearchRequest request) {
 *         if (request.getCompanyName() == null && request.getTaxId() == null) {
 *             throw new IllegalArgumentException("Either companyName or taxId must be provided");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Request/Response DTOs:</b></p>
 * <pre>{@code
 * @Data
 * @Builder
 * @Schema(description = "Request to search for a company")
 * public class CompanySearchRequest {
 *     @Schema(description = "Company name to search for", example = "Acme Corp")
 *     private String companyName;
 *
 *     @Schema(description = "Tax ID to search for", example = "TAX-12345678")
 *     private String taxId;
 * }
 *
 * @Data
 * @Builder
 * @Schema(description = "Company search result")
 * public class CompanySearchResponse {
 *     @Schema(description = "Provider's internal ID", example = "PROV-12345", required = true)
 *     private String providerId;
 *
 *     @Schema(description = "Company name", example = "ACME CORPORATION", required = true)
 *     private String companyName;
 *
 *     @Schema(description = "Tax ID", example = "TAX-12345678")
 *     private String taxId;
 *
 *     @Schema(description = "Match confidence score", example = "0.95", minimum = "0", maximum = "1")
 *     private Double confidence;
 * }
 * }</pre>
 *
 * <p><b>Automatic REST Endpoint:</b></p>
 * <ul>
 *   <li>{@code GET /api/v1/enrichment/credit-bureau/search-company}</li>
 *   <li>Request: Query parameters or JSON body (depending on HTTP method)</li>
 *   <li>Response: JSON with CompanySearchResponse structure</li>
 *   <li>Automatic JSON Schema validation</li>
 *   <li>Automatic OpenAPI documentation</li>
 * </ul>
 *
 * <p><b>Registering Operations with Enrichers:</b></p>
 * <pre>{@code
 * @Service
 * public class CreditBureauEnricher extends DataEnricher<...> {
 *
 *     private final SearchCompanyOperation searchCompanyOperation;
 *     private final ValidateTaxIdOperation validateTaxIdOperation;
 *
 *     public CreditBureauEnricher(
 *             // ... enricher dependencies ...
 *             SearchCompanyOperation searchCompanyOperation,
 *             ValidateTaxIdOperation validateTaxIdOperation) {
 *         // ... enricher initialization ...
 *         this.searchCompanyOperation = searchCompanyOperation;
 *         this.validateTaxIdOperation = validateTaxIdOperation;
 *     }
 *
 *     @Override
 *     public List<EnricherOperationInterface<?, ?>> getOperations() {
 *         return List.of(
 *             searchCompanyOperation,
 *             validateTaxIdOperation
 *         );
 *     }
 * }
 * }</pre>
 *
 * @param <TRequest> the request DTO type
 * @param <TResponse> the response DTO type
 * @see AbstractEnricherOperation
 * @see EnricherOperation
 * @see EnricherOperationMetadata
 */
public interface EnricherOperationInterface<TRequest, TResponse> {

    /**
     * Executes the enricher operation.
     *
     * <p>This method handles the complete operation lifecycle including:</p>
     * <ul>
     *   <li>Request validation</li>
     *   <li>Execution of the operation logic</li>
     *   <li>Error handling</li>
     *   <li>Response transformation</li>
     * </ul>
     *
     * @param request the request DTO
     * @return a Mono emitting the response DTO
     */
    Mono<TResponse> execute(TRequest request);

    /**
     * Gets the metadata for this operation.
     *
     * <p>The metadata includes:</p>
     * <ul>
     *   <li>Operation ID</li>
     *   <li>Description</li>
     *   <li>HTTP method</li>
     *   <li>Tags</li>
     *   <li>Request/Response JSON schemas</li>
     *   <li>Example request/response</li>
     * </ul>
     *
     * @return the operation metadata
     */
    EnricherOperationMetadata getMetadata();

    /**
     * Gets the request type class.
     *
     * @return the request DTO class
     */
    Class<TRequest> getRequestType();

    /**
     * Gets the response type class.
     *
     * @return the response DTO class
     */
    Class<TResponse> getResponseType();
}

