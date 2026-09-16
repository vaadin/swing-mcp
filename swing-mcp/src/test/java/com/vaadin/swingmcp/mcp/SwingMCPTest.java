package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void listTools() throws Exception {
        mcpClient.initialize();

        List<MCPProtocol.Tool> tools = mcpClient.listTools();
        assertNotNull(tools);
        assertFalse(tools.isEmpty());
    }
}
