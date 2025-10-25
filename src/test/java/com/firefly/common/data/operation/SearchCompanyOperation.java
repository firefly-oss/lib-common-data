package com.firefly.common.data.operation;

import com.firefly.common.data.operation.dto.CompanySearchRequest;
import com.firefly.common.data.operation.dto.CompanySearchResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

/**
 * Example enricher operation for searching companies.
 */
@EnricherOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.GET,
    tags = {"lookup", "search"},
    requiresAuth = true
)
public class SearchCompanyOperation
        extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> {

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        // Simulate a search operation
        return Mono.just(CompanySearchResponse.builder()
            .providerId("PROV-12345")
            .companyName(request.getCompanyName() != null 
                ? request.getCompanyName().toUpperCase() 
                : "UNKNOWN COMPANY")
            .taxId(request.getTaxId())
            .confidence(0.95)
            .build());
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.getCompanyName() == null && request.getTaxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
        
        if (request.getMinConfidence() != null && 
            (request.getMinConfidence() < 0 || request.getMinConfidence() > 1)) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1");
        }
    }
}

