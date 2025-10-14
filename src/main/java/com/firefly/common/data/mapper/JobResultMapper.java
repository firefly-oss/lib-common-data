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

/**
 * Base interface for job result mappers using MapStruct.
 * 
 * Core-data microservices should implement this interface with specific MapStruct mappers
 * to transform raw job results into target DTOs during the RESULT stage.
 * 
 * <p>Example implementation:</p>
 * <pre>
 * {@code
 * @Mapper(componentModel = "spring")
 * public interface CustomerDataMapper extends JobResultMapper<RawCustomerData, CustomerDTO> {
 *     
 *     @Override
 *     @Mapping(source = "firstName", target = "givenName")
 *     @Mapping(source = "lastName", target = "familyName")
 *     CustomerDTO mapToTarget(RawCustomerData source);
 * }
 * }
 * </pre>
 * 
 * @param <S> the source type (raw data from job execution)
 * @param <T> the target type (transformed DTO)
 */
public interface JobResultMapper<S, T> {

    /**
     * Maps raw job result data to the target DTO.
     * 
     * This method is called during the RESULT stage to transform raw data
     * collected from the job execution into the final business DTO.
     * 
     * @param source the raw data from job execution
     * @return the transformed target DTO
     */
    T mapToTarget(S source);

    /**
     * Gets the source class type for this mapper.
     * 
     * This is used to validate that the raw data can be mapped by this mapper.
     * 
     * @return the source class
     */
    default Class<S> getSourceType() {
        throw new UnsupportedOperationException(
            "Mapper must implement getSourceType() or use JobResultMapperRegistry");
    }

    /**
     * Gets the target class type for this mapper.
     * 
     * This is used to identify which mapper to use for a specific target DTO.
     * 
     * @return the target class
     */
    default Class<T> getTargetType() {
        throw new UnsupportedOperationException(
            "Mapper must implement getTargetType() or use JobResultMapperRegistry");
    }
}
