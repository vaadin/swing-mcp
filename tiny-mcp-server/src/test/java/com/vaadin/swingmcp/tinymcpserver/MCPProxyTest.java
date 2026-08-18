package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.vaadin.swingmcp.ToolDescriptor;
import com.vaadin.swingmcp.tinymcpclient.MCPSessionLostException;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link MCPProxy} (DR-forwarding-proxy). Covers the
 * scenarios listed in {@code tiny-mcp-server/spec/architecture.md}:
 * upstream-down, drift (symmetric), session-lost, IO mid-call,
 * {@code _meta} passthrough, and the static {@code tools/list}.
 *
 * <p>Architecture: each test stands up a real upstream
 * {@link HttpMCPServer} on an ephemeral loopback port, builds a
 * proxy {@link MCPHandler} via {@link MCPProxy#newHandler}, hosts
 * that handler in a second {@code HttpMCPServer}, and drives the
 * proxy with a {@link TinyMCPClient}. The whole stack is real
 * loopback HTTP — no mocks, no in-memory shortcuts.
 */
class MCPProxyTest {

    /** Sentinel strings — distinct enough to assert against without quoting the live wording. */
    private static final ProxyMessages MESSAGES = new ProxyMessages(
            "<<UPSTREAM-DOWN>>",
            "<<DRIFT>>",
            "<<SESSION-LOST>>",
            "<<IO-MID-CALL>>");

    private static final ToolDescriptor ECHO = new ToolDescriptor(
            "echo",
            "Echoes the supplied text",
            new InputSchemaBuilder().requiredString("text", "the text").build());

    private static final ToolDescriptor PING = new ToolDescriptor(
            "ping",
            "Returns a fixed value",
            new InputSchemaBuilder().build());

    private HttpMCPServer upstream;
    private HttpMCPServer proxy;
    private TinyMCPClient client;
    /** Forwarded ToolRequests captured on the upstream side. */
    private final AtomicReference<ToolRequest> lastUpstreamRequest = new AtomicReference<>();

    @AfterEach
    void teardown() {
        try { if (client != null) client.close(); } catch (IOException ignored) {}
        if (proxy != null) proxy.stop();
        if (upstream != null) upstream.stop();
    }

    // ===== Helpers =====

    /** Stands up an upstream with the supplied tools and starts it on an ephemeral port. */
    private URI startUpstream(ToolDescriptor... tools) {
        return startUpstream(List.of(tools));
    }

    private URI startUpstream(List<ToolDescriptor> tools) {
        upstream = new HttpMCPServer(0, "/mcp");
        for (ToolDescriptor t : tools) {
            upstream.getHandler().addTool(t, request -> {
                lastUpstreamRequest.set(request);
                return MCPProtocol.Content.text("upstream:" + request.name());
            });
        }
        upstream.start();
        return URI.create(upstream.getUrl());
    }

    /** Builds the proxy handler around an existing upstream URL and starts it. */
    private void startProxy(URI upstreamUrl, List<ToolDescriptor> manifest) throws IOException {
        MCPHandler handler = MCPProxy.newHandler(manifest, upstreamUrl, MESSAGES);
        proxy = new HttpMCPServer(0, "/mcp", handler);
        proxy.start();
        client = new TinyMCPClient(URI.create(proxy.getUrl()));
        client.initialize();
    }

    private static int findUnusedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static String textOf(MCPProtocol.CallToolResult result) {
        assertNotNull(result.getContent());
        assertFalse(result.getContent().isEmpty(), "expected at least one content item");
        return result.getContent().get(0).getText();
    }

    // ===== tools/list always answers from the manifest =====

    @Test
    void toolsListAlwaysFromManifest() throws IOException {
        // Upstream advertises a *different* tool set; the proxy must
        // still report exactly what's in the manifest.
        URI upstreamUrl = startUpstream(PING);
        startProxy(upstreamUrl, List.of(ECHO));

        List<MCPProtocol.Tool> tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).getName());
        assertEquals(ECHO.description(), tools.get(0).getDescription());
    }

    @Test
    void toolsListSucceedsEvenWhenUpstreamIsDown() throws IOException {
        // No upstream at all — but tools/list must still succeed.
        int port = findUnusedPort();
        URI upstreamUrl = URI.create("http://127.0.0.1:" + port + "/mcp");
        startProxy(upstreamUrl, List.of(ECHO, PING));

        List<MCPProtocol.Tool> tools = client.listTools();
        assertEquals(2, tools.size());
    }

    // ===== Happy path =====

    @Test
    void successPathForwardsToUpstream() throws IOException {
        URI upstreamUrl = startUpstream(ECHO);
        startProxy(upstreamUrl, List.of(ECHO));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "hello"));
        assertNotEquals(Boolean.TRUE, r.getIsError());
        assertEquals("upstream:echo", textOf(r));
        assertEquals("echo", lastUpstreamRequest.get().name());
        assertEquals("hello", lastUpstreamRequest.get().arguments().raw().get("text"));
    }

    @Test
    void metaPassthrough() throws IOException {
        URI upstreamUrl = startUpstream(ECHO);
        startProxy(upstreamUrl, List.of(ECHO));

        JsonObject meta = new JsonObject();
        meta.add("progressToken", new JsonPrimitive("tkn-42"));

        client.callTool("echo", Map.of("text", "x"), meta);

        JsonObject seen = lastUpstreamRequest.get().jsonRpcMeta();
        assertNotNull(seen, "_meta must reach upstream");
        assertEquals("tkn-42", seen.get("progressToken").getAsString());
    }

    // ===== Upstream-down =====

    @Test
    void upstreamDownReturnsUpstreamDownMessage() throws IOException {
        int port = findUnusedPort();
        URI upstreamUrl = URI.create("http://127.0.0.1:" + port + "/mcp");
        startProxy(upstreamUrl, List.of(ECHO));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals(MESSAGES.upstreamDownMessage(), textOf(r));
    }

    @Test
    void upstreamDownIsNotPermanentlyDeadAcrossCalls() throws IOException {
        // Multiple calls against a down upstream all yield the same
        // upstream-down message — the proxy retries init each call (the
        // init failure is *not* cached as drift). This is the behaviour
        // that lets the operator start the upstream and have the next
        // call go through.
        int port = findUnusedPort();
        URI upstreamUrl = URI.create("http://127.0.0.1:" + port + "/mcp");
        startProxy(upstreamUrl, List.of(ECHO));

        for (int i = 0; i < 3; i++) {
            MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
            assertEquals(Boolean.TRUE, r.getIsError());
            assertEquals(MESSAGES.upstreamDownMessage(), textOf(r),
                    "call " + i + " should still see upstream-down, not drift");
        }
    }

    // ===== Drift detection (symmetric) =====

    @Test
    void driftExtraToolOnManifest() throws IOException {
        // Manifest has [echo, ping]; upstream only has [echo].
        URI upstreamUrl = startUpstream(ECHO);
        startProxy(upstreamUrl, List.of(ECHO, PING));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals(MESSAGES.driftMessage(), textOf(r));
    }

    @Test
    void driftExtraToolOnUpstream() throws IOException {
        // Manifest has [echo]; upstream has [echo, ping].
        URI upstreamUrl = startUpstream(ECHO, PING);
        startProxy(upstreamUrl, List.of(ECHO));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals(MESSAGES.driftMessage(), textOf(r));
    }

    @Test
    void driftFieldDifference() throws IOException {
        // Same name on both sides, different description.
        ToolDescriptor manifestEcho = new ToolDescriptor(
                "echo", "Manifest description",
                new InputSchemaBuilder().requiredString("text", "the text").build());
        ToolDescriptor upstreamEcho = new ToolDescriptor(
                "echo", "Upstream description",  // <-- differs
                new InputSchemaBuilder().requiredString("text", "the text").build());
        URI upstreamUrl = startUpstream(upstreamEcho);
        startProxy(upstreamUrl, List.of(manifestEcho));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals(MESSAGES.driftMessage(), textOf(r));
    }

    @Test
    void driftCachedForSession() throws IOException {
        // After drift is detected once, subsequent calls return the
        // same cached message *without* re-probing upstream.
        URI upstreamUrl = startUpstream(ECHO);
        startProxy(upstreamUrl, List.of(ECHO, PING));

        for (int i = 0; i < 3; i++) {
            MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
            assertEquals(Boolean.TRUE, r.getIsError());
            assertEquals(MESSAGES.driftMessage(), textOf(r));
        }
        // Upstream should have observed exactly one initialize and one
        // listTools — drift cache prevents re-probing.
        // (We don't assert the exact upstream session count because
        // re-probe would create new sessions on upstream; but
        // lastUpstreamRequest stayed null since no callTool was forwarded.)
        assertNull(lastUpstreamRequest.get(),
                "drifted session must not forward any callTool to upstream");
    }

    // ===== Upstream tool-layer error forwarding =====

    @Test
    void upstreamIsErrorForwardedVerbatim() throws IOException {
        // Upstream tool function throws MCPErrorResponseException with a
        // specific message — the proxy must forward that message as
        // isError, NOT replace it with one of its own ProxyMessages.
        upstream = new HttpMCPServer(0, "/mcp");
        upstream.getHandler().addTool(ECHO, request -> {
            throw new MCPErrorResponseException("upstream-tool-rejected-the-input");
        });
        upstream.start();
        URI upstreamUrl = URI.create(upstream.getUrl());
        startProxy(upstreamUrl, List.of(ECHO));

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "x"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals("upstream-tool-rejected-the-input", textOf(r));
    }

    // ===== Session-lost mid-call =====

    @Test
    void sessionLostMidCallReturnsSessionLostMessageAndResetsInit() throws IOException {
        URI upstreamUrl = startUpstream(ECHO);
        startProxy(upstreamUrl, List.of(ECHO));

        // First call: success (upstream init + listTools + callTool).
        client.callTool("echo", Map.of("text", "hi"));

        // Force upstream to drop its session — mimics a Swing app
        // restart while the proxy is running. The proxy's upstream
        // client still holds a now-stale session id; the next
        // tools/call will see HTTP 404 → MCPSessionLostException.
        List<MCPSession> evicted = upstream.getHandler().removeAllSessions();
        assertEquals(1, evicted.size(), "expected exactly one upstream session to evict");

        MCPProtocol.CallToolResult r = client.callTool("echo", Map.of("text", "ping"));
        assertEquals(Boolean.TRUE, r.getIsError());
        assertEquals(MESSAGES.sessionLostMessage(), textOf(r));

        // The third call must walk lazy-init from scratch and succeed.
        MCPProtocol.CallToolResult r3 = client.callTool("echo", Map.of("text", "fresh"));
        assertNotEquals(Boolean.TRUE, r3.getIsError(),
                "after session-lost, the next call must re-initialize upstream and succeed");
    }

    // ===== Round-trip count regression guard =====

    @Test
    void firstCallTriggersInitializeListToolsCallTool_subsequentCallsTriggerOnlyCallTool() throws IOException {
        // Wrap upstream's handler with a request counter. Counts every
        // POST handled by upstream's HTTP server.
        upstream = new HttpMCPServer(0, "/mcp");
        AtomicInteger upstreamRequests = new AtomicInteger();
        AtomicInteger upstreamCallToolCount = new AtomicInteger();
        upstream.getHandler().addTool(ECHO, request -> {
            upstreamCallToolCount.incrementAndGet();
            return MCPProtocol.Content.text("ok");
        });
        upstream.getHandler().setOnSessionStarted(s -> upstreamRequests.incrementAndGet());
        upstream.start();
        URI upstreamUrl = URI.create(upstream.getUrl());

        startProxy(upstreamUrl, List.of(ECHO));

        // Pre-call: only the proxy's own session has been created.
        // Upstream has not been touched yet.
        assertEquals(0, upstreamRequests.get(),
                "upstream must not be touched until the first tools/call");

        // First call: triggers init → listTools (drift probe) → callTool.
        client.callTool("echo", Map.of("text", "first"));
        assertEquals(1, upstreamRequests.get(),
                "first call must initialize exactly one upstream session");
        assertEquals(1, upstreamCallToolCount.get());

        // Second call: only callTool, no re-init.
        client.callTool("echo", Map.of("text", "second"));
        assertEquals(1, upstreamRequests.get(),
                "second call must NOT trigger another upstream initialize");
        assertEquals(2, upstreamCallToolCount.get());
    }

    // ===== Single-session policy by default (supersede on conflict, DR-supersede-sessions) =====

    @Test
    void proxyIsSingleSessionByDefault() throws IOException {
        URI upstreamUrl = startUpstream(ECHO);
        MCPHandler handler = MCPProxy.newHandler(List.of(ECHO), upstreamUrl, MESSAGES);
        proxy = new HttpMCPServer(0, "/mcp", handler);
        proxy.start();

        // First client: succeeds.
        TinyMCPClient first = new TinyMCPClient(URI.create(proxy.getUrl()));
        first.initialize();

        // Second client: also succeeds, evicting the first (DR-supersede-sessions supersede).
        try (TinyMCPClient second = new TinyMCPClient(URI.create(proxy.getUrl()))) {
            second.initialize();
            // The first client's next call is rejected with the supersede tombstone.
            MCPSessionLostException ex = assertThrows(MCPSessionLostException.class,
                    () -> first.callTool("echo", Map.of("text", "anything")),
                    "first session must be superseded once the second initializes");
            assertTrue(ex.getMessage().toLowerCase().contains("supersed"),
                    "tombstone message should explain the eviction; got: " + ex.getMessage());
        }

        first.close();
    }

    // ===== Tracks accumulated requests by hooking the proxy's session =====

    @SuppressWarnings("unused")
    private List<String> recordedSessionIds() {
        // Reserved for future tests that need to enumerate session ids.
        return new ArrayList<>();
    }
}
