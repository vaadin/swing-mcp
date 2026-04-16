package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A minimal in-process MCP server using Java's built-in HttpServer.
 * Supports only HTTP transport (no STDIO), binds to 127.0.0.1 only.
 */
public class TinyMCPServer {

    private static final Logger LOG = Logger.getLogger(TinyMCPServer.class.getName());
    private static final Gson GSON_WITH_NULLS = new GsonBuilder().serializeNulls().create();

    public static final int DEFAULT_PORT = 18088;
    public static final String DEFAULT_CONTEXT_PATH = "/mcp";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    /**
     * A tool handler function that receives parsed parameters and returns content.
     *
     * <p>The parameter map is always non-null, even when no parameters are defined or passed.
     * Values are typed according to their schema: {@code String} for string parameters,
     * {@code Integer} for integer parameters, {@code Double} for number parameters,
     * {@code Boolean} for boolean parameters, {@code List<Object>} for array parameters,
     * and {@code Map<String, Object>} for object parameters. Elements and values inside
     * arrays and objects follow the same Java type mapping recursively.
     * Optional parameters absent from the call are not included in the map.
     *
     * <p>The MCP specification defines {@code CallToolResult.content} as a required
     * JSON array with no minimum size. Returning {@code null} produces an empty
     * content array ({@code []}); returning a non-null {@link MCPProtocol.Content}
     * produces a single-element array.
     *
     * <p>Throwing {@link MCPErrorResponseException} produces {@code isError=true} with
     * the exception's message as the text content (no Java class name prefix).
     * Throwing any other exception also produces {@code isError=true} but uses
     * {@link Throwable#toString()} as the text content.
     * Throwing {@link MCPServerException} sends a JSON-RPC protocol error instead.
     */
    @FunctionalInterface
    public interface ToolFunction {
        /**
         * Invokes the tool.
         *
         * @param params the parameter values, never null
         * @return the result content (wrapped in a single-element array),
         *         or {@code null} for an empty result (produces {@code "content": []})
         * @throws MCPErrorResponseException to return {@code isError=true} with a clean message
         * @throws MCPServerException to return a JSON-RPC protocol error
         * @throws Exception if tool execution fails unexpectedly
         */
        MCPProtocol.Content call(Map<String, Object> params) throws Exception;
    }

    private static class RegisteredTool {
        final MCPProtocol.InputSchema inputSchema;
        final ToolFunction function;
        final MCPProtocol.Tool descriptor;

        RegisteredTool(String name, String description, MCPProtocol.InputSchema inputSchema, ToolFunction function) {
            this.inputSchema = inputSchema;
            this.function = function;
            this.descriptor = new MCPProtocol.Tool();
            this.descriptor.setName(name);
            this.descriptor.setDescription(description);
            this.descriptor.setInputSchema(inputSchema);
        }
    }

    /**
     * The port requested in the constructor. May be {@code 0}, meaning
     * "let the OS pick an ephemeral port at bind time". After {@link #start()},
     * the actual bound port is available via {@link #getPort()}.
     */
    private final int port;
    private final String contextPath;
    private final MCPProtocol.Implementation serverInfo;
    private final String instructions;
    private final Map<String, RegisteredTool> tools = new LinkedHashMap<>();
    private volatile boolean started = false;
    private HttpServer httpServer;
    /** Populated by {@link #start()} once the OS has assigned a port. */
    private volatile int boundPort = -1;
    private String activeSessionId;

    public TinyMCPServer() {
        this(DEFAULT_PORT, DEFAULT_CONTEXT_PATH);
    }

    public TinyMCPServer(int port, String contextPath) {
        this(port, contextPath, new MCPProtocol.Implementation(), null);
    }

    public TinyMCPServer(int port, String contextPath, MCPProtocol.Implementation serverInfo) {
        this(port, contextPath, serverInfo, null);
    }

