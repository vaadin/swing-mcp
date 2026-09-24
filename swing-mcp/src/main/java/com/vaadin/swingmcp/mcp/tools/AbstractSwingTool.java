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
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.ToolDescriptor;
import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;

import javax.accessibility.Accessible;
import javax.swing.JTable;
import java.util.Iterator;
import java.util.Objects;

/**
 * Base class for a Swing MCP tool. A mutation tool binds its descriptor, validates synchronously,
 * posts the action and echoes it:
 *
 * <pre>{@code
 * public SwingIncrementTool() {
 *     super(SwingTools.SWING_INCREMENT);
 * }
 *
 * public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
 *     int ref = params.getInt("ref");
 *     Accessible accessible = context.getAccessibleByRef(ref);
 *     // ...refuse with MCPErrorResponseException: unsupported, disabled...
 *     SwingUtilities.invokeLater(() -> aa.doAccessibleAction(actionIndex));
 *     return echo(ref);
 * }
 *
 * public boolean isMutation() {
 *     return true;
 * }
 * }</pre>
 *
 * The {@code require*} guards hold the refusals the selection tools share; {@link #echo} and the
 * {@code renderEcho*} helpers own the success echo's exact text (D_dispatched_echo).
 *
 * <p>UI-thread-confined.
 */
public abstract class AbstractSwingTool {

    private final ToolDescriptor descriptor;

    /**
     * @param descriptor this tool's {@code SwingTools.SWING_*} constant; the name, description and
     *                   schema getters are {@code final} delegates to it (D_shared_tool_manifest)
     */
    protected AbstractSwingTool(ToolDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public final ToolDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * @return the MCP tool name, e.g. {@code "swing_snapshot"}
     */
    public final String getName() {
        return descriptor.name();
    }

    public final String getDescription() {
        return descriptor.description();
    }

    public final MCPProtocol.InputSchema getInputSchema() {
        return descriptor.inputSchema();
    }

    /**
     * Runs one call of this tool.
     *
     * @throws MCPErrorResponseException a refusal the model can act on: {@code isError=true} with
     *         this message
     * @throws com.vaadin.swingmcp.tinymcpserver.MCPServerException a JSON-RPC protocol error, e.g.
     *         {@code INVALID_PARAMS} for a malformed parameter
     * @throws Exception on an unexpected failure
     */
    public abstract MCPProtocol.Content execute(Parameters params,
                                                SwingToolContext context) throws Exception;

    /**
     * Whether this tool changes the application. A mutation returns before its action runs
     * (D_fire_and_forget_dispatch); the session's ref map is cleared when {@link #execute}
     * returns normally and kept when it throws, so the model can retry without re-snapshotting.
     */
    public abstract boolean isMutation();

    /**
     * Refuses a component without user-facing selection — naming row-selection mode for a
     * {@code JTable} that is not in it.
     *
     * @param toolName names the tool in the generic refusal, e.g. {@code "swing_get_selection"}
     * @throws MCPErrorResponseException if the component does not support selection
     */
    protected static void requireSelectable(Accessible accessible, String toolName)
            throws MCPErrorResponseException {
        if (!SwingUtils.supportsSelection(accessible)) {
            if (accessible instanceof JTable) {
                throw new MCPErrorResponseException(
                        "JTable is not in row-selection mode. Only row selection is supported.");
            }
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support " + toolName
                            + ". Call swing_snapshot or swing_get_cells to verify the list of actions.");
        }
    }

    /**
     * Refuses a target of {@code swing_get_items} / {@code swing_get_item_count} that fails
     * {@link SwingUtils#supportsGetItems} — which, unlike {@link #requireSelectable}, passes a
     * {@code JTable} in any selection mode.
     *
     * @param toolName names the tool in the refusal, e.g. {@code "swing_get_items"}
     * @throws MCPErrorResponseException if the component does not support the tool
     */
    protected static void requireGetItemsSupported(Accessible accessible, String toolName)
            throws MCPErrorResponseException {
        if (SwingUtils.supportsGetItems(accessible)) {
            return;
        }
        throw new MCPErrorResponseException(
                ComponentClassResolver.resolveClassName(accessible)
                        + " does not support " + toolName
                        + ". Call swing_snapshot or swing_get_cells to verify the list of actions.");
    }

