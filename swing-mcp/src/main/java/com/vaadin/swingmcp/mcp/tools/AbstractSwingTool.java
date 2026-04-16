package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.swing.JTable;
import java.util.Iterator;

/**
 * Base class for all Swing MCP tools. Subclasses implement
 * {@link #execute(Parameters, SwingToolContext)} which is guaranteed to run on the EDT
 * (or the test-equivalent) and receives a context containing the considered
 * components.
 * <p>
 * Registration is handled by {@code MCPServer.registerTool(AbstractSwingTool)}
 * which wraps this in a {@code TinyMCPServer.ToolFunction} that marshals onto
 * the EDT and resolves the context before calling
 * {@link #execute(Parameters, SwingToolContext)}.
 */
public abstract class AbstractSwingTool {

    public static final String TOOL_SWING_SNAPSHOT = "swing_snapshot";
    public static final String TOOL_SWING_SCREENSHOT = "swing_screenshot";
    public static final String TOOL_SWING_CLICK = "swing_click";
    public static final String TOOL_SWING_TOGGLE_POPUP = "swing_toggle_popup";
    public static final String TOOL_SWING_GET_TEXT = "swing_get_text";
    public static final String TOOL_SWING_SET_TEXT = "swing_set_text";
    public static final String TOOL_SWING_SET_VALUE = "swing_set_value";
    public static final String TOOL_SWING_INCREMENT = "swing_increment";
    public static final String TOOL_SWING_DECREMENT = "swing_decrement";
    public static final String TOOL_SWING_TOGGLE_EXPAND = "swing_toggle_expand";
    public static final String TOOL_SWING_GET_VALUE = "swing_get_value";
    public static final String TOOL_SWING_CLOSE = "swing_close";
    public static final String TOOL_SWING_GET_SELECTION = "swing_get_selection";
    public static final String TOOL_SWING_SET_SELECTION = "swing_set_selection";
    public static final String TOOL_SWING_CLEAR_SELECTION = "swing_clear_selection";
    public static final String TOOL_SWING_GET_ITEMS = "swing_get_items";
    public static final String TOOL_SWING_GET_ITEM_COUNT = "swing_get_item_count";
    public static final String TOOL_SWING_SELECT_ALL = "swing_select_all";
    public static final String TOOL_SWING_GET_CELLS = "swing_get_cells";
    public static final String TOOL_SWING_GET_CELL_COUNT = "swing_get_cell_count";
    public static final String TOOL_SWING_ICONIFY = "swing_iconify";
    public static final String TOOL_SWING_RESTORE = "swing_restore";
    public static final String TOOL_SWING_GET_DESCRIPTION = "swing_get_description";
    public static final String TOOL_SWING_DRAG = "swing_drag";

    /**
     * @return the MCP tool name (e.g. {@code "swing_snapshot"})
     */
    public abstract String getName();

    /**
     * @return a human-readable description of what the tool does
     */
    public abstract String getDescription();

    /**
     * @return the JSON-schema describing accepted parameters
     */
    public abstract MCPProtocol.InputSchema getInputSchema();

    /**
     * Executes the tool. Callers guarantee this runs on the EDT (or the
     * test override of {@code MCPServer.runInEDT}).
     *
     * @param params  typed wrapper around the MCP request parameters, never null
     * @param context the tool execution context, never null
     * @return the result content, or {@code null} for an empty result
     * @throws com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException to return
     *         {@code isError=true} with a clean, human-readable message
     * @throws com.vaadin.swingmcp.tinymcpserver.MCPServerException to return a JSON-RPC
     *         protocol error, e.g. {@code MCPServerException(INVALID_PARAMS, "...")} when
     *         a tool parameter has an invalid value
     * @throws Exception if tool execution fails unexpectedly
     */
    public abstract MCPProtocol.Content execute(Parameters params,
                                                SwingToolContext context) throws Exception;

    /**
     * @return true if this tool mutates the Swing app (e.g. clicks a button).
     * Snapshot and screenshot tools return false.
     * Mutation tools have their ref map cleared after successful execution
     * and dispatch their action via {@code SwingUtilities.invokeLater()}
     * (fire-and-forget). A pre-dispatch validation error (thrown as
     * {@link MCPErrorResponseException}) does not clear the ref map, so
     * the AI can retry with a different ref without re-snapshotting.
     */
    public abstract boolean isMutation();

