package com.vaadin.swingmcp.tinymcpserver;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for tool registration and invocation via the MCP client.
 * Uses a dedicated server on an OS-assigned ephemeral port with tools pre-registered.
 */
class TinyMCPServerToolTest {

    private static TinyMCPServer server;
    private static McpSyncClient client;

    /** Captures the arguments received by the last call to the multi-type test tool. */
    private static final AtomicReference<Map<String, Object>> lastCallArgs = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        server = new TinyMCPServer(0, "/mcp");

        // Tool: echo_text — returns the "message" string as text content
        server.addTool("echo_text", "Echo a text message",
                new InputSchemaBuilder()
                        .requiredString("message", "The message to echo")
                        .build(),
                request -> MCPProtocol.Content.text((String) request.arguments().get("message")));

        // Tool: add_integers — returns sum of two integers as text
        server.addTool("add_integers", "Add two integers",
                new InputSchemaBuilder()
                        .requiredInteger("a", "First integer")
                        .requiredInteger("b", "Second integer")
                        .build(),
                request -> MCPProtocol.Content.text(
                        String.valueOf((Integer) request.arguments().get("a") + (Integer) request.arguments().get("b"))));

        // Tool: multi_type — accepts all parameter types, records args, returns text
        server.addTool("multi_type", "Test all parameter types",
                new InputSchemaBuilder()
                        .requiredString("str_param", "A string")
                        .requiredInteger("int_param", "An integer")
                        .requiredNumber("num_param", "A number")
                        .requiredBoolean("bool_param", "A boolean")
                        .build(),
                request -> {
                    lastCallArgs.set(Map.copyOf(request.arguments()));
                    return MCPProtocol.Content.text("ok");
                });

        // Tool: optional_params — has one required and one optional param
        server.addTool("optional_params", "Tool with optional parameters",
                new InputSchemaBuilder()
                        .requiredString("required_str", "Required string")
                        .optionalString("optional_str", "Optional string")
                        .build(),
                request -> {
                    lastCallArgs.set(Map.copyOf(request.arguments()));
                    return MCPProtocol.Content.text("ok");
                });

        // Tool: return_null — always returns null content
        server.addTool("return_null", "Returns null content",
                new InputSchemaBuilder().build(),
                request -> null);

        // Tool: return_image — returns image content
        server.addTool("return_image", "Returns image content",
                new InputSchemaBuilder().build(),
                request -> MCPProtocol.Content.image("aW1hZ2VkYXRh", "image/png"));

        // Tool: return_audio — returns audio content
        server.addTool("return_audio", "Returns audio content",
                new InputSchemaBuilder().build(),
                request -> MCPProtocol.Content.audio("YXVkaW9kYXRh", "audio/wav"));

        // Tool: return_resource — returns embedded resource content
        server.addTool("return_resource", "Returns resource content",
                new InputSchemaBuilder().build(),
                request -> {
                    MCPProtocol.ResourceContents rc = new MCPProtocol.ResourceContents();
                    rc.setUri("file:///test.txt");
                    rc.setMimeType("text/plain");
                    rc.setText("resource text");
                    return MCPProtocol.Content.resource(rc);
                });

        // Tool: throw_exception — always throws
        server.addTool("throw_exception", "Always throws an exception",
                new InputSchemaBuilder().build(),
                request -> { throw new RuntimeException("something went wrong"); });

        // Tool: throw_mcp_internal_error — throws MCPServerException with INTERNAL_ERROR
        server.addTool("throw_mcp_internal_error", "Throws MCPServerException INTERNAL_ERROR",
                new InputSchemaBuilder().build(),
                request -> {
                    throw new MCPServerException(MCPServerException.INTERNAL_ERROR, "internal failure");
                });

