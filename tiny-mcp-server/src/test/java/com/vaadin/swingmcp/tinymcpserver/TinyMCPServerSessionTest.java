package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP session lifecycle gate (UC-005).
 * Uses raw HTTP requests for precise control over the {@code Mcp-Session-Id} header.
 */
class TinyMCPServerSessionTest {

    private static TinyMCPServer server;
    private static HttpClient http;
    private static URI serverUri;

    @BeforeAll
    static void startServer() throws Exception {
        server = new TinyMCPServer(0, "/mcp");
        server.addTool("echo", "Echo tool",
                new InputSchemaBuilder().requiredString("msg", "message").build(),
                params -> MCPProtocol.Content.text((String) params.get("msg")));
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

    /**
     * Terminates any active session before each test so every test
     * starts with a clean slate ({@code activeSessionId == null}).
     */
    @BeforeEach
    void resetSession() throws Exception {
        http.send(HttpRequest.newBuilder(serverUri).DELETE().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    // ===== Helpers =====

    private HttpResponse<String> post(String jsonRpcBody, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRpcBody));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUri).DELETE();
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonRpc(String method, int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    private static String jsonRpcToolsCall(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{\"msg\":\"hi\"}}}";
    }

    /**
     * Sends an initialize request and returns the session ID from the response header.
     */
    private String initialize() throws Exception {
        HttpResponse<String> resp = post(jsonRpc("initialize", 1), null);
        assertEquals(200, resp.statusCode(), "initialize should succeed");
        return resp.headers().firstValue("Mcp-Session-Id").orElse(null);
    }

    private static void assertJsonRpcError(HttpResponse<String> resp, int expectedHttpStatus,
                                           int expectedCode, String expectedMessage) {
        assertEquals(expectedHttpStatus, resp.statusCode());
        JsonObject body = MCPProtocol.fromJson(resp.body(), JsonObject.class);
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        JsonObject error = body.getAsJsonObject("error");
        assertNotNull(error, "expected JSON-RPC error in body");
        assertEquals(expectedCode, error.get("code").getAsInt());
        assertEquals(expectedMessage, error.get("message").getAsString());
    }

    // ===== Tests =====

    @Test
    void toolsListBeforeInitializeReturns400() throws Exception {
        HttpResponse<String> resp = post(jsonRpc("tools/list", 1), null);
        assertJsonRpcError(resp, 400, -32002, "Server not initialized. Send 'initialize' first.");
    }

    @Test
    void toolsCallBeforeInitializeReturns400() throws Exception {
        HttpResponse<String> resp = post(jsonRpcToolsCall(1), null);
        assertJsonRpcError(resp, 400, -32002, "Server not initialized. Send 'initialize' first.");
    }

    @Test
    void toolsListWithWrongSessionIdReturns404() throws Exception {
        initialize();
        HttpResponse<String> resp = post(jsonRpc("tools/list", 2), "wrong-session-id");
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }

    @Test
    void toolsListWithCorrectSessionIdSucceeds() throws Exception {
        String sessionId = initialize();
        assertNotNull(sessionId, "initialize should return a session ID");
        HttpResponse<String> resp = post(jsonRpc("tools/list", 2), sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject body = MCPProtocol.fromJson(resp.body(), JsonObject.class);
        assertNotNull(body.get("result"), "expected a result, not an error");
    }

    @Test
    void pingBeforeInitializeSucceeds() throws Exception {
        HttpResponse<String> resp = post(jsonRpc("ping", 1), null);
        assertEquals(200, resp.statusCode());
    }

    @Test
    void pingAfterInitializeWithoutSessionIdSucceeds() throws Exception {
        initialize();
        HttpResponse<String> resp = post(jsonRpc("ping", 2), null);
        assertEquals(200, resp.statusCode());
    }

    @Test
    void initializeAfterDeleteSucceeds() throws Exception {
        String firstSessionId = initialize();
        delete(firstSessionId);

        String secondSessionId = initialize();
        assertNotNull(secondSessionId);
        assertNotEquals(firstSessionId, secondSessionId, "new session should have a different ID");
    }

    @Test
    void toolsListAfterDeleteReturns400() throws Exception {
        initialize();
        delete(null);

        HttpResponse<String> resp = post(jsonRpc("tools/list", 2), null);
        assertJsonRpcError(resp, 400, -32002, "Server not initialized. Send 'initialize' first.");
    }

    @Test
    void toolsCallAfterDeleteWithStaleSessionIdReturns404() throws Exception {
        String sessionId = initialize();
        delete(sessionId);

        HttpResponse<String> resp = post(jsonRpcToolsCall(2), sessionId);
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }

    @Test
    void unknownSessionIdWhenNoActiveSessionReturns404() throws Exception {
        // No init — activeSessionId is null. Any Mcp-Session-Id is unknown.
        HttpResponse<String> resp = post(jsonRpc("initialize", 1), "some-random-id");
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }

    @Test
    void pingWithWrongSessionIdReturns404() throws Exception {
        initialize();
        HttpResponse<String> resp = post(jsonRpc("ping", 2), "wrong-session-id");
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }
}
