package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fluent builder for MCP tool input schemas.
 *
 * <p>Example usage:
 * <pre>{@code
 * InputSchema schema = new InputSchemaBuilder()
 *     .requiredInteger("ref", "The element reference number")
 *     .build();
 * }</pre>
 */
public class InputSchemaBuilder {

    private final LinkedHashMap<String, MCPProtocol.PropertySchema> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();
    private String lastAdded = null;

    public InputSchemaBuilder requiredString(String name, String description) {
        return add(name, "string", description, true);
    }

    public InputSchemaBuilder optionalString(String name, String description) {
        return add(name, "string", description, false);
    }

    public InputSchemaBuilder requiredInteger(String name, String description) {
        return add(name, "integer", description, true);
    }

    public InputSchemaBuilder optionalInteger(String name, String description) {
        return add(name, "integer", description, false);
    }

    public InputSchemaBuilder requiredNumber(String name, String description) {
        return add(name, "number", description, true);
    }

    public InputSchemaBuilder optionalNumber(String name, String description) {
        return add(name, "number", description, false);
    }

    public InputSchemaBuilder requiredBoolean(String name, String description) {
        return add(name, "boolean", description, true);
    }

    public InputSchemaBuilder optionalBoolean(String name, String description) {
        return add(name, "boolean", description, false);
    }

    private InputSchemaBuilder add(String name, String type, String description, boolean isRequired) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be null or blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Parameter name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Parameter description must not be null or blank");
        }
        if (properties.containsKey(name)) {
            throw new IllegalStateException("Parameter already exists: " + name);
        }
        MCPProtocol.PropertySchema schema = new MCPProtocol.PropertySchema();
        schema.setType(type);
        schema.setDescription(description);
        properties.put(name, schema);
        lastAdded = name;
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    public InputSchemaBuilder withEnum(String... values) {
        if (lastAdded == null) {
            throw new IllegalStateException("No parameter has been added yet");
        }
        MCPProtocol.PropertySchema schema = properties.get(lastAdded);
        if (schema.getEnumValues() != null) {
            throw new IllegalStateException("Enum already set for parameter: " + lastAdded);
        }
        schema.setEnumValues(List.of(values));
        return this;
    }

    public InputSchemaBuilder withMinimum(Number min) {
        if (lastAdded == null) {
            throw new IllegalStateException("No parameter has been added yet");
        }
        MCPProtocol.PropertySchema schema = properties.get(lastAdded);
        if (schema.getMinimum() != null) {
            throw new IllegalStateException("Minimum already set for parameter: " + lastAdded);
        }
        schema.setMinimum(min);
        return this;
    }

    public InputSchemaBuilder withMaximum(Number max) {
        if (lastAdded == null) {
            throw new IllegalStateException("No parameter has been added yet");
        }
        MCPProtocol.PropertySchema schema = properties.get(lastAdded);
        if (schema.getMaximum() != null) {
            throw new IllegalStateException("Maximum already set for parameter: " + lastAdded);
        }
        schema.setMaximum(max);
        return this;
    }

    public MCPProtocol.InputSchema build() {
        MCPProtocol.InputSchema schema = new MCPProtocol.InputSchema();
        schema.setProperties(new LinkedHashMap<>(properties));
        schema.setRequired(new ArrayList<>(required));
        return schema;
    }

    @Override
    public String toString() {
        return properties.entrySet().stream()
                .map(this::propertyToString)
                .collect(Collectors.joining(", "));
    }

    private String propertyToString(Map.Entry<String, MCPProtocol.PropertySchema> e) {
        String name = e.getKey();
        MCPProtocol.PropertySchema schema = e.getValue();
        String optionalMark = required.contains(name) ? "" : "?";
        String result = name + ": " + schema.getType() + optionalMark;
        if (schema.getEnumValues() != null) {
            result += "(" + String.join("|", schema.getEnumValues()) + ")";
        }
        if (schema.getMinimum() != null || schema.getMaximum() != null) {
            String min = schema.getMinimum() != null ? schema.getMinimum().toString() : "";
            String max = schema.getMaximum() != null ? schema.getMaximum().toString() : "";
            result += "[" + min + "," + max + "]";
        }
        return result;
    }
}
