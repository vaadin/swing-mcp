package com.vaadin.swingmcp.agent;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;

class AgentTest {

    /**
     * Calls {@code premain} directly (no real {@link java.lang.instrument.Instrumentation}
     * needed) and verifies that the MCP server starts accepting HTTP connections
     * on the default port.
     */
    @Test
    void premainStartsMcpServer() throws Exception {
        Agent.premain(null, null);

        // The server starts on a background thread; poll until it's ready.
        int port = 18088;
        long deadline = System.currentTimeMillis() + 5_000;
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
