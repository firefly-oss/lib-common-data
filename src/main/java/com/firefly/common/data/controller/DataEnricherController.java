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

package com.firefly.common.data.controller;

import com.firefly.common.data.model.EnrichmentApiRequest;
import com.firefly.common.data.model.EnrichmentApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller interface for data enrichment endpoints.
 *
 * <p>This interface defines the standard REST API endpoints for data enrichment operations:
 * <ul>
 *   <li>POST /enrich: Enrich a single data item using the configured provider</li>
 *   <li>POST /enrich/batch: Enrich multiple data items in a batch operation</li>
 *   <li>GET /health: Check health of the enrichment provider</li>
 * </ul>
 *
 * <p>Implementations should delegate to the {@link com.firefly.common.data.service.DataEnricher}
 * for business logic.</p>
 *
 * <p><b>IMPORTANT:</b> Implementations MUST add their own @Tag annotation to specify the Swagger tag.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/enrichment/equifax-spain-credit")
 * @Tag(name = "Data Enrichment - Equifax Spain Credit", description = "Equifax Spain credit report enrichment")
 * public class EquifaxSpainCreditController extends AbstractDataEnricherController {
 *
 *     public EquifaxSpainCreditController(
 *             @Qualifier("equifaxSpainCreditEnricher") DataEnricher enricher,
 *             DataEnricherRegistry registry) {
 *         super(enricher, registry);
 *     }
 * }
 * }</pre>
 *
 * <p>This creates the following endpoints:</p>
 * <pre>
 * POST   /api/v1/enrichment/equifax-spain-credit/enrich
 * POST   /api/v1/enrichment/equifax-spain-credit/enrich/batch
 * GET    /api/v1/enrichment/equifax-spain-credit/health
 * </pre>
 *
 * <p><b>Provider Discovery:</b></p>
 * <p>To discover available providers in the microservice, use the global discovery endpoint:</p>
 * <pre>
 * GET    /api/v1/enrichment/providers
 * GET    /api/v1/enrichment/providers?enrichmentType=credit-report
 * </pre>
 *
 * @see EnrichmentDiscoveryController
 */
@RequestMapping("/api/v1/enrichment")
public interface DataEnricherController {
    
    /**
     * Enriches data using the configured provider.
     * 
     * @param request the enrichment request
     * @return a Mono emitting the enrichment response
     */
    @Operation(
        summary = "Enrich data",
        description = "Enriches data using the configured provider with the specified strategy. " +
                     "The enrichment type and provider are determined by the endpoint and service implementation."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Enrichment completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(responseCode = "404", description = "Provider not found or data not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error"),
        @ApiResponse(responseCode = "503", description = "Provider service unavailable")
    })
    @PostMapping("/enrich")
    Mono<EnrichmentApiResponse> enrich(
        @Valid @RequestBody EnrichmentApiRequest request
    );

    /**
     * Enriches multiple data items in a batch operation.
     *
     * <p>This endpoint processes multiple enrichment requests in parallel,
     * with automatic caching and error handling for individual items.</p>
     *
     * <p><b>Features:</b></p>
     * <ul>
     *   <li>Parallel processing with configurable concurrency</li>
     *   <li>Cache integration - checks cache before calling provider</li>
     *   <li>Individual error handling - one failure doesn't fail the entire batch</li>
     *   <li>Maintains request order in response</li>
     * </ul>
     *
     * @param requests the list of enrichment requests
     * @return a Flux emitting enrichment responses in the same order as requests
     */
    @Operation(
        summary = "Batch enrich data",
        description = "Enriches multiple data items in a single batch operation with parallel processing. " +
                     "Each request is processed independently, and failures are returned as individual error responses."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Batch enrichment completed (check individual responses for errors)"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters or batch size exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/enrich/batch")
    Flux<EnrichmentApiResponse> enrichBatch(
        @Valid @RequestBody List<EnrichmentApiRequest> requests
    );

    /**
     * Checks the health of the enrichment provider.
     * 
     * @return a Mono emitting the health status
     */
    @Operation(
        summary = "Check provider health",
        description = "Checks if the enrichment provider is available and healthy"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Provider is healthy"),
        @ApiResponse(responseCode = "503", description = "Provider is unavailable")
    })
    @GetMapping("/health")
    Mono<ProviderHealthResponse> checkHealth();
    
    /**
     * Health response DTO.
     */
    record ProviderHealthResponse(
        @Parameter(description = "Whether the provider is healthy")
        boolean healthy,
        
        @Parameter(description = "Provider name")
        String providerName,
        
        @Parameter(description = "Health status message")
        String message,
        
        @Parameter(description = "Supported enrichment types")
        String[] supportedTypes
    ) {}
}

