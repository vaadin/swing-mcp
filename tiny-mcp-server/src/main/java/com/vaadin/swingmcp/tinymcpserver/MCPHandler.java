package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.ToolDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transport-agnostic MCP dispatch core. Owns the tool/resource/prompt
 * registries, the session map, the shared scheduled executor, and the
 * JSON-RPC protocol dispatch (initialize, ping, session-scoped methods).
 * Knows nothing about HTTP or stdio framing — both transports drive the
 * same handler instance.
 * <p>
 * Configure the handler (call {@code addTool}/{@code addResource}/{@code addPrompt}
 * and the {@link #setAcceptNewSession}/{@link #setOnSessionStarted}/{@link #setOnSessionClosed}
 * fluent setters) <em>before</em> handing it to a transport such as
 * {@link HttpMCPServer} or {@link StdioMCPServer}. The transport calls
 * {@link #start()} on {@code start()} and {@link #stop()} on {@code stop()};
 * a single handler instance is paired with a single transport for one
 * lifecycle cycle.
 * <p>
 * Per DR-stdio-transport, stdio is single-session by definition; HTTP is multi-session
 * with sessions keyed by {@code Mcp-Session-Id} (DR-session-lifecycle-gate). The handler
 * itself is multi-session capable; the {@code acceptNewSession} policy
 * (DR-supersede-sessions) lets a caller (e.g. swing-mcp's {@code SwingMCP}) clamp the
 * session count and decide whether to reject or supersede on conflict.
 * <p>
 * Per DR-settable-listeners, the three session-lifecycle listeners are settable until
 * the first session is accepted, then locked — calling any setter
 * afterwards throws {@link IllegalStateException}. This matches the
 * "one handler, one transport, one lifecycle cycle" rule while letting
 * factories (e.g. {@link MCPProxy#newHandler}) wire listeners
 * post-construction.
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
     * Sessions idle for at least this long are evicted, on transports that
     * schedule the tick ({@link #scheduleIdleCleanup()}).
     * <p>
     * Package-private, as is {@link #cleanupIdleSessions()}: tests drive the
     * tick directly against a backdated
     * {@link MCPSession#setLastAccessNanos(long)} rather than waiting.
     */
    static final long IDLE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(30);
    /** Interval between cleanup ticks. */
    static final long CLEANUP_TICK_SECONDS = 60;

    private final MCPProtocol.Implementation serverInfo;
    private final String instructions;

    /**
     * Session-lifecycle listeners. Settable via fluent setters until the
     * first session is accepted, then locked (DR-settable-listeners). Defaults are no-op
     * (always accept, do nothing on start/close).
     */
    private volatile Function<List<MCPSession>, SessionDecision> acceptNewSession =
            existing -> new SessionDecision.Accept();
    private volatile Consumer<MCPSession> onSessionStarted = session -> {};
    private volatile Consumer<MCPSession> onSessionClosed = session -> {};
    /**
     * Set under {@link #sessionGuardLock} the moment the first session is
     * placed in the session map. Once true, listener setters reject with
     * {@link IllegalStateException}.
     */
    private volatile boolean firstSessionAccepted = false;

    private final MCPToolHandler toolHandler = new MCPToolHandler();
    private final MCPResourceHandler resourceHandler = new MCPResourceHandler();
    private final MCPPromptHandler promptHandler = new MCPPromptHandler();

    private final ConcurrentHashMap<String, MCPSession> sessions = new ConcurrentHashMap<>();
    /** Guards all session-map mutations (put, remove, clear) to prevent races. */
    private final Object sessionGuardLock = new Object();

    /**
     * Reason strings for sessions that have been removed from the map.
     * Looked up by {@link HttpMCPServer} when an incoming request carries
     * an unknown {@code Mcp-Session-Id}, so the displaced client gets a
     * specific 404 message (eviction reason, "idle timeout") instead of
     * the generic "Session not found." Bounded LRU — entries roll off
     * when the cap is exceeded. Supersede tombstones carry the
     * {@link SessionDecision.AcceptAndEvict#evictionReason()} from the
     * decision that evicted them. (DR-supersede-sessions)
     */
    static final String IDLE_REASON = "Session expired (idle timeout)";
    private final BoundedLRUMap<String, String> tombstones = new BoundedLRUMap<>(64);

    private volatile boolean started = false;
    /**
     * Shared scheduled executor. Created in {@link #start()}, shut down in
     * {@link #stop()}. Runs the session-idle-cleanup tick, where one was
     * scheduled ({@link #scheduleIdleCleanup()}), and is exposed via
     * {@link #getExecutor()} for tool handlers that need background work.
     */
    private ScheduledExecutorService executor;

    /** Convenience constructor: empty server info, no instructions. */
    public MCPHandler() {
        this(new MCPProtocol.Implementation(), null);
    }

    /**
     * @param serverInfo   advertised in {@code initialize.serverInfo};
     *                     {@code null} means "use a default empty
     *                     {@link MCPProtocol.Implementation}"
     * @param instructions advertised in {@code initialize.instructions}; may be null
     */
    public MCPHandler(MCPProtocol.Implementation serverInfo, String instructions) {
        this.serverInfo = serverInfo != null ? serverInfo : new MCPProtocol.Implementation();
        this.instructions = instructions;
    }

    // ===== Session-lifecycle listener setters (DR-settable-listeners) =====

    /**
     * Sets the policy consulted on every {@code initialize} to decide
     * whether to accept a new session and, if so, whether to evict any
     * existing sessions. Called under the session-guard lock with a
     * snapshot of the currently active sessions (excluding the one being
     * created); returns a {@link SessionDecision}:
     * <ul>
     *   <li>{@link SessionDecision.Reject} → HTTP 409 + JSON-RPC
     *       {@code SERVER_NOT_INITIALIZED}.</li>
     *   <li>{@link SessionDecision.Accept} → accept without eviction.</li>
     *   <li>{@link SessionDecision.AcceptAndEvict} → tombstone and evict
     *       the listed sessions (blocking until each one's in-flight
     *       request finishes), then accept the new session. The displaced
     *       client gets a 404 with the supersede message on its next call.</li>
     * </ul>
     *
     * <p>Default: {@code existing -> new Accept()} (always accept, no
     * eviction). For supersede-on-conflict (single-session, new-wins),
     * use {@code existing -> new AcceptAndEvict(existing, reason)} where
     * {@code reason} is an application-specific message that will reach
     * the displaced client verbatim in its next-call 404.
     *
     * @throws NullPointerException if {@code accept} is null
     * @throws IllegalStateException if a session has already been accepted
     */
    public MCPHandler setAcceptNewSession(Function<List<MCPSession>, SessionDecision> accept) {
        Objects.requireNonNull(accept, "acceptNewSession");
        checkListenersUnlocked();
        this.acceptNewSession = accept;
        return this;
    }

    /**
     * Sets the listener invoked the moment a fresh session has been added
     * to the session map (after {@code acceptNewSession} returned true,
     * before the {@code initialize} response is built). Used by
     * {@link MCPProxy} to allocate per-session upstream-client state.
     * <p>
     * Default: no-op.
     *
     * @throws NullPointerException if {@code onStarted} is null
     * @throws IllegalStateException if a session has already been accepted
     */
    public MCPHandler setOnSessionStarted(Consumer<MCPSession> onStarted) {
        Objects.requireNonNull(onStarted, "onSessionStarted");
        checkListenersUnlocked();
        this.onSessionStarted = onStarted;
        return this;
    }

    /**
     * Sets the listener invoked outside any handler lock after a session
     * has been removed from the map (via DELETE, explicit removal, or
     * idle eviction).
     * <p>
     * Default: no-op.
     *
     * @throws NullPointerException if {@code onClosed} is null
     * @throws IllegalStateException if a session has already been accepted
     */
    public MCPHandler setOnSessionClosed(Consumer<MCPSession> onClosed) {
        Objects.requireNonNull(onClosed, "onSessionClosed");
        checkListenersUnlocked();
        this.onSessionClosed = onClosed;
        return this;
    }

    private void checkListenersUnlocked() {
        if (firstSessionAccepted) {
            throw new IllegalStateException(
                    "Session-lifecycle listeners are locked once the first session has been accepted");
        }
    }

    // ===== Registries =====

    /**
     * Registers a tool with this handler. Must be called before the
     * handler has been started by a transport.
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
     *   <li>The tool function is invoked synchronously on the dispatch thread.</li>
     * </ul>
     *
     * @param name        the tool name; not null, not blank; must match
     *                    {@code [a-zA-Z_][a-zA-Z0-9_]*}
     * @param description human-readable description of the tool; not null, not blank
     * @param inputSchema the parameter schema; not null; consider using {@link InputSchemaBuilder}
     * @param function    the handler to invoke when the tool is called; not null
     * @throws IllegalArgumentException if any argument is null, blank, or (for
     *                                  {@code name}) does not match the required pattern
     * @throws IllegalStateException    if the handler has already been started
     * @throws IllegalStateException    if a tool with the same name is already registered
     */
    public void addTool(String name, String description, MCPProtocol.InputSchema inputSchema,
            ToolFunction function) {
        addTool(new ToolDescriptor(name, description, inputSchema), function);
    }

    /**
     * Registers a tool from a {@link ToolDescriptor} (DR-settable-listeners). This is the
     * primary registration form; the four-arg overload constructs a
     * descriptor and delegates here.
     *
     * @param descriptor the tool descriptor; not null
     * @param function   the handler to invoke when the tool is called; not null
     * @throws NullPointerException  if {@code descriptor} or {@code function} is null
     * @throws IllegalStateException if the handler has already been started, or
     *                               if a tool with the same name is already registered
     */
    public void addTool(ToolDescriptor descriptor, ToolFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add tools after server has been started");
        }
        toolHandler.addTool(descriptor, function);
    }

    /**
     * Registers a resource with this handler. Must be called before the
     * handler has been started by a transport.
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
     * @throws IllegalStateException    if the handler has already been started
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
     * Registers a prompt with this handler. Must be called before the
     * handler has been started by a transport.
     *
     * @param name        the prompt name; not null, not blank
     * @param description human-readable description of the prompt; not null, not blank
     * @param arguments   the argument builder (pass an empty
     *                    {@link PromptArgumentsBuilder} for a zero-argument
     *                    prompt); not null
     * @param function    the handler to invoke for {@code prompts/get}; not null
     * @throws IllegalArgumentException if any argument is null/blank
     * @throws IllegalStateException    if the handler has already been started
     * @throws IllegalStateException    if a prompt with the same name is already registered
     */
    public void addPrompt(String name, String description, PromptArgumentsBuilder arguments,
            PromptFunction function) {
        if (started) {
            throw new IllegalStateException("Cannot add prompts after server has been started");
        }
        promptHandler.addPrompt(name, description, arguments, function);
    }

    // ===== Lifecycle =====

    /**
     * Creates the shared executor. Called by the transport on
     * {@code start()}; not intended for callers. Idempotent only against
     * itself; calling twice will overwrite the executor reference (callers
     * must {@link #stop()} first).
     *
     * <p>Does <em>not</em> schedule idle-session cleanup; a transport that
     * wants it calls {@link #scheduleIdleCleanup()} as well.
     */
    void start() {
        started = true;
        AtomicInteger threadNum = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "tiny-mcp-server-" + threadNum.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedules the once-per-minute idle-cleanup tick on the shared executor.
     * <p>
     * Only a transport that cannot observe its client's death needs this —
     * HTTP, where a vanished client would otherwise hold its session slot
     * forever. A process-scoped session must not be evicted at all; see
     * {@link StdioMCPServer} (DR-stdio-never-evicts).
     *
     * @throws IllegalStateException if {@link #start()} has not run
     */
    void scheduleIdleCleanup() {
        if (executor == null) {
            throw new IllegalStateException("start() must be called before scheduleIdleCleanup()");
        }
        executor.scheduleAtFixedRate(this::cleanupIdleSessionsSafe,
                CLEANUP_TICK_SECONDS, CLEANUP_TICK_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Shuts down the executor. Called by the transport on {@code stop()}.
     */
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Returns the shared scheduled executor. Threads are daemon and named
     * {@code tiny-mcp-server-N}.
     * <p>
     * Available to tool handlers for background work (debouncing, deferred
     * cleanup, periodic polling). Do not submit long-blocking I/O that
     * could starve session idle-cleanup on an HTTP server — for heavy work,
     * create your own executor.
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

    /**
     * Returns the number of active sessions.
     */
    public int getSessionCount() {
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
     * Returns the tombstone reason for a session id that has been
     * removed from the session map (via supersede or idle eviction), or
     * {@code null} if no tombstone exists. Used by {@link HttpMCPServer}
     * to produce a specific 404 message instead of the generic "Session
     * not found." Tombstones are kept in a bounded LRU; older entries
     * roll off and revert to the generic message. (DR-supersede-sessions)
     */
    String getTombstoneReason(String id) {
        if (id == null) return null;
        return tombstones.get(id);
    }

    /** Generic 404 message used when no tombstone is recorded for the id. */
    static final String SESSION_NOT_FOUND_MESSAGE = "Session not found.";

    /**
     * Resolves the message for a 404 on the given session id: the
     * tombstone reason if one exists, otherwise the generic
     * {@link #SESSION_NOT_FOUND_MESSAGE}.
     */
    String tombstoneOrDefault(String id) {
        String reason = getTombstoneReason(id);
        return reason != null ? reason : SESSION_NOT_FOUND_MESSAGE;
    }

    /**
     * Removes the session with the given id. Returns the removed session
     * (or {@code null}) without invoking {@code onSessionClosed} — the
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
     * caller invokes {@code onSessionClosed} on each.
     */
    List<MCPSession> removeAllSessions() {
        synchronized (sessionGuardLock) {
            List<MCPSession> closed = new ArrayList<>(sessions.values());
            sessions.clear();
            return closed;
        }
    }

    /**
     * Removes every active session and invokes {@code onSessionClosed}
     * on each. Idempotent — a subsequent call sees an empty session map
     * and is a no-op. A listener that throws is logged at WARNING and
     * does not abort the iteration.
     *
     * <p>Intended for transport-driven shutdown paths (stdio EOF, JVM
     * shutdown hook) where the process is about to exit and live
     * sessions need their close listeners run to release per-session
     * resources (e.g. {@code MCPProxy}'s upstream {@code TinyMCPClient}).
     */
    public void closeAllSessions() {
        for (MCPSession s : removeAllSessions()) {
            try {
                notifySessionClosed(s);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "onSessionClosed threw for " + s.getId(), e);
            }
        }
    }

    void notifySessionClosed(MCPSession session) {
        // Wrap in runListenerHook so the listener can safely read
        // attributes populated by onSessionStarted (DR-settable-listeners).
        session.runListenerHook(() -> onSessionClosed.accept(session));
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
     * result. Consults the {@link #setAcceptNewSession} policy with a
     * snapshot of currently active sessions:
     * <ul>
     *   <li>{@link SessionDecision.Reject} → HTTP 409 +
     *       {@code SERVER_NOT_INITIALIZED}.</li>
     *   <li>{@link SessionDecision.Accept} → new session is created,
     *       no eviction.</li>
     *   <li>{@link SessionDecision.AcceptAndEvict} → tombstones are
     *       written under the session-guard lock, the new session is
     *       inserted, then the lock is released and each evicted session
     *       is closed (blocking on its in-flight request) and reported
     *       via {@code onSessionClosed}, before the new session's
     *       {@code onSessionStarted} fires.</li>
     * </ul>
     */
    InitializeOutcome dispatchInitialize(MCPProtocol.JsonRpcRequest request) {
        MCPSession session;
        List<MCPSession> toEvict;
        synchronized (sessionGuardLock) {
            List<MCPSession> existingSnapshot = List.copyOf(sessions.values());
            SessionDecision decision = acceptNewSession.apply(existingSnapshot);
            if (decision instanceof SessionDecision.Reject) {
                LOG.warning("Rejecting initialization: acceptNewSession returned Reject");
                throw new MCPServerException(409,
                        MCPServerException.SERVER_NOT_INITIALIZED, "Another session is already active");
            }
            String evictionReason;
            if (decision instanceof SessionDecision.AcceptAndEvict ae) {
                toEvict = ae.sessions();
                evictionReason = ae.evictionReason();
            } else {
                toEvict = List.of();
                evictionReason = null;
            }
            for (MCPSession e : toEvict) {
                tombstones.put(e.getId(), evictionReason);
            }
            String sessionId = UUID.randomUUID().toString();
            session = new MCPSession(sessionId, toolHandler, resourceHandler, promptHandler, this);
            sessions.put(sessionId, session);
            // DR-settable-listeners: lock listener setters now that a session has been
            // placed in the map. Any subsequent setter call fails fast.
            firstSessionAccepted = true;
        }

        // Evict outside the session-guard lock — close() blocks on the
        // session's lock, waiting for any in-flight request to finish.
        for (MCPSession e : toEvict) {
            try {
                e.close();
                if (sessions.remove(e.getId(), e)) {
                    LOG.info("Session superseded: " + e.getId());
                    notifySessionClosed(e);
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Failed to evict session " + e.getId(), ex);
            }
        }

        try {
            // Run inside session.runListenerHook so the listener can
            // populate session attributes safely (the public
            // setAttribute/getAttribute API requires the session lock for
            // memory visibility).
            MCPSession s = session;
            session.runListenerHook(() -> onSessionStarted.accept(s));
        } catch (RuntimeException e) {
            // Listener failure must not corrupt the session map. Roll back
            // and rethrow so the caller sees a clean failure.
            LOG.log(Level.WARNING, "onSessionStarted threw for " + session.getId(), e);
            synchronized (sessionGuardLock) {
                sessions.remove(session.getId(), session);
            }
            throw e;
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
                if (sessions.remove(s.getId(), s)) {
                    tombstones.put(s.getId(), IDLE_REASON);
                }
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
