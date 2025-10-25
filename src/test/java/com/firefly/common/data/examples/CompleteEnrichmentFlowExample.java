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

package com.firefly.common.data.examples;

import com.firefly.common.data.enrichment.EnrichmentStrategyApplier;
import com.firefly.common.data.examples.dto.CompanyProfileDTO;
import com.firefly.common.data.examples.dto.FinancialDataResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complete example demonstrating the full data enrichment flow.
 * 
 * <h2>How Data Enrichment Works</h2>
 * 
 * <p>The data enrichment process follows these steps:</p>
 * 
 * <ol>
 *   <li><b>Client creates EnrichmentRequest</b> with:
 *     <ul>
 *       <li><code>enrichmentType</code> - identifies which enricher to use (e.g., "company-profile")</li>
 *       <li><code>sourceDto</code> - the partial/incomplete data you already have</li>
 *       <li><code>parameters</code> - provider-specific parameters (e.g., companyId)</li>
 *       <li><code>strategy</code> - how to merge source + provider data (ENHANCE/MERGE/REPLACE/RAW)</li>
 *     </ul>
 *   </li>
 *   
 *   <li><b>DataEnricherRegistry routes the request</b>:
 *     <ul>
 *       <li>Looks up enricher by <code>enrichmentType</code></li>
 *       <li>Each enricher declares supported types via <code>getSupportedEnrichmentTypes()</code></li>
 *       <li>Registry matches request type to enricher's supported types</li>
 *     </ul>
 *   </li>
 *   
 *   <li><b>Enricher fetches provider data</b>:
 *     <ul>
 *       <li>Calls <code>fetchProviderData()</code> to get data from third-party API</li>
 *       <li>Returns raw provider response (e.g., FinancialDataResponse)</li>
 *     </ul>
 *   </li>
 *   
 *   <li><b>Enricher maps provider data to target DTO</b>:
 *     <ul>
 *       <li>Calls <code>mapToTarget()</code> to convert provider format to your DTO format</li>
 *       <li>Returns mapped data (e.g., CompanyProfileDTO)</li>
 *     </ul>
 *   </li>
 *   
 *   <li><b>Strategy is applied automatically</b>:
 *     <ul>
 *       <li><code>EnrichmentStrategyApplier.apply()</code> merges sourceDto + mappedData</li>
 *       <li><b>ENHANCE</b>: Only fills null/empty fields from provider (preserves existing data)</li>
 *       <li><b>MERGE</b>: Combines both, provider wins conflicts</li>
 *       <li><b>REPLACE</b>: Uses only provider data, ignores source</li>
 *       <li><b>RAW</b>: Returns provider data as-is without mapping</li>
 *     </ul>
 *   </li>
 *   
 *   <li><b>Response is built with metadata</b>:
 *     <ul>
 *       <li>Enriched DTO is wrapped in EnrichmentResponse</li>
 *       <li>Includes: provider name, enrichment type, strategy used, fields enriched count</li>
 *       <li>Optionally includes: cost, confidence score, raw provider response</li>
 *     </ul>
 *   </li>
 * </ol>
 * 
 * <h2>Example Scenario</h2>
 * 
 * <p>You have partial company data (just ID and name) and want to enrich it with
 * financial information from a third-party provider.</p>
 * 
 * <pre>
 * BEFORE (sourceDto):
 * {
 *   "companyId": "12345",
 *   "name": "Acme Corp",
 *   "registeredAddress": null,
 *   "industry": null,
 *   "employeeCount": null,
 *   "annualRevenue": null
 * }
 * 
 * PROVIDER DATA (from API):
 * {
 *   "id": "12345",
 *   "businessName": "Acme Corporation",
 *   "primaryAddress": "123 Main St, New York, NY",
 *   "sector": "Technology",
 *   "totalEmployees": 500,
 *   "revenue": 50000000.0
 * }
 * 
 * AFTER ENHANCE (enrichedDto):
 * {
 *   "companyId": "12345",
 *   "name": "Acme Corp",                    // Preserved from source
 *   "registeredAddress": "123 Main St...",  // Added from provider
 *   "industry": "Technology",                // Added from provider
 *   "employeeCount": 500,                    // Added from provider
 *   "annualRevenue": 50000000.0              // Added from provider
 * }
 * </pre>
 */
public class CompleteEnrichmentFlowExample {
    
