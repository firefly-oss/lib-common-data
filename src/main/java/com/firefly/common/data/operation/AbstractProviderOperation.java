package com.firefly.common.data.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.cache.OperationCacheService;
import com.firefly.common.data.config.DataEnrichmentProperties;
import com.firefly.common.data.event.OperationEventPublisher;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.operation.schema.JsonSchemaGenerator;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base class for provider-specific operations with enterprise-grade features.
 *
 * <p>This class provides common functionality for all provider operations including:</p>
 * <ul>
 *   <li><b>Observability:</b> Automatic tracing, metrics, and event publishing</li>
 *   <li><b>Resiliency:</b> Circuit breaker, retry, rate limiting, and timeout</li>
 *   <li><b>Caching:</b> Automatic caching with tenant isolation and TTL management</li>
 *   <li><b>Validation:</b> Jakarta Validation support with automatic validation</li>
 *   <li><b>Metadata:</b> Automatic extraction from {@link ProviderCustomOperation} annotation</li>
 *   <li><b>JSON Schema:</b> Automatic schema generation for request/response DTOs</li>
 *   <li><b>Error Handling:</b> Comprehensive error handling with structured responses</li>
 * </ul>
 *
 * <p><b>Example Implementation:</b></p>
 * <pre>{@code
 * @Operation(
 *     operationId = "search-company",
 *     description = "Search for a company by name or tax ID to obtain provider internal ID",
 *     method = RequestMethod.GET,
 *     tags = {"lookup", "search"}
 * )
 * public class SearchCompanyOperation 
 *         extends AbstractProviderOperation<CompanySearchRequest, CompanySearchResponse> {
 *
 *     private final RestClient bureauClient;
 *
 *     public SearchCompanyOperation(RestClient bureauClient) {
 *         this.bureauClient = bureauClient;
 *     }
 *
 *     @Override
 *     protected Mono<CompanySearchResponse> doExecute(CompanySearchRequest request) {
 *         return bureauClient.get("/search", CompanySearchResponse.class)
 *             .withQueryParam("name", request.getCompanyName())
 *             .withQueryParam("taxId", request.getTaxId())
 *             .execute()
 *             .map(result -> CompanySearchResponse.builder()
 *                 .providerId(result.getId())
 *                 .companyName(result.getName())
 *                 .taxId(result.getTaxId())
 *                 .confidence(result.getMatchScore())
 *                 .build());
 *     }
 *
 *     @Override
 *     protected void validateRequest(CompanySearchRequest request) {
 *         if (request.getCompanyName() == null && request.getTaxId() == null) {
 *             throw new IllegalArgumentException("Either companyName or taxId must be provided");
 *         }
 *     }
 * }
 * }</pre>
 *
 * @param <TRequest> the request DTO type
 * @param <TResponse> the response DTO type
 * @see ProviderOperation
 * @see Operation
 */
