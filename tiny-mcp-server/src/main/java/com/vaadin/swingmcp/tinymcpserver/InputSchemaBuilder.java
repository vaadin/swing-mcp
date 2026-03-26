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
        if (isRequired) {
            required.add(name);
        }
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
                .map(e -> e.getKey() + ": " + e.getValue().getType() + (required.contains(e.getKey()) ? "" : "?"))
                .collect(Collectors.joining(", "));
    }
}
