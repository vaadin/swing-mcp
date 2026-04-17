package com.vaadin.swingmcp.tinymcpserver;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
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
    /**
     * The session lock, prevents concurrent access to the session.
     */
    private final ReentrantLock sessionLock = new ReentrantLock();

    MCPSession(String id, MCPToolHandler toolHandler) {
        this.id = id;
        this.toolHandler = toolHandler;
    }

    /**
     * Returns this session's opaque identifier, assigned by the server at
     * session creation and surfaced to clients via the {@code Mcp-Session-Id}
     * HTTP header.
     */
    public String getId() {
        return id;
    }

    void handlePost(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) throws IOException {
        sessionLock.lock();
        try {
            instance.set(this);
            doHandlePost(rpc,  request);
        } finally {
            instance.remove();
            sessionLock.unlock();
        }
    }

    /**
     * Dispatches a parsed JSON-RPC request to the appropriate handler.
     * Called for all session-scoped methods (everything except
     * {@code initialize} and {@code ping}, which are handled by
     * {@link TinyMCPServer}).
     */
    private void doHandlePost(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) throws IOException {
        checkLocked();
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

    private void checkLocked() {
        if (!sessionLock.isLocked()) {
            throw new IllegalStateException("Invalid state: running outside of MCP session thread");
        }
    }

    private void handleResourcesList(JsonRpcExchange rpc) throws IOException {
        checkLocked();
        MCPProtocol.ListResourcesResult result = new MCPProtocol.ListResourcesResult();
        result.setResources(Collections.emptyList());
        rpc.sendResponse(result);
    }

    private void handlePromptsList(JsonRpcExchange rpc) throws IOException {
        checkLocked();
        MCPProtocol.ListPromptsResult result = new MCPProtocol.ListPromptsResult();
        result.setPrompts(Collections.emptyList());
        rpc.sendResponse(result);
    }

    /**
     * Returns the value of the named session attribute, or {@code null} if
     * no attribute with that name is set (or if the stored value is itself
     * {@code null}).
     * <p>
     * Attributes let handlers and tool callbacks stash session-scoped state
     * (such as user-specific caches or ref maps) that lives for the lifetime
     * of the session and is discarded when the session is closed.
     *
     * @param name attribute name; must not be {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public Object getAttribute(String name) {
        checkLocked();
        Objects.requireNonNull(name);
        return attributes.get(name);
    }

    /**
     * Stores a session attribute under the given name, replacing any previous
     * value. Passing a {@code null} value clears the existing mapping as far
     * as {@link #getAttribute(String)} is concerned (it will return {@code null}).
     *
     * @param name  attribute name; must not be {@code null}
     * @param value attribute value; may be {@code null}
     * @throws NullPointerException if {@code name} is {@code null}
     */
    public void setAttribute(String name, Object value) {
        checkLocked();
        Objects.requireNonNull(name);
        attributes.put(name, value);
    }

    /**
     * Type-safe variant of {@link #getAttribute(String)} that keys the
     * attribute by the fully-qualified name of {@code type}. Returns
     * {@code null} if no value is stored under that key.
     *
     * @param type the class whose name is used as the attribute key
     * @param <T>  the expected attribute type
     * @throws ClassCastException if a value is stored under this key but is
     *                            not an instance of {@code type} (can occur
     *                            if the same key was previously set via the
     *                            {@link #setAttribute(String, Object)} overload)
     */
    public <T> T getAttribute(Class<T> type) {
        checkLocked();
        return type.cast(getAttribute(type.getName()));
    }

    /**
     * Type-safe variant of {@link #setAttribute(String, Object)} that keys
     * the attribute by the fully-qualified name of {@code type}. Intended
     * for the common case where a handler stores a single instance of a
     * given type on the session.
     *
     * @param type  the class whose name is used as the attribute key
     * @param value attribute value; may be {@code null}
     * @param <T>   the attribute type
     */
    public <T> void setAttribute(Class<T> type, T value) {
        checkLocked();
        setAttribute(type.getName(), value);
    }

    /**
     * Thread-local binding to the session currently dispatching a request.
     * Set by {@link TinyMCPServer} around {@link #handlePost} and cleared
     * immediately after; visible only on the HTTP dispatch thread.
     */
    private static final ThreadLocal<MCPSession> instance = new ThreadLocal<>();

    /**
     * Returns the session whose request is being dispatched on the current
     * thread. Intended for tool callbacks and other handler code that needs
     * to reach its session without having it passed explicitly — for example,
     * to read or write session attributes.
     * <p>
     * Only bound on the HTTP dispatch thread for the duration of
     * {@link #handlePost}. If a tool marshals its work onto another thread
     * (e.g. the Swing EDT), resolve the session attribute <em>before</em> crossing the
     * thread boundary and capture it into the other thread's closure.
     *
     * @return the current session, never {@code null}
     * @throws NullPointerException if no session is bound to the current
     *                              thread (i.e. this method was called outside
     *                              of a session-dispatched request)
     */
    public static MCPSession getCurrent() {
        return Objects.requireNonNull(instance.get(), "Not running in a MCP session");
    }

    /**
     * For use by tests only: runs {@code block} with the session lock held
     * and {@link #instance} bound to this session, mirroring what
     * {@link #handlePost} sets up in production. Lets unit tests exercise
     * lock-guarded accessors (such as {@link #getAttribute(String)} and
     * {@link #setAttribute(String, Object)}) without dispatching a real
     * HTTP request.
     */
    void runLocked(Runnable block) {
        sessionLock.lock();
        try {
            instance.set(this);
            try {
                block.run();
            } finally {
                instance.remove();
            }
        } finally {
            sessionLock.unlock();
        }
    }
}
