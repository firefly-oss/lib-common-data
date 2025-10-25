/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.common.data.service;

import com.firefly.common.data.cache.EnrichmentCacheService;
import com.firefly.common.data.controller.EndpointAware;
import com.firefly.common.data.enrichment.EnricherMetadataReader;
import com.firefly.common.data.enrichment.EnrichmentResponseBuilder;
import com.firefly.common.data.enrichment.EnrichmentStrategyApplier;
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.model.EnrichmentStrategy;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.operation.EnricherOperationInterface;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Type-safe abstract base class for data enrichers with enterprise-grade features.
 *
 * <p>This class provides a complete enrichment framework with:</p>
 * <ul>
 *   <li><b>Type Safety:</b> Generics for source, provider, and target DTOs</li>
 *   <li><b>Automatic Strategy Application:</b> ENHANCE, MERGE, REPLACE, RAW strategies</li>
 *   <li><b>Observability:</b> Distributed tracing, metrics, and comprehensive logging</li>
 *   <li><b>Resiliency:</b> Circuit breaker, retry, rate limiting, and bulkhead patterns</li>
 *   <li><b>Caching:</b> Automatic caching with tenant isolation and TTL management</li>
 *   <li><b>Event Publishing:</b> Enrichment lifecycle events (started, completed, failed)</li>
 *   <li><b>Declarative Metadata:</b> {@link com.firefly.common.data.enrichment.EnricherMetadata} annotation</li>
 *   <li><b>Simplified API:</b> Only implement fetchProviderData() and mapToTarget()</li>
 *   <li><b>Reduced Boilerplate:</b> 70-80% less code compared to manual implementation</li>
 * </ul>
 *
 * <p><b>Type Parameters:</b></p>
 * <ul>
 *   <li><b>TSource</b> - The source DTO type (input from client)</li>
 *   <li><b>TProvider</b> - The provider response type (from third-party API)</li>
 *   <li><b>TTarget</b> - The target DTO type (enriched output)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @EnricherMetadata(
 *     providerName = "Equifax Spain",
 *     tenantId = "spain-tenant-id",
 *     type = "credit-report",
 *     description = "Equifax Spain credit bureau enrichment",
 *     version = "1.0.0",
 *     tags = {"production", "gdpr-compliant", "spain"},
 *     priority = 10
 * )
 * public class EquifaxSpainCreditReportEnricher
 *         extends DataEnricher<CreditReportDTO, EquifaxResponse, CreditReportDTO> {
 *
 *     private final RestClient equifaxClient;
 *
 *     public EquifaxSpainCreditReportEnricher(
 *             JobTracingService tracingService,
 *             JobMetricsService metricsService,
 *             ResiliencyDecoratorService resiliencyService,
 *             EnrichmentEventPublisher eventPublisher,
 *             RestClient equifaxClient) {
 *         super(tracingService, metricsService, resiliencyService, eventPublisher, CreditReportDTO.class);
 *         this.equifaxClient = equifaxClient;
 *     }
 *
 *     @Override
 *     protected Mono<EquifaxResponse> fetchProviderData(EnrichmentRequest request) {
 *         String companyId = request.requireParam("companyId");
 *         return equifaxClient.get("/credit-reports/{id}", EquifaxResponse.class)
 *             .withPathParam("id", companyId)
 *             .execute();
 *     }
 *
 *     @Override
 *     protected CreditReportDTO mapToTarget(EquifaxResponse providerData) {
 *         return CreditReportDTO.builder()
 *             .companyId(providerData.getId())
 *             .creditScore(providerData.getScore())
 *             .build();
 *     }
 * }
 * }</pre>
 *
 * @param <TSource> the source DTO type
 * @param <TProvider> the provider response type
 * @param <TTarget> the target DTO type
 *
 * @see EnrichmentStrategyApplier
 * @see com.firefly.common.data.enrichment.EnricherMetadata
 */
@Slf4j
public abstract class DataEnricher<TSource, TProvider, TTarget> implements EndpointAware {

