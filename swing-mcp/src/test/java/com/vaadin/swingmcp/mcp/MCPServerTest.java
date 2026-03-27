package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MCPServerTest extends AbstractHeadlessTest {

    private static final int TEST_PORT = 18090;
    private static MCPServer server;
    private static McpSyncClient client;

    @BeforeAll
    static void startServer() throws Exception {
        server = new MCPServer(TEST_PORT, "/mcp");
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
    void smokeTestStartAndStop() throws Exception {
        MCPServer s = new MCPServer(18091, "/mcp");
        s.start();
        s.stop();
    }

    @Test
    void pingAndListTools() {
        client.initialize();
        assertDoesNotThrow(() -> client.ping());

        McpSchema.ListToolsResult tools = client.listTools();
        assertNotNull(tools);
        assertNotNull(tools.tools());
    }
}
