package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.time.Duration;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void enableHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    private static final int MCP_PORT = 18090;
    protected static FakeMCPServer mcpServer;
    protected static McpSyncClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        mcpServer = new FakeMCPServer(MCP_PORT, "/mcp");
        mcpServer.start();

        mcpClient = buildSyncClient("http://127.0.0.1:" + MCP_PORT + "/mcp", Duration.ofSeconds(5));
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

    protected static McpSyncClient buildSyncClient(String url, Duration timeout) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(url)
                .openConnectionOnStartup(false)
                .build();
        return McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
    }
}
