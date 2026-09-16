package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.tinymcpclient.MCPSessionLostException;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the supersede-on-conflict session policy (DR-supersede-sessions): when a
 * caller registers an {@link SessionDecision.AcceptAndEvict} policy, a
 * new {@code initialize} replaces the existing session(s) and the
 * displaced client gets a tombstone-backed 404 on its next call.
 */
class SessionSupersedeTest {

    private static final String TEST_REASON = "test-supersede-reason";

    private HttpMCPServer server;
    private final List<String> closedIds = new CopyOnWriteArrayList<>();
    private final List<String> startedIds = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    private MCPHandler newHandlerWithEcho() {
        MCPHandler handler = new MCPHandler()
                .setOnSessionStarted(s -> startedIds.add(s.getId()))
                .setOnSessionClosed(s -> closedIds.add(s.getId()));
        handler.addTool("echo", "Echo tool",
                new InputSchemaBuilder().requiredString("msg", "message").build(),
                request -> MCPProtocol.Content.text((String) request.arguments().raw().get("msg")));
        return handler;
    }

    private void start(MCPHandler handler) {
        server = new HttpMCPServer(0, "/mcp", handler);
        server.start();
    }

    private URI uri() { return URI.create(server.getUrl()); }

    // ===== Supersede =====

    @Test
    void supersedeEvictsOldSessionAndReturnsTombstoneOnNextCall() throws IOException {
        MCPHandler handler = newHandlerWithEcho()
                .setAcceptNewSession(existing -> new SessionDecision.AcceptAndEvict(existing, TEST_REASON));
        start(handler);

        try (TinyMCPClient first = new TinyMCPClient(uri());
             TinyMCPClient second = new TinyMCPClient(uri())) {

            first.initialize();
            second.initialize();

            assertDoesNotThrow(() -> second.callTool("echo", java.util.Map.of("msg", "ok")),
                    "the surviving session must accept calls");

            MCPSessionLostException ex = assertThrows(MCPSessionLostException.class,
                    () -> first.callTool("echo", java.util.Map.of("msg", "anything")));
            assertEquals(TEST_REASON, ex.getMessage(),
                    "the supersede tombstone reason must reach the displaced client verbatim");

            assertEquals(1, closedIds.size(), "exactly one session was evicted");
            assertEquals(2, startedIds.size(), "two sessions were started");
            assertEquals(startedIds.get(0), closedIds.get(0),
                    "the first-started session is the one that got evicted");
        }
    }

    @Test
    void supersedeFiresOnSessionClosedExactlyOncePerEvictedSession() throws IOException {
        MCPHandler handler = newHandlerWithEcho()
                .setAcceptNewSession(existing -> new SessionDecision.AcceptAndEvict(existing, TEST_REASON));
        start(handler);

        TinyMCPClient a = new TinyMCPClient(uri());
        TinyMCPClient b = new TinyMCPClient(uri());
        TinyMCPClient c = new TinyMCPClient(uri());
        try {
            a.initialize();
            b.initialize();   // supersedes a → 1 close
            c.initialize();   // supersedes b → 1 close
            // Don't close c — measure the supersede-induced closes only.
            assertEquals(2, closedIds.size(),
                    "two superseded sessions, two onSessionClosed firings");
        } finally {
            a.close();
            b.close();
            c.close();
        }
    }

    // ===== AcceptAndEvict with empty list =====

    @Test
    void acceptAndEvictWithEmptyListIsNoop() throws IOException {
        MCPHandler handler = newHandlerWithEcho()
                .setAcceptNewSession(existing -> new SessionDecision.AcceptAndEvict(List.of(), TEST_REASON));
        start(handler);

        try (TinyMCPClient first = new TinyMCPClient(uri());
             TinyMCPClient second = new TinyMCPClient(uri())) {
            first.initialize();
            second.initialize();
            assertDoesNotThrow(() -> first.callTool("echo", java.util.Map.of("msg", "x")));
            assertDoesNotThrow(() -> second.callTool("echo", java.util.Map.of("msg", "y")));
            assertTrue(closedIds.isEmpty(), "empty eviction list must not fire onSessionClosed");
        }
    }

    // ===== Reject =====

