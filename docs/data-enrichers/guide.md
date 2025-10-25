# Data Enrichers - Complete Guide

> **Complete guide for building data enricher microservices with lib-common-data**
>
> **Time**: 1-2 hours | **Prerequisites**: Java 21+, Maven 3.8+, Spring Boot 3.x

---

## Table of Contents

1. [What Are Data Enrichers?](#what-are-data-enrichers)
2. [Why Do They Exist?](#why-do-they-exist)
3. [Architecture Overview](#architecture-overview)
4. [Quick Start](#quick-start)
5. [Do I Need to Create Controllers?](#do-i-need-to-create-controllers)
6. [Enrichment Strategies](#enrichment-strategies)
7. [Batch Enrichment](#batch-enrichment)
8. [Multi-Module Project Structure](#multi-module-project-structure)
9. [Building Your First Enricher](#building-your-first-enricher)
10. [Multi-Tenancy](#multi-tenancy)
11. [Priority-Based Selection](#priority-based-selection)
12. [Custom Operations](#custom-operations)
13. [Testing](#testing)
14. [Configuration](#configuration)
15. [Best Practices](#best-practices)

---

## What Are Data Enrichers?

**Data Enrichers** are specialized microservices that integrate with third-party data providers. They serve two main purposes:

1. **Data Enrichment** - Enhance your data with information from external providers
2. **Provider Abstraction** - Abstract provider implementations behind a unified interface

### Real-World Example: Credit Bureau Microservice

Let's use a concrete example: **`core-data-credit-bureaus`** - a microservice that provides credit reports from multiple providers.

**Scenario**: Your company operates in multiple countries and needs credit reports. Each country uses different credit bureau providers:
- 🇪🇸 **Spain** → Equifax Spain
- 🇺🇸 **USA** → Experian USA
- 🇬🇧 **UK** → Experian UK

#### Use Case 1: Data Enrichment

**Your data**:
```json
{
  "companyId": "12345",
  "name": "Acme Corp",
  "taxId": "B12345678"
}
```

**After enrichment** (using `ENHANCE` strategy):
```json
{
  "companyId": "12345",
  "name": "Acme Corp",
  "taxId": "B12345678",
  "creditScore": 750,
  "creditRating": "A",
  "paymentBehavior": "EXCELLENT",
  "riskLevel": "LOW"
}
```

#### Use Case 2: Provider Abstraction

**Without Data Enrichers** (tight coupling):
```java
// Client code depends on specific provider
EquifaxSpainClient equifaxClient = new EquifaxSpainClient();
EquifaxCreditReport report = equifaxClient.getCreditReport("B12345678");
// Now you're locked to Equifax Spain API!
```

**With Data Enrichers** (loose coupling):
```java
// Client calls unified enrichment API
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "params": {"taxId": "B12345678"},
  "strategy": "RAW"  // Returns provider data as-is
}

// Response: Raw Equifax Spain data
{
  "score": 750,
  "rating": "A",
  "paymentHistory": [...],
  // ... Equifax-specific fields
}
```

**Benefits**:
- ✅ Switch from Equifax to Experian without changing client code
- ✅ Different providers per country (Spain uses Equifax, USA uses Experian)
- ✅ A/B test providers in the same country
- ✅ Automatic observability, resiliency, caching for all providers
- ✅ Unified API regardless of underlying provider

### All Use Cases Supported

**Data Enrichers provide a standardized way to**:
- Call external provider APIs
- Transform provider data to your DTOs (or return raw)
- Merge enriched data with your existing data
- Abstract provider implementations
- Handle errors, retries, and circuit breakers
- Trace and monitor all operations
- Cache responses
- Support multi-tenancy

---

## Why Do They Exist?

### The Problem

When integrating with multiple credit bureaus, you face these challenges:

1. **Multiple Bureaus, Multiple Countries**
   - Equifax serves Spain, USA, UK (different APIs per region)
   - Experian serves USA, UK (different data models)
   - Each country needs different bureau configurations

2. **Different Products per Bureau**
   - Equifax Spain: credit-report, credit-monitoring
   - Experian USA: credit-report (different API), business-score
   - Experian UK: risk-assessment, financial-data

3. **Complex Integration Requirements**
   - Each bureau has different authentication
   - Different data formats (JSON, XML, SOAP)
   - Different error handling
   - Different rate limits

4. **Operational Challenges**
   - Need observability (tracing, metrics, logs)
   - Need resiliency (circuit breaker, retry, timeout)
   - Need caching for performance
   - Need health checks and monitoring

### The Solution

**lib-common-data** provides a framework where you:

1. **Create one enricher per type per tenant**
   ```java
   @EnricherMetadata(
       providerName = "Equifax Spain",
       tenantId = "spain-tenant-id",
       type = "credit-report"
   )
   public class EquifaxSpainCreditReportEnricher { ... }
   ```

2. **Get everything automatically**
   - ✅ REST endpoints (Smart routing, Discovery, Health)
   - ✅ Observability (Tracing, Metrics, Logging)
   - ✅ Resiliency (Circuit breaker, Retry, Rate limiting)
   - ✅ Event publishing
   - ✅ Caching
   - ✅ Multi-tenancy support
   - ✅ Priority-based provider selection

3. **Focus only on business logic**
   - Fetch data from provider
   - Map provider data to your DTOs
   - Done!

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Your Microservice: core-data-credit-bureaus                    │
│                                                                 │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Your Enrichers (Just create these!)                       │ │
│  │                                                            │ │
│  │  @EnricherMetadata(providerName="Equifax Spain",           │ │
│  │                    tenantId="spain-tenant-id",             │ │
│  │                    type="credit-report")                   │ │
│  │  class EquifaxSpainCreditReportEnricher { ... }            │ │
│  │                                                            │ │
│  │  @EnricherMetadata(providerName="Experian USA",            │ │
│  │                    tenantId="usa-tenant-id",               │ │
│  │                    type="credit-report")                   │ │
│  │  class ExperianUsaCreditReportEnricher { ... }             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            ↓                                    │
│                   Auto-Registration                             │
│                            ↓                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Global Endpoints (Provided by lib-common-data)            │ │
│  │                                                            │ │
│  │  POST /api/v1/enrichment/smart                             │ │
│  │  POST /api/v1/enrichment/smart/batch                       │ │
│  │  GET  /api/v1/enrichment/providers                         │ │
│  │  GET  /api/v1/enrichment/health                            │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Key Principles

1. **One Enricher = One Type**
   - Each enricher implements exactly ONE enrichment type
   - For ONE specific tenant
   - Clear responsibility, easy to test

2. **No Controllers Needed** ✨
   - Just create the enricher with `@EnricherMetadata`
   - **The library automatically creates all REST endpoints**
   - Your microservice doesn't need to create any controllers
   - Zero boilerplate

3. **Smart Routing**
   - Client sends: `{type: "credit-report", tenantId: "550e8400-..."}`
   - System automatically routes to correct enricher
   - Based on type + tenant + priority

### What the Library Creates Automatically

When you add `lib-common-data` to your microservice, the library **automatically creates** these global REST controllers:

1. **`SmartEnrichmentController`** - Smart enrichment endpoints
   - `POST /api/v1/enrichment/smart` - Single enrichment
   - `POST /api/v1/enrichment/smart/batch` - Batch enrichment
   - Automatic routing by type + tenant + priority

2. **`EnrichmentDiscoveryController`** - Discovery endpoint
   - `GET /api/v1/enrichment/providers`
   - Lists all enrichers in your microservice

3. **`GlobalEnrichmentHealthController`** - Global health endpoint
   - `GET /api/v1/enrichment/health`
   - Health check for all enrichers

4. **`GlobalOperationsController`** - Provider operations endpoint
   - `GET /api/v1/enrichment/operations`
   - `POST /api/v1/enrichment/operations/execute`
   - Lists and executes custom operations

**You don't create these controllers** - they are part of the library and are automatically registered via Spring Boot auto-configuration (`DataEnrichmentAutoConfiguration`).

**Your microservice only needs to**:
1. Add `lib-common-data` dependency
2. Create enrichers with `@EnricherMetadata`
3. That's it! The REST API is ready

---

## Quick Start

Let's build **`core-data-credit-bureaus`** - a microservice that provides credit reports from Equifax Spain.

### Step 1: Create Maven Project

```bash
mvn archetype:generate \
  -DgroupId=com.firefly \
  -DartifactId=core-data-credit-bureaus \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false

cd core-data-credit-bureaus
```

### Step 2: Add Dependencies

```xml
<dependencies>
    <!-- Firefly Common Data Library -->
    <dependency>
        <groupId>com.firefly</groupId>
        <artifactId>lib-common-data</artifactId>
        <version>${lib-common-data.version}</version>
    </dependency>

    <!-- Spring Boot WebFlux -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

### Step 3: Create Your Enricher

```java
package com.firefly.creditbureaus.enricher;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.creditbureaus.client.EquifaxSpainClient;
import com.firefly.creditbureaus.dto.CreditReportDTO;
import com.firefly.creditbureaus.dto.EquifaxResponse;
import reactor.core.publisher.Mono;

@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    description = "Equifax Spain credit report enrichment",
    version = "1.0.0",
    priority = 100,
    tags = {"production", "gdpr-compliant", "spain"}
)
public class EquifaxSpainCreditReportEnricher
        extends DataEnricher<CreditReportDTO, EquifaxResponse, CreditReportDTO> {

    private final EquifaxSpainClient equifaxClient;

    public EquifaxSpainCreditReportEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            EquifaxSpainClient equifaxClient) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);
        this.equifaxClient = equifaxClient;
    }

    @Override
    protected Mono<EquifaxResponse> fetchProviderData(EnrichmentRequest request) {
        String taxId = request.requireParam("taxId");
        return equifaxClient.getCreditReport(taxId);
    }

    @Override
    protected CreditReportDTO mapToTarget(EquifaxResponse equifaxData) {
        return CreditReportDTO.builder()
            .taxId(equifaxData.getCompanyTaxId())
            .creditScore(equifaxData.getScore())
            .creditRating(equifaxData.getRating())
            .paymentBehavior(equifaxData.getPaymentBehavior())
            .riskLevel(equifaxData.getRiskLevel())
            .build();
    }
}
```

### Step 4: That's It!

Your enricher is automatically available via REST API:

```bash
# Smart Enrichment Endpoint
POST http://localhost:8080/api/v1/enrichment/smart
Content-Type: application/json

{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "source": {
    "companyId": "12345",
    "name": "Acme Corp",
    "taxId": "B12345678"
  },
  "params": {
    "taxId": "B12345678"
  },
  "strategy": "ENHANCE"
}

# Response
{
  "success": true,
  "enrichedData": {
    "companyId": "12345",
    "name": "Acme Corp",
    "taxId": "B12345678",
    "creditScore": 750,
    "creditRating": "A",
    "paymentBehavior": "EXCELLENT",
    "riskLevel": "LOW"
  },
  "providerName": "Equifax Spain",
  "type": "credit-report"
}
```

---

## Do I Need to Create Controllers?

**NO!** This is a common question, so let's be crystal clear:

### ❌ What You DON'T Need to Do

You **DO NOT** need to create:
- ❌ REST controllers
- ❌ `@RestController` classes
- ❌ `@RequestMapping` endpoints
- ❌ Any HTTP layer code

### ✅ What You DO Need to Do

You **ONLY** need to:
1. ✅ Add `lib-common-data` dependency to your `pom.xml`
2. ✅ Create enricher classes with `@EnricherMetadata`
3. ✅ That's it!

### How Does It Work?

**The library automatically creates all REST endpoints** for you via Spring Boot auto-configuration:

```
┌──────────────────────────────────────────────────────────────────────┐
│ Your Microservice (core-data-credit-bureaus)                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  📁 pom.xml                                                          │
│     └── <dependency>lib-common-data</dependency>                     │
│                                                                      │
│  📁 src/main/java/com/firefly/creditbureaus/                         │
│     ├── 📄 Application.java (@SpringBootApplication)                 │
│     └── 📁 enricher/                                                 │
│         ├── 📄 EquifaxSpainCreditEnricher.java (@EnricherMetadata)   │
│         └── 📄 ExperianUsaCreditEnricher.java (@EnricherMetadata)    │
│                                                                      │
│  ❌ NO CONTROLLERS IN YOUR CODE!                                     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                                   ↓
                          Spring Boot starts
                                   ↓
┌──────────────────────────────────────────────────────────────────────┐
│ lib-common-data Auto-Configuration Activates                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1️⃣  @ComponentScan discovers "com.firefly.common.data" package      │
│                                                                      │
│  2️⃣  Registers these @RestController beans:                          │
│      ✅ SmartEnrichmentController                                    │
│      ✅ EnrichmentDiscoveryController                                │
│      ✅ GlobalEnrichmentHealthController                             │
│      ✅ GlobalOperationsController                                   │
│                                                                      │
│  3️⃣  Creates DataEnricherRegistry bean                               │
│                                                                      │
│  4️⃣  Scans for your enrichers with @EnricherMetadata                 │
│                                                                      │
│  5️⃣  Auto-registers your enrichers in the registry                   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                                   ↓
                         REST API is ready!
                                   ↓
┌──────────────────────────────────────────────────────────────────────┐
│ 🌐 Available Endpoints (created automatically by library)            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  POST   /api/v1/enrichment/smart                                     │
│         → SmartEnrichmentController                                  │
│         → Routes to correct enricher by type + tenant + priority     │
│                                                                      │
│  GET    /api/v1/enrichment/providers                                 │
│         → EnrichmentDiscoveryController                              │
│         → Lists all available enrichers                              │
│                                                                      │
│  GET    /api/v1/enrichment/health                                    │
│         → GlobalEnrichmentHealthController                           │
│         → Health check for all enrichers                             │
│                                                                      │
│  GET    /api/v1/enrichment/operations                                │
│  POST   /api/v1/enrichment/operations/execute                        │
│         → GlobalOperationsController                                 │
│         → Lists and executes custom operations                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Key Point**: The controllers (`SmartEnrichmentController`, etc.) are **inside lib-common-data JAR**, not in your microservice code. Spring Boot's `@ComponentScan` from `DataEnrichmentAutoConfiguration` automatically discovers and registers them.

**Important Note**: There is a deprecated `AbstractDataEnricherController` class that you might see in the codebase. This is an **old pattern** that required microservices to manually extend it and create controllers. **Do NOT use it** - it's marked for removal. The new pattern (October 2025) uses only the 3 global controllers above.

### Example: Complete Microservice Structure

```
core-data-provider-a-enricher/
├── pom.xml
│   └── <dependency>lib-common-data</dependency>
├── src/main/java/
│   └── com/company/enricher/
│       ├── ProviderACreditEnricher.java      # Your enricher
│       └── ProviderAEnricherApplication.java # Spring Boot app
└── src/main/resources/
    └── application.yml

# NO CONTROLLERS NEEDED!
# The library creates them automatically
```

### What Endpoints Are Available?

Once your microservice starts, these endpoints are **automatically available**:

```bash
# 1. Smart Enrichment (automatic routing)
POST http://localhost:8080/api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "550e8400-...",
  "params": {"companyId": "12345"}
}

# 2. Discovery (list all enrichers)
GET http://localhost:8080/api/v1/enrichment/providers

# 3. Global Health (health check)
GET http://localhost:8080/api/v1/enrichment/health

# 4. List Operations (list all custom operations)
GET http://localhost:8080/api/v1/enrichment/operations

# 5. Execute Operation (execute a custom operation)
POST http://localhost:8080/api/v1/enrichment/operations/execute
{
  "type": "credit-report",
  "tenantId": "550e8400-...",
  "operationId": "search-company",
  "request": {"companyName": "Acme Corp"}
}
```

### Configuration

The global endpoints are **enabled by default**. To disable them:

```yaml
firefly:
  data:
    enrichment:
      discovery:
        enabled: false  # Disables all global endpoints
```

### Summary

| What | Who Creates It | You Need To |
|------|---------------|-------------|
| **REST Controllers** | ✅ Library (automatic) | ❌ Nothing |
| **Enrichment Endpoints** | ✅ Library (automatic) | ❌ Nothing |
| **Discovery Endpoint** | ✅ Library (automatic) | ❌ Nothing |
| **Health Endpoint** | ✅ Library (automatic) | ❌ Nothing |
| **Operations Endpoints** | ✅ Library (automatic) | ❌ Nothing |
| **Enricher Classes** | ❌ You | ✅ Create with @EnricherMetadata |
| **Operation Classes** | ❌ You (optional) | ✅ Create with @EnricherOperation |

**Bottom line**: Just create your enrichers and operations. The library handles all the REST API for you! 🎉

---

## Enrichment Strategies

Data Enrichers support **four enrichment strategies** that control how provider data is combined with your source data.

### Strategy 1: ENHANCE

**Purpose**: Fill only null/empty fields from provider data, preserving existing data.

**Use Case**: You have partial data and want to fill gaps without overwriting existing values.

**Example**:

```java
// Your source data
{
  "companyId": "12345",
  "name": "Acme Corp",
  "creditScore": null,  // Missing
  "rating": null        // Missing
}

// Provider data
{
  "id": "12345",
  "businessName": "ACME CORPORATION",  // Different
  "score": 750,
  "grade": "A"
}

// Request
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "550e8400-...",
  "source": {"companyId": "12345", "name": "Acme Corp", "creditScore": null, "rating": null},
  "params": {"companyId": "12345"},
  "strategy": "ENHANCE"
}

// Result (only fills null fields)
{
  "companyId": "12345",
  "name": "Acme Corp",           // Preserved (not null)
  "creditScore": 750,             // Filled from provider
  "rating": "A"                   // Filled from provider
}
```

### Strategy 2: MERGE

**Purpose**: Combine source and provider data, with provider data taking precedence on conflicts.

**Use Case**: You want the most complete data from both sources, preferring provider data when both exist.

**Example**:

```java
// Your source data
{
  "companyId": "12345",
  "name": "Acme Corp",
  "creditScore": 700,  // Old value
  "rating": null
}

// Provider data
{
  "id": "12345",
  "businessName": "ACME CORPORATION",
  "score": 750,        // New value
  "grade": "A"
}

// Request
POST /api/v1/enrichment/smart
{
  "strategy": "MERGE"
}

// Result (provider data wins on conflicts)
{
  "companyId": "12345",
  "name": "ACME CORPORATION",     // Provider wins
  "creditScore": 750,              // Provider wins (newer)
  "rating": "A"                    // From provider
}
```

### Strategy 3: REPLACE

**Purpose**: Completely replace source data with provider data (transformed to your DTO format).

**Use Case**: Provider data is authoritative and should override everything.

**Example**:

```java
// Your source data
{
  "companyId": "12345",
  "name": "Acme Corp",
  "creditScore": 700,
  "rating": "B"
}

// Provider data
{
  "id": "12345",
  "businessName": "ACME CORPORATION",
  "score": 750,
  "grade": "A"
}

// Request
POST /api/v1/enrichment/smart
{
  "strategy": "REPLACE"
}

// Result (completely replaced, mapped to your DTO)
{
  "companyId": "12345",
  "companyName": "ACME CORPORATION",
  "creditScore": 750,
  "rating": "A"
}
```

### Strategy 4: RAW

**Purpose**: Return raw bureau data without transformation or merging.

**Use Case 1 - Bureau Abstraction**: You want to abstract bureau implementations behind a unified API.

```java
// Request for Spain (uses Equifax Spain)
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "params": {"taxId": "B12345678"},
  "strategy": "RAW"
}

// Response (raw Equifax Spain data)
{
  "companyTaxId": "B12345678",
  "companyName": "ACME CORPORATION SL",
  "score": 750,
  "rating": "A",
  "paymentBehavior": "EXCELLENT",
  "riskLevel": "LOW",
  "creditLimit": 500000.00
  // ... all Equifax-specific fields
}

// Change tenant to USA (uses Experian USA - different bureau)
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "usa-tenant-id",
  "params": {"taxId": "12-3456789"},
  "strategy": "RAW"
}

// Response (raw Experian USA data - different structure!)
{
  "businessId": "12-3456789",
  "legalName": "Acme Corporation",
  "creditScore": {
    "value": 780,
    "grade": "AA"
  },
  "riskIndicator": "LOW"
  // ... different Experian-specific fields
}
```

**Use Case 2 - Debugging**: You need to see the exact bureau response for debugging.

**Use Case 3 - Custom Processing**: You want to process bureau data yourself in the client.

### Strategy Comparison

| Strategy | Preserves Source | Uses Provider | Transforms | Use Case |
|----------|-----------------|---------------|------------|----------|
| **ENHANCE** | ✅ Yes (non-null) | ✅ For nulls only | ✅ Yes | Fill gaps in existing data |
| **MERGE** | ⚠️ Partial | ✅ Yes (wins conflicts) | ✅ Yes | Combine both sources |
| **REPLACE** | ❌ No | ✅ Yes (all) | ✅ Yes | Provider is authoritative |
| **RAW** | ❌ No | ✅ Yes (all) | ❌ No | Provider abstraction, debugging |

### When to Use Each Strategy

**Use ENHANCE when**:
- You have existing data that should not be overwritten
- You only want to fill missing fields
- Your data is more up-to-date than provider data

**Use MERGE when**:
- You want the most complete data from both sources
- Provider data is generally more accurate
- You want to combine complementary data

**Use REPLACE when**:
- Provider data is the single source of truth
- You want to completely refresh your data
- You're doing initial data loading

**Use RAW when**:
- You want to abstract provider implementations
- You need to switch providers without changing client code
- You're debugging provider responses
- You want to process provider data yourself
- You're building a provider abstraction layer

---

## Batch Enrichment

### What Is Batch Enrichment?

**Batch Enrichment** allows you to enrich multiple items in a single request with automatic parallelization and error handling.

### Why Use Batch Enrichment?

**Benefits**:
- ✅ **Higher Throughput** - Process hundreds of items in parallel
- ✅ **Reduced Latency** - Single HTTP request instead of N requests
- ✅ **Automatic Parallelization** - Configurable concurrency control
- ✅ **Individual Error Handling** - One failure doesn't stop the batch
- ✅ **Efficient Provider Usage** - Grouped by enricher for optimal routing

### Batch Enrichment Endpoint

```bash
POST /api/v1/enrichment/smart/batch
```

**Request**: Array of enrichment requests
**Response**: Stream of enrichment responses (in same order as requests)

### Example: Enrich 100 Companies

```bash
POST /api/v1/enrichment/smart/batch
[
  {
    "type": "credit-report",
    "tenantId": "spain-tenant-id",
    "params": {"taxId": "B12345678"},
    "strategy": "RAW"
  },
  {
    "type": "credit-report",
    "tenantId": "spain-tenant-id",
    "params": {"taxId": "B87654321"},
    "strategy": "RAW"
  },
  {
    "type": "credit-report",
    "tenantId": "usa-tenant-id",
    "params": {"ein": "12-3456789"},
    "strategy": "RAW"
  },
  // ... 97 more requests
]
```

**Response** (streamed):
```json
[
  {
    "success": true,
    "enrichedData": {
      "companyTaxId": "B12345678",
      "score": 750,
      "rating": "A"
    },
    "providerName": "Equifax Spain",
    "type": "credit-report"
  },
  {
    "success": true,
    "enrichedData": {
      "companyTaxId": "B87654321",
      "score": 680,
      "rating": "B"
    },
    "providerName": "Equifax Spain",
    "type": "credit-report"
  },
  {
    "success": true,
    "enrichedData": {
      "businessId": "12-3456789",
      "creditScore": {"value": 780, "grade": "AA"}
    },
    "providerName": "Experian USA",
    "type": "credit-report"
  },
  // ... 97 more responses
]
```

### How It Works

1. **Grouping**: Requests are automatically grouped by `type + tenantId`
   - All Spain credit reports → Equifax Spain enricher
   - All USA credit reports → Experian USA enricher

2. **Parallel Processing**: Each group is processed in parallel
   - Configurable parallelism (default: 10 concurrent requests)
   - Prevents overwhelming providers

3. **Error Handling**: Individual failures don't stop the batch
   - Failed items return error response
   - Successful items return enriched data
   - All responses in same order as requests

### Configuration

```yaml
firefly:
  data:
    enrichment:
      max-batch-size: 100          # Maximum items per batch
      batch-parallelism: 10        # Concurrent requests per enricher
      batch-fail-fast: false       # Continue on individual errors
```

### Use Cases

**1. Bulk Credit Report Retrieval**
```bash
# Enrich 1000 companies from CSV file
POST /api/v1/enrichment/smart/batch
[
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B12345678"}},
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B87654321"}},
  # ... 998 more
]
```

**2. Multi-Tenant Batch Processing**
```bash
# Mix of Spain and USA companies in single batch
POST /api/v1/enrichment/smart/batch
[
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B12345678"}},
  {"type": "credit-report", "tenantId": "usa-tenant-id", "params": {"ein": "12-3456789"}},
  {"type": "credit-report", "tenantId": "uk-tenant-id", "params": {"companyNumber": "12345678"}},
  # ... automatically routed to correct enrichers
]
```

**3. Mixed Enrichment Types**
```bash
# Different enrichment types in same batch
POST /api/v1/enrichment/smart/batch
[
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B12345678"}},
  {"type": "company-profile", "tenantId": "spain-tenant-id", "params": {"companyId": "12345"}},
  {"type": "risk-assessment", "tenantId": "spain-tenant-id", "params": {"taxId": "B12345678"}},
  # ... different enrichers for each type
]
```

### Performance Characteristics

| Batch Size | Parallelism | Avg Time per Item | Total Time | Throughput |
|------------|-------------|-------------------|------------|------------|
| 10 | 10 | 500ms | ~500ms | 20 items/sec |
| 100 | 10 | 500ms | ~5s | 20 items/sec |
| 100 | 20 | 500ms | ~2.5s | 40 items/sec |
| 1000 | 10 | 500ms | ~50s | 20 items/sec |
| 1000 | 50 | 500ms | ~10s | 100 items/sec |

**Key Insight**: Higher parallelism = higher throughput, but also higher load on providers. Tune based on provider rate limits.

### Error Handling in Batches

```json
// Request with 3 items (one will fail)
POST /api/v1/enrichment/smart/batch
[
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B12345678"}},
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "INVALID"}},
  {"type": "credit-report", "tenantId": "spain-tenant-id", "params": {"taxId": "B87654321"}}
]

// Response (all 3 items, one with error)
[
  {
    "success": true,
    "enrichedData": {"score": 750, "rating": "A"},
    "providerName": "Equifax Spain"
  },
  {
    "success": false,
    "error": "Invalid tax ID format: INVALID",
    "providerName": "Equifax Spain"
  },
  {
    "success": true,
    "enrichedData": {"score": 680, "rating": "B"},
    "providerName": "Equifax Spain"
  }
]
```

### Best Practices for Batch Enrichment

**1. Use Appropriate Batch Sizes**
- ✅ **Small batches (10-50)**: Low latency, quick feedback
- ✅ **Medium batches (50-200)**: Balanced throughput and latency
- ✅ **Large batches (200-1000)**: Maximum throughput, higher latency

**2. Tune Parallelism Based on Provider**
```yaml
# Conservative (for rate-limited providers)
batch-parallelism: 5

# Balanced (default)
batch-parallelism: 10

# Aggressive (for high-capacity providers)
batch-parallelism: 50
```

**3. Handle Partial Failures**
```java
// Client code should check each response
responses.forEach(response -> {
    if (response.isSuccess()) {
        // Process successful enrichment
    } else {
        // Log or retry failed item
        log.error("Enrichment failed: {}", response.getError());
    }
});
```

**4. Monitor Batch Performance**
- Track batch size distribution
- Monitor parallelism effectiveness
- Watch for provider rate limit errors
- Measure end-to-end batch latency

---

## Multi-Module Project Structure

For production-ready microservices, use a multi-module Maven structure. Here's the recommended structure for **`core-data-credit-bureaus`**:

```
core-data-credit-bureaus/
├── pom.xml                                    # Parent POM
│
├── credit-bureaus-domain/                    # Shared DTOs, models, enums
│   ├── pom.xml
│   └── src/main/java/
│       └── com/firefly/creditbureaus/domain/
│           ├── dto/
│           │   ├── CreditReportDTO.java       # Common credit report DTO
│           │   └── CompanySearchRequest.java
│           └── enums/
│               ├── CreditRating.java
│               └── RiskLevel.java
│
├── equifax-spain-client/                     # Equifax Spain REST client
│   ├── pom.xml
│   └── src/main/java/
│       └── com/firefly/creditbureaus/equifax/spain/
│           ├── client/
│           │   └── EquifaxSpainClient.java
│           ├── model/
│           │   └── EquifaxResponse.java       # Equifax-specific response
│           └── config/
│               └── EquifaxSpainConfig.java
│
├── experian-usa-client/                      # Experian USA REST client
│   ├── pom.xml
│   └── src/main/java/
│       └── com/firefly/creditbureaus/experian/usa/
│           ├── client/
│           │   └── ExperianUsaClient.java
│           ├── model/
│           │   └── ExperianResponse.java      # Experian-specific response
│           └── config/
│               └── ExperianUsaConfig.java
│
├── credit-bureaus-enricher/                  # Enrichers (main module)
│   ├── pom.xml
│   └── src/main/java/
│       └── com/firefly/creditbureaus/enricher/
│           ├── EquifaxSpainCreditReportEnricher.java
│           ├── ExperianUsaCreditReportEnricher.java
│           └── operation/
│               ├── SearchCompanyOperation.java
│               └── ValidateTaxIdOperation.java
│
└── credit-bureaus-app/                       # Spring Boot application
    ├── pom.xml
    └── src/main/
        ├── java/
        │   └── com/firefly/creditbureaus/
        │       └── CreditBureausApplication.java
        └── resources/
            └── application.yml

---

## Building Your First Enricher

Let's build the **Equifax Spain enricher** for `core-data-credit-bureaus` step by step.

### Step 1: Create Domain Module

**credit-bureaus-domain/pom.xml**:

```xml
<project>
    <parent>
        <groupId>com.firefly</groupId>
        <artifactId>core-data-credit-bureaus</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>credit-bureaus-domain</artifactId>
    <name>Credit Bureaus - Domain</name>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

**CreditReportDTO.java** (Common DTO for all providers):

```java
package com.firefly.creditbureaus.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreditReportDTO {
    private String taxId;
    private String companyName;
    private Integer creditScore;
    private String creditRating;
    private String paymentBehavior;
    private String riskLevel;
    private Double creditLimit;
}
```

**CreditRating.java** (Enum):

```java
package com.firefly.creditbureaus.domain.enums;

public enum CreditRating {
    AAA, AA, A, BBB, BB, B, CCC, CC, C, D
}
```

### Step 2: Create Equifax Spain Client Module

**equifax-spain-client/pom.xml**:

```xml
<project>
    <parent>
        <groupId>com.firefly</groupId>
        <artifactId>core-data-credit-bureaus</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>equifax-spain-client</artifactId>
    <name>Equifax Spain - Client</name>

    <dependencies>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>credit-bureaus-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>lib-common-client</artifactId>
        </dependency>
    </dependencies>
</project>
```

**EquifaxResponse.java** (Equifax-specific response model):

```java
package com.firefly.creditbureaus.equifax.spain.model;

import lombok.Data;

@Data
public class EquifaxResponse {
    private String companyTaxId;
    private String companyName;
    private Integer score;
    private String rating;
    private String paymentBehavior;
    private String riskLevel;
    private Double creditLimit;
}
```

**EquifaxSpainClient.java**:

```java
package com.firefly.creditbureaus.equifax.spain.client;

import com.firefly.common.client.RestClient;
import com.firefly.creditbureaus.equifax.spain.model.EquifaxResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class EquifaxSpainClient {

    private final RestClient restClient;

    public EquifaxSpainClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Mono<EquifaxResponse> getCreditReport(String taxId) {
        return restClient.get("/api/v2/credit-reports/{taxId}", EquifaxResponse.class)
            .withPathParam("taxId", taxId)
            .withHeader("X-API-Key", "${equifax.api-key}")
            .execute();
    }
}
```

**EquifaxSpainConfig.java**:

```java
package com.firefly.creditbureaus.equifax.spain.config;

import com.firefly.common.client.RestClient;
import com.firefly.common.client.config.RestClientProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EquifaxSpainConfig {

    @Bean
    @ConfigurationProperties(prefix = "equifax.spain.client")
    public RestClientProperties equifaxSpainClientProperties() {
        return new RestClientProperties();
    }

    @Bean
    public RestClient equifaxSpainRestClient(RestClientProperties equifaxSpainClientProperties) {
        return RestClient.builder()
            .properties(equifaxSpainClientProperties)
            .build();
    }
}
```

### Step 3: Create Enricher Module

**credit-bureaus-enricher/pom.xml**:

```xml
<project>
    <parent>
        <groupId>com.firefly</groupId>
        <artifactId>core-data-credit-bureaus</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>credit-bureaus-enricher</artifactId>
    <name>Credit Bureaus - Enricher</name>

    <dependencies>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>credit-bureaus-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>equifax-spain-client</artifactId>
        </dependency>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>lib-common-data</artifactId>
        </dependency>
    </dependencies>
</project>
```

**EquifaxSpainCreditReportEnricher.java**:

```java
package com.firefly.creditbureaus.enricher;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.creditbureaus.equifax.spain.client.EquifaxSpainClient;
import com.firefly.creditbureaus.equifax.spain.model.EquifaxResponse;
import com.firefly.creditbureaus.domain.dto.CreditReportDTO;
import reactor.core.publisher.Mono;

@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    description = "Equifax Spain credit report enrichment",
    version = "1.0.0",
    priority = 100,
    tags = {"production", "gdpr-compliant", "spain"}
)
public class EquifaxSpainCreditReportEnricher
        extends DataEnricher<CreditReportDTO, EquifaxResponse, CreditReportDTO> {

    private final EquifaxSpainClient equifaxClient;

    public EquifaxSpainCreditReportEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            EquifaxSpainClient equifaxClient) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);
        this.equifaxClient = equifaxClient;
    }

    @Override
    protected Mono<EquifaxResponse> fetchProviderData(EnrichmentRequest request) {
        // Extract required parameter
        String taxId = request.requireParam("taxId");

        // Call Equifax Spain API
        return equifaxClient.getCreditReport(taxId);
    }

    @Override
    protected CreditReportDTO mapToTarget(EquifaxResponse equifaxData) {
        // Map Equifax response to common DTO
        return CreditReportDTO.builder()
            .taxId(equifaxData.getCompanyTaxId())
            .companyName(equifaxData.getCompanyName())
            .creditScore(equifaxData.getScore())
            .creditRating(equifaxData.getRating())
            .paymentBehavior(equifaxData.getPaymentBehavior())
            .riskLevel(equifaxData.getRiskLevel())
            .creditLimit(equifaxData.getCreditLimit())
            .build();
    }
}
```

### Step 4: Create Application Module

**credit-bureaus-app/pom.xml**:

```xml
<project>
    <parent>
        <groupId>com.firefly</groupId>
        <artifactId>core-data-credit-bureaus</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>credit-bureaus-app</artifactId>
    <name>Credit Bureaus - Application</name>

    <dependencies>
        <dependency>
            <groupId>com.firefly</groupId>
            <artifactId>credit-bureaus-enricher</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**CreditBureausApplication.java**:

```java
package com.firefly.creditbureaus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.firefly.creditbureaus",
    "com.firefly.common.data"
})
public class CreditBureausApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditBureausApplication.class, args);
    }
}
```

**application.yml**:

```yaml
server:
  port: 8080

spring:
  application:
    name: core-data-credit-bureaus
  profiles:
    active: dev

# Equifax Spain Client Configuration
equifax:
  spain:
    client:
      base-url: https://api.equifax.es
      timeout: 30s
      auth:
        type: BEARER
        token: ${EQUIFAX_SPAIN_API_KEY}
  api-key: ${EQUIFAX_SPAIN_API_KEY}

# Experian USA Client Configuration (for future use)
experian:
  usa:
    client:
      base-url: https://api.experian.com
      timeout: 30s
      auth:
        type: BEARER
        token: ${EXPERIAN_USA_API_KEY}

# Firefly Common Data Configuration
firefly:
  data:
    enrichment:
      enabled: true
      cache:
        enabled: true
        ttl: 3600
      discovery:
        enabled: true
    resiliency:
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 1s
      rate-limiter:
        enabled: true
        limit-for-period: 100
        limit-refresh-period: 1s
    observability:
      tracing:
        enabled: true
      metrics:
        enabled: true
```

### Step 5: Run and Test

```bash
# Build
mvn clean install

# Run
cd credit-bureaus-app
mvn spring-boot:run

# Test Equifax Spain enricher
curl -X POST http://localhost:8080/api/v1/enrichment/smart \
  -H "Content-Type: application/json" \
  -d '{
    "type": "credit-report",
    "tenantId": "spain-tenant-id",
    "source": {
      "companyId": "12345",
      "name": "Acme Corp",
      "taxId": "B12345678"
    },
    "params": {
      "taxId": "B12345678"
    },
    "strategy": "ENHANCE"
  }'
```

---

## Multi-Tenancy

> **⚠️ Important Note**: The examples below are **simplified and non-exhaustive**. In reality, each credit bureau provider has different products, APIs, authentication methods, and data models. The scenarios shown here illustrate common patterns but **your actual implementation will vary** based on your specific provider contracts and requirements.

### The Problem

In **`core-data-credit-bureaus`**, different regions (tenants) use different credit bureau providers, and each provider offers different products with different APIs:

**🇪🇸 Spain (Tenant: `spain-tenant-id`)**
- **Provider**: Equifax Spain
- **Products**:
  - ✅ Credit Report (single unified API)
  - ✅ Credit Monitoring (single unified API)
- **Characteristics**: Simple, one API per product

**🇺🇸 USA (Tenant: `usa-tenant-id`)**
- **Provider**: Experian USA
- **Products**:
  - ✅ Business Credit Report (API v1)
  - ✅ Consumer Credit Report (API v2 - different from business!)
  - ✅ Credit Score Plus (API v3 - premium product)
- **Characteristics**: Complex, multiple APIs per provider, different authentication per API

**🇬🇧 UK (Tenant: `uk-tenant-id`)**
- **Provider**: Experian UK
- **Products**:
  - ✅ Credit Report (different API than USA Experian!)
  - ✅ Risk Assessment (UK-specific product)
- **Characteristics**: Same provider name (Experian) but completely different implementation than USA

### Key Insight: N Providers × M Products × P Tenants

The complexity comes from:
- **N Providers per Tenant**: Spain uses Equifax, USA uses Experian
- **M Products per Provider**: Experian USA has 3 different products with 3 different APIs
- **P Tenants**: Each country is a separate tenant with different configurations

**Formula**: Total Enrichers = Sum of (Products per Provider per Tenant)

### The Solution: One Enricher per Product per Tenant

Create **one enricher for each product offered by each provider in each tenant**:

#### 🇪🇸 Spain - Equifax (Simple Case: 1 Provider, 2 Products)

```java
// Product 1: Credit Report
@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    description = "Equifax Spain unified credit report API",
    priority = 100
)
public class EquifaxSpainCreditReportEnricher extends DataEnricher<...> {
    @Override
    protected Mono<EquifaxResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Equifax Spain credit report API
        return equifaxClient.getCreditReport(request.requireParam("taxId"));
    }
}

// Product 2: Credit Monitoring
@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-monitoring",
    description = "Equifax Spain credit monitoring API",
    priority = 100
)
public class EquifaxSpainCreditMonitoringEnricher extends DataEnricher<...> {
    @Override
    protected Mono<EquifaxMonitoringResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Equifax Spain monitoring API (different endpoint!)
        return equifaxClient.getMonitoring(request.requireParam("taxId"));
    }
}
```

#### 🇺🇸 USA - Experian (Complex Case: 1 Provider, 3 Products, 3 Different APIs)

```java
// Product 1: Business Credit Report (API v1)
@EnricherMetadata(
    providerName = "Experian USA",
    tenantId = "usa-tenant-id",
    type = "business-credit-report",
    description = "Experian USA Business Credit Report API v1",
    priority = 100
)
public class ExperianUsaBusinessCreditEnricher extends DataEnricher<...> {
    private final ExperianBusinessApiClient businessClient; // Different client!

    @Override
    protected Mono<ExperianBusinessResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Experian Business API (v1) - requires EIN
        return businessClient.getBusinessReport(request.requireParam("ein"));
    }
}

