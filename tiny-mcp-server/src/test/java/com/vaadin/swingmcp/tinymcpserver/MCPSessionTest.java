package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MCPSession} protocol dispatch.
 * Uses {@link FakeHttpExchange} to test without an HTTP server.
 */
class MCPSessionTest {

    private static MCPSession session;

    @BeforeAll
    static void setup() {
        MCPToolHandler toolHandler = new MCPToolHandler();
        toolHandler.addTool("echo", "Echo tool",
                new InputSchemaBuilder().requiredString("msg", "message").build(),
                params -> MCPProtocol.Content.text((String) params.get("msg")));
        session = new MCPSession("test-session-id", toolHandler);
    }

    // ===== Helpers =====

    private FakeHttpExchange dispatch(String jsonRpcBody) throws IOException {
        FakeHttpExchange exchange = new FakeHttpExchange(jsonRpcBody);
        JsonRpcExchange rpc = new JsonRpcExchange(exchange);
        MCPProtocol.JsonRpcRequest request = rpc.parsePost();
        assertNotNull(request, "parsePost should succeed for valid JSON-RPC");
        session.handlePost(rpc, request);
        return exchange;
    }

    private FakeHttpExchange dispatch(String method, int id) throws IOException {
        return dispatch("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}");
    }

    private static JsonObject parseResponse(FakeHttpExchange exchange) {
        return MCPProtocol.fromJson(exchange.getResponseBodyString(), JsonObject.class);
    }

    // ===== Tests =====

    @Test
    void resourcesListReturnsEmptyList() throws Exception {
        FakeHttpExchange exchange = dispatch("resources/list", 1);
        assertEquals(200, exchange.getResponseCode());
        JsonObject result = parseResponse(exchange).getAsJsonObject("result");
        assertNotNull(result);
        assertTrue(result.getAsJsonArray("resources").isEmpty());
    }

    @Test
    void promptsListReturnsEmptyList() throws Exception {
        FakeHttpExchange exchange = dispatch("prompts/list", 1);
        assertEquals(200, exchange.getResponseCode());
        JsonObject result = parseResponse(exchange).getAsJsonObject("result");
        assertNotNull(result);
        assertTrue(result.getAsJsonArray("prompts").isEmpty());
    }

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        FakeHttpExchange exchange = dispatch("nonexistent/method", 1);
        assertEquals(200, exchange.getResponseCode());
        JsonObject error = parseResponse(exchange).getAsJsonObject("error");
        assertNotNull(error);
        assertEquals(-32601, error.get("code").getAsInt());
        assertTrue(error.get("message").getAsString().contains("nonexistent/method"));
    }

    @Test
    void toolsListDelegatesToToolHandler() throws Exception {
        FakeHttpExchange exchange = dispatch("tools/list", 1);
        assertEquals(200, exchange.getResponseCode());
        JsonObject result = parseResponse(exchange).getAsJsonObject("result");
        assertNotNull(result);
        JsonArray tools = result.getAsJsonArray("tools");
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test
    void toolsCallDelegatesToToolHandler() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"msg\":\"hello\"}}}";
        FakeHttpExchange exchange = dispatch(body);
        assertEquals(200, exchange.getResponseCode());
        JsonObject result = parseResponse(exchange).getAsJsonObject("result");
        assertNotNull(result);
        JsonArray content = result.getAsJsonArray("content");
        assertEquals(1, content.size());
        assertEquals("hello", content.get(0).getAsJsonObject().get("text").getAsString());
    }
}
