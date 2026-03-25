package com.vaadin.swingmcp.tinymcpserver;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TinyMCPServerTest {

    private static final int TEST_PORT = 18089;
    private static TinyMCPServer server;
    private static McpSyncClient client;

    @BeforeAll
    static void startServer() throws Exception {
        server = new TinyMCPServer(TEST_PORT, "/mcp");
        server.start();

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + TEST_PORT + "/mcp")
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
        assertEquals("Swing MCP", result.serverInfo().name());
        assertEquals("0.0.1", result.serverInfo().version());
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
}
