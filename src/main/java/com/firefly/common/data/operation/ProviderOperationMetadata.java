package com.firefly.common.data.operation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Metadata for a provider-specific operation.
 *
 * <p>This class contains all the information needed to expose a provider operation as a REST endpoint,
 * including JSON schemas for request/response validation and documentation.</p>
 *
 * <p>Metadata is automatically extracted from the {@link Operation} annotation and the
 * operation's request/response DTO classes.</p>
 *
 * <p><b>Example Metadata:</b></p>
 * <pre>{@code
 * ProviderOperationMetadata.builder()
 *     .operationId("search-company")
 *     .description("Search for a company by name or tax ID")
 *     .method(RequestMethod.GET)
 *     .path("/search-company")
 *     .tags(new String[]{"lookup", "search"})
 *     .requiresAuth(true)
 *     .discoverable(true)
 *     .requestType(CompanySearchRequest.class)
 *     .responseType(CompanySearchResponse.class)
 *     .requestSchema(requestJsonSchema)
 *     .responseSchema(responseJsonSchema)
 *     .requestExample(exampleRequest)
 *     .responseExample(exampleResponse)
 *     .build();
 * }</pre>
 *
 * @see Operation
 * @see ProviderOperation
 */
@Value
@Builder
public class ProviderOperationMetadata {

    /**
     * Unique identifier for this operation within the provider.
     *
     * <p>This is used as part of the URL path. Should be kebab-case.</p>
     *
     * <p><b>Examples:</b> "search-company", "validate-tax-id", "get-credit-score"</p>
     */
    String operationId;

    /**
     * Human-readable description of what this operation does.
     *
     * <p>This is included in OpenAPI documentation and discovery responses.</p>
     */
    String description;

    /**
     * HTTP method for this operation.
     *
     * <p>Typically GET for lookups/searches, POST for complex queries.</p>
     */
    RequestMethod method;

    /**
     * The URL path for this operation (relative to the enricher's base path).
     *
     * <p>Should start with "/" and typically matches the operationId.</p>
     *
     * <p><b>Example:</b> "/search-company"</p>
     */
    String path;

    /**
     * Tags for categorizing this operation.
     *
     * <p>Used for grouping operations in documentation and discovery.</p>
     *
     * <p><b>Common Tags:</b> "lookup", "search", "validation", "metadata", "quick-query"</p>
     */
    String[] tags;

    /**
     * Whether this operation requires authentication.
     *
     * <p>This is informational and included in the operation metadata.
     * Actual authentication enforcement should be handled by Spring Security.</p>
     */
    boolean requiresAuth;

    /**
     * Whether this operation should be included in the discovery endpoint.
     *
     * <p>Set to false to hide internal/deprecated operations from discovery.</p>
     */
    boolean discoverable;

    /**
     * The Java class type of the request DTO.
     *
     * <p>This is used for deserialization and schema generation.</p>
     *
     * <p><b>Example:</b> {@code CompanySearchRequest.class}</p>
     */
    Class<?> requestType;

    /**
     * The Java class type of the response DTO.
     *
     * <p>This is used for serialization and schema generation.</p>
     *
     * <p><b>Example:</b> {@code CompanySearchResponse.class}</p>
     */
    Class<?> responseType;

    /**
     * JSON Schema for the request DTO.
     *
     * <p>This schema is used for:</p>
     * <ul>
     *   <li>Request validation</li>
     *   <li>OpenAPI documentation</li>
     *   <li>Client code generation</li>
     *   <li>Discovery endpoint responses</li>
     * </ul>
     *
     * <p>The schema is automatically generated from the request DTO class using
     * Jackson's JSON Schema module and {@code @Schema} annotations.</p>
     */
    JsonNode requestSchema;

    /**
     * JSON Schema for the response DTO.
     *
     * <p>This schema is used for:</p>
     * <ul>
     *   <li>Response validation (in tests)</li>
     *   <li>OpenAPI documentation</li>
     *   <li>Client code generation</li>
     *   <li>Discovery endpoint responses</li>
     * </ul>
     *
     * <p>The schema is automatically generated from the response DTO class using
     * Jackson's JSON Schema module and {@code @Schema} annotations.</p>
     */
    JsonNode responseSchema;

    /**
     * Example request object for documentation.
     *
     * <p>This is an instance of the request DTO with example values, used for
     * documentation and testing purposes.</p>
     *
     * <p>The example is automatically generated from {@code @Schema(example = "...")}
     * annotations on the request DTO fields.</p>
     */
    Object requestExample;

    /**
     * Example response object for documentation.
     *
     * <p>This is an instance of the response DTO with example values, used for
     * documentation and testing purposes.</p>
     *
     * <p>The example is automatically generated from {@code @Schema(example = "...")}
     * annotations on the response DTO fields.</p>
     */
    Object responseExample;

    /**
     * Gets the full endpoint path by combining base path and operation path.
     *
     * @param basePath the enricher's base path (e.g., "/api/v1/enrichment/credit-bureau")
     * @return the full endpoint path
     */
    public String getFullPath(String basePath) {
        // Remove trailing slash from base path if present
        String cleanBasePath = basePath.endsWith("/")
            ? basePath.substring(0, basePath.length() - 1)
            : basePath;

        // Ensure operation path starts with /
        String cleanPath = path.startsWith("/") ? path : "/" + path;

        return cleanBasePath + cleanPath;
    }

    /**
     * Converts Spring RequestMethod to HTTP method string.
     *
     * @return HTTP method as string (GET, POST, etc.)
     */
    public String getHttpMethod() {
        return method.name();
    }

    /**
     * Gets the simple name of the request type.
     *
     * @return the simple class name of the request DTO
     */
    public String getRequestTypeName() {
        return requestType != null ? requestType.getSimpleName() : "Object";
    }

    /**
     * Gets the simple name of the response type.
     *
     * @return the simple class name of the response DTO
     */
    public String getResponseTypeName() {
        return responseType != null ? responseType.getSimpleName() : "Object";
    }
}

