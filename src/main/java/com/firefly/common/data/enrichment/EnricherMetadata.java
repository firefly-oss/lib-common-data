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

package com.firefly.common.data.enrichment;

import org.springframework.stereotype.Service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares metadata for a data enricher implementation.
 *
 * <p>This annotation is used to define all metadata for a data enricher in a declarative way,
 * eliminating the need to implement multiple metadata methods. It follows the same pattern
 * as {@link com.firefly.common.data.operation.EnricherOperation}.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Declarative metadata definition at the class level</li>
 *   <li>Automatic Spring bean registration via {@code @Service} meta-annotation</li>
 *   <li>Multi-tenancy support with UUID-based tenant identification</li>
 *   <li>Support for multiple enrichment types per enricher</li>
 *   <li>Priority-based selection when multiple enrichers match</li>
 *   <li>Tagging for categorization and filtering</li>
 *   <li>Version tracking for enricher implementations</li>
 * </ul>
 *
 * <p><b>Basic Usage Example:</b></p>
 * <pre>{@code
 * @EnricherMetadata(
 *     providerName = "Financial Data Provider",
 *     type = "company-profile",
 *     description = "Enriches company data with corporate information",
 *     version = "2.1.0"
 * )
 * public class CompanyProfileEnricher
 *         extends DataEnricher<CompanyProfileDTO, ProviderResponse, CompanyProfileDTO> {
 *
 *     // Constructor and enrichment logic only - no metadata methods needed!
 *
 *     @Override
 *     protected Mono<ProviderResponse> fetchProviderData(EnrichmentRequest request) {
 *         String companyId = request.requireParam("companyId");
 *         return providerClient.getCompanyProfile(companyId);
 *     }
 *
 *     @Override
 *     protected CompanyProfileDTO mapToTarget(ProviderResponse providerData) {
 *         return mapToCompanyProfile(providerData);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Multi-Tenancy Example:</b></p>
 * <pre>{@code
 * // Provider A - Spain - Credit Report Product
 * @EnricherMetadata(
 *     providerName = "Provider A",
 *     tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Spain tenant UUID
 *     type = "credit-report",
 *     description = "Provider A Spain credit report enrichment",
 *     tags = {"production", "gdpr-compliant", "spain"},
 *     priority = 50
 * )
 * public class ProviderASpainCreditReportEnricher extends DataEnricher<...> {
 *     // Calls Provider A Spain API for credit reports
 * }
 *
 * // Provider A - USA - Credit Report Product (different API, different implementation)
 * @EnricherMetadata(
 *     providerName = "Provider A",
 *     tenantId = "660e8400-e29b-41d4-a716-446655440002",  // USA tenant UUID
 *     type = "credit-report",
 *     description = "Provider A USA credit report enrichment",
 *     tags = {"production", "soc2-compliant", "usa"},
 *     priority = 50
 * )
 * public class ProviderAUSACreditReportEnricher extends DataEnricher<...> {
 *     // Calls Provider A USA API for credit reports (different endpoint, different format)
 * }
 *
 * // Provider A - Spain - Company Profile Product (different product, same provider/tenant)
 * @EnricherMetadata(
 *     providerName = "Provider A",
 *     tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Spain tenant UUID
 *     type = "company-profile",
 *     description = "Provider A Spain company profile enrichment",
 *     tags = {"production", "gdpr-compliant", "spain"},
 *     priority = 50
 * )
 * public class ProviderASpainCompanyProfileEnricher extends DataEnricher<...> {
 *     // Calls Provider A Spain API for company profiles
 * }
 * }</pre>
 *
 * <p><b>Priority-Based Selection (Multiple Providers for Same Type + Tenant):</b></p>
 * <pre>{@code
 * // Provider A - Spain - Credit Report (Primary)
 * @EnricherMetadata(
 *     providerName = "Provider A",
 *     tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Spain
 *     type = "credit-report",
 *     priority = 100,  // Highest priority - used first
 *     tags = {"production", "primary", "sla-guaranteed"}
 * )
 * public class ProviderASpainCreditReportEnricher extends DataEnricher<...> { }
 *
 * // Provider B - Spain - Credit Report (Fallback)
 * @EnricherMetadata(
 *     providerName = "Provider B",
 *     tenantId = "550e8400-e29b-41d4-a716-446655440001",  // Spain
 *     type = "credit-report",
 *     priority = 50,  // Lower priority - used if Provider A fails
 *     tags = {"production", "fallback", "cost-optimized"}
 * )
 * public class ProviderBSpainCreditReportEnricher extends DataEnricher<...> { }
 * }</pre>
 *
 * <p><b>Tenant Management:</b></p>
 * <p>Tenant UUIDs are managed by a higher-level microservice (e.g., tenant-management-service)
 * and stored in a database. Each enricher is associated with one or more tenants via this UUID.</p>
 *
 * <p><b>Discovery and Registry:</b></p>
 * <p>The {@link com.firefly.common.data.service.DataEnricherRegistry} automatically discovers
 * all enrichers annotated with {@code @EnricherMetadata} and indexes them by:</p>
 * <ul>
 *   <li>Provider name (case-insensitive)</li>
 *   <li>Tenant ID (UUID)</li>
 *   <li>Enrichment type</li>
 *   <li>Tags</li>
 * </ul>
 *
 * <p>The {@link com.firefly.common.data.controller.EnrichmentDiscoveryController} exposes
 * this metadata via REST API for service discovery.</p>
 *
 * @see com.firefly.common.data.service.DataEnricher
 * @see com.firefly.common.data.service.AbstractResilientDataEnricher
 * @see com.firefly.common.data.service.DataEnricherRegistry
 * @see com.firefly.common.data.controller.EnrichmentDiscoveryController
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface EnricherMetadata {

    /**
     * The name of the data enrichment provider.
     *
     * <p>This identifies the third-party provider being used for enrichment.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"Financial Data Provider"</li>
     *   <li>"Credit Bureau Provider"</li>
     *   <li>"Business Data Provider"</li>
     *   <li>"Provider A"</li>
     *   <li>"Provider B"</li>
     * </ul>
     *
     * <p><b>Naming Guidelines:</b></p>
     * <ul>
     *   <li>Use the official provider name or a recognizable alias</li>
     *   <li>Be consistent across all enrichers for the same provider</li>
     *   <li>Avoid including region/tenant in the provider name (use tenantId instead)</li>
     * </ul>
     *
     * @return the provider name (required)
     */
    String providerName();

    /**
     * The tenant UUID this enricher is associated with.
     *
     * <p>Tenants are managed by a higher-level microservice and represent:</p>
     * <ul>
     *   <li>Different clients/organizations</li>
     *   <li>Geographic regions (Spain, USA, LATAM, EU)</li>
     *   <li>Environments (Production, Sandbox, Testing)</li>
     *   <li>Business segments (Enterprise, SMB, Retail)</li>
     *   <li>Compliance zones (GDPR, SOC2, HIPAA)</li>
     * </ul>
     *
     * <p><b>Special Values:</b></p>
     * <ul>
     *   <li>{@code "00000000-0000-0000-0000-000000000000"} - Global/default tenant (available to all)</li>
     *   <li>Any valid UUID - Specific tenant from tenant management service</li>
     * </ul>
     *
     * <p><b>Multi-Tenancy Pattern:</b></p>
     * <pre>{@code
     * // Enricher for Spain tenant
     * @EnricherMetadata(
     *     providerName = "Provider A",
     *     tenantId = "550e8400-e29b-41d4-a716-446655440001"
     * )
     * 
     * // Enricher for USA tenant
     * @EnricherMetadata(
     *     providerName = "Provider A",
     *     tenantId = "550e8400-e29b-41d4-a716-446655440002"
     * )
     * }</pre>
     *
     * @return the tenant UUID (default: global tenant)
     */
    String tenantId() default "00000000-0000-0000-0000-000000000000";

    /**
     * The enrichment type this enricher provides.
     *
     * <p><b>Design Principle: One Enricher = One Type</b></p>
     * <p>Each enricher implements exactly ONE enrichment type. This ensures:</p>
     * <ul>
     *   <li>Single Responsibility - Each enricher does one thing well</li>
     *   <li>Clear API contracts - No ambiguity about what data is returned</li>
     *   <li>Easy testing - Test one specific behavior</li>
     *   <li>Simple routing - Type + Tenant uniquely identifies the enricher</li>
     * </ul>
     *
     * <p><b>The type identifies WHAT data is being enriched:</b></p>
     * <ul>
     *   <li>{@code "credit-report"} - Full credit report data</li>
     *   <li>{@code "company-profile"} - Company information and corporate data</li>
     *   <li>{@code "company-financials"} - Financial statements and metrics</li>
     *   <li>{@code "credit-score"} - Credit scoring only</li>
     *   <li>{@code "risk-assessment"} - Risk scoring and analysis</li>
     *   <li>{@code "address-verification"} - Address validation</li>
     *   <li>{@code "director-information"} - Director and officer details</li>
     * </ul>
     *
     * <p><b>Multi-Provider Example:</b></p>
     * <pre>{@code
     * // Provider A - Spain - Credit Report
     * @EnricherMetadata(
     *     providerName = "Provider A",
     *     tenantId = "550e8400-...",  // Spain
     *     type = "credit-report"
     * )
     * public class ProviderASpainCreditReportEnricher { }
     *
     * // Provider A - USA - Credit Report (different API, different implementation)
     * @EnricherMetadata(
     *     providerName = "Provider A",
     *     tenantId = "660e8400-...",  // USA
     *     type = "credit-report"
     * )
     * public class ProviderAUSACreditReportEnricher { }
     *
     * // Provider B - Spain - Credit Report (different provider, same type)
     * @EnricherMetadata(
     *     providerName = "Provider B",
     *     tenantId = "550e8400-...",  // Spain
     *     type = "credit-report",
     *     priority = 100  // Higher priority than Provider A
     * )
     * public class ProviderBSpainCreditReportEnricher { }
     * }</pre>
     *
     * <p><b>Naming Guidelines:</b></p>
     * <ul>
     *   <li>Use kebab-case (lowercase with hyphens)</li>
     *   <li>Be specific and descriptive</li>
     *   <li>Use consistent naming across all enrichers</li>
     *   <li>Avoid provider-specific names (use generic types)</li>
     * </ul>
     *
     * @return the enrichment type (required)
     */
    String type();

    /**
     * Human-readable description of what this enricher does.
     *
     * <p>This description is included in:</p>
     * <ul>
     *   <li>Discovery endpoint responses</li>
     *   <li>OpenAPI documentation</li>
     *   <li>Logging and monitoring</li>
     * </ul>
     *
     * <p><b>Example:</b> "Enriches company data with financial and corporate information from Provider A Spain"</p>
     *
     * @return the enricher description (default: empty string)
     */
    String description() default "";

    /**
     * Version of this enricher implementation.
     *
     * <p>Use semantic versioning (MAJOR.MINOR.PATCH):</p>
     * <ul>
     *   <li>MAJOR - Breaking changes to the enricher behavior or API</li>
     *   <li>MINOR - New features, backward compatible</li>
     *   <li>PATCH - Bug fixes, backward compatible</li>
     * </ul>
     *
     * <p><b>Examples:</b> "1.0.0", "2.1.3", "3.0.0-beta"</p>
     *
     * @return the version string (default: "1.0.0")
     */
    String version() default "1.0.0";

    /**
     * Tags for categorizing and filtering enrichers.
     *
     * <p>Tags are used for:</p>
     * <ul>
     *   <li>Filtering in discovery endpoint</li>
     *   <li>Grouping in monitoring dashboards</li>
     *   <li>Categorization in documentation</li>
     * </ul>
     *
     * <p><b>Common Tags:</b></p>
     * <ul>
     *   <li>{@code "production"} - Production-ready enricher</li>
     *   <li>{@code "sandbox"} - Sandbox/testing enricher</li>
     *   <li>{@code "gdpr-compliant"} - GDPR compliant</li>
     *   <li>{@code "soc2-compliant"} - SOC2 compliant</li>
     *   <li>{@code "enterprise"} - Enterprise tier</li>
     *   <li>{@code "standard"} - Standard tier</li>
     *   <li>{@code "high-volume"} - Optimized for high volume</li>
     *   <li>{@code "real-time"} - Real-time enrichment</li>
     *   <li>{@code "batch"} - Batch processing optimized</li>
     * </ul>
     *
     * @return array of tags (default: empty array)
     */
    String[] tags() default {};

    /**
     * Priority for selection when multiple enrichers match the same criteria.
     *
     * <p>When multiple enrichers support the same enrichment type for the same tenant,
     * the registry will prefer the one with the highest priority.</p>
     *
     * <p><b>Priority Levels:</b></p>
     * <ul>
     *   <li>{@code 100+} - Critical/Premium enrichers (enterprise SLA)</li>
     *   <li>{@code 50-99} - Standard production enrichers</li>
     *   <li>{@code 10-49} - Fallback/secondary enrichers</li>
     *   <li>{@code 0-9} - Testing/sandbox enrichers</li>
     *   <li>{@code <0} - Deprecated enrichers (avoid using)</li>
     * </ul>
     *
     * @return the priority value (default: 50 for standard priority)
     */
    int priority() default 50;

    /**
     * Whether this enricher is enabled and should be registered.
     *
     * <p>Set to {@code false} to temporarily disable an enricher without removing it.
     * Disabled enrichers are not registered in the {@link com.firefly.common.data.service.DataEnricherRegistry}
     * and will not appear in discovery endpoints.</p>
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Temporarily disable an enricher during maintenance</li>
     *   <li>Feature flags for gradual rollout</li>
     *   <li>A/B testing different enricher implementations</li>
     * </ul>
     *
     * @return true if enabled (default: true)
     */
    boolean enabled() default true;

    /**
     * The Spring bean name for this enricher.
     *
     * <p>If not specified, Spring will generate a default bean name based on the class name.</p>
     *
     * <p><b>Example:</b> "financialDataEnricher", "providerASpainCreditEnricher"</p>
     *
     * @return the bean name (default: empty string for auto-generated name)
     */
    String value() default "";
}

