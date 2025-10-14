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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility class for calculating the size of data objects.
 * 
 * This class provides methods to accurately measure the size of data objects
 * by serializing them to JSON and measuring the byte size.
 */
@Slf4j
public final class DataSizeCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DataSizeCalculator() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculates the size of a data object in bytes by serializing it to JSON.
     * 
     * @param data the data object to measure
     * @return the size in bytes, or 0 if the data is null or cannot be serialized
     */
    public static long calculateSize(Object data) {
        if (data == null) {
            return 0L;
        }

        try {
            // Serialize to JSON string
            String json = OBJECT_MAPPER.writeValueAsString(data);
            
            // Calculate byte size using UTF-8 encoding
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            long size = bytes.length;
            
            log.trace("Calculated data size: {} bytes for object of type {}", size, data.getClass().getSimpleName());
            return size;
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object for size calculation: {}. Falling back to toString() method.", 
                    e.getMessage());
            return fallbackSizeCalculation(data);
        } catch (Exception e) {
            log.error("Unexpected error calculating data size: {}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * Calculates the combined size of multiple data objects.
     * 
     * @param dataObjects the data objects to measure
     * @return the total size in bytes
     */
    public static long calculateCombinedSize(Object... dataObjects) {
        if (dataObjects == null || dataObjects.length == 0) {
            return 0L;
        }

        long totalSize = 0L;
        for (Object data : dataObjects) {
            totalSize += calculateSize(data);
        }

        log.trace("Calculated combined data size: {} bytes for {} objects", totalSize, dataObjects.length);
        return totalSize;
    }

    /**
     * Calculates the size of a Map by serializing it to JSON.
     * 
     * @param map the map to measure
     * @return the size in bytes, or 0 if the map is null or empty
     */
    public static long calculateMapSize(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return 0L;
        }

        return calculateSize(map);
    }

    /**
     * Fallback method for size calculation when JSON serialization fails.
     * Uses toString() method and UTF-8 byte encoding.
     * 
     * @param data the data object
     * @return the estimated size in bytes
     */
    private static long fallbackSizeCalculation(Object data) {
        try {
            String stringRepresentation = data.toString();
            byte[] bytes = stringRepresentation.getBytes(StandardCharsets.UTF_8);
            long size = bytes.length;
            
            log.trace("Fallback size calculation: {} bytes", size);
            return size;
            
        } catch (Exception e) {
            log.error("Fallback size calculation also failed: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Formats a byte size into a human-readable string.
     * 
     * @param bytes the size in bytes
     * @return a formatted string (e.g., "1.5 KB", "2.3 MB")
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Checks if a data object exceeds a specified size limit.
     * 
     * @param data the data object to check
     * @param maxSizeBytes the maximum allowed size in bytes
     * @return true if the data exceeds the limit, false otherwise
     */
    public static boolean exceedsSize(Object data, long maxSizeBytes) {
        long actualSize = calculateSize(data);
        boolean exceeds = actualSize > maxSizeBytes;
        
        if (exceeds) {
            log.debug("Data size {} exceeds limit of {} bytes", formatSize(actualSize), formatSize(maxSizeBytes));
        }
        
        return exceeds;
    }
}

