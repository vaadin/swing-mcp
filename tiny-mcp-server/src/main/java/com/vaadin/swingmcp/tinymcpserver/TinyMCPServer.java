package com.vaadin.swingmcp.tinymcpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A minimal in-process MCP server using Java's built-in HttpServer.
 * Supports only HTTP transport (no STDIO), binds to 127.0.0.1 only.
 * <p>
 * Handles HTTP lifecycle, session creation/destruction, and routing.
 * Protocol dispatch for session-scoped requests is delegated to
 * {@link MCPSession}.
 */
public class TinyMCPServer {

    private static final Logger LOG = Logger.getLogger(TinyMCPServer.class.getName());

    public static final int DEFAULT_PORT = 18088;
    public static final String DEFAULT_CONTEXT_PATH = "/mcp";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    /**
     * A tool handler function that receives parsed parameters and returns content.
     * Invoked synchronously on the HTTP handler thread that is serving the
     * {@code tools/call} request.
     *
     * <p>The parameter map is always non-null, even when no parameters are defined or passed.
     * Values are typed according to their schema: {@code String} for string parameters,
     * {@code Integer} for integer parameters, {@code Double} for number parameters,
     * {@code Boolean} for boolean parameters, {@code List<Object>} for array parameters,
     * and {@code Map<String, Object>} for object parameters. Elements and values inside
     * arrays and objects follow the same Java type mapping recursively. The function
     * never receives raw GSON {@code JsonElement} instances. Optional parameters
     * absent from the call are not included in the map.
     *
     * <p>Verified against the MCP specification (2025-03-26 schema):
     * {@code CallToolResult.content} is a required JSON array with no {@code minItems}
     * constraint, so an empty array is valid. Returning {@code null} produces an empty
     * content array ({@code "content": []}) — useful for mutation tools that have
     * nothing to report. Returning a non-null {@link MCPProtocol.Content} produces a
     * single-element array.
     *
     * <p>Throwing {@link MCPErrorResponseException} produces {@code isError=true} with
     * the exception's message as the text content (no Java class name prefix).
     * Throwing any other exception also produces {@code isError=true} but uses
     * {@link Throwable#toString()} (class name + message, no stacktrace) as the text
     * content. Throwing {@link MCPServerException} sends a JSON-RPC protocol error
     * instead.
     */
    @FunctionalInterface
    public interface ToolFunction {
        /**
         * Invokes the tool.
         *
         * @param params the parameter values, never null
         * @return the result content (wrapped in a single-element array),
         *         or {@code null} for an empty result (produces {@code "content": []})
         * @throws MCPErrorResponseException to return {@code isError=true} with a clean message
         * @throws MCPServerException to return a JSON-RPC protocol error
         * @throws Exception if tool execution fails unexpectedly
         */
        MCPProtocol.Content call(Map<String, Object> params) throws Exception;
    }

    /**
     * Functional interface for resource handlers. Invoked when a client
     * requests {@code resources/read} for a registered URI.
     *
     * <p>Implementations return the current contents of the resource. The
     * returned list must not be {@code null}; it typically contains a single
     * {@link MCPProtocol.ResourceContents} entry, but the MCP spec allows
     * multiple (e.g. for composite resources).
     *
     * <p>Throwing {@link MCPServerException} produces a JSON-RPC error with
     * the given code; any other exception becomes {@code INTERNAL_ERROR}.
     */
    @FunctionalInterface
    public interface ResourceFunction {
        /**
         * @param uri the URI of the resource being read (matches the
         *            registered URI verbatim)
         * @return the resource contents; must not be null
         * @throws MCPServerException to return a JSON-RPC protocol error
         * @throws Exception          if resource loading fails unexpectedly
         */
        java.util.List<MCPProtocol.ResourceContents> call(String uri) throws Exception;
    }

