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

/**
 * Interface for enrichers that can be made aware of their REST API endpoint.
 * 
 * <p>This interface is used by {@link AbstractDataEnricherController} to automatically
 * register the enrichment endpoint path with the enricher during initialization.</p>
 * 
 * <p>The endpoint path is used by the discovery endpoint to provide clients with
 * the direct URL to call for enrichment operations.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * public class MyEnricher extends TypedDataEnricher<...> implements EndpointAware {
 *     private String enrichmentEndpoint;
 *     
 *     @Override
 *     public void setEnrichmentEndpoint(String endpoint) {
 *         this.enrichmentEndpoint = endpoint;
 *     }
 *     
 *     @Override
 *     public String getEnrichmentEndpoint() {
 *         return enrichmentEndpoint;
 *     }
 * }
 * }</pre>
 * 
 * <p><b>Note:</b> {@link com.firefly.common.data.service.TypedDataEnricher} already
 * implements this interface, so most enrichers don't need to implement it directly.</p>
 */
public interface EndpointAware {
    
    /**
     * Sets the REST API endpoint path for this enricher.
     * 
     * <p>This method is called automatically by {@link AbstractDataEnricherController}
     * during initialization.</p>
     * 
     * @param endpoint the REST API endpoint path (e.g., "/api/v1/enrichment/provider-a-credit/enrich")
     */
    void setEnrichmentEndpoint(String endpoint);
}