// Product 2: Consumer Credit Report (API v2 - completely different!)
@EnricherMetadata(
    providerName = "Experian USA",
    tenantId = "usa-tenant-id",
    type = "consumer-credit-report",
    description = "Experian USA Consumer Credit Report API v2",
    priority = 100
)
public class ExperianUsaConsumerCreditEnricher extends DataEnricher<...> {
    private final ExperianConsumerApiClient consumerClient; // Different client!

    @Override
    protected Mono<ExperianConsumerResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Experian Consumer API (v2) - requires SSN, different auth!
        return consumerClient.getConsumerReport(request.requireParam("ssn"));
    }
}

// Product 3: Credit Score Plus (API v3 - premium product)
@EnricherMetadata(
    providerName = "Experian USA",
    tenantId = "usa-tenant-id",
    type = "credit-score-plus",
    description = "Experian USA Credit Score Plus API v3 (premium)",
    priority = 100
)
public class ExperianUsaCreditScorePlusEnricher extends DataEnricher<...> {
    private final ExperianPremiumApiClient premiumClient; // Yet another client!

    @Override
    protected Mono<ExperianScorePlusResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Experian Premium API (v3) - different pricing, different SLA
        return premiumClient.getScorePlus(request.requireParam("ein"));
    }
}
```

#### 🇬🇧 UK - Experian UK (Same Provider Name, Different Implementation)

```java
// Product 1: Credit Report (DIFFERENT from USA Experian!)
@EnricherMetadata(
    providerName = "Experian UK",
    tenantId = "uk-tenant-id",
    type = "credit-report",
    description = "Experian UK Credit Report API (different from USA)",
    priority = 100
)
public class ExperianUkCreditReportEnricher extends DataEnricher<...> {
    private final ExperianUkApiClient ukClient; // Completely different client than USA!

