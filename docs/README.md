# lib-common-data Documentation

Welcome to the **lib-common-data** library documentation! This library provides a standardized, production-ready foundation for building data processing microservices in the Firefly ecosystem.

## 📖 What is lib-common-data?

`lib-common-data` is a Spring Boot library that provides two main capabilities:

### 1. **Data Jobs** - Orchestrated Workflows
For executing complex, multi-step workflows that interact with external systems (databases, APIs, file systems, etc.).

**Use Cases:**
- Processing large datasets from external sources
- Running ETL (Extract, Transform, Load) operations
- Coordinating multi-step business processes
- Batch processing and scheduled tasks

**Learn More:** [Data Jobs — Complete Guide →](data-jobs/guide.md)

### 2. **Data Enrichers** - Third-Party Provider Integration
For fetching and integrating data from external third-party providers (credit bureaus, financial data providers, business intelligence services, etc.).

**Use Cases:**
- Enriching customer data with credit scores
- Adding financial metrics from market data providers
- Augmenting company profiles with business intelligence
- Validating addresses or tax IDs with government services

**Learn More:** [Data Enrichers Documentation →](data-enrichers/README.md)

---

## 🚀 Quick Start

### Choose Your Path

**I want to build a data job microservice**
→ See the [Data Jobs — Complete Guide](data-jobs/guide.md)

**I want to build a data enricher microservice**
→ See [Data Enrichers - Step-by-Step Guide](data-enrichers/enricher-microservice-guide.md)

**I want to understand the architecture first**
→ See [Architecture Overview](common/architecture.md)

**I want to see code examples**
→ See [Examples](common/examples.md)

---

## 📚 Documentation Structure

### [Data Jobs — Complete Guide](data-jobs/guide.md)
Documentation for building orchestrated workflows (async and sync) in one place.

### [Data Enrichers](data-enrichers/README.md)
Documentation for integrating with third-party providers:
- **[Step-by-Step Guide](data-enrichers/enricher-microservice-guide.md)** - ⭐ Complete guide from scratch
- **[Data Enrichment Reference](data-enrichers/data-enrichment.md)** - Complete reference guide

### [Common Documentation](common/README.md)
Shared concepts, architecture, and utilities:
- **[Architecture Overview](common/architecture.md)** - Hexagonal architecture and design patterns
- **[Configuration Reference](common/configuration.md)** - Comprehensive configuration options
- **[Observability](common/observability.md)** - Distributed tracing, metrics, health checks
- **[Resiliency](common/resiliency.md)** - Circuit breaker, retry, rate limiting patterns
- **[Logging](common/logging.md)** - Comprehensive logging guide
- **[Testing](common/testing.md)** - Testing strategies and examples
- **[MapStruct Mappers](common/mappers.md)** - Data transformation guide
- **[API Reference](common/api-reference.md)** - Complete API documentation
- **[Examples](common/examples.md)** - Real-world usage patterns

---

## 🎯 Common Tasks

