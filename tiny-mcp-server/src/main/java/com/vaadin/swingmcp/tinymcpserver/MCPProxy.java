package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.ToolDescriptor;
import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import com.vaadin.swingmcp.tinymcpclient.MCPClientException;
import com.vaadin.swingmcp.tinymcpclient.MCPSessionLostException;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic forwarding-proxy factory (DR-012). Builds an {@link MCPHandler}
 * whose tool functions forward {@code tools/call} requests to an upstream
 * MCP server reachable over HTTP, while {@code tools/list} answers locally
 * from a static descriptor manifest.
 *
 * <p>Typical usage:
 * <pre>{@code
 *     MCPHandler handler = MCPProxy.newHandler(
 *             SwingTools.ALL,
 *             URI.create("http://127.0.0.1:18088/mcp"),
 *             new ProxyMessages(...));
 *     try (StdioMCPServer stdio = new StdioMCPServer(handler)) {
 *         stdio.runStdio(System.in, System.out);
 *     }
 * }</pre>
 *
 * <p>Per-session state — the lazily-initialized upstream client and the
 * cached drift result — lives on {@link MCPSession#setAttribute} keyed
 * by {@link #STATE_KEY}. There is no {@code MCPProxy} instance to hold;
 * the factory pattern keeps the wiring rule visible at the call site
 * (handler returned by static method, wrapped in transport, run).
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@code onSessionStarted}: allocates {@code TinyMCPClient(upstreamUrl)}
 *       and a fresh {@link ProxySessionState}; no upstream traffic yet.</li>
 *   <li>First {@code tools/call}: lazy {@code initialize}, then a
 *       symmetric drift probe ({@code listTools()} compared against the
 *       supplied {@link ToolDescriptor} list as a set keyed by name,
 *       structural equality on each entry per DR-014). Mismatch → cache
 *       {@code messages.driftMessage()} as a permanent error for this
 *       session. {@code IOException} → return
 *       {@code messages.upstreamDownMessage()} but <em>do not</em> mark
 *       the session permanently dead — the next call retries init.</li>
 *   <li>Subsequent calls: forward
 *       {@code callTool(name, args, _meta)}.
 *       {@link MCPSessionLostException} → return
 *       {@code messages.sessionLostMessage()} and reset
 *       {@code initialized = false}; {@link IOException} →
 *       {@code messages.ioMidCallMessage()} and reset; any other
 *       {@link MCPClientException} → forward upstream's error message
 *       verbatim; {@code CallToolResult.isError == true} → forward as
 *       {@code isError} with the same text content.</li>
 *   <li>{@code onSessionClosed}: best-effort {@code upstream.close()}.</li>
 * </ul>
 *
 * <p>Single-session by default ({@code count -> count == 0}) — the
 * intended consumer is a stdio process spawned by an MCP client like
 * Claude Code. Callers that need a different policy can override it
 * via {@link MCPHandler#setAcceptNewSession} <em>before</em> the first
 * session is accepted (the factory has not yet been wired into a
 * transport at that point — the listener-lockdown rule from DR-013
 * lets caller customize then).
 */
public final class MCPProxy {

    private static final Logger LOG = Logger.getLogger(MCPProxy.class.getName());

    /**
     * Session-attribute key under which the per-session state is
     * stashed. Public so tests can poke at the internals.
     */
    public static final String STATE_KEY = "com.vaadin.swingmcp.tinymcpserver.MCPProxy.state";

    private MCPProxy() {}

    /**
     * Builds an {@link MCPHandler} that forwards {@code tools/call} to
     * an upstream MCP server while answering {@code tools/list} from
     * the supplied manifest.
     *
     * @param tools       the static manifest used to answer
     *                    {@code tools/list} and to drift-check upstream's
     *                    response. Must be non-empty and contain unique
     *                    tool names.
     * @param upstreamUrl URL of the upstream MCP HTTP server (typically
     *                    {@code http://127.0.0.1:<port>/mcp})
     * @param messages    the four pre-formatted error strings (DR-012)
     * @return a fully-wired single-session {@code MCPHandler}; the
     *         caller wraps it in a transport (typically
     *         {@link StdioMCPServer}) and runs it
     */
    public static MCPHandler newHandler(
            List<ToolDescriptor> tools,
            URI upstreamUrl,
            ProxyMessages messages) {
        return newHandler(null, null, tools, upstreamUrl, messages);
    }

    /**
     * Variant of {@link #newHandler(List, URI, ProxyMessages)} that also
     * sets the {@code initialize.serverInfo} and {@code initialize.instructions}
     * advertised by the proxy. Used by {@code swing-mcp-proxy.Main} so the
     * proxy presents bit-identical identity to whatever the in-process
     * server would present (sourced from the shared
     * {@code swing-mcp-tool-defs} module).
     *
     * @param serverInfo   server identity, or {@code null} for the default
     *                     empty {@link MCPProtocol.Implementation}
     * @param instructions {@code initialize.instructions} block, or {@code null}
     */
    public static MCPHandler newHandler(
            MCPProtocol.Implementation serverInfo,
            String instructions,
            List<ToolDescriptor> tools,
            URI upstreamUrl,
            ProxyMessages messages) {
        Objects.requireNonNull(tools, "tools");
        Objects.requireNonNull(upstreamUrl, "upstreamUrl");
        Objects.requireNonNull(messages, "messages");
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("tools list must not be empty");
        }
        List<ToolDescriptor> manifest = List.copyOf(tools);

        MCPHandler handler = new MCPHandler(serverInfo, instructions);
        handler.setAcceptNewSession(count -> count == 0);
        handler.setOnSessionStarted(session -> {
            ProxySessionState state = new ProxySessionState(new TinyMCPClient(upstreamUrl));
            session.setAttribute(STATE_KEY, state);
        });
        handler.setOnSessionClosed(session -> {
            ProxySessionState state = (ProxySessionState) session.getAttribute(STATE_KEY);
            if (state == null) return;
            try {
                state.upstream.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING,
                        "Upstream client close failed for session " + session.getId(), e);
            }
        });

        ForwardingToolFunction forwarder = new ForwardingToolFunction(manifest, messages);
        for (ToolDescriptor descriptor : manifest) {
            handler.addTool(descriptor, forwarder);
        }
        return handler;
    }

    /** Per-session state stashed on {@link MCPSession#setAttribute}. */
    static final class ProxySessionState {
        final MCPClient upstream;
        boolean initialized = false;
        /** Cached drift error for this session, or {@code null} if not yet drifted. */
        String driftFailure = null;

        ProxySessionState(MCPClient upstream) {
            this.upstream = upstream;
        }
    }

    /**
     * Single forwarding implementation registered for every manifest
     * tool. Reads the session's state on every invocation and
     * dispatches by the request name.
     */
    private static final class ForwardingToolFunction implements ToolFunction {
        private final List<ToolDescriptor> manifest;
        private final ProxyMessages messages;

        ForwardingToolFunction(List<ToolDescriptor> manifest, ProxyMessages messages) {
            this.manifest = manifest;
            this.messages = messages;
        }

        @Override
        public MCPProtocol.Content call(ToolRequest request) throws Exception {
            MCPSession session = MCPSession.getCurrent();
            ProxySessionState state = (ProxySessionState) session.getAttribute(STATE_KEY);
            if (state == null) {
                // Should never happen — onSessionStarted always populates
                // the state before any tools/call can run.
                throw new IllegalStateException("Proxy session state missing for " + session.getId());
            }

            // Cached drift failure: permanent for this session.
            if (state.driftFailure != null) {
                throw new MCPErrorResponseException(state.driftFailure);
            }

            // Lazy init + drift probe.
            if (!state.initialized) {
                try {
                    state.upstream.initialize();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Upstream initialize failed", e);
                    throw new MCPErrorResponseException(messages.upstreamDownMessage());
                }
                String driftDetail;
                try {
                    driftDetail = computeDrift(state.upstream.listTools());
                } catch (IOException e) {
                    // listTools failed mid-init — same diagnostic as init failure.
                    LOG.log(Level.WARNING, "Upstream listTools failed during drift probe", e);
                    throw new MCPErrorResponseException(messages.upstreamDownMessage());
                }
                if (driftDetail != null) {
                    state.driftFailure = messages.driftMessage();
                    LOG.warning("Manifest drift detected: " + driftDetail);
                    throw new MCPErrorResponseException(state.driftFailure);
                }
                state.initialized = true;
            }

            // Forward.
            MCPProtocol.CallToolResult result;
            try {
                result = state.upstream.callTool(
                        request.name(),
                        request.arguments(),
                        request.jsonRpcMeta());
            } catch (MCPSessionLostException e) {
                state.initialized = false;
                throw new MCPErrorResponseException(messages.sessionLostMessage());
            } catch (MCPClientException e) {
                // Forward upstream's JSON-RPC error message verbatim.
                throw new MCPErrorResponseException(
                        e.getMessage() != null ? e.getMessage() : "(no message)");
            } catch (IOException e) {
                state.initialized = false;
                LOG.log(Level.WARNING, "I/O failure forwarding " + request.name(), e);
                throw new MCPErrorResponseException(messages.ioMidCallMessage());
            }

            // isError on a successful HTTP response: forward as a tool-layer error.
            if (Boolean.TRUE.equals(result.getIsError())) {
                throw new MCPErrorResponseException(extractErrorText(result.getContent()));
            }

            // Success: forward content. ToolFunction's contract is single
            // Content — multi-content upstream results are a future
            // concern (no current Swing tool returns multi-content).
            List<MCPProtocol.Content> content = result.getContent();
            if (content == null || content.isEmpty()) {
                return null;
            }
            if (content.size() > 1) {
                LOG.warning("Upstream " + request.name()
                        + " returned " + content.size()
                        + " content items; only the first is forwarded by this proxy");
            }
            return content.get(0);
        }

        /**
         * Compares the static manifest against upstream's {@code listTools()}
         * response. Returns {@code null} if the two agree, or a short
         * human-readable description of the first detected divergence
         * otherwise. Symmetric and order-insensitive (compared as sets
         * keyed by tool name).
         */
        private String computeDrift(List<MCPProtocol.Tool> upstreamTools) {
            Map<String, ToolDescriptor> manifestByName = new HashMap<>(manifest.size() * 2);
            for (ToolDescriptor d : manifest) {
                manifestByName.put(d.name(), d);
            }
            Map<String, ToolDescriptor> upstreamByName = new HashMap<>(upstreamTools.size() * 2);
            for (MCPProtocol.Tool t : upstreamTools) {
                upstreamByName.put(t.getName(), toDescriptor(t));
            }

            // Set-difference checks.
            Set<String> manifestOnly = new HashSet<>(manifestByName.keySet());
            manifestOnly.removeAll(upstreamByName.keySet());
            if (!manifestOnly.isEmpty()) {
                String name = manifestOnly.iterator().next();
                return "tool '" + name + "' is in the manifest but not advertised by upstream";
            }
            Set<String> upstreamOnly = new HashSet<>(upstreamByName.keySet());
            upstreamOnly.removeAll(manifestByName.keySet());
            if (!upstreamOnly.isEmpty()) {
                String name = upstreamOnly.iterator().next();
                return "tool '" + name + "' is advertised by upstream but not in the manifest";
            }

            // Field-level checks for shared names.
            for (Map.Entry<String, ToolDescriptor> entry : manifestByName.entrySet()) {
                ToolDescriptor m = entry.getValue();
                ToolDescriptor u = upstreamByName.get(entry.getKey());
                if (!m.equals(u)) {
                    return diffSummary(entry.getKey(), m, u);
                }
            }
            return null;
        }

        private static String diffSummary(String name, ToolDescriptor manifest, ToolDescriptor upstream) {
            String field;
            if (!Objects.equals(manifest.description(), upstream.description())) {
                field = "description";
            } else if (!Objects.equals(manifest.inputSchema(), upstream.inputSchema())) {
                field = "input schema";
            } else {
                field = "name";
            }
            return "tool '" + name + "' differs in " + field
                    + " (manifest=" + manifest + " upstream=" + upstream + ")";
        }

        private static ToolDescriptor toDescriptor(MCPProtocol.Tool t) {
            String name = t.getName() != null ? t.getName() : "";
            String description = t.getDescription() != null ? t.getDescription() : "";
            MCPProtocol.InputSchema schema = t.getInputSchema() != null
                    ? t.getInputSchema()
                    : new MCPProtocol.InputSchema();
            return new ToolDescriptor(name, description, schema);
        }

        private static String extractErrorText(List<MCPProtocol.Content> content) {
            if (content == null || content.isEmpty()) {
                return "(upstream returned an error with no content)";
            }
            // Concatenate text content; ignore non-text (rare in practice).
            StringBuilder out = new StringBuilder();
            for (MCPProtocol.Content c : content) {
                if (c.getText() != null) {
                    if (out.length() > 0) out.append('\n');
                    out.append(c.getText());
                }
            }
            return out.length() > 0 ? out.toString() : "(upstream returned a non-text error)";
        }
    }
}
