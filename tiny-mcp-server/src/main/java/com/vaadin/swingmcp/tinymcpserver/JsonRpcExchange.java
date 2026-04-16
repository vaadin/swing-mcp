package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Request-scoped wrapper around {@link HttpExchange} that provides
 * JSON-RPC response helpers. Created once per incoming request; holds
 * the mutable request ID (set after parsing) and the session ID
 * (captured at construction, updatable for the initialize flow).
 */
class JsonRpcExchange {

    private static final Gson GSON_WITH_NULLS = new GsonBuilder().serializeNulls().create();

    private final HttpExchange exchange;
    private String sessionId;
    private Object requestId;

    JsonRpcExchange(HttpExchange exchange, String sessionId) {
        this.exchange = exchange;
        this.sessionId = sessionId;
    }

    HttpExchange getHttpExchange() { return exchange; }
    void setRequestId(Object requestId) { this.requestId = requestId; }
    void setSessionId(String sessionId) { this.sessionId = sessionId; }

    void sendResponse(Object result) throws IOException {
        MCPProtocol.JsonRpcResponse response = new MCPProtocol.JsonRpcResponse();
        response.setId(requestId);
        response.setResultFrom(result);
        sendJsonBody(200, response.toJson());
    }

    void sendResponseRaw(String resultJson) throws IOException {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + MCPProtocol.toJson(requestId) + ",\"result\":" + resultJson + "}";
        sendJsonBody(200, json);
    }

    void sendError(int code, String message) throws IOException {
        sendError(200, code, message);
    }

    void sendError(int httpStatus, int code, String message) throws IOException {
        MCPProtocol.ErrorObject errorObj = new MCPProtocol.ErrorObject();
        errorObj.setCode(code);
        errorObj.setMessage(message);

        MCPProtocol.JsonRpcError error = new MCPProtocol.JsonRpcError();
        error.setId(requestId);
        error.setError(errorObj);
        sendJsonBody(httpStatus, GSON_WITH_NULLS.toJson(error));
    }

    void sendToolError(String message) throws IOException {
        MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
        result.setIsError(true);
        result.setContent(Collections.singletonList(MCPProtocol.Content.text(message)));
        sendResponse(result);
    }

    void sendPlain(int statusCode, String body) throws IOException {
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
    }

    private void sendJsonBody(int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (sessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
