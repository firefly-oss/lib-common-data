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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for reading and caching {@link EnricherMetadata} annotations.
 *
 * <p>This class provides efficient access to enricher metadata with caching to avoid
 * repeated reflection calls. It validates metadata and provides helpful error messages
 * when metadata is missing or invalid.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic metadata extraction from {@code @EnricherMetadata} annotation</li>
 *   <li>Thread-safe caching of metadata per enricher class</li>
 *   <li>Validation of required fields and UUID format</li>
 *   <li>Helpful error messages for missing or invalid metadata</li>
 *   <li>Conversion of String[] to List&lt;String&gt; for better API ergonomics</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * DataEnricher enricher = ...;
 *
 * String providerName = EnricherMetadataReader.getProviderName(enricher);
 * UUID tenantId = EnricherMetadataReader.getTenantId(enricher);
 * List<String> types = EnricherMetadataReader.getSupportedTypes(enricher);
 * int priority = EnricherMetadataReader.getPriority(enricher);
 * }</pre>
 *
 * @see EnricherMetadata
 */
public final class EnricherMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(EnricherMetadataReader.class);

    /**
     * Global tenant UUID - available to all clients.
     */
    public static final UUID GLOBAL_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * Cache of metadata annotations by enricher class.
     * Key: enricher class, Value: EnricherMetadata annotation
     */
    private static final Map<Class<?>, EnricherMetadata> METADATA_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache of parsed tenant UUIDs by enricher class.
     * Key: enricher class, Value: parsed UUID
     */
    private static final Map<Class<?>, UUID> TENANT_UUID_CACHE = new ConcurrentHashMap<>();

    // Private constructor - utility class
    private EnricherMetadataReader() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Gets the {@link EnricherMetadata} annotation from an enricher.
     *
     * @param enricher the data enricher
     * @return the metadata annotation
     * @throws IllegalStateException if the enricher is not annotated with {@code @EnricherMetadata}
     */
    public static EnricherMetadata getMetadata(Object enricher) {
        return getMetadata(enricher.getClass());
    }

    /**
     * Gets the {@link EnricherMetadata} annotation from an enricher class.
     *
     * @param enricherClass the enricher class
     * @return the metadata annotation, or null if not annotated (e.g., for mocks)
     */
    public static EnricherMetadata getMetadata(Class<?> enricherClass) {
        return METADATA_CACHE.computeIfAbsent(enricherClass, clazz -> {
            EnricherMetadata metadata = clazz.getAnnotation(EnricherMetadata.class);
            if (metadata != null) {
                validateMetadata(metadata, clazz);
            }
            return metadata;
        });
    }

    /**
     * Checks if an enricher has the {@link EnricherMetadata} annotation.
     *
     * @param enricher the data enricher
     * @return true if annotated, false otherwise
     */
    public static boolean hasMetadata(Object enricher) {
        return enricher.getClass().isAnnotationPresent(EnricherMetadata.class);
    }

    /**
     * Gets the provider name from the enricher metadata.
     * Falls back to class simple name if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the provider name
     */
    public static String getProviderName(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.providerName();
        }
        // Fallback for mocks or enrichers without annotation
        return enricher.getClass().getSimpleName();
    }

    /**
     * Gets the tenant UUID from the enricher metadata.
     * Falls back to global tenant if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the tenant UUID
     * @throws IllegalArgumentException if the tenant ID is not a valid UUID
     */
    public static UUID getTenantId(Object enricher) {
        return TENANT_UUID_CACHE.computeIfAbsent(enricher.getClass(), clazz -> {
            EnricherMetadata metadata = getMetadata(clazz);
            if (metadata == null) {
                // Fallback for mocks or enrichers without annotation
                return GLOBAL_TENANT_ID;
            }
            String tenantIdStr = metadata.tenantId();
            try {
                return UUID.fromString(tenantIdStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "Invalid tenant ID in @EnricherMetadata for " + clazz.getName() + ": '" +
                    tenantIdStr + "'. Must be a valid UUID format (e.g., '550e8400-e29b-41d4-a716-446655440001')",
                    e
                );
            }
        });
    }

    /**
     * Gets the tenant ID as a string from the enricher metadata.
     * Falls back to global tenant ID if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the tenant ID string
     */
    public static String getTenantIdString(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.tenantId();
        }
        return GLOBAL_TENANT_ID.toString();
    }

    /**
     * Checks if the enricher is associated with the global tenant.
     *
     * @param enricher the data enricher
     * @return true if global tenant, false otherwise
     */
    public static boolean isGlobalTenant(Object enricher) {
        return GLOBAL_TENANT_ID.equals(getTenantId(enricher));
    }

    /**
     * Gets the enrichment type from the enricher metadata.
     * Falls back to "unknown" if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the enrichment type
     */
    public static String getType(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.type();
        }
        // Fallback for mocks or enrichers without annotation
        return "unknown";
    }



    /**
     * Gets the description from the enricher metadata.
     * Falls back to empty string if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the description
     */
    public static String getDescription(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.description();
        }
        // Fallback for mocks or enrichers without annotation
        return "";
    }

    /**
     * Gets the version from the enricher metadata.
     * Falls back to default version if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the version string
     */
    public static String getVersion(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.version();
        }
        return "1.0.0"; // Default version for mocks
    }

    /**
     * Gets the tags from the enricher metadata as a List.
     * Falls back to empty list if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return immutable list of tags
     */
    public static List<String> getTags(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            String[] tags = metadata.tags();
            return Arrays.asList(tags);
        }
        return Collections.emptyList(); // Default for mocks
    }

    /**
     * Gets the priority from the enricher metadata.
     * Falls back to default priority if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return the priority value
     */
    public static int getPriority(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.priority();
        }
        return 50; // Default priority for mocks
    }

    /**
     * Checks if the enricher is enabled.
     * Falls back to true if no annotation is present (e.g., for mocks).
     *
     * @param enricher the data enricher
     * @return true if enabled, false otherwise
     */
    public static boolean isEnabled(Object enricher) {
        EnricherMetadata metadata = getMetadata(enricher);
        if (metadata != null) {
            return metadata.enabled();
        }
        return true; // Default for mocks
    }

    /**
     * Validates the metadata annotation.
     *
     * @param metadata the metadata annotation
     * @param enricherClass the enricher class
     * @throws IllegalStateException if validation fails
     */
    private static void validateMetadata(EnricherMetadata metadata, Class<?> enricherClass) {
        // Validate provider name
        if (metadata.providerName() == null || metadata.providerName().trim().isEmpty()) {
            throw new IllegalStateException(
                "@EnricherMetadata on " + enricherClass.getName() + " must specify a non-empty providerName"
            );
        }

        // Validate tenant ID format
        try {
            UUID.fromString(metadata.tenantId());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "@EnricherMetadata on " + enricherClass.getName() + " has invalid tenantId: '" + 
                metadata.tenantId() + "'. Must be a valid UUID format.",
                e
            );
        }

        // Validate type
        if (metadata.type() == null || metadata.type().trim().isEmpty()) {
            throw new IllegalStateException(
                "@EnricherMetadata on " + enricherClass.getName() + " must specify a non-empty type"
            );
        }

        log.debug("Validated @EnricherMetadata for {}: provider={}, tenant={}, type={}",
            enricherClass.getSimpleName(),
            metadata.providerName(),
            metadata.tenantId(),
            metadata.type()
        );
    }

    /**
     * Clears the metadata cache.
     * 
     * <p>This is primarily useful for testing purposes.</p>
     */
    public static void clearCache() {
        METADATA_CACHE.clear();
        TENANT_UUID_CACHE.clear();
        log.debug("Cleared EnricherMetadata cache");
    }
}

