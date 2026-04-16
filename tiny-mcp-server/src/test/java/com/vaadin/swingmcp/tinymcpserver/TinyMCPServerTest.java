package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TinyMCPServerTest {

    private static TinyMCPServer server;
    private static McpSyncClient client;

    @BeforeAll
    static void startServer() throws Exception {
        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName("Test Server");
        serverInfo.setVersion("1.0");
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        server = new TinyMCPServer(0, "/mcp", serverInfo);
        server.start();

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(server.getUrl())
                .openConnectionOnStartup(false)
                .build();

        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .build();
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

    @Test
    void initializeAndConnect() {
        McpSchema.InitializeResult result = client.initialize();
        assertNotNull(result);
        assertEquals("Test Server", result.serverInfo().name());
        assertEquals("1.0", result.serverInfo().version());
        assertTrue(client.isInitialized());
    }

    @Test
    void listToolsReturnsEmpty() {
        client.initialize();
        McpSchema.ListToolsResult tools = client.listTools();
        assertNotNull(tools);
        assertTrue(tools.tools().isEmpty());
    }

    @Test
    void listResourcesReturnsEmpty() {
        client.initialize();
        McpSchema.ListResourcesResult resources = client.listResources();
        assertNotNull(resources);
        assertTrue(resources.resources().isEmpty());
    }

    @Test
    void listPromptsReturnsEmpty() {
        client.initialize();
        McpSchema.ListPromptsResult prompts = client.listPrompts();
        assertNotNull(prompts);
        assertTrue(prompts.prompts().isEmpty());
    }

    @Test
    void pingSucceeds() {
        client.initialize();
        assertDoesNotThrow(() -> client.ping());
    }

    // ===== addTool() validation tests =====

    @Test
    void addToolRejectsNullName() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool(null, "desc", new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void addToolRejectsBlankName() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("  ", "desc", new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void addToolRejectsInvalidName() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("1tool", "desc", new InputSchemaBuilder().build(), params -> null));
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my-tool", "desc", new InputSchemaBuilder().build(), params -> null));
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my tool", "desc", new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void addToolRejectsNullDescription() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my_tool", null, new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void addToolRejectsBlankDescription() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my_tool", "  ", new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void addToolRejectsNullInputSchema() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my_tool", "desc", null, params -> null));
    }

    @Test
    void addToolRejectsNullFunction() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        assertThrows(IllegalArgumentException.class, () ->
                s.addTool("my_tool", "desc", new InputSchemaBuilder().build(), null));
    }

    @Test
    void addToolAfterStartThrows() throws Exception {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        s.start();
        try {
            assertThrows(IllegalStateException.class, () ->
                    s.addTool("my_tool", "desc", new InputSchemaBuilder().build(), params -> null));
        } finally {
            s.stop();
        }
    }

    @Test
    void addToolDuplicateNameThrows() {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        s.addTool("my_tool", "desc", new InputSchemaBuilder().build(), params -> null);
        assertThrows(IllegalStateException.class, () ->
                s.addTool("my_tool", "other desc", new InputSchemaBuilder().build(), params -> null));
    }

    @Test
    void malformedJsonReturnsJsonRpcParseError() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{not valid json"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());

        JsonObject body = MCPProtocol.fromJson(response.body(), JsonObject.class);
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertTrue(body.get("id").isJsonNull());
        JsonObject error = body.getAsJsonObject("error");
        assertEquals(-32700, error.get("code").getAsInt());
        assertEquals("Parse error", error.get("message").getAsString());
    }

    @Test
    void batchRequestReturnsInvalidRequestError() throws Exception {
        // A JSON-RPC batch is a JSON array — valid JSON, but we don't support it.
        // Must return -32600 (Invalid Request), not -32700 (Parse error).
        String batchBody = "[{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":1},"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":2}]";
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(server.getUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(batchBody))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());

        JsonObject body = MCPProtocol.fromJson(response.body(), JsonObject.class);
        assertEquals("2.0", body.get("jsonrpc").getAsString());
        assertTrue(body.get("id").isJsonNull());
        JsonObject error = body.getAsJsonObject("error");
        assertEquals(-32600, error.get("code").getAsInt());
        assertEquals("Batch requests are not supported", error.get("message").getAsString());
    }
}