    /**
     * Demonstrates the ENHANCE strategy.
     * 
     * <p>ENHANCE fills only null/empty fields from provider data,
     * preserving all existing data in the source DTO.</p>
     */
    @Test
    void demonstrateEnhanceStrategy() {
        // STEP 1: You have partial company data
        CompanyProfileDTO sourceDto = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")  // You already have the name
                // Other fields are null - need enrichment
                .build();
        
        // STEP 2: Provider returns complete data
        FinancialDataResponse providerData = FinancialDataResponse.builder()
                .id("12345")
                .businessName("Acme Corporation")  // Different name from provider
                .primaryAddress("123 Main St, New York, NY")
                .sector("Technology")
                .totalEmployees(500)
                .revenue(50000000.0)
                .build();
        
        // STEP 3: Map provider data to your DTO format
        CompanyProfileDTO mappedData = CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .employeeCount(providerData.getTotalEmployees())
                .annualRevenue(providerData.getRevenue())
                .build();
        
        // STEP 4: Apply ENHANCE strategy
        // This merges sourceDto + mappedData, keeping source values where they exist
        CompanyProfileDTO enrichedDto = EnrichmentStrategyApplier.apply(
                EnrichmentStrategy.ENHANCE,
                sourceDto,
                mappedData,
                CompanyProfileDTO.class
        );
        
        // VERIFY: Source data is preserved, null fields are filled
        assertThat(enrichedDto.getCompanyId()).isEqualTo("12345");
        assertThat(enrichedDto.getName()).isEqualTo("Acme Corp");  // Source name preserved!
        assertThat(enrichedDto.getRegisteredAddress()).isEqualTo("123 Main St, New York, NY");  // Added
        assertThat(enrichedDto.getIndustry()).isEqualTo("Technology");  // Added
        assertThat(enrichedDto.getEmployeeCount()).isEqualTo(500);  // Added
        assertThat(enrichedDto.getAnnualRevenue()).isEqualTo(50000000.0);  // Added
    }
    
    /**
     * Demonstrates the MERGE strategy.
     * 
     * <p>MERGE combines source and provider data, with provider data
     * taking precedence in case of conflicts.</p>
     */
    @Test
    void demonstrateMergeStrategy() {
        // Source has some data
        CompanyProfileDTO sourceDto = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")  // Old name
                .employeeCount(450)  // Old employee count
                .build();
        
        // Provider has updated data
        FinancialDataResponse providerData = FinancialDataResponse.builder()
                .id("12345")
                .businessName("Acme Corporation")  // Updated name
                .primaryAddress("123 Main St, New York, NY")
                .sector("Technology")
                .totalEmployees(500)  // Updated employee count
                .revenue(50000000.0)
                .build();
        
        // Map provider data
        CompanyProfileDTO mappedData = CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .employeeCount(providerData.getTotalEmployees())
                .annualRevenue(providerData.getRevenue())
                .build();
        
        // Apply MERGE strategy - provider wins conflicts
        CompanyProfileDTO enrichedDto = EnrichmentStrategyApplier.apply(
                EnrichmentStrategy.MERGE,
                sourceDto,
                mappedData,
                CompanyProfileDTO.class
        );
        
        // VERIFY: Provider data overwrites source data
        assertThat(enrichedDto.getName()).isEqualTo("Acme Corporation");  // Provider name wins!
        assertThat(enrichedDto.getEmployeeCount()).isEqualTo(500);  // Provider count wins!
        assertThat(enrichedDto.getRegisteredAddress()).isEqualTo("123 Main St, New York, NY");
        assertThat(enrichedDto.getIndustry()).isEqualTo("Technology");
        assertThat(enrichedDto.getAnnualRevenue()).isEqualTo(50000000.0);
    }
    
    /**
     * Demonstrates the REPLACE strategy.
     * 
     * <p>REPLACE uses only provider data, completely ignoring the source DTO.</p>
     */
    @Test
    void demonstrateReplaceStrategy() {
        // Source data (will be ignored)
        CompanyProfileDTO sourceDto = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Old Name")
                .build();
        
        // Provider data
        FinancialDataResponse providerData = FinancialDataResponse.builder()
                .id("12345")
                .businessName("Acme Corporation")
                .primaryAddress("123 Main St, New York, NY")
                .sector("Technology")
                .totalEmployees(500)
                .revenue(50000000.0)
                .build();
        
        // Map provider data
        CompanyProfileDTO mappedData = CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .employeeCount(providerData.getTotalEmployees())
                .annualRevenue(providerData.getRevenue())
                .build();
        
        // Apply REPLACE strategy - source is completely ignored
        CompanyProfileDTO enrichedDto = EnrichmentStrategyApplier.apply(
                EnrichmentStrategy.REPLACE,
                sourceDto,
                mappedData,
                CompanyProfileDTO.class
        );
        
        // VERIFY: Only provider data is used
        assertThat(enrichedDto.getName()).isEqualTo("Acme Corporation");  // Provider data only
        assertThat(enrichedDto.getRegisteredAddress()).isEqualTo("123 Main St, New York, NY");
        assertThat(enrichedDto.getIndustry()).isEqualTo("Technology");
        assertThat(enrichedDto.getEmployeeCount()).isEqualTo(500);
        assertThat(enrichedDto.getAnnualRevenue()).isEqualTo(50000000.0);
    }
    
