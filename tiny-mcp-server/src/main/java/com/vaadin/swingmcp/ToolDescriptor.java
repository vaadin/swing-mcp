/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * In-memory contract type for an MCP tool: name, description, and input
 * schema. Distinct from the wire-shape POJO {@link MCPProtocol.Tool} —
 * that one is shaped for GSON serialization, this one is the
 * canonical descriptor passed around by callers (manifest declarations,
 * in-process registration).
 *
 * <pre>{@code
 * new ToolDescriptor(
 *         "swing_click",
 *         "Click a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.",
 *         new InputSchemaBuilder()
 *                 .requiredInteger("ref", "The element reference number from swing_snapshot")
 *                 .build());
 * }</pre>
 *
 * <p>Equality is structural: two descriptors compare equal iff their
 * names, descriptions, and input schemas are equal. Schema equality
 * follows {@link MCPProtocol.InputSchema#equals(Object)} (D_structural_schema_equality):
 * deep, set-semantics on {@code required}, order-insensitive on
 * {@code properties}, order-sensitive on {@code enum}. A manifest-coherence
 * test compares descriptors with it, so a false negative hides real drift
 * between a manifest and what the server registered.
 *
 * <p>See D_settable_listeners for the rationale of the type living in this parent
 * package alongside generic protocol types rather than under
 * {@code tinymcpserver} (which is the transport implementation).
 *
 * <p>Immutable.
 */
public final class ToolDescriptor {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final String name;
    private final String description;
    private final MCPProtocol.InputSchema inputSchema;

    /**
     * @param name        tool name; must match {@code [a-zA-Z_][a-zA-Z0-9_]*}
     * @param description human-readable description; not blank
     * @param inputSchema input parameter schema; usually built via
     *                    {@link com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder}
     * @throws IllegalArgumentException if {@code name} is blank or malformed,
     *                                  or {@code description} is blank
     */
    public ToolDescriptor(String name,
                          String description,
                          MCPProtocol.InputSchema inputSchema) {
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
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public MCPProtocol.InputSchema inputSchema() {
        return inputSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolDescriptor)) {
            return false;
        }
        final ToolDescriptor that = (ToolDescriptor) o;
        return name.equals(that.name)
                && description.equals(that.description)
                && inputSchema.equals(that.inputSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, inputSchema);
    }

    @Override
    public String toString() {
        return "ToolDescriptor[name=" + name + ", description=" + description
                + ", inputSchema=" + inputSchema + ']';
    }
}
