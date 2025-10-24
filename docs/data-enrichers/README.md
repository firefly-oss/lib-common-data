# Data Enrichers Documentation

This section contains all documentation related to **Data Enrichers** - integrating and enriching data from third-party providers.

## 📖 What are Data Enrichers?

Data Enrichers are for fetching and integrating data from external third-party providers (credit bureaus, financial data providers, business intelligence services, etc.). They standardize how you call external APIs and merge their data with yours.

### Use Cases
- Enriching customer data with credit scores from credit bureaus
- Adding financial metrics from market data providers
- Augmenting company profiles with business intelligence data
- Validating addresses or tax IDs with government services
- Fetching real-time market data or exchange rates

---

## 🚀 Quick Start Guides

### For Beginners
- **[Getting Started with Data Enrichers](getting-started.md)** - Basic setup and first implementation
- **[Step-by-Step Guide: Building a Data Enricher Microservice](enricher-microservice-guide.md)** - ⭐ **Complete guide from scratch**
  - Multi-module Maven project structure (Domain, Client, Enricher, Application)
  - Using Firefly's lib-parent-pom
  - Building enrichers WITHOUT custom operations
  - Building enrichers WITH custom operations
  - Comprehensive testing and deployment
  - Production-ready examples

### For Specific Scenarios
- **[Data Enrichment Reference](data-enrichment.md)** - Complete reference guide
  - Enrichment strategies (ENHANCE, MERGE, REPLACE, RAW)
  - Provider-specific custom operations
  - Enricher utilities and helpers
  - Best practices

---

## 📚 Core Concepts

### Enrichment Strategies

#### ENHANCE
Fills only null/empty fields from provider data, preserving existing data.
**Use when**: You have partial data and want to fill gaps without overwriting.

#### MERGE
Combines source and provider data, with provider data taking precedence on conflicts.
**Use when**: You want the most complete data from both sources.

#### REPLACE
Completely replaces source data with provider data (transformed to your DTO format).
**Use when**: Provider data is authoritative and should override everything.

#### RAW
Returns raw provider data without transformation.
**Use when**: You need the original provider response format.

### Provider-Specific Custom Operations

Many providers require auxiliary operations before enrichment:
- **ID Lookups** - Search for internal provider IDs
- **Entity Matching** - Fuzzy match companies or individuals
- **Validation** - Validate identifiers (Tax ID, VAT, etc.)
- **Metadata Retrieval** - Get provider-specific configuration

The library provides a **class-based operation system** with:
- ✅ Automatic REST endpoint exposure
- ✅ JSON Schema generation from DTOs
- ✅ Type-safe request/response handling
- ✅ Automatic discovery endpoint
- ✅ Request validation

---

## 🏗️ Architecture Patterns

### Multi-Module Maven Structure

Recommended structure for production-ready enricher microservices:

```
enricher-microservice/
├── domain/          # DTOs, models, enums (no external dependencies)
├── client/          # REST/SOAP client using lib-common-client
├── enricher/        # Enricher implementations, controllers, operations
└── application/     # Spring Boot application
```

**Benefits**:
- ✅ Separation of concerns
- ✅ Reusability (domain and client modules can be shared)
- ✅ Testability (test each module independently)
- ✅ Maintainability (changes isolated to specific modules)

### Microservice Organization

**One microservice per provider** with multiple enrichers:
- `core-data-provider-a-enricher` - All Provider A enrichers
  - `ProviderASpainCreditEnricher`
  - `ProviderAUSACompanyEnricher`
  - `ProviderAFranceRiskEnricher`

Each enricher has its own dedicated REST endpoint and can have custom operations.

---

## 📖 Reference Documentation

### Getting Started
- **[Getting Started with Data Enrichers](getting-started.md)** - Basic setup
- **[Step-by-Step Guide: Building a Data Enricher Microservice](enricher-microservice-guide.md)** - Complete guide

### Core Documentation
- **[Data Enrichment Reference](data-enrichment.md)** - Complete reference
  - How it works
  - Architecture
  - Implementation guide
  - Provider-specific operations
  - Enricher utilities
  - Configuration
  - Examples
  - Best practices

### Shared Documentation
- **[Architecture Overview](../common/architecture.md)** - Hexagonal architecture
- **[Configuration Reference](../common/configuration.md)** - Configuration options
- **[Observability](../common/observability.md)** - Tracing, metrics, health checks
- **[Resiliency](../common/resiliency.md)** - Circuit breaker, retry, rate limiting
- **[Logging](../common/logging.md)** - Comprehensive logging
- **[Testing Guide](../common/testing.md)** - Testing strategies

---

## 🎯 Common Tasks

### I want to...

**Create a new data enricher microservice from scratch**
→ See [Step-by-Step Guide: Building a Data Enricher Microservice](enricher-microservice-guide.md)

**Build a simple enricher (no custom operations)**
→ See [Step-by-Step Guide - Section 8](enricher-microservice-guide.md#8-building-enrichers-without-custom-operations)

**Build an enricher with custom operations**
→ See [Step-by-Step Guide - Section 9](enricher-microservice-guide.md#9-building-enrichers-with-custom-operations)

**Understand enrichment strategies**
→ See [Data Enrichment Reference - Enrichment Strategies](data-enrichment.md#enrichment-strategies)

**Implement provider-specific operations**
→ See [Data Enrichment Reference - Provider-Specific Operations](data-enrichment.md#provider-specific-custom-operations)

**Use lib-common-client for API calls**
→ See [Step-by-Step Guide - Section 5](enricher-microservice-guide.md#5-creating-the-client-module)

**Set up multi-module Maven project**
→ See [Step-by-Step Guide - Section 2](enricher-microservice-guide.md#2-multi-module-maven-structure)

**Configure observability and resiliency**
→ See [Observability](../common/observability.md) and [Resiliency](../common/resiliency.md)

---

## 🔗 Related Documentation

- **[Data Jobs](../data-jobs/README.md)** - For orchestrated workflows
- **[Common Documentation](../common/README.md)** - Shared concepts, architecture, and utilities

---

## 📋 Document Index

### Getting Started
- [getting-started.md](getting-started.md) - Basic setup and first implementation
- [enricher-microservice-guide.md](enricher-microservice-guide.md) - ⭐ Complete step-by-step guide

### Reference
- [data-enrichment.md](data-enrichment.md) - Complete reference guide

### Shared Documentation
- See [Common Documentation](../common/README.md) for architecture, configuration, observability, resiliency, logging, and testing

