package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
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

        client = buildSyncClient("http://127.0.0.1:" + TEST_PORT + "/mcp", Duration.ofSeconds(5));
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
