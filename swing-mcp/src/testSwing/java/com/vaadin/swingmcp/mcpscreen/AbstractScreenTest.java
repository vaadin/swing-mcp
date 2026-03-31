package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.FakeMCPServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.swing.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractScreenTest {
    @BeforeAll
    public static void assertScreenPresent() {
        assertEquals("false", System.getProperty("java.awt.headless"));
        new JFrame(); // this fails on headless
    }

    private static final int MCP_PORT = 18090;
    protected static FakeMCPServer mcpServer;
    protected static McpSyncClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        mcpServer = new FakeMCPServer(MCP_PORT, "/mcp");
        mcpServer.start();

        Duration timeout = Duration.ofSeconds(5);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + MCP_PORT + "/mcp")
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