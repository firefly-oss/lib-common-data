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
import com.firefly.common.data.event.EnrichmentEventPublisher;
import com.firefly.common.data.model.EnrichmentRequest;
import com.firefly.common.data.model.EnrichmentResponse;
import com.firefly.common.data.observability.JobMetricsService;
import com.firefly.common.data.observability.JobTracingService;
import com.firefly.common.data.resiliency.ResiliencyDecoratorService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Abstract base class for DataEnricher implementations that provides
 * built-in observability, resiliency, and event publishing features.
 * 
 * <p>This class automatically wraps all enrichment operations with:</p>
 * <ul>
 *   <li>Distributed tracing via Micrometer</li>
 *   <li>Metrics collection (enrichment time, success/failure rates, provider costs)</li>
 *   <li>Circuit breaker, retry, rate limiting, and bulkhead patterns</li>
 *   <li>Event publishing (enrichment started, completed, failed)</li>
 *   <li>Comprehensive logging with enrichment context</li>
 *   <li>Automatic error handling and recovery</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Service
 * public class FinancialDataEnricher extends AbstractResilientDataEnricher {
 *
 *     private final RestClient financialDataClient;
 *
 *     public FinancialDataEnricher(
 *             JobTracingService tracingService,
 *             JobMetricsService metricsService,
 *             ResiliencyDecoratorService resiliencyService,
 *             EnrichmentEventPublisher eventPublisher,
 *             RestClient financialDataClient) {
 *         super(tracingService, metricsService, resiliencyService, eventPublisher);
 *         this.financialDataClient = financialDataClient;
 *     }
 *
 *     @Override
 *     protected Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request) {
 *         String companyId = (String) request.getParameters().get("companyId");
 *
 *         return financialDataClient.get("/companies/{id}", FinancialDataResponse.class)
 *             .withPathParam("id", companyId)
 *             .execute()
 *             .map(financialData -> {
 *                 CompanyProfileDTO enriched = applyEnrichmentStrategy(
 *                     request.getStrategy(),
 *                     request.getSourceDto(),
 *                     financialData
 *                 );
 *
 *                 return EnrichmentResponse.success(
 *                     enriched,
 *                     getProviderName(),
 *                     request.getEnrichmentType(),
 *                     "Company data enriched successfully"
 *                 );
 *             });
 *     }
 *
 *     @Override
 *     public String getProviderName() {
 *         return "Financial Data Provider";
 *     }
 *
 *     @Override
 *     public String[] getSupportedEnrichmentTypes() {
 *         return new String[]{"company-profile", "company-financials"};
 *     }
 *
 *     @Override
 *     public String getEnricherDescription() {
 *         return "Enriches company data with financial and corporate information";
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Subclass Requirements:</b></p>
 * <ul>
 *   <li>Implement {@link #doEnrich(EnrichmentRequest)} with your enrichment logic</li>
 *   <li>Override {@link #getProviderName()} to provide the provider name</li>
 *   <li>Override {@link #getSupportedEnrichmentTypes()} to list supported types</li>
 *   <li>Optionally override {@link #getEnricherDescription()} to describe the enricher</li>
 * </ul>
 * 
 * @see DataEnricher
 * @see EnrichmentRequest
 * @see EnrichmentResponse
 */
@Slf4j
public abstract class AbstractResilientDataEnricher implements DataEnricher {

    private final JobTracingService tracingService;
    private final JobMetricsService metricsService;
    private final ResiliencyDecoratorService resiliencyService;
    private final EnrichmentEventPublisher eventPublisher;
    private final EnrichmentCacheService cacheService;

    /**
     * Full constructor with all dependencies including cache.
     *
     * @param tracingService service for distributed tracing
     * @param metricsService service for metrics collection
     * @param resiliencyService service for resiliency patterns
     * @param eventPublisher publisher for enrichment events
     * @param cacheService service for caching enrichment results (optional)
     */
    protected AbstractResilientDataEnricher(JobTracingService tracingService,
                                           JobMetricsService metricsService,
                                           ResiliencyDecoratorService resiliencyService,
                                           EnrichmentEventPublisher eventPublisher,
                                           EnrichmentCacheService cacheService) {
        this.tracingService = tracingService;
        this.metricsService = metricsService;
        this.resiliencyService = resiliencyService;
        this.eventPublisher = eventPublisher;
        this.cacheService = cacheService;
    }

    /**
     * Constructor without cache for backward compatibility.
     */
    protected AbstractResilientDataEnricher(JobTracingService tracingService,
                                           JobMetricsService metricsService,
                                           ResiliencyDecoratorService resiliencyService,
                                           EnrichmentEventPublisher eventPublisher) {
        this(tracingService, metricsService, resiliencyService, eventPublisher, null);
    }

    /**
     * Constructor without event publisher for backward compatibility.
     */
    protected AbstractResilientDataEnricher(JobTracingService tracingService,
                                           JobMetricsService metricsService,
                                           ResiliencyDecoratorService resiliencyService) {
        this(tracingService, metricsService, resiliencyService, null, null);
    }
    
    @Override
    public final Mono<EnrichmentResponse> enrich(EnrichmentRequest request) {
        String providerName = getProviderName();

        // Try to get from cache first (if cache is enabled)
        if (cacheService != null && cacheService.isCacheEnabled()) {
            return cacheService.get(request, providerName)
                .flatMap(cachedOpt -> {
                    if (cachedOpt.isPresent()) {
                        log.debug("Cache HIT for enrichment: type={}, provider={}, tenant={}",
                                request.getEnrichmentType(), providerName, request.getTenantId());
                        return Mono.just(cachedOpt.get());
                    } else {
                        log.debug("Cache MISS for enrichment: type={}, provider={}, tenant={}",
                                request.getEnrichmentType(), providerName, request.getTenantId());
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
        String enrichmentType = request.getEnrichmentType();
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
    
    @Override
    public Flux<EnrichmentResponse> enrichBatch(List<EnrichmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Flux.empty();
        }

        log.info("Starting batch enrichment: provider={}, batchSize={}", getProviderName(), requests.size());

        // Process requests in parallel with controlled concurrency
        // Default parallelism is 10, can be overridden by subclasses
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
     * Implements the actual enrichment logic.
     * Subclasses must implement this method.
     *
     * <p>This method should:</p>
     * <ol>
     *   <li>Extract parameters from the request</li>
     *   <li>Call the provider API (using ServiceClient or provider SDK)</li>
     *   <li>Apply the enrichment strategy to combine source and provider data</li>
     *   <li>Return an EnrichmentResponse with the enriched data</li>
     * </ol>
     *
     * @param request the enrichment request
     * @return a Mono emitting the enrichment response
     */
    protected abstract Mono<EnrichmentResponse> doEnrich(EnrichmentRequest request);
}

