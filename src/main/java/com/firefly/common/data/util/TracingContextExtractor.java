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

package com.firefly.common.data.util;

import brave.propagation.TraceContext;
import io.micrometer.observation.Observation;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Utility class for extracting tracing context information from Micrometer Observation.
 *
 * This class provides methods to extract trace IDs and span IDs from the current
 * observation context, supporting both Brave and OpenTelemetry tracing backends.
 */
@Slf4j
public final class TracingContextExtractor {

    private static Tracer tracer;

    private TracingContextExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Sets the tracer instance to use for extracting trace context.
     * This should be called during application startup.
     *
     * @param tracerInstance the tracer instance
     */
    public static void setTracer(Tracer tracerInstance) {
        tracer = tracerInstance;
        log.debug("Tracer instance set for TracingContextExtractor");
    }

    /**
     * Extracts the trace ID from the current observation.
     *
     * @param observation the current observation
     * @return the trace ID, or null if not available
     */
    public static String extractTraceId(Observation observation) {
        if (observation == null) {
            log.trace("Observation is null, cannot extract trace ID");
            return null;
        }

        // Try using the tracer directly first (most reliable)
        if (tracer != null) {
            Optional<String> traceId = extractFromTracer(tracer, true);
            if (traceId.isPresent()) {
                return traceId.get();
            }
        }

        // Fallback to observation context
        return extractGenericTraceId(observation)
                .or(() -> extractBraveTraceId(observation))
                .orElse(null);
    }

    /**
     * Extracts the span ID from the current observation.
     *
     * @param observation the current observation
     * @return the span ID, or null if not available
     */
    public static String extractSpanId(Observation observation) {
        if (observation == null) {
            log.trace("Observation is null, cannot extract span ID");
            return null;
        }

        // Try using the tracer directly first (most reliable)
        if (tracer != null) {
            Optional<String> spanId = extractFromTracer(tracer, false);
            if (spanId.isPresent()) {
                return spanId.get();
            }
        }

        // Fallback to observation context
        return extractGenericSpanId(observation)
                .or(() -> extractBraveSpanId(observation))
                .orElse(null);
    }

