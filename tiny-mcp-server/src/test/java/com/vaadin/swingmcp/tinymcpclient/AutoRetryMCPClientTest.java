package com.vaadin.swingmcp.tinymcpclient;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import com.vaadin.swingmcp.tinymcpserver.ToolRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the one-shot session-loss retry contract of {@link AutoRetryMCPClient}
 * (D_no_auto_retry): {@link MCPSessionLostException} triggers exactly one
 * {@code initialize()} + replay; everything else passes straight through.
 *
 * <p>Driven through a scripted {@link FakeClient} rather than a real server —
 * the decorator's whole job is which exception it catches, and an in-memory
 * fake is the only way to hand it a second failure on the replay.
 */
class AutoRetryMCPClientTest {

    private static final ToolRequest ECHO = new ToolRequest(
            "echo", Map.of("text", "hi"), Collections.emptyMap(), null);

    // ===== Construction =====

    @Test
    void constructorRejectsNullInner() {
        assertThrows(NullPointerException.class, () -> new AutoRetryMCPClient(null));
    }

    @Test
    void autoRetryDefaultMethodWraps() {
        FakeClient inner = new FakeClient();
        assertInstanceOf(AutoRetryMCPClient.class, inner.autoRetry());
    }

    // ===== initialize / close are plain delegation =====

    @Test
    void initializeDelegatesAndIsNotRetried() {
        // A 404 on initialize means something other than a lost session
        // (initialize carries no session id) — re-initializing cannot help.
        FakeClient inner = new FakeClient();
        inner.initializeFailures.add(new MCPSessionLostException("no session"));
        MCPClient client = inner.autoRetry();

        assertThrows(MCPSessionLostException.class, client::initialize);
        assertEquals(List.of("initialize"), inner.calls);
    }

    @Test
    void initializeReturnsInnerResult() throws IOException {
        FakeClient inner = new FakeClient();
        assertSame(inner.initializeResult, inner.autoRetry().initialize());
    }

    @Test
    void closeDelegates() throws IOException {
        FakeClient inner = new FakeClient();
        inner.autoRetry().close();
        assertEquals(List.of("close"), inner.calls);
        assertTrue(inner.closed);
    }

    // ===== listTools =====

    @Test
    void listToolsRecoversFromOneSessionLoss() throws IOException {
        FakeClient inner = new FakeClient();
        inner.listToolsFailures.add(new MCPSessionLostException("session gone"));
        MCPClient client = inner.autoRetry();

        assertSame(inner.tools, client.listTools());
        assertEquals(List.of("listTools", "initialize", "listTools"), inner.calls);
    }

    @Test
    void listToolsSurfacesASecondSessionLoss() {
        FakeClient inner = new FakeClient();
        inner.listToolsFailures.add(new MCPSessionLostException("first"));
        inner.listToolsFailures.add(new MCPSessionLostException("second"));
        MCPClient client = inner.autoRetry();

        MCPSessionLostException ex =
                assertThrows(MCPSessionLostException.class, client::listTools);
        assertEquals("second", ex.getMessage(), "the replay's failure must surface, not the first");
        assertEquals(List.of("listTools", "initialize", "listTools"), inner.calls,
                "there must be no second recovery attempt");
    }

    @Test
    void listToolsDoesNotRetryTransportFailures() {
        FakeClient inner = new FakeClient();
        inner.listToolsFailures.add(new IOException("connection refused"));
        MCPClient client = inner.autoRetry();

        assertThrows(IOException.class, client::listTools);
        assertEquals(List.of("listTools"), inner.calls);
    }

    @Test
    void listToolsDoesNotRetryGenericProtocolErrors() {
        FakeClient inner = new FakeClient();
        inner.listToolsFailures.add(
                new MCPClientException(MCPServerException.INVALID_PARAMS, "bad params"));
        MCPClient client = inner.autoRetry();

        assertThrows(MCPClientException.class, client::listTools);
        assertEquals(List.of("listTools"), inner.calls);
    }

    // ===== callTool =====

