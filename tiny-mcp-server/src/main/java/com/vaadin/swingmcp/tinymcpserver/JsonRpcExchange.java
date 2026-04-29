package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Request-scoped wrapper around {@link HttpExchange} that provides
 * JSON-RPC response helpers. Created once per incoming request; holds
 * the mutable request ID (set after parsing) and the session ID
 * (set by {@link TinyMCPServer} after routing to a session).
 */
class JsonRpcExchange {

    private static final Logger LOG = Logger.getLogger(JsonRpcExchange.class.getName());
    private static final Gson GSON_WITH_NULLS = new GsonBuilder().serializeNulls().create();

    private final HttpExchange exchange;
    private String sessionId;
    private Object requestId;

    JsonRpcExchange(HttpExchange exchange) {
        this.exchange = exchange;
    }

    HttpExchange getHttpExchange() { return exchange; }
    void setSessionId(String sessionId) { this.sessionId = sessionId; }

    /**
     * Returns the request transport headers as an unmodifiable
     * {@code Map<String, String>}, taking the first value for each header
     * (HTTP allows multi-valued headers; MCP transport headers are
     * single-valued in practice). Used by {@link MCPToolHandler},
     * {@link MCPPromptHandler}, and {@link MCPResourceHandler} to populate
     * the {@code transportHeaders} field on the request records.
     */
    Map<String, String> getTransportHeaders() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
            List<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                result.put(entry.getKey(), values.get(0));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    void sendResponse(Object result) {
        MCPProtocol.JsonRpcResponse response = new MCPProtocol.JsonRpcResponse();
        response.setId(requestId);
        response.setResultFrom(result);
        sendJsonBody(200, response.toJson());
    }

    void sendResponseRaw(String resultJson) {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + MCPProtocol.toJson(requestId) + ",\"result\":" + resultJson + "}";
        sendJsonBody(200, json);
    }

    void sendError(int code, String message) {
        sendError(200, code, message);
    }

    void sendError(int httpStatus, int code, String message) {
        MCPProtocol.ErrorObject errorObj = new MCPProtocol.ErrorObject();
        errorObj.setCode(code);
        errorObj.setMessage(message);

        MCPProtocol.JsonRpcError error = new MCPProtocol.JsonRpcError();
        error.setId(requestId);
        error.setError(errorObj);
        sendJsonBody(httpStatus, GSON_WITH_NULLS.toJson(error));
    }

    String readBody() {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TransportIOException(e);
        }
    }

    /**
     * Reads the request body, parses it as a JSON-RPC request, and sets
     * the request ID. Returns the parsed request on success, or {@code null}
     * for notifications (in which case a 202 Accepted has already been sent).
     * Throws {@link MCPServerException} with an appropriate HTTP status for
     * parse/shape errors — caught and rendered by
     * {@link TinyMCPServer#handleRequest}.
     * <p>
     * Session ID validation is handled by {@link TinyMCPServer} before
     * this method is called.
     */
    MCPProtocol.JsonRpcRequest parsePost() {
        String body = readBody();
        LOG.fine("Received POST: " + body);

        // Parse as a generic JsonElement first so we can distinguish
        // malformed JSON (-32700) from valid-JSON-but-wrong-shape (-32600).
        JsonElement jsonElement;
        try {
            jsonElement = MCPProtocol.fromJson(body, JsonElement.class);
        } catch (JsonSyntaxException e) {
            LOG.log(Level.WARNING, "Malformed JSON in request", e);
            throw new MCPServerException(400, MCPServerException.PARSE_ERROR, "Parse error", e);
        }

        if (jsonElement instanceof JsonArray) {
            LOG.warning("Batch requests are not supported");
            throw new MCPServerException(400,
                    MCPServerException.INVALID_REQUEST, "Batch requests are not supported");
        }

        MCPProtocol.JsonRpcRequest request;
        try {
            request = MCPProtocol.gson().fromJson(jsonElement, MCPProtocol.JsonRpcRequest.class);
        } catch (JsonSyntaxException e) {
            LOG.log(Level.WARNING, "Invalid JSON-RPC request", e);
            throw new MCPServerException(400, MCPServerException.INVALID_REQUEST, "Invalid Request", e);
        }

        // Notifications have no id — respond with 202 Accepted
        if (request.getId() == null) {
            sendPlain(202, "");
            return null;
        }

        requestId = request.getId();
        return request;
    }

    void sendPlain(int statusCode, String body) {
        try {
            if (body == null || body.isEmpty()) {
                exchange.sendResponseHeaders(statusCode, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
            exchange.close();
        } catch (IOException e) {
            throw new TransportIOException(e);
        }
    }

    private void sendJsonBody(int statusCode, String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        try {
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new TransportIOException(e);
        }
    }
}