### Getting Started
- **[Install the library](#installation)** - Add to your project
- **[Create your first data job](data-jobs/guide.md)** - Complete guide (async and sync)
- **[Create your first enricher](data-enrichers/enricher-microservice-guide.md)** - Step-by-step guide

### Configuration
- **[Configure orchestrators](common/configuration.md#orchestration-configuration)** - Airflow, AWS Step Functions, Mock
- **[Configure observability](common/observability.md)** - Tracing, metrics, health checks
- **[Configure resiliency](common/resiliency.md)** - Circuit breaker, retry, rate limiting

### Advanced Topics
- **[Implement SAGA patterns](data-jobs/guide.md#saga-and-step-events)** - Distributed transactions
- **[Create custom operations](data-enrichers/data-enrichment.md#provider-specific-custom-operations)** - Provider-specific workflows
- **[Test your code](common/testing.md)** - Unit and integration testing

---

## 📦 Installation

### Maven

Add the following to your `pom.xml`:

```xml
<!-- Use Firefly's parent POM for standardized dependency management -->
<parent>
    <groupId>com.firefly</groupId>
    <artifactId>lib-parent-pom</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath/>
</parent>

<dependencies>
    <!-- Firefly Common Data Library -->
    <dependency>
        <groupId>com.firefly</groupId>
        <artifactId>lib-common-data</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### Prerequisites

- **Java 21+** - Required for virtual threads and modern language features
- **Maven 3.8+** or Gradle 7+
- **Spring Boot 3.x** knowledge
- **Reactive programming** familiarity (Project Reactor)

---

## ✨ Key Features

### Automatic Observability
- ✅ **Distributed Tracing** - Micrometer integration with Zipkin/Jaeger
- ✅ **Metrics Collection** - Automatic metrics for all operations
- ✅ **Health Checks** - Ready-to-use health check endpoints
- ✅ **Comprehensive Logging** - Structured JSON logging

### Automatic Resiliency
- ✅ **Circuit Breaker** - Prevent cascading failures
- ✅ **Retry Logic** - Exponential backoff with jitter
- ✅ **Rate Limiting** - Protect external APIs
- ✅ **Bulkhead Isolation** - Isolate failures

### Event-Driven Architecture
- ✅ **Automatic Event Publishing** - Job and enrichment lifecycle events
- ✅ **CQRS Integration** - Command/Query separation
- ✅ **SAGA Support** - Distributed transaction patterns

### Developer Experience
- ✅ **Abstract Base Classes** - Minimal boilerplate code
- ✅ **Type-Safe APIs** - Compile-time safety
- ✅ **Reactive Programming** - Non-blocking operations with Project Reactor
- ✅ **Comprehensive Testing** - Test utilities and examples

---

## 🏗️ Architecture

The library follows **Hexagonal Architecture** (Ports and Adapters):

```
┌────────────────────────────────────────────────────────┐
│                   Your Application                     │
│                                                        │
│  ┌──────────────┐              ┌──────────────┐        │
│  │  Data Jobs   │              │   Enrichers  │        │
│  │              │              │              │        │
│  │  - Async     │              │  - Credit    │        │
│  │  - Sync      │              │  - Company   │        │
│  └──────────────┘              └──────────────┘        │
│         ↓                              ↓               │
│  ┌──────────────────────────────────────────────┐      │
│  │         lib-common-data (Core)               │      │
│  │                                              │      │
│  │  - Abstract base classes                     │      │
│  │  - Observability (automatic)                 │      │
│  │  - Resiliency (automatic)                    │      │
│  │  - Event publishing (automatic)              │      │
│  └──────────────────────────────────────────────┘      │
│         ↓                              ↓               │
│  ┌──────────────┐              ┌──────────────┐        │
│  │ Orchestrators│              │   Providers  │        │
│  │              │              │              │        │
│  │  - Airflow   │              │  - REST APIs │        │
│  │  - AWS SF    │              │  - SOAP APIs │        │
│  │  - Mock      │              │  - gRPC APIs │        │
│  └──────────────┘              └──────────────┘        │
└────────────────────────────────────────────────────────┘
```

**Learn More:** [Architecture Overview](common/architecture.md)

---

## 📖 Documentation Index

### By Feature
- **[Data Jobs — Complete Guide](data-jobs/guide.md)** - Orchestrated workflows
- **[Data Enrichers](data-enrichers/README.md)** - Third-party provider integration

### By Topic
- **[Architecture](common/architecture.md)** - Design patterns and principles
- **[Configuration](common/configuration.md)** - All configuration options
- **[Observability](common/observability.md)** - Monitoring and tracing
- **[Resiliency](common/resiliency.md)** - Fault tolerance patterns
- **[Testing](common/testing.md)** - Testing strategies
- **[Examples](common/examples.md)** - Real-world code examples

---

## 🤝 Support

For questions, issues, or contributions:
- Check the [Examples](common/examples.md) for common patterns
- Review the [API Reference](common/api-reference.md) for detailed API docs
- See the [Architecture Overview](common/architecture.md) for design decisions

---

## 📝 License

Copyright © 2024 Firefly. All rights reserved.

