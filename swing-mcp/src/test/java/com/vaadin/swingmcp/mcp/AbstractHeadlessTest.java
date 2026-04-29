package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    protected static FakeSwingMCP mcpServer;
    protected static McpSyncClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new FakeSwingMCP(0, "/mcp", false);
        mcpServer.start();

        Duration timeout = Duration.ofSeconds(5);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(mcpServer.getUrl())
                .openConnectionOnStartup(false)
                .build();
        mcpClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        mcpClient.initialize();
    }

    @AfterAll
    static void stopMcpServer() {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (mcpServer != null) {
            mcpServer.stop();
        }
    }
}
