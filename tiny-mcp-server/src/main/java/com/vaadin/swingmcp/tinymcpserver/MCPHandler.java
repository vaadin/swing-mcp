package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transport-agnostic MCP dispatch core. Owns the tool/resource/prompt
 * registries, the session map, the shared scheduled executor, and the
 * JSON-RPC protocol dispatch (initialize, ping, session-scoped methods).
 * Knows nothing about HTTP or stdio framing — both transports drive the
 * same handler instance.
 * <p>
 * Per-DR-007, stdio is single-session by definition; HTTP is multi-session
 * with sessions keyed by {@code Mcp-Session-Id} (DR-005). The handler
 * itself is multi-session capable; the {@code acceptNewSession} predicate
 * lets a transport (or a wrapping subclass like swing-mcp's
 * {@code MCPServer}) clamp the session count.
 */
public class MCPHandler {

    private static final Logger LOG = Logger.getLogger(MCPHandler.class.getName());

    /**
     * Protocol versions this handler speaks. Order does not matter for
     * negotiation: {@link #buildInitializeResult} echoes the client's
     * requested version back if it appears here, otherwise it picks
     * {@link #LATEST_PROTOCOL_VERSION}.
     */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25");

    /**
     * Returned by {@code initialize} when the client requests a version
     * we don't speak. Per the MCP spec the client may then disconnect.
     */
    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";

    /**
     * Sessions idle for at least this long are evicted by the cleanup tick.
     * Package-private so tests can substitute a short value via reflection.
     */
    static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(30);
    /** Interval between cleanup ticks. */
    static final long CLEANUP_TICK_SECONDS = 60;

    private final MCPProtocol.Implementation serverInfo;
    private final String instructions;
    private final IntPredicate acceptNewSession;
    private final Consumer<MCPSession> onSessionClosed;

    private final MCPToolHandler toolHandler = new MCPToolHandler();
    private final MCPResourceHandler resourceHandler = new MCPResourceHandler();
    private final MCPPromptHandler promptHandler = new MCPPromptHandler();

    private final ConcurrentHashMap<String, MCPSession> sessions = new ConcurrentHashMap<>();
    /** Guards all session-map mutations (put, remove, clear) to prevent races. */
    private final Object sessionGuardLock = new Object();

    private volatile boolean started = false;
    /**
     * Shared scheduled executor. Created in {@link #start()}, shut down in
     * {@link #stop()}. Runs the session-idle-cleanup tick and is exposed
     * via {@link #getExecutor()} for tool handlers that need background work.
     */
    private ScheduledExecutorService executor;

    /**
     * @param serverInfo       advertised in {@code initialize.serverInfo}; never null
     * @param instructions     advertised in {@code initialize.instructions}; may be null
     * @param acceptNewSession called under the session guard lock with the
     *                         current session count; return {@code true} to
     *                         accept the new session, {@code false} to reject
     *                         (the dispatch raises a 409 / SERVER_NOT_INITIALIZED)
     * @param onSessionClosed  called outside any handler lock after a session
     *                         has been removed from the map (via DELETE,
     *                         explicit removal, or idle eviction)
     */
    MCPHandler(MCPProtocol.Implementation serverInfo, String instructions,
            IntPredicate acceptNewSession, Consumer<MCPSession> onSessionClosed) {
        this.serverInfo = serverInfo != null ? serverInfo : new MCPProtocol.Implementation();
        this.instructions = instructions;
        this.acceptNewSession = acceptNewSession;
        this.onSessionClosed = onSessionClosed;
    }

    // ===== Registries =====

