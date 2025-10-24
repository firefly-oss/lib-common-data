# Data Enrichment

This document provides a comprehensive guide to implementing and using data enrichment providers in the `lib-common-data` library.

## Table of Contents

- [How It Works](#how-it-works)
- [Overview](#overview)
- [Architecture](#architecture)
- [Core Concepts](#core-concepts)
- [Quick Start](#quick-start)
- [Tutorial: Building a Professional Data Enricher](#tutorial-building-a-professional-data-enricher)
- [Implementation Guide](#implementation-guide)
- [Provider-Specific Operations](#provider-specific-operations)
- [Enricher Utilities](#enricher-utilities)
- [Configuration](#configuration)
- [Examples](#examples)
- [Best Practices](#best-practices)

---

## How It Works

### The Complete Enrichment Flow

Data enrichment combines your partial data with complete data from third-party providers. Here's the complete flow:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. CLIENT DISCOVERS AVAILABLE PROVIDERS (Optional)                      │
│                                                                         │
│    GET /api/v1/enrichment/providers?enrichmentType=company-profile      │
│                                                                         │
│    Response: [                                                          │
│      {                                                                  │
│        providerName: "Provider A Spain",                                │
│        supportedTypes: ["company-profile", "credit-report"],            │
│        description: "Provider A Spain data enrichment services",        │
│        endpoints: [                                                     │
│          "/api/v1/enrichment/provider-a-spain-company/enrich",          │
│          "/api/v1/enrichment/provider-a-spain-credit/enrich"            │
│        ],                                                               │
│        operations: [                                                    │
│          {                                                              │
│            operationId: "search-company",                               │
│            path: "/api/v1/enrichment/provider-a-spain/search-company",  │
│            method: "GET",                                               │
│            description: "Search for company to get internal ID"         │
│          }                                                              │
│        ]                                                                │
│      }                                                                  │
│    ]                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. CLIENT SENDS HTTP REQUEST TO SPECIFIC PROVIDER ENDPOINT              │
│                                                                         │
│    POST /api/v1/enrichment/provider-a-spain-company/enrich              │
│         ─────────────────────────────────────────                       │
│         Provider + Region + Type in URL path                            │
│                                                                         │
│    EnrichmentRequest {                                                  │
│      enrichmentType: "company-profile"  ← For logging/metadata          │
│      sourceDto: {                       ← Your partial data             │
│        companyId: "12345",                                              │
│        name: "Acme Corp"                                                │
│      },                                                                 │
│      parameters: {                      ← Provider-specific params      │
│        companyId: "12345"                                               │
│      },                                                                 │
│      strategy: ENHANCE                  ← How to merge data             │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. CONTROLLER RECEIVES REQUEST                                          │
│                                                                         │
│    ProviderASpainCompanyController                                      │
│      → Already knows which enricher to use (injected in constructor)    │
│      → No routing logic needed - one controller per enricher            │
│      → Calls: enricher.enrich(request)                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. ENRICHER FETCHES PROVIDER DATA                                       │
│                                                                         │
│    ProviderASpainCompanyEnricher.fetchProviderData(request)             │
│      → Calls Provider A Spain API                                       │
│      → Returns: ProviderACompanyResponse {                              │
│           id: "12345",                                                  │
│           businessName: "Acme Corporation",                             │
│           primaryAddress: "123 Main St",                                │
│           sector: "Technology",                                         │
│           totalEmployees: 500,                                          │
│           revenue: 50000000.0                                           │
│         }                                                               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. ENRICHER MAPS TO TARGET DTO                                          │
│                                                                         │
│    mapToTarget(providerData)                                            │
│      → Converts Provider A format to your DTO format                    │
│      → Returns: CompanyProfileDTO {                                     │
│           companyId: "12345",                                           │
│           name: "Acme Corporation",                                     │
│           registeredAddress: "123 Main St",                             │
│           industry: "Technology",                                       │
│           employeeCount: 500,                                           │
│           annualRevenue: 50000000.0                                     │
│         }                                                               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 6. STRATEGY IS APPLIED AUTOMATICALLY                                    │
│                                                                         │
│    EnrichmentStrategyApplier.apply(ENHANCE, sourceDto, mappedData)      │
│                                                                         │
│    SOURCE (what you had):        MAPPED (from provider):                │
│    {                             {                                      │
│      companyId: "12345",           companyId: "12345",                  │
│      name: "Acme Corp"             name: "Acme Corporation",            │
│    }                               registeredAddress: "123 Main St",    │
│                                    industry: "Technology",              │
│                                    employeeCount: 500,                  │
│                                    annualRevenue: 50000000.0            │
│                                  }                                      │
│                                                                         │
│    ENHANCE Strategy:                                                    │
│    - Keeps source.name ("Acme Corp")  ← Preserves existing data         │
│    - Adds all null fields from mapped data                              │
│                                                                         │
│    RESULT (enrichedDto):                                                │
│    {                                                                    │
│      companyId: "12345",                                                │
│      name: "Acme Corp",              ← From source (preserved)          │
│      registeredAddress: "123 Main St", ← From provider (added)          │
│      industry: "Technology",           ← From provider (added)          │
│      employeeCount: 500,               ← From provider (added)          │
│      annualRevenue: 50000000.0         ← From provider (added)          │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 7. RESPONSE IS BUILT WITH METADATA                                      │
│                                                                         │
│    EnrichmentResponse {                                                 │
│      success: true,                                                     │
│      data: enrichedDto,                                                 │
│      providerName: "Provider A Spain",                                  │
│      enrichmentType: "company-profile",                                 │
│      strategy: "ENHANCE",                                               │
│      fieldsEnriched: 4,              ← Auto-counted                     │
│      message: "company-profile enrichment completed successfully"       │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Concepts Explained

| Concept | Purpose | Example |
|---------|---------|---------|
| **Microservice** | One microservice per provider (e.g., `core-data-provider-a-enricher`) | Contains all Provider A enrichers for different regions/products |
| **Enricher** | One enricher per provider-region-product combination | `ProviderASpainCompanyEnricher`, `ProviderAUSACreditEnricher` |
| **Controller** | One controller per enricher with dedicated endpoint | `ProviderASpainCompanyController` → `/api/v1/enrichment/provider-a-spain-company/enrich` |
| **enrichmentType** | Identifies the type of enrichment (for logging/metadata and discovery filtering) | `"company-profile"`, `"credit-report"` |
| **sourceDto** | Your partial/incomplete data | `{ companyId: "12345", name: "Acme Corp" }` |
| **parameters** | Provider-specific parameters for the API call | `{ companyId: "12345", includeFinancials: true }` |
| **providerData** | Raw response from third-party API | `ProviderACompanyResponse` from Provider A API |
| **mappedData** | Provider data converted to your DTO format | `CompanyProfileDTO` with provider data |
| **strategy** | How to merge sourceDto + mappedData | `ENHANCE`, `MERGE`, `REPLACE`, `RAW` |
| **enrichedDto** | Final result after applying strategy | `CompanyProfileDTO` with merged data |
| **getSupportedEnrichmentTypes()** | Declares which types this enricher handles (used by discovery endpoint for filtering) | `["company-profile", "company-financials"]` |
| **getEnrichmentEndpoint()** | Returns the REST API endpoint for this enricher (automatically registered by controller) | `"/api/v1/enrichment/provider-a-spain-company/enrich"` |

**Important Architecture Points:**

1. **Routing by URL Path**: Each enricher has its own dedicated REST endpoint (e.g., `/api/v1/enrichment/provider-a-spain-credit/enrich`). The URL path determines which enricher is used - no routing logic needed.

2. **enrichmentType Field**: Used for logging, metadata, and filtering in the discovery endpoint - NOT for routing.

3. **Provider Discovery**: To discover available providers in a microservice, use the global discovery endpoint:
   ```bash
   GET /api/v1/enrichment/providers                           # List all providers in this microservice
   GET /api/v1/enrichment/providers?enrichmentType=credit-report  # Filter by type
   ```

   Response includes provider names, supported types, descriptions, and **REST endpoints**:
   ```json
   [
     {
       "providerName": "Provider A Spain",
       "supportedTypes": ["company-profile", "credit-report"],
       "description": "Provider A Spain data enrichment services",
       "endpoints": [
         "/api/v1/enrichment/provider-a-spain-company/enrich",
         "/api/v1/enrichment/provider-a-spain-credit/enrich"
       ]
     }
   ]
   ```

### Enrichment Strategies

Each strategy determines how the framework merges your source data with provider data:

| Strategy | Behavior | Implementation | Use Case |
|----------|----------|----------------|----------|
| **ENHANCE** | Fills only null/empty fields from provider. Preserves all existing source data. | 1. Calls `mapToTarget(providerData)`<br>2. Merges keeping source values where they exist | You have some data and want to fill in the gaps without overwriting existing values |
| **MERGE** | Combines source + provider data. Provider wins conflicts. | 1. Calls `mapToTarget(providerData)`<br>2. Merges with provider values taking precedence | You want the most up-to-date data from provider, overwriting stale source data |
| **REPLACE** | Uses only provider data. Ignores source completely. | 1. Calls `mapToTarget(providerData)`<br>2. Returns mapped data, ignores source | You want completely fresh data, don't trust source at all |
| **RAW** | Returns provider data as-is without mapping | 1. **Skips** `mapToTarget()`<br>2. Returns raw provider response directly | You need the raw provider response format for debugging or custom processing |

**Important:** The RAW strategy is special - it's the only strategy that skips the `mapToTarget()` step and returns the provider's response format directly.

---

## Overview

The Data Enrichment abstraction provides a standardized way to integrate third-party data enrichment providers into your microservices. It supports multiple provider types and enrichment strategies while providing automatic observability, resiliency, and event publishing.

### Key Features

- **Multiple Provider Support**: Financial data providers, credit bureaus, business intelligence, and custom providers
- **Flexible Enrichment Strategies**: ENHANCE, MERGE, REPLACE, and RAW
- **ServiceClient Integration**: Use REST, SOAP, or gRPC clients from lib-common-client
- **Automatic Observability**: Built-in tracing, metrics, and event publishing
- **Resiliency Patterns**: Circuit breaker, retry, rate limiting, and bulkhead
- **Tenant-Aware**: Multi-tenant support for provider routing
- **Registry Pattern**: Auto-discovery and lookup of enrichers

### Use Cases

- Enriching company profiles with financial and corporate data
- Obtaining credit reports for individuals or companies
- Validating and standardizing addresses
- Enriching director/officer information
- Verifying business identities

---

## Architecture

The Data Enrichment abstraction follows the same architectural patterns as DataJobs:

```
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                      │
│  ┌────────────────────────────────────────────┐         │
│  │  Microservice Business Logic               │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│               ▼                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  DataEnricher Interface                    │         │
│  │  - enrich(request)                         │         │
│  │  - getProviderName()                       │         │
│  │  - getSupportedEnrichmentTypes()           │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│               ▼                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  TypedDataEnricher<TSource,TProvider,      │         │
│  │                    TTarget>                │         │
│  │  - Observability (tracing, metrics)        │         │
│  │  - Resiliency (circuit breaker, retry)     │         │
│  │  - Event Publishing                        │         │
│  │  - Automatic Strategy Application          │         │
│  │  - Automatic Response Building             │         │
│  └────────────┬───────────────────────────────┘         │
│               │                                         │
│               ▼                                         │
│  ┌────────────────────────────────────────────┐         │
│  │  Your Implementation                       │         │
│  │  - fetchProviderData()                     │         │
│  │  - mapToTarget()                           │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│         Third-Party Providers                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │Financial │  │  Credit  │  │ Custom   │               │
│  │   Data   │  │  Bureau  │  │ Provider │               │
│  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────┘
```

### Microservice Architecture

In a typical deployment, you have **one microservice per provider**, with multiple enricher implementations for different regions and products:

```
┌────────────────────────────────────────────────────────────────────────────┐
│  Microservice: core-data-provider-a-enricher                               │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ProviderASpainCreditReportEnricher                                 │   │
│  │  - providerName: "Provider A Spain"                                 │   │
│  │  - supportedTypes: ["credit-report", "credit-score"]                │   │
│  │  - endpoint: POST /api/v1/enrichment/provider-a-spain-credit/enrich │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ProviderAUSACreditReportEnricher                                   │   │
│  │  - providerName: "Provider A USA"                                   │   │
│  │  - supportedTypes: ["credit-report", "business-credit"]             │   │
│  │  - endpoint: POST /api/v1/enrichment/provider-a-usa-credit/enrich   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ProviderASpainCompanyProfileEnricher                               │   │
│  │  - providerName: "Provider A Spain"                                 │   │
│  │  - supportedTypes: ["company-profile"]                              │   │
│  │  - endpoint: POST /api/v1/enrichment/provider-a-spain-company/enrich│   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  Discovery Endpoint:                                                       │
│  GET /api/v1/enrichment/providers                                          │
│  → [                                                                       │
│      {                                                                     │
│        "providerName": "Provider A Spain",                                 │
│        "supportedTypes": ["credit-report", "company-profile"],             │
│        "endpoints": [                                                      │
│          "/api/v1/enrichment/provider-a-spain-credit/enrich",              │
│          "/api/v1/enrichment/provider-a-spain-company/enrich"              │
│        ]                                                                   │
│      },                                                                    │
│      {                                                                     │
│        "providerName": "Provider A USA",                                   │
│        "supportedTypes": ["credit-report", "business-credit"],             │
│        "endpoints": [                                                      │
│          "/api/v1/enrichment/provider-a-usa-credit/enrich"                 │
│        ]                                                                   │
│      }                                                                     │
│    ]                                                                       │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│  Microservice: core-data-provider-b-enricher                               │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ProviderBSpainCreditReportEnricher                                 │   │
│  │  - providerName: "Provider B Spain"                                 │   │
│  │  - supportedTypes: ["credit-report"]                                │   │
│  │  - endpoint: POST /api/v1/enrichment/provider-b-spain-credit/enrich │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ProviderBUSACreditReportEnricher                                   │   │
│  │  - providerName: "Provider B USA"                                   │   │
│  │  - supportedTypes: ["credit-report"]                                │   │
│  │  - endpoint: POST /api/v1/enrichment/provider-b-usa-credit/enrich   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│  Discovery Endpoint:                                                       │
│  GET /api/v1/enrichment/providers                                          │
│  → [                                                                       │
│      {                                                                     │
│        "providerName": "Provider B Spain",                                 │
│        "supportedTypes": ["credit-report"],                                │
│        "endpoints": [                                                      │
│          "/api/v1/enrichment/provider-b-spain-credit/enrich"               │
│        ]                                                                   │
│      },                                                                    │
│      {                                                                     │
│        "providerName": "Provider B USA",                                   │
│        "supportedTypes": ["credit-report"],                                │
│        "endpoints": [                                                      │
│          "/api/v1/enrichment/provider-b-usa-credit/enrich"                 │
│        ]                                                                   │
│      }                                                                     │
│    ]                                                                       │
└────────────────────────────────────────────────────────────────────────────┘
```

**Key Points:**
- Each microservice is dedicated to a specific provider (e.g., Provider A, Provider B)
- Within each microservice, you have multiple enricher implementations for different regions/products
- Each enricher has its own dedicated REST endpoint
- The discovery endpoint (`GET /api/v1/enrichment/providers`) lists all enrichers available in that microservice
- `getSupportedEnrichmentTypes()` is used by the discovery endpoint to filter providers by enrichment type

### Integration with lib-common-client

Data enrichers can use ServiceClient from lib-common-client:

```
┌─────────────────────────────────────────────────────────┐
│              lib-common-data                            │
│  ┌────────────────────────────────────────────┐         │
│  │  DataEnricher Implementation               │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────────────────┐
│              lib-common-client                          │
│  ┌────────────────────────────────────────────┐         │
│  │  ServiceClient (REST/SOAP/gRPC)            │         │
│  │  - Circuit breaker                         │         │
│  │  - Retry logic                             │         │
│  │  - Health checks                           │         │
│  └────────────┬───────────────────────────────┘         │
└───────────────┼─────────────────────────────────────────┘
                │
                ▼
         Provider API (REST/SOAP/gRPC)
```

---

## Core Concepts

### Enrichment Strategies

The library supports four enrichment strategies:

#### 1. ENHANCE
Fill in only null or missing fields in the existing DTO with provider data.

```java
// Before: { name: "Acme Corp", address: null }
// After:  { name: "Acme Corp", address: "123 Main St" }
```

**Use Cases:**
- Completing partial data from user input
- Adding optional fields without overwriting existing values
- Progressive data enrichment from multiple sources

#### 2. MERGE
Combine existing DTO with provider data, with provider data taking precedence.

```java
// Before: { name: "Acme Corp", address: "Old Address" }
// After:  { name: "Acme Corp", address: "New Address from Provider" }
```

**Use Cases:**
- Updating stale data with fresh provider information
- Synchronizing local data with authoritative source
- Refreshing cached data

#### 3. REPLACE
Replace the entire DTO with provider response data.

```java
// Before: { name: "Acme Corp", address: "Old Address" }
// After:  { name: "Acme Corporation Inc", address: "123 Main St", revenue: 1000000, ... }
```

**Use Cases:**
- Fetching complete data from provider using only an identifier
- Refreshing all data from authoritative source
- Initial data population

#### 4. RAW
Return the raw provider response without transformation.

```java
// Returns: Map<String, Object> or provider-specific object
```

**Use Cases:**
- Custom processing of provider data
- Debugging provider responses
- Storing raw provider data for audit purposes

### Models

#### EnrichmentRequest
Contains all information needed for enrichment:
- `enrichmentType`: Type of enrichment (e.g., "company-profile")
- `strategy`: Enrichment strategy to apply
- `sourceDto`: Existing DTO to enrich (optional for REPLACE/RAW)
- `parameters`: Provider-specific parameters
- `tenantId`: Tenant identifier for multi-tenant routing
- `requestId`: For tracing and correlation

#### EnrichmentResponse
Contains the enrichment result:
- `success`: Whether enrichment succeeded
- `enrichedData`: The enriched data result
- `rawProviderResponse`: Raw provider response (optional)
- `providerName`: Name of the provider
- `confidenceScore`: Provider's confidence in the data
- `fieldsEnriched`: Number of fields enriched
- `cost`: Cost of the operation

---

## Quick Start

### 1. Add Dependency

The data enrichment components are included in `lib-common-data`:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-data</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Enable Data Enrichment

Add configuration to `application.yml`:

```yaml
firefly:
  data:
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30
```

### 3. Implement a Data Enricher

You only need to implement 2 methods: `fetchProviderData()` and `mapToTarget()`. The framework handles everything else automatically.

```java
@Service
public class FinancialDataEnricher
        extends TypedDataEnricher<CompanyProfileDTO, FinancialDataResponse, CompanyProfileDTO> {

    private final RestClient financialDataClient;

    public FinancialDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);

        // Create REST client using lib-common-client
        this.financialDataClient = ServiceClient.rest("financial-data-provider")
            .baseUrl("https://api.financial-data-provider.example")
            .defaultHeader("Authorization", "Bearer ${provider.api.key}")
            .timeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * STEP 1: Fetch data from the third-party provider.
     *
     * This method is called by the framework to get data from the provider's API.
     * You receive the enrichment request with parameters and return the raw provider response.
     */
    @Override
    protected Mono<FinancialDataResponse> fetchProviderData(EnrichmentRequest request) {
        // Validate and extract required parameters
        String companyId = request.requireParam("companyId");

        // Call the provider's API
        return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
            .withPathParam("id", companyId)
            .execute();
    }

    /**
     * STEP 2: Map provider data to your target DTO format.
     *
     * This method converts the provider's response format to your application's DTO format.
     * The framework will then automatically merge this with the source DTO using the
     * selected strategy (ENHANCE/MERGE/REPLACE/RAW).
     */
    @Override
    protected CompanyProfileDTO mapToTarget(FinancialDataResponse providerData) {
        return CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .industry(providerData.getSector())
                .annualRevenue(providerData.getRevenue())
                .employeeCount(providerData.getTotalEmployees())
                .build();
    }

    /**
     * Provider name for logging and response metadata.
     */
    @Override
    public String getProviderName() {
        return "Financial Data Provider";
    }

    /**
     * Declares which enrichment types this enricher supports.
     *
     * The DataEnricherRegistry uses this to route requests:
     * - Request with enrichmentType="company-profile" → routed to this enricher
     * - Request with enrichmentType="credit-report" → routed to different enricher
     */
    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"company-profile", "company-financials"};
    }

    @Override
    public String getEnricherDescription() {
        return "Enriches company data with financial and corporate information";
    }
}
```

**What happens automatically:**

1. ✅ **Observability**: Tracing spans, metrics, and logs are created automatically
2. ✅ **Resiliency**: Circuit breaker, retry, and rate limiting are applied automatically
3. ✅ **Strategy Application**: Your `mappedData` is merged with `sourceDto` using `EnrichmentStrategyApplier`
4. ✅ **Response Building**: `EnrichmentResponse` is built with metadata and field counting
5. ✅ **Event Publishing**: Enrichment events are published automatically
6. ✅ **Error Handling**: Errors are caught and wrapped in proper response format

### 4. Implement the REST Controller

**Option A: Extend AbstractDataEnricherController (Recommended)**

This approach provides automatic comprehensive logging for all HTTP requests/responses:

```java
@RestController
@RequestMapping("/api/v1/enrichment/provider-a-spain-company")
@Tag(name = "Data Enrichment - Provider A Spain Company",
     description = "Provider A Spain company profile enrichment")
public class ProviderASpainCompanyController extends AbstractDataEnricherController {

    public ProviderASpainCompanyController(
            @Qualifier("providerASpainCompanyEnricher") DataEnricher enricher,
            DataEnricherRegistry registry) {
        super(enricher, registry);
    }

    // That's it! All endpoints are implemented with automatic logging:
    // POST   /api/v1/enrichment/provider-a-spain-company/enrich
    // GET    /api/v1/enrichment/provider-a-spain-company/health

    // The endpoint is automatically registered with the enricher via @PostConstruct
}
```

**Key Points:**
- **URL Path**: Must include provider + region + type (e.g., `provider-a-spain-company`)
- **One Controller Per Enricher**: Each enricher gets its own dedicated controller
- **Automatic Endpoint Registration**: The controller automatically registers its endpoint with the enricher
- **No Routing Logic**: The enricher is injected in the constructor - no routing needed

**Option B: Use the Enricher Directly in Your Service**

If you don't need REST endpoints, you can use the enricher directly:

```java
@Service
public class CompanyService {

    private final DataEnricherRegistry enricherRegistry;

    /**
     * Enriches company data using the registry to route to the correct enricher.
     *
     * This demonstrates the complete flow:
     * 1. Create request with enrichmentType, sourceDto, parameters, and strategy
     * 2. Registry routes to correct enricher based on enrichmentType
     * 3. Enricher fetches from provider, maps data, applies strategy
     * 4. Returns enriched result
     */
    public Mono<CompanyProfileDTO> enrichCompanyData(
            String companyId,
            CompanyProfileDTO partialData) {

        // Create enrichment request
        EnrichmentRequest request = EnrichmentRequest.builder()
            .enrichmentType("company-profile")  // Routes to FinancialDataEnricher
            .sourceDto(partialData)             // Your partial data
            .strategy(EnrichmentStrategy.ENHANCE)  // Fill only null fields
            .parameters(Map.of("companyId", companyId))
            .requestId(UUID.randomUUID().toString())
            .build();

        // Registry finds the enricher that supports "company-profile"
        DataEnricher enricher = enricherRegistry
            .getEnricherForType("company-profile")
            .orElseThrow(() -> new EnricherNotFoundException("company-profile"));

        // Enrich and extract the result
        return enricher.enrich(request)
            .map(response -> (CompanyProfileDTO) response.getEnrichedData());
    }
}
```

---

## Tutorial: Building a Professional Data Enricher

This comprehensive tutorial will guide you through building a **production-ready data enricher** for Equifax Spain credit reports. You'll learn every aspect of creating a professional enricher with all best practices.

### What We'll Build

We're going to build a complete Equifax Spain credit report enricher that:

- ✅ Fetches credit reports from Equifax Spain API
- ✅ Handles provider-specific ID lookups (CIF → Equifax ID)
- ✅ Implements provider-specific operations (search, validate, quick-lookup)
- ✅ Includes full observability (tracing, metrics, logging)
- ✅ Has automatic resiliency (circuit breaker, retry, rate limiting)
- ✅ Exposes REST endpoints with comprehensive documentation
- ✅ Publishes enrichment events
- ✅ Includes complete error handling
- ✅ Has comprehensive tests

### Prerequisites

Before starting, ensure you have:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-data</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-client</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```yaml
# application.yml
firefly:
  data:
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30
```

---

### Step 1: Define Your DTOs

First, define the data structures you'll work with.

#### 1.1 Target DTO (Your Application's Format)

```java
package com.firefly.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Credit report DTO in your application's format.
 * This is what your application works with.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditReportDTO {

    // Company identification
    private String companyId;
    private String companyName;
    private String cif;  // Spanish tax ID

    // Credit information
    private Integer creditScore;
    private String creditRating;  // AAA, AA, A, BBB, etc.
    private String riskLevel;     // LOW, MEDIUM, HIGH

    // Financial data
    private Double annualRevenue;
    private Double totalDebt;
    private Double liquidityRatio;

    // Payment behavior
    private Integer daysPayableOutstanding;
    private Double paymentDelayPercentage;
    private Integer numberOfDefaults;

    // Metadata
    private String reportDate;
    private String dataSource;
    private String equifaxId;  // Provider's internal ID
}
```

#### 1.2 Provider Response DTO (Equifax's Format)

```java
package com.firefly.enricher.equifax.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Equifax Spain API response format.
 * This matches exactly what Equifax returns.
 */
@Data
public class EquifaxCreditReportResponse {

    @JsonProperty("empresa_id")
    private String empresaId;  // Equifax's internal ID

    @JsonProperty("razon_social")
    private String razonSocial;  // Company name

    @JsonProperty("cif")
    private String cif;

    @JsonProperty("puntuacion_credito")
    private Integer puntuacionCredito;  // Credit score

    @JsonProperty("calificacion")
    private String calificacion;  // Rating

    @JsonProperty("nivel_riesgo")
    private String nivelRiesgo;  // Risk level

    @JsonProperty("facturacion_anual")
    private Double facturacionAnual;

    @JsonProperty("deuda_total")
    private Double deudaTotal;

    @JsonProperty("ratio_liquidez")
    private Double ratioLiquidez;

    @JsonProperty("dias_pago_medio")
    private Integer diasPagoMedio;

    @JsonProperty("porcentaje_retrasos")
    private Double porcentajeRetrasos;

    @JsonProperty("numero_impagos")
    private Integer numeroImpagos;

    @JsonProperty("fecha_informe")
    private String fechaInforme;
}
```

#### 1.3 Search Response DTO (For ID Lookup)

```java
package com.firefly.enricher.equifax.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response from Equifax company search endpoint.
 */
@Data
public class EquifaxSearchResponse {

    @JsonProperty("empresa_id")
    private String empresaId;

    @JsonProperty("razon_social")
    private String razonSocial;

    @JsonProperty("cif")
    private String cif;

    @JsonProperty("confianza")
    private Double confianza;  // Match confidence (0.0 - 1.0)

    @JsonProperty("activa")
    private Boolean activa;  // Is company active?
}
```

---

### Step 2: Create the Enricher Service

Now create the enricher that implements the business logic.

```java
package com.firefly.enricher.equifax;

import com.firefly.common.client.ServiceClient;
import com.firefly.common.client.rest.RestClient;
import com.firefly.common.data.controller.ProviderOperation;
import com.firefly.common.data.controller.ProviderOperationCatalog;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.TypedDataEnricher;
import com.firefly.customer.dto.CreditReportDTO;
import com.firefly.enricher.equifax.dto.EquifaxCreditReportResponse;
import com.firefly.enricher.equifax.dto.EquifaxSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Professional Equifax Spain credit report enricher.
 *
 * <p>This enricher demonstrates all best practices:</p>
 * <ul>
 *   <li>Provider-specific operations for ID lookup</li>
 *   <li>Comprehensive error handling</li>
 *   <li>Automatic observability and resiliency</li>
 *   <li>Clean separation of concerns</li>
 *   <li>Proper validation</li>
 * </ul>
 */
@Slf4j
@Service
public class EquifaxSpainCreditEnricher
        extends TypedDataEnricher<CreditReportDTO, EquifaxCreditReportResponse, CreditReportDTO>
        implements ProviderOperationCatalog {

    private final RestClient equifaxClient;

    @Value("${equifax.spain.api.key}")
    private String apiKey;

    /**
     * Constructor with dependency injection.
     *
     * <p>All observability and resiliency services are injected automatically
     * by Spring and passed to the parent class.</p>
     */
    public EquifaxSpainCreditEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            @Value("${equifax.spain.base-url}") String baseUrl) {

        super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);

        // Create REST client using lib-common-client
        this.equifaxClient = ServiceClient.rest("equifax-spain")
            .baseUrl(baseUrl)
            .defaultHeader("X-API-Key", apiKey)
            .defaultHeader("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .build();

        log.info("Initialized Equifax Spain Credit Enricher with base URL: {}", baseUrl);
    }

    // ========== CORE ENRICHMENT METHODS ==========

    /**
     * Step 1: Fetch credit report from Equifax API.
     *
     * <p>This method is called by the framework. You just need to:</p>
     * <ol>
     *   <li>Extract parameters from the request</li>
     *   <li>Call the provider's API</li>
     *   <li>Return the raw response</li>
     * </ol>
     *
     * <p>The framework handles everything else automatically:</p>
     * <ul>
     *   <li>✅ Tracing span creation</li>
     *   <li>✅ Metrics recording</li>
     *   <li>✅ Circuit breaker</li>
     *   <li>✅ Retry logic</li>
     *   <li>✅ Error handling</li>
     * </ul>
     */
    @Override
    protected Mono<EquifaxCreditReportResponse> fetchProviderData(EnrichmentRequest request) {
        // Extract required parameter
        String equifaxId = request.requireParam("equifaxId");

        log.debug("Fetching credit report from Equifax for ID: {}", equifaxId);

        // Call Equifax API
        return equifaxClient.get("/v2/informes-credito/{id}", EquifaxCreditReportResponse.class)
            .withPathParam("id", equifaxId)
            .execute()
            .doOnSuccess(response ->
                log.debug("Successfully fetched credit report for Equifax ID: {}", equifaxId))
            .doOnError(error ->
                log.error("Failed to fetch credit report from Equifax for ID: {}", equifaxId, error));
    }

    /**
     * Step 2: Map Equifax response to your application's DTO format.
     *
     * <p>This method converts the provider's format to your format.
     * The framework will then automatically apply the enrichment strategy
     * (ENHANCE/MERGE/REPLACE/RAW) to combine this with the source DTO.</p>
     */
    @Override
    protected CreditReportDTO mapToTarget(EquifaxCreditReportResponse providerData) {
        return CreditReportDTO.builder()
            // Company identification
            .equifaxId(providerData.getEmpresaId())
            .companyName(providerData.getRazonSocial())
            .cif(providerData.getCif())

            // Credit information
            .creditScore(providerData.getPuntuacionCredito())
            .creditRating(providerData.getCalificacion())
            .riskLevel(mapRiskLevel(providerData.getNivelRiesgo()))

            // Financial data
            .annualRevenue(providerData.getFacturacionAnual())
            .totalDebt(providerData.getDeudaTotal())
            .liquidityRatio(providerData.getRatioLiquidez())

            // Payment behavior
            .daysPayableOutstanding(providerData.getDiasPagoMedio())
            .paymentDelayPercentage(providerData.getPorcentajeRetrasos())
            .numberOfDefaults(providerData.getNumeroImpagos())

            // Metadata
            .reportDate(providerData.getFechaInforme())
            .dataSource("Equifax Spain")
            .build();
    }

    // ========== PROVIDER-SPECIFIC OPERATIONS ==========

    /**
     * Declares the catalog of provider-specific operations.
     *
     * <p>These operations are automatically exposed as REST endpoints
     * and included in the discovery response.</p>
     */
    @Override
    public List<ProviderOperation> getOperationCatalog() {
        return List.of(
            // Operation 1: Search company by name/CIF to get Equifax ID
            ProviderOperation.builder()
                .operationId("search-company")
                .path("/search-company")
                .method(RequestMethod.GET)
                .description("Search for a company by name or CIF to obtain Equifax internal ID. " +
                           "This is typically the first step before enrichment.")
                .requestExample(Map.of(
                    "companyName", "ACME CORPORATION SL",
                    "cif", "A12345678"
                ))
                .responseExample(Map.of(
                    "equifaxId", "ES-12345",
                    "companyName", "ACME CORPORATION SL",
                    "cif", "A12345678",
                    "confidence", 0.95,
                    "active", true
                ))
                .tags(new String[]{"lookup", "search", "prerequisite"})
                .requiresAuth(true)
                .build(),

            // Operation 2: Validate CIF
            ProviderOperation.builder()
                .operationId("validate-cif")
                .path("/validate-cif")
                .method(RequestMethod.GET)
                .description("Validate that a CIF (Spanish tax ID) exists in Equifax database")
                .requestExample(Map.of("cif", "A12345678"))
                .responseExample(Map.of(
                    "valid", true,
                    "equifaxId", "ES-12345",
                    "companyName", "ACME CORPORATION SL"
                ))
                .tags(new String[]{"validation"})
                .requiresAuth(true)
                .build(),

            // Operation 3: Get credit score only (quick lookup)
            ProviderOperation.builder()
                .operationId("get-credit-score")
                .path("/credit-score")
                .method(RequestMethod.POST)
                .description("Get just the credit score without full enrichment data. " +
                           "Faster and cheaper than full credit report.")
                .requestExample(Map.of("equifaxId", "ES-12345"))
                .responseExample(Map.of(
                    "creditScore", 750,
                    "creditRating", "AAA",
                    "riskLevel", "LOW",
                    "lastUpdated", "2025-10-24"
                ))
                .tags(new String[]{"credit", "quick-lookup"})
                .requiresAuth(true)
                .build()
        );
    }

    /**
     * Executes provider-specific operations.
     *
     * <p>This method is called when clients invoke the operation endpoints.
     * The framework handles routing, parameter extraction, and response formatting.</p>
     */
    @Override
    public Mono<Map<String, Object>> executeOperation(String operationId, Map<String, Object> parameters) {
        log.info("Executing Equifax operation: {} with parameters: {}", operationId, parameters);

        return switch (operationId) {
            case "search-company" -> searchCompany(parameters);
            case "validate-cif" -> validateCif(parameters);
            case "get-credit-score" -> getCreditScore(parameters);
            default -> Mono.error(new IllegalArgumentException(
                "Unknown operation: " + operationId + ". Available operations: search-company, validate-cif, get-credit-score"));
        };
    }

    // ========== OPERATION IMPLEMENTATIONS ==========

    /**
     * Search for a company in Equifax database.
     */
    private Mono<Map<String, Object>> searchCompany(Map<String, Object> params) {
        String companyName = (String) params.get("companyName");
        String cif = (String) params.get("cif");

        // Validate parameters
        if (companyName == null && cif == null) {
            return Mono.error(new IllegalArgumentException(
                "Either 'companyName' or 'cif' parameter is required"));
        }

        log.debug("Searching Equifax for company: name={}, cif={}", companyName, cif);

        // Build query parameters
        var queryBuilder = equifaxClient.get("/v2/empresas/buscar", EquifaxSearchResponse.class);
        if (companyName != null) {
            queryBuilder.withQueryParam("razon_social", companyName);
        }
        if (cif != null) {
            queryBuilder.withQueryParam("cif", cif);
        }

        return queryBuilder.execute()
            .map(result -> Map.of(
                "equifaxId", result.getEmpresaId(),
                "companyName", result.getRazonSocial(),
                "cif", result.getCif(),
                "confidence", result.getConfianza(),
                "active", result.getActiva()
            ))
            .doOnSuccess(result ->
                log.info("Found company in Equifax: equifaxId={}, confidence={}",
                    result.get("equifaxId"), result.get("confidence")))
            .doOnError(error ->
                log.error("Failed to search company in Equifax: name={}, cif={}",
                    companyName, cif, error));
    }

    /**
     * Validate a CIF in Equifax database.
     */
    private Mono<Map<String, Object>> validateCif(Map<String, Object> params) {
        String cif = (String) params.get("cif");

        if (cif == null || cif.isBlank()) {
            return Mono.error(new IllegalArgumentException("Parameter 'cif' is required"));
        }

        log.debug("Validating CIF in Equifax: {}", cif);

        return equifaxClient.get("/v2/empresas/validar-cif", EquifaxSearchResponse.class)
            .withQueryParam("cif", cif)
            .execute()
            .map(result -> Map.of(
                "valid", true,
                "equifaxId", result.getEmpresaId(),
                "companyName", result.getRazonSocial()
            ))
            .onErrorResume(error -> {
                log.warn("CIF validation failed: {}", cif);
                return Mono.just(Map.of("valid", false));
            });
    }

    /**
     * Get credit score only (quick lookup).
     */
    private Mono<Map<String, Object>> getCreditScore(Map<String, Object> params) {
        String equifaxId = (String) params.get("equifaxId");

        if (equifaxId == null || equifaxId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Parameter 'equifaxId' is required"));
        }

        log.debug("Fetching credit score from Equifax for ID: {}", equifaxId);

        return equifaxClient.get("/v2/puntuacion-credito/{id}", Map.class)
            .withPathParam("id", equifaxId)
            .execute()
            .map(result -> Map.of(
                "creditScore", result.get("puntuacion"),
                "creditRating", result.get("calificacion"),
                "riskLevel", mapRiskLevel((String) result.get("nivel_riesgo")),
                "lastUpdated", result.get("fecha_actualizacion"),
                "equifaxId", equifaxId
            ))
            .doOnSuccess(result ->
                log.info("Retrieved credit score: equifaxId={}, score={}",
                    equifaxId, result.get("creditScore")));
    }

    // ========== METADATA METHODS ==========

    @Override
    public String getProviderName() {
        return "Equifax Spain";
    }

    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"credit-report", "credit-score", "company-risk"};
    }

    @Override
    public String getEnricherDescription() {
        return "Equifax Spain credit report and risk assessment enrichment service. " +
               "Provides comprehensive credit information, payment behavior analysis, " +
               "and risk scoring for Spanish companies.";
    }

    // ========== HELPER METHODS ==========

    /**
     * Maps Equifax risk level codes to standardized values.
     */
    private String mapRiskLevel(String equifaxRiskLevel) {
        if (equifaxRiskLevel == null) {
            return "UNKNOWN";
        }

        return switch (equifaxRiskLevel.toUpperCase()) {
            case "BAJO", "MUY_BAJO" -> "LOW";
            case "MEDIO" -> "MEDIUM";
            case "ALTO", "MUY_ALTO" -> "HIGH";
            default -> "UNKNOWN";
        };
    }
}
```

---

### Step 3: Create the REST Controller

The controller exposes your enricher as REST endpoints.

```java
package com.firefly.enricher.equifax;

import com.firefly.common.data.controller.AbstractDataEnricherController;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.service.DataEnricherRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Equifax Spain credit enrichment.
 *
 * <p>This controller automatically exposes the following endpoints:</p>
 * <ul>
 *   <li>POST /api/v1/enrichment/equifax-spain-credit/enrich - Main enrichment endpoint</li>
 *   <li>GET  /api/v1/enrichment/equifax-spain-credit/health - Health check</li>
 *   <li>GET  /api/v1/enrichment/equifax-spain-credit/operations - List operations</li>
 *   <li>GET  /api/v1/enrichment/equifax-spain-credit/search-company - Search company</li>
 *   <li>GET  /api/v1/enrichment/equifax-spain-credit/validate-cif - Validate CIF</li>
 *   <li>POST /api/v1/enrichment/equifax-spain-credit/credit-score - Get credit score</li>
 * </ul>
 *
 * <p><b>URL Pattern:</b> {@code /api/v1/enrichment/{provider}-{region}-{type}}</p>
 * <ul>
 *   <li><b>provider:</b> equifax</li>
 *   <li><b>region:</b> spain</li>
 *   <li><b>type:</b> credit</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/enrichment/equifax-spain-credit")
@Tag(name = "Data Enrichment - Equifax Spain Credit",
     description = "Equifax Spain credit report and risk assessment enrichment")
public class EquifaxSpainCreditController extends AbstractDataEnricherController {

    /**
     * Constructor with dependency injection.
     *
     * <p>The enricher is injected by qualifier to ensure we get the correct one
     * when multiple enrichers are registered.</p>
     *
     * @param enricher the Equifax Spain credit enricher
     * @param registry the enricher registry (for discovery)
     */
    public EquifaxSpainCreditController(
            @Qualifier("equifaxSpainCreditEnricher") DataEnricher enricher,
            DataEnricherRegistry registry) {
        super(enricher, registry);
    }

    // That's it! All endpoints are implemented automatically:
    //
    // 1. POST /enrich - Main enrichment endpoint
    //    - Automatic request logging
    //    - Automatic response logging
    //    - Automatic error handling
    //    - Automatic metrics and tracing
    //
    // 2. GET /health - Health check endpoint
    //    - Returns provider name and supported types
    //
    // 3. GET /operations - List provider-specific operations
    //    - Returns the operation catalog
    //
    // 4. GET|POST /operation/{operationId} - Execute operation
    //    - Routes to executeOperation() in the enricher
    //
    // The @PostConstruct method in AbstractDataEnricherController
    // automatically registers this endpoint with the enricher.
}
```

**Key Points:**

- ✅ **URL Pattern**: `{provider}-{region}-{type}` (e.g., `equifax-spain-credit`)
- ✅ **One Controller Per Enricher**: Each enricher has its own dedicated controller
- ✅ **No Routing Logic**: The enricher is injected directly - no routing needed
- ✅ **Automatic Endpoint Registration**: The controller registers its path with the enricher
- ✅ **Comprehensive Logging**: All requests/responses are logged automatically
- ✅ **OpenAPI Documentation**: Swagger annotations for API documentation

---

### Step 4: Add Configuration

Create configuration properties for Equifax.

```yaml
# application.yml
equifax:
  spain:
    base-url: https://api.equifax.es
    api-key: ${EQUIFAX_SPAIN_API_KEY}  # From environment variable
    timeout-seconds: 30

firefly:
  data:
    enrichment:
      enabled: true
      publish-events: true
      default-timeout-seconds: 30

    # Resiliency configuration (optional - has sensible defaults)
    orchestration:
      resiliency:
        circuit-breaker-enabled: true
        circuit-breaker-failure-rate-threshold: 50.0
        circuit-breaker-wait-duration-in-open-state: 60s
        retry-enabled: true
        retry-max-attempts: 3
        retry-wait-duration: 5s
        rate-limiter-enabled: false
```

---

### Step 5: Write Comprehensive Tests

Create tests to ensure your enricher works correctly.

```java
package com.firefly.enricher.equifax;

import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.customer.dto.CreditReportDTO;
import com.firefly.enricher.equifax.dto.EquifaxCreditReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Equifax Spain Credit Enricher Tests")
class EquifaxSpainCreditEnricherTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private EquifaxSpainCreditEnricher enricher;

    @BeforeEach
    void setUp() {
        // Mock resiliency service to pass through operations
        when(resiliencyService.decorateSupplier(any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(1));

        enricher = new EquifaxSpainCreditEnricher(
            tracingService,
            metricsService,
            resiliencyService,
            eventPublisher,
            "https://api.equifax.es"
        );
    }

    @Test
    @DisplayName("Should successfully enrich credit report with ENHANCE strategy")
    void shouldEnrichCreditReportWithEnhanceStrategy() {
        // Given
        CreditReportDTO sourceDto = CreditReportDTO.builder()
            .companyId("12345")
            .companyName("ACME CORP")
            .cif("A12345678")
            .build();

        EnrichmentRequest request = EnrichmentRequest.builder()
            .enrichmentType("credit-report")
            .strategy(EnrichmentStrategy.ENHANCE)
            .sourceDto(sourceDto)
            .parameters(Map.of("equifaxId", "ES-12345"))
            .requestId("test-request-001")
            .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.isSuccess()).isTrue();
                assertThat(response.getProviderName()).isEqualTo("Equifax Spain");
                assertThat(response.getEnrichmentType()).isEqualTo("credit-report");

                CreditReportDTO enrichedData = (CreditReportDTO) response.getEnrichedData();
                assertThat(enrichedData.getCompanyId()).isEqualTo("12345");
                assertThat(enrichedData.getCompanyName()).isEqualTo("ACME CORP");
                assertThat(enrichedData.getCreditScore()).isNotNull();
                assertThat(enrichedData.getDataSource()).isEqualTo("Equifax Spain");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should return error when equifaxId parameter is missing")
    void shouldReturnErrorWhenEquifaxIdMissing() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
            .enrichmentType("credit-report")
            .strategy(EnrichmentStrategy.ENHANCE)
            .sourceDto(new CreditReportDTO())
            .parameters(Map.of())  // Missing equifaxId
            .build();

        // When
        Mono<EnrichmentResponse> result = enricher.enrich(request);

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getMessage()).contains("equifaxId");
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should execute search-company operation successfully")
    void shouldExecuteSearchCompanyOperation() {
        // Given
        Map<String, Object> params = Map.of(
            "companyName", "ACME CORPORATION",
            "cif", "A12345678"
        );

        // When
        Mono<Map<String, Object>> result = enricher.executeOperation("search-company", params);

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response).containsKeys("equifaxId", "companyName", "cif", "confidence");
                assertThat(response.get("equifaxId")).isNotNull();
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Should map Equifax response to target DTO correctly")
    void shouldMapEquifaxResponseToTargetDto() {
        // Given
        EquifaxCreditReportResponse providerData = new EquifaxCreditReportResponse();
        providerData.setEmpresaId("ES-12345");
        providerData.setRazonSocial("ACME CORPORATION SL");
        providerData.setCif("A12345678");
        providerData.setPuntuacionCredito(750);
        providerData.setCalificacion("AAA");
        providerData.setNivelRiesgo("BAJO");

        // When
        CreditReportDTO result = enricher.mapToTarget(providerData);

        // Then
        assertThat(result.getEquifaxId()).isEqualTo("ES-12345");
        assertThat(result.getCompanyName()).isEqualTo("ACME CORPORATION SL");
        assertThat(result.getCif()).isEqualTo("A12345678");
        assertThat(result.getCreditScore()).isEqualTo(750);
        assertThat(result.getCreditRating()).isEqualTo("AAA");
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
        assertThat(result.getDataSource()).isEqualTo("Equifax Spain");
    }

    @Test
    @DisplayName("Should have correct provider metadata")
    void shouldHaveCorrectProviderMetadata() {
        assertThat(enricher.getProviderName()).isEqualTo("Equifax Spain");
        assertThat(enricher.getSupportedEnrichmentTypes())
            .containsExactlyInAnyOrder("credit-report", "credit-score", "company-risk");
        assertThat(enricher.getEnricherDescription()).contains("Equifax Spain");
    }

    @Test
    @DisplayName("Should have provider-specific operations catalog")
    void shouldHaveProviderSpecificOperationsCatalog() {
        var operations = enricher.getOperationCatalog();

        assertThat(operations).hasSize(3);
        assertThat(operations).extracting("operationId")
            .containsExactlyInAnyOrder("search-company", "validate-cif", "get-credit-score");
    }
}
```

---

### Step 6: Test the Complete Flow

Now test your enricher end-to-end.

#### 6.1 Start Your Application

```bash
mvn spring-boot:run
```

#### 6.2 Discover Available Providers

```bash
curl -X GET "http://localhost:8080/api/v1/enrichment/providers?enrichmentType=credit-report"
```

Response:
```json
{
  "providers": [
    {
      "providerName": "Equifax Spain",
      "supportedTypes": ["credit-report", "credit-score", "company-risk"],
      "description": "Equifax Spain credit report and risk assessment enrichment service...",
      "endpoints": [
        "/api/v1/enrichment/equifax-spain-credit/enrich"
      ],
      "operations": [
        {
          "operationId": "search-company",
          "path": "/api/v1/enrichment/equifax-spain-credit/search-company",
          "method": "GET",
          "description": "Search for a company by name or CIF to obtain Equifax internal ID...",
          "tags": ["lookup", "search", "prerequisite"],
          "requiresAuth": true
        },
        {
          "operationId": "validate-cif",
          "path": "/api/v1/enrichment/equifax-spain-credit/validate-cif",
          "method": "GET",
          "description": "Validate that a CIF (Spanish tax ID) exists in Equifax database",
          "tags": ["validation"],
          "requiresAuth": true
        },
        {
          "operationId": "get-credit-score",
          "path": "/api/v1/enrichment/equifax-spain-credit/credit-score",
          "method": "POST",
          "description": "Get just the credit score without full enrichment data...",
          "tags": ["credit", "quick-lookup"],
          "requiresAuth": true
        }
      ]
    }
  ]
}
```

#### 6.3 Search for Company (Get Equifax ID)

```bash
curl -X GET "http://localhost:8080/api/v1/enrichment/equifax-spain-credit/search-company?companyName=ACME%20CORP&cif=A12345678"
```

Response:
```json
{
  "equifaxId": "ES-12345",
  "companyName": "ACME CORPORATION SL",
  "cif": "A12345678",
  "confidence": 0.95,
  "active": true
}
```

#### 6.4 Enrich Credit Report

```bash
curl -X POST "http://localhost:8080/api/v1/enrichment/equifax-spain-credit/enrich" \
  -H "Content-Type: application/json" \
  -d '{
    "enrichmentType": "credit-report",
    "strategy": "ENHANCE",
    "sourceDto": {
      "companyId": "12345",
      "companyName": "ACME CORP",
      "cif": "A12345678"
    },
    "parameters": {
      "equifaxId": "ES-12345"
    },
    "requestId": "req-001",
    "tenantId": "tenant-001"
  }'
```

Response:
```json
{
  "success": true,
  "enrichedData": {
    "companyId": "12345",
    "companyName": "ACME CORP",
    "cif": "A12345678",
    "creditScore": 750,
    "creditRating": "AAA",
    "riskLevel": "LOW",
    "annualRevenue": 5000000.0,
    "totalDebt": 500000.0,
    "liquidityRatio": 2.5,
    "daysPayableOutstanding": 45,
    "paymentDelayPercentage": 5.2,
    "numberOfDefaults": 0,
    "reportDate": "2025-10-24",
    "dataSource": "Equifax Spain",
    "equifaxId": "ES-12345"
  },
  "providerName": "Equifax Spain",
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "message": "Enrichment successful",
  "fieldsEnriched": 11,
  "requestId": "req-001"
}
```

---

### Step 7: Monitor and Observe

Your enricher automatically provides observability:

#### 7.1 Metrics (Micrometer)

```
# Enrichment metrics
enrichment.requests.total{provider="Equifax Spain",type="credit-report",status="success"} 150
enrichment.requests.total{provider="Equifax Spain",type="credit-report",status="failure"} 2
enrichment.duration.seconds{provider="Equifax Spain",type="credit-report"} 0.450

# Resiliency metrics
resilience4j.circuitbreaker.state{name="equifax-spain"} 0  # 0=CLOSED, 1=OPEN
resilience4j.retry.calls{name="equifax-spain",kind="successful"} 148
```

#### 7.2 Tracing (Micrometer Tracing)

Distributed traces are created automatically:
```
Span: enrich-credit-report
  ├─ Span: fetch-provider-data (Equifax API call)
  ├─ Span: map-to-target
  └─ Span: apply-strategy
```

#### 7.3 Logs (Structured JSON)

```json
{
  "timestamp": "2025-10-24T10:30:00.123Z",
  "level": "INFO",
  "logger": "EquifaxSpainCreditEnricher",
  "message": "Enrichment completed: type=credit-report, provider=Equifax Spain, success=true, duration=450ms",
  "traceId": "abc123",
  "spanId": "def456",
  "provider": "Equifax Spain",
  "enrichmentType": "credit-report",
  "requestId": "req-001"
}
```

---

### Step 8: Best Practices Checklist

Ensure your enricher follows all best practices:

- ✅ **Extends TypedDataEnricher**: Uses the framework's base class
- ✅ **Implements ProviderOperationCatalog**: Exposes auxiliary operations
- ✅ **Uses lib-common-client**: For REST/SOAP/gRPC calls
- ✅ **Proper Error Handling**: Validates parameters, handles errors gracefully
- ✅ **Comprehensive Logging**: Logs at appropriate levels (debug, info, error)
- ✅ **Provider-Specific Operations**: Implements ID lookups and validations
- ✅ **Clean Separation**: fetchProviderData() and mapToTarget() are focused
- ✅ **Proper DTOs**: Separate DTOs for provider response and target format
- ✅ **Configuration Externalized**: Uses application.yml for configuration
- ✅ **Comprehensive Tests**: Unit tests for all methods
- ✅ **OpenAPI Documentation**: Swagger annotations on controller
- ✅ **Proper Naming**: URL pattern follows {provider}-{region}-{type}
- ✅ **Metadata Complete**: Provider name, description, supported types
- ✅ **Observability**: Automatic tracing, metrics, and logging
- ✅ **Resiliency**: Automatic circuit breaker, retry, rate limiting

---

### Summary

You've now built a **production-ready data enricher** with:

1. ✅ **Core Enrichment**: Fetches and maps Equifax credit reports
2. ✅ **Provider Operations**: Search, validate, and quick-lookup endpoints
3. ✅ **REST API**: Fully documented with OpenAPI/Swagger
4. ✅ **Observability**: Automatic tracing, metrics, and structured logging
5. ✅ **Resiliency**: Circuit breaker, retry, and rate limiting
6. ✅ **Event Publishing**: Automatic enrichment event publishing
7. ✅ **Comprehensive Tests**: Full test coverage
8. ✅ **Best Practices**: Follows all framework conventions

**Next Steps:**

- Add more provider-specific operations as needed
- Implement caching for frequently accessed data
- Add custom metrics for business KPIs
- Create integration tests with real Equifax sandbox
- Document your enricher in your team's wiki

---

## REST API

### Complete Request Flow (Controller → Enricher → Response)

Here's the complete flow from HTTP request to response:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. HTTP REQUEST                                                         │
│                                                                         │
│    POST /api/v1/enrichment/provider-a-spain-company/enrich              │
│         ─────────────────────────────────────────                       │
│         Provider + Region + Type in URL path                            │
│    Content-Type: application/json                                       │
│                                                                         │
│    {                                                                    │
│      "enrichmentType": "company-profile",  ← For logging/metadata       │
│      "strategy": "ENHANCE",                                             │
│      "sourceDto": { "companyId": "12345", "name": "Acme Corp" },        │
│      "parameters": { "companyId": "12345" }                             │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. CONTROLLER (ProviderASpainCompanyController)                         │
│                                                                         │
│    @RequestMapping("/api/v1/enrichment/provider-a-spain-company")       │
│    public class ProviderASpainCompanyController                         │
│            extends AbstractDataEnricherController {                     │
│                                                                         │
│        // Enricher injected in constructor - no routing logic needed    │
│        private final DataEnricher enricher; // ProviderASpainCompanyEnricher │
│                                                                         │
│        @PostMapping("/enrich")                                          │
│        public Mono<EnrichmentApiResponse> enrich(                       │
│                @RequestBody EnrichmentApiRequest request) {             │
│                                                                         │
│            // Logs incoming request with all details                    │
│            log.info("Received enrichment request: type={}, strategy={}", │
│                    request.getEnrichmentType(), request.getStrategy()); │
│                                                                         │
│            // Calls the enricher (already knows which one to use)       │
│            return enricher.enrich(request.toEnrichmentRequest())        │
│                .map(EnrichmentApiResponse::fromEnrichmentResponse)      │
│                .doOnSuccess(response -> {                               │
│                    // Logs successful response                          │
│                    log.info("Enrichment completed: success={}, fields={}", │
│                            response.isSuccess(),                        │
│                            response.getFieldsEnriched());               │
│                });                                                      │
│        }                                                                │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. ENRICHER (AbstractResilientDataEnricher)                             │
│                                                                         │
│    public Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {  │
│                                                                         │
│        // Wraps doEnrich() with automatic features:                     │
│        return executeWithObservabilityAndResiliency(                    │
│            () -> doEnrich(request),                                     │
│            request                                                      │
│        );                                                               │
│                                                                         │
│        // Automatic features applied:                                   │
│        // Tracing span created                                          │
│        // Metrics recorded (duration, success/failure)                  │
│        // Circuit breaker applied                                       │
│        // Retry logic applied                                           │
│        // Rate limiting applied                                         │
│        // Events published (start, success, failure)                    │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. TYPED ENRICHER (TypedDataEnricher.doEnrich)                          │
│                                                                         │
│    protected Mono<EnrichmentResponse> doEnrich(                         │
│            EnrichmentRequest request) {                                 │
│                                                                         │
│        return fetchProviderData(request)  // Your implementation        │
│            .map(providerData -> {                                       │
│                                                                         │
│                TTarget enrichedData;                                    │
│                                                                         │
│                if (request.getStrategy() == RAW) {                      │
│                    // RAW: Skip mapping, return raw provider data       │
│                    enrichedData = (TTarget) providerData;               │
│                } else {                                                 │
│                    // Other strategies: Map first                       │
│                    TTarget mappedData = mapToTarget(providerData);      │
│                                                                         │
│                    // Apply strategy (ENHANCE/MERGE/REPLACE)            │
│                    enrichedData = EnrichmentStrategyApplier.apply(      │
│                        request.getStrategy(),                           │
│                        request.getSourceDto(),                          │
│                        mappedData,                                      │
│                        targetClass                                      │
│                    );                                                   │
│                }                                                        │
│                                                                         │
│                // Build response with metadata                          │
│                return EnrichmentResponseBuilder                         │
│                    .success(enrichedData)                               │
│                    .forRequest(request)                                 │
│                    .withProvider(getProviderName())                     │
│                    .countingEnrichedFields(request.getSourceDto())      │
│                    .build();                                            │
│            });                                                          │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. HTTP RESPONSE                                                        │
│                                                                         │
│    HTTP/1.1 200 OK                                                      │
│    Content-Type: application/json                                       │
│                                                                         │
│    {                                                                    │
│      "success": true,                                                   │
│      "enrichedData": {                                                  │
│        "companyId": "12345",                                            │
│        "name": "Acme Corp",                                             │
│        "registeredAddress": "123 Main St",                              │
│        "industry": "Technology",                                        │
│        "employeeCount": 500,                                            │
│        "annualRevenue": 50000000.0                                      │
│      },                                                                 │
│      "providerName": "Financial Data Provider",                         │
│      "enrichmentType": "company-profile",                               │
│      "strategy": "ENHANCE",                                             │
│      "fieldsEnriched": 4,                                               │
│      "message": "company-profile enrichment completed successfully"     │
│    }                                                                    │
└─────────────────────────────────────────────────────────────────────────┘
```

**Key Points:**

1. **Controller Layer**: Handles HTTP, validates request, logs everything
2. **Resilient Layer**: Adds observability, resiliency, and event publishing
3. **Typed Layer**: Handles strategy application and response building
4. **Your Code**: Only `fetchProviderData()` and `mapToTarget()` (except RAW strategy skips mapping)

### Endpoints

#### POST /api/v1/enrichment/{type}/enrich
Enriches data using the configured provider.

**Request:**
```json
{
  "enrichmentType": "company-profile",
  "strategy": "ENHANCE",
  "sourceDto": {
    "companyId": "12345",
    "name": "Acme Corp"
  },
  "parameters": {
    "companyId": "12345",
    "includeFinancials": true
  },
  "tenantId": "tenant-001",
  "requestId": "req-abc-123",
  "initiator": "user@example.com"
}
```

**Response:**
```json
{
  "success": true,
  "enrichedData": {
    "companyId": "12345",
    "name": "Acme Corp",
    "address": "123 Main St",
    "revenue": 1000000
  },
  "providerName": "Financial Data Provider",
  "enrichmentType": "company-profile",
  "strategy": "ENHANCE",
  "message": "Company data enriched successfully",
  "confidenceScore": 0.95,
  "fieldsEnriched": 2,
  "timestamp": "2025-10-23T10:30:00Z",
  "requestId": "req-abc-123",
  "cost": 0.50,
  "costCurrency": "USD"
}
```

#### GET /api/v1/enrichment/{type}/providers
Lists available providers for the enrichment type.

**Response:**
```json
[
  "Financial Data Provider",
  "Business Data Provider",
  "Credit Bureau Provider"
]
```

#### GET /api/v1/enrichment/{type}/health
Checks the health of the enrichment provider.

**Response:**
```json
{
  "healthy": true,
  "providerName": "Financial Data Provider",
  "message": "Provider is healthy and ready",
  "supportedTypes": ["company-profile", "company-financials"]
}
```

---

## Provider-Specific Operations

Many data enrichment providers require auxiliary operations before the main enrichment can be performed. For example:

- **ID Lookups**: Search for internal provider IDs (e.g., Equifax ID, DUNS number)
- **Entity Matching**: Fuzzy match companies or individuals in the provider's database
- **Validation**: Validate identifiers (CIF, VAT, Tax ID, etc.)
- **Metadata Retrieval**: Get provider-specific configuration or metadata

The library provides a **declarative catalog system** that allows enrichers to expose these operations as REST endpoints automatically.

### Implementing Provider-Specific Operations

#### Step 1: Implement `ProviderOperationCatalog`

```java
@Service
public class EquifaxSpainEnricher
        extends TypedDataEnricher<CompanyDTO, EquifaxResponse, CompanyDTO>
        implements ProviderOperationCatalog {

    @Override
    public List<ProviderOperation> getOperationCatalog() {
        return List.of(
            // Operation 1: Search company by name/CIF to get Equifax internal ID
            ProviderOperation.builder()
                .operationId("search-company")
                .path("/search-company")
                .method(RequestMethod.GET)
                .description("Search for a company by name or CIF to obtain Equifax internal ID")
                .requestExample(Map.of(
                    "companyName", "Acme Corporation",
                    "cif", "A12345678"
                ))
                .responseExample(Map.of(
                    "equifaxId", "ES-12345",
                    "companyName", "ACME CORPORATION SL",
                    "cif", "A12345678",
                    "confidence", 0.95
                ))
                .tags(new String[]{"lookup", "search"})
                .requiresAuth(true)
                .build(),

            // Operation 2: Validate CIF
            ProviderOperation.builder()
                .operationId("validate-cif")
                .path("/validate-cif")
                .method(RequestMethod.GET)
                .description("Validate that a CIF exists in Equifax database")
                .requestExample(Map.of("cif", "A12345678"))
                .responseExample(Map.of(
                    "valid", true,
                    "equifaxId", "ES-12345"
                ))
                .tags(new String[]{"validation"})
                .build(),

            // Operation 3: Get credit score only (without full enrichment)
            ProviderOperation.builder()
                .operationId("get-credit-score")
                .path("/credit-score")
                .method(RequestMethod.POST)
                .description("Get just the credit score without full enrichment data")
                .requestExample(Map.of("equifaxId", "ES-12345"))
                .responseExample(Map.of(
                    "creditScore", 750,
                    "creditRating", "AAA",
                    "lastUpdated", "2025-10-24"
                ))
                .tags(new String[]{"credit", "quick-lookup"})
                .build()
        );
    }

    @Override
    public Mono<Map<String, Object>> executeOperation(
            String operationId,
            Map<String, Object> parameters) {

        return switch (operationId) {
            case "search-company" -> searchCompany(parameters);
            case "validate-cif" -> validateCif(parameters);
            case "get-credit-score" -> getCreditScore(parameters);
            default -> Mono.error(new IllegalArgumentException(
                "Unknown operation: " + operationId));
        };
    }

    private Mono<Map<String, Object>> searchCompany(Map<String, Object> params) {
        String companyName = (String) params.get("companyName");
        String cif = (String) params.get("cif");

        // Call Equifax search API
        return equifaxClient.searchCompany(companyName, cif)
            .map(result -> Map.of(
                "equifaxId", result.getId(),
                "companyName", result.getName(),
                "cif", result.getCif(),
                "confidence", result.getMatchScore()
            ));
    }

    private Mono<Map<String, Object>> validateCif(Map<String, Object> params) {
        String cif = (String) params.get("cif");

        // Call Equifax validation API
        return equifaxClient.validateCif(cif)
            .map(result -> Map.of(
                "valid", result.isValid(),
                "equifaxId", result.getId()
            ));
    }

    private Mono<Map<String, Object>> getCreditScore(Map<String, Object> params) {
        String equifaxId = (String) params.get("equifaxId");

        // Call Equifax credit score API
        return equifaxClient.getCreditScore(equifaxId)
            .map(result -> Map.of(
                "creditScore", result.getScore(),
                "creditRating", result.getRating(),
                "lastUpdated", result.getLastUpdated().toString(),
                "equifaxId", equifaxId
            ));
    }
}
```

#### Step 2: REST Endpoints Are Automatically Exposed

The `AbstractDataEnricherController` automatically exposes these endpoints:

```
POST /api/v1/enrichment/equifax-spain/enrich              (standard enrichment)
GET  /api/v1/enrichment/equifax-spain/health              (health check)
GET  /api/v1/enrichment/equifax-spain/operations          (list operations)
GET  /api/v1/enrichment/equifax-spain/search-company      (via generic handler)
GET  /api/v1/enrichment/equifax-spain/validate-cif        (via generic handler)
POST /api/v1/enrichment/equifax-spain/credit-score        (via generic handler)
```

#### Step 3: Operations Appear in Discovery

```bash
GET /api/v1/enrichment/providers
```

```json
{
  "providerName": "Equifax Spain",
  "supportedTypes": ["credit-report", "company-profile"],
  "description": "Equifax Spain data enrichment services",
  "endpoints": [
    "/api/v1/enrichment/equifax-spain/enrich"
  ],
  "operations": [
    {
      "operationId": "search-company",
      "path": "/api/v1/enrichment/equifax-spain/search-company",
      "method": "GET",
      "description": "Search for a company by name or CIF to obtain Equifax internal ID",
      "tags": ["lookup", "search"],
      "requiresAuth": true,
      "requestExample": {
        "companyName": "Acme Corporation",
        "cif": "A12345678"
      },
      "responseExample": {
        "equifaxId": "ES-12345",
        "companyName": "ACME CORPORATION SL",
        "cif": "A12345678",
        "confidence": 0.95
      }
    }
  ]
}
```

### Complete Workflow Example

```bash
# Step 1: Discover available providers and their operations
GET /api/v1/enrichment/providers?enrichmentType=credit-report

# Step 2: Search for company to get Equifax internal ID
GET /api/v1/enrichment/equifax-spain/search-company?companyName=Acme%20Corp&cif=A12345678

Response:
{
  "equifaxId": "ES-12345",
  "companyName": "ACME CORPORATION SL",
  "cif": "A12345678",
  "confidence": 0.95
}

# Step 3: Use the Equifax ID for enrichment
POST /api/v1/enrichment/equifax-spain/enrich
{
  "enrichmentType": "credit-report",
  "strategy": "ENHANCE",
  "sourceDto": {
    "companyId": "12345",
    "name": "Acme Corp"
  },
  "parameters": {
    "equifaxId": "ES-12345"
  }
}
```

### ProviderOperation Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `operationId` | String | ✅ | Unique identifier (kebab-case) |
| `path` | String | ✅ | URL path relative to enricher base |
| `method` | RequestMethod | ✅ | HTTP method (GET, POST, etc.) |
| `description` | String | ✅ | Human-readable description |
| `requestExample` | Map | ❌ | Example request for documentation |
| `responseExample` | Map | ❌ | Example response for documentation |
| `requiresAuth` | boolean | ❌ | Whether authentication is required (default: true) |
| `tags` | String[] | ❌ | Tags for categorization |

### Benefits

✅ **Declarative**: Operations are declared in a clear, structured way
✅ **Automatic**: Endpoints are exposed automatically without additional code
✅ **Documented**: Each operation includes description and examples
✅ **Discoverable**: Operations appear in the discovery endpoint
✅ **Flexible**: Supports any HTTP method and parameter structure
✅ **Type-Safe**: Uses `Map<String, Object>` for maximum flexibility
✅ **Controlled**: Each enricher controls what operations it exposes

---

## Enricher Utilities

The library provides powerful utilities to simplify enricher development and reduce boilerplate code by **60-70%**.

### TypedDataEnricher - Type-Safe Base Class

For maximum developer experience, use `TypedDataEnricher` which provides:
- Type safety with generics
- Automatic strategy application
- Automatic response building
- Simplified API - only implement `fetchProviderData()` and `mapToTarget()`

```java
@Service
public class FinancialDataEnricher
        extends TypedDataEnricher<CompanyProfileDTO, FinancialDataResponse, CompanyProfileDTO> {

    private final RestClient financialDataClient;

    public FinancialDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            RestClient financialDataClient) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);
        this.financialDataClient = financialDataClient;
    }

    @Override
    protected Mono<FinancialDataResponse> fetchProviderData(EnrichmentRequest request) {
        // Automatic validation with fluent API
        String companyId = request.requireParam("companyId");

        return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
            .withPathParam("id", companyId)
            .execute();
    }

    @Override
    protected CompanyProfileDTO mapToTarget(FinancialDataResponse providerData) {
        // Just map - strategy is applied automatically!
        return CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .annualRevenue(providerData.getRevenue())
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
}
```

### EnrichmentRequestValidator - Fluent Validation DSL

Declarative validation for enrichment requests:

```java
// Validate required parameters
EnrichmentRequestValidator.of(request)
    .requireParam("companyId")
    .requireParamMatching("email", EMAIL_PATTERN)
    .requireParamOfType("includeFinancials", Boolean.class)
    .requireSourceDto()
    .requireStrategy(EnrichmentStrategy.ENHANCE, EnrichmentStrategy.MERGE)
    .requireTenantId()
    .validate();  // Throws exception with all errors if validation fails
```

### EnrichmentRequest Helper Methods

Convenient parameter extraction:

```java
// Get required parameter (throws if missing)
String companyId = request.requireParam("companyId");

// Get optional parameter with default
Boolean includeFinancials = request.param("includeFinancials", false);

// Type-safe parameter extraction
Integer maxResults = request.paramAsInt("maxResults");
String email = request.paramAsString("email");
Boolean active = request.paramAsBoolean("active");

// Check parameter existence
if (request.hasParam("companyId")) {
    // ...
}

// Get typed source DTO
CompanyProfileDTO source = request.getSourceDtoAs(CompanyProfileDTO.class);
```

### EnrichmentStrategyApplier - Automatic Strategy Application

Apply enrichment strategies without manual implementation:

```java
// Automatic strategy application
CompanyProfileDTO enriched = EnrichmentStrategyApplier.apply(
    request.getStrategy(),
    request.getSourceDto(),
    providerData,
    CompanyProfileDTO.class
);

// Or use specific strategies
CompanyProfileDTO enhanced = EnrichmentStrategyApplier.enhance(source, provider, CompanyProfileDTO.class);
CompanyProfileDTO merged = EnrichmentStrategyApplier.merge(source, provider, CompanyProfileDTO.class);
CompanyProfileDTO replaced = EnrichmentStrategyApplier.replace(provider, CompanyProfileDTO.class);

// Count enriched fields
int count = EnrichmentStrategyApplier.countEnrichedFields(source, enriched);
```

### EnrichmentResponseBuilder - Fluent Response Building

Build responses with a fluent API:

```java
// Success response
EnrichmentResponse response = EnrichmentResponseBuilder
    .success(enrichedData)
    .forRequest(request)
    .withProvider("Financial Data Provider")
    .withMessage("Company data enriched successfully")
    .countingEnrichedFields(request.getSourceDto())
    .withCost(0.50, "USD")
    .withConfidence(0.95)
    .withMetadata("provider_id", "FDP-12345")
    .build();

// Failure response
EnrichmentResponse response = EnrichmentResponseBuilder
    .failure("Provider returned 404: Company not found")
    .forRequest(request)
    .withProvider("Financial Data Provider")
    .build();
```

### Benefits Summary

| Aspect | Without Utilities | With Utilities | Improvement |
|--------|------------------|----------------|-------------|
| Lines of Code | 120+ | 40 | **67% reduction** |
| Parameter Validation | Manual if/else | Fluent DSL | **Declarative** |
| Strategy Application | Manual (40+ lines) | Automatic | **Zero boilerplate** |
| Response Building | Manual builder | Fluent helper | **Simplified** |
| Type Safety | Object casting | Generics | **Compile-time** |

---

## Implementation Guide

### Using REST Provider

```java
@Service
public class CreditBureauEnricher
        extends TypedDataEnricher<CreditRequestDTO, CreditReportResponse, CreditReportDTO> {

    private final RestClient creditBureauClient;

    public CreditBureauEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);

        this.creditBureauClient = ServiceClient.rest("credit-bureau")
            .baseUrl("https://api.credit-bureau.example")
            .defaultHeader("Authorization", "Bearer ${credit.api.key}")
            .build();
    }

    @Override
    protected Mono<CreditReportResponse> fetchProviderData(EnrichmentRequest request) {
        // Automatic validation with fluent API
        String subjectId = request.requireParam("subjectId");

        return creditBureauClient.post("/credit-reports", CreditReportResponse.class)
            .withBody(Map.of("subjectId", subjectId, "reportType", "FULL"))
            .execute();
    }

    @Override
    protected CreditReportDTO mapToTarget(CreditReportResponse providerData) {
        // Simple mapping - strategy is applied automatically!
        return CreditReportDTO.builder()
                .subjectId(providerData.getSubjectId())
                .creditScore(providerData.getScore())
                .riskLevel(providerData.getRiskLevel())
                .accounts(providerData.getAccounts())
                .inquiries(providerData.getInquiries())
                .build();
    }

    @Override
    public String getProviderName() {
        return "Credit Bureau Provider";
    }

    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"credit-report-individual", "credit-report-business"};
    }
}
```

### Using SOAP Provider

```java
@Service
public class LegacyDataEnricher
        extends TypedDataEnricher<CompanyProfileDTO, CompanyInfoResponse, CompanyProfileDTO> {

    private final SoapClient soapClient;

    public LegacyDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);

        this.soapClient = ServiceClient.soap("legacy-data-provider")
            .wsdlUrl("https://legacy-provider.example/service?wsdl")
            .credentials("username", "password")
            .timeout(Duration.ofSeconds(45))
            .build();
    }

    @Override
    protected Mono<CompanyInfoResponse> fetchProviderData(EnrichmentRequest request) {
        String companyId = request.requireParam("companyId");

        CompanyRequest soapRequest = new CompanyRequest();
        soapRequest.setCompanyId(companyId);

        return soapClient.invokeAsync("GetCompanyInfo", soapRequest, CompanyInfoResponse.class);
    }

    @Override
    protected CompanyProfileDTO mapToTarget(CompanyInfoResponse providerData) {
        return CompanyProfileDTO.builder()
                .companyId(providerData.getId())
                .name(providerData.getCompanyName())
                .registeredAddress(providerData.getAddress())
                .build();
    }

    @Override
    public String getProviderName() {
        return "Legacy Data Provider";
    }

    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"company-profile"};
    }
}
```

### Using Provider SDK

```java
@Service
public class BusinessDataEnricher
        extends TypedDataEnricher<CompanyProfileDTO, BusinessDataProfile, CompanyProfileDTO> {

    private final BusinessDataClient businessDataClient;

    public BusinessDataEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            @Value("${business.api.key}") String apiKey,
            @Value("${business.api.secret}") String apiSecret) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CompanyProfileDTO.class);
        this.businessDataClient = new BusinessDataClient(apiKey, apiSecret);
    }

    @Override
    protected Mono<BusinessDataProfile> fetchProviderData(EnrichmentRequest request) {
        String companyId = request.requireParam("companyId");

        // Wrap blocking SDK call in Mono
        return Mono.fromCallable(() -> businessDataClient.getCompanyProfile(companyId));
    }

    @Override
    protected CompanyProfileDTO mapToTarget(BusinessDataProfile providerData) {
        return CompanyProfileDTO.builder()
                .companyId(providerData.getBusinessId())
                .name(providerData.getBusinessName())
                .registeredAddress(providerData.getPrimaryAddress())
                .annualRevenue(providerData.getRevenue())
                .build();
    }

    @Override
    public String getProviderName() {
        return "Business Data Provider";
    }

    @Override
    public String[] getSupportedEnrichmentTypes() {
        return new String[]{"company-profile", "company-financials"};
    }
}
```

---

## Configuration

### Application Properties

```yaml
firefly:
  data:
    enrichment:
      # Enable/disable data enrichment
      enabled: true
      
      # Event publishing
      publish-events: true
      
      # Caching
      cache-enabled: false
      cache-ttl-seconds: 3600
      
      # Timeouts
      default-timeout-seconds: 30
      
      # Audit
      capture-raw-responses: false
      
      # Concurrency
      max-concurrent-enrichments: 100
```

### Provider-Specific Configuration

```yaml
# Financial Data Provider
financial-data-provider:
  api:
    key: ${FINANCIAL_DATA_API_KEY}
    base-url: https://api.financial-data-provider.example

# Credit Bureau Provider
credit-bureau:
  api:
    key: ${CREDIT_BUREAU_API_KEY}
    base-url: https://api.credit-bureau.example
```

---

## Examples

See the implementation examples above and the test files for more detailed examples.

---

## Testing

The Data Enrichment abstraction includes comprehensive unit tests that demonstrate best practices for testing enrichers.

### Unit Testing Data Enrichers

```java
@ExtendWith(MockitoExtension.class)
class MyEnricherTest {

    @Mock
    private ServiceClient serviceClient;

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    private MyEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new MyEnricher(
            serviceClient,
            tracingService,
            metricsService,
            resiliencyService,
            eventPublisher
        );
    }

    @Test
    void enrich_shouldReturnEnrichedData_whenProviderReturnsSuccess() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
                .enrichmentType("company-profile")
                .strategy(EnrichmentStrategy.ENHANCE)
                .sourceDto(Map.of("companyId", "12345"))
                .build();

        ProviderResponse providerResponse = new ProviderResponse(/* ... */);
        when(serviceClient.get(anyString(), eq(ProviderResponse.class)))
                .thenReturn(Mono.just(providerResponse));

        // When & Then
        StepVerifier.create(enricher.enrich(request))
                .assertNext(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEnrichedData()).isNotNull();
                })
                .verifyComplete();
    }
}
```

### Testing Event Publishing

```java
@Test
void enrich_shouldPublishEvents_whenEnrichmentCompletes() {
    // Given
    EnrichmentRequest request = EnrichmentRequest.builder()
            .enrichmentType("company-profile")
            .build();

    // When
    StepVerifier.create(enricher.enrich(request))
            .expectNextCount(1)
            .verifyComplete();

    // Then
    verify(eventPublisher).publishEnrichmentStarted(eq(request), eq("My Provider"));
    verify(eventPublisher).publishEnrichmentCompleted(
            eq(request),
            any(EnrichmentResponse.class),
            anyLong()
    );
}
```

### Testing Error Handling

```java
@Test
void enrich_shouldReturnFailureResponse_whenProviderFails() {
    // Given
    EnrichmentRequest request = EnrichmentRequest.builder()
            .enrichmentType("company-profile")
            .build();

    when(serviceClient.get(anyString(), any()))
            .thenReturn(Mono.error(new RuntimeException("Provider unavailable")));

    // When & Then
    StepVerifier.create(enricher.enrich(request))
            .assertNext(response -> {
                assertThat(response.isSuccess()).isFalse();
                assertThat(response.getError()).contains("Provider unavailable");
            })
            .verifyComplete();
}
```

### Test Files

The library includes the following test files as examples:

- `TypedDataEnricherTest.java` - Tests for the typed enricher base class
- `EnrichmentStrategyApplierTest.java` - Tests for automatic strategy application
- `EnrichmentRequestValidatorTest.java` - Tests for fluent validation DSL
- `DataEnricherRegistryTest.java` - Tests for enricher registry and discovery
- `EnrichmentEventPublisherTest.java` - Tests for event publishing
- `AbstractDataEnricherControllerTest.java` - Tests for REST controller
- `DataEnrichmentPropertiesTest.java` - Tests for configuration properties

---

## Best Practices

### 1. Use Appropriate Enrichment Strategy

- **ENHANCE**: When you want to preserve existing data
- **MERGE**: When provider data is more authoritative
- **REPLACE**: When you need complete fresh data
- **RAW**: For debugging or custom processing

### 2. Handle Errors Gracefully

Error handling is automatic with `TypedDataEnricher`, but you can add custom error handling in `fetchProviderData()`:

```java
@Override
protected Mono<ProviderResponse> fetchProviderData(EnrichmentRequest request) {
    String companyId = request.requireParam("companyId");

    return providerClient.get("/data/{id}", ProviderResponse.class)
        .withPathParam("id", companyId)
        .execute()
        .onErrorResume(WebClientResponseException.class, error -> {
            if (error.getStatusCode().value() == 404) {
                log.warn("Company not found: {}", companyId);
                return Mono.error(new EnrichmentException("Company not found"));
            }
            return Mono.error(error);
        });
}
```

### 3. Implement Tenant-Aware Routing

```java
@Service
public class TenantAwareEnrichmentService {
    
    private final DataEnricherRegistry enricherRegistry;
    private final TenantConfigService tenantConfig;
    
    public Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
        String providerName = tenantConfig.getProviderForTenant(
            request.getTenantId(),
            request.getEnrichmentType()
        );
        
        DataEnricher enricher = enricherRegistry
            .getEnricherByProvider(providerName)
            .orElseThrow(() -> new EnricherNotFoundException(providerName));
        
        return enricher.enrich(request);
    }
}
```

### 4. Monitor Costs

You can override the `enrich()` method to add cost tracking while still using `TypedDataEnricher`:

```java
@Override
public Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
    return super.enrich(request)
        .doOnNext(response -> {
            if (response.isSuccess()) {
                // Calculate and set cost based on provider pricing
                double cost = calculateCost(request);
                response.setCost(cost);
                response.setCostCurrency("USD");
            }
        });
}

private double calculateCost(EnrichmentRequest request) {
    // Provider-specific cost calculation
    return 0.50; // Example: $0.50 per enrichment
}
```

### 5. Cache Expensive Operations

```java
@Service
public class CachedEnrichmentService {
    
    private final DataEnricher enricher;
    private final Cache<String, EnrichmentResponse> cache;
    
    public Mono<EnrichmentResponse> enrichWithCache(EnrichmentRequest request) {
        String cacheKey = generateCacheKey(request);
        
        EnrichmentResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        
        return enricher.enrich(request)
            .doOnNext(response -> cache.put(cacheKey, response));
    }
}
```

---

For more information, see:
- [Architecture](architecture.md) - Overall library architecture
- [Configuration](configuration.md) - Complete configuration reference
- [Examples](examples.md) - More usage examples