    /**
     * Validates that the accessible supports user-facing selection, throwing an
     * {@link MCPErrorResponseException} if it does not.
     * <p>
     * Produces a JTable-specific error message when the table is not in
     * row-selection mode, and a generic message (including {@code toolName})
     * otherwise.
     *
     * @param accessible the component to check
     * @param toolName   the tool name for the generic error message
     *                   (e.g. {@code "get_selection"})
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
     * Validates that the accessible is a valid target for the read-only
     * selection-item tools ({@code swing_get_items} and
     * {@code swing_get_item_count}). Throws an
     * {@link MCPErrorResponseException} if it is not.
     *
     * <p>Delegates to {@link SwingUtils#supportsGetItems}: every
     * {@code JTable} passes regardless of selection mode; other components must
     * satisfy the standard {@code supportsSelection} gate.</p>
     *
     * @param accessible the component to check
     * @param toolName   the tool name for the error message
     *                   (e.g. {@code "get_items"})
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
     * Validates that the accessible is a valid target for {@code swing_get_cells}
     * or {@code swing_get_cell_count} — i.e. its role is LIST or TREE. Throws an
     * {@link MCPErrorResponseException} otherwise.
     *
     * <p>JTable targets receive a dedicated error that redirects the AI to
     * {@code swing_get_items} (for {@code swing_get_cells}) or
     * {@code swing_get_item_count} (for {@code swing_get_cell_count}).
     * Table cells are stamp-painted plain text labels with no actionable children,
     * so these tools can never return a useful ref for a JTable.</p>
     *
     * @param accessible       the component to check
     * @param toolName         the tool name for the generic error message
     *                         (e.g. {@code "get_cells"})
     * @param jtableRedirectTo the name of the tool the AI should use instead for
     *                         JTable (e.g. {@code "swing_get_items"})
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
     * Validates that the accessible supports multi-selection, throwing an
     * {@link MCPErrorResponseException} if it does not.
     * <p>
     * Calls {@link #requireSelectable} first, then rejects single-selection
     * components.
     *
     * @param accessible the component to check
     * @param toolName   the tool name for the generic error message
     * @throws MCPErrorResponseException if the component does not support
     *         multi-selection
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
    // DR-010: mutation-tool success echo helpers
    // ════════════════════════════════════════════════════════════════════════

    private static final String SWING_TOOL_PREFIX = "swing_";

    /**
     * Returns the action name portion of this tool's DR-010 success echo. Derived
     * from {@link #getName()} by stripping the {@code swing_} prefix and converting
     * underscores to hyphens — e.g. {@code "swing_set_text"} yields {@code "set-text"}.
     *
     * @return the action name for the DR-010 echo
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
     * Composes the DR-010 success echo without a value:
     * {@code Dispatched <action> on ref=<N> — call swing_snapshot to verify the outcome}.
     *
     * @param ref the component ref that was acted on
     * @return a single text-content item carrying the echo
     */
    protected final MCPProtocol.Content echo(int ref) {
        return MCPProtocol.Content.text("Dispatched " + getEchoAction() + " on ref=" + ref
                + " — call swing_snapshot to verify the outcome");
    }

    /**
     * Composes the DR-010 success echo with a value:
     * {@code Dispatched <action> on ref=<N> to <renderedValue> — call swing_snapshot to verify the outcome}. The caller is
     * responsible for rendering {@code renderedValue} per DR-010 — strings via
     * {@link #renderEchoString}, numbers via {@link #renderEchoNumber}, arrays
     * via {@link #renderEchoIntArray}.
     *
     * @param ref           the component ref that was acted on
     * @param renderedValue the already-rendered value text
     * @return a single text-content item carrying the echo
     */
    protected final MCPProtocol.Content echo(int ref, String renderedValue) {
        return MCPProtocol.Content.text(
                "Dispatched " + getEchoAction() + " on ref=" + ref + " to " + renderedValue
                        + " — call swing_snapshot to verify the outcome");
    }

    /**
     * Renders a string value per DR-010: double-quoted, truncated at 15 content
     * characters. Strings of 15 or fewer characters are quoted as-is; longer
     * strings are truncated to the first 14 characters with a trailing Unicode
     * ellipsis (U+2026).
     *
     * @param value the raw string
     * @return the value formatted for inclusion in the echo
     */
    protected static String renderEchoString(String value) {
        if (value.length() <= 15) {
            return '"' + value + '"';
        }
        return '"' + value.substring(0, 14) + '\u2026' + '"';
    }

    /**
     * Renders a number value per DR-010: bare (no quotes), integer-when-whole.
     * Delegates to {@link SwingUtils#serializeNumber} for the integer-when-whole
     * normalization.
     *
     * @param value the number
     * @return the value formatted for inclusion in the echo
     */
    protected static String renderEchoNumber(Number value) {
        return String.valueOf(SwingUtils.serializeNumber(value));
    }

    /**
     * Renders an integer collection per DR-010 as a JSON-style array. If the
     * rendered form is 15 characters or fewer it is returned in full; otherwise
     * leading elements are retained and a trailing {@code , …]} is appended to
     * keep the total rendered length within 15 characters (with a single-element
     * fallback when even the first element does not fit).
     *
     * @param values the integers in iteration order (e.g. a {@code LinkedHashSet})
     * @return the array formatted for inclusion in the echo
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
        // Truncated form: pack leading elements followed by ", …]" within 15 chars.
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
        // First element alone exceeds the budget — emit it with the suffix anyway.
        Iterator<Integer> it = values.iterator();
        return "[" + it.next() + suffix;
    }
}
