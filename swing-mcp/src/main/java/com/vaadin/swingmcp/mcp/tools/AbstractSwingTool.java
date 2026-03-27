package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import java.util.Map;

/**
 * Base class for all Swing MCP tools. Subclasses implement
 * {@link #execute(Map, SwingToolContext)} which is guaranteed to run on the EDT
 * (or the test-equivalent) and receives a context containing the considered
 * components.
 * <p>
 * Registration is handled by {@code MCPServer.registerTool(AbstractSwingTool)}
 * which wraps this in a {@code TinyMCPServer.ToolFunction} that marshals onto
 * the EDT and resolves the context before calling
 * {@link #execute(Map, SwingToolContext)}.
 */
public abstract class AbstractSwingTool {

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
     * @param params  the parameter values from the MCP request, never null
     * @param context the tool execution context, never null
     * @return the result content, or {@code null} for an empty result
     * @throws Exception if tool execution fails
     */
    public abstract MCPProtocol.Content execute(Map<String, Object> params,
                                                SwingToolContext context) throws Exception;

    /**
     * @return true if this tool mutates Swing app: e.g. clicks a button or the like.
     * For example snapshot/screenshot doesn't mutate the app.
     */
    public abstract boolean isMutation();
}
