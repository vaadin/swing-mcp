package com.vaadin.swingmcp.tinymcpserver;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises idle-session cleanup and the shared scheduled executor.
 * <p>
 * Cleanup is tested by invoking {@link TinyMCPServer#cleanupIdleSessions()}
 * directly after backdating sessions via
 * {@link MCPSession#setLastAccessNanos(long)} — no real time is waited.
 */
class SessionCleanupTest {

    /** A timestamp safely older than any reasonable idle threshold. */
    private static long ancient() {
        return System.nanoTime() - TimeUnit.HOURS.toNanos(1);
    }

    private static RecordingServer newServer() {
        return new RecordingServer();
    }

    private static class RecordingServer extends TinyMCPServer {
        final List<String> closedSessionIds = new ArrayList<>();
        RuntimeException closeThrow;

        RecordingServer() {
            super(0, "/mcp");
        }

        @Override
        protected void onSessionClosed(MCPSession session) {
            closedSessionIds.add(session.getId());
            if (closeThrow != null) {
                throw closeThrow;
            }
        }
    }

    /**
     * Initializes a session against a started HTTP server and returns the
     * server-side {@link MCPSession} so tests can manipulate its state
     * (e.g. backdate {@code lastAccessNanos}).
     */
    private static MCPSession initAndGetSession(TinyMCPServer server) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(server.getUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        String sessionId = resp.headers().firstValue("Mcp-Session-Id").orElseThrow();
        return findSessionById(server, sessionId);
    }

    /**
     * Retrieves the {@link MCPSession} with the given id from a server.
     * Uses reflection on the private {@code sessions} map because the
     * server does not otherwise expose sessions — acceptable for in-package
     * tests of cleanup mechanics.
     */
    private static MCPSession findSessionById(TinyMCPServer server, String id) throws Exception {
        java.lang.reflect.Field f = TinyMCPServer.class.getDeclaredField("sessions");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.ConcurrentHashMap<String, MCPSession> map =
                (java.util.concurrent.ConcurrentHashMap<String, MCPSession>) f.get(server);
        MCPSession s = map.get(id);
        assertNotNull(s, "no session with id " + id);
        return s;
    }

    // ===== Cleanup logic =====

    @Test
    void idleSessionIsEvicted() throws Exception {
        RecordingServer server = newServer();
        server.start();
        try {
            MCPSession session = initAndGetSession(server);
            session.setLastAccessNanos(ancient());

            server.cleanupIdleSessions();

            assertEquals(0, server.getSessionCount(), "idle session should be removed");
            assertEquals(List.of(session.getId()), server.closedSessionIds,
                    "onSessionClosed should fire exactly once with the evicted id");

            // A later dispatch on the same (now-closed) session throws 404.
            JsonRpcExchange rpc = new JsonRpcExchange(new FakeHttpExchange(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
            MCPProtocol.JsonRpcRequest req = rpc.parsePost();
            MCPServerException ex = assertThrows(MCPServerException.class,
                    () -> session.handlePost(rpc, req));
            assertEquals(404, ex.getHttpStatus());
        } finally {
            server.stop();
        }
    }

    @Test
    void freshSessionSurvivesCleanup() throws Exception {
        RecordingServer server = newServer();
        server.start();
        try {
            MCPSession session = initAndGetSession(server);
            // do not backdate — timestamp is fresh

            server.cleanupIdleSessions();

            assertEquals(1, server.getSessionCount(), "fresh session must remain");
            assertTrue(server.closedSessionIds.isEmpty(),
                    "onSessionClosed must not fire for fresh sessions");
            assertNotNull(findSessionById(server, session.getId()));
        } finally {
            server.stop();
        }
    }

    @Test
    void inUseSessionIsSkippedThenEvictedOnNextTick() throws Exception {
        RecordingServer server = newServer();
        server.start();
        try {
            MCPSession session = initAndGetSession(server);
            session.setLastAccessNanos(ancient());

            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread holder = new Thread(() -> session.runLocked(() -> {
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            holder.setDaemon(true);
            holder.start();

            assertTrue(locked.await(2, TimeUnit.SECONDS), "holder thread should acquire lock");

            // Cleanup runs while lock is held — tryClose returns false, session stays.
            server.cleanupIdleSessions();
            assertEquals(1, server.getSessionCount(), "in-use session must not be evicted");
            assertTrue(server.closedSessionIds.isEmpty());

            // Release the lock and re-backdate — runLocked() refreshes
            // lastAccessNanos on entry, so the holder thread's acquisition
            // counts as "activity". After it exits, backdate again so the
            // next cleanup tick sees a stale timestamp.
            release.countDown();
            holder.join(2000);
            session.setLastAccessNanos(ancient());

            server.cleanupIdleSessions();
            assertEquals(0, server.getSessionCount(), "session should be evicted after lock release");
            assertEquals(List.of(session.getId()), server.closedSessionIds);
        } finally {
            server.stop();
        }
    }

    @Test
    void handlePostUpdatesLastAccessNanos() throws Exception {
        RecordingServer server = newServer();
        server.start();
        try {
            MCPSession session = initAndGetSession(server);
            long before = ancient();
            session.setLastAccessNanos(before);

            // Dispatch any session-scoped request directly via MCPSession.
            FakeHttpExchange exchange = new FakeHttpExchange(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
            JsonRpcExchange rpc = new JsonRpcExchange(exchange);
            MCPProtocol.JsonRpcRequest req = rpc.parsePost();
            session.handlePost(rpc, req);

            assertTrue(session.getLastAccessNanos() > before,
                    "handlePost must advance lastAccessNanos");
        } finally {
            server.stop();
        }
    }

    @Test
    void cleanupContinuesWhenOnSessionClosedThrows() throws Exception {
        RecordingServer server = newServer();
        server.closeThrow = new RuntimeException("boom");
        server.start();
        try {
            // Two sessions, both stale — a throw on the first must not
            // prevent the second from being removed and reported.
            MCPSession s1 = initAndGetSession(server);
            // Second initialize requires releasing the first — most servers
            // accept multiple, but if acceptNewSession() is overridden
            // this could fail. TinyMCPServer default allows unlimited.
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(server.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String id2 = resp.headers().firstValue("Mcp-Session-Id").orElseThrow();
            MCPSession s2 = findSessionById(server, id2);

            s1.setLastAccessNanos(ancient());
            s2.setLastAccessNanos(ancient());

            server.cleanupIdleSessions();

            assertEquals(0, server.getSessionCount(),
                    "both sessions must be removed from the map despite the throw");
            assertEquals(2, server.closedSessionIds.size(),
                    "onSessionClosed must be attempted for both sessions");
            assertTrue(server.closedSessionIds.contains(s1.getId()));
            assertTrue(server.closedSessionIds.contains(s2.getId()));
        } finally {
            server.stop();
        }
    }

    // ===== Executor =====

    @Test
    void executorThreadsAreDaemonAndNamed() throws Exception {
        TinyMCPServer server = new TinyMCPServer(0, "/mcp");
        server.start();
        try {
            AtomicReference<Thread> captured = new AtomicReference<>();
            server.getExecutor().submit(() -> captured.set(Thread.currentThread()))
                    .get(2, TimeUnit.SECONDS);

            Thread t = captured.get();
            assertNotNull(t);
            assertTrue(t.isDaemon(), "executor threads must be daemon");
            assertTrue(Pattern.matches("tiny-mcp-server-\\d+", t.getName()),
                    "expected tiny-mcp-server-N, got: " + t.getName());
        } finally {
            server.stop();
        }
    }

    @Test
    void getExecutorThrowsBeforeStart() {
        TinyMCPServer server = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalStateException.class, server::getExecutor);
    }

    @Test
    void stopShutsDownExecutor() throws Exception {
        TinyMCPServer server = new TinyMCPServer(0, "/mcp");
        server.start();
        ScheduledExecutorService ref = server.getExecutor();
        server.stop();

        assertTrue(ref.isShutdown(), "executor must be shut down after stop()");
        assertThrows(RejectedExecutionException.class,
                () -> ref.submit(() -> {}));
    }

    @Test
    void sessionGetServerReturnsOwningServer() throws Exception {
        RecordingServer server = newServer();
        server.start();
        try {
            MCPSession session = initAndGetSession(server);
            assertSame(server, session.getServer(),
                    "getServer() must return the TinyMCPServer that created the session");
            assertSame(server.getExecutor(), session.getServer().getExecutor(),
                    "executor must be shared via the session back-pointer");
        } finally {
            server.stop();
        }
    }
}