    /**
     * Extracts trace or span ID from the tracer's current span.
     */
    private static Optional<String> extractFromTracer(Tracer tracerInstance, boolean extractTraceId) {
        try {
            io.micrometer.tracing.Span currentSpan = tracerInstance.currentSpan();
            if (currentSpan != null) {
                io.micrometer.tracing.TraceContext context = currentSpan.context();
                if (context != null) {
                    String id = extractTraceId ? context.traceId() : context.spanId();
                    log.trace("Extracted {} from tracer: {}", extractTraceId ? "trace ID" : "span ID", id);
                    return Optional.ofNullable(id);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract from tracer: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Extracts trace ID from Brave tracing context.
     */
    private static Optional<String> extractBraveTraceId(Observation observation) {
        try {
            Observation.Context context = observation.getContext();
            if (context == null) {
                return Optional.empty();
            }

            // Try to get generic TraceContext from the observation context
            io.micrometer.tracing.TraceContext traceContext = context.get(io.micrometer.tracing.TraceContext.class);
            if (traceContext != null) {
                String traceId = traceContext.traceId();
                log.trace("Extracted trace ID from TraceContext: {}", traceId);
                return Optional.ofNullable(traceId);
            }

            // Alternative: Try to get Brave TraceContext directly
            TraceContext directContext = context.get(TraceContext.class);
            if (directContext != null) {
                String traceId = directContext.traceIdString();
                log.trace("Extracted Brave trace ID (direct): {}", traceId);
                return Optional.ofNullable(traceId);
            }

        } catch (Exception e) {
            log.debug("Failed to extract Brave trace ID: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Extracts span ID from Brave tracing context.
     */
    private static Optional<String> extractBraveSpanId(Observation observation) {
        try {
            Observation.Context context = observation.getContext();
            if (context == null) {
                return Optional.empty();
            }

            // Try to get generic TraceContext from the observation context
            io.micrometer.tracing.TraceContext traceContext = context.get(io.micrometer.tracing.TraceContext.class);
            if (traceContext != null) {
                String spanId = traceContext.spanId();
                log.trace("Extracted span ID from TraceContext: {}", spanId);
                return Optional.ofNullable(spanId);
            }

            // Alternative: Try to get Brave TraceContext directly
            TraceContext directContext = context.get(TraceContext.class);
            if (directContext != null) {
                String spanId = directContext.spanIdString();
                log.trace("Extracted Brave span ID (direct): {}", spanId);
                return Optional.ofNullable(spanId);
            }

        } catch (Exception e) {
            log.debug("Failed to extract Brave span ID: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Extracts trace ID using generic Micrometer Tracing API.
     * This works as a fallback for other tracing implementations.
     */
    private static Optional<String> extractGenericTraceId(Observation observation) {
        try {
            Observation.Context context = observation.getContext();
            if (context == null) {
                return Optional.empty();
            }

            // Method 1: Try to get TraceContext from the context map
            io.micrometer.tracing.TraceContext traceContext = context.get(io.micrometer.tracing.TraceContext.class);
            if (traceContext != null) {
                String traceId = traceContext.traceId();
                log.trace("Extracted generic trace ID from context map: {}", traceId);
                return Optional.ofNullable(traceId);
            }

            // Method 2: Try to get from context name (some implementations store it there)
            String contextName = context.getName();
            if (contextName != null && contextName.contains("traceId=")) {
                String traceId = extractFromContextName(contextName, "traceId");
                if (traceId != null) {
                    log.trace("Extracted trace ID from context name: {}", traceId);
                    return Optional.of(traceId);
                }
            }

        } catch (Exception e) {
            log.debug("Failed to extract generic trace ID: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Extracts span ID using generic Micrometer Tracing API.
     * This works as a fallback for other tracing implementations.
     */
    private static Optional<String> extractGenericSpanId(Observation observation) {
        try {
            Observation.Context context = observation.getContext();
            if (context == null) {
                return Optional.empty();
            }

            // Method 1: Try to get TraceContext from the context map
            io.micrometer.tracing.TraceContext traceContext = context.get(io.micrometer.tracing.TraceContext.class);
            if (traceContext != null) {
                String spanId = traceContext.spanId();
                log.trace("Extracted generic span ID from context map: {}", spanId);
                return Optional.ofNullable(spanId);
            }

            // Method 2: Try to get from context name (some implementations store it there)
            String contextName = context.getName();
            if (contextName != null && contextName.contains("spanId=")) {
                String spanId = extractFromContextName(contextName, "spanId");
                if (spanId != null) {
                    log.trace("Extracted span ID from context name: {}", spanId);
                    return Optional.of(spanId);
                }
            }

        } catch (Exception e) {
            log.debug("Failed to extract generic span ID: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Helper method to extract values from context name string.
     */
    private static String extractFromContextName(String contextName, String key) {
        try {
            int startIndex = contextName.indexOf(key + "=");
            if (startIndex == -1) {
                return null;
            }
            startIndex += key.length() + 1;
            int endIndex = contextName.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = contextName.indexOf("}", startIndex);
            }
            if (endIndex == -1) {
                endIndex = contextName.length();
            }
            return contextName.substring(startIndex, endIndex).trim();
        } catch (Exception e) {
            log.debug("Failed to extract {} from context name: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if tracing context is available in the observation.
     *
     * @param observation the observation to check
     * @return true if tracing context is available, false otherwise
     */
    public static boolean hasTracingContext(Observation observation) {
        if (observation == null || observation.getContext() == null) {
            return false;
        }

        try {
            // Check if tracer has a current span
            if (tracer != null && tracer.currentSpan() != null) {
                return true;
            }

            // Fallback: check observation context
            Observation.Context context = observation.getContext();
            return context.get(io.micrometer.tracing.TraceContext.class) != null
                    || context.get(TraceContext.class) != null;
        } catch (Exception e) {
            log.debug("Error checking for tracing context: {}", e.getMessage());
            return false;
        }
    }
}