    @Test
    void rejectStillProducesHttp409() throws Exception {
        MCPHandler handler = newHandlerWithEcho()
                .setAcceptNewSession(existing ->
                        existing.isEmpty() ? new SessionDecision.Accept() : new SessionDecision.Reject());
        start(handler);

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest init = HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                .build();
        HttpResponse<String> first = http.send(init, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        HttpResponse<String> second = http.send(init, HttpResponse.BodyHandlers.ofString());
        assertEquals(409, second.statusCode(),
                "Reject decision must surface as HTTP 409");
        assertTrue(second.body().contains("Another session is already active"));
    }

    // ===== Idle eviction tombstone =====

    @Test
    void idleEvictionLeavesIdleTombstone() throws Exception {
        MCPHandler handler = newHandlerWithEcho();
        start(handler);

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest initReq = HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                .build();
        HttpResponse<String> initResp = http.send(initReq, HttpResponse.BodyHandlers.ofString());
        String sessionId = initResp.headers().firstValue("Mcp-Session-Id").orElseThrow();

        MCPSession s = handler.getSession(sessionId);
        assertNotNull(s);
        s.setLastAccessNanos(System.nanoTime() - TimeUnit.HOURS.toNanos(1));
        server.cleanupIdleSessions();

        assertNull(handler.getSession(sessionId), "session must have been evicted");
        assertEquals(MCPHandler.IDLE_REASON, handler.getTombstoneReason(sessionId));

        HttpRequest call = HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Mcp-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}"))
                .build();
        HttpResponse<String> resp = http.send(call, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains(MCPHandler.IDLE_REASON),
                "404 body should carry the idle tombstone reason; got: " + resp.body());
    }

    // ===== Tombstone LRU rollover =====

    @Test
    void tombstoneCapEnforcesRollover() throws Exception {
        MCPHandler handler = newHandlerWithEcho();
        start(handler);

        HttpClient http = HttpClient.newHttpClient();
        HttpRequest initReq = HttpRequest.newBuilder(uri())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}"))
                .build();

        List<String> idsInOrder = new ArrayList<>();
        // 100 idle-evicted sessions vs cap=64 → at least the first 36 entries
        // have rolled out of the LRU.
        for (int i = 0; i < 100; i++) {
            HttpResponse<String> r = http.send(initReq, HttpResponse.BodyHandlers.ofString());
            String id = r.headers().firstValue("Mcp-Session-Id").orElseThrow();
            idsInOrder.add(id);
            MCPSession s = handler.getSession(id);
            s.setLastAccessNanos(System.nanoTime() - TimeUnit.HOURS.toNanos(1));
            server.cleanupIdleSessions();
        }

        assertNull(handler.getTombstoneReason(idsInOrder.get(0)),
                "oldest tombstone should have rolled off the LRU cap");
        assertEquals(MCPHandler.IDLE_REASON,
                handler.getTombstoneReason(idsInOrder.get(idsInOrder.size() - 1)),
                "newest tombstone should still be present");
    }

    // ===== In-flight request during supersede =====

    @Test
    void inFlightRequestCompletesBeforeSupersede() throws Exception {
        MCPHandler handler = newHandlerWithEcho()
                .setAcceptNewSession(existing -> new SessionDecision.AcceptAndEvict(existing, TEST_REASON));
        start(handler);

        try (TinyMCPClient first = new TinyMCPClient(uri());
             TinyMCPClient second = new TinyMCPClient(uri())) {

            first.initialize();
            assertEquals(1, startedIds.size());
            String firstId = startedIds.get(0);
            MCPSession firstSession = handler.getSession(firstId);
            assertNotNull(firstSession);

            // Hold the first session's lock from another thread, simulating
            // an in-flight request. The supersede path's blocking close()
            // cannot fire onSessionClosed until the lock is released.
            CountDownLatch holderHasLock = new CountDownLatch(1);
            CountDownLatch releaseHolder = new CountDownLatch(1);
            Thread holder = new Thread(() -> firstSession.runLocked(() -> {
                holderHasLock.countDown();
                try {
                    releaseHolder.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }), "in-flight-holder");
            holder.setDaemon(true);
            holder.start();
            assertTrue(holderHasLock.await(2, TimeUnit.SECONDS), "holder must take the session lock");

            CountDownLatch initFinished = new CountDownLatch(1);
            Thread initWorker = new Thread(() -> {
                try {
                    second.initialize();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                initFinished.countDown();
            }, "second-init");
            initWorker.setDaemon(true);
            initWorker.start();

            // Give the worker a moment to enter the supersede path. It must
            // remain blocked on the held session lock.
            assertTrue(holder.isAlive(), "holder still owns the lock");
            Thread.sleep(200);
            assertEquals(1L, initFinished.getCount(),
                    "second.initialize() must block until the in-flight request quiesces");
            assertTrue(closedIds.isEmpty(),
                    "onSessionClosed must not fire while the session lock is held");

            releaseHolder.countDown();
            holder.join(2_000);
            assertTrue(initFinished.await(2, TimeUnit.SECONDS),
                    "second.initialize() must complete once the in-flight request finishes");

            assertEquals(List.of(firstId), closedIds);
            MCPSessionLostException ex = assertThrows(MCPSessionLostException.class,
                    () -> first.callTool("echo", java.util.Map.of("msg", "x")));
            assertEquals(TEST_REASON, ex.getMessage());
        }
    }
}