    /**
     * Demonstrates the RAW strategy.
     *
     * <p>RAW returns the provider data as-is without any mapping or transformation.
     * This is useful when you need the raw provider response format.</p>
     */
    @Test
    void demonstrateRawStrategy() {
        // Source data (will be ignored)
        CompanyProfileDTO sourceDto = CompanyProfileDTO.builder()
                .companyId("12345")
                .name("Acme Corp")
                .build();

        // Provider data in its raw format
        FinancialDataResponse providerData = FinancialDataResponse.builder()
                .id("12345")
                .businessName("Acme Corporation")
                .primaryAddress("123 Main St, New York, NY")
                .sector("Technology")
                .totalEmployees(500)
                .revenue(50000000.0)
                .build();

        // Apply RAW strategy - returns provider data as-is
        // Note: In real usage, DataEnricher skips mapToTarget() for RAW strategy
        FinancialDataResponse rawResult = EnrichmentStrategyApplier.apply(
                EnrichmentStrategy.RAW,
                sourceDto,
                providerData,  // Raw provider data
                FinancialDataResponse.class
        );

        // VERIFY: Raw provider data is returned unchanged
        assertThat(rawResult).isSameAs(providerData);
        assertThat(rawResult.getId()).isEqualTo("12345");
        assertThat(rawResult.getBusinessName()).isEqualTo("Acme Corporation");
        assertThat(rawResult.getPrimaryAddress()).isEqualTo("123 Main St, New York, NY");
        assertThat(rawResult.getSector()).isEqualTo("Technology");
        assertThat(rawResult.getTotalEmployees()).isEqualTo(500);
        assertThat(rawResult.getRevenue()).isEqualTo(50000000.0);
    }

    /**
     * Demonstrates how getSupportedEnrichmentTypes() is used.
     *
     * <p><b>Primary Use:</b> The global discovery endpoint (GET /api/v1/enrichment/providers)
     * uses this to list all available providers and their supported enrichment types.</p>
     *
     * <p><b>Secondary Use:</b> The DataEnricherRegistry can use this for programmatic
     * lookup (optional - most apps use dedicated controllers instead).</p>
     *
     * <p><b>Architecture:</b> In a typical deployment, you have one microservice per provider
     * (e.g., core-data-provider-a-enricher) with multiple regional implementations:</p>
     * <pre>
     * Microservice: core-data-provider-a-enricher
     * ├── ProviderASpainCreditEnricher (supports: credit-report, credit-score)
     * ├── ProviderAUSACreditEnricher (supports: credit-report, business-credit)
     * └── ProviderASpainCompanyEnricher (supports: company-profile)
     *
     * Discovery endpoint:
     * GET /api/v1/enrichment/providers
     * → ["Provider A Spain", "Provider A USA"]
     *
     * GET /api/v1/enrichment/providers?enrichmentType=credit-report
     * → ["Provider A Spain", "Provider A USA"]
     * </pre>
     */
    @Test
    void demonstrateEnrichmentTypeUsage() {
        // Each enricher supports exactly ONE type (One Enricher = One Type principle)
        String enricherType = "company-profile";

        // USE CASE 1: Discovery endpoint filters by enrichment type
        // When client calls GET /api/v1/enrichment/providers?type=company-profile
        // The discovery controller filters enrichers that support "company-profile"
        String requestedType = "company-profile";
        boolean canHandle = enricherType.equals(requestedType);
        assertThat(canHandle).isTrue();

        // USE CASE 2: Smart routing endpoint automatically selects best enricher
        // POST /api/v1/enrichment/smart with type="company-profile" + tenantId
        // The registry finds the highest-priority enricher for that type + tenant

        // If the request was for "credit-report", this enricher wouldn't support it
        String differentType = "credit-report";
        boolean canHandleDifferent = enricherType.equals(differentType);
        assertThat(canHandleDifferent).isFalse();
    }
}

