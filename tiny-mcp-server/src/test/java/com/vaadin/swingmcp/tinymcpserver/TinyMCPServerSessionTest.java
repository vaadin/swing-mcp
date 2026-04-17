package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonArray;
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
 * Tests for MCP session lifecycle gate (see DR-005 in {@code spec/decisions.md}).
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
        server.addTool("whoami", "Returns MCPSession.getCurrent().getId()",
                new InputSchemaBuilder().build(),
                params -> MCPProtocol.Content.text(MCPSession.getCurrent().getId()));
        server.addTool("set_attr", "Store an attribute on the current session",
                new InputSchemaBuilder()
                        .requiredString("key", "key")
                        .requiredString("value", "value")
                        .build(),
                params -> {
                    MCPSession.getCurrent().setAttribute((String) params.get("key"), params.get("value"));
                    return MCPProtocol.Content.text("ok");
                });
        server.addTool("get_attr", "Read an attribute from the current session",
                new InputSchemaBuilder().requiredString("key", "key").build(),
                params -> {
                    Object v = MCPSession.getCurrent().getAttribute((String) params.get("key"));
                    return MCPProtocol.Content.text(v == null ? "<null>" : v.toString());
                });
        server.addPrompt("greet", "Greet someone",
                new InputSchemaBuilder()
                        .requiredString("name", "Who to greet")
                        .optionalString("style", "Greeting style")
                        .build(),
                args -> {
                    MCPProtocol.GetPromptResult r = new MCPProtocol.GetPromptResult();
                    String style = args.getOrDefault("style", "friendly");
                    r.setDescription(style + " greeting");
                    MCPProtocol.PromptMessage msg = new MCPProtocol.PromptMessage();
                    msg.setRole("user");
                    msg.setContent(MCPProtocol.Content.text("Hello, " + args.get("name") + "!"));
                    r.setMessages(java.util.List.of(msg));
                    return r;
                });
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
     * Terminates all active sessions before each test so every test
     * starts with a clean slate.
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
        // No init — no sessions. Any Mcp-Session-Id is unknown.
        HttpResponse<String> resp = post(jsonRpc("initialize", 1), "some-random-id");
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }

    @Test
    void pingWithWrongSessionIdReturns404() throws Exception {
        initialize();
        HttpResponse<String> resp = post(jsonRpc("ping", 2), "wrong-session-id");
        assertJsonRpcError(resp, 404, -32002, "Session not found.");
    }

    // ===== Multi-session =====

    @Test
    void doubleInitializeCreatesSecondSession() throws Exception {
        String first = initialize();
        String second = initialize();
        assertNotNull(second);
        assertNotEquals(first, second, "second session should have a different ID");
    }

    @Test
    void twoSessionsOperateIndependently() throws Exception {
        String sessionA = initialize();
        String sessionB = initialize();

        // Session A can list tools
        HttpResponse<String> respA = post(jsonRpc("tools/list", 10), sessionA);
        assertEquals(200, respA.statusCode());
        JsonObject bodyA = MCPProtocol.fromJson(respA.body(), JsonObject.class);
        assertNotNull(bodyA.get("result"), "session A should get a result");

        // Session B can list tools
        HttpResponse<String> respB = post(jsonRpc("tools/list", 11), sessionB);
        assertEquals(200, respB.statusCode());
        JsonObject bodyB = MCPProtocol.fromJson(respB.body(), JsonObject.class);
        assertNotNull(bodyB.get("result"), "session B should get a result");

        // Delete session A
        delete(sessionA);

        // Session A no longer works
        HttpResponse<String> respA2 = post(jsonRpc("tools/list", 12), sessionA);
        assertJsonRpcError(respA2, 404, -32002, "Session not found.");

        // Session B still works
        HttpResponse<String> respB2 = post(jsonRpc("tools/list", 13), sessionB);
        assertEquals(200, respB2.statusCode());
    }

    // ===== MCPSession.getCurrent() inside tool callback =====

    private static String jsonRpcToolsCall(int id, String tool, String argsJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":" + argsJson + "}}";
    }

    private static String firstTextContent(HttpResponse<String> resp) {
        JsonObject body = MCPProtocol.fromJson(resp.body(), JsonObject.class);
        JsonObject result = body.getAsJsonObject("result");
        assertNotNull(result, "expected a result, got: " + resp.body());
        JsonArray content = result.getAsJsonArray("content");
        assertEquals(1, content.size());
        return content.get(0).getAsJsonObject().get("text").getAsString();
    }

    @Test
    void getCurrentInsideToolCallReturnsInvokingSession() throws Exception {
        String sessionId = initialize();
        HttpResponse<String> resp = post(jsonRpcToolsCall(2, "whoami", "{}"), sessionId);
        assertEquals(200, resp.statusCode());
        assertEquals(sessionId, firstTextContent(resp));
    }

    @Test
    void getCurrentThrowsOnTestThreadOutsideDispatch() {
        // handlePost binds/unbinds the ThreadLocal on the HTTP dispatch thread.
        // The test thread never ran a dispatch, so no session is bound here.
        assertThrows(NullPointerException.class, MCPSession::getCurrent);
    }

    @Test
    void threadLocalIsClearedAfterDispatch() throws Exception {
        String sessionId = initialize();
        HttpResponse<String> resp = post(jsonRpcToolsCall(2, "whoami", "{}"), sessionId);
        assertEquals(200, resp.statusCode());
        // After the dispatch returns, the test thread still sees no binding.
        assertThrows(NullPointerException.class, MCPSession::getCurrent);
    }

    @Test
    void attributesPersistAcrossToolCallsInSameSession() throws Exception {
        String sessionId = initialize();

        HttpResponse<String> setResp = post(
                jsonRpcToolsCall(2, "set_attr", "{\"key\":\"color\",\"value\":\"blue\"}"),
                sessionId);
        assertEquals(200, setResp.statusCode());
        assertEquals("ok", firstTextContent(setResp));

        HttpResponse<String> getResp = post(
                jsonRpcToolsCall(3, "get_attr", "{\"key\":\"color\"}"),
                sessionId);
        assertEquals(200, getResp.statusCode());
        assertEquals("blue", firstTextContent(getResp));
    }

    @Test
    void attributesAreIsolatedAcrossSessions() throws Exception {
        String sessionA = initialize();
        String sessionB = initialize();

        HttpResponse<String> setA = post(
                jsonRpcToolsCall(2, "set_attr", "{\"key\":\"color\",\"value\":\"red\"}"),
                sessionA);
        assertEquals(200, setA.statusCode());

        HttpResponse<String> getB = post(
                jsonRpcToolsCall(3, "get_attr", "{\"key\":\"color\"}"),
                sessionB);
        assertEquals(200, getB.statusCode());
        assertEquals("<null>", firstTextContent(getB));

        HttpResponse<String> getA = post(
                jsonRpcToolsCall(4, "get_attr", "{\"key\":\"color\"}"),
                sessionA);
        assertEquals(200, getA.statusCode());
        assertEquals("red", firstTextContent(getA));
    }

    // ===== Prompts =====

    @Test
    void promptsListReturnsRegisteredPrompts() throws Exception {
        String sessionId = initialize();
        HttpResponse<String> resp = post(jsonRpc("prompts/list", 2), sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject body = MCPProtocol.fromJson(resp.body(), JsonObject.class);
        JsonArray prompts = body.getAsJsonObject("result").getAsJsonArray("prompts");
        assertEquals(1, prompts.size());
        assertEquals("greet", prompts.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void promptsGetReturnsExpandedResult() throws Exception {
        String sessionId = initialize();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"Alice\",\"style\":\"formal\"}}}";
        HttpResponse<String> resp = post(body, sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject result = MCPProtocol.fromJson(resp.body(), JsonObject.class).getAsJsonObject("result");
        assertEquals("formal greeting", result.get("description").getAsString());
        JsonArray messages = result.getAsJsonArray("messages");
        assertEquals(1, messages.size());
        assertEquals("Hello, Alice!",
                messages.get(0).getAsJsonObject().getAsJsonObject("content").get("text").getAsString());
    }

    @Test
    void promptsGetUsesDefaultForOmittedOptionalArg() throws Exception {
        String sessionId = initialize();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"Bob\"}}}";
        HttpResponse<String> resp = post(body, sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject result = MCPProtocol.fromJson(resp.body(), JsonObject.class).getAsJsonObject("result");
        assertEquals("friendly greeting", result.get("description").getAsString());
    }

    @Test
    void promptsGetUnknownPromptReturnsInvalidParams() throws Exception {
        String sessionId = initialize();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"nope\",\"arguments\":{}}}";
        HttpResponse<String> resp = post(body, sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject err = MCPProtocol.fromJson(resp.body(), JsonObject.class).getAsJsonObject("error");
        assertNotNull(err);
        assertEquals(-32602, err.get("code").getAsInt());
    }

    @Test
    void promptsGetMissingRequiredArgReturnsInvalidParams() throws Exception {
        String sessionId = initialize();
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"prompts/get\","
                + "\"params\":{\"name\":\"greet\",\"arguments\":{}}}";
        HttpResponse<String> resp = post(body, sessionId);
        assertEquals(200, resp.statusCode());
        JsonObject err = MCPProtocol.fromJson(resp.body(), JsonObject.class).getAsJsonObject("error");
        assertNotNull(err);
        assertEquals(-32602, err.get("code").getAsInt());
    }

    // ===== acceptNewSession hook =====

    @Test
    void acceptNewSessionHookCanRejectSecondSession() throws Exception {
        TinyMCPServer singleSessionServer = new TinyMCPServer(0, "/mcp") {
            @Override
            protected boolean acceptNewSession() {
                return getSessionCount() == 0;
            }
        };
        singleSessionServer.addTool("echo", "Echo tool",
                new InputSchemaBuilder().requiredString("msg", "message").build(),
                params -> MCPProtocol.Content.text((String) params.get("msg")));
        singleSessionServer.start();
        try {
            URI uri = URI.create(singleSessionServer.getUrl());

            // First initialize succeeds
            HttpResponse<String> resp1 = http.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonRpc("initialize", 1)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp1.statusCode());

            // Second initialize rejected
            HttpResponse<String> resp2 = http.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json, text/event-stream")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonRpc("initialize", 2)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertJsonRpcError(resp2, 409, -32002, "Another session is already active");
        } finally {
            singleSessionServer.stop();
        }
    }
}
