package com.vaadin.swingmcp.tinymcpserver;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a single MCP session and handles protocol dispatch for all
 * session-scoped requests (everything after {@code initialize}).
 * <p>
 * {@link TinyMCPServer} owns the session map and routes incoming requests
 * to the appropriate session. Server-level concerns (HTTP lifecycle,
 * session creation/destruction, {@code initialize}, {@code ping}) stay
 * on TinyMCPServer; protocol handling lives here.
 * <p>
 * Thread safety: callers must hold this session's intrinsic lock
 * ({@code synchronized(session)}) for the entire duration of
 * {@link #handlePost}.
 */
public class MCPSession {

    private static final Logger LOG = Logger.getLogger(MCPSession.class.getName());

    private final String id;
    private final MCPToolHandler toolHandler;
    private final Map<String, Object> attributes = new HashMap<>();

    MCPSession(String id, MCPToolHandler toolHandler) {
        this.id = id;
        this.toolHandler = toolHandler;
    }

    public String getId() {
        return id;
    }

    /**
     * Dispatches a parsed JSON-RPC request to the appropriate handler.
     * Called for all session-scoped methods (everything except
     * {@code initialize} and {@code ping}, which are handled by
     * {@link TinyMCPServer}).
     */
    void handlePost(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) throws IOException {
        String rpcMethod = request.getMethod();
        try {
            switch (rpcMethod != null ? rpcMethod : "") {
                case "tools/list":
                    toolHandler.handleToolsList(rpc);
                    break;
                case "tools/call":
                    toolHandler.handleToolsCall(rpc, request);
                    break;
                case "resources/list":
                    handleResourcesList(rpc);
                    break;
                case "prompts/list":
                    handlePromptsList(rpc);
                    break;
                default:
                    rpc.sendError(-32601, "Method not found: " + rpcMethod);
                    break;
            }
        } catch (MCPServerException e) {
            LOG.log(Level.FINE, "Method '" + rpcMethod + "' threw MCPServerException (code=" + e.getCode() + ")", e);
            rpc.sendError(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Method '" + rpcMethod + "' threw an exception", e);
            rpc.sendError(MCPServerException.INTERNAL_ERROR, "Internal error: " + e);
        }
    }

    private void handleResourcesList(JsonRpcExchange rpc) throws IOException {
        MCPProtocol.ListResourcesResult result = new MCPProtocol.ListResourcesResult();
        result.setResources(Collections.emptyList());
        rpc.sendResponse(result);
    }

    private void handlePromptsList(JsonRpcExchange rpc) throws IOException {
        MCPProtocol.ListPromptsResult result = new MCPProtocol.ListPromptsResult();
        result.setPrompts(Collections.emptyList());
        rpc.sendResponse(result);
    }

    public Object getAttribute(String name) {
        Objects.requireNonNull(name);
        return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
        Objects.requireNonNull(name);
        attributes.put(name, value);
    }

    public <T> T getAttribute(Class<T> type) {
        return type.cast(getAttribute(type.getName()));
    }

    public <T> void setAttribute(Class<T> type, T value) {
        setAttribute(type.getName(), value);
    }

    static final ThreadLocal<MCPSession> instance = new ThreadLocal<>();

    public static MCPSession getCurrent() {
        return Objects.requireNonNull(instance.get(), "Not running in a MCP session");
    }
}
