package com.vaadin.swingmcp.tinymcpserver;

import java.util.Objects;

/**
 * Pre-formatted error strings emitted by {@link MCPProxy} to the LLM
 * when something goes wrong. Each field is a complete sentence that
 * the proxy returns verbatim as the {@code text} content of an
 * {@code isError: true} {@code CallToolResult}.
 *
 * <p>{@code tiny-mcp-server} does <em>no</em> templating — any URL,
 * remote-name, or tool-name interpolation must already be baked into
 * these strings by the caller. (The caller is the proxy module that
 * knows its target's identity; tiny-mcp-server is generic.)
 *
 * <p>Each constructor parameter's javadoc carries an example string showing
 * the voice and concreteness expected of a good message: a clear cause,
 * a named remediation, and "do not retry" where appropriate.
 *
 * <p>Immutable.
 */
public final class ProxyMessages {

    private final String upstreamDownMessage;
    private final String driftMessage;
    private final String sessionLostMessage;
    private final String ioMidCallMessage;

    /**
     * @param upstreamDownMessage  emitted on the lazy-init {@code IOException}
     *                             path. Example: {@code "Cannot reach the MCP
     *                             agent at http://127.0.0.1:18088/mcp — ask
     *                             the user to start the host application (the
     *                             MCP agent runs inside it)."}
     * @param driftMessage         emitted when the manifest does not match
     *                             upstream's {@code listTools()} response.
     *                             Permanent for the session — caller should
     *                             tell the LLM "do not retry." Example:
     *                             {@code "This proxy is out of sync with the
     *                             MCP agent at &lt;URL&gt; — the tool manifest
     *                             doesn't match. Tell the user to restart the
     *                             proxy or the host application so versions
     *                             match. Do not retry."}
     * @param sessionLostMessage   emitted on {@code MCPSessionLostException}
     *                             from upstream. Example: {@code "The host
     *                             application session was lost — take a fresh
     *                             snapshot to re-orient and retry."}
     * @param ioMidCallMessage     emitted on {@code IOException} during a
     *                             forwarded call. Example: {@code "Lost
     *                             connection to the MCP agent at &lt;URL&gt;
     *                             mid-call — the action may or may not have
     *                             completed; take a fresh snapshot to
     *                             verify."}
     */
    public ProxyMessages(String upstreamDownMessage,
                         String driftMessage,
                         String sessionLostMessage,
                         String ioMidCallMessage) {
        this.upstreamDownMessage = Objects.requireNonNull(upstreamDownMessage, "upstreamDownMessage");
        this.driftMessage = Objects.requireNonNull(driftMessage, "driftMessage");
        this.sessionLostMessage = Objects.requireNonNull(sessionLostMessage, "sessionLostMessage");
        this.ioMidCallMessage = Objects.requireNonNull(ioMidCallMessage, "ioMidCallMessage");
    }

    public String upstreamDownMessage() {
        return upstreamDownMessage;
    }

    public String driftMessage() {
        return driftMessage;
    }

    public String sessionLostMessage() {
        return sessionLostMessage;
    }

    public String ioMidCallMessage() {
        return ioMidCallMessage;
    }
}
