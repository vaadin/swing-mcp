package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwingMCPTest extends AbstractHeadlessTest {

    @Test
    void smokeTestStartAndStop() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        SwingMCP s = new SwingMCP(0, "/mcp");
        s.start();
        s.stop();
    }

    @Test
    void pingAndListTools() {
        mcpClient.initialize();
        assertDoesNotThrow(() -> mcpClient.ping());

        McpSchema.ListToolsResult tools = mcpClient.listTools();
        assertNotNull(tools);
        assertNotNull(tools.tools());
    }
}
