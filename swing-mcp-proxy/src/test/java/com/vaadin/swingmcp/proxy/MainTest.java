package com.vaadin.swingmcp.proxy;

import com.vaadin.swingmcp.tinymcpserver.HttpMCPServer;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.ProxyMessages;
import com.vaadin.swingmcp.tools.SwingTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the proxy's wire-up: port resolution, {@link ProxyMessages}
 * construction, identity passthrough, and an end-to-end smoke test that
 * drives {@code Main.runWithStreams} against a real upstream
 * {@link HttpMCPServer} over piped stdio.
 *
 * <p>End-to-end behaviour of the forwarding stack itself is covered by
 * {@code MCPProxyTest} in {@code tiny-mcp-server}; the smoke test here
 * exists to catch packaging-level breakage (server identity not wired,
 * stdio framing not wired, manifest source mismatch) that the unit
 * tests would miss.
 */
class MainTest {

    // ===== Port resolution =====

    @Test
    void resolvePortDefaultsTo18088() {
        assertEquals(HttpMCPServer.DEFAULT_PORT,
                Main.resolvePort(null, null));
        assertEquals(HttpMCPServer.DEFAULT_PORT,
                Main.resolvePort("", null));
        assertEquals(HttpMCPServer.DEFAULT_PORT,
                Main.resolvePort("   ", "   "));
    }

    @Test
    void resolvePortPrefersSystemPropertyOverEnv() {
        assertEquals(20000, Main.resolvePort("20000", "9999"));
    }

    @Test
    void resolvePortFallsBackToEnvWhenSystemPropertyMissing() {
        assertEquals(9999, Main.resolvePort(null, "9999"));
        assertEquals(9999, Main.resolvePort("", "9999"));
    }

    @Test
    void resolvePortAcceptsZero() {
        // Port 0 means "ephemeral" — useful for tests pointing at a
        // pre-allocated socket.
        assertEquals(0, Main.resolvePort("0", null));
    }

    @Test
    void resolvePortRejectsNonNumeric() {
        NumberFormatException e = assertThrows(NumberFormatException.class,
                () -> Main.resolvePort("not-a-port", null));
        assertTrue(e.getMessage().contains("not-a-port"));
        assertTrue(e.getMessage().contains(Main.PORT_SYSTEM_PROPERTY));
        assertTrue(e.getMessage().contains(Main.PORT_ENV_VAR));
    }

    @Test
    void resolvePortRejectsOutOfRange() {
        assertThrows(NumberFormatException.class, () -> Main.resolvePort("65536", null));
        assertThrows(NumberFormatException.class, () -> Main.resolvePort("-1", null));
    }

    // ===== ProxyMessages wording =====

    @Test
    void proxyMessagesInterpolateUrl() {
        URI upstream = URI.create("http://127.0.0.1:18088/mcp");
        ProxyMessages m = Main.buildProxyMessages(upstream);

        assertTrue(m.upstreamDownMessage().contains("Swing MCP Agent at " + upstream),
                "upstream-down message must include the upstream URL");
        assertTrue(m.upstreamDownMessage().contains("start the Swing application"),
                "upstream-down message must guide the user to start the app");

        assertTrue(m.driftMessage().contains("Swing MCP Agent at " + upstream));
        assertTrue(m.driftMessage().contains("Do not retry."),
                "drift message must include the no-retry directive");

        assertTrue(m.ioMidCallMessage().contains("Swing MCP Agent at " + upstream));
        assertTrue(m.ioMidCallMessage().contains("call swing_snapshot to verify"),
                "io-mid-call message must guide the user to verify via swing_snapshot");
    }

    @Test
    void sessionLostMessageIsTheSharedConstant() {
        // The shared SESSION_LOST_MESSAGE ensures the proxy and the
        // in-process server speak with one voice (grilling Sub-item 1).
        URI upstream = URI.create("http://127.0.0.1:18088/mcp");
        ProxyMessages m = Main.buildProxyMessages(upstream);
        assertEquals(SwingTools.SESSION_LOST_MESSAGE, m.sessionLostMessage());
    }

    @Test
    void messagesUseAgentVsApplicationDistinction() {
        // Connectivity errors say "Swing MCP Agent" (right diagnostic —
        // is the agent loaded?). Session-lost says "Swing application"
        // (right diagnostic — did the app restart?).
        URI upstream = URI.create("http://127.0.0.1:18088/mcp");
        ProxyMessages m = Main.buildProxyMessages(upstream);
        assertTrue(m.upstreamDownMessage().contains("Swing MCP Agent"));
        assertTrue(m.driftMessage().contains("Swing MCP Agent"));
        assertTrue(m.ioMidCallMessage().contains("Swing MCP Agent"));
        assertTrue(m.sessionLostMessage().contains("Swing application"));
    }

    // ===== Stdio fixture (piped streams + worker thread) =====

    private HttpMCPServer upstream;
    private Thread worker;
    private PipedOutputStream clientOut;
    private BufferedReader clientIn;
    private final AtomicReference<Throwable> workerError = new AtomicReference<>();
    private final AtomicReference<String> lastUpstreamCall = new AtomicReference<>();

    @AfterEach
    void teardownStdio() throws Exception {
        if (clientOut != null) {
            try { clientOut.close(); } catch (IOException ignored) {}
        }
        if (worker != null) {
            worker.join(5_000);
            assertFalse(worker.isAlive(), "runWithStreams did not return after EOF");
        }
        if (upstream != null) {
            upstream.stop();
        }
        if (workerError.get() != null) {
            throw new AssertionError("runWithStreams worker failed", workerError.get());
        }
    }

