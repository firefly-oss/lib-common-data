package com.firefly.common.data.operation.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.common.data.operation.dto.CompanySearchRequest;
import com.firefly.common.data.operation.dto.CompanySearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JSON Schema generation.
 */
class JsonSchemaGeneratorTest {

    private JsonSchemaGenerator schemaGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        schemaGenerator = new JsonSchemaGenerator(objectMapper);
    }

    @Test
    void testGenerateSchemaForRequest() {
        JsonNode schema = schemaGenerator.generateSchema(CompanySearchRequest.class);
        
        assertThat(schema).isNotNull();
        assertThat(schema.has("type")).isTrue();
        assertThat(schema.get("type").asText()).isEqualTo("object");
    }

    @Test
    void testGenerateSchemaForResponse() {
        JsonNode schema = schemaGenerator.generateSchema(CompanySearchResponse.class);
        
        assertThat(schema).isNotNull();
        assertThat(schema.has("type")).isTrue();
        assertThat(schema.get("type").asText()).isEqualTo("object");
    }

    @Test
    void testGenerateSchemaWithProperties() {
        JsonNode schema = schemaGenerator.generateSchema(CompanySearchRequest.class);
        
        assertThat(schema.has("properties")).isTrue();
        JsonNode properties = schema.get("properties");
        
        assertThat(properties.has("companyName")).isTrue();
        assertThat(properties.has("taxId")).isTrue();
        assertThat(properties.has("minConfidence")).isTrue();
    }

    @Test
    void testGenerateSchemaWithDescriptions() {
        JsonNode schema = schemaGenerator.generateSchema(CompanySearchRequest.class);
        
        // Check class-level description
        if (schema.has("description")) {
            assertThat(schema.get("description").asText())
                .isEqualTo("Request to search for a company");
        }
        
        // Check field descriptions
        JsonNode properties = schema.get("properties");
        if (properties.has("companyName")) {
            JsonNode companyNameProp = properties.get("companyName");
            if (companyNameProp.has("description")) {
                assertThat(companyNameProp.get("description").asText())
                    .contains("Company name");
            }
        }
    }

    @Test
    void testGenerateExampleForRequest() {
        Object example = schemaGenerator.generateExample(CompanySearchRequest.class);
        
        assertThat(example).isNotNull();
        assertThat(example).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleMap = (Map<String, Object>) example;
        
        assertThat(exampleMap).containsKey("companyName");
        assertThat(exampleMap.get("companyName")).isEqualTo("Acme Corp");
        
        assertThat(exampleMap).containsKey("taxId");
        assertThat(exampleMap.get("taxId")).isEqualTo("TAX-12345678");
        
        assertThat(exampleMap).containsKey("minConfidence");
        assertThat(exampleMap.get("minConfidence")).isEqualTo(0.8);
    }

    @Test
    void testGenerateExampleForResponse() {
        Object example = schemaGenerator.generateExample(CompanySearchResponse.class);
        
        assertThat(example).isNotNull();
        assertThat(example).isInstanceOf(Map.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleMap = (Map<String, Object>) example;
        
        assertThat(exampleMap).containsKey("providerId");
        assertThat(exampleMap.get("providerId")).isEqualTo("PROV-12345");
        
        assertThat(exampleMap).containsKey("companyName");
        assertThat(exampleMap.get("companyName")).isEqualTo("ACME CORPORATION");
        
        assertThat(exampleMap).containsKey("taxId");
        assertThat(exampleMap.get("taxId")).isEqualTo("TAX-12345678");
        
        assertThat(exampleMap).containsKey("confidence");
        assertThat(exampleMap.get("confidence")).isEqualTo(0.95);
    }

    @Test
    void testConvertExampleValueString() {
        Object example = schemaGenerator.generateExample(CompanySearchRequest.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleMap = (Map<String, Object>) example;
        
        assertThat(exampleMap.get("companyName")).isInstanceOf(String.class);
    }

    @Test
    void testConvertExampleValueDouble() {
        Object example = schemaGenerator.generateExample(CompanySearchRequest.class);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> exampleMap = (Map<String, Object>) example;
        
        assertThat(exampleMap.get("minConfidence")).isInstanceOf(Double.class);
    }
}

