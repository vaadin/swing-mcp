package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetCellsTool;
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

class SwingGetCellsScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetCellsTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetCellsTool();
        context = new SwingToolContext();
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
        return result.getText();
    }

    private String getCells(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> tool.execute(new Parameters(Map.of("ref", ref, "offset", offset, "length", length)), context));
        return result == null ? null : result.getText();
    }

    private JTable createTable(int rows, int cols) {
        DefaultTableModel model = new DefaultTableModel(rows, cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                model.setValueAt("r" + r + "c" + c, r, c);
            }
        }
        return new JTable(model);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jtableInsideJFrame_returnsRedirectError() throws Exception {
        JFrame frame = new JFrame("Test");
        JTable table = createTable(15, 1);
        frame.getContentPane().add(table);

        snapshot(frame);
        int ref = context.getRefOf(table);
        Exception ex = assertThrows(Exception.class, () -> getCells(ref, 0, 5));
        Throwable cause = ex instanceof MCPErrorResponseException ? ex : ex.getCause();
        assertTrue(cause instanceof MCPErrorResponseException,
                "Expected MCPErrorResponseException, got: " + ex);
        assertEquals(
                "JTable does not support swing_get_cells. Table cells are plain text labels \u2014 "
                        + "use swing_get_items to page through rows.",
                cause.getMessage());
    }

    @Test
    void truncatedJListInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        frame.getContentPane().add(list);

        snapshot(frame);
        String output = getCells(context.getRefOf(list), 0, 3);
        assertTrue(output.contains("Showing 3 children from offset 0 (total 20) for list [ref=1]"),
                "Should show header, got: " + output);
        assertTrue(output.contains("\"Item-0\""), "Should contain Item-0, got: " + output);
        assertTrue(output.contains("\"Item-2\""), "Should contain Item-2, got: " + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncatedJListInsideJDialog() throws Exception {
        JDialog dialog = new JDialog((Frame) null, "Test");
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        dialog.getContentPane().add(list);

        snapshot(dialog);
        String output = getCells(context.getRefOf(list), 5, 5);
        assertTrue(output.contains("Showing 5 children from offset 5 (total 20) for list [ref=1]"),
                "Should show header, got: " + output);
        assertTrue(output.contains("\"Item-5\""), "Should contain Item-5, got: " + output);
        assertTrue(output.contains("\"Item-9\""), "Should contain Item-9, got: " + output);
    }
}