    @Override
    protected Mono<ExperianUkResponse> fetchProviderData(EnrichmentRequest request) {
        // Call Experian UK API - different endpoint, different auth, different data model
        return ukClient.getCreditReport(request.requireParam("companyNumber"));
    }
}

// Product 2: Risk Assessment (UK-specific product)
@EnricherMetadata(
    providerName = "Experian UK",
    tenantId = "uk-tenant-id",
    type = "risk-assessment",
    description = "Experian UK Risk Assessment (UK-specific)",
    priority = 100
)
public class ExperianUkRiskAssessmentEnricher extends DataEnricher<...> {
    @Override
    protected Mono<ExperianUkRiskResponse> fetchProviderData(EnrichmentRequest request) {
        // UK-specific risk assessment API
        return ukClient.getRiskAssessment(request.requireParam("companyNumber"));
    }
}
```

### Real-World Complexity Matrix

| Tenant | Provider | Product | Enricher Class | API Endpoint | Auth Method |
|--------|----------|---------|----------------|--------------|-------------|
| 🇪🇸 Spain | Equifax Spain | credit-report | `EquifaxSpainCreditReportEnricher` | `/v1/credit-report` | API Key |
| 🇪🇸 Spain | Equifax Spain | credit-monitoring | `EquifaxSpainCreditMonitoringEnricher` | `/v1/monitoring` | API Key |
| 🇺🇸 USA | Experian USA | business-credit-report | `ExperianUsaBusinessCreditEnricher` | `/business/v1/report` | OAuth 2.0 |
| 🇺🇸 USA | Experian USA | consumer-credit-report | `ExperianUsaConsumerCreditEnricher` | `/consumer/v2/report` | mTLS |
| 🇺🇸 USA | Experian USA | credit-score-plus | `ExperianUsaCreditScorePlusEnricher` | `/premium/v3/score` | OAuth 2.0 + API Key |
| 🇬🇧 UK | Experian UK | credit-report | `ExperianUkCreditReportEnricher` | `/uk/v1/credit` | Basic Auth |
| 🇬🇧 UK | Experian UK | risk-assessment | `ExperianUkRiskAssessmentEnricher` | `/uk/v1/risk` | Basic Auth |

**Total Enrichers in this example**: 7 (2 for Spain + 3 for USA + 2 for UK)

### Usage Examples

```bash
# 🇪🇸 Spain - Credit Report (Equifax Spain, simple API)
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "params": {"taxId": "B12345678"}
}
→ Routes to EquifaxSpainCreditReportEnricher

