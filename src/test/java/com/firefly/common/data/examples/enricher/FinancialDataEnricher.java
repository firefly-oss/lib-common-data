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

package com.firefly.common.data.examples.enricher;

import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.examples.dto.CompanyProfileDTO;
import com.firefly.common.data.examples.dto.FinancialDataResponse;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.TypedDataEnricher;
import reactor.core.publisher.Mono;

/**
 * Example enricher that demonstrates using TypedDataEnricher with a REST provider.
 * This is a working example used in documentation and tests.
 * 
 * <p>In a real implementation, you would inject a RestClient from lib-common-client
 * to call the actual provider API. This example simulates the provider response.</p>
 */
public class FinancialDataEnricher 
        extends TypedDataEnricher<CompanyProfileDTO, FinancialDataResponse, CompanyProfileDTO> {
    
    public FinancialDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);
    }
    
    @Override
    protected Mono<FinancialDataResponse> fetchProviderData(EnrichmentRequest request) {
        // Automatic validation with fluent API
        String companyId = request.requireParam("companyId");
        
        // In a real implementation, you would use RestClient:
        // return restClient.get("/companies/{id}", FinancialDataResponse.class)
        //     .withPathParam("id", companyId)
        //     .execute();
        
        // For this example, simulate a provider response
        FinancialDataResponse response = FinancialDataResponse.builder()
                .id(companyId)
                .businessName("Acme Corporation")
                .primaryAddress("123 Business St, New York, NY 10001")
                .sector("Technology")
                .totalEmployees(500)
                .revenue(50000000.0)
                .ein("12-3456789")
                .websiteUrl("https://www.acme-corp.example")
                .build();
        
        return Mono.just(response);
    }
    
    @Override
    protected CompanyProfileDTO mapToTarget(FinancialDataResponse providerData) {
        // Simple mapping - strategy is applied automatically!
        return CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .employeeCount(providerData.getTotalEmployees())
                .annualRevenue(providerData.getRevenue())
                .taxId(providerData.getEin())
                .website(providerData.getWebsiteUrl())
                .build();
    }
    
    @Override
    public String getProviderName() {
        return "Financial Data Provider";
    }
    
    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"company-profile", "company-financials"};
    }
    
    @Override
    public String getEnricherDescription() {
        return "Enriches company data with financial and corporate information";
    }
}

