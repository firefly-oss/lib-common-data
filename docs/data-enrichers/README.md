# Data Enrichers

> **Complete framework for building data enricher microservices**
>
> Build production-ready microservices that integrate with third-party data providers
> with zero boilerplate, smart routing, and enterprise-grade features.

---

## 📖 What Are Data Enrichers?

**Data Enrichers** are specialized microservices that integrate with third-party data providers (like Equifax, Moody's, Dun & Bradstreet). They serve two main purposes:

1. **Data Enrichment** - Enhance your data with information from external providers
2. **Provider Abstraction** - Abstract provider implementations behind a unified interface

### Real-World Use Cases

**Data Enrichment**:
- **Credit Scoring**: Enrich customer data with credit scores from credit bureaus
- **Financial Data**: Add financial metrics from market data providers
- **Business Intelligence**: Augment company profiles with business intelligence data
- **Validation**: Validate addresses or tax IDs with government services
- **Market Data**: Fetch real-time market data or exchange rates

**Provider Abstraction** (using `RAW` strategy):
- **Multi-Provider Support**: Switch between providers without changing client code
- **A/B Testing**: Test different providers for the same data type
- **Gradual Migration**: Migrate from one provider to another transparently
- **Multi-Tenant Routing**: Spain uses Provider A, USA uses Provider B - automatically routed
- **Unified Observability**: All providers get tracing, metrics, circuit breaker, caching

---

## 🚀 Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-common-data</artifactId>
    <version>${lib-common-data.version}</version>
</dependency>
```

### 2. Create Your Enricher

```java
@EnricherMetadata(
    providerName = "Provider A",
    tenantId = "550e8400-e29b-41d4-a716-446655440001",
    type = "credit-report",
    priority = 100
)
public class ProviderACreditReportEnricher extends DataEnricher {

    @Override
    protected Mono<ProviderResponse> fetchProviderData(EnrichmentRequest request) {
        String companyId = request.requireParam("companyId");
        return providerClient.getCreditReport(companyId);
    }

    @Override
    protected CreditReportDTO mapToTarget(ProviderResponse providerData) {
        return CreditReportDTO.builder()
            .companyId(providerData.getId())
            .creditScore(providerData.getScore())
            .build();
    }
}
```

### 3. That's It!

**No controllers needed!** The library automatically creates all REST endpoints for you.

Your enricher is automatically available:

```bash
POST /api/v1/enrichment/smart
{
  "type": "credit-report",
  "tenantId": "550e8400-e29b-41d4-a716-446655440001",
  "params": {"companyId": "12345"}
}
```

---

## Do I Need to Create Controllers?

**NO!** This is important to understand:

### ❌ You DON'T Create
- REST controllers
- HTTP endpoints
- Any `@RestController` classes

### ✅ You ONLY Create
- Enricher classes with `@EnricherMetadata`

### How It Works

The library (`lib-common-data`) **automatically creates** these global controllers via Spring Boot auto-configuration:

1. **`SmartEnrichmentController`** - `POST /api/v1/enrichment/smart`
2. **`EnrichmentDiscoveryController`** - `GET /api/v1/enrichment/providers`
3. **`GlobalEnrichmentHealthController`** - `GET /api/v1/enrichment/health`

**Your microservice structure**:
```
your-enricher-microservice/
├── pom.xml (includes lib-common-data)
├── YourEnricher.java (@EnricherMetadata)
└── Application.java (@SpringBootApplication)

# NO CONTROLLERS NEEDED!
# The library creates them automatically
```

When Spring Boot starts:
1. Library auto-configuration activates
2. Library scans for enrichers with `@EnricherMetadata`
3. Library automatically registers global controllers
4. Your enrichers are automatically available via REST API

**See the [Complete Guide](guide.md#do-i-need-to-create-controllers) for more details.**

---

## 📖 Complete Guide

**👉 [Read the Complete Guide](guide.md)**

The complete guide covers everything you need to know:

1. **What Are Data Enrichers?** - Understanding enrichment AND provider abstraction
2. **Why Do They Exist?** - Real-world use cases and challenges
3. **Architecture Overview** - How everything works together
4. **Quick Start** - Get running in 5 minutes
5. **Enrichment Strategies** - ENHANCE, MERGE, REPLACE, RAW explained
6. **Multi-Module Project Structure** - Production-ready structure
7. **Building Your First Enricher** - Step-by-step tutorial
8. **Multi-Tenancy** - Different implementations per tenant
9. **Priority-Based Selection** - Control provider selection
10. **Custom Operations** - Auxiliary operations (search, validate, etc.)
11. **Testing** - Unit and integration testing
12. **Configuration** - Complete configuration reference
13. **Best Practices** - Production-ready patterns

---

## ✨ What You Get Automatically

When you add `lib-common-data` and create enrichers with `@EnricherMetadata`, the library automatically provides:

### Global REST Controllers (Created by Library)
The library **automatically creates** these controllers - you don't need to create them:

- ✅ **`SmartEnrichmentController`** - `POST /api/v1/enrichment/smart`
  - Automatic routing by type + tenant + priority
  - No need to know which provider to call

- ✅ **`EnrichmentDiscoveryController`** - `GET /api/v1/enrichment/providers`
  - Lists all enrichers in your microservice
  - Filterable by type and tenant

- ✅ **`GlobalEnrichmentHealthController`** - `GET /api/v1/enrichment/health`
  - Health check for all enrichers
  - Filterable by type and tenant

### Enterprise Features (Built-in)
- ✅ **Observability** - Distributed tracing, metrics, logging
- ✅ **Resiliency** - Circuit breaker, retry, rate limiting, timeout
- ✅ **Event Publishing** - Enrichment lifecycle events
- ✅ **Caching** - Configurable caching layer
- ✅ **Multi-Tenancy** - Native support for multiple tenants
- ✅ **Priority-Based Selection** - Control which provider is used

### What This Means
You **only write business logic** (enrichers). The library handles:
- ✅ REST API layer
- ✅ Routing and discovery
- ✅ Health checks
- ✅ Observability
- ✅ Resiliency
- ✅ Caching

---

## 🎯 Key Principles

### 1. One Enricher = One Type

Each enricher implements exactly ONE enrichment type for ONE tenant:

```java
@EnricherMetadata(type = "credit-report", tenantId = "550e8400-...")
public class ProviderACreditReportEnricher { ... }
```

### 2. Zero Boilerplate

No controllers, no configuration, no boilerplate:

```java
// Just create the enricher
@EnricherMetadata(...)
public class MyEnricher extends DataEnricher { ... }

// Done! Automatically available at /api/v1/enrichment/smart
```

### 3. Smart Routing

The system automatically routes requests to the correct enricher:

```bash
POST /api/v1/enrichment/smart
{"type": "credit-report", "tenantId": "550e8400-...", ...}
→ Routes to ProviderACreditReportEnricher
```

---

## 🔗 Related Documentation

- **[Data Jobs — Complete Guide](../data-jobs/guide.md)** - For orchestrated workflows
- **[Common Documentation](../common/README.md)** - Shared concepts, architecture, and utilities

---

## 📋 Need Help?

For questions, issues, or contributions, please refer to the main project documentation.