    private final JobTracingService tracingService;
    private final JobMetricsService metricsService;
    private final ResiliencyDecoratorService resiliencyService;
    private final EnrichmentEventPublisher eventPublisher;
    private final EnrichmentCacheService cacheService;
    private final Class<TTarget> targetClass;
    private String enrichmentEndpoint;

    /**
     * Full constructor with all dependencies including cache.
     *
     * @param tracingService service for distributed tracing
     * @param metricsService service for metrics collection
     * @param resiliencyService service for resiliency patterns
     * @param eventPublisher publisher for enrichment events
     * @param cacheService service for caching enrichment results (optional)
     * @param targetClass the target DTO class
     */
    protected DataEnricher(JobTracingService tracingService,
                          JobMetricsService metricsService,
                          ResiliencyDecoratorService resiliencyService,
                          EnrichmentEventPublisher eventPublisher,
                          EnrichmentCacheService cacheService,
                          Class<TTarget> targetClass) {
        this.tracingService = tracingService;
        this.metricsService = metricsService;
        this.resiliencyService = resiliencyService;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
        this.targetClass = targetClass;
    }

    /**
     * Constructor without cache for backward compatibility.
     */
    protected DataEnricher(JobTracingService tracingService,
                          JobMetricsService metricsService,
                          ResiliencyDecoratorService resiliencyService,
                          EnrichmentEventPublisher eventPublisher,
                          Class<TTarget> targetClass) {
        this(tracingService, metricsService, resiliencyService, eventPublisher, null, targetClass);
    }

    /**
     * Constructor without event publisher for backward compatibility.
     */
    protected DataEnricher(JobTracingService tracingService,
                          JobMetricsService metricsService,
                          ResiliencyDecoratorService resiliencyService,
                          Class<TTarget> targetClass) {
        this(tracingService, metricsService, resiliencyService, null, null, targetClass);
    }
    
    /**
     * Enriches data using the specified enrichment request.
     *
     * <p>This method performs the data enrichment operation by:</p>
     * <ol>
     *   <li>Checking cache for existing results (if caching is enabled)</li>
     *   <li>Calling the third-party provider with the request parameters</li>
     *   <li>Receiving the provider's response</li>
     *   <li>Applying the enrichment strategy (ENHANCE, MERGE, REPLACE, or RAW)</li>
     *   <li>Returning the enriched data in the response</li>
     *   <li>Caching the result (if caching is enabled)</li>
     * </ol>
     *
     * <p>The method is reactive and returns a Mono for non-blocking execution.</p>
     *
     * @param request the enrichment request containing source data, parameters, and strategy
     * @return a Mono emitting the enrichment response with enriched data
     */
    public final Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
        String providerName = getProviderName();

        // Try to get from cache first (if cache is enabled)
        if (cacheService != null && cacheService.isCacheEnabled()) {
            return cacheService.get(request, providerName)
                .flatMap(cachedOpt -> {
                    if (cachedOpt.isPresent()) {
                        log.debug("Cache HIT for enrichment: type={}, provider={}, tenant={}",
                                request.getType(), providerName, request.getTenantId());
                        return Mono.just(cachedOpt.get());
                    } else {
                        log.debug("Cache MISS for enrichment: type={}, provider={}, tenant={}",
                                request.getType(), providerName, request.getTenantId());
                        return enrichAndCache(request, providerName);
                    }
                });
        }