# 🇺🇸 USA - Business Credit Report (Experian USA API v1)
POST /api/v1/enrichment/smart
{
  "type": "business-credit-report",
  "tenantId": "usa-tenant-id",
  "params": {"ein": "12-3456789"}
}
→ Routes to ExperianUsaBusinessCreditEnricher

# 🇺🇸 USA - Consumer Credit Report (Experian USA API v2 - different!)
POST /api/v1/enrichment/smart
{
  "type": "consumer-credit-report",
  "tenantId": "usa-tenant-id",
  "params": {"ssn": "123-45-6789"}
}
→ Routes to ExperianUsaConsumerCreditEnricher

# 🇬🇧 UK - Credit Report (Experian UK - different from USA!)
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "uk-tenant-id",
  "params": {"companyNumber": "12345678"}
}
→ Routes to ExperianUkCreditReportEnricher

# ❌ ERROR - Product doesn't exist in this tenant
POST /api/v1/enrichment/smart
{
  "type": "credit-score-plus",
  "tenantId": "spain-tenant-id",  # Spain doesn't have this product
  "params": {"ein": "12-3456789"}
}
→ 404 Not Found: No enricher found for type 'credit-score-plus' and tenant 'spain-tenant-id'
```

### Key Takeaways

1. **One Enricher = One Product + One Tenant**
   - Each enricher handles exactly ONE product from ONE provider in ONE tenant
   - Clear separation of concerns

2. **Same Provider ≠ Same Implementation**
   - Experian USA and Experian UK are completely different implementations
   - Different APIs, different auth, different data models

3. **One Provider Can Have Multiple Products**
   - Experian USA has 3 different products with 3 different APIs
   - Each product needs its own enricher

4. **Flexibility**
   - Add new products without touching existing enrichers
   - Different tenants can have different product catalogs
   - Easy to A/B test or migrate providers per tenant

---

## Priority-Based Selection

### The Problem

In **Spain**, you have **multiple credit bureaus** available:
- **Equifax Spain** (primary, more comprehensive, more expensive)
- **CRIF Spain** (fallback, cheaper, basic data)

### The Solution

Use **priority** to control which enricher is selected:

```java
// Primary provider (high priority)
@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    priority = 100  // Higher priority - selected first
)
public class EquifaxSpainCreditReportEnricher { ... }

