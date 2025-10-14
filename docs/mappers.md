# MapStruct Mappers

Complete guide to result transformation using MapStruct in `lib-common-data`.

## Table of Contents

- [Overview](#overview)
- [JobResultMapper Interface](#jobresultmapper-interface)
- [Mapper Registry](#mapper-registry)
- [Creating Mappers](#creating-mappers)
- [Advanced Mapping](#advanced-mapping)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

The `lib-common-data` library uses **MapStruct** for transforming raw job results into typed DTOs during the **RESULT stage**.

### Why MapStruct?

✅ **Type-safe** - Compile-time validation  
✅ **Performance** - No reflection, pure Java code  
✅ **Maintainable** - Clear mapping definitions  
✅ **Flexible** - Custom transformations supported  
✅ **IDE-friendly** - Auto-completion and refactoring  

### Transformation Flow

```
COLLECT Stage                    RESULT Stage
     │                                │
     ▼                                ▼
┌──────────────┐              ┌──────────────┐
│  Raw Data    │              │  Mapper      │
│  Map<String, │  ─────────>  │  Registry    │
│  Object>     │              │              │
└──────────────┘              └──────┬───────┘
                                     │
                                     ▼
                              ┌──────────────┐
                              │ JobResult    │
                              │ Mapper       │
                              │ (MapStruct)  │
                              └──────┬───────┘
                                     │
                                     ▼
                              ┌──────────────┐
                              │  Typed DTO   │
                              │  CustomerDTO │
                              └──────────────┘
```

---

## JobResultMapper Interface

### Interface Definition

```java
package com.firefly.common.data.mapper;

/**
 * Base interface for job result mappers using MapStruct.
 * 
 * @param <S> the source type (raw data from job execution)
 * @param <T> the target type (transformed DTO)
 */
public interface JobResultMapper<S, T> {

    /**
     * Maps raw job result data to the target DTO.
     * 
     * @param source the raw data from job execution
     * @return the transformed target DTO
     */
    T mapToTarget(S source);

    /**
     * Gets the source class type for this mapper.
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
     * @return the target class
     */
    default Class<T> getTargetType() {
        throw new UnsupportedOperationException(
            "Mapper must implement getTargetType() or use JobResultMapperRegistry");
    }
}
```

### Generic Types

- **S (Source)** - Usually `Map<String, Object>` for raw job output
- **T (Target)** - Your business DTO class

---

## Mapper Registry

### JobResultMapperRegistry

The registry automatically discovers and manages all mapper beans:

```java
@Component
public class JobResultMapperRegistry {
    
    /**
     * Auto-discovers all JobResultMapper beans
     */
    public JobResultMapperRegistry(List<JobResultMapper<?, ?>> mappers) {
        // Registers all mappers by target type
    }
    
    /**
     * Retrieves a mapper for the specified target type
     */
    public Optional<JobResultMapper<?, ?>> getMapper(Class<?> targetType) {
        // Returns mapper if found
    }
    
    /**
     * Checks if a mapper exists for the target type
     */
    public boolean hasMapper(Class<?> targetType) {
        // Returns true if mapper exists
    }
}
```

### How It Works

1. **Auto-Discovery** - Spring scans for all `JobResultMapper` beans
2. **Type Extraction** - Uses reflection to extract generic type parameters
3. **Registration** - Indexes mappers by target DTO class
4. **Lookup** - Provides type-safe mapper retrieval

---

## Creating Mappers

### Basic Mapper

**Step 1: Define Your DTO**

```java
package com.example.dto;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CustomerDTO {
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
}
```

**Step 2: Create MapStruct Mapper**

```java
package com.example.mapper;

import com.example.dto.CustomerDTO;
import com.firefly.common.data.mapper.JobResultMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Map;

@Mapper(componentModel = "spring")
public interface CustomerDataMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    
    @Override
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    @Mapping(source = "last_name", target = "lastName")
    @Mapping(source = "email_address", target = "email")
    @Mapping(source = "phone", target = "phoneNumber")
    CustomerDTO mapToTarget(Map<String, Object> source);
    
    @Override
    default Class<CustomerDTO> getTargetType() {
        return CustomerDTO.class;
    }
}
```

**Step 3: Add MapStruct Dependency**

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>1.5.5.Final</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok-mapstruct-binding</artifactId>
                        <version>0.2.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Step 4: Use in Service**

```java
@Service
public class CustomerDataJobService implements DataJobService {
    
    @Autowired
    private JobResultMapperRegistry mapperRegistry;
    
    @Override
    public Mono<JobStageResponse> getJobResult(JobStageRequest request) {
        return collectJobResults(request)
            .flatMap(collectResponse -> {
                try {
                    Class<?> targetClass = Class.forName(request.getTargetDtoClass());
                    
                    JobResultMapper mapper = mapperRegistry.getMapper(targetClass)
                        .orElseThrow(() -> new MapperNotFoundException(targetClass));
                    
                    Map<String, Object> rawData = collectResponse.getData();
                    Object mappedResult = mapper.mapToTarget(rawData);
                    
                    return Mono.just(JobStageResponse.builder()
                        .stage(JobStage.RESULT)
                        .data(Map.of("result", mappedResult))
                        .success(true)
                        .build());
                } catch (ClassNotFoundException e) {
                    return Mono.error(e);
                }
            });
    }
}
```

---

## Advanced Mapping

### Custom Transformations

```java
@Mapper(componentModel = "spring")
public interface OrderDataMapper extends JobResultMapper<Map<String, Object>, OrderDTO> {
    
    @Override
    @Mapping(source = "order_id", target = "orderId")
    @Mapping(source = "order_date", target = "orderDate", qualifiedByName = "parseDate")
    @Mapping(source = "total_amount", target = "totalAmount", qualifiedByName = "parseMoney")
    @Mapping(source = "status_code", target = "status", qualifiedByName = "mapStatus")
    @Mapping(target = "orderNumber", expression = "java(generateOrderNumber(source))")
    OrderDTO mapToTarget(Map<String, Object> source);
    
    @Named("parseDate")
    default LocalDateTime parseDate(Object dateValue) {
        if (dateValue instanceof String) {
            return LocalDateTime.parse((String) dateValue);
        }
        return null;
    }
    
    @Named("parseMoney")
    default BigDecimal parseMoney(Object amount) {
        if (amount instanceof Number) {
            return BigDecimal.valueOf(((Number) amount).doubleValue());
        }
        return BigDecimal.ZERO;
    }
    
    @Named("mapStatus")
    default OrderStatus mapStatus(Object statusCode) {
        if (statusCode instanceof String) {
            return OrderStatus.fromCode((String) statusCode);
        }
        return OrderStatus.UNKNOWN;
    }
    
    default String generateOrderNumber(Map<String, Object> source) {
        String orderId = (String) source.get("order_id");
        String timestamp = String.valueOf(System.currentTimeMillis());
        return "ORD-" + orderId + "-" + timestamp.substring(timestamp.length() - 6);
    }
    
    @Override
    default Class<OrderDTO> getTargetType() {
        return OrderDTO.class;
    }
}
```

### Nested Object Mapping

```java
@Mapper(componentModel = "spring")
public interface CustomerWithAddressMapper extends JobResultMapper<Map<String, Object>, CustomerWithAddressDTO> {
    
    @Override
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    @Mapping(source = "last_name", target = "lastName")
    @Mapping(source = ".", target = "address", qualifiedByName = "mapAddress")
    CustomerWithAddressDTO mapToTarget(Map<String, Object> source);
    
    @Named("mapAddress")
    default AddressDTO mapAddress(Map<String, Object> source) {
        return AddressDTO.builder()
            .street((String) source.get("street"))
            .city((String) source.get("city"))
            .state((String) source.get("state"))
            .zipCode((String) source.get("zip_code"))
            .country((String) source.get("country"))
            .build();
    }
    
    @Override
    default Class<CustomerWithAddressDTO> getTargetType() {
        return CustomerWithAddressDTO.class;
    }
}
```

### Collection Mapping

```java
@Mapper(componentModel = "spring")
public interface OrderListMapper extends JobResultMapper<Map<String, Object>, OrderListDTO> {
    
    @Override
    @Mapping(source = "orders", target = "orders", qualifiedByName = "mapOrders")
    @Mapping(source = "total_count", target = "totalCount")
    OrderListDTO mapToTarget(Map<String, Object> source);
    
    @Named("mapOrders")
    default List<OrderDTO> mapOrders(Object ordersObj) {
        if (ordersObj instanceof List) {
            List<?> ordersList = (List<?>) ordersObj;
            return ordersList.stream()
                .filter(item -> item instanceof Map)
                .map(item -> mapSingleOrder((Map<String, Object>) item))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
    
    private OrderDTO mapSingleOrder(Map<String, Object> orderData) {
        return OrderDTO.builder()
            .orderId((String) orderData.get("order_id"))
            .orderDate(parseDate(orderData.get("order_date")))
            .totalAmount(parseMoney(orderData.get("total_amount")))
            .build();
    }
    
    @Override
    default Class<OrderListDTO> getTargetType() {
        return OrderListDTO.class;
    }
}
```

### Conditional Mapping

```java
@Mapper(componentModel = "spring")
public interface ProductDataMapper extends JobResultMapper<Map<String, Object>, ProductDTO> {
    
    @Override
    @Mapping(source = "product_id", target = "productId")
    @Mapping(source = "name", target = "productName")
    @Mapping(source = "price", target = "price", qualifiedByName = "mapPrice")
    @Mapping(source = ".", target = "availability", qualifiedByName = "determineAvailability")
    ProductDTO mapToTarget(Map<String, Object> source);
    
    @Named("mapPrice")
    default BigDecimal mapPrice(Object priceObj) {
        if (priceObj == null) {
            return BigDecimal.ZERO;
        }
        if (priceObj instanceof Number) {
            return BigDecimal.valueOf(((Number) priceObj).doubleValue());
        }
        return BigDecimal.ZERO;
    }
    
    @Named("determineAvailability")
    default ProductAvailability determineAvailability(Map<String, Object> source) {
        Integer stock = (Integer) source.get("stock_quantity");
        Boolean active = (Boolean) source.get("is_active");
        
        if (active == null || !active) {
            return ProductAvailability.DISCONTINUED;
        }
        if (stock == null || stock == 0) {
            return ProductAvailability.OUT_OF_STOCK;
        }
        if (stock < 10) {
            return ProductAvailability.LOW_STOCK;
        }
        return ProductAvailability.IN_STOCK;
    }
    
    @Override
    default Class<ProductDTO> getTargetType() {
        return ProductDTO.class;
    }
}
```

---

## Best Practices

### 1. Use Explicit Mappings

❌ **Bad** - Relies on field name matching:
```java
@Mapper(componentModel = "spring")
public interface BadMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    CustomerDTO mapToTarget(Map<String, Object> source);
}
```

✅ **Good** - Explicit field mappings:
```java
@Mapper(componentModel = "spring")
public interface GoodMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    @Mapping(source = "customer_id", target = "customerId")
    @Mapping(source = "first_name", target = "firstName")
    CustomerDTO mapToTarget(Map<String, Object> source);
}
```

### 2. Handle Null Values

```java
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_DEFAULT)
public interface SafeMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    
    @Mapping(source = "email", target = "email", defaultValue = "unknown@example.com")
    @Mapping(source = "phone", target = "phoneNumber", defaultValue = "N/A")
    CustomerDTO mapToTarget(Map<String, Object> source);
}
```

### 3. Validate Input Data

```java
@Named("validateAndMapEmail")
default String validateAndMapEmail(Object emailObj) {
    if (emailObj == null) {
        return null;
    }
    String email = emailObj.toString();
    if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
        throw new IllegalArgumentException("Invalid email format: " + email);
    }
    return email;
}
```

### 4. Use Type-Safe Enums

```java
@Named("mapOrderStatus")
default OrderStatus mapOrderStatus(Object statusCode) {
    if (statusCode instanceof String) {
        try {
            return OrderStatus.valueOf((String) statusCode);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown status code: {}", statusCode);
            return OrderStatus.UNKNOWN;
        }
    }
    return OrderStatus.UNKNOWN;
}
```

### 5. Document Complex Mappings

```java
/**
 * Maps raw order data to OrderDTO.
 * 
 * Special handling:
 * - order_date: Parsed from ISO-8601 string
 * - total_amount: Converted to BigDecimal with 2 decimal places
 * - status_code: Mapped to OrderStatus enum
 * - order_number: Generated from order_id and timestamp
 */
@Mapper(componentModel = "spring")
public interface OrderDataMapper extends JobResultMapper<Map<String, Object>, OrderDTO> {
    // ...
}
```

---

## Troubleshooting

### Common Issues

**Issue 1: Mapper Not Found**

```
Error: No mapper found for target type: CustomerDTO
```

**Solution:**
- Ensure mapper implements `JobResultMapper`
- Verify `@Mapper(componentModel = "spring")` annotation
- Check that `getTargetType()` returns correct class
- Rebuild project to generate MapStruct implementation

**Issue 2: ClassCastException**

```
Error: java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Integer
```

**Solution:**
```java
@Named("safeParseInt")
default Integer safeParseInt(Object value) {
    if (value instanceof Integer) {
        return (Integer) value;
    }
    if (value instanceof String) {
        try {
            return Integer.parseInt((String) value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    return null;
}
```

**Issue 3: Null Pointer Exception**

```
Error: NullPointerException in mapper
```

**Solution:**
```java
@Mapper(componentModel = "spring", 
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface SafeMapper extends JobResultMapper<Map<String, Object>, CustomerDTO> {
    // Null checks added automatically
}
```

**Issue 4: MapStruct Not Generating Code**

**Solution:**
1. Check annotation processor configuration in `pom.xml`
2. Ensure MapStruct version compatibility
3. Clean and rebuild: `mvn clean compile`
4. Check `target/generated-sources/annotations` for generated code

---

## Testing Mappers

### Unit Test Example

```java
@SpringBootTest
class CustomerDataMapperTest {

    @Autowired
    private CustomerDataMapper mapper;
    
    @Test
    void shouldMapCustomerDataCorrectly() {
        // Given
        Map<String, Object> rawData = Map.of(
            "customer_id", "12345",
            "first_name", "John",
            "last_name", "Doe",
            "email_address", "john@example.com",
            "phone", "+1-555-0100"
        );
        
        // When
        CustomerDTO result = mapper.mapToTarget(rawData);
        
        // Then
        assertThat(result.getCustomerId()).isEqualTo("12345");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getPhoneNumber()).isEqualTo("+1-555-0100");
    }
    
    @Test
    void shouldHandleNullValues() {
        // Given
        Map<String, Object> rawData = Map.of(
            "customer_id", "12345",
            "first_name", "John"
            // Missing fields
        );
        
        // When
        CustomerDTO result = mapper.mapToTarget(rawData);
        
        // Then
        assertThat(result.getCustomerId()).isEqualTo("12345");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isNull();
    }
}
```

---

## See Also

- [Job Lifecycle](job-lifecycle.md) - COLLECT vs RESULT stages
- [Examples](examples.md) - Complete mapper examples
- [API Reference](api-reference.md) - Mapper API documentation
- [MapStruct Documentation](https://mapstruct.org/) - Official MapStruct docs

