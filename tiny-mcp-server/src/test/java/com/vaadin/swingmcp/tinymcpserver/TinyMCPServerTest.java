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

    // ===== addTool() guard (validation tests are in MCPToolHandlerTest) =====

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
    void addResourceAfterStartThrows() throws Exception {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        s.start();
        try {
            assertThrows(IllegalStateException.class, () ->
                    s.addResource("file://x", "x", "desc", "text/plain",
                            uri -> java.util.List.of(
                                    MCPProtocol.ResourceContents.text(uri, "text/plain", "x"))));
        } finally {
            s.stop();
        }
    }

    @Test
    void resourcesListAndRead() throws Exception {
        TinyMCPServer s = new TinyMCPServer(0, "/mcp");
        s.addResource("file://greeting", "Greeting", "A friendly greeting", "text/plain",
                uri -> java.util.List.of(
                        MCPProtocol.ResourceContents.text(uri, "text/plain", "Hello, world!")));
        s.start();
        try {
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                    .builder(s.getUrl())
                    .openConnectionOnStartup(false)
                    .build();
            try (McpSyncClient c = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(5))
                    .initializationTimeout(Duration.ofSeconds(5))
                    .build()) {
                c.initialize();

                McpSchema.ListResourcesResult listed = c.listResources();
                assertEquals(1, listed.resources().size());
                McpSchema.Resource r = listed.resources().get(0);
                assertEquals("file://greeting", r.uri());
                assertEquals("Greeting", r.name());
                assertEquals("A friendly greeting", r.description());
                assertEquals("text/plain", r.mimeType());

                McpSchema.ReadResourceResult read = c.readResource(
                        new McpSchema.ReadResourceRequest("file://greeting"));
                assertEquals(1, read.contents().size());
                McpSchema.ResourceContents contents = read.contents().get(0);
                assertInstanceOf(McpSchema.TextResourceContents.class, contents);
                McpSchema.TextResourceContents text = (McpSchema.TextResourceContents) contents;
                assertEquals("file://greeting", text.uri());
                assertEquals("text/plain", text.mimeType());
                assertEquals("Hello, world!", text.text());
            }
        } finally {
            s.stop();
        }
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