// Fallback provider (lower priority)
@EnricherMetadata(
    providerName = "CRIF Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    priority = 50  // Lower priority - used as fallback
)
public class CrifSpainCreditReportEnricher { ... }
```

### How It Works

When a request comes in:
```bash
POST /api/v1/enrichment/smart
{"type": "credit-report", "tenantId": "spain-tenant-id", ...}
```

The system:
1. Finds all enrichers with `type="credit-report"` and `tenantId="spain-tenant-id"`
2. Sorts by priority (highest first)
3. Selects Equifax Spain (priority 100)
4. If Equifax Spain fails (circuit breaker open), could manually route to CRIF Spain

### Use Cases

- **Primary/Fallback**: Use comprehensive bureau first, fallback to basic
- **A/B Testing**: Route percentage of traffic to new bureau
- **Gradual Migration**: Slowly increase priority of new bureau
- **Regional Preferences**: Different priorities per region

---

## Custom Operations

### What Are Custom Operations?

**Custom Operations** (also called Enricher Operations) are auxiliary operations that enrichers expose to support their enrichment workflow. These are enricher-specific operations that clients may need to call before or alongside enrichment requests.

### Common Use Cases in Credit Bureaus

- **Company Search** - Search for a company by name or tax ID to get the bureau's internal ID
- **Tax ID Validation** - Validate tax ID format before requesting credit report
- **Coverage Check** - Check if a company exists in the bureau's database
- **Quick Score** - Get just the credit score without full report
- **Monitoring Status** - Check if a company is being monitored

### Why Do They Exist?

Many credit bureaus require a **two-step workflow**:

1. **First**: Search/validate to get bureau's internal company ID
2. **Then**: Use that ID for credit report enrichment

**Example Workflow with Equifax Spain**:
```
Client has: "Acme Corp" + Tax ID "B12345678"
Equifax needs: "EQF-ES-987654" (Equifax's internal company ID)

Step 1: Call search-company operation to find Equifax company ID
Step 2: Use Equifax company ID in credit report enrichment request
```

### How to Create Custom Operations

#### Step 1: Define Request/Response DTOs

```java
package com.firefly.creditbureaus.domain.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanySearchRequest {
    private String companyName;
    private String taxId;
    private Double minConfidence;
}

@Data
@Builder
public class CompanySearchResponse {
    private String providerId;
    private String companyName;
    private String taxId;
    private Double confidence;
}
```

#### Step 2: Create Operation Class

Use `@EnricherOperation` annotation and extend `AbstractEnricherOperation`:

```java
package com.firefly.creditbureaus.enricher.operation;

import com.firefly.common.data.operation.AbstractEnricherOperation;
import com.firefly.common.data.operation.EnricherOperation;
import com.firefly.creditbureaus.equifax.spain.client.EquifaxSpainClient;
import com.firefly.creditbureaus.domain.dto.CompanySearchRequest;
import com.firefly.creditbureaus.domain.dto.CompanySearchResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

@EnricherOperation(
    operationId = "search-company",
    description = "Search for a company in Equifax Spain database by name or tax ID",
    method = RequestMethod.POST,
    tags = {"lookup", "search", "equifax"}
)
public class EquifaxSearchCompanyOperation
        extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> {

    private final EquifaxSpainClient equifaxClient;

    public EquifaxSearchCompanyOperation(EquifaxSpainClient equifaxClient) {
        this.equifaxClient = equifaxClient;
    }

    @Override
    protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
        return equifaxClient.searchCompany(request.getCompanyName(), request.getTaxId())
            .map(equifaxResult -> CompanySearchResponse.builder()
                .providerId(equifaxResult.getCompanyId())
                .companyName(equifaxResult.getName())
                .taxId(equifaxResult.getTaxId())
                .confidence(equifaxResult.getMatchScore())
                .build());
    }

    @Override
    protected void validateRequest(CompanySearchRequest request) {
        if (request.getCompanyName() == null && request.getTaxId() == null) {
            throw new IllegalArgumentException("Either companyName or taxId must be provided");
        }
    }
}
```

**Key Points**:
- ✅ Use `@EnricherOperation` annotation (automatically registers as Spring bean)
- ✅ Extend `AbstractEnricherOperation<TRequest, TResponse>`
- ✅ Implement `doExecute()` with your business logic
- ✅ Optionally override `validateRequest()` for custom validation
- ✅ You get observability, resiliency, caching, and events **automatically**!

#### Step 3: Register Operations in Your Enricher

Override `getOperations()` in your enricher:

```java
package com.firefly.creditbureaus.enricher;