        // Tool: throw_mcp_invalid_params — throws MCPServerException with INVALID_PARAMS
        server.addTool("throw_mcp_invalid_params", "Throws MCPServerException INVALID_PARAMS",
                new InputSchemaBuilder()
                        .requiredString("value", "A value to validate")
                        .build(),
                request -> {
                    throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                            "value must be non-empty");
                });

        // Tool: throw_mcp_custom_code — throws MCPServerException with a custom code
        server.addTool("throw_mcp_custom_code", "Throws MCPServerException with custom code",
                new InputSchemaBuilder().build(),
                request -> {
                    throw new MCPServerException(-32000, "custom server error");
                });

        // Tool: throw_mcp_error_response — throws MCPErrorResponseException
        server.addTool("throw_mcp_error_response", "Throws MCPErrorResponseException",
                new InputSchemaBuilder().build(),
                request -> {
                    throw new MCPErrorResponseException("clean error message");
                });

        // Tool: no_params_tool — has no defined params, to test unknown param rejection
        server.addTool("no_params_tool", "Tool with no params",
                new InputSchemaBuilder().build(),
                request -> {
                    lastCallArgs.set(Map.copyOf(request.arguments()));
                    return MCPProtocol.Content.text("ok");
                });

        // Tool: bounded_integer — integer param with min and max constraints
        server.addTool("bounded_integer", "Tool with bounded integer parameter",
                new InputSchemaBuilder()
                        .requiredInteger("count", "Number of items")
                        .withMinimum(1)
                        .withMaximum(100)
                        .build(),
                request -> {
                    lastCallArgs.set(Map.copyOf(request.arguments()));
                    return MCPProtocol.Content.text("ok");
                });

        // Tool: enum_string — string param with enum constraint
        server.addTool("enum_string", "Tool with enum string parameter",
                new InputSchemaBuilder()
                        .requiredString("color", "A color")
                        .withEnum("red", "green", "blue")
                        .build(),
                request -> {
                    lastCallArgs.set(Map.copyOf(request.arguments()));
                    return MCPProtocol.Content.text("ok");
                });

        // Tool: echo_array — accepts a required array param, returns it as JSON content
        server.addTool("echo_array", "Echoes an array back as JSON",
                new InputSchemaBuilder()
                        .requiredArray("items", "The items to echo")
                        .build(),
                request -> MCPProtocol.Content.json(request.arguments().get("items")));

        // Tool: echo_object — accepts a required object param, returns it as JSON content
        server.addTool("echo_object", "Echoes an object back as JSON",
                new InputSchemaBuilder()
                        .requiredObject("config", "The config to echo")
                        .build(),
                request -> MCPProtocol.Content.json(request.arguments().get("config")));

        server.start();

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(server.getUrl())
                .openConnectionOnStartup(false)
                .build();

        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .build();

        client.initialize();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    // ===== tools/list =====

    @Test
    void toolsListReturnsAllRegisteredTools() {
        McpSchema.ListToolsResult result = client.listTools();
        assertNotNull(result);
        List<McpSchema.Tool> tools = result.tools();
        assertEquals(18, tools.size());

        McpSchema.Tool echoTool = tools.stream()
                .filter(t -> "echo_text".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("echo_text", echoTool.name());
        assertEquals("Echo a text message", echoTool.description());
        assertNotNull(echoTool.inputSchema());
        assertEquals("object", echoTool.inputSchema().type());
        assertTrue(echoTool.inputSchema().properties().containsKey("message"));
        assertTrue(echoTool.inputSchema().required().contains("message"));
    }

    @Test
    void toolsListInputSchemaPassedAsIs() {
        McpSchema.ListToolsResult result = client.listTools();
        McpSchema.Tool addTool = result.tools().stream()
                .filter(t -> "add_integers".equals(t.name()))
                .findFirst()
                .orElseThrow();

        assertNotNull(addTool.inputSchema());
        assertTrue(addTool.inputSchema().properties().containsKey("a"));
        assertTrue(addTool.inputSchema().properties().containsKey("b"));
        assertEquals(2, addTool.inputSchema().required().size());
    }

    // ===== tools/call — parameter passing =====

    @Test
    void callToolWithEmptyParams() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("return_null", Map.of()));
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(result.content().isEmpty());
    }

    @Test
    void callToolAllParameterTypes() {
        lastCallArgs.set(null);
        client.callTool(new McpSchema.CallToolRequest("multi_type", Map.of(
                "str_param", "hello",
                "int_param", 42,
                "num_param", 3.14,
                "bool_param", true
        )));
        Map<String, Object> args = lastCallArgs.get();
        assertNotNull(args);
        assertEquals("hello", args.get("str_param"));
        assertInstanceOf(Integer.class, args.get("int_param"));
        assertEquals(42, args.get("int_param"));
        assertInstanceOf(Double.class, args.get("num_param"));
        assertEquals(3.14, (Double) args.get("num_param"), 0.001);
        assertEquals(Boolean.TRUE, args.get("bool_param"));
    }

    @Test
    void callToolIntegerCoercionWholeDouble() {
        // Pass 5.0 (a Double) for an integer parameter — should be coerced to Integer(5)
        lastCallArgs.set(null);
        client.callTool(new McpSchema.CallToolRequest("multi_type", Map.of(
                "str_param", "x",
                "int_param", 5.0,
                "num_param", 1.0,
                "bool_param", false
        )));
        Map<String, Object> args = lastCallArgs.get();
        assertNotNull(args);
        assertInstanceOf(Integer.class, args.get("int_param"));
        assertEquals(5, args.get("int_param"));
    }

    @Test
    void callToolIntegerCoercionFractionalDoubleReturnsError() {
        // Pass 5.5 for an integer parameter — should return -32602
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("multi_type", Map.of(
                        "str_param", "x",
                        "int_param", 5.5,
                        "num_param", 1.0,
                        "bool_param", false
                ))));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32602, mcpError.getJsonRpcError().code());
        assertEquals("Parameter 'int_param' must be a whole number, got 5.5", mcpError.getJsonRpcError().message());
    }

    @Test
    void callToolMissingRequiredParameterReturnsError() {
        // Don't pass "message" which is required
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("echo_text", Map.of())));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32602, mcpError.getJsonRpcError().code());
        assertEquals("Missing required parameter 'message'", mcpError.getJsonRpcError().message());
    }

    @Test
    void callToolNullRequiredParameterTreatedAsMissing() {
        // Pass null for required param — treated as missing → -32602
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("message", null);
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("echo_text", args)));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32602, mcpError.getJsonRpcError().code());
        assertEquals("Missing required parameter 'message'", mcpError.getJsonRpcError().message());
    }

    @Test
    void callToolNullOptionalParameterAbsentFromMap() {
        // Pass null for optional param — should be absent from the callArgs map
        lastCallArgs.set(null);
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("required_str", "hello");
        args.put("optional_str", null);
        client.callTool(new McpSchema.CallToolRequest("optional_params", args));
        Map<String, Object> received = lastCallArgs.get();
        assertNotNull(received);
        assertTrue(received.containsKey("required_str"));
        assertFalse(received.containsKey("optional_str"));
    }

    @Test
    void callToolUnknownParameterReturnsIsError() {
        lastCallArgs.set(null);
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("no_params_tool",
                        Map.of("unknown_param", "surprise")));
        assertTrue(Boolean.TRUE.equals(result.isError()));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertTrue(text.text().contains("Unknown parameter 'unknown_param'"));
        assertTrue(text.text().contains("no_params_tool"));
        // Tool should NOT have been invoked
        assertNull(lastCallArgs.get());
    }

    @Test
    void callToolUnknownParameterDidYouMean() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("echo_text",
                        Map.of("mesage", "hello")));
        assertTrue(Boolean.TRUE.equals(result.isError()));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertTrue(text.text().contains("'mesage'"), "should mention the unknown param");
        assertTrue(text.text().contains("did you mean 'message'"), "should suggest closest match");
    }

    @Test
    void callToolUnknownParameterListsValidParameters() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("add_integers",
                        Map.of("x", 1, "y", 2)));
        assertTrue(Boolean.TRUE.equals(result.isError()));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertTrue(text.text().contains("Valid parameters:"));
        assertTrue(text.text().contains("a"));
        assertTrue(text.text().contains("b"));
    }

    @Test
    void callToolNotFoundReturnsError() {
        assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("nonexistent_tool", Map.of())));
    }

    // ===== tools/call — return value handling =====

    @Test
    void callToolTextContent() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("echo_text", Map.of("message", "hello world")));
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("hello world", text.text());
    }

    @Test
    void callToolNullContentReturnsEmptyArray() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("return_null", Map.of()));
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(result.content().isEmpty());
    }

    @Test
    void callToolImageContent() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("return_image", Map.of()));
        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.ImageContent.class, result.content().get(0));
        McpSchema.ImageContent img = (McpSchema.ImageContent) result.content().get(0);
        assertEquals("aW1hZ2VkYXRh", img.data());
        assertEquals("image/png", img.mimeType());
    }

    @Test
    void callToolAudioContent() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("return_audio", Map.of()));
        assertNotNull(result);
        assertEquals(1, result.content().size());
        // Audio content — verify the raw JSON has the expected structure
        // The SDK may deserialize audio as a generic content type
        Object content = result.content().get(0);
        assertNotNull(content);
    }

    @Test
    void callToolResourceContent() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("return_resource", Map.of()));
        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.EmbeddedResource.class, result.content().get(0));
        McpSchema.EmbeddedResource resource = (McpSchema.EmbeddedResource) result.content().get(0);
        assertNotNull(resource.resource());
    }

    @Test
    void callToolExceptionReturnsIsErrorWithMessage() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("throw_exception", Map.of()));
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("java.lang.RuntimeException: something went wrong", text.text());
    }

    @Test
    void callToolParameterIntegration() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("add_integers", Map.of("a", 3, "b", 7)));
        assertNotNull(result);
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
        assertEquals("10", ((McpSchema.TextContent) result.content().get(0)).text());
    }

    // ===== min/max integer parameter =====

    @Test
    void toolWithBoundedIntegerIsAcceptedByClient() {
        McpSchema.ListToolsResult result = client.listTools();
        McpSchema.Tool tool = result.tools().stream()
                .filter(t -> "bounded_integer".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertNotNull(tool.inputSchema());
        assertTrue(tool.inputSchema().properties().containsKey("count"));
    }

    @Test
    void callToolWithBoundedIntegerValidValue() {
        lastCallArgs.set(null);
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("bounded_integer", Map.of("count", 50)));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        Map<String, Object> args = lastCallArgs.get();
        assertNotNull(args);
        assertEquals(50, args.get("count"));
    }

    // ===== enum string parameter =====

    @Test
    void toolWithEnumStringIsAcceptedByClient() {
        McpSchema.ListToolsResult result = client.listTools();
        McpSchema.Tool tool = result.tools().stream()
                .filter(t -> "enum_string".equals(t.name()))
                .findFirst()
                .orElseThrow();
        assertNotNull(tool.inputSchema());
        assertTrue(tool.inputSchema().properties().containsKey("color"));
    }

    @Test
    void callToolWithEnumStringValidValue() {
        lastCallArgs.set(null);
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("enum_string", Map.of("color", "green")));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        Map<String, Object> args = lastCallArgs.get();
        assertNotNull(args);
        assertEquals("green", args.get("color"));
    }

    // ===== array and object parameter types =====

    @Test
    void callToolArrayParamReceivedAsList() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("echo_array",
                        Map.of("items", List.of("a", "b", "c"))));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("[\"a\",\"b\",\"c\"]", text.text());
    }

    @Test
    void callToolObjectParamReceivedAsMap() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("echo_object",
                        Map.of("config", Map.of("key", "value"))));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("{\"key\":\"value\"}", text.text());
    }

    @Test
    void callToolMissingRequiredArrayParamReturnsError() {
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("echo_array", Map.of())));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32602, mcpError.getJsonRpcError().code());
        assertEquals("Missing required parameter 'items'", mcpError.getJsonRpcError().message());
    }

    // ===== MCPServerException handling =====

    @Test
    void callToolMCPServerExceptionReturnsJsonRpcError() {
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("throw_mcp_internal_error", Map.of())));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32603, mcpError.getJsonRpcError().code());
        assertEquals("internal failure", mcpError.getJsonRpcError().message());
    }

    @Test
    void callToolMCPServerExceptionInvalidParamsReturnsJsonRpcError() {
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("throw_mcp_invalid_params",
                        Map.of("value", "test"))));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32602, mcpError.getJsonRpcError().code());
        assertEquals("value must be non-empty", mcpError.getJsonRpcError().message());
    }

    @Test
    void callToolMCPServerExceptionCustomCodeReturnsJsonRpcError() {
        Exception ex = assertThrows(Exception.class, () ->
                client.callTool(new McpSchema.CallToolRequest("throw_mcp_custom_code", Map.of())));
        McpError mcpError = assertInstanceOf(McpError.class, McpError.findRootCause(ex));
        assertEquals(-32000, mcpError.getJsonRpcError().code());
        assertEquals("custom server error", mcpError.getJsonRpcError().message());
    }

    // ===== MCPErrorResponseException handling =====

    @Test
    void callToolMCPErrorResponseExceptionReturnsIsErrorWithCleanMessage() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("throw_mcp_error_response", Map.of()));
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertEquals("clean error message", text.text());
    }

    @Test
    void callToolMCPErrorResponseExceptionDoesNotExposeClassName() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("throw_mcp_error_response", Map.of()));
        McpSchema.TextContent text = (McpSchema.TextContent) result.content().get(0);
        assertFalse(text.text().contains("Exception"),
                "error message must not contain any Java class name");
    }

    @Test
    void regularExceptionStillReturnsIsErrorToolResult() {
        // Verify that non-MCPServerException still produces isError tool result, not JSON-RPC error
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("throw_exception", Map.of()));
        assertNotNull(result);
        assertTrue(Boolean.TRUE.equals(result.isError()));
        assertEquals(1, result.content().size());
        assertInstanceOf(McpSchema.TextContent.class, result.content().get(0));
    }

}
