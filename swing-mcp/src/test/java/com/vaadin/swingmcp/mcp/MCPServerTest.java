package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MCPServerTest extends AbstractHeadlessTest {

    @Test
    void smokeTestStartAndStop() throws Exception {
        MCPServer s = new MCPServer(18091, "/mcp");
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
