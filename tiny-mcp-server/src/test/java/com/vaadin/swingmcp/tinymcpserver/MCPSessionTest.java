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

    // ===== Attributes =====

    private static MCPSession freshSession() {
        return new MCPSession("attr-session", new MCPToolHandler());
    }

    @Test
    void getAttributeReturnsNullForMissingName() {
        assertNull(freshSession().getAttribute("missing"));
    }

    @Test
    void setAttributeThenGetAttributeRoundtrips() {
        MCPSession s = freshSession();
        s.setAttribute("key", "value");
        assertEquals("value", s.getAttribute("key"));
    }

    @Test
    void setAttributeOverwritesPreviousValue() {
        MCPSession s = freshSession();
        s.setAttribute("key", "first");
        s.setAttribute("key", "second");
        assertEquals("second", s.getAttribute("key"));
    }

    @Test
    void setAttributeAllowsNullValue() {
        MCPSession s = freshSession();
        s.setAttribute("key", "value");
        s.setAttribute("key", null);
        assertNull(s.getAttribute("key"));
    }

    @Test
    void getAttributeRejectsNullName() {
        assertThrows(NullPointerException.class, () -> freshSession().getAttribute((String) null));
    }

    @Test
    void setAttributeRejectsNullName() {
        assertThrows(NullPointerException.class, () -> freshSession().setAttribute((String) null, "v"));
    }

    @Test
    void attributesAreIsolatedPerSession() {
        MCPSession a = freshSession();
        MCPSession b = freshSession();
        a.setAttribute("key", "A-value");
        assertNull(b.getAttribute("key"));
    }

    @Test
    void typedSetAttributeThenGetAttributeRoundtrips() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hello");
        s.setAttribute(StringBuilder.class, value);
        StringBuilder retrieved = s.getAttribute(StringBuilder.class);
        assertSame(value, retrieved);
    }

    @Test
    void typedGetAttributeReturnsNullWhenMissing() {
        assertNull(freshSession().getAttribute(StringBuilder.class));
    }

    @Test
    void typedAttributeKeyIsClassName() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hi");
        s.setAttribute(StringBuilder.class, value);
        assertSame(value, s.getAttribute(StringBuilder.class.getName()));
    }

    @Test
    void stringKeyedAttributeVisibleViaTypedGetWhenNameMatches() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hi");
        s.setAttribute(StringBuilder.class.getName(), value);
        assertSame(value, s.getAttribute(StringBuilder.class));
    }

    @Test
    void typedGetAttributeThrowsClassCastExceptionForWrongType() {
        MCPSession s = freshSession();
        s.setAttribute(StringBuilder.class.getName(), "not a StringBuilder");
        assertThrows(ClassCastException.class, () -> s.getAttribute(StringBuilder.class));
    }

    @Test
    void typedSetAttributeOverwritesSameTypeKey() {
        MCPSession s = freshSession();
        StringBuilder first = new StringBuilder("first");
        StringBuilder second = new StringBuilder("second");
        s.setAttribute(StringBuilder.class, first);
        s.setAttribute(StringBuilder.class, second);
        assertSame(second, s.getAttribute(StringBuilder.class));
    }

    @Test
    void typedAttributesForDifferentClassesDoNotCollide() {
        MCPSession s = freshSession();
        StringBuilder sb = new StringBuilder("sb");
        Integer i = 42;
        s.setAttribute(StringBuilder.class, sb);
        s.setAttribute(Integer.class, i);
        assertSame(sb, s.getAttribute(StringBuilder.class));
        assertEquals(42, s.getAttribute(Integer.class));
    }

    // ===== getCurrent() / ThreadLocal binding =====

    @Test
    void getCurrentThrowsWhenNoSessionBound() {
        MCPSession.instance.remove();
        NullPointerException ex = assertThrows(NullPointerException.class, MCPSession::getCurrent);
        assertEquals("Not running in a MCP session", ex.getMessage());
    }

    @Test
    void getCurrentReturnsBoundSession() {
        MCPSession s = freshSession();
        MCPSession.instance.set(s);
        try {
            assertSame(s, MCPSession.getCurrent());
        } finally {
            MCPSession.instance.remove();
        }
    }

    @Test
    void getCurrentThrowsAfterInstanceRemoved() {
        MCPSession.instance.set(freshSession());
        MCPSession.instance.remove();
        assertThrows(NullPointerException.class, MCPSession::getCurrent);
    }

    @Test
    void getCurrentIsThreadLocal() throws Exception {
        MCPSession main = freshSession();
        MCPSession.instance.set(main);
        try {
            MCPSession[] seen = new MCPSession[1];
            Throwable[] err = new Throwable[1];
            Thread t = new Thread(() -> {
                try {
                    MCPSession.getCurrent();
                } catch (NullPointerException expected) {
                    // other thread has no binding — then bind its own and verify isolation
                    MCPSession other = new MCPSession("other", new MCPToolHandler());
                    MCPSession.instance.set(other);
                    try {
                        seen[0] = MCPSession.getCurrent();
                    } finally {
                        MCPSession.instance.remove();
                    }
                    return;
                } catch (Throwable t2) {
                    err[0] = t2;
                    return;
                }
                err[0] = new AssertionError("expected NPE on unbound thread");
            });
            t.start();
            t.join();
            if (err[0] != null) throw new AssertionError(err[0]);
            assertNotNull(seen[0]);
            assertEquals("other", seen[0].getId());
            // main thread's binding is untouched
            assertSame(main, MCPSession.getCurrent());
        } finally {
            MCPSession.instance.remove();
        }
    }
}