    /**
     * Functional interface for prompt handlers. Invoked when a client
     * requests {@code prompts/get}. Arguments have already been validated
     * against the registered schema (required / unknown-arg checks), so
     * implementations can read them directly.
     */
    @FunctionalInterface
    public interface PromptFunction {
        /**
         * @param arguments the argument map, always non-null and containing
         *                  only declared keys; missing optional arguments
         *                  are simply absent
         * @return the prompt result; must not be null
         * @throws MCPServerException to return a JSON-RPC protocol error
         * @throws Exception          if prompt expansion fails unexpectedly
         */
        MCPProtocol.GetPromptResult call(Map<String, String> arguments) throws Exception;
    }

    /**
     * The port requested in the constructor. May be {@code 0}, meaning
     * "let the OS pick an ephemeral port at bind time". After {@link #start()},
     * the actual bound port is available via {@link #getPort()}.
     */
    private final int port;
    private final String contextPath;
    private final MCPProtocol.Implementation serverInfo;
    private final String instructions;
    private final MCPToolHandler toolHandler = new MCPToolHandler();
    private final MCPResourceHandler resourceHandler = new MCPResourceHandler();
    private final MCPPromptHandler promptHandler = new MCPPromptHandler();
    private volatile boolean started = false;
    private HttpServer httpServer;
    /** Populated by {@link #start()} once the OS has assigned a port. */
    private volatile int boundPort = -1;

    private final ConcurrentHashMap<String, MCPSession> sessions = new ConcurrentHashMap<>();
    /** Guards all session-map mutations (put, remove, clear) to prevent races. */
    private final Object sessionGuardLock = new Object();

    /**
     * Sessions idle for at least this long are evicted by the cleanup tick.
     * Package-private so tests can substitute a short value via reflection.
     */
    static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(30);
    /** Interval between cleanup ticks. */
    static final long CLEANUP_TICK_SECONDS = 60;

    /**
     * Shared scheduled executor. Created in {@link #start()}, shut down in
     * {@link #stop()}. Runs the session-idle-cleanup tick and is exposed via
     * {@link #getExecutor()} for tool handlers that need background work.
     */
    private ScheduledExecutorService executor;

    public TinyMCPServer() {
        this(DEFAULT_PORT, DEFAULT_CONTEXT_PATH);
    }

    public TinyMCPServer(int port, String contextPath) {
        this(port, contextPath, new MCPProtocol.Implementation(), null);
    }

    public TinyMCPServer(int port, String contextPath, MCPProtocol.Implementation serverInfo) {
        this(port, contextPath, serverInfo, null);
    }

