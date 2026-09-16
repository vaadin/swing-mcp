package com.vaadin.swingmcp.tinymcpclient;

import com.google.gson.JsonObject;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.ToolRequest;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Minimal MCP client surface used by in-tree consumers (notably the
 * forwarding proxy use case). Speaks the MCP HTTP transport from the
 * caller side.
 *
 * <p>The surface is intentionally small: {@link #initialize()},
 * {@link #listTools()}, {@link #callTool(ToolRequest)}, and
 * {@link #close()}. Resources and prompts are not in the initial
 * surface; add when a use case asks for them. See D_embedded_client.
 *
 * <h2>Errors</h2>
 * <ul>
 *   <li>{@link MCPClientException} — JSON-RPC protocol error from the
 *       server, or HTTP 4xx/5xx (other than 404) rendered as a synthetic
 *       JSON-RPC error.</li>
 *   <li>{@link MCPSessionLostException} (extends {@code MCPClientException})
 *       — HTTP 404 from a non-{@code initialize} call.</li>
 *   <li>{@link IOException} — transport failure (connection refused,
 *       timeout, broken pipe). The client itself does not retry transport
 *       errors; the caller decides.</li>
 * </ul>
 *
 * <p>{@link MCPProtocol.CallToolResult#getIsError()} = {@code true} is
 * <em>not</em> an exception — it is returned to the caller as a normal
 * result, mirroring the server's three-layer error model (D_three_error_layers).
 */
public interface MCPClient extends Closeable {

    /**
     * Performs the JSON-RPC handshake, stores the {@code Mcp-Session-Id}
     * returned by the server, and sends the {@code notifications/initialized}
     * follow-up. Idempotent: calling again starts a fresh session against
     * the same URL — used by the {@link AutoRetryMCPClient} recovery path.
     *
     * @return the parsed {@code InitializeResult} from the server
     * @throws MCPClientException if the server returns a JSON-RPC protocol
     *                            error or a non-2xx HTTP status
     * @throws IOException        if the underlying HTTP transport fails
     */
    MCPProtocol.InitializeResult initialize() throws IOException;

    /**
     * Calls {@code tools/list} and returns the registered tools.
     *
     * @return the list of tools advertised by the server; never {@code null}
     * @throws MCPSessionLostException if the server returned HTTP 404
     *                                 (session lost — re-initialize required)
     * @throws MCPClientException      if the server returned any other
     *                                 protocol error
     * @throws IOException             if the underlying HTTP transport fails
     */
    List<MCPProtocol.Tool> listTools() throws IOException;

    /**
     * Calls {@code tools/call}, forwarding the tool name, arguments, and
     * JSON-RPC {@code _meta} from the supplied {@link ToolRequest} to the
     * server. {@code _meta} (e.g. {@code progressToken}) is embedded into
     * the outgoing request's {@code params._meta}, so cross-cutting fields
     * survive a hop through a forwarding proxy (see D_request_records).
     *
     * <p>{@link ToolRequest#transportHeaders()} is <em>not</em> forwarded
     * as outbound HTTP headers — they belong to the inbound transport and
     * forwarding them would clobber session and content-type headers on
     * the outgoing request. Implementations should ignore that field.
     *
     * <p>An application-level tool error is signalled by
     * {@link MCPProtocol.CallToolResult#getIsError()} being {@code true};
     * the call returns normally and the caller inspects the result.
     *
     * @param request the tool request bundle (name, arguments, transport
     *                headers, JSON-RPC {@code _meta}); not null
     * @return the {@code CallToolResult}; never {@code null}
     * @throws MCPSessionLostException if the server returned HTTP 404
     * @throws MCPClientException      if the server returned any other
     *                                 protocol error
     * @throws IOException             if the underlying HTTP transport fails
     */
    MCPProtocol.CallToolResult callTool(ToolRequest request) throws IOException;

    /**
     * Convenience overload of {@link #callTool(ToolRequest)} for callers
     * that don't have a {@link ToolRequest} on hand. Builds a request with
     * empty transport headers and no {@code _meta}.
     *
     * @param name      the tool name; not null
     * @param arguments the tool arguments; may be {@code null} or empty
     * @return the {@code CallToolResult}; never {@code null}
     * @throws MCPSessionLostException if the server returned HTTP 404
     * @throws MCPClientException      if the server returned any other
     *                                 protocol error
     * @throws IOException             if the underlying HTTP transport fails
     */
    default MCPProtocol.CallToolResult callTool(String name, Map<String, Object> arguments) throws IOException {
        return callTool(name, arguments, null);
    }

    /**
     * Convenience overload of {@link #callTool(ToolRequest)} accepting an
     * explicit JSON-RPC {@code _meta} object. Used by forwarding proxies
     * (D_forwarding_proxy / D_settable_listeners) so cross-cutting envelope fields like
     * {@code progressToken} survive end-to-end through a proxy hop.
     *
     * @param name      the tool name; not null
     * @param arguments the tool arguments; may be {@code null} or empty
     * @param meta      the JSON-RPC {@code _meta} object to forward; may
     *                  be {@code null} for "no meta"
     * @return the {@code CallToolResult}; never {@code null}
     * @throws MCPSessionLostException if the server returned HTTP 404
     * @throws MCPClientException      if the server returned any other
     *                                 protocol error
     * @throws IOException             if the underlying HTTP transport fails
     */
    default MCPProtocol.CallToolResult callTool(String name, Map<String, Object> arguments, JsonObject meta) throws IOException {
        return callTool(new ToolRequest(
                name,
                arguments != null ? arguments : Collections.emptyMap(),
                Collections.emptyMap(),
                meta));
    }

    /**
     * Sends an HTTP {@code DELETE} for the current session, releasing it
     * server-side. The client is unusable afterwards. Idempotent: calling
     * a second time is a no-op. If the session is already gone server-side
     * (HTTP 404), {@code close()} returns normally — there is nothing left
     * to release.
     *
     * @throws IOException if the HTTP transport fails
     */
    @Override
    void close() throws IOException;

    /**
     * Wraps {@code this} in a one-shot session-loss retry decorator.
     * Calling code:
     * <pre>{@code
     *     MCPClient client = new TinyMCPClient(url).autoRetry();
     * }</pre>
     * makes any single {@link MCPSessionLostException} from {@code this}
     * recoverable: the decorator catches the exception, calls
     * {@code this.initialize()}, and replays the failed call exactly once.
     * A second {@code MCPSessionLostException} on the replay surfaces to
     * the caller. {@link IOException} is never retried.
     *
     * <p>Implemented as a default method so any future {@code MCPClient}
     * implementation gets retry-wrapping for free, and chaining with
     * future decorators reads left-to-right.
     *
     * <p>Auto-retry is opt-in because re-initialization silently discards
     * any session-bound state (e.g. a map of handles a previous call handed
     * out, whose keys mean nothing to a fresh session) — for
     * stateful callers, failure is information; for stateless callers, the
     * convenience is worth it. See D_no_auto_retry.
     */
    default MCPClient autoRetry() {
        return new AutoRetryMCPClient(this);
    }
}