import com.firefly.common.data.enrichment.EnricherMetadata;
import com.firefly.common.data.service.DataEnricher;
import com.firefly.common.data.operation.EnricherOperationInterface;
import com.firefly.creditbureaus.enricher.operation.EquifaxSearchCompanyOperation;
import com.firefly.creditbureaus.enricher.operation.EquifaxValidateTaxIdOperation;

@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    description = "Equifax Spain credit report enrichment",
    priority = 100
)
public class EquifaxSpainCreditReportEnricher
        extends DataEnricher<CreditReportDTO, EquifaxResponse, CreditReportDTO> {

    private final EquifaxSpainClient equifaxClient;
    private final EquifaxSearchCompanyOperation searchCompanyOperation;
    private final EquifaxValidateTaxIdOperation validateTaxIdOperation;

    public EquifaxSpainCreditReportEnricher(
            JobTracingService tracingService,
            JobMetricsService metricsService,
            ResiliencyDecoratorService resiliencyService,
            EnrichmentEventPublisher eventPublisher,
            EquifaxSpainClient equifaxClient,
            EquifaxSearchCompanyOperation searchCompanyOperation,
            EquifaxValidateTaxIdOperation validateTaxIdOperation) {
        super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);
        this.equifaxClient = equifaxClient;
        this.searchCompanyOperation = searchCompanyOperation;
        this.validateTaxIdOperation = validateTaxIdOperation;
    }

    @Override
    public List<EnricherOperationInterface<?, ?>> getOperations() {
        return List.of(
            searchCompanyOperation,
            validateTaxIdOperation
        );
    }

    @Override
    protected Mono<EquifaxResponse> fetchProviderData(EnrichmentRequest request) {
        String taxId = request.requireParam("taxId");
        return equifaxClient.getCreditReport(taxId);
    }

    @Override
    protected CreditReportDTO mapToTarget(EquifaxResponse equifaxData) {
        return CreditReportDTO.builder()
            .taxId(equifaxData.getCompanyTaxId())
            .companyName(equifaxData.getCompanyName())
            .creditScore(equifaxData.getScore())
            .creditRating(equifaxData.getRating())
            .build();
    }
}
```

### Global Operations Endpoints

The library **automatically creates** global endpoints for operations (no controllers needed!):

#### 1. List All Operations

```bash
# List all operations across all enrichers
GET /api/v1/enrichment/operations

# List operations for specific type
GET /api/v1/enrichment/operations?type=credit-report

# List operations for specific tenant
GET /api/v1/enrichment/operations?tenantId=spain-tenant-id