@Slf4j
public abstract class AbstractProviderOperation<TRequest, TResponse>
        implements ProviderOperation<TRequest, TResponse> {

    // Core dependencies
    @Autowired(required = false)
    private JsonSchemaGenerator schemaGenerator;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private Validator validator;

    // Observability dependencies
    @Autowired(required = false)
    private JobTracingService tracingService;

    @Autowired(required = false)
    private JobMetricsService metricsService;

    @Autowired(required = false)
    private OperationEventPublisher eventPublisher;

    // Resiliency dependencies
    @Autowired(required = false)
    private ResiliencyDecoratorService resiliencyService;

    // Caching dependencies
    @Autowired(required = false)
    private OperationCacheService cacheService;

    // Configuration
    @Autowired(required = false)
    private DataEnrichmentProperties properties;

    // Metadata
    private ProviderOperationMetadata metadata;
    private Class<TRequest> requestType;
    private Class<TResponse> responseType;
    private String providerName;

    /**
     * Sets the JSON schema generator (for testing purposes).
     * @param schemaGenerator the schema generator
     */
    public void setSchemaGenerator(JsonSchemaGenerator schemaGenerator) {
        this.schemaGenerator = schemaGenerator;
    }

    /**
     * Sets the object mapper (for testing purposes).
     * @param objectMapper the object mapper
     */
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Sets the validator (for testing purposes).
     * @param validator the validator
     */
    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Sets the tracing service (for testing purposes).
     * @param tracingService the tracing service
     */
    public void setTracingService(JobTracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * Sets the metrics service (for testing purposes).
     * @param metricsService the metrics service
     */
    public void setMetricsService(JobMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Sets the event publisher (for testing purposes).
     * @param eventPublisher the event publisher
     */
    public void setEventPublisher(OperationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Sets the resiliency service (for testing purposes).
     * @param resiliencyService the resiliency service
     */
    public void setResiliencyService(ResiliencyDecoratorService resiliencyService) {
        this.resiliencyService = resiliencyService;
    }

    /**
     * Sets the cache service (for testing purposes).
     * @param cacheService the cache service
     */
    public void setCacheService(OperationCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Sets the properties (for testing purposes).
     * @param properties the properties
     */
    public void setProperties(DataEnrichmentProperties properties) {
        this.properties = properties;
    }

    /**
     * Sets the provider name.
     * @param providerName the provider name
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * Initializes the operation metadata after bean construction.
     *
     * <p>This method extracts metadata from the {@link Operation} annotation
     * and generates JSON schemas for request/response DTOs.</p>
     *
     * <p>This method is public to allow manual initialization in tests.</p>
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void initializeMetadata() {
        // Extract generic type parameters
        Type genericSuperclass = getClass().getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            if (typeArguments.length >= 2) {
                this.requestType = (Class<TRequest>) extractClass(typeArguments[0]);
                this.responseType = (Class<TResponse>) extractClass(typeArguments[1]);
            }
        }

        // Initialize metadata from annotation
        initializeMetadataFromAnnotation();
    }

    /**
     * Extracts the raw class from a Type, handling both Class and ParameterizedType.
     */
    private Class<?> extractClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        throw new IllegalArgumentException("Cannot extract class from type: " + type);
    }

    /**
     * Initializes metadata after extracting type parameters.
     */
    private void initializeMetadataFromAnnotation() {

        // Extract metadata from @ProviderCustomOperation annotation
        ProviderCustomOperation annotation = getClass().getAnnotation(ProviderCustomOperation.class);
        if (annotation == null) {
            throw new IllegalStateException(
                "Operation class " + getClass().getSimpleName() +
                " must be annotated with @ProviderCustomOperation");
        }

        // Determine path (use operationId if path is empty)
        String path = annotation.path().isEmpty() 
            ? "/" + annotation.operationId() 
            : annotation.path();

        // Generate JSON schemas
        JsonNode requestSchema = null;
        JsonNode responseSchema = null;
        Object requestExample = null;
        Object responseExample = null;

        if (schemaGenerator != null) {
            try {
                requestSchema = schemaGenerator.generateSchema(requestType);
                responseSchema = schemaGenerator.generateSchema(responseType);
                requestExample = schemaGenerator.generateExample(requestType);
                responseExample = schemaGenerator.generateExample(responseType);
            } catch (Exception e) {
                log.warn("Failed to generate JSON schemas for operation {}: {}", 
                    annotation.operationId(), e.getMessage());
            }
        } else {
            log.warn("JsonSchemaGenerator not available - schemas will not be generated for operation {}", 
                annotation.operationId());
        }

        // Build metadata
        this.metadata = ProviderOperationMetadata.builder()
            .operationId(annotation.operationId())
            .description(annotation.description())
            .method(annotation.method())
            .path(path)
            .tags(annotation.tags())
            .requiresAuth(annotation.requiresAuth())
            .discoverable(annotation.discoverable())
            .requestType(requestType)
            .responseType(responseType)
            .requestSchema(requestSchema)
            .responseSchema(responseSchema)
            .requestExample(requestExample)
            .responseExample(responseExample)
            .build();

        log.info("Initialized provider operation: {} ({})", 
            annotation.operationId(), getClass().getSimpleName());
    }

    @Override
    public final Mono<TResponse> execute(TRequest request) {
        return executeWithContext(request, null, null);
    }

    /**
     * Executes the operation with full context (tenant ID and request ID).
     *
     * <p>This method provides the complete execution flow with:</p>
     * <ul>
     *   <li>Automatic validation (Jakarta Validation + custom validation)</li>
     *   <li>Cache lookup and storage</li>
     *   <li>Distributed tracing</li>
     *   <li>Metrics collection</li>
     *   <li>Event publishing</li>
     *   <li>Resiliency patterns (circuit breaker, retry, rate limiting)</li>
     * </ul>
     *
     * @param request the request DTO
     * @param tenantId the tenant ID (optional, for caching and events)
     * @param requestId the request ID (optional, for correlation)
     * @return a Mono emitting the response DTO
     */
    public final Mono<TResponse> executeWithContext(TRequest request, String tenantId, String requestId) {
        String operationId = metadata.getOperationId();
        String effectiveRequestId = requestId != null ? requestId : UUID.randomUUID().toString();
        String effectiveTenantId = tenantId != null ? tenantId : "default";

        log.debug("Executing operation {} with request: {}, tenantId: {}, requestId: {}",
            operationId, request, effectiveTenantId, effectiveRequestId);

        // Check cache first
        if (shouldUseCache()) {
            return cacheService.get(effectiveTenantId, providerName, operationId, request, responseType)
                .flatMap(cached -> {
                    if (cached.isPresent()) {
                        log.debug("Operation {} result served from cache", operationId);

                        // Publish cache hit event
                        if (shouldPublishEvents()) {
                            eventPublisher.publishOperationCached(
                                operationId, providerName, effectiveRequestId, effectiveTenantId);
                        }

                        return Mono.just(cached.get());
                    }

                    // Not in cache, execute and cache
                    return executeAndCache(request, effectiveTenantId, effectiveRequestId);
                });
        }

        // Cache disabled, execute directly
        return executeWithObservabilityAndResiliency(request, effectiveTenantId, effectiveRequestId);
    }

    /**
     * Executes the operation and caches the result.
     */
    private Mono<TResponse> executeAndCache(TRequest request, String tenantId, String requestId) {
        return executeWithObservabilityAndResiliency(request, tenantId, requestId)
            .flatMap(response -> {
                // Cache successful responses
                if (shouldUseCache()) {
                    return cacheService.put(tenantId, providerName, metadata.getOperationId(), request, response)
                        .thenReturn(response);
                }
                return Mono.just(response);
            });
    }

    /**
     * Executes the operation with full observability and resiliency wrapping.
     */
    private Mono<TResponse> executeWithObservabilityAndResiliency(TRequest request, String tenantId, String requestId) {
        String operationId = metadata.getOperationId();
        Instant startTime = Instant.now();

        // Publish operation started event
        if (shouldPublishEvents()) {
            eventPublisher.publishOperationStarted(operationId, providerName, requestId, tenantId);
        }

        // Validate request
        Mono<TResponse> operation = Mono.fromRunnable(() -> performValidation(request))
            .then(doExecute(request));

        // Wrap with tracing
        if (shouldUseTracing()) {
            operation = tracingService.traceOperation(
                "operation-" + operationId,
                requestId,
                operation
            );
        }

        // Wrap with resiliency patterns
        if (shouldUseResiliency()) {
            operation = resiliencyService.decorate(operation);
        }

        // Add metrics and event publishing
        return operation
            .doOnSuccess(response -> {
                long durationMillis = Duration.between(startTime, Instant.now()).toMillis();

                // Record metrics
                if (shouldUseMetrics()) {
                    recordOperationMetrics(operationId, true, durationMillis);
                }

                // Publish completion event
                if (shouldPublishEvents()) {
                    eventPublisher.publishOperationCompleted(
                        operationId, providerName, requestId, tenantId, durationMillis, false);
                }

                log.info("Operation completed: operation={}, provider={}, duration={}ms, requestId={}",
                    operationId, providerName, durationMillis, requestId);
            })
            .doOnError(error -> {
                long durationMillis = Duration.between(startTime, Instant.now()).toMillis();

                // Record error metrics
                if (shouldUseMetrics()) {
                    recordOperationMetrics(operationId, false, durationMillis);
                }

                // Publish failure event
                if (shouldPublishEvents()) {
                    eventPublisher.publishOperationFailed(
                        operationId, providerName, requestId, tenantId,
                        error.getMessage(), error, durationMillis);
                }

                log.error("Operation failed: operation={}, provider={}, duration={}ms, error={}, requestId={}",
                    operationId, providerName, durationMillis, error.getMessage(), requestId);
            });
    }

    /**
     * Executes the operation logic.
     *
     * <p>Subclasses must implement this method to provide the actual operation logic.</p>
     *
     * @param request the validated request DTO
     * @return a Mono emitting the response DTO
     */
    protected abstract Mono<TResponse> doExecute(TRequest request);

    /**
     * Performs validation on the request DTO.
     *
     * <p>This method performs both Jakarta Validation (if enabled) and custom validation.</p>
     *
     * @param request the request DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void performValidation(TRequest request) {
        // Jakarta Validation
        if (shouldUseValidation() && validator != null) {
            Set<ConstraintViolation<TRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Validation failed: " + errors);
            }
        }

        // Custom validation
        validateRequest(request);
    }

    /**
     * Validates the request DTO with custom logic.
     *
     * <p>Subclasses can override this method to provide custom validation logic.
     * The default implementation does nothing.</p>
     *
     * <p>Throw {@link IllegalArgumentException} or other runtime exceptions to indicate
     * validation failures.</p>
     *
     * @param request the request DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateRequest(TRequest request) {
        // Default: no validation
        // Subclasses can override to add custom validation
    }

    /**
     * Records operation metrics.
     */
    private void recordOperationMetrics(String operationId, boolean success, long durationMillis) {
        if (metricsService == null) {
            return;
        }

        // Use enrichment metrics with operation-specific tags
        metricsService.recordEnrichmentMetrics(
            "operation-" + operationId,
            providerName != null ? providerName : "unknown",
            success,
            durationMillis,
            null,
            null
        );
    }

    /**
     * Checks if caching should be used.
     */
    private boolean shouldUseCache() {
        return cacheService != null
            && cacheService.isCacheEnabled()
            && properties != null
            && properties.getOperations().isCacheEnabled();
    }

    /**
     * Checks if tracing should be used.
     */
    private boolean shouldUseTracing() {
        return tracingService != null
            && properties != null
            && properties.getOperations().isObservabilityEnabled();
    }

    /**
     * Checks if metrics should be used.
     */
    private boolean shouldUseMetrics() {
        return metricsService != null
            && properties != null
            && properties.getOperations().isObservabilityEnabled();
    }

    /**
     * Checks if events should be published.
     */
    private boolean shouldPublishEvents() {
        return eventPublisher != null
            && properties != null
            && properties.getOperations().isPublishEvents();
    }

    /**
     * Checks if resiliency should be used.
     */
    private boolean shouldUseResiliency() {
        return resiliencyService != null
            && properties != null
            && properties.getOperations().isResiliencyEnabled();
    }

    /**
     * Checks if validation should be used.
     */
    private boolean shouldUseValidation() {
        return properties != null
            && properties.getOperations().isValidationEnabled();
    }

    @Override
    public final ProviderOperationMetadata getMetadata() {
        return metadata;
    }

    @Override
    public final Class<TRequest> getRequestType() {
        return requestType;
    }

    @Override
    public final Class<TResponse> getResponseType() {
        return responseType;
    }

    /**
     * Gets the ObjectMapper for JSON serialization/deserialization.
     *
     * @return the ObjectMapper instance
     */
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

