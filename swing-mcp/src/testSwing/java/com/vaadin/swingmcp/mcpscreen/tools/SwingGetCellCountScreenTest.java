package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetCellCountTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetCellCountScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetCellCountTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetCellCountTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private String getCount(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> tool.execute(new Parameters(Map.of("ref", ref)), context));
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTableInsideJFrame_returnsRedirectError() throws Exception {
        JFrame frame = new JFrame("Test");
        JTable table = new JTable(new DefaultTableModel(10, 3));
        frame.getContentPane().add(table);

        snapshot(frame);
        int ref = context.getRefOf(table);
        Exception ex = assertThrows(Exception.class, () -> getCount(ref));
        Throwable cause = ex instanceof MCPErrorResponseException ? ex : ex.getCause();
        assertTrue(cause instanceof MCPErrorResponseException,
                "Expected MCPErrorResponseException, got: " + ex);
        assertEquals(
                "JTable does not support swing_get_cell_count. Table cells are plain text labels \u2014 "
                        + "use swing_get_item_count to page through rows.",
                cause.getMessage());
    }

    @Test
    void jListInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        frame.getContentPane().add(list);

        snapshot(frame);
        assertEquals("20", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jListInsideJDialog() throws Exception {
        JDialog dialog = new JDialog((Frame) null, "Test");
        String[] items = new String[15];
        for (int i = 0; i < 15; i++) items[i] = "Dialog-" + i;
        JList<String> list = new JList<>(items);
        dialog.getContentPane().add(list);

        snapshot(dialog);
        assertEquals("15", getCount(context.getRefOf(list)));
    }
}
