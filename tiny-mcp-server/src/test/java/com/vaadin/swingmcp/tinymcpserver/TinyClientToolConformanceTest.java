package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import com.vaadin.swingmcp.tinymcpclient.MCPClientException;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import org.junit.jupiter.api.function.Executable;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The conformance suite driven by this project's own client — the leg that
 * also runs on Java 11, since {@link TinyMCPClient} has no dependency the
 * official SDK's Java 17 floor would drag in.
 */
class TinyClientToolConformanceTest extends AbstractToolConformanceTest {

    @Override
    protected MCPClient newClient(String url) {
        return new TinyMCPClient(URI.create(url));
    }

    @Override
    protected RpcError rpcErrorOf(Executable call) {
        final MCPClientException e = assertThrows(MCPClientException.class, call);
        return new RpcError(e.getCode(), e.getMessage());
    }
}
