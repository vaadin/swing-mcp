package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    protected static FakeSwingMCP mcpServer;
    protected static MCPClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new FakeSwingMCP(0, "/mcp", false);
        mcpServer.start();

        mcpClient = new TinyMCPClient(URI.create(mcpServer.getUrl()));
        mcpClient.initialize();
    }

    @AfterAll
    static void stopMcpServer() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (mcpServer != null) {
            mcpServer.stop();
        }
    }
}