    /**
     * Refuses a target of {@code swing_get_cells} / {@code swing_get_cell_count} whose role is
     * not LIST or TREE; a {@code JTable} gets a refusal redirecting to the row tool instead
     * (D_no_jtable_cells).
     *
     * @param toolName         names the tool in the refusal, e.g. {@code "swing_get_cells"}
     * @param jtableRedirectTo the row tool a {@code JTable} refusal names, e.g.
     *                         {@code "swing_get_items"}
     * @throws MCPErrorResponseException if the component does not support the tool
     */
    protected static void requireGetCellsSupported(Accessible accessible,
                                                   String toolName,
                                                   String jtableRedirectTo)
            throws MCPErrorResponseException {
        if (SwingUtils.isGetCellsSupported(accessible)) {
            return;
        }
        if (accessible instanceof JTable) {
            throw new MCPErrorResponseException(
                    "JTable does not support " + toolName
                            + ". Table cells are plain text labels \u2014 use "
                            + jtableRedirectTo + " to page through rows.");
        }
        throw new MCPErrorResponseException(
                ComponentClassResolver.resolveClassName(accessible)
                        + " does not support " + toolName
                        + ". Call swing_snapshot or swing_get_cells to verify the list of actions.");
    }

    /**
     * {@link #requireSelectable}, then refuses a single-selection component too.
     *
     * @throws MCPErrorResponseException if the component does not support multi-selection
     */
    protected static void requireMultiSelectable(Accessible accessible, String toolName)
            throws MCPErrorResponseException {
        requireSelectable(accessible, toolName);
        if (SwingUtils.supportsSingleSelection(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is in single-selection mode. " + toolName + " requires multi-selection.");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // D_dispatched_echo: mutation-tool success echo helpers
    // ════════════════════════════════════════════════════════════════════════

    private static final String SWING_TOOL_PREFIX = "swing_";

    /**
     * The action slot of the echo, from {@link #getName()}: {@code "swing_set_text"} yields
     * {@code "set-text"}.
     *
     * @throws IllegalStateException if the tool name does not start with {@code swing_}
     */
    protected final String getEchoAction() {
        String name = getName();
        if (!name.startsWith(SWING_TOOL_PREFIX)) {
            throw new IllegalStateException(
                    "Tool name must start with '" + SWING_TOOL_PREFIX + "': " + name);
        }
        return name.substring(SWING_TOOL_PREFIX.length()).replace('_', '-');
    }

    /**
     * The success echo without a value:
     *
     * <pre>
     * Dispatched click on ref=4 — call swing_snapshot to verify the outcome
     * </pre>
     */
    protected final MCPProtocol.Content echo(int ref) {
        return MCPProtocol.Content.text("Dispatched " + getEchoAction() + " on ref=" + ref
                + " — call swing_snapshot to verify the outcome");
    }

    /**
     * The success echo with a value:
     *
     * <pre>
     * Dispatched set-text on ref=4 to "new" — call swing_snapshot to verify the outcome
     * </pre>
     *
     * @param renderedValue inserted verbatim — render it first with {@link #renderEchoString},
     *                      {@link #renderEchoNumber} or {@link #renderEchoIntArray}
     */
    protected final MCPProtocol.Content echo(int ref, String renderedValue) {
        return MCPProtocol.Content.text(
                "Dispatched " + getEchoAction() + " on ref=" + ref + " to " + renderedValue
                        + " — call swing_snapshot to verify the outcome");
    }

    /**
     * Double-quotes a string for the echo, cutting one longer than 15 characters to 14 plus an
     * ellipsis: {@code "new"} stays {@code "new"}, {@code "this is a pretty long message"}
     * becomes {@code "this is a pret…"}.
     */
    protected static String renderEchoString(String value) {
        if (value.length() <= 15) {
            return '"' + value + '"';
        }
        return '"' + value.substring(0, 14) + '\u2026' + '"';
    }

    /**
     * Renders a number for the echo, bare and integer-when-whole ({@link SwingUtils#serializeNumber}):
     * {@code 75.0} becomes {@code 75}.
     */
    protected static String renderEchoNumber(Number value) {
        return String.valueOf(SwingUtils.serializeNumber(value));
    }

    /**
     * Renders integers for the echo as an array of at most 15 characters, cutting at an element
     * boundary: {@code [0, 2]} stays whole, {@code 0..9} becomes {@code [0, 1, 2, 3, …]}. A first
     * element too long to fit is emitted anyway, as {@code [<first>, …]}.
     *
     * @param values rendered in iteration order
     */
    protected static String renderEchoIntArray(Iterable<Integer> values) {
        StringBuilder full = new StringBuilder("[");
        boolean first = true;
        for (int v : values) {
            if (!first) {
                full.append(", ");
            }
            full.append(v);
            first = false;
        }
        full.append(']');
        if (full.length() <= 15) {
            return full.toString();
        }
        final String suffix = ", \u2026]"; // 4 chars
        StringBuilder truncated = new StringBuilder("[");
        boolean any = false;
        for (int v : values) {
            String addition = (any ? ", " : "") + v;
            if (truncated.length() + addition.length() + suffix.length() > 15) {
                break;
            }
            truncated.append(addition);
            any = true;
        }
        if (any) {
            return truncated.append(suffix).toString();
        }
        Iterator<Integer> it = values.iterator();
        return "[" + it.next() + suffix;
    }
}
