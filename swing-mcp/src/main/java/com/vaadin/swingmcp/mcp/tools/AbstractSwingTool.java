package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.swing.JTable;

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
     * Mutation tools have their ref map cleared after execution and dispatch
     * their action via {@code SwingUtilities.invokeLater()} (fire-and-forget).
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
                    "Component does not support " + toolName
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
                "Component does not support " + toolName
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
                "Component does not support " + toolName
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
}
