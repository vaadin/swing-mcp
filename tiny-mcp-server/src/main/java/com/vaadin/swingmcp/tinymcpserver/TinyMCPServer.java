package com.vaadin.swingmcp.tinymcpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * A minimal in-process MCP server using Java's built-in HttpServer.
 * Supports only HTTP transport (no STDIO), binds to 127.0.0.1 only.
 */
public class TinyMCPServer {

    private static final Logger LOG = LoggerFactory.getLogger(TinyMCPServer.class);

    public static final int DEFAULT_PORT = 18088;
    public static final String DEFAULT_CONTEXT_PATH = "/mcp";
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "Swing MCP";
    private static final String SERVER_VERSION = "0.0.1";

    private final int port;
    private final String contextPath;
    private HttpServer httpServer;
    private String activeSessionId;

    public TinyMCPServer() {
        this(DEFAULT_PORT, DEFAULT_CONTEXT_PATH);
    }

    public TinyMCPServer(int port, String contextPath) {
        this.port = port;
        this.contextPath = contextPath;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        httpServer.createContext(contextPath, this::handleRequest);
        httpServer.start();
        LOG.info("TinyMCPServer started on 127.0.0.1:{}{}", port, contextPath);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            LOG.info("TinyMCPServer stopped");
        }
    }

    public int getPort() {
        return port;
    }

    public String getContextPath() {
        return contextPath;
    }

    private void handleRequest(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            switch (method) {
                case "POST":
                    handlePost(exchange);
                    break;
                case "DELETE":
                    handleDelete(exchange);
                    break;
                default:
                    sendPlainResponse(exchange, 405, "Method Not Allowed");
                    break;
            }
        } catch (Exception e) {
            LOG.error("Error handling request", e);
            try {
                sendPlainResponse(exchange, 500, "Internal Server Error");
            } catch (IOException ioe) {
                LOG.error("Failed to send error response", ioe);
            }
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        LOG.debug("Received POST: {}", body);

        MCPProtocol.JsonRpcRequest request = MCPProtocol.fromJson(body, MCPProtocol.JsonRpcRequest.class);
        String rpcMethod = request.getMethod();

        // Notifications have no id — respond with 202 Accepted
        if (request.getId() == null) {
            sendPlainResponse(exchange, 202, "");
            return;
        }

        switch (rpcMethod != null ? rpcMethod : "") {
            case "initialize":
                handleInitialize(exchange, request);
                break;
            case "ping":
                handlePing(exchange, request);
                break;
            case "tools/list":
                handleToolsList(exchange, request);
                break;
            case "resources/list":
                handleResourcesList(exchange, request);
                break;
            case "prompts/list":
                handlePromptsList(exchange, request);
                break;
            default:
                sendJsonRpcError(exchange, request.getId(),
                        -32601, "Method not found: " + rpcMethod);
                break;
        }
    }

    private void handleInitialize(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        synchronized (this) {
            if (activeSessionId != null) {
                LOG.warn("Rejecting initialization: another session is already active (id={})", activeSessionId);
                sendPlainResponse(exchange, 409, "Another session is already active");
                return;
            }
            activeSessionId = UUID.randomUUID().toString();
        }

        MCPProtocol.InitializeResult result = new MCPProtocol.InitializeResult();
        result.setProtocolVersion(PROTOCOL_VERSION);

        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName(SERVER_NAME);
        serverInfo.setVersion(SERVER_VERSION);
        result.setServerInfo(serverInfo);

        MCPProtocol.ServerCapabilities capabilities = new MCPProtocol.ServerCapabilities();
        capabilities.setTools(new MCPProtocol.ToolsCapability());
        capabilities.setResources(new MCPProtocol.ResourcesCapability());
        capabilities.setPrompts(new MCPProtocol.PromptsCapability());
        result.setCapabilities(capabilities);

        sendJsonRpcResponse(exchange, request.getId(), result);
    }

    private void handlePing(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        sendJsonRpcResponseRaw(exchange, request.getId(), "{}");
    }

    private void handleToolsList(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        MCPProtocol.ListToolsResult result = new MCPProtocol.ListToolsResult();
        result.setTools(Collections.emptyList());
        sendJsonRpcResponse(exchange, request.getId(), result);
    }

    private void handleResourcesList(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        MCPProtocol.ListResourcesResult result = new MCPProtocol.ListResourcesResult();
        result.setResources(Collections.emptyList());
        sendJsonRpcResponse(exchange, request.getId(), result);
    }

    private void handlePromptsList(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        MCPProtocol.ListPromptsResult result = new MCPProtocol.ListPromptsResult();
        result.setPrompts(Collections.emptyList());
        sendJsonRpcResponse(exchange, request.getId(), result);
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        synchronized (this) {
            if (activeSessionId != null) {
                LOG.info("Session terminated: {}", activeSessionId);
                activeSessionId = null;
            }
        }
        sendPlainResponse(exchange, 200, "");
    }

    // ===== Response helpers =====

    private void sendJsonRpcResponse(HttpExchange exchange, Object id, Object result) throws IOException {
        MCPProtocol.JsonRpcResponse response = new MCPProtocol.JsonRpcResponse();
        response.setId(id);
        response.setResultFrom(result);
        sendJsonBody(exchange, 200, response.toJson());
    }

    private void sendJsonRpcResponseRaw(HttpExchange exchange, Object id, String resultJson) throws IOException {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + MCPProtocol.toJson(id) + ",\"result\":" + resultJson + "}";
        sendJsonBody(exchange, 200, json);
    }

    private void sendJsonRpcError(HttpExchange exchange, Object id, int code, String message) throws IOException {
        MCPProtocol.ErrorObject errorObj = new MCPProtocol.ErrorObject();
        errorObj.setCode(code);
        errorObj.setMessage(message);

        MCPProtocol.JsonRpcError error = new MCPProtocol.JsonRpcError();
        error.setId(id);
        error.setError(errorObj);
        sendJsonBody(exchange, 200, error.toJson());
    }

    private void sendJsonBody(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (activeSessionId != null) {
            exchange.getResponseHeaders().set("Mcp-Session-Id", activeSessionId);
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendPlainResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
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

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