    /**
     * Stands up an upstream {@link HttpMCPServer} on an ephemeral port
     * with the full {@link SwingTools#ALL} manifest registered. Each
     * tool simply records the call name in {@link #lastUpstreamCall} and
     * returns a sentinel string so the smoke test can assert the round
     * trip.
     */
    private URI startUpstream() {
        upstream = new HttpMCPServer(0, "/mcp");
        for (var descriptor : SwingTools.ALL) {
            upstream.getHandler().addTool(descriptor, request -> {
                lastUpstreamCall.set(request.name());
                return MCPProtocol.Content.text("upstream:" + request.name());
            });
        }
        upstream.start();
        return URI.create(upstream.getUrl());
    }

    /**
     * Wires piped streams and launches {@link Main#runWithStreams} on a
     * daemon worker. Returns a writer pointing at the proxy's stdin;
     * {@link #clientIn} is set to a reader on the proxy's stdout.
     */
    private BufferedWriter startProxy(URI upstreamUrl) throws IOException {
        PipedInputStream serverIn = new PipedInputStream(64 * 1024);
        clientOut = new PipedOutputStream(serverIn);
        PipedOutputStream serverOut = new PipedOutputStream();
        PipedInputStream clientInPipe = new PipedInputStream(serverOut, 64 * 1024);
        clientIn = new BufferedReader(new InputStreamReader(clientInPipe, StandardCharsets.UTF_8));
        PrintStream serverOutPs = new PrintStream(serverOut, true, StandardCharsets.UTF_8);

        worker = new Thread(() -> {
            try {
                Main.runWithStreams(upstreamUrl, serverIn, serverOutPs);
            } catch (Throwable t) {
                workerError.set(t);
            }
        }, "swing-mcp-proxy-test");
        worker.setDaemon(true);
        worker.start();
        return new BufferedWriter(new OutputStreamWriter(clientOut, StandardCharsets.UTF_8));
    }

    private static void send(BufferedWriter w, String json) throws IOException {
        w.write(json);
        w.write('\n');
        w.flush();
    }

    private MCPProtocol.JsonRpcResponse readResponse() throws IOException {
        String line = clientIn.readLine();
        assertNotNull(line, "expected response line, got EOF");
        // Parse as JsonRpcError first to surface error envelopes with a
        // clear failure message; success envelopes leave error=null.
        MCPProtocol.JsonRpcError err = MCPProtocol.fromJson(line, MCPProtocol.JsonRpcError.class);
        if (err.getError() != null) {
            fail("expected a successful JSON-RPC response but got error: "
                    + err.getError().getCode() + " " + err.getError().getMessage()
                    + " — raw=" + line);
        }
        return MCPProtocol.fromJson(line, MCPProtocol.JsonRpcResponse.class);
    }

    private static String initRequest(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-11-25\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}";
    }

    // ===== Identity passthrough =====

    @Test
    void initializeReturnsSwingToolDefsServerInfoAndInstructions() throws Exception {
        // Boot the proxy without an upstream — initialize is local to the
        // proxy, so server identity comes purely from swing-mcp-tool-defs.
        // Picking an unused port keeps the URL well-formed without a
        // listener attached.
        URI upstreamUrl = URI.create("http://127.0.0.1:1/mcp");
        BufferedWriter w = startProxy(upstreamUrl);
        send(w, initRequest(1));

        MCPProtocol.InitializeResult result = readResponse()
                .getResultAs(MCPProtocol.InitializeResult.class);

        // Bit-identical with the constants in swing-mcp-tool-defs — that's
        // the whole point of routing identity through the shared module.
        assertEquals(SwingTools.SERVER_NAME, result.getServerInfo().getName());
        assertEquals(SwingTools.SERVER_VERSION, result.getServerInfo().getVersion());
        assertEquals(SwingTools.INSTRUCTIONS, result.getInstructions());
    }

    @Test
    void toolsListReturnsSwingToolsManifestVerbatim() throws Exception {
        // tools/list answers from the static manifest — no upstream traffic.
        URI upstreamUrl = URI.create("http://127.0.0.1:1/mcp");
        BufferedWriter w = startProxy(upstreamUrl);
        send(w, initRequest(1));
        readResponse();

        send(w, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        MCPProtocol.ListToolsResult result = readResponse()
                .getResultAs(MCPProtocol.ListToolsResult.class);

        assertEquals(SwingTools.ALL.size(), result.getTools().size(),
                "proxy must surface every tool in the shared manifest");
        for (int i = 0; i < SwingTools.ALL.size(); i++) {
            assertEquals(SwingTools.ALL.get(i).name(),
                    result.getTools().get(i).getName(),
                    "tool order must match SwingTools.ALL");
        }
    }

    // ===== End-to-end smoke =====

    @Test
    void toolsCallForwardsThroughStdioAndHttp() throws Exception {
        // Drive the full Main → stdio → upstream HTTP path. Catches
        // packaging-level breakage that the wire-up unit tests above
        // would miss (e.g. forwarder not registered for the manifest,
        // ProxyMessages misconstructed, identity not propagated).
        URI upstreamUrl = startUpstream();
        BufferedWriter w = startProxy(upstreamUrl);
        send(w, initRequest(1));
        readResponse();

        send(w, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"swing_snapshot\",\"arguments\":{}}}");
        MCPProtocol.JsonRpcResponse resp = readResponse();
        assertEquals(2.0, ((Number) resp.getId()).doubleValue());
        MCPProtocol.CallToolResult result = resp.getResultAs(MCPProtocol.CallToolResult.class);
        // The upstream stub returns "upstream:<name>" — proves the call
        // crossed the proxy and came back through the same wire.
        assertEquals("upstream:swing_snapshot", result.getContent().get(0).getText());
        assertEquals("swing_snapshot", lastUpstreamCall.get(),
                "upstream must have observed the forwarded call");
    }
}
