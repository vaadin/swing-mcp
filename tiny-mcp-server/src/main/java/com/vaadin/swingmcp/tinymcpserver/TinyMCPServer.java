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
     *
     * <p>The parameter map is always non-null, even when no parameters are defined or passed.
     * Values are typed according to their schema: {@code String} for string parameters,
     * {@code Integer} for integer parameters, {@code Double} for number parameters,
     * {@code Boolean} for boolean parameters, {@code List<Object>} for array parameters,
     * and {@code Map<String, Object>} for object parameters. Elements and values inside
     * arrays and objects follow the same Java type mapping recursively.
     * Optional parameters absent from the call are not included in the map.
     *
     * <p>The MCP specification defines {@code CallToolResult.content} as a required
     * JSON array with no minimum size. Returning {@code null} produces an empty
     * content array ({@code []}); returning a non-null {@link MCPProtocol.Content}
     * produces a single-element array.
     *
     * <p>Throwing {@link MCPErrorResponseException} produces {@code isError=true} with
     * the exception's message as the text content (no Java class name prefix).
     * Throwing any other exception also produces {@code isError=true} but uses
     * {@link Throwable#toString()} as the text content.
     * Throwing {@link MCPServerException} sends a JSON-RPC protocol error instead.
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
     * The port requested in the constructor. May be {@code 0}, meaning
     * "let the OS pick an ephemeral port at bind time". After {@link #start()},
     * the actual bound port is available via {@link #getPort()}.
     */
    private final int port;
    private final String contextPath;
    private final MCPProtocol.Implementation serverInfo;
    private final String instructions;
    private final MCPToolHandler toolHandler = new MCPToolHandler();
    private volatile boolean started = false;
    private HttpServer httpServer;
    /** Populated by {@link #start()} once the OS has assigned a port. */
    private volatile int boundPort = -1;

    private final ConcurrentHashMap<String, MCPSession> sessions = new ConcurrentHashMap<>();
    /** Guards all session-map mutations (put, remove, clear) to prevent races. */
    private final Object sessionGuardLock = new Object();

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
     * @param name        the tool name; not null, not blank
     * @param description human-readable description of the tool; not null, not blank
     * @param inputSchema the parameter schema; not null; consider using {@link InputSchemaBuilder}
     * @param function    the handler to invoke when the tool is called; not null
     * @throws IllegalArgumentException if any argument is null or blank
     * @throws IllegalStateException    if the server has already been started
     * @throws IllegalStateException    if a tool with the same name is already registered
     */
    public void addTool(String name, String description, MCPProtocol.InputSchema inputSchema, ToolFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add tools after server has been started");
        }
        toolHandler.addTool(name, description, inputSchema, function);
    }

    public void start() throws IOException {
        started = true;
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        // If port was 0, the OS assigned an ephemeral port; capture the actual port.
        boundPort = httpServer.getAddress().getPort();
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        httpServer.createContext(contextPath, this::handleRequest);
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
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            boundPort = -1;
            LOG.info("TinyMCPServer stopped");
        }
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
                    rpc.sendPlain(405, "Method Not Allowed");
                    break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error handling request", e);
            try {
                rpc.sendPlain(500, "Internal Server Error");
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Failed to send error response", ioe);
            }
        }
    }

    private void handlePost(JsonRpcExchange rpc) throws IOException {
        // Session ID header validation: if present, must match a known session
        String incomingSessionId = rpc.getHttpExchange().getRequestHeaders()
                .getFirst("Mcp-Session-Id");
        if (incomingSessionId != null && !sessions.containsKey(incomingSessionId)) {
            LOG.warning("Rejecting request: unknown Mcp-Session-Id " + incomingSessionId);
            rpc.sendError(404,
                    MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
            return;
        }

        MCPProtocol.JsonRpcRequest request = rpc.parsePost();
        if (request == null) return;

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
            rpc.sendError(400,
                    MCPServerException.SERVER_NOT_INITIALIZED,
                    "Server not initialized. Send 'initialize' first.");
            return;
        }
        MCPSession session = sessions.get(incomingSessionId);
        if (session == null) {
            // Race: session removed between header check and lookup
            rpc.sendError(404,
                    MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
            return;
        }

        rpc.setSessionId(session.getId());
        session.handlePost(rpc, request);
    }

    private void handleInitialize(JsonRpcExchange rpc) throws IOException {
        MCPSession session;
        synchronized (sessionGuardLock) {
            if (!acceptNewSession()) {
                LOG.warning("Rejecting initialization: acceptNewSession() returned false");
                rpc.sendError(409, -32002, "Another session is already active");
                return;
            }
            String sessionId = UUID.randomUUID().toString();
            session = new MCPSession(sessionId, toolHandler);
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

    private void handlePing(JsonRpcExchange rpc) throws IOException {
        rpc.sendResponseRaw("{}");
    }

    private void handleDelete(JsonRpcExchange rpc) throws IOException {
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
     * Called when a session has been closed (via HTTP DELETE), <em>after</em>
     * the session has been removed from the session map. Subclasses can
     * override this to release session-scoped resources.
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
}
