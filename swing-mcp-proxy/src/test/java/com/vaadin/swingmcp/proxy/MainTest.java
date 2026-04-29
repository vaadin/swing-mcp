package com.vaadin.swingmcp.proxy;

import com.vaadin.swingmcp.tinymcpserver.HttpMCPServer;
import com.vaadin.swingmcp.tinymcpserver.ProxyMessages;
import com.vaadin.swingmcp.tools.SwingTools;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the proxy's wire-up: port resolution and the
 * {@link ProxyMessages} construction. End-to-end behaviour of the
 * forwarding stack itself is covered by {@code MCPProxyTest} in
 * {@code tiny-mcp-server}.
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
}
