package com.vaadin.swingmcp.tinymcpclient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import com.vaadin.swingmcp.tinymcpserver.ToolRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * No-retry HTTP MCP client. Drives the server's HTTP transport via
 * {@link HttpClient} (JDK built-in, zero new runtime deps per DR-002).
 *
 * <p>HTTP 404 from a non-{@code initialize} call is mapped to
 * {@link MCPSessionLostException}; other 4xx/5xx responses become
 * generic {@link MCPClientException} instances. The mapping happens
 * here, in the only class that knows the protocol — decorators
 * ({@link AutoRetryMCPClient}) do not re-derive it from status codes.
 *
 * <p>This implementation does <em>not</em> retry. Stateless callers can
 * opt into transparent recovery via {@link MCPClient#autoRetry()}; see
 * DR-008.
 */
public final class TinyMCPClient implements MCPClient {

    private static final Logger LOG = Logger.getLogger(TinyMCPClient.class.getName());

    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final String CLIENT_NAME = "tiny-mcp-client";
    private static final String CLIENT_VERSION = "1.0";

    private final URI serverUrl;
    private final HttpClient http;
    private final AtomicLong nextId = new AtomicLong(1);

    /**
     * The {@code Mcp-Session-Id} returned by the server during
     * {@link #initialize()}. Sent on every subsequent request.
     */
    private volatile String sessionId;

    /**
     * The protocol version the server picked in its {@code initialize}
     * response, captured so we can echo it back via the
     * {@code MCP-Protocol-Version} HTTP header on every subsequent
     * request (required by the MCP spec from 2025-06-18 onward).
     */
    private volatile String negotiatedProtocolVersion;

    private volatile boolean closed;

    public TinyMCPClient(URI serverUrl) {
        if (serverUrl == null) {
            throw new IllegalArgumentException("serverUrl must not be null");
        }
        this.serverUrl = serverUrl;
        this.http = HttpClient.newHttpClient();
    }

    @Override
    public MCPProtocol.InitializeResult initialize() throws IOException {
        ensureOpen();

        MCPProtocol.InitializeParams params = new MCPProtocol.InitializeParams();
        params.setProtocolVersion(PROTOCOL_VERSION);
        params.setCapabilities(new MCPProtocol.ClientCapabilities());
        MCPProtocol.Implementation clientInfo = new MCPProtocol.Implementation();
        clientInfo.setName(CLIENT_NAME);
        clientInfo.setVersion(CLIENT_VERSION);
        params.setClientInfo(clientInfo);

        // Reset before sending; the new session id arrives in the response
        // header and is captured by sendRequest(). The negotiated protocol
        // version is captured here once the server replies.
        sessionId = null;
        negotiatedProtocolVersion = null;

        JsonElement resultEl = sendRequest("initialize", params, null);
        MCPProtocol.InitializeResult result = MCPProtocol.fromJson(resultEl.toString(),
                MCPProtocol.InitializeResult.class);
        negotiatedProtocolVersion = result.getProtocolVersion();

        // Per the MCP spec, the client must follow up with the
        // notifications/initialized notification.
        sendNotification("notifications/initialized");

        return result;
    }

    @Override
    public List<MCPProtocol.Tool> listTools() throws IOException {
        ensureOpen();
        JsonElement resultEl = sendRequest("tools/list", null, null);
        MCPProtocol.ListToolsResult result = MCPProtocol.fromJson(resultEl.toString(),
                MCPProtocol.ListToolsResult.class);
        return result.getTools() != null ? result.getTools() : Collections.emptyList();
    }

    @Override
    public MCPProtocol.CallToolResult callTool(ToolRequest request) throws IOException {
        ensureOpen();
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Tool name must not be null or blank");
        }
        MCPProtocol.CallToolParams params = new MCPProtocol.CallToolParams();
        params.setName(request.name());
        params.setArguments(request.arguments() != null ? request.arguments().raw() : Collections.emptyMap());

        JsonElement resultEl = sendRequest("tools/call", params, request.jsonRpcMeta());
        return MCPProtocol.fromJson(resultEl.toString(), MCPProtocol.CallToolResult.class);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        if (sessionId == null) {
            return;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUrl).DELETE();
        addSessionHeaders(builder);
        try {
            HttpResponse<Void> response = http.send(builder.build(), BodyHandlers.discarding());
            int status = response.statusCode();
            if (status != 200 && status != 404) {
                // Don't throw on a best-effort cleanup, just log.
                LOG.warning("DELETE returned HTTP " + status + " for session " + sessionId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending DELETE", e);
        } finally {
            sessionId = null;
        }
    }

    // ---------- internals ----------

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Client has been closed");
        }
    }

    /**
     * Adds the session-scoped HTTP headers to {@code builder}: the
     * {@code Mcp-Session-Id} from the server's {@code initialize} response
     * (so the server can route the request) and the
     * {@code MCP-Protocol-Version} header naming the negotiated version
     * (required by the MCP spec from 2025-06-18 onward, ignored by older
     * servers). Both are no-ops before {@code initialize} succeeds.
     */
    private void addSessionHeaders(HttpRequest.Builder builder) {
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        if (negotiatedProtocolVersion != null) {
            builder.header("MCP-Protocol-Version", negotiatedProtocolVersion);
        }
    }

    /**
     * Sends a JSON-RPC notification (no {@code id}, no expected response
     * body). Used for {@code notifications/initialized}. Server replies
     * with HTTP 202 Accepted; any other 2xx is also tolerated.
     */
    private void sendNotification(String method) throws IOException {
        MCPProtocol.JsonRpcNotification notif = new MCPProtocol.JsonRpcNotification();
        notif.setMethod(method);
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUrl)
                .POST(BodyPublishers.ofString(notif.toJson()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        addSessionHeaders(builder);
        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending notification " + method, e);
        }
        int status = response.statusCode();
        if (status == 404) {
            throw new MCPSessionLostException(extractErrorMessage(response.body(),
                    "Session not found while sending notification " + method));
        }
        if (status / 100 != 2) {
            throw new MCPClientException(MCPServerException.INTERNAL_ERROR,
                    "Notification " + method + " failed: HTTP " + status + ": " + response.body());
        }
    }

    /**
     * Parses a JSON-RPC error envelope and returns its {@code error.message}
     * field; falls back to {@code fallback} on any parse failure or if the
     * field is missing/empty. Used to surface server-side 404 reasons (e.g.
     * the supersede tombstone message — see DR-015) instead of a generic
     * client-side string.
     */
    private static String extractErrorMessage(String body, String fallback) {
        if (body == null || body.isEmpty()) return fallback;
        try {
            JsonElement el = MCPProtocol.fromJson(body, JsonElement.class);
            if (el == null || !el.isJsonObject()) return fallback;
            JsonElement err = el.getAsJsonObject().get("error");
            if (err == null || !err.isJsonObject()) return fallback;
            JsonElement msg = err.getAsJsonObject().get("message");
            if (msg == null || !msg.isJsonPrimitive()) return fallback;
            String s = msg.getAsString();
            return (s == null || s.isEmpty()) ? fallback : s;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Sends a JSON-RPC request and returns the {@code result} as a
     * {@link JsonElement} ready to be deserialized by the caller.
     *
     * <p>Captures the {@code Mcp-Session-Id} response header, if present,
     * so the {@code initialize} response wires up subsequent calls.
     *
     * <p>If {@code extraMeta} is non-null, it is merged into the outgoing
     * request's {@code params._meta} object, so a forwarding caller (proxy)
     * can pass through cross-cutting fields such as {@code progressToken}.
     */
    private JsonElement sendRequest(String method, Object params, JsonObject extraMeta) throws IOException {
        MCPProtocol.JsonRpcRequest request = new MCPProtocol.JsonRpcRequest();
        request.setId(nextId.getAndIncrement());
        request.setMethod(method);
        if (params != null) {
            request.setParamsFrom(params);
        }
        if (extraMeta != null) {
            JsonElement paramsEl = request.getParams();
            JsonObject paramsObj;
            if (paramsEl != null && paramsEl.isJsonObject()) {
                paramsObj = paramsEl.getAsJsonObject();
            } else {
                paramsObj = new JsonObject();
                request.setParams(paramsObj);
            }
            paramsObj.add("_meta", extraMeta);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(serverUrl)
                .POST(BodyPublishers.ofString(request.toJson()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        addSessionHeaders(builder);

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while sending request " + method, e);
        }

        int status = response.statusCode();
        // Streamable HTTP transport allows the server to reply with either
        // application/json (a single JSON-RPC envelope) or text/event-stream
        // (one or more SSE events whose data: payload is the JSON-RPC
        // envelope). For our minimal request/response surface there is
        // always exactly one response event, so it is safe to extract the
        // first event's data and treat it as plain JSON.
        String body = unframeSse(response.body(),
                response.headers().firstValue("Content-Type").orElse(null));

        // 404 from a non-initialize call → session lost.
        // initialize itself never carries a session id, so the server
        // would not send 404 there; treat it as a generic protocol error.
        if (status == 404 && !"initialize".equals(method)) {
            throw new MCPSessionLostException(extractErrorMessage(body,
                    "Session not found (HTTP 404) on " + method));
        }

        // Capture session id from the response header. The server emits it
        // on every successful response (including initialize); we record it
        // so initialize wires up subsequent calls. Already-known ids are
        // overwritten harmlessly (they will be the same).
        response.headers().firstValue("Mcp-Session-Id").ifPresent(id -> this.sessionId = id);

        // 4xx/5xx (other than the 404 above) — try to surface a JSON-RPC
        // error from the body, falling back to a synthetic one.
        if (status / 100 != 2) {
            MCPProtocol.ErrorObject errObj = tryParseErrorBody(body);
            if (errObj != null) {
                throw new MCPClientException(errObj.getCode(), errObj.getMessage());
            }
            throw new MCPClientException(MCPServerException.INTERNAL_ERROR,
                    "HTTP " + status + ": " + (body != null ? body : ""));
        }

        // 2xx — parse the JSON-RPC envelope.
        JsonElement bodyEl;
        try {
            bodyEl = MCPProtocol.fromJson(body, JsonElement.class);
        } catch (JsonSyntaxException e) {
            throw new MCPClientException(MCPServerException.INTERNAL_ERROR,
                    "Malformed JSON in response: " + body, e);
        }
        if (bodyEl == null || !bodyEl.isJsonObject()) {
            throw new MCPClientException(MCPServerException.INTERNAL_ERROR,
                    "Expected JSON-RPC object in response: " + body);
        }
        JsonObject obj = bodyEl.getAsJsonObject();
        JsonElement errorEl = obj.get("error");
        if (errorEl != null && errorEl.isJsonObject()) {
            MCPProtocol.ErrorObject errObj = MCPProtocol.fromJson(errorEl.toString(),
                    MCPProtocol.ErrorObject.class);
            throw new MCPClientException(errObj.getCode(), errObj.getMessage());
        }
        JsonElement resultEl = obj.get("result");
        if (resultEl == null) {
            throw new MCPClientException(MCPServerException.INTERNAL_ERROR,
                    "Response has neither 'result' nor 'error': " + body);
        }
        return resultEl;
    }

    /**
     * If {@code contentType} indicates SSE framing, returns the data
     * payload of the first complete event in {@code body}. Multiple
     * {@code data:} lines for one event are joined with {@code \n} as
     * the SSE spec requires. Otherwise returns {@code body} unchanged.
     *
     * <p>This is intentionally minimal: our client surface
     * (initialize / listTools / callTool) does not request progress
     * notifications, so the server replies with a single response event;
     * if a future caller does request progress, this code will need to
     * evolve to scan for the event whose payload matches the request id.
     */
    private static String unframeSse(String body, String contentType) {
        if (body == null || contentType == null
                || !contentType.toLowerCase().contains("text/event-stream")) {
            return body;
        }
        StringBuilder data = new StringBuilder();
        for (String rawLine : body.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.startsWith("data:")) {
                String value = line.substring("data:".length());
                if (value.startsWith(" ")) value = value.substring(1);
                if (data.length() > 0) data.append('\n');
                data.append(value);
            } else if (line.isEmpty() && data.length() > 0) {
                return data.toString();
            }
            // Other SSE fields (id:, event:, comments starting with :) are
            // not load-bearing for our request/response surface.
        }
        return data.toString();
    }

    private static MCPProtocol.ErrorObject tryParseErrorBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonElement el = MCPProtocol.fromJson(body, JsonElement.class);
            if (el == null || !el.isJsonObject()) return null;
            JsonElement errorEl = el.getAsJsonObject().get("error");
            if (errorEl == null || !errorEl.isJsonObject()) return null;
            return MCPProtocol.fromJson(errorEl.toString(), MCPProtocol.ErrorObject.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