    /**
     * @param port the TCP port to bind to, or {@code 0} to let the OS pick a free
     *             ephemeral port. After {@link #start()}, {@link #getPort()} returns
     *             the actual bound port.
     */
    public TinyMCPServer(int port, String contextPath, MCPProtocol.Implementation serverInfo, String instructions) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Parameter port: invalid value " + port + ": must be in [0, 65535]");
        }
        this.port = port;
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Parameter contextPath: invalid value " + contextPath + ": must start with a slash");
        }
        this.contextPath = contextPath;
        this.serverInfo = serverInfo != null ? serverInfo : new MCPProtocol.Implementation();
        this.instructions = instructions;
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort() + contextPath;
    }

    /**
     * Registers a tool with this server. Must be called before {@link #start()}.
     *
     * <p>Registered tools are advertised by {@code tools/list} (name, description,
     * and {@code inputSchema} passed as-is) and invoked via {@code tools/call}.
     *
     * <p>{@code tools/call} dispatch rules:
     * <ul>
     *   <li>Unknown tool name → JSON-RPC error {@code -32601} (Method not found).</li>
     *   <li>Missing required parameter → JSON-RPC error {@code -32602} with message
     *       {@code Missing required parameter '<name>'}. A {@code null} argument value
     *       is treated as missing.</li>
     *   <li>JSON numbers are deserialized by GSON as {@code Double}; for parameters
     *       declared as {@code integer}, whole-number doubles are coerced to
     *       {@code Integer}, and fractional doubles are rejected with {@code -32602}.</li>
     *   <li>Unknown parameters are silently ignored (a warning is logged).</li>
     *   <li>The tool function is invoked synchronously on the HTTP handler thread.</li>
     * </ul>
     *
     * @param name        the tool name; not null, not blank; must match
     *                    {@code [a-zA-Z_][a-zA-Z0-9_]*}
     * @param description human-readable description of the tool; not null, not blank
     * @param inputSchema the parameter schema; not null; consider using {@link InputSchemaBuilder}
     * @param function    the handler to invoke when the tool is called; not null
     * @throws IllegalArgumentException if any argument is null, blank, or (for
     *                                  {@code name}) does not match the required pattern
     * @throws IllegalStateException    if the server has already been started
     * @throws IllegalStateException    if a tool with the same name is already registered
     */
    public void addTool(String name, String description, MCPProtocol.InputSchema inputSchema, ToolFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add tools after server has been started");
        }
        toolHandler.addTool(name, description, inputSchema, function);
    }

    /**
     * Registers a resource with this server. Must be called before
     * {@link #start()}.
     *
     * <p>Registered resources are advertised by {@code resources/list}
     * (uri, name, description, mimeType) and read via {@code resources/read}.
     * The URI is the lookup key: clients pass it back verbatim in
     * {@code resources/read}, and unknown URIs produce JSON-RPC error
     * {@code -32602}.
     *
     * @param uri         the resource URI; not null, not blank; must be
     *                    unique across registered resources
     * @param name        human-readable name; not null, not blank
     * @param description human-readable description; may be null
     * @param mimeType    the resource MIME type (e.g. {@code "text/plain"});
     *                    may be null
     * @param function    the handler to invoke for {@code resources/read};
     *                    not null
     * @throws IllegalArgumentException if {@code uri}, {@code name}, or
     *                                  {@code function} is null/blank
     * @throws IllegalStateException    if the server has already been started
     * @throws IllegalStateException    if a resource with the same URI is
     *                                  already registered
     */
    public void addResource(String uri, String name, String description, String mimeType,
            ResourceFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add resources after server has been started");
        }
        resourceHandler.addResource(uri, name, description, mimeType, function);
    }

    /**
     * Registers a prompt with this server. Must be called before
     * {@link #start()}.
     *
     * @param name        the prompt name; not null, not blank
     * @param description human-readable description of the prompt; not null, not blank
     * @param arguments   the argument builder (pass an empty
     *                    {@link PromptArgumentsBuilder} for a zero-argument
     *                    prompt); not null
     * @param function    the handler to invoke for {@code prompts/get}; not null
     * @throws IllegalArgumentException if any argument is null/blank
     * @throws IllegalStateException    if the server has already been started
     * @throws IllegalStateException    if a prompt with the same name is already registered
     */
    public void addPrompt(String name, String description, PromptArgumentsBuilder arguments, PromptFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add prompts after server has been started");
        }
        promptHandler.addPrompt(name, description, arguments, function);
    }

    public void start() {
        started = true;
        try {
            httpServer = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        } catch (IOException e) {
            throw new TransportIOException("Failed to bind HTTP server on port " + port, e);
        }
        // If port was 0, the OS assigned an ephemeral port; capture the actual port.
        boundPort = httpServer.getAddress().getPort();
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        httpServer.createContext(contextPath, this::handleRequest);
        AtomicInteger threadNum = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "tiny-mcp-server-" + threadNum.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::cleanupIdleSessionsSafe,
                CLEANUP_TICK_SECONDS, CLEANUP_TICK_SECONDS, TimeUnit.SECONDS);
        // Start from a daemon thread so that HTTP-Dispatcher inherits daemon status,
        // preventing it from keeping the JVM alive after the Swing app closes.
        Thread starter = new Thread(httpServer::start, "mcp-server-starter");
        starter.setDaemon(true);
        starter.start();
        try {
            starter.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("TinyMCPServer started on " + getUrl());
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            boundPort = -1;
            LOG.info("TinyMCPServer stopped");
        }
    }

    /**
     * Returns the server's shared scheduled executor. Threads are daemon
     * and named {@code tiny-mcp-server-N}. Created by {@link #start()} and
     * shut down by {@link #stop()}.
     * <p>
     * Available to tool handlers for background work (debouncing, deferred
     * cleanup, periodic polling). Do not submit long-blocking I/O that
     * could starve session idle-cleanup — for heavy work, create your own
     * executor.
     *
     * @throws IllegalStateException if the server is not started
     */
    public ScheduledExecutorService getExecutor() {
        if (executor == null) {
            throw new IllegalStateException("Server not started");
        }
        return executor;
    }

    /**
     * Returns the port this server is bound to. Before {@link #start()} (or after
     * {@link #stop()}), returns the port requested in the constructor — which may be
     * {@code 0}, meaning the OS will pick an ephemeral port at bind time. After
     * {@code start()}, returns the actual bound port.
     */
    public int getPort() {
        return boundPort > 0 ? boundPort : port;
    }

    public String getContextPath() {
        return contextPath;
    }

    private void handleRequest(HttpExchange exchange) {
        JsonRpcExchange rpc = new JsonRpcExchange(exchange);
        try {
            String method = exchange.getRequestMethod();
            switch (method) {
                case "POST":
                    handlePost(rpc);
                    break;
                case "DELETE":
                    handleDelete(rpc);
                    break;
                default:
                    trySendPlain(rpc, 405, "Method Not Allowed");
                    break;
            }
        } catch (MCPServerException e) {
            LOG.log(Level.FINE, "Request produced MCP error (code=" + e.getCode() + ")", e);
            trySendError(rpc, e.getHttpStatus(), e.getCode(), e.getMessage());
        } catch (TransportIOException e) {
            // Transport is dead — no point trying to write another response.
            LOG.log(Level.WARNING, "Transport I/O failed; abandoning response", e);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error handling request", e);
            trySendError(rpc, 500, MCPServerException.INTERNAL_ERROR, "Internal error");
        }
    }

    private static void trySendError(JsonRpcExchange rpc, int httpStatus, int code, String message) {
        try {
            rpc.sendError(httpStatus, code, message);
        } catch (TransportIOException ioe) {
            LOG.log(Level.WARNING, "Could not send error response (transport dead)", ioe);
        }
    }

    private static void trySendPlain(JsonRpcExchange rpc, int status, String message) {
        try {
            rpc.sendPlain(status, message);
        } catch (TransportIOException ioe) {
            LOG.log(Level.WARNING, "Could not send plain response (transport dead)", ioe);
        }
    }

    private void handlePost(JsonRpcExchange rpc) {
        // Session ID header validation: if present, must match a known session
        String incomingSessionId = rpc.getHttpExchange().getRequestHeaders()
                .getFirst("Mcp-Session-Id");
        if (incomingSessionId != null && !sessions.containsKey(incomingSessionId)) {
            LOG.warning("Rejecting request: unknown Mcp-Session-Id " + incomingSessionId);
            throw new MCPServerException(404,
                    MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
        }

        MCPProtocol.JsonRpcRequest request = rpc.parsePost();
        if (request == null) return; // notification — 202 already sent

        String rpcMethod = request.getMethod();

        // Server-level methods (no session required)
        if ("initialize".equals(rpcMethod)) {
            handleInitialize(rpc);
            return;
        }
        if ("ping".equals(rpcMethod)) {
            handlePing(rpc);
            return;
        }

        // Session-scoped methods — require Mcp-Session-Id header
        if (incomingSessionId == null) {
            LOG.warning("Rejecting '" + rpcMethod + "': no Mcp-Session-Id header");
            throw new MCPServerException(400,
                    MCPServerException.SERVER_NOT_INITIALIZED,
                    "Server not initialized. Send 'initialize' first.");
        }
        MCPSession session = sessions.get(incomingSessionId);
        if (session == null) {
            // Race: session removed between header check and lookup
            throw new MCPServerException(404,
                    MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
        }

        rpc.setSessionId(session.getId());
        session.handlePost(rpc, request);
    }

    private void handleInitialize(JsonRpcExchange rpc) {
        MCPSession session;
        synchronized (sessionGuardLock) {
            if (!acceptNewSession()) {
                LOG.warning("Rejecting initialization: acceptNewSession() returned false");
                throw new MCPServerException(409,
                        MCPServerException.SERVER_NOT_INITIALIZED, "Another session is already active");
            }
            String sessionId = UUID.randomUUID().toString();
            session = new MCPSession(sessionId, toolHandler, resourceHandler, promptHandler, this);
            sessions.put(sessionId, session);
        }
        rpc.setSessionId(session.getId());

        MCPProtocol.InitializeResult result = new MCPProtocol.InitializeResult();
        result.setProtocolVersion(PROTOCOL_VERSION);

        result.setServerInfo(this.serverInfo);
        result.setInstructions(this.instructions);

        MCPProtocol.ServerCapabilities capabilities = new MCPProtocol.ServerCapabilities();
        capabilities.setTools(new MCPProtocol.ToolsCapability());
        capabilities.setResources(new MCPProtocol.ResourcesCapability());
        capabilities.setPrompts(new MCPProtocol.PromptsCapability());
        result.setCapabilities(capabilities);

        rpc.sendResponse(result);
    }

    private void handlePing(JsonRpcExchange rpc) {
        rpc.sendResponseRaw("{}");
    }

    private void handleDelete(JsonRpcExchange rpc) {
        String incomingSessionId = rpc.getHttpExchange().getRequestHeaders()
                .getFirst("Mcp-Session-Id");
        List<MCPSession> closed;
        synchronized (sessionGuardLock) {
            if (incomingSessionId != null) {
                // Close specific session
                MCPSession session = sessions.remove(incomingSessionId);
                closed = session != null ? List.of(session) : List.of();
            } else {
                // No session ID header — close all sessions
                closed = new ArrayList<>(sessions.values());
                sessions.clear();
            }
        }
        for (MCPSession session : closed) {
            LOG.info("Session terminated: " + session.getId());
            onSessionClosed(session);
        }
        rpc.sendPlain(200, "");
    }

    /**
     * Called before creating a new session. Subclasses can override this
     * to limit the number of concurrent sessions (e.g. to enforce a
     * single-session policy).
     * <p>
     * The default implementation always returns {@code true} (unlimited sessions).
     * <p>
     * This method is called while holding the session guard lock, so
     * the session count will not change between this check and the
     * session being added to the map.
     *
     * @return {@code true} to accept the new session, {@code false} to reject
     *         with HTTP 409
     */
    protected boolean acceptNewSession() {
        return true;
    }

    /**
     * Returns the number of active sessions.
     */
    protected int getSessionCount() {
        return sessions.size();
    }

    /**
     * Called when a session has been closed (via HTTP DELETE, or evicted by
     * the idle-cleanup tick), <em>after</em> the session has been removed
     * from the session map. Subclasses can override this to release
     * session-scoped resources.
     * <p>
     * The default implementation does nothing.
     * <p>
     * This method is called <em>outside</em> any server lock,
     * so implementations may safely acquire their own locks (e.g. to
     * serialise with in-flight tool calls).
     */
    protected void onSessionClosed(MCPSession session) {
        // no-op by default
    }

    private void cleanupIdleSessionsSafe() {
        try {
            cleanupIdleSessions();
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Session cleanup tick failed", t);
        }
    }

    /**
     * Evicts sessions whose last access is older than {@link #IDLE_TIMEOUT_NANOS}.
     * For each candidate, attempts {@link MCPSession#tryClose()} — if a
     * request is in flight the session is skipped and re-evaluated on the
     * next tick. Removed sessions are reported via {@link #onSessionClosed}.
     * <p>
     * Package-private so tests can invoke the tick synchronously without
     * waiting for the scheduler.
     */
    void cleanupIdleSessions() {
        long now = System.nanoTime();
        List<MCPSession> closed = new ArrayList<>();
        for (MCPSession s : sessions.values()) {
            if (now - s.getLastAccessNanos() < IDLE_TIMEOUT_NANOS) {
                continue;
            }
            if (!s.tryClose()) {
                continue;
            }
            synchronized (sessionGuardLock) {
                sessions.remove(s.getId(), s);
            }
            closed.add(s);
        }
        for (MCPSession s : closed) {
            LOG.info("Session expired (idle): " + s.getId());
            try {
                onSessionClosed(s);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "onSessionClosed threw for " + s.getId(), e);
            }
        }
    }
}
