package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The same conformance suite driven by the official MCP SDK — the authority
 * leg. A disagreement between this class and {@code TinyClientToolConformanceTest}
 * is either a bug in this server or a bug in this project's own client, and
 * neither would show up if the suite only ever ran against itself.
 *
 * <p>Java 17+ only: the SDK publishes no Java 11 build, which is why this lives
 * in {@code src/testOfficial} rather than beside the suite it runs.
 */
class OfficialClientToolConformanceTest extends AbstractToolConformanceTest {

    @Override
    protected MCPClient newClient(String url) {
        return new OfficialMCPClient(url);
    }

    @Override
    protected RpcError rpcErrorOf(Executable call) {
        // The SDK wraps the protocol error in whatever the transport threw.
        final Exception thrown = assertThrows(Exception.class, call);
        final McpError error = assertInstanceOf(McpError.class, McpError.findRootCause(thrown));
        return new RpcError(error.getJsonRpcError().code(), error.getJsonRpcError().message());
    }
}
