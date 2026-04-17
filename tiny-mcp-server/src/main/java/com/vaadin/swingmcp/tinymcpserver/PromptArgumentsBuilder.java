package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Fluent builder for MCP prompt arguments.
 * <p>
 * Unlike {@link InputSchemaBuilder} (used for tools), this builder only
 * offers string arguments — MCP prompts always pass their arguments as
 * strings, so types like {@code integer}/{@code number}/{@code boolean}
 * cannot be expressed in a prompt definition at all.
 *
 * <p>Example:
 * <pre>{@code
 * List<PromptArgument> args = new PromptArgumentsBuilder()
 *     .required("name", "Who to greet")
 *     .optional("style", "Greeting style (formal|casual)")
 *     .build();
 * }</pre>
 */
public class PromptArgumentsBuilder {

    private final LinkedHashMap<String, MCPProtocol.PromptArgument> arguments = new LinkedHashMap<>();

    /**
     * Declares a required string argument.
     *
     * @throws IllegalArgumentException if the name or description is
     *         null/blank, or the name doesn't match
     *         {@code [a-zA-Z_][a-zA-Z0-9_]*}
     * @throws IllegalStateException    if an argument with this name has
     *         already been added
     */
    public PromptArgumentsBuilder required(String name, String description) {
        return add(name, description, true);
    }

    /**
     * Declares an optional string argument.
     *
     * @see #required(String, String) for validation rules
     */
    public PromptArgumentsBuilder optional(String name, String description) {
        return add(name, description, false);
    }

    private PromptArgumentsBuilder add(String name, String description, boolean isRequired) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Argument name must not be null or blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Argument name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Argument description must not be null or blank");
        }
        if (arguments.containsKey(name)) {
            throw new IllegalStateException("Argument already exists: " + name);
        }
        MCPProtocol.PromptArgument arg = new MCPProtocol.PromptArgument();
        arg.setName(name);
        arg.setDescription(description);
        arg.setRequired(isRequired);
        arguments.put(name, arg);
        return this;
    }

    public List<MCPProtocol.PromptArgument> build() {
        return new ArrayList<>(arguments.values());
    }
}