# List operations for specific type + tenant
GET /api/v1/enrichment/operations?type=credit-report&tenantId=spain-tenant-id
```

**Response**:
```json
[
  {
    "providerName": "Equifax Spain",
    "operations": [
      {
        "operationId": "search-company",
        "path": "/api/v1/enrichment/operations/execute",
        "method": "POST",
        "description": "Search for a company in Equifax Spain database by name or tax ID",
        "tags": ["lookup", "search", "equifax"],
        "requiresAuth": true,
        "requestType": "CompanySearchRequest",
        "responseType": "CompanySearchResponse",
        "requestSchema": { ... },
        "responseSchema": { ... },
        "requestExample": {
          "companyName": "Acme Corp",
          "taxId": "B12345678"
        },
        "responseExample": {
          "providerId": "EQF-ES-987654",
          "companyName": "ACME CORPORATION SL",
          "taxId": "B12345678",
          "confidence": 0.98
        }
      }
    ]
  }
]
```
#### 2. Execute an Operation

```bash
POST /api/v1/enrichment/operations/execute
Content-Type: application/json

{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "operationId": "search-company",
  "request": {
    "companyName": "Acme Corp",
    "taxId": "B12345678"
  }
}
```

**Response**:
```json
{
  "providerId": "EQF-ES-987654",
  "companyName": "ACME CORPORATION SL",
  "taxId": "B12345678",
  "confidence": 0.98
}
```

### Complete Workflow Example

Here's a complete workflow showing how to use operations with Equifax Spain enrichment:

```bash
# Step 1: Search for company to get Equifax company ID
POST /api/v1/enrichment/operations/execute
{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "operationId": "search-company",
  "request": {
    "companyName": "Acme Corp",
    "taxId": "B12345678"
  }
}

# Response
{
  "providerId": "EQF-ES-987654",
  "companyName": "ACME CORPORATION SL",
  "taxId": "B12345678",
  "confidence": 0.98
}

# Step 2: Use Equifax company ID in enrichment
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "spain-tenant-id",
  "params": {
    "taxId": "B12345678"
  },
  "strategy": "RAW"
}

# Response - Full credit report from Equifax Spain
{
  "success": true,
  "enrichedData": {
    "companyTaxId": "B12345678",
    "companyName": "ACME CORPORATION SL",
    "score": 750,
    "rating": "A",
    "paymentBehavior": "EXCELLENT",
    "riskLevel": "LOW",
    "creditLimit": 500000.00
  },
  "providerName": "Equifax Spain",
  "type": "credit-report"
}
```

### What You Get Automatically

When you create operations with `@EnricherOperation` and `AbstractEnricherOperation`, you get:

- ✅ **Automatic REST endpoints** via `GlobalOperationsController`
- ✅ **Observability** - Distributed tracing, metrics, logging
- ✅ **Resiliency** - Circuit breaker, retry, rate limiting, timeout
- ✅ **Caching** - Automatic caching with tenant isolation
- ✅ **Validation** - Jakarta Validation support
- ✅ **JSON Schema** - Automatic schema generation for request/response
- ✅ **Event Publishing** - Operation started, completed, failed events
- ✅ **Error Handling** - Comprehensive error handling

### Best Practices for Operations

#### 1. Use Descriptive Operation IDs

**❌ DON'T**:
```java
@EnricherOperation(operationId = "search")  // Too generic
```

**✅ DO**:
```java
@EnricherOperation(operationId = "search-company")  // Clear and specific
```

#### 2. Provide Complete Metadata

**❌ DON'T**:
```java
@EnricherOperation(
    operationId = "search-company",
    method = RequestMethod.POST
)
```

**✅ DO**:
```java
@EnricherOperation(
    operationId = "search-company",
    description = "Search for a company by name or tax ID to obtain provider internal ID",
    method = RequestMethod.POST,
    tags = {"lookup", "search"},
    requiresAuth = true
)
```

#### 3. Validate Input

**❌ DON'T**:
```java
@Override
protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
    // No validation - might fail with NPE
    return client.searchCompany(request.getCompanyName(), request.getTaxId());
}
```

**✅ DO**:
```java
@Override
protected void validateRequest(CompanySearchRequest request) {
    if (request.getCompanyName() == null && request.getTaxId() == null) {
        throw new IllegalArgumentException("Either companyName or taxId must be provided");
    }
}

@Override
protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
    return client.searchCompany(request.getCompanyName(), request.getTaxId());
}
```

#### 4. Use Meaningful DTOs

**❌ DON'T**:
```java
// Using Map<String, Object> - no type safety
public class SearchOperation extends AbstractEnricherOperation<Map<String, Object>, Map<String, Object>> { }
```

**✅ DO**:
```java
// Using proper DTOs - type safe, validated, documented
public class SearchOperation extends AbstractEnricherOperation<CompanySearchRequest, CompanySearchResponse> { }
```

---

## Testing

### Unit Testing Your Enricher

```java
package com.firefly.creditbureaus.enricher;

import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import com.firefly.creditbureaus.equifax.spain.client.EquifaxSpainClient;
import com.firefly.creditbureaus.equifax.spain.model.EquifaxResponse;
import com.firefly.creditbureaus.domain.dto.CreditReportDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquifaxSpainCreditReportEnricherTest {

    @Mock
    private JobTracingService tracingService;

    @Mock
    private JobMetricsService metricsService;

    @Mock
    private ResiliencyDecoratorService resiliencyService;

    @Mock
    private EnrichmentEventPublisher eventPublisher;

    @Mock
    private EquifaxSpainClient equifaxClient;

    private EquifaxSpainCreditReportEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new EquifaxSpainCreditReportEnricher(
            tracingService,
            metricsService,
            resiliencyService,
            eventPublisher,
            equifaxClient
        );
    }

    @Test
    void shouldFetchEquifaxData() {
        // Given
        EquifaxResponse equifaxResponse = new EquifaxResponse();
        equifaxResponse.setCompanyTaxId("B12345678");
        equifaxResponse.setCompanyName("Acme Corp SL");
        equifaxResponse.setScore(750);
        equifaxResponse.setRating("A");
        equifaxResponse.setPaymentBehavior("EXCELLENT");
        equifaxResponse.setRiskLevel("LOW");
        equifaxResponse.setCreditLimit(500000.0);

        when(equifaxClient.getCreditReport(anyString()))
            .thenReturn(Mono.just(equifaxResponse));

        EnrichmentRequest request = EnrichmentRequest.builder()
            .params(Map.of("taxId", "B12345678"))
            .build();

        // When
        Mono<EquifaxResponse> result = enricher.fetchProviderData(request);

        // Then
        StepVerifier.create(result)
            .assertNext(response -> {
                assertThat(response.getCompanyTaxId()).isEqualTo("B12345678");
                assertThat(response.getScore()).isEqualTo(750);
            })
            .verifyComplete();
    }

    @Test
    void shouldMapToTarget() {
        // Given
        EquifaxResponse equifaxResponse = new EquifaxResponse();
        equifaxResponse.setCompanyTaxId("B12345678");
        equifaxResponse.setCompanyName("Acme Corp SL");
        equifaxResponse.setScore(750);
        equifaxResponse.setRating("A");
        equifaxResponse.setPaymentBehavior("EXCELLENT");
        equifaxResponse.setRiskLevel("LOW");
        equifaxResponse.setCreditLimit(500000.0);

        // When
        CreditReportDTO result = enricher.mapToTarget(equifaxResponse);

        // Then
        assertThat(result.getTaxId()).isEqualTo("B12345678");
        assertThat(result.getCompanyName()).isEqualTo("Acme Corp SL");
        assertThat(result.getCreditScore()).isEqualTo(750);
        assertThat(result.getCreditRating()).isEqualTo("A");
        assertThat(result.getPaymentBehavior()).isEqualTo("EXCELLENT");
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
        assertThat(result.getCreditLimit()).isEqualTo(500000.0);
    }
}
```

### Integration Testing

```java
package com.firefly.creditbureaus;

import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.creditbureaus.domain.dto.CreditReportDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreditBureausIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldEnrichCreditReportWithEquifaxSpain() {
        // Given
        EnrichmentRequest request = EnrichmentRequest.builder()
            .type("credit-report")
            .tenantId("spain-tenant-id")
            .source(Map.of("companyId", "12345", "name", "Acme Corp", "taxId", "B12345678"))
            .params(Map.of("taxId", "B12345678"))
            .strategy("ENHANCE")
            .build();

        // When
        ResponseEntity<EnrichmentResponse> response = restTemplate.postForEntity(
            "/api/v1/enrichment/smart",
            request,
            EnrichmentResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getProviderName()).isEqualTo("Equifax Spain");

        CreditReportDTO enrichedData = (CreditReportDTO) response.getBody().getEnrichedData();
        assertThat(enrichedData.getTaxId()).isEqualTo("B12345678");
        assertThat(enrichedData.getCreditScore()).isNotNull();
    }

    @Test
    void shouldDiscoverEnrichers() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/enrichment/providers",
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Equifax Spain");
        assertThat(response.getBody()).contains("credit-report");
    }

    @Test
    void shouldCheckHealth() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/enrichment/health",
            String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("healthy");
    }
}
```

---

## Configuration

### Complete Configuration Reference

```yaml
server:
  port: 8080