    void addTool(String name, String description, MCPProtocol.InputSchema inputSchema,
            TinyMCPServer.ToolFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add tools after server has been started");
        }
        toolHandler.addTool(name, description, inputSchema, function);
    }

    void addResource(String uri, String name, String description, String mimeType,
            TinyMCPServer.ResourceFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add resources after server has been started");
        }
        resourceHandler.addResource(uri, name, description, mimeType, function);
    }

    void addPrompt(String name, String description, PromptArgumentsBuilder arguments,
            TinyMCPServer.PromptFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add prompts after server has been started");
        }
        promptHandler.addPrompt(name, description, arguments, function);
    }

    // ===== Lifecycle =====

    /**
     * Creates the shared executor and schedules the idle-cleanup tick.
     * Idempotent only against itself; calling twice will overwrite the
     * executor reference (callers must {@link #stop()} first).
     */
    void start() {
        started = true;
        AtomicInteger threadNum = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "tiny-mcp-server-" + threadNum.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::cleanupIdleSessionsSafe,
                CLEANUP_TICK_SECONDS, CLEANUP_TICK_SECONDS, TimeUnit.SECONDS);
    }

    void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Returns the shared scheduled executor. Threads are daemon and named
     * {@code tiny-mcp-server-N}.
     *
     * @throws IllegalStateException if the handler is not started
     */
    public ScheduledExecutorService getExecutor() {
        if (executor == null) {
            throw new IllegalStateException("Server not started");
        }
        return executor;
    }

    // ===== Session management =====

    int getSessionCount() {
        return sessions.size();
    }

    /**
     * Looks up a session by id. Returns {@code null} if no such session
     * exists. Callers that need to fail-fast with a 404 should check the
     * result and throw {@link MCPServerException} themselves — the lookup
     * itself is silent.
     */
    MCPSession getSession(String id) {
        if (id == null) return null;
        return sessions.get(id);
    }

    /**
     * Removes the session with the given id. Returns the removed session
     * (or {@code null}) without invoking {@link #onSessionClosed} — the
     * caller is responsible for calling it after any external state has
     * been cleaned up.
     */
    MCPSession removeSession(String id) {
        synchronized (sessionGuardLock) {
            return sessions.remove(id);
        }
    }

    /**
     * Removes all sessions. Returns the snapshot of removed sessions; the
     * caller invokes {@link #onSessionClosed} on each.
     */
    List<MCPSession> removeAllSessions() {
        synchronized (sessionGuardLock) {
            List<MCPSession> closed = new ArrayList<>(sessions.values());
            sessions.clear();
            return closed;
        }
    }

    void notifySessionClosed(MCPSession session) {
        if (onSessionClosed != null) {
            onSessionClosed.accept(session);
        }
    }

    // ===== Protocol dispatch =====

    /**
     * Outcome of {@code initialize}: the freshly created session and the
     * negotiated initialize result. Transports use {@link #sessionId()} to
     * surface the session token (HTTP {@code Mcp-Session-Id} header; stdio
     * ignores it) and {@link #result()} as the JSON-RPC body.
     */
    public record InitializeOutcome(String sessionId, MCPProtocol.InitializeResult result) {}

    /**
     * Creates a new session and builds the corresponding {@code initialize}
     * result. Throws {@link MCPServerException} with HTTP 409 +
     * {@code SERVER_NOT_INITIALIZED} if {@code acceptNewSession} rejects.
     */
    InitializeOutcome dispatchInitialize(MCPProtocol.JsonRpcRequest request) {
        MCPSession session;
        synchronized (sessionGuardLock) {
            if (acceptNewSession != null && !acceptNewSession.test(sessions.size())) {
                LOG.warning("Rejecting initialization: acceptNewSession() returned false");
                throw new MCPServerException(409,
                        MCPServerException.SERVER_NOT_INITIALIZED, "Another session is already active");
            }
            String sessionId = UUID.randomUUID().toString();
            session = new MCPSession(sessionId, toolHandler, resourceHandler, promptHandler, this);
            sessions.put(sessionId, session);
        }

        MCPProtocol.InitializeResult result = new MCPProtocol.InitializeResult();
        result.setProtocolVersion(negotiateProtocolVersion(request));
        result.setServerInfo(this.serverInfo);
        result.setInstructions(this.instructions);

        MCPProtocol.ServerCapabilities capabilities = new MCPProtocol.ServerCapabilities();
        capabilities.setTools(new MCPProtocol.ToolsCapability());
        capabilities.setResources(new MCPProtocol.ResourcesCapability());
        capabilities.setPrompts(new MCPProtocol.PromptsCapability());
        result.setCapabilities(capabilities);

        return new InitializeOutcome(session.getId(), result);
    }

    /** {@code ping} returns an empty result object ({@code {}}). */
    Object dispatchPing() {
        return Collections.emptyMap();
    }

    /**
     * Picks the protocol version to advertise back to the client.
     * Per the MCP spec ({@code initialize} lifecycle): if the client's
     * requested version is supported, echo it back; otherwise reply with
     * a version we do support and let the client decide whether to proceed.
     */
    private static String negotiateProtocolVersion(MCPProtocol.JsonRpcRequest request) {
        try {
            MCPProtocol.InitializeParams params = request.getParamsAs(MCPProtocol.InitializeParams.class);
            String requested = params != null ? params.getProtocolVersion() : null;
            if (requested != null && SUPPORTED_PROTOCOL_VERSIONS.contains(requested)) {
                return requested;
            }
        } catch (RuntimeException e) {
            // Malformed initialize params — fall back to the latest version.
            LOG.log(Level.FINE, "Could not parse initialize params", e);
        }
        return LATEST_PROTOCOL_VERSION;
    }

    // ===== Idle cleanup =====

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
     * next tick. Removed sessions are reported via {@code onSessionClosed}.
     * <p>
     * Package-private so tests can invoke the tick synchronously.
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
                notifySessionClosed(s);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "onSessionClosed threw for " + s.getId(), e);
            }
        }
    }
}
