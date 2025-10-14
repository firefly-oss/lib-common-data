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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSizeCalculatorTest {

    @Test
    void shouldCalculateSizeOfSimpleString() {
        // Given
        String data = "Hello, World!";

        // When
        long size = DataSizeCalculator.calculateSize(data);

        // Then
        assertThat(size).isGreaterThan(0);
        assertThat(size).isEqualTo(15); // "Hello, World!" in JSON is quoted: "Hello, World!"
    }

    @Test
    void shouldCalculateSizeOfMap() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);
        data.put("key3", true);

        // When
        long size = DataSizeCalculator.calculateSize(data);

        // Then
        assertThat(size).isGreaterThan(0);
        // JSON: {"key1":"value1","key2":123,"key3":true}
        assertThat(size).isGreaterThan(30);
    }

    @Test
    void shouldReturnZeroForNullData() {
        // When
        long size = DataSizeCalculator.calculateSize(null);

        // Then
        assertThat(size).isEqualTo(0);
    }

    @Test
    void shouldReturnZeroForEmptyMap() {
        // Given
        Map<String, Object> emptyMap = new HashMap<>();

        // When
        long size = DataSizeCalculator.calculateMapSize(emptyMap);

        // Then
        assertThat(size).isEqualTo(0);
    }

    @Test
    void shouldCalculateCombinedSize() {
        // Given
        String data1 = "test1";
        String data2 = "test2";
        Map<String, Object> data3 = Map.of("key", "value");

        // When
        long combinedSize = DataSizeCalculator.calculateCombinedSize(data1, data2, data3);

        // Then
        long size1 = DataSizeCalculator.calculateSize(data1);
        long size2 = DataSizeCalculator.calculateSize(data2);
        long size3 = DataSizeCalculator.calculateSize(data3);

        assertThat(combinedSize).isEqualTo(size1 + size2 + size3);
    }

    @Test
    void shouldHandleNullInCombinedSize() {
        // Given
        String data1 = "test";
        String data2 = null;

        // When
        long combinedSize = DataSizeCalculator.calculateCombinedSize(data1, data2);

        // Then
        long size1 = DataSizeCalculator.calculateSize(data1);
        assertThat(combinedSize).isEqualTo(size1);
    }

    @Test
    void shouldFormatBytesCorrectly() {
        // When & Then
        assertThat(DataSizeCalculator.formatSize(500)).isEqualTo("500 B");
        assertThat(DataSizeCalculator.formatSize(1024)).isEqualTo("1.0 KB");
        assertThat(DataSizeCalculator.formatSize(1536)).isEqualTo("1.5 KB");
        assertThat(DataSizeCalculator.formatSize(1048576)).isEqualTo("1.0 MB");
        assertThat(DataSizeCalculator.formatSize(1073741824)).isEqualTo("1.0 GB");
    }

    @Test
    void shouldDetectWhenDataExceedsSize() {
        // Given
        Map<String, Object> largeData = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            largeData.put("key" + i, "value" + i);
        }

        // When
        boolean exceedsSmallLimit = DataSizeCalculator.exceedsSize(largeData, 100);
        boolean exceedsLargeLimit = DataSizeCalculator.exceedsSize(largeData, 10000);

        // Then
        assertThat(exceedsSmallLimit).isTrue();
        assertThat(exceedsLargeLimit).isFalse();
    }

    @Test
    void shouldCalculateSizeOfComplexObject() {
        // Given
        TestDataObject testObject = new TestDataObject(
                "test-id-123",
                "Test Name",
                Map.of("meta1", "value1", "meta2", "value2"),
                123
        );

        // When
        long size = DataSizeCalculator.calculateSize(testObject);

        // Then
        assertThat(size).isGreaterThan(0);
        assertThat(size).isGreaterThan(50); // Should be larger than just the strings
    }

    @Test
    void shouldHandleNestedMaps() {
        // Given
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("level1", Map.of(
                "level2", Map.of(
                        "level3", "deep value"
                )
        ));

        // When
        long size = DataSizeCalculator.calculateSize(nestedMap);

        // Then
        assertThat(size).isGreaterThan(0);
        assertThat(size).isGreaterThan(30);
    }

    @Test
    void shouldCalculateMapSizeCorrectly() {
        // Given
        Map<String, String> map = Map.of(
                "key1", "value1",
                "key2", "value2",
                "key3", "value3"
        );

        // When
        long mapSize = DataSizeCalculator.calculateMapSize(map);
        long objectSize = DataSizeCalculator.calculateSize(map);

        // Then
        assertThat(mapSize).isEqualTo(objectSize);
        assertThat(mapSize).isGreaterThan(0);
    }

    @Test
    void shouldReturnZeroForNullMap() {
        // When
        long size = DataSizeCalculator.calculateMapSize(null);

        // Then
        assertThat(size).isEqualTo(0);
    }

    @Test
    void shouldHandleEmptyArray() {
        // When
        long size = DataSizeCalculator.calculateCombinedSize();

        // Then
        assertThat(size).isEqualTo(0);
    }

    @Test
    void shouldHandleNullArray() {
        // When
        long size = DataSizeCalculator.calculateCombinedSize((Object[]) null);

        // Then
        assertThat(size).isEqualTo(0);
    }

    @Test
    void shouldCalculateSizeOfInteger() {
        // Given
        Integer number = 12345;

        // When
        long size = DataSizeCalculator.calculateSize(number);

        // Then
        assertThat(size).isGreaterThan(0);
        assertThat(size).isEqualTo(5); // "12345" is 5 bytes
    }

    @Test
    void shouldCalculateSizeOfBoolean() {
        // Given
        Boolean bool = true;

        // When
        long size = DataSizeCalculator.calculateSize(bool);

        // Then
        assertThat(size).isGreaterThan(0);
        assertThat(size).isEqualTo(4); // "true" is 4 bytes
    }

    // Test data class
    private record TestDataObject(
            String id,
            String name,
            Map<String, String> metadata,
            int count
    ) {
    }
}

