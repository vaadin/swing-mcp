package com.vaadin.swingmcp.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;

class AgentTest {

    @AfterEach
    void tearDown() {
        if (Agent.server != null) {
            Agent.server.stop();
            Agent.server = null;
        }
        System.clearProperty(Agent.PORT_PROPERTY);
    }

    /**
     * Calls {@code premain} directly (no real {@link java.lang.instrument.Instrumentation}
     * needed) and verifies that the MCP server starts accepting HTTP connections.
     * Uses port 0 (OS-assigned ephemeral port) so parallel test runs don't collide.
     */
    @Test
    void premainStartsMcpServer() throws Exception {
        System.setProperty(Agent.PORT_PROPERTY, "0");
        Agent.premain(null, null);

        // The server starts on a background thread; poll until it's visible, then until reachable.
        long deadline = System.currentTimeMillis() + 5_000;
        while (Agent.server == null) {
            if (System.currentTimeMillis() >= deadline) {
                fail("MCP server did not publish itself within 5 seconds");
            }
            Thread.sleep(50);
        }

        int port = Agent.server.getPort();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create("http://127.0.0.1:" + port + "/mcp").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(200);
                conn.setReadTimeout(200);
                conn.getResponseCode();
                // Any HTTP response (even an error) means the server is up.
                return; // success
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                // Server not ready yet — retry.
                Thread.sleep(100);
            }
        }
        fail("MCP server did not start within 5 seconds");
    }
}
