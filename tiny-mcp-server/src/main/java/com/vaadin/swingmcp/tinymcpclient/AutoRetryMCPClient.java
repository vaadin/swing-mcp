package com.vaadin.swingmcp.tinymcpclient;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.ToolRequest;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-shot session-loss retry decorator. When the wrapped client throws
 * {@link MCPSessionLostException} (HTTP 404 from the server), this
 * decorator calls {@code inner.initialize()} and replays the failed call
 * exactly once. A second {@code MCPSessionLostException} on the replay
 * surfaces to the caller — there is no infinite retry.
 *
 * <p>{@link IOException} is <em>not</em> retried: transport failures are
 * the caller's call. Generic {@link MCPClientException} (non-404 protocol
 * errors) is also not retried — re-initializing wouldn't change the
 * outcome.
 *
 * <p>Obtained via the {@link MCPClient#autoRetry()} default method on the
 * interface so it composes with future decorators left-to-right. See
 * DR-008.
 */
public final class AutoRetryMCPClient implements MCPClient {

    private static final Logger LOG = Logger.getLogger(AutoRetryMCPClient.class.getName());

    private final MCPClient inner;

    public AutoRetryMCPClient(MCPClient inner) {
        if (inner == null) {
            throw new IllegalArgumentException("inner client must not be null");
        }
        this.inner = inner;
    }

    @Override
    public MCPProtocol.InitializeResult initialize() throws IOException {
        return inner.initialize();
    }

    @Override
    public List<MCPProtocol.Tool> listTools() throws IOException {
        try {
            return inner.listTools();
        } catch (MCPSessionLostException e) {
            recover(e);
            return inner.listTools();
        }
    }

    @Override
    public MCPProtocol.CallToolResult callTool(ToolRequest request) throws IOException {
        try {
            return inner.callTool(request);
        } catch (MCPSessionLostException e) {
            recover(e);
            return inner.callTool(request);
        }
    }

    @Override
    public void close() throws IOException {
        inner.close();
    }

    /**
     * Re-initializes the inner client. Called once per intercepted
     * {@link MCPSessionLostException}. We call {@code inner.initialize()}
     * directly (not {@code this.initialize()}) so wrapping order with
     * future decorators stays predictable.
     */
    private void recover(MCPSessionLostException e) throws IOException {
        LOG.log(Level.FINE, "Session lost, re-initializing", e);
        inner.initialize();
    }
}
