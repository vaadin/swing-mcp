package com.vaadin.swingmcp.proxy;

import com.vaadin.swingmcp.tinymcpserver.HttpMCPServer;
import com.vaadin.swingmcp.tinymcpserver.MCPHandler;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPProxy;
import com.vaadin.swingmcp.tinymcpserver.ProxyMessages;
import com.vaadin.swingmcp.tinymcpserver.StdioMCPServer;
import com.vaadin.swingmcp.tools.SwingTools;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for {@code swing-mcp-proxy}: a stdio MCP server that
 * Claude Code (or any MCP client) launches as a subprocess and that
 * forwards every {@code tools/call} to a separately-running in-process
 * {@code SwingMCP} reachable over loopback HTTP.
 *
 * <p>From the LLM's perspective this process <em>is</em> Swing MCP.
 * Server identity ({@code SERVER_NAME} / {@code SERVER_VERSION} /
 * {@code INSTRUCTIONS}), the tool manifest, and the session-lost
 * message are all sourced from {@code swing-mcp-tool-defs}, so what
 * Claude sees here is bit-identical to what it would see talking
 * directly to {@code SwingMCP} over HTTP.
 *
 */
public final class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    /** System property whose value (if set) overrides the upstream port. */
    public static final String PORT_SYSTEM_PROPERTY = "swing.mcp.port";

    /** Environment variable whose value (if set) overrides the upstream port. */
    public static final String PORT_ENV_VAR = "SWING_MCP_PORT";

    /** Loopback host the upstream {@code SwingMCP} binds to; it listens nowhere else. */
    public static final String UPSTREAM_HOST = "127.0.0.1";

    /** Context path the upstream {@code SwingMCP} serves on. Hardcoded. */
    public static final String UPSTREAM_CONTEXT_PATH = "/mcp";

    private Main() {}

    public static void main(String[] args) {
        int port = resolvePort(System.getProperty(PORT_SYSTEM_PROPERTY),
                System.getenv(PORT_ENV_VAR));
        URI upstreamUrl = URI.create("http://" + UPSTREAM_HOST + ":" + port + UPSTREAM_CONTEXT_PATH);

        ProxyMessages messages = buildProxyMessages(upstreamUrl);

        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName(SwingTools.SERVER_NAME);
        serverInfo.setVersion(SwingTools.SERVER_VERSION);
        MCPHandler handler = MCPProxy.newHandler(
                serverInfo,
                SwingTools.INSTRUCTIONS,
                SwingTools.ALL,
                upstreamUrl,
                messages);
        // Single-session is already configured by MCPProxy.newHandler.
        StdioMCPServer stdio = new StdioMCPServer(handler);

        // SIGTERM/kill: close the upstream client cascading through
        // onSessionClosed. Idempotent against the EOF path, which already
        // calls closeAllSessions inside StdioMCPServer.runStdio's finally.
        Runtime.getRuntime().addShutdownHook(new Thread(
                handler::closeAllSessions, "swing-mcp-proxy-shutdown"));

        LOG.info("swing-mcp-proxy starting; upstream URL = " + upstreamUrl);
        stdio.runStdio(System.in, System.out);
        LOG.info("swing-mcp-proxy stdin closed; exiting cleanly");
    }

    /**
     * Picks the upstream port from system property, env var, or the
     * default (in that precedence).
     *
     * @param systemPropValue raw value of {@link #PORT_SYSTEM_PROPERTY}, or {@code null}
     * @param envValue        raw value of {@link #PORT_ENV_VAR}, or {@code null}
     * @return the resolved port
     * @throws NumberFormatException if either provided value is non-numeric
     *         or out of the {@code [0, 65535]} range
     */
    public static int resolvePort(@Nullable String systemPropValue, @Nullable String envValue) {
        String raw;
        if (systemPropValue != null && !systemPropValue.isBlank()) {
            raw = systemPropValue;
        } else if (envValue != null && !envValue.isBlank()) {
            raw = envValue;
        } else {
            return HttpMCPServer.DEFAULT_PORT;
        }
        int p;
        try {
            p = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // Surface the source of the bad value so the operator can
            // identify which channel to fix.
            throw new NumberFormatException(
                    "Invalid swing-mcp-proxy port (" + raw + "): set "
                            + PORT_SYSTEM_PROPERTY + " (system property) or "
                            + PORT_ENV_VAR + " (environment variable) to a number in [0, 65535]");
        }
        if (p < 0 || p > 65535) {
            throw new NumberFormatException(
                    "Invalid swing-mcp-proxy port (" + p + "): out of range [0, 65535]");
        }
        return p;
    }

    /**
     * Builds the {@link ProxyMessages} record with the locked wordings
     * (grilling Sub-item 1) and the upstream URL interpolated where it
     * helps the operator distinguish "is the agent loaded?" from "is the
     * app running on a different port?".
     *
     * <p>Visible for testing.
     */
    static ProxyMessages buildProxyMessages(URI upstreamUrl) {
        String url = upstreamUrl.toString();
        return new ProxyMessages(
                "Cannot reach Swing MCP Agent at " + url
                        + " — ask the user to start the Swing application (the MCP agent runs inside it).",
                "Swing-MCP is out of sync with the Swing MCP Agent at " + url
                        + " — the tool manifest doesn't match. Tell the user to restart the MCP server"
                        + " or the Swing application so versions match. Do not retry.",
                SwingTools.SESSION_LOST_MESSAGE,
                "Lost connection to Swing MCP Agent at " + url
                        + " mid-call — the action may or may not have completed; call swing_snapshot to verify.");
    }

    /**
     * Visible for testing — the entry point that fully wires the stack
     * but takes streams instead of using {@code System.in}/{@code System.out}.
     * Lets a test drive the proxy via piped streams.
     */
    static void runWithStreams(URI upstreamUrl, java.io.InputStream in, PrintStream out) {
        ProxyMessages messages = buildProxyMessages(upstreamUrl);
        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName(SwingTools.SERVER_NAME);
        serverInfo.setVersion(SwingTools.SERVER_VERSION);
        MCPHandler handler = MCPProxy.newHandler(
                serverInfo, SwingTools.INSTRUCTIONS,
                SwingTools.ALL, upstreamUrl, messages);
        StdioMCPServer stdio = new StdioMCPServer(handler);
        try {
            stdio.runStdio(in, out);
        } catch (RuntimeException | Error e) {
            LOG.log(Level.WARNING, "stdio loop failed", e);
            throw e;
        }
    }
}