spring:
  application:
    name: core-data-credit-bureaus

# Equifax Spain Client Configuration
equifax:
  spain:
    client:
      base-url: https://api.equifax.es
      timeout: 30s
      auth:
        type: BEARER
        token: ${EQUIFAX_SPAIN_API_KEY}
      retry:
        enabled: true
        max-attempts: 3

# Firefly Common Data Configuration
firefly:
  data:
    # Enrichment Configuration
    enrichment:
      enabled: true
      cache:
        enabled: true
        ttl: 3600  # 1 hour
        max-size: 10000
      validation:
        enabled: true
        fail-fast: true

    # Resiliency Configuration
    resiliency:
      circuit-breaker:
        enabled: true
        failure-rate-threshold: 50  # Open circuit if 50% failures
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
        sliding-window-size: 100
      retry:
        enabled: true
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
      rate-limiter:
        enabled: true
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 5s
      timeout:
        enabled: true
        duration: 30s

    # Observability Configuration
    observability:
      tracing:
        enabled: true
        sample-rate: 1.0  # 100% sampling
      metrics:
        enabled: true
        export-interval: 60s
      logging:
        enabled: true
        level: INFO

    # Event Publishing Configuration
    events:
      enabled: true
      async: true
      topics:
        enrichment-started: enrichment.started
        enrichment-completed: enrichment.completed
        enrichment-failed: enrichment.failed

# Management Endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
```

### Environment-Specific Configuration

**application-dev.yml**:

```yaml
firefly:
  data:
    enrichment:
      cache:
        enabled: false  # Disable cache in dev
    observability:
      tracing:
        sample-rate: 1.0  # 100% sampling in dev
```

**application-prod.yml**:

```yaml
firefly:
  data:
    enrichment:
      cache:
        enabled: true
        ttl: 7200  # 2 hours in prod
    resiliency:
      circuit-breaker:
        failure-rate-threshold: 30  # More aggressive in prod
    observability:
      tracing:
        sample-rate: 0.1  # 10% sampling in prod
```

---

## Best Practices

### 1. One Enricher = One Type

**❌ DON'T** create enrichers that handle multiple types:

```java
// BAD - Handles multiple types
@EnricherMetadata(type = "credit-report,company-profile")
public class ProviderAEnricher { ... }
```

**✅ DO** create one enricher per type:

```java
// GOOD - One type per enricher
@EnricherMetadata(type = "credit-report")
public class ProviderACreditReportEnricher { ... }

@EnricherMetadata(type = "company-profile")
public class ProviderACompanyProfileEnricher { ... }
```

### 2. Use Meaningful Tenant IDs

**❌ DON'T** use generic or unclear tenant IDs:

```java
@EnricherMetadata(tenantId = "00000000-0000-0000-0000-000000000001")  // What tenant is this?
```

**✅ DO** document tenant IDs clearly:

```java
// Spain tenant: 550e8400-e29b-41d4-a716-446655440001
@EnricherMetadata(tenantId = "550e8400-e29b-41d4-a716-446655440001")
```

### 3. Set Appropriate Priorities

**❌ DON'T** use the same priority for all enrichers:

```java
@EnricherMetadata(priority = 50)  // Default for everything
```

**✅ DO** use priorities strategically:

```java
// Primary provider (expensive, accurate)
@EnricherMetadata(priority = 100)

// Fallback provider (cheaper, less accurate)
@EnricherMetadata(priority = 50)

// Test/experimental provider
@EnricherMetadata(priority = 10)
```

### 4. Validate Input Parameters

**❌ DON'T** assume parameters exist:

```java
String companyId = request.getParams().get("companyId");  // NPE if missing!
```

**✅ DO** use `requireParam()` for required parameters:

```java
String companyId = request.requireParam("companyId");  // Throws clear error if missing
```

### 5. Handle Provider Errors Gracefully

**❌ DON'T** let provider errors crash your enricher:

```java
return providerClient.getCreditReport(companyId);  // What if provider returns 500?
```

**✅ DO** handle errors and provide meaningful messages:

```java
return equifaxClient.getCreditReport(taxId)
    .onErrorMap(WebClientResponseException.class, ex ->
        new EnrichmentException(
            "Equifax Spain credit report failed for tax ID " + taxId,
            ex
        )
    );
```

### 6. Use Descriptive Metadata

**❌ DON'T** use minimal metadata:

```java
@EnricherMetadata(
    providerName = "EQ",
    type = "cr"
)
```

**✅ DO** provide complete, descriptive metadata:

```java
@EnricherMetadata(
    providerName = "Equifax Spain",
    tenantId = "spain-tenant-id",
    type = "credit-report",
    description = "Equifax Spain credit report enrichment with GDPR compliance",
    version = "1.0.0",
    priority = 100,
    tags = {"production", "gdpr-compliant", "spain", "equifax"}
)
```

### 7. Test Both Success and Failure Paths

**❌ DON'T** only test happy path:

```java
@Test
void shouldEnrich() {
    // Only tests success
}
```

**✅ DO** test all scenarios:

```java
@Test
void shouldEnrichSuccessfully() { ... }

@Test
void shouldHandleProviderError() { ... }

@Test
void shouldHandleInvalidInput() { ... }

@Test
void shouldHandleTimeout() { ... }

@Test
void shouldHandleCircuitBreakerOpen() { ... }
```

### 8. Use Multi-Module Structure for Production

**❌ DON'T** put everything in one module:

```
src/
├── domain/
├── client/
├── enricher/
└── application/
```

**✅ DO** use separate modules:

```
credit-bureaus-domain/
equifax-spain-client/
experian-usa-client/
credit-bureaus-enricher/
credit-bureaus-app/
```

### 9. Configure Resiliency Appropriately

**❌ DON'T** use default values for everything:

```yaml
firefly:
  data:
    resiliency:
      enabled: true  # Using all defaults
```

**✅ DO** tune based on your bureau's characteristics:

```yaml
firefly:
  data:
    resiliency:
      circuit-breaker:
        failure-rate-threshold: 30  # Equifax Spain can be flaky
        wait-duration-in-open-state: 120s  # Give it time to recover
      retry:
        max-attempts: 5  # Equifax has transient errors
        wait-duration: 2s
      timeout:
        duration: 45s  # Credit bureau APIs can be slow
```

### 10. Monitor and Observe

**❌ DON'T** deploy without monitoring:

```yaml
firefly:
  data:
    observability:
      enabled: false  # No visibility!
```

**✅ DO** enable full observability:

```yaml
firefly:
  data:
    observability:
      tracing:
        enabled: true
      metrics:
        enabled: true
      logging:
        enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## Summary

### What You've Learned

1. **What Data Enrichers Are**: Specialized microservices for data enrichment AND provider abstraction
2. **Why They Exist**: Solve multi-provider, multi-tenant, multi-product integration challenges
3. **Architecture**: One enricher = one type, zero boilerplate, smart routing
4. **Enrichment Strategies**: ENHANCE, MERGE, REPLACE, RAW - each for different use cases
5. **Multi-Tenancy**: Different implementations per tenant, different products per tenant
6. **Priority-Based Selection**: Control which provider is used when multiple match
7. **Custom Operations**: Auxiliary operations like search, validate, lookup
8. **Testing**: Unit and integration testing strategies
9. **Configuration**: Complete configuration reference
10. **Best Practices**: Production-ready patterns and anti-patterns

### What You Get Automatically

When you create an enricher with `@EnricherMetadata`, you automatically get:

- ✅ **Smart Enrichment Endpoint** - `POST /api/v1/enrichment/smart`
- ✅ **Batch Enrichment Endpoint** - `POST /api/v1/enrichment/smart/batch`
- ✅ **Discovery Endpoint** - `GET /api/v1/enrichment/providers`
- ✅ **Global Health Endpoint** - `GET /api/v1/enrichment/health`
- ✅ **Distributed Tracing** - Micrometer integration
- ✅ **Metrics** - Prometheus-compatible metrics
- ✅ **Circuit Breaker** - Resilience4j integration
- ✅ **Retry Logic** - Configurable retry with exponential backoff
- ✅ **Rate Limiting** - Protect your providers
- ✅ **Timeout Handling** - Prevent hanging requests
- ✅ **Event Publishing** - Enrichment lifecycle events
- ✅ **Caching** - Configurable caching layer
- ✅ **Validation** - Fluent validation DSL

### Next Steps

1. **Create your first enricher** following the [Building Your First Enricher](#building-your-first-enricher) section
2. **Add multi-tenancy** if you serve multiple tenants
3. **Configure resiliency** based on your provider's characteristics
4. **Write tests** for both success and failure paths
5. **Enable monitoring** and observe your enrichers in production
6. **Iterate** based on real-world usage and metrics

---

## Need Help?

- **Documentation**: Check the complete API reference in the source code
- **Examples**: See the test files in `src/test/java/com/firefly/common/data/`
- **Issues**: Report issues in the project's issue tracker

---

**Happy Enriching! 🚀**
