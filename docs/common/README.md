# Common Documentation

This section contains documentation that applies to **both Data Jobs and Data Enrichers** - shared concepts, architecture, configuration, and utilities.

## 📖 What's in Common Documentation?

These topics are relevant whether you're building data jobs, data enrichers, or both:
- Architecture and design patterns
- Configuration and properties
- Observability (tracing, metrics, health checks)
- Resiliency patterns (circuit breaker, retry, rate limiting)
- Logging and monitoring
- Testing strategies
- MapStruct mappers for data transformation

---

## 📚 Core Documentation

### Architecture & Design

**[Architecture Overview](architecture.md)** - Deep dive into the library's architecture
- Hexagonal architecture (Ports and Adapters)
- Core components
- Integration architecture
- Data flow
- Design patterns
- Component interactions

**Topics covered**:
- ✅ Dependency inversion and interface segregation
- ✅ Pluggable adapters for different platforms
- ✅ Reactive programming with Project Reactor
- ✅ Event-driven architecture integration
- ✅ CQRS pattern support
- ✅ SAGA pattern for distributed transactions

---

### Configuration

**[Configuration Reference](configuration.md)** - Comprehensive configuration guide
- Application properties
- Environment-specific configuration (dev, staging, prod)
- Orchestrator configuration
- Enrichment configuration
- EDA (Event-Driven Architecture) configuration
- CQRS configuration
- Transactional engine configuration
- Observability configuration
- Resiliency configuration

**Topics covered**:
- ✅ YAML configuration examples
- ✅ Property reference
- ✅ Environment profiles
- ✅ Feature toggles
- ✅ Connection configuration
- ✅ Timeout and retry settings

---

### Observability

**[Observability Guide](observability.md)** - Monitoring and tracing
- Distributed tracing with Micrometer
- Metrics collection
- Health checks
- Custom metrics
- Integration with monitoring systems (Prometheus, Grafana, Zipkin, Jaeger)

**Topics covered**:
- ✅ Automatic tracing for all operations
- ✅ Custom trace spans
- ✅ Metrics for jobs and enrichments
- ✅ Health check endpoints
- ✅ Performance monitoring
- ✅ Error tracking

---

### Resiliency

**[Resiliency Patterns](resiliency.md)** - Building resilient applications
- Circuit Breaker pattern
- Retry logic with exponential backoff
- Rate limiting
- Bulkhead isolation
- Timeout configuration
- Fallback strategies

**Topics covered**:
- ✅ Resilience4j integration
- ✅ Automatic resiliency for jobs and enrichments
- ✅ Configuration examples
- ✅ Custom resiliency strategies
- ✅ Monitoring resiliency metrics

---

### Logging

**[Logging Guide](logging.md)** - Comprehensive logging
- Structured JSON logging
- Log levels and configuration
- Contextual logging
- Log aggregation
- Best practices

**Topics covered**:
- ✅ Automatic logging for all operations
- ✅ Request/response logging
- ✅ Error logging with stack traces
- ✅ Performance logging
- ✅ Integration with log aggregation systems (ELK, Splunk)

---

### Testing

**[Testing Guide](testing.md)** - Testing strategies and examples
- Unit testing
- Integration testing
- Testing reactive code with StepVerifier
- Mocking external dependencies
- Test configuration
- Best practices

**Topics covered**:
- ✅ Testing data jobs
- ✅ Testing data enrichers
- ✅ Testing custom operations
- ✅ Testing with MockWebServer
- ✅ Testing resiliency patterns
- ✅ Test coverage strategies

---

### Data Transformation

**[MapStruct Mappers](mappers.md)** - Guide to result transformation
- MapStruct basics
- Mapping provider responses to DTOs
- Custom mapping logic
- Collection mapping
- Nested object mapping
- Best practices

**Topics covered**:
- ✅ Setting up MapStruct
- ✅ Creating mappers
- ✅ Field mapping
- ✅ Custom converters
- ✅ Testing mappers

---

### API Reference

**[API Reference](api-reference.md)** - Complete API documentation
- REST endpoints
- Request/response formats
- Error codes
- OpenAPI/Swagger documentation

**Topics covered**:
- ✅ Data job endpoints
- ✅ Data enrichment endpoints
- ✅ Discovery endpoints
- ✅ Health check endpoints
- ✅ Custom operation endpoints

---

### Examples

**[Examples](examples.md)** - Real-world usage patterns
- Basic job execution
- Polling patterns
- Error handling
- Event-driven workflows
- SAGA integration
- Advanced scenarios

**Topics covered**:
- ✅ Complete working examples
- ✅ Best practices
- ✅ Common patterns
- ✅ Integration scenarios

---

## 🎯 Common Tasks

### I want to...

**Understand the architecture**
→ See [Architecture Overview](architecture.md)

**Configure my application**
→ See [Configuration Reference](configuration.md)

**Add distributed tracing**
→ See [Observability Guide](observability.md)

**Implement retry and circuit breaker**
→ See [Resiliency Patterns](resiliency.md)

**Set up structured logging**
→ See [Logging Guide](logging.md)

**Write tests for my code**
→ See [Testing Guide](testing.md)

**Transform data with MapStruct**
→ See [MapStruct Mappers](mappers.md)

**See complete examples**
→ See [Examples](examples.md)

---

## 🔗 Related Documentation

- **[Data Jobs](../data-jobs/README.md)** - For orchestrated workflows
- **[Data Enrichers](../data-enrichers/README.md)** - For third-party provider integration

---

## 📋 Document Index

### Architecture & Design
- [architecture.md](architecture.md) - Hexagonal architecture and design patterns

### Configuration & Setup
- [configuration.md](configuration.md) - Comprehensive configuration reference

### Observability & Monitoring
- [observability.md](observability.md) - Distributed tracing, metrics, health checks
- [logging.md](logging.md) - Comprehensive logging guide

### Resiliency & Reliability
- [resiliency.md](resiliency.md) - Circuit breaker, retry, rate limiting patterns

### Development & Testing
- [testing.md](testing.md) - Testing strategies and examples
- [mappers.md](mappers.md) - MapStruct transformation guide

### Reference
- [api-reference.md](api-reference.md) - Complete API documentation
- [examples.md](examples.md) - Real-world usage patterns
- [persistence.md](persistence.md) - Data persistence and audit trail

