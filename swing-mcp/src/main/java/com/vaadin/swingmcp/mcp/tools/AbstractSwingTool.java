package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import java.util.concurrent.Callable;

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
     * @return true if this tool mutates Swing app: e.g. clicks a button or the like.
     * For example snapshot/screenshot doesn't mutate the app.
     */
    public abstract boolean isMutation();

    /**
     * Optional post-EDT verification, set by {@link #execute} when the tool's
     * side-effect may complete asynchronously (e.g. window disposal posted via
     * {@code invokeLater}). {@code null} means no verification needed.
     * <p>
     * Set during {@code execute()} on the EDT; read and cleared by
     * {@code MCPServer.registerTool} on the HTTP thread immediately after
     * {@code runInEDT} returns. Safe because the wrapper holds the MCPServer
     * lock for the full duration of the tool call.
     * <p>
     * See architecture.md §2 — PostVerification.
     */
    public PostVerification postVerification;

    /**
     * Describes a bounded polling check that {@code MCPServer.registerTool}
     * performs on the HTTP thread after the EDT phase of a tool call, so the
     * EDT is free to drain any {@code invokeLater} tasks between checks.
     * <p>
     * See architecture.md §2 — PostVerification.
     */
    public static final class PostVerification {
        /** Sleep durations between checks (milliseconds). */
        public final int[] delayScheduleMs;
        /** Checked on the EDT; returns {@code true} when the side-effect has completed. */
        public final Callable<Boolean> isDone;
        /** Returned as the tool result when all delays are exhausted and {@code isDone} is still false. */
        public final String pendingMessage;

        public PostVerification(int[] delayScheduleMs, Callable<Boolean> isDone, String pendingMessage) {
            this.delayScheduleMs = delayScheduleMs;
            this.isDone = isDone;
            this.pendingMessage = pendingMessage;
        }
    }
}
