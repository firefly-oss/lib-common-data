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

package com.firefly.common.data.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JobResultMapperRegistry.
 */
class JobResultMapperRegistryTest {

    private JobResultMapperRegistry registry;

    // Test DTOs
    static class CustomerDTO {
        private String customerId;
        private String name;
    }

    static class OrderDTO {
        private String orderId;
        private String status;
    }

    // Test Mappers
    static class CustomerMapper implements JobResultMapper<Map<String, Object>, CustomerDTO> {
        @Override
        public CustomerDTO mapToTarget(Map<String, Object> source) {
            return new CustomerDTO();
        }

        @Override
        public Class<CustomerDTO> getTargetType() {
            return CustomerDTO.class;
        }
    }

    static class OrderMapper implements JobResultMapper<Map<String, Object>, OrderDTO> {
        @Override
        public OrderDTO mapToTarget(Map<String, Object> source) {
            return new OrderDTO();
        }

        @Override
        public Class<OrderDTO> getTargetType() {
            return OrderDTO.class;
        }
    }

    @BeforeEach
    void setUp() {
        List<JobResultMapper<?, ?>> mappers = List.of(
                new CustomerMapper(),
                new OrderMapper()
        );
        registry = new JobResultMapperRegistry(mappers);
    }

    @Test
    void shouldRegisterMappersOnConstruction() {
        // Then
        assertThat(registry.getAllMappers()).hasSize(2);
        assertThat(registry.hasMapper(CustomerDTO.class)).isTrue();
        assertThat(registry.hasMapper(OrderDTO.class)).isTrue();
    }

    @Test
    void shouldGetMapperByTargetType() {
        // When
        Optional<JobResultMapper<?, ?>> mapper = registry.getMapper(CustomerDTO.class);

        // Then
        assertThat(mapper).isPresent();
        assertThat(mapper.get()).isInstanceOf(CustomerMapper.class);
    }

    @Test
    void shouldReturnEmptyWhenMapperNotFound() {
        // Given
        class UnknownDTO {}

        // When
        Optional<JobResultMapper<?, ?>> mapper = registry.getMapper(UnknownDTO.class);

        // Then
        assertThat(mapper).isEmpty();
    }

    @Test
    void shouldGetMapperByName() {
        // When
        Optional<JobResultMapper<?, ?>> mapper = registry.getMapperByName("CustomerMapper");

        // Then
        assertThat(mapper).isPresent();
        assertThat(mapper.get()).isInstanceOf(CustomerMapper.class);
    }

    @Test
    void shouldReturnEmptyWhenMapperNameNotFound() {
        // When
        Optional<JobResultMapper<?, ?>> mapper = registry.getMapperByName("NonExistentMapper");

        // Then
        assertThat(mapper).isEmpty();
    }

    @Test
    void shouldCheckIfMapperExists() {
        // Then
        assertThat(registry.hasMapper(CustomerDTO.class)).isTrue();
        assertThat(registry.hasMapper(OrderDTO.class)).isTrue();
        
        class UnknownDTO {}
        assertThat(registry.hasMapper(UnknownDTO.class)).isFalse();
    }

    @Test
    void shouldGetAllMappers() {
        // When
        Map<Class<?>, JobResultMapper<?, ?>> allMappers = registry.getAllMappers();

        // Then
        assertThat(allMappers).hasSize(2);
        assertThat(allMappers).containsKey(CustomerDTO.class);
        assertThat(allMappers).containsKey(OrderDTO.class);
    }

    @Test
    void shouldReturnImmutableCopyOfMappers() {
        // When
        Map<Class<?>, JobResultMapper<?, ?>> allMappers = registry.getAllMappers();

        // Then
        assertThat(allMappers).isUnmodifiable();
    }

    @Test
    void shouldHandleEmptyMapperList() {
        // Given
        JobResultMapperRegistry emptyRegistry = new JobResultMapperRegistry(List.of());

        // Then
        assertThat(emptyRegistry.getAllMappers()).isEmpty();
        assertThat(emptyRegistry.hasMapper(CustomerDTO.class)).isFalse();
    }

    @Test
    void shouldMapCorrectly() {
        // Given
        Optional<JobResultMapper<?, ?>> mapper = registry.getMapper(CustomerDTO.class);
        Map<String, Object> rawData = Map.of("customerId", "123", "name", "John");

        // When
        assertThat(mapper).isPresent();
        @SuppressWarnings("unchecked")
        JobResultMapper<Map<String, Object>, CustomerDTO> typedMapper = 
                (JobResultMapper<Map<String, Object>, CustomerDTO>) mapper.get();
        CustomerDTO result = typedMapper.mapToTarget(rawData);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(CustomerDTO.class);
    }
}