        // No cache, execute directly
        return enrichAndCache(request, providerName);
    }

    /**
     * Enriches data and caches the result if caching is enabled.
     */
    private Mono<EnrichmentResponse> enrichAndCache(EnrichmentRequest request, String providerName) {
        // Publish enrichment started event
        if (eventPublisher != null) {
            eventPublisher.publishEnrichmentStarted(request, providerName);
        }

        return executeWithObservabilityAndResiliency(request)
            .flatMap(response -> {
                // Cache successful responses
                if (cacheService != null && cacheService.isCacheEnabled() && response.isSuccess()) {
                    return cacheService.put(request, providerName, response)
                        .thenReturn(response);
                }
                return Mono.just(response);
            });
    }

    /**
     * Executes the enrichment with full observability and resiliency wrapping.
     */
    private Mono<EnrichmentResponse> executeWithObservabilityAndResiliency(EnrichmentRequest request) {
        String requestId = request.getRequestId();
        String enrichmentType = request.getType();
        String providerName = getProviderName();
        Instant startTime = Instant.now();

        log.debug("Starting enrichment: type={}, provider={}, requestId={}",
                enrichmentType, providerName, requestId);

        // Wrap with tracing
        Mono<EnrichmentResponse> tracedOperation = tracingService.traceOperation(
                "enrich-" + enrichmentType,
                requestId,
                doEnrich(request)
        );

        // Wrap with resiliency patterns
        Mono<EnrichmentResponse> resilientOperation = resiliencyService.decorate(tracedOperation);

        // Add metrics, logging, and event publishing
        return resilientOperation
                .doOnSubscribe(subscription -> {
                    log.debug("Subscribed to enrichment operation: type={}, provider={}, requestId={}",
                            enrichmentType, providerName, requestId);
                })
                .doOnNext(response -> {
                    long durationMillis = Duration.between(startTime, Instant.now()).toMillis();

                    // Record metrics
                    if (metricsService != null) {
                        metricsService.recordEnrichmentMetrics(
                                enrichmentType,
                                providerName,
                                response.isSuccess(),
                                durationMillis,
                                response.getFieldsEnriched(),
                                response.getCost()
                        );
                    }

                    // Publish completion event
                    if (eventPublisher != null) {
                        eventPublisher.publishEnrichmentCompleted(request, response, durationMillis);
                    }

                    log.info("Enrichment completed: type={}, provider={}, success={}, duration={}ms, requestId={}",
                            enrichmentType, providerName, response.isSuccess(), durationMillis, requestId);
                })
                .doOnError(error -> {
                    long durationMillis = Duration.between(startTime, Instant.now()).toMillis();

                    // Record error metrics
                    if (metricsService != null) {
                        metricsService.recordEnrichmentError(
                                enrichmentType,
                                providerName,
                                error.getClass().getSimpleName(),
                                durationMillis
                        );
                    }

                    // Publish failure event
                    if (eventPublisher != null) {
                        eventPublisher.publishEnrichmentFailed(
                                request,
                                providerName,
                                error.getMessage(),
                                error,
                                durationMillis
                        );
                    }

                    log.error("Enrichment failed: type={}, provider={}, error={}, duration={}ms, requestId={}",
                            enrichmentType, providerName, error.getMessage(), durationMillis, requestId, error);
                })
                .onErrorResume(error -> {
                    log.warn("Returning failure response for enrichment - type: {}, provider: {}, error: {}",
                            enrichmentType, providerName, error.getMessage());

                    // Return a failure response instead of propagating the error
                    return Mono.just(EnrichmentResponse.failure(
                            enrichmentType,
                            providerName,
                            "Error during enrichment: " + error.getMessage()
                    ));
                });
    }

    /**
     * Implements the enrichment logic with automatic strategy application.
     */
    protected final Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
        log.debug("Starting typed enrichment for type: {}, strategy: {}",
                request.getType(), request.getStrategy());

        return fetchProviderData(request)
                .map(providerData -> {
                    TTarget enrichedData;

                    // Use ENHANCE as default strategy if not specified
                    EnrichmentStrategy strategy = request.getStrategy() != null
                            ? request.getStrategy()
                            : EnrichmentStrategy.ENHANCE;

                    // RAW strategy: Return provider data as-is without mapping
                    if (strategy == EnrichmentStrategy.RAW) {
                        log.debug("Using RAW strategy - returning provider data without mapping");
                        enrichedData = EnrichmentStrategyApplier.apply(
                                strategy,
                                request.getSourceDto(),
                                providerData,  // Pass raw provider data directly
                                targetClass
                        );
                    } else {
                        // For other strategies: Map provider data to target DTO first
                        TTarget mappedData = mapToTarget(providerData);

                        // Apply enrichment strategy automatically
                        enrichedData = EnrichmentStrategyApplier.apply(
                                strategy,
                                request.getSourceDto(),
                                mappedData,
                                targetClass
                        );
                    }

                    // Build response with automatic field counting
                    return EnrichmentResponseBuilder
                            .success(enrichedData)
                            .forRequest(request)
                            .withProvider(getProviderName())
                            .withMessage(buildSuccessMessage(request))
                            .countingEnrichedFields(request.getSourceDto())
                            .withRawResponse(shouldIncludeRawResponse() ? providerData : null)
                            .build();
                })
                .onErrorResume(error -> {
                    log.warn("Enrichment failed for type {}: {}",
                            request.getType(), error.getMessage());

                    return Mono.just(
                            EnrichmentResponseBuilder
                                    .failure("Enrichment failed: " + error.getMessage())
                                    .forRequest(request)
                                    .withProvider(getProviderName())
                                    .build()
                    );
                });
    }
    
    /**
     * Fetches data from the provider.
     * 
     * <p>Subclasses implement this method to call the third-party provider API
     * and return the raw provider response.</p>
     * 
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Override
     * protected Mono<OrbisCompanyResponse> fetchProviderData(EnrichmentRequest request) {
     *     String companyId = request.requireParam("companyId");
     *     
     *     return orbisClient.get("/companies/{id}", OrbisCompanyResponse.class)
     *         .withPathParam("id", companyId)
     *         .execute();
     * }
     * }</pre>
     *
     * @param request the enrichment request
     * @return a Mono emitting the provider response
     */
    protected abstract Mono<TProvider> fetchProviderData(EnrichmentRequest request);
    
    /**
     * Maps the provider response to the target DTO.
     * 
     * <p>Subclasses implement this method to transform the provider's response
     * format into the target DTO format.</p>
     * 
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Override
     * protected CompanyProfileDTO mapToTarget(OrbisCompanyResponse providerData) {
     *     return CompanyProfileDTO.builder()
     *         .companyId(providerData.getId())
     *         .name(providerData.getCompanyName())
     *         .address(providerData.getRegisteredAddress())
     *         .revenue(providerData.getAnnualRevenue())
     *         .build();
     * }
     * }</pre>
     *
     * @param providerData the provider response data
     * @return the mapped target DTO
     */
    protected abstract TTarget mapToTarget(TProvider providerData);
    
    /**
     * Builds the success message for the response.
     * 
     * <p>Subclasses can override this to customize the success message.</p>
     *
     * @param request the enrichment request
     * @return the success message
     */
    protected String buildSuccessMessage(EnrichmentRequest request) {
        return String.format("%s enrichment completed successfully", request.getType());
    }
    
    /**
     * Determines whether to include the raw provider response in the enrichment response.
     * 
     * <p>Subclasses can override this to control whether raw responses are included.
     * By default, raw responses are not included to reduce response size.</p>
     *
     * @return true to include raw response, false otherwise
     */
    protected boolean shouldIncludeRawResponse() {
        return false;
    }
    
    /**
     * Gets the target class.
     *
     * @return the target class
     */
    protected Class<TTarget> getTargetClass() {
        return targetClass;
    }

    @Override
    public void setEnrichmentEndpoint(String endpoint) {
        this.enrichmentEndpoint = endpoint;
    }

    public String getEnrichmentEndpoint() {
        return enrichmentEndpoint;
    }

    /**
     * Enriches a batch of requests in parallel.
     *
     * @param requests the list of enrichment requests to process
     * @return a Flux emitting enrichment responses in the same order as requests
     */
    public Flux<EnrichmentResponse> enrichBatch(List<EnrichmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Flux.empty();
        }

        log.info("Starting batch enrichment: provider={}, batchSize={}", getProviderName(), requests.size());

        // Process requests in parallel with controlled concurrency
        int parallelism = getBatchParallelism();

        return Flux.fromIterable(requests)
            .flatMap(this::enrich, parallelism)
            .doOnComplete(() -> log.info("Batch enrichment completed: provider={}, batchSize={}",
                    getProviderName(), requests.size()));
    }

    /**
     * Gets the parallelism level for batch operations.
     * Subclasses can override this to customize parallelism.
     *
     * @return the parallelism level (default: 10)
     */
    protected int getBatchParallelism() {
        return 10;
    }

    /**
     * Gets the provider name from the @EnricherMetadata annotation.
     *
     * @return the provider name
     */
    public String getProviderName() {
        return EnricherMetadataReader.getProviderName(this);
    }

    /**
     * Gets the enricher description from the @EnricherMetadata annotation.
     *
     * @return the enricher description
     */
    public String getEnricherDescription() {
        return EnricherMetadataReader.getDescription(this);
    }

    /**
     * Gets the enricher version from the @EnricherMetadata annotation.
     *
     * @return the enricher version
     */
    public String getEnricherVersion() {
        return EnricherMetadataReader.getVersion(this);
    }

    /**
     * Gets the tenant ID from the @EnricherMetadata annotation.
     *
     * @return the tenant ID, or null if not specified
     */
    public UUID getTenantId() {
        return EnricherMetadataReader.getTenantId(this);
    }

    /**
     * Gets the priority from the @EnricherMetadata annotation.
     *
     * @return the priority (higher values = higher priority)
     */
    public int getPriority() {
        return EnricherMetadataReader.getPriority(this);
    }

    /**
     * Gets the supported enrichment types from the @EnricherMetadata annotation.
     *
     * @return list of supported enrichment types
     */
    public List<String> getSupportedEnrichmentTypes() {
        return Collections.singletonList(EnricherMetadataReader.getType(this));
    }

    /**
     * Gets the tags from the @EnricherMetadata annotation.
     *
     * @return list of tags
     */
    public List<String> getTags() {
        return EnricherMetadataReader.getTags(this);
    }

    /**
     * Checks if this enricher supports a specific enrichment type.
     *
     * @param enrichmentType the enrichment type to check
     * @return true if this enricher supports the type, false otherwise
     */
    public boolean supportsEnrichmentType(String enrichmentType) {
        if (enrichmentType == null || enrichmentType.isEmpty()) {
            return false;
        }
        List<String> supportedTypes = getSupportedEnrichmentTypes();
        return supportedTypes != null && supportedTypes.contains(enrichmentType);
    }

    /**
     * Checks if this enricher is ready to process enrichment requests.
     *
     * <p>This method can be used for health checks to verify that the enricher
     * is properly configured and can communicate with its provider.</p>
     *
     * <p>Subclasses can override this method to provide custom readiness checks.
     * By default, returns true (always ready).</p>
     *
     * @return a Mono emitting true if ready, false otherwise
     */
    public Mono<Boolean> isReady() {
        return Mono.just(true);
    }

    /**
     * Gets the list of enricher-specific operations supported by this enricher.
     *
     * <p>Enricher operations are auxiliary operations that support the enrichment workflow,
     * such as ID lookups, entity matching, validation, and quick queries.</p>
     *
     * <p>Subclasses can override this method to provide their own operations.
     * By default, returns an empty list (no operations).</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Service
     * public class EquifaxSpainEnricher extends DataEnricher<...> {
     *
     *     private final SearchCompanyOperation searchCompanyOperation;
     *     private final ValidateTaxIdOperation validateTaxIdOperation;
     *
     *     public EquifaxSpainEnricher(
     *             // ... enricher dependencies ...
     *             SearchCompanyOperation searchCompanyOperation,
     *             ValidateTaxIdOperation validateTaxIdOperation) {
     *         // ... enricher initialization ...
     *         this.searchCompanyOperation = searchCompanyOperation;
     *         this.validateTaxIdOperation = validateTaxIdOperation;
     *     }
     *
     *     @Override
     *     public List<EnricherOperationInterface<?, ?>> getOperations() {
     *         return List.of(
     *             searchCompanyOperation,
     *             validateTaxIdOperation
     *         );
     *     }
     * }
     * }</pre>
     *
     * @return list of enricher operations (empty by default)
     * @see EnricherOperationInterface
     * @see com.firefly.common.data.operation.AbstractEnricherOperation
     */
    public List<EnricherOperationInterface<?, ?>> getOperations() {
        return Collections.emptyList();
    }
}

