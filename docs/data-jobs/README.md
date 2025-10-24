# Data Jobs Documentation

This section contains all documentation related to **Data Jobs** - orchestrated workflows for processing data from external systems.

## 📖 What are Data Jobs?

Data Jobs are for executing complex, multi-step workflows that interact with external systems (databases, APIs, file systems, etc.). They support both **asynchronous** (long-running, multi-stage) and **synchronous** (quick, single-stage) execution models.

### Use Cases
- Processing large datasets from external sources
- Running ETL (Extract, Transform, Load) operations
- Coordinating multi-step business processes
- Batch processing and scheduled tasks
- Quick data validation and transformation (sync jobs)

---

## 🚀 Quick Start Guides

### For Beginners
- **[Getting Started with Data Jobs](getting-started.md)** - Basic setup and first implementation
- **[Step-by-Step Guide: Building a Data Job Microservice](step-by-step-guide.md)** - Complete guide from scratch
  - Project setup with multi-module Maven
  - Configuration (dev vs prod)
  - Creating job orchestrators (MOCK, AWS Step Functions, Apache Airflow)
  - Creating multiple data job services
  - Creating controllers
  - Testing and troubleshooting

### For Specific Scenarios
- **[Synchronous Jobs Guide](sync-jobs.md)** - For quick operations (< 30 seconds)
- **[Multiple Jobs Example](multiple-jobs-example.md)** - Real-world example with 3 different job types

---

## 📚 Core Concepts

### Job Types

#### Asynchronous Jobs (Multi-Stage)
For long-running tasks that may take minutes or hours:
- **START** - Initialize the job
- **CHECK** - Monitor job progress
- **COLLECT** - Gather results
- **RESULT** - Return final data
- **STOP** - Clean up resources

**Best for**: Large data processing, external API calls, batch operations

#### Synchronous Jobs (Single-Stage)
For quick operations that complete in seconds:
- **EXECUTE** - Single operation that returns immediately

**Best for**: Data validation, quick transformations, simple lookups

### Orchestrators
- **MOCK** - For development and testing
- **AWS Step Functions** - For AWS-based deployments
- **Apache Airflow** - For complex workflow orchestration
- **Custom** - Implement your own orchestrator

---

## 📖 Reference Documentation

### Architecture & Design
- **[Job Lifecycle](job-lifecycle.md)** - Detailed explanation of job stages and data flow
- **[Architecture Overview](../common/architecture.md)** - Hexagonal architecture and design patterns

### Configuration
- **[Configuration Reference](../common/configuration.md)** - Comprehensive configuration options
- **[Observability](../common/observability.md)** - Distributed tracing, metrics, and health checks
- **[Resiliency](../common/resiliency.md)** - Circuit breaker, retry, rate limiting patterns
- **[Logging](../common/logging.md)** - Comprehensive logging for all job lifecycle phases

### Advanced Topics
- **[SAGA Integration](saga-integration.md)** - Distributed transactions and step events
- **[MapStruct Mappers](../common/mappers.md)** - Guide to result transformation
- **[Testing Guide](../common/testing.md)** - Testing strategies and examples

---

## 🎯 Common Tasks

### I want to...

**Create a new data job microservice**
→ See [Step-by-Step Guide: Building a Data Job Microservice](step-by-step-guide.md)

**Implement a quick synchronous job**
→ See [Synchronous Jobs Guide](sync-jobs.md)

**Add multiple jobs to one microservice**
→ See [Multiple Jobs Example](multiple-jobs-example.md)

**Understand the job lifecycle**
→ See [Job Lifecycle](job-lifecycle.md)

**Configure orchestrators**
→ See [Configuration Reference](../common/configuration.md)

**Add distributed tracing**
→ See [Observability](../common/observability.md)

**Implement SAGA patterns**
→ See [SAGA Integration](saga-integration.md)

---

## 🔗 Related Documentation

- **[Data Enrichers](../data-enrichers/README.md)** - For integrating with third-party data providers
- **[Common Documentation](../common/README.md)** - Shared concepts, architecture, and utilities

---

## 📋 Document Index

### Getting Started
- [getting-started.md](getting-started.md) - Basic setup and first implementation
- [step-by-step-guide.md](step-by-step-guide.md) - Complete guide from scratch
- [sync-jobs.md](sync-jobs.md) - Synchronous jobs guide
- [multiple-jobs-example.md](multiple-jobs-example.md) - Multiple jobs example

### Core Concepts
- [job-lifecycle.md](job-lifecycle.md) - Job stages and data flow

### Advanced Topics
- [saga-integration.md](saga-integration.md) - Distributed transactions

### Reference
- See [Common Documentation](../common/README.md) for shared reference materials

