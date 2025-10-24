package com.firefly.common.data.operation.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates JSON Schemas and example objects from Java classes.
 *
 * <p>This component uses Jackson's JSON Schema module and Swagger {@code @Schema} annotations
 * to generate:</p>
 * <ul>
 *   <li>JSON Schema for validation and documentation</li>
 *   <li>Example objects for documentation and testing</li>
 * </ul>
 *
 * <p><b>Supported Annotations:</b></p>
 * <ul>
 *   <li>{@code @Schema(description = "...")} - Field description</li>
 *   <li>{@code @Schema(example = "...")} - Example value</li>
 *   <li>{@code @Schema(required = true)} - Required field</li>
 *   <li>{@code @Schema(minimum = "0", maximum = "100")} - Numeric constraints</li>
 *   <li>{@code @Schema(pattern = "...")} - String pattern (regex)</li>
 * </ul>
 *
 * <p><b>Example DTO with Annotations:</b></p>
 * <pre>{@code
 * @Data
 * @Builder
 * @Schema(description = "Request to search for a company")
 * public class CompanySearchRequest {
 *     @Schema(
 *         description = "Company name to search for",
 *         example = "Acme Corp",
 *         required = true
 *     )
 *     private String companyName;
 *
 *     @Schema(
 *         description = "Tax ID to search for",
 *         example = "TAX-12345678",
 *         pattern = "^TAX-[0-9]{8}$"
 *     )
 *     private String taxId;
 *
 *     @Schema(
 *         description = "Match confidence threshold",
 *         example = "0.8",
 *         minimum = "0",
 *         maximum = "1"
 *     )
 *     private Double minConfidence;
 * }
 * }</pre>
 *
 * <p><b>Generated JSON Schema:</b></p>
 * <pre>{@code
 * {
 *   "type": "object",
 *   "description": "Request to search for a company",
 *   "properties": {
 *     "companyName": {
 *       "type": "string",
 *       "description": "Company name to search for"
 *     },
 *     "taxId": {
 *       "type": "string",
 *       "description": "Tax ID to search for",
 *       "pattern": "^TAX-[0-9]{8}$"
 *     },
 *     "minConfidence": {
 *       "type": "number",
 *       "description": "Match confidence threshold",
 *       "minimum": 0,
 *       "maximum": 1
 *     }
 *   },
 *   "required": ["companyName"]
 * }
 * }</pre>
 *
 * <p><b>Generated Example Object:</b></p>
 * <pre>{@code
 * {
 *   "companyName": "Acme Corp",
 *   "taxId": "TAX-12345678",
 *   "minConfidence": 0.8
 * }
 * }</pre>
 */
@Component
@Slf4j
public class JsonSchemaGenerator {

    private final ObjectMapper objectMapper;
    private final com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator schemaGenerator;

