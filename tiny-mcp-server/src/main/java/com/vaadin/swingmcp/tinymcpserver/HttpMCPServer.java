package com.vaadin.swingmcp.tinymcpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A minimal in-process MCP server using Java's built-in HttpServer.
 * Binds to 127.0.0.1 only.
 * <p>
 * This class is the HTTP transport: it owns the {@link HttpServer},
 * routes incoming POST/DELETE requests, validates the
 * {@code Mcp-Session-Id} header, and writes JSON-RPC responses to the
 * wire. All protocol logic — registries, session lifecycle, dispatch of
 * {@code initialize}/{@code ping}/session-scoped methods — lives in the
 * {@link MCPHandler} supplied at construction. Configure the handler
 * (tools/resources/prompts, optional session-cap predicate, optional
 * close callback) before calling {@link #start()}.
 */
public class HttpMCPServer {

    private static final Logger LOG = Logger.getLogger(HttpMCPServer.class.getName());

    public static final int DEFAULT_PORT = 18088;
    public static final String DEFAULT_CONTEXT_PATH = "/mcp";

    /**
     * The port requested in the constructor. May be {@code 0}, meaning
     * "let the OS pick an ephemeral port at bind time". After {@link #start()},
     * the actual bound port is available via {@link #getPort()}.
     */
    private final int port;
    private final String contextPath;
    private final MCPHandler handler;
    private HttpServer httpServer;
    /** Populated by {@link #start()} once the OS has assigned a port. */
    private volatile int boundPort = -1;

    /** Convenience: default port and context path, fresh empty handler. */
    public HttpMCPServer() {
        this(DEFAULT_PORT, DEFAULT_CONTEXT_PATH, new MCPHandler());
    }

    /** Convenience: caller-supplied port/context, fresh empty handler. */
    public HttpMCPServer(int port, String contextPath) {
        this(port, contextPath, new MCPHandler());
    }

    /**
     * @param port        the TCP port to bind to, or {@code 0} to let the OS pick a free
     *                    ephemeral port. After {@link #start()}, {@link #getPort()} returns
     *                    the actual bound port.
     * @param contextPath the URL context path; must start with a slash
     * @param handler     the configured {@link MCPHandler}; not null. Tools and other
     *                    registrations should be added before {@link #start()}.
     */
    public HttpMCPServer(int port, String contextPath, MCPHandler handler) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Parameter port: invalid value " + port + ": must be in [0, 65535]");
        }
        this.port = port;
        Objects.requireNonNull(contextPath, "contextPath");
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Parameter contextPath: invalid value " + contextPath + ": must start with a slash");
        }
        this.contextPath = contextPath;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort() + contextPath;
    }

    public void start() {
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
        handler.start();
        // A remote client can vanish without sending DELETE, so HTTP — and
        // only HTTP — evicts on idle (D_stdio_never_evicts).
        handler.scheduleIdleCleanup();
        // Start from a daemon thread so that HTTP-Dispatcher inherits daemon status,
        // preventing it from keeping the JVM alive after the host application closes.
        Thread starter = new Thread(httpServer::start, "mcp-server-starter");
        starter.setDaemon(true);
        starter.start();
        try {
            starter.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("HttpMCPServer started on " + getUrl());
    }

    public void stop() {
        handler.stop();
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            boundPort = -1;
            LOG.info("HttpMCPServer stopped");
        }
    }

    /**
     * Returns the {@link MCPHandler} this server delegates protocol dispatch to.
     */
    public MCPHandler getHandler() {
        return handler;
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
        if (incomingSessionId != null && handler.getSession(incomingSessionId) == null) {
            LOG.warning("Rejecting request: unknown Mcp-Session-Id " + incomingSessionId);
            throw new MCPServerException(404,
                    MCPServerException.SERVER_NOT_INITIALIZED,
                    handler.tombstoneOrDefault(incomingSessionId));
        }

        MCPProtocol.JsonRpcRequest request = rpc.parsePost();
        if (request == null) return; // notification — 202 already sent

        String rpcMethod = request.getMethod();

        // Server-level methods (no session required)
        if ("initialize".equals(rpcMethod)) {
            MCPHandler.InitializeOutcome outcome = handler.dispatchInitialize(request);
            rpc.setSessionId(outcome.sessionId());
            rpc.sendResponse(outcome.result());
            return;
        }
        if ("ping".equals(rpcMethod)) {
            rpc.sendResponse(handler.dispatchPing());
            return;
        }

        // Session-scoped methods — require Mcp-Session-Id header
        if (incomingSessionId == null) {
            LOG.warning("Rejecting '" + rpcMethod + "': no Mcp-Session-Id header");
            throw new MCPServerException(400,
                    MCPServerException.SERVER_NOT_INITIALIZED,
                    "Server not initialized. Send 'initialize' first.");
        }
        MCPSession session = handler.getSession(incomingSessionId);
        if (session == null) {
            // Race: session removed between header check and lookup
            throw new MCPServerException(404,
                    MCPServerException.SERVER_NOT_INITIALIZED,
                    handler.tombstoneOrDefault(incomingSessionId));
        }

        rpc.setSessionId(session.getId());
        Object result = session.handlePost(request, rpc.getTransportHeaders());
        rpc.sendResponse(result);
    }

    private void handleDelete(JsonRpcExchange rpc) {
        String incomingSessionId = rpc.getHttpExchange().getRequestHeaders()
                .getFirst("Mcp-Session-Id");
        List<MCPSession> closed;
        if (incomingSessionId != null) {
            MCPSession session = handler.removeSession(incomingSessionId);
            closed = session != null ? List.of(session) : List.of();
        } else {
            closed = handler.removeAllSessions();
        }
        for (MCPSession session : closed) {
            LOG.info("Session terminated: " + session.getId());
            handler.notifySessionClosed(session);
        }
        rpc.sendPlain(200, "");
    }

    /**
     * Test hook: synchronously runs the idle-session cleanup tick. Used by
     * {@code SessionCleanupTest}; package-private.
     */
    void cleanupIdleSessions() {
        handler.cleanupIdleSessions();
    }
}
