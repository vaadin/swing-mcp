package com.vaadin.swingmcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * In-memory contract type for an MCP tool: name, description, and input
 * schema. Distinct from the wire-shape POJO {@link MCPProtocol.Tool} —
 * that one is shaped for GSON serialization, this one is the
 * canonical descriptor passed around by callers (manifest declarations,
 * proxy wiring, in-process registration).
 *
 * <p>Equality is structural: two descriptors compare equal iff their
 * names, descriptions, and input schemas are equal. Schema equality
 * follows {@link MCPProtocol.InputSchema#equals(Object)} (D_structural_schema_equality):
 * deep, set-semantics on {@code required}, order-insensitive on
 * {@code properties}, order-sensitive on {@code enum}. This is the
 * predicate {@code MCPProxy}'s drift probe uses (D_forwarding_proxy) — extra care
 * with that contract is warranted because false positives become
 * spurious drift errors and false negatives become silent
 * mismatched-version bugs.
 *
 * <p>See D_settable_listeners for the rationale of the type living in this parent
 * package alongside generic protocol types rather than under
 * {@code tinymcpserver} (which is the transport implementation).
 *
 * @param name        tool name; must match {@code [a-zA-Z_][a-zA-Z0-9_]*}
 * @param description human-readable description; not blank
 * @param inputSchema input parameter schema; usually built via
 *                    {@link com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder}
 */
public record ToolDescriptor(
        String name,
        String description,
        MCPProtocol.InputSchema inputSchema) {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    public ToolDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputSchema, "inputSchema");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be blank");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Tool name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be blank");
        }
    }
}
