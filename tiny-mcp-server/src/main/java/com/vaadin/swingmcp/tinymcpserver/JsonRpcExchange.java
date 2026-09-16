/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Request-scoped wrapper around {@link HttpExchange} that provides
 * JSON-RPC response helpers. Created once per incoming request; holds
 * the mutable request ID (set after parsing) and the session ID
 * (set by {@link HttpMCPServer} after routing to a session).
 */
class JsonRpcExchange {

    private static final Logger LOG = Logger.getLogger(JsonRpcExchange.class.getName());
    private static final Gson GSON_WITH_NULLS = new GsonBuilder().serializeNulls().create();

    private final HttpExchange exchange;
    private @Nullable String sessionId;
    private @Nullable Object requestId;
    private boolean requestBodyConsumed;

    JsonRpcExchange(HttpExchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
    }

    HttpExchange getHttpExchange() { return exchange; }
    void setSessionId(String sessionId) { this.sessionId = Objects.requireNonNull(sessionId, "sessionId"); }

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
            requestBodyConsumed = true;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TransportIOException(e);
        }
    }

    /**
     * Reads and discards anything left of the request body, so the connection
     * stays reusable.
     *
     * @implNote {@code com.sun.net.httpserver} closes the TCP connection when a
     * handler responds without consuming the request body. The rejection paths
     * — an unknown session id, an unsupported method — answer before the body
     * is ever parsed, and would otherwise poison a keep-alive connection the
     * client has already pooled. A client that then reuses it sees the request
     * die with no response; Java 11's {@code HttpClient} does not retry a POST
     * in that case (JDK 12+ does), so on the Java 11 floor this surfaces as a
     * flat "header parser received no bytes" rather than a clean error.
     */
    private void drainRequestBody() {
        if (requestBodyConsumed) {
            return;
        }
        requestBodyConsumed = true;
        try (InputStream is = exchange.getRequestBody()) {
            final byte[] scratch = new byte[4096];
            while (is.read(scratch) >= 0) {
                // discard
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not drain request body before responding", e);
        }
    }

    /**
     * Reads the request body, parses it as a JSON-RPC request, and sets the
     * request ID. Session ID validation is handled by {@link HttpMCPServer}
     * before this method is called.
     *
     * @return the parsed request, or {@code null} if the body was a
     * notification — an id-less request that gets no JSON-RPC response. The
     * 202 Accepted has already been sent by the time {@code null} comes back,
     * so the caller must simply return.
     * @throws MCPServerException with an appropriate HTTP status for
     * parse/shape errors — caught and rendered by
     * {@link HttpMCPServer#handleRequest}
     */
    MCPProtocol.@Nullable JsonRpcRequest parsePost() {
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
        drainRequestBody();
        try {
            if (body.isEmpty()) {
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
        drainRequestBody();
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
