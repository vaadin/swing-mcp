package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the component ref map is cleared when a session is terminated
 * via HTTP DELETE. The {@link com.vaadin.swingmcp.mcp.tools.SwingToolContext}
 * (which owns the ref map) is stored as a per-session attribute on
 * {@link com.vaadin.swingmcp.tinymcpserver.MCPSession}, so removing the
 * session from the server's session map drops the ref map with it.
 */
class SessionCloseTest {

    private static FakeSwingMCPHandler server;
    private static HttpClient http;
    private static URI serverUri;

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    @BeforeAll
    static void startServer() throws Exception {
        server = new FakeSwingMCPHandler(0, "/mcp", false);
        server.setConsideredComponents(List.of(new JButton("Test")));
        server.start();
        http = HttpClient.newHttpClient();
        serverUri = URI.create(server.getUrl());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    // ===== Helpers =====

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String initialize() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}";
        HttpResponse<String> resp = post(body, null);
        assertEquals(200, resp.statusCode());
        return resp.headers().firstValue("Mcp-Session-Id").orElse(null);
    }

    private void delete(String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri).DELETE();
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
    }

    private HttpResponse<String> snapshot(String sessionId) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"swing_snapshot\",\"arguments\":{}}}";
        return post(body, sessionId);
    }

    private HttpResponse<String> click(int ref, String sessionId) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"swing_click\",\"arguments\":{\"ref\":" + ref + "}}}";
        return post(body, sessionId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        return MCPProtocol.fromJson(json, Map.class);
    }

    // ===== Test =====

    @SuppressWarnings("unchecked")
    @Test
    void sessionDeleteClearsRefMap() throws Exception {
        // Session 1: initialize and snapshot to populate refs
        String session1 = initialize();
        assertNotNull(session1);

        HttpResponse<String> snapResp = snapshot(session1);
        assertEquals(200, snapResp.statusCode());
        Map<String, Object> snapBody = parseJson(snapResp.body());
        assertNotNull(snapBody.get("result"), "snapshot should succeed");

        // Verify a ref works in session 1
        HttpResponse<String> clickResp = click(1, session1);
        assertEquals(200, clickResp.statusCode());
        Map<String, Object> clickBody = parseJson(clickResp.body());
        assertNotNull(clickBody.get("result"), "click should succeed");

        // Terminate session 1 — this should clear the ref map
        delete(session1);

        // Session 2: initialize a new session
        String session2 = initialize();
        assertNotNull(session2);
        assertNotEquals(session1, session2);

        // Try to use a ref without re-snapshotting — should fail because
        // the previous session's ref map was dropped when its MCPSession
        // was removed. The stale-ref check in SwingToolContext throws
        // MCPServerException (INVALID_PARAMS), which HttpMCPServer renders
        // as a JSON-RPC error.
        HttpResponse<String> staleClickResp = click(1, session2);
        assertEquals(200, staleClickResp.statusCode());
        Map<String, Object> staleBody = parseJson(staleClickResp.body());
        Map<String, Object> error = (Map<String, Object>) staleBody.get("error");
        assertNotNull(error, "should get a JSON-RPC error for stale ref");
        String errorMessage = (String) error.get("message");
        assertTrue(errorMessage.contains("stale"),
                "error should mention stale ref map, got: " + errorMessage);
    }
}