    /**
     * @param port the TCP port to bind to, or {@code 0} to let the OS pick a free
     *             ephemeral port. After {@link #start()}, {@link #getPort()} returns
     *             the actual bound port.
     */
    public TinyMCPServer(int port, String contextPath, MCPProtocol.Implementation serverInfo, String instructions) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Parameter port: invalid value " + port + ": must be in [0, 65535]");
        }
        this.port = port;
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Parameter contextPath: invalid value " + contextPath + ": must start with a slash");
        }
        this.contextPath = contextPath;
        this.serverInfo = serverInfo != null ? serverInfo : new MCPProtocol.Implementation();
        this.instructions = instructions;
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort() + contextPath;
    }

    /**
     * Registers a tool with this server. Must be called before {@link #start()}.
     *
     * @param name        the tool name; not null, not blank
     * @param description human-readable description of the tool; not null, not blank
     * @param inputSchema the parameter schema; not null; consider using {@link InputSchemaBuilder}
     * @param function    the handler to invoke when the tool is called; not null
     * @throws IllegalArgumentException if any argument is null or blank
     * @throws IllegalStateException    if the server has already been started
     * @throws IllegalStateException    if a tool with the same name is already registered
     */
    public void addTool(String name, String description, MCPProtocol.InputSchema inputSchema, ToolFunction function) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name must not be null or blank");
        }
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Tool name must start with a letter or underscore and contain only alphanumeric characters and underscores: " + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description must not be null or blank");
        }
        if (inputSchema == null) {
            throw new IllegalArgumentException("InputSchema must not be null");
        }
        if (function == null) {
            throw new IllegalArgumentException("ToolFunction must not be null");
        }
        if (started) {
            throw new IllegalStateException("Cannot add tools after server has been started");
        }
        if (tools.containsKey(name)) {
            throw new IllegalStateException("A tool with name '" + name + "' is already registered");
        }
        tools.put(name, new RegisteredTool(name, description, inputSchema, function));
    }

    public void start() throws IOException {
        started = true;
        httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
        // If port was 0, the OS assigned an ephemeral port; capture the actual port.
        boundPort = httpServer.getAddress().getPort();
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));
        httpServer.createContext(contextPath, this::handleRequest);
        // Start from a daemon thread so that HTTP-Dispatcher inherits daemon status,
        // preventing it from keeping the JVM alive after the Swing app closes.
        Thread starter = new Thread(httpServer::start, "mcp-server-starter");
        starter.setDaemon(true);
        starter.start();
        try {
            starter.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("TinyMCPServer started on " + getUrl());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            boundPort = -1;
            LOG.info("TinyMCPServer stopped");
        }
    }

    /**
     * Returns the port this server is bound to. Before {@link #start()} (or after
     * {@link #stop()}), returns the port requested in the constructor — which may be
     * {@code 0}, meaning the OS will pick an ephemeral port at bind time. After
     * {@code start()}, returns the actual bound port.
     */
    public int getPort() {
        return boundPort > 0 ? boundPort : port;
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
            LOG.log(Level.SEVERE, "Error handling request", e);
            try {
                sendPlainResponse(exchange, 500, "Internal Server Error");
            } catch (IOException ioe) {
                LOG.log(Level.SEVERE, "Failed to send error response", ioe);
            }
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        // --- Session ID validation (pre-parse) ---
        String incomingSessionId = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        if (incomingSessionId != null) {
            synchronized (this) {
                if (!incomingSessionId.equals(activeSessionId)) {
                    LOG.warning("Rejecting request: Mcp-Session-Id mismatch (received="
                            + incomingSessionId + ", active=" + activeSessionId + ")");
                    sendJsonRpcError(exchange, 404, null,
                            MCPServerException.SERVER_NOT_INITIALIZED, "Session not found.");
                    return;
                }
            }
        }

        String body = readBody(exchange);
        LOG.fine("Received POST: " + body);

        // Parse as a generic JsonElement first so we can distinguish
        // malformed JSON (-32700) from valid-JSON-but-wrong-shape (-32600).
        JsonElement jsonElement;
        try {
            jsonElement = MCPProtocol.fromJson(body, JsonElement.class);
        } catch (JsonSyntaxException e) {
            LOG.log(Level.WARNING, "Malformed JSON in request", e);
            sendJsonRpcError(exchange, 400, null, MCPServerException.PARSE_ERROR, "Parse error");
            return;
        }

        if (jsonElement instanceof JsonArray) {
            LOG.warning("Batch requests are not supported");
            sendJsonRpcError(exchange, 400, null,
                    MCPServerException.INVALID_REQUEST, "Batch requests are not supported");
            return;
        }

        MCPProtocol.JsonRpcRequest request;
        try {
            request = MCPProtocol.gson().fromJson(jsonElement, MCPProtocol.JsonRpcRequest.class);
        } catch (JsonSyntaxException e) {
            LOG.log(Level.WARNING, "Invalid JSON-RPC request", e);
            sendJsonRpcError(exchange, 400, null, MCPServerException.INVALID_REQUEST, "Invalid Request");
            return;
        }
        String rpcMethod = request.getMethod();

        // Notifications have no id — respond with 202 Accepted
        if (request.getId() == null) {
            sendPlainResponse(exchange, 202, "");
            return;
        }

        // --- Session required check (post-parse) ---
        // initialize and ping are always allowed; everything else requires an active session.
        if (!"initialize".equals(rpcMethod) && !"ping".equals(rpcMethod)) {
            synchronized (this) {
                if (activeSessionId == null) {
                    LOG.warning("Rejecting '" + rpcMethod + "': no active session");
                    sendJsonRpcError(exchange, 400, request.getId(),
                            MCPServerException.SERVER_NOT_INITIALIZED,
                            "Server not initialized. Send 'initialize' first.");
                    return;
                }
            }
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
            case "tools/call":
                handleToolsCall(exchange, request);
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
                LOG.warning("Rejecting initialization: another session is already active (id=" + activeSessionId + ")");
                sendJsonRpcError(exchange, 409, request.getId(), -32002, "Another session is already active");
                return;
            }
            activeSessionId = UUID.randomUUID().toString();
        }

        MCPProtocol.InitializeResult result = new MCPProtocol.InitializeResult();
        result.setProtocolVersion(PROTOCOL_VERSION);

        result.setServerInfo(this.serverInfo);
        result.setInstructions(this.instructions);

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
        List<MCPProtocol.Tool> toolList = new ArrayList<>();
        for (RegisteredTool rt : tools.values()) {
            toolList.add(rt.descriptor);
        }
        result.setTools(toolList);
        sendJsonRpcResponse(exchange, request.getId(), result);
    }

    private void handleToolsCall(HttpExchange exchange, MCPProtocol.JsonRpcRequest request) throws IOException {
        MCPProtocol.CallToolParams params = request.getParamsAs(MCPProtocol.CallToolParams.class);
        if (params == null || params.getName() == null) {
            sendJsonRpcError(exchange, request.getId(), -32601, "Method not found");
            return;
        }

        String toolName = params.getName();
        RegisteredTool tool = tools.get(toolName);
        if (tool == null) {
            sendJsonRpcError(exchange, request.getId(), -32601, "Method not found: " + toolName);
            return;
        }

        Map<String, Object> rawArgs = params.getArguments() != null ? params.getArguments() : Collections.emptyMap();
        MCPProtocol.InputSchema schema = tool.inputSchema;
        Map<String, MCPProtocol.PropertySchema> properties =
                schema.getProperties() != null ? schema.getProperties() : Collections.emptyMap();
        List<String> required =
                schema.getRequired() != null ? schema.getRequired() : Collections.emptyList();

        // Warn about unknown parameters
        for (String key : rawArgs.keySet()) {
            if (!properties.containsKey(key)) {
                LOG.warning("Unknown parameter '" + key + "' for tool '" + toolName + "', ignoring");
            }
        }

        // Validate and coerce parameters
        Map<String, Object> callArgs = new HashMap<>();
        for (Map.Entry<String, MCPProtocol.PropertySchema> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            String paramType = entry.getValue().getType();
            Object value = rawArgs.get(paramName);
            boolean isRequired = required.contains(paramName);

            if (value == null) {
                if (isRequired) {
                    sendJsonRpcError(exchange, request.getId(), -32602,
                            "Missing required parameter '" + paramName + "'");
                    return;
                }
                // optional and absent: omit from callArgs
                continue;
            }

            if ("integer".equals(paramType)) {
                if (value instanceof Long) {
                    long l = (Long) value;
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        sendJsonRpcError(exchange, request.getId(), -32602,
                                "Parameter '" + paramName + "' value " + l + " is out of 32-bit integer range");
                        return;
                    }
                    value = (int) l;
                } else if (value instanceof Double) {
                    double d = (Double) value;
                    if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.floor(d)) {
                        sendJsonRpcError(exchange, request.getId(), -32602,
                                "Parameter '" + paramName + "' must be a whole number, got " + d);
                        return;
                    }
                    long l = (long) d;
                    if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
                        sendJsonRpcError(exchange, request.getId(), -32602,
                                "Parameter '" + paramName + "' value " + l + " is out of 32-bit integer range");
                        return;
                    }
                    value = (int) l;
                }
            }

            callArgs.put(paramName, value);
        }

        // Invoke the tool function
        try {
            MCPProtocol.Content content = tool.function.call(callArgs);
            MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
            if (content == null) {
                result.setContent(Collections.emptyList());
            } else {
                result.setContent(Collections.singletonList(content));
            }
            sendJsonRpcResponse(exchange, request.getId(), result);
        } catch (MCPErrorResponseException e) {
            LOG.fine("Tool '" + toolName + "' returned error response: " + e.getMessage());
            MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
            result.setIsError(true);
            result.setContent(Collections.singletonList(MCPProtocol.Content.text(e.getMessage())));
            sendJsonRpcResponse(exchange, request.getId(), result);
        } catch (MCPServerException e) {
            LOG.log(Level.FINE, "Tool '" + toolName + "' threw MCPServerException (code=" + e.getCode() + ")", e);
            sendJsonRpcError(exchange, request.getId(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tool '" + toolName + "' threw an exception", e);
            MCPProtocol.CallToolResult result = new MCPProtocol.CallToolResult();
            result.setIsError(true);
            result.setContent(Collections.singletonList(MCPProtocol.Content.text(e.toString())));
            sendJsonRpcResponse(exchange, request.getId(), result);
        }
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
        boolean wasActive;
        synchronized (this) {
            wasActive = activeSessionId != null;
            if (wasActive) {
                LOG.info("Session terminated: " + activeSessionId);
                activeSessionId = null;
            }
        }
        if (wasActive) {
            onSessionClosed();
        }
        sendPlainResponse(exchange, 200, "");
    }

    /**
     * Called when a session has been closed (via HTTP DELETE), <em>after</em>
     * {@code activeSessionId} has been set to {@code null}. Subclasses can
     * override this to release session-scoped resources.
     * <p>
     * The default implementation does nothing.
     * <p>
     * This method is called <em>outside</em> the server's intrinsic lock,
     * so implementations may safely acquire their own locks (e.g. to
     * serialise with in-flight tool calls).
     */
    protected void onSessionClosed() {
        // no-op by default
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
        sendJsonRpcError(exchange, 200, id, code, message);
    }

    private void sendJsonRpcError(HttpExchange exchange, int httpStatus, Object id, int code, String message) throws IOException {
        MCPProtocol.ErrorObject errorObj = new MCPProtocol.ErrorObject();
        errorObj.setCode(code);
        errorObj.setMessage(message);

        MCPProtocol.JsonRpcError error = new MCPProtocol.JsonRpcError();
        error.setId(id);
        error.setError(errorObj);
        sendJsonBody(exchange, httpStatus, GSON_WITH_NULLS.toJson(error));
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
