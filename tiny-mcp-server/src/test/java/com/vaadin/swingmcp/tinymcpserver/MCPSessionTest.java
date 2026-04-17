package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        session = new MCPSession("test-session-id", toolHandler, new MCPPromptHandler());
    }

    // ===== Helpers =====

    private FakeHttpExchange dispatch(String jsonRpcBody) {
        FakeHttpExchange exchange = new FakeHttpExchange(jsonRpcBody);
        JsonRpcExchange rpc = new JsonRpcExchange(exchange);
        MCPProtocol.JsonRpcRequest request = rpc.parsePost();
        assertNotNull(request, "parsePost should succeed for valid JSON-RPC");
        session.handlePost(rpc, request);
        return exchange;
    }

    private FakeHttpExchange dispatch(String method, int id) {
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
        return new MCPSession("attr-session", new MCPToolHandler(), new MCPPromptHandler());
    }

    @Test
    void getAttributeReturnsNullForMissingName() {
        MCPSession s = freshSession();
        s.runLocked(() -> assertNull(s.getAttribute("missing")));
    }

    @Test
    void setAttributeThenGetAttributeRoundtrips() {
        MCPSession s = freshSession();
        s.runLocked(() -> {
            s.setAttribute("key", "value");
            assertEquals("value", s.getAttribute("key"));
        });
    }

    @Test
    void setAttributeOverwritesPreviousValue() {
        MCPSession s = freshSession();
        s.runLocked(() -> {
            s.setAttribute("key", "first");
            s.setAttribute("key", "second");
            assertEquals("second", s.getAttribute("key"));
        });
    }

    @Test
    void setAttributeAllowsNullValue() {
        MCPSession s = freshSession();
        s.runLocked(() -> {
            s.setAttribute("key", "value");
            s.setAttribute("key", null);
            assertNull(s.getAttribute("key"));
        });
    }

    @Test
    void getAttributeRejectsNullName() {
        MCPSession s = freshSession();
        s.runLocked(() ->
                assertThrows(NullPointerException.class, () -> s.getAttribute((String) null)));
    }

    @Test
    void setAttributeRejectsNullName() {
        MCPSession s = freshSession();
        s.runLocked(() ->
                assertThrows(NullPointerException.class, () -> s.setAttribute((String) null, "v")));
    }

    @Test
    void attributesAreIsolatedPerSession() {
        MCPSession a = freshSession();
        MCPSession b = freshSession();
        a.runLocked(() -> a.setAttribute("key", "A-value"));
        b.runLocked(() -> assertNull(b.getAttribute("key")));
    }

    @Test
    void typedSetAttributeThenGetAttributeRoundtrips() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hello");
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class, value);
            assertSame(value, s.getAttribute(StringBuilder.class));
        });
    }

    @Test
    void typedGetAttributeReturnsNullWhenMissing() {
        MCPSession s = freshSession();
        s.runLocked(() -> assertNull(s.getAttribute(StringBuilder.class)));
    }

    @Test
    void typedAttributeKeyIsClassName() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hi");
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class, value);
            assertSame(value, s.getAttribute(StringBuilder.class.getName()));
        });
    }

    @Test
    void stringKeyedAttributeVisibleViaTypedGetWhenNameMatches() {
        MCPSession s = freshSession();
        StringBuilder value = new StringBuilder("hi");
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class.getName(), value);
            assertSame(value, s.getAttribute(StringBuilder.class));
        });
    }

    @Test
    void typedGetAttributeThrowsClassCastExceptionForWrongType() {
        MCPSession s = freshSession();
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class.getName(), "not a StringBuilder");
            assertThrows(ClassCastException.class, () -> s.getAttribute(StringBuilder.class));
        });
    }

    @Test
    void typedSetAttributeOverwritesSameTypeKey() {
        MCPSession s = freshSession();
        StringBuilder first = new StringBuilder("first");
        StringBuilder second = new StringBuilder("second");
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class, first);
            s.setAttribute(StringBuilder.class, second);
            assertSame(second, s.getAttribute(StringBuilder.class));
        });
    }

    @Test
    void typedAttributesForDifferentClassesDoNotCollide() {
        MCPSession s = freshSession();
        StringBuilder sb = new StringBuilder("sb");
        Integer i = 42;
        s.runLocked(() -> {
            s.setAttribute(StringBuilder.class, sb);
            s.setAttribute(Integer.class, i);
            assertSame(sb, s.getAttribute(StringBuilder.class));
            assertEquals(42, s.getAttribute(Integer.class));
        });
    }

    // ===== Lock enforcement =====

    @Test
    void getAttributeWithoutLockThrowsIllegalStateException() {
        MCPSession s = freshSession();
        assertThrows(IllegalStateException.class, () -> s.getAttribute("key"));
    }

    @Test
    void setAttributeWithoutLockThrowsIllegalStateException() {
        MCPSession s = freshSession();
        assertThrows(IllegalStateException.class, () -> s.setAttribute("key", "value"));
    }

    @Test
    void typedGetAttributeWithoutLockThrowsIllegalStateException() {
        MCPSession s = freshSession();
        assertThrows(IllegalStateException.class, () -> s.getAttribute(StringBuilder.class));
    }

    @Test
    void typedSetAttributeWithoutLockThrowsIllegalStateException() {
        MCPSession s = freshSession();
        assertThrows(IllegalStateException.class, () -> s.setAttribute(StringBuilder.class, new StringBuilder()));
    }

    @Test
    void runLockedReleasesLockAfterBlock() {
        MCPSession s = freshSession();
        s.runLocked(() -> s.setAttribute("key", "value"));
        // After runLocked returns, the session lock is released again — calls
        // from unlocked threads must still fail.
        assertThrows(IllegalStateException.class, () -> s.getAttribute("key"));
    }

    @Test
    void runLockedReleasesLockAfterBlockEvenOnFailure() {
        MCPSession s = freshSession();
        RuntimeException boom = new RuntimeException("boom");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> s.runLocked(() -> { throw boom; }));
        assertSame(boom, thrown);
        // Lock was released — a subsequent runLocked on the same thread works.
        s.runLocked(() -> s.setAttribute("key", "value"));
    }

    // ===== getCurrent() / ThreadLocal binding =====

    @Test
    void getCurrentThrowsWhenNoSessionBound() {
        NullPointerException ex = assertThrows(NullPointerException.class, MCPSession::getCurrent);
        assertEquals("Not running in a MCP session", ex.getMessage());
    }

    @Test
    void getCurrentReturnsBoundSessionInsideRunLocked() {
        MCPSession s = freshSession();
        s.runLocked(() -> assertSame(s, MCPSession.getCurrent()));
    }

    @Test
    void getCurrentThrowsAfterRunLockedReturns() {
        MCPSession s = freshSession();
        s.runLocked(() -> assertSame(s, MCPSession.getCurrent()));
        assertThrows(NullPointerException.class, MCPSession::getCurrent);
    }

    @Test
    void getCurrentIsThreadLocal() throws Exception {
        MCPSession main = freshSession();
        MCPSession[] seenOnOther = new MCPSession[1];
        boolean[] otherSawNpeFirst = new boolean[1];
        Throwable[] err = new Throwable[1];
        main.runLocked(() -> {
            Thread t = new Thread(() -> {
                try {
                    try {
                        MCPSession.getCurrent();
                        err[0] = new AssertionError("expected NPE on unbound thread");
                        return;
                    } catch (NullPointerException expected) {
                        otherSawNpeFirst[0] = true;
                    }
                    MCPSession other = new MCPSession("other", new MCPToolHandler(), new MCPPromptHandler());
                    other.runLocked(() -> seenOnOther[0] = MCPSession.getCurrent());
                } catch (Throwable t2) {
                    err[0] = t2;
                }
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // main thread's binding is untouched by the other thread
            assertSame(main, MCPSession.getCurrent());
        });
        if (err[0] != null) throw new AssertionError(err[0]);
        assertTrue(otherSawNpeFirst[0], "other thread should have seen NPE before binding its own session");
        assertNotNull(seenOnOther[0]);
        assertEquals("other", seenOnOther[0].getId());
    }
}