    public JsonSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaGenerator = new com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator(objectMapper);
    }

    /**
     * Generates a JSON Schema for the given class.
     *
     * <p>The schema is generated from:</p>
     * <ul>
     *   <li>Jackson annotations ({@code @JsonProperty}, etc.)</li>
     *   <li>Swagger {@code @Schema} annotations</li>
     *   <li>Java type information</li>
     * </ul>
     *
     * @param clazz the class to generate schema for
     * @return the JSON Schema as a JsonNode
     */
    public JsonNode generateSchema(Class<?> clazz) {
        try {
            JsonSchema schema = schemaGenerator.generateSchema(clazz);
            JsonNode schemaNode = objectMapper.valueToTree(schema);

            // Enhance schema with @Schema annotations
            enhanceSchemaWithAnnotations(schemaNode, clazz);

            return schemaNode;
        } catch (Exception e) {
            log.error("Failed to generate JSON schema for class {}: {}", 
                clazz.getSimpleName(), e.getMessage(), e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * Generates an example object for the given class.
     *
     * <p>The example is generated from {@code @Schema(example = "...")} annotations
     * on the class fields.</p>
     *
     * @param clazz the class to generate example for
     * @return the example object as a Map
     */
    public Object generateExample(Class<?> clazz) {
        try {
            Map<String, Object> example = new HashMap<>();

            // Get class-level @Schema annotation
            Schema classSchema = clazz.getAnnotation(Schema.class);

            // Iterate through fields and extract examples from @Schema annotations
            for (Field field : clazz.getDeclaredFields()) {
                Schema fieldSchema = field.getAnnotation(Schema.class);
                if (fieldSchema != null && !fieldSchema.example().isEmpty()) {
                    String exampleValue = fieldSchema.example();
                    Object typedValue = convertExampleValue(exampleValue, field.getType());
                    example.put(field.getName(), typedValue);
                }
            }

            return example.isEmpty() ? null : example;
        } catch (Exception e) {
            log.error("Failed to generate example for class {}: {}", 
                clazz.getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Enhances the generated JSON schema with information from {@code @Schema} annotations.
     *
     * @param schemaNode the schema node to enhance
     * @param clazz the class with annotations
     */
    private void enhanceSchemaWithAnnotations(JsonNode schemaNode, Class<?> clazz) {
        if (!(schemaNode instanceof ObjectNode)) {
            return;
        }

        ObjectNode objectSchema = (ObjectNode) schemaNode;

        // Add class-level description
        Schema classSchema = clazz.getAnnotation(Schema.class);
        if (classSchema != null && !classSchema.description().isEmpty()) {
            objectSchema.put("description", classSchema.description());
        }

        // Enhance field schemas
        JsonNode properties = objectSchema.get("properties");
        if (properties != null && properties.isObject()) {
            ObjectNode propertiesNode = (ObjectNode) properties;

            for (Field field : clazz.getDeclaredFields()) {
                Schema fieldSchema = field.getAnnotation(Schema.class);
                if (fieldSchema != null) {
                    JsonNode fieldNode = propertiesNode.get(field.getName());
                    if (fieldNode != null && fieldNode.isObject()) {
                        ObjectNode fieldObjectNode = (ObjectNode) fieldNode;

                        // Add description
                        if (!fieldSchema.description().isEmpty()) {
                            fieldObjectNode.put("description", fieldSchema.description());
                        }

                        // Add constraints
                        if (!fieldSchema.minimum().isEmpty()) {
                            try {
                                fieldObjectNode.put("minimum", Double.parseDouble(fieldSchema.minimum()));
                            } catch (NumberFormatException e) {
                                log.warn("Invalid minimum value for field {}: {}", 
                                    field.getName(), fieldSchema.minimum());
                            }
                        }

                        if (!fieldSchema.maximum().isEmpty()) {
                            try {
                                fieldObjectNode.put("maximum", Double.parseDouble(fieldSchema.maximum()));
                            } catch (NumberFormatException e) {
                                log.warn("Invalid maximum value for field {}: {}", 
                                    field.getName(), fieldSchema.maximum());
                            }
                        }

                        if (!fieldSchema.pattern().isEmpty()) {
                            fieldObjectNode.put("pattern", fieldSchema.pattern());
                        }

                        // Add required fields
                        if (fieldSchema.required()) {
                            if (!objectSchema.has("required")) {
                                objectSchema.putArray("required");
                            }
                            ((com.fasterxml.jackson.databind.node.ArrayNode) objectSchema.get("required"))
                                .add(field.getName());
                        }
                    }
                }
            }
        }
    }

    /**
     * Converts a string example value to the appropriate type.
     *
     * @param exampleValue the example value as a string
     * @param targetType the target type
     * @return the converted value
     */
    private Object convertExampleValue(String exampleValue, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return exampleValue;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(exampleValue);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(exampleValue);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(exampleValue);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(exampleValue);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(exampleValue);
            } else {
                // For complex types, try to parse as JSON
                return objectMapper.readValue(exampleValue, targetType);
            }
        } catch (Exception e) {
            log.warn("Failed to convert example value '{}' to type {}: {}", 
                exampleValue, targetType.getSimpleName(), e.getMessage());
            return exampleValue;
        }
    }
}

