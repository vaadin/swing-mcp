package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingClearSelectionTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClearSelectionTool clearSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clearSelectionTool = new SwingClearSelectionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(java.awt.Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void clearSelection(int ref) throws Exception {
        clearSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
    }

    @Test
    void jList_clearSelection_deselectsItem() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        assertEquals(1, list.getSelectedIndex());

        snapshot(list);
        int ref = context.getRefOf(list);
        clearSelection(ref);

        assertEquals(-1, list.getSelectedIndex());
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    void jList_insideJInternalFrame_clearSelection_deselectsItem() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        iframe.getContentPane().add(list);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        JPanel root = new JPanel();
        root.add(desktop);

        snapshot(root);
        int ref = context.getRefOf(list);
        clearSelection(ref);

        assertEquals(-1, list.getSelectedIndex());
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    void successEchoUsesClearSelectionActionName() throws Exception {
        // the wrapper composes the echo from this tool's MCP-exposed name
        // (clear-selection), not from the delegated SwingSetSelectionTool (set-selection).
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPProtocol.Content result = clearSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched clear-selection on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void clearSelectionViaMcpClient() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(2);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_clear_selection", Map.of("ref", 1)));

        assertNotEquals(Boolean.TRUE, result.isError(), "clear_selection should succeed");
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
        assertEquals(-1, list.getSelectedIndex());
    }
}