    @Test
    void callToolRecoversFromOneSessionLoss() throws IOException {
        FakeClient inner = new FakeClient();
        inner.callToolFailures.add(new MCPSessionLostException("session gone"));
        MCPClient client = inner.autoRetry();

        assertSame(inner.callResult, client.callTool(ECHO));
        assertEquals(List.of("callTool:echo", "initialize", "callTool:echo"), inner.calls);
    }

    @Test
    void callToolSurfacesASecondSessionLoss() {
        FakeClient inner = new FakeClient();
        inner.callToolFailures.add(new MCPSessionLostException("first"));
        inner.callToolFailures.add(new MCPSessionLostException("second"));
        MCPClient client = inner.autoRetry();

        MCPSessionLostException ex =
                assertThrows(MCPSessionLostException.class, () -> client.callTool(ECHO));
        assertEquals("second", ex.getMessage());
        assertEquals(List.of("callTool:echo", "initialize", "callTool:echo"), inner.calls);
    }

    @Test
    void callToolDoesNotRetryTransportFailures() {
        FakeClient inner = new FakeClient();
        inner.callToolFailures.add(new IOException("broken pipe"));
        MCPClient client = inner.autoRetry();

        assertThrows(IOException.class, () -> client.callTool(ECHO));
        assertEquals(List.of("callTool:echo"), inner.calls);
    }

    @Test
    void recoveryFailureSurfacesInsteadOfTheOriginal() {
        // If re-initializing is itself impossible, the caller needs to hear
        // about that — not the stale session-lost message.
        FakeClient inner = new FakeClient();
        inner.callToolFailures.add(new MCPSessionLostException("session gone"));
        inner.initializeFailures.add(new IOException("upstream is down"));
        MCPClient client = inner.autoRetry();

        IOException ex = assertThrows(IOException.class, () -> client.callTool(ECHO));
        assertEquals("upstream is down", ex.getMessage());
        assertEquals(List.of("callTool:echo", "initialize"), inner.calls);
    }

    @Test
    void retryReplaysTheSameRequest() throws IOException {
        FakeClient inner = new FakeClient();
        inner.callToolFailures.add(new MCPSessionLostException("session gone"));

        inner.autoRetry().callTool(ECHO);

        assertEquals(List.of(ECHO, ECHO), inner.forwarded,
                "the replay must carry the original arguments, not a rebuilt request");
    }

    /**
     * An {@link MCPClient} that records every call and throws a scripted
     * failure per invocation. A method with an empty failure queue succeeds.
     */
    private static final class FakeClient implements MCPClient {

        final List<String> calls = new ArrayList<>();
        final List<ToolRequest> forwarded = new ArrayList<>();

        /** Popped once per matching call; {@code null} on empty means "succeed". */
        final Deque<Throwable> initializeFailures = new ArrayDeque<>();
        final Deque<Throwable> listToolsFailures = new ArrayDeque<>();
        final Deque<Throwable> callToolFailures = new ArrayDeque<>();

        final MCPProtocol.InitializeResult initializeResult = new MCPProtocol.InitializeResult();
        final List<MCPProtocol.Tool> tools = List.of();
        final MCPProtocol.CallToolResult callResult = new MCPProtocol.CallToolResult();

        boolean closed;

        @Override
        public MCPProtocol.InitializeResult initialize() throws IOException {
            calls.add("initialize");
            throwNext(initializeFailures);
            return initializeResult;
        }

        @Override
        public List<MCPProtocol.Tool> listTools() throws IOException {
            calls.add("listTools");
            throwNext(listToolsFailures);
            return tools;
        }

        @Override
        public MCPProtocol.CallToolResult callTool(ToolRequest request) throws IOException {
            calls.add("callTool:" + request.name());
            forwarded.add(request);
            throwNext(callToolFailures);
            return callResult;
        }

        @Override
        public void close() {
            calls.add("close");
            closed = true;
        }

        private static void throwNext(Deque<Throwable> queue) throws IOException {
            Throwable t = queue.poll();
            if (t == null) {
                return;
            }
            if (t instanceof IOException io) {
                throw io;
            }
            throw (RuntimeException) t;
        }
    }
}
