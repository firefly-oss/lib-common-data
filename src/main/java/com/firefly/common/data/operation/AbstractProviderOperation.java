package com.firefly.common.data.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.operation.schema.JsonSchemaGenerator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Abstract base class for provider-specific operations.
 *
 * <p>This class provides common functionality for all provider operations including:</p>
 * <ul>
 *   <li>Automatic metadata extraction from {@link Operation} annotation</li>
 *   <li>JSON Schema generation for request/response DTOs</li>
 *   <li>Example generation from {@code @Schema} annotations</li>
 *   <li>Request validation</li>
 *   <li>Error handling</li>
 *   <li>Logging</li>
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

    @Autowired(required = false)
    private JsonSchemaGenerator schemaGenerator;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

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

    private ProviderOperationMetadata metadata;
    private Class<TRequest> requestType;
    private Class<TResponse> responseType;

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
        log.debug("Executing operation {} with request: {}", 
            metadata.getOperationId(), request);

        return Mono.fromRunnable(() -> validateRequest(request))
            .then(doExecute(request))
            .doOnSuccess(response -> 
                log.debug("Operation {} completed successfully", metadata.getOperationId()))
            .doOnError(error -> 
                log.error("Operation {} failed: {}", metadata.getOperationId(), error.getMessage(), error));
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
     * Validates the request DTO.
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

