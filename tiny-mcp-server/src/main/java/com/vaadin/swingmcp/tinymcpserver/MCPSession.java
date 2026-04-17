package com.vaadin.swingmcp.tinymcpserver;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

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

    private final String id;
    private final MCPToolHandler toolHandler;
    private final MCPResourceHandler resourceHandler;
    private final MCPPromptHandler promptHandler;
    private final TinyMCPServer server;
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * The session lock, prevents concurrent access to the session.
     */
    private final ReentrantLock sessionLock = new ReentrantLock();

    /**
     * Monotonic timestamp of the last dispatched request, used by the
     * server's idle-cleanup tick to decide when to evict this session.
     * Updated under the session lock in {@link #handlePost}; read without
     * the lock from the cleanup thread.
     */
    private volatile long lastAccessNanos = System.nanoTime();

    /**
     * Set to {@code true} by {@link #tryClose()} to mark this session as
     * evicted. Any further dispatch on this session will throw a 404
     * {@link MCPServerException}. Checked under the session lock to
     * serialise with in-flight requests.
     */
    private volatile boolean closed = false;

    MCPSession(String id, MCPToolHandler toolHandler, MCPResourceHandler resourceHandler,
            MCPPromptHandler promptHandler, TinyMCPServer server) {
        this.id = id;
        this.toolHandler = toolHandler;
        this.resourceHandler = resourceHandler;
        this.promptHandler = promptHandler;
        this.server = server;
    }

    /**
     * Returns this session's opaque identifier, assigned by the server at
     * session creation and surfaced to clients via the {@code Mcp-Session-Id}
     * HTTP header.
     */
    public String getId() {
        return id;
    }

    /**
     * Dispatches a parsed JSON-RPC request to the appropriate handler.
     * Called for all session-scoped methods (everything except
     * {@code initialize} and {@code ping}, which are handled by
     * {@link TinyMCPServer}).
     */
    void handlePost(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) {
        runLocked(() -> doHandlePost(rpc, request));
    }

    /**
     * Returns the monotonic {@link System#nanoTime()} value recorded at the
     * start of the most recently dispatched request, or at session creation
     * if no request has yet been dispatched. Read without the session lock;
     * callers use this for idle-detection only.
     */
    long getLastAccessNanos() {
        return lastAccessNanos;
    }

    /**
     * For use by tests only: forces the recorded last-access timestamp to a
     * given {@link System#nanoTime()} value, allowing the cleanup tick to be
     * exercised without waiting real wall-clock time.
     */
    void setLastAccessNanos(long nanos) {
        this.lastAccessNanos = nanos;
    }

    /**
     * Attempts to close this session. Acquires the session lock
     * non-blockingly; if an in-flight request holds it, returns {@code false}
     * and leaves the session untouched — the caller should retry on the next
     * cleanup tick. On success, sets the {@code closed} flag so any later
     * dispatch on this session fails with a 404.
     */
    boolean tryClose() {
        if (!sessionLock.tryLock()) {
            return false;
        }
        try {
            closed = true;
        } finally {
            sessionLock.unlock();
        }
        return true;
    }

    /**
     * Returns the {@link TinyMCPServer} that owns this session, giving tool
     * handlers access to the shared scheduled executor (see
     * {@link TinyMCPServer#getExecutor()}) and other server-level services.
     *
     * @throws IllegalStateException if this session was constructed without
     *                               an owning server (tests only)
     */
    public TinyMCPServer getServer() {
        if (server == null) {
            throw new IllegalStateException("Session was constructed without an owning server");
        }
        return server;
    }

    private void doHandlePost(JsonRpcExchange rpc, MCPProtocol.JsonRpcRequest request) {
        checkLocked();
        String rpcMethod = request.getMethod();
        switch (rpcMethod != null ? rpcMethod : "") {
            case "tools/list":
                toolHandler.handleToolsList(rpc);
                break;
            case "tools/call":
                toolHandler.handleToolsCall(rpc, request);
                break;
            case "resources/list":
                resourceHandler.handleResourcesList(rpc);
                break;
            case "resources/read":
                resourceHandler.handleResourcesRead(rpc, request);
                break;
            case "prompts/list":
                promptHandler.handlePromptsList(rpc);
                break;
            case "prompts/get":
                promptHandler.handlePromptsGet(rpc, request);
                break;
            default:
                throw new MCPServerException(-32601, "Method not found: " + rpcMethod);
        }
    }

    private void checkLocked() {
        if (!sessionLock.isLocked()) {
            throw new IllegalStateException("Invalid state: running outside of MCP session thread");
        }
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
     * Runs {@code block} with the session lock held and {@link #instance}
     * bound to this session. Used by {@link #handlePost} for request dispatch
     * and by tests to exercise lock-guarded accessors (such as
     * {@link #getAttribute(String)} and {@link #setAttribute(String, Object)})
     * without dispatching a real HTTP request.
     * <p>
     * Also refreshes {@link #lastAccessNanos} so the cleanup tick leaves this
     * session alone, and fails fast with a 404 {@link MCPServerException} if
     * the session has already been closed by the cleanup tick (or any other
     * {@link #tryClose()} caller).
     */
    void runLocked(Runnable block) {
        sessionLock.lock();
        try {
            if (closed) {
                throw new MCPServerException(404,
                        MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
            }
            lastAccessNanos = System.nanoTime();
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
