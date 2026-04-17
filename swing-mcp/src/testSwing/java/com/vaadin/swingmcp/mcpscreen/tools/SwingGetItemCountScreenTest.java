package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetItemCountTool;
import com.vaadin.swingmcp.mcp.tools.SwingSetTextTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.mcpscreen.JFilterableComboBox;
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

class SwingGetItemCountScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetItemCountTool tool;
    private SwingSetTextTool setTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetItemCountTool();
        setTextTool = new SwingSetTextTool();
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

    private void setText(int ref, String text) throws Exception {
        executeOnEDT(() -> setTextTool.execute(new Parameters(Map.of("ref", ref, "text", text)), context));
        executeOnEDT(() -> null); // drain EDT: setTextContents fires
        executeOnEDT(() -> null); // drain EDT: deferred filter (invokeLater) fires
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jListInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        frame.getContentPane().add(list);

        snapshot(frame);
        assertEquals("3", getCount(context.getRefOf(list)));
    }

    @Test
    void jTabbedPaneInsideJFrame_isRejected() throws Exception {
        // Regression guard for P-001 Wave A — JTabbedPane dropped as a
        // supported target; tab count is derivable from the snapshot (T-002 SC-2).
        JFrame frame = new JFrame("Test");
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        frame.getContentPane().add(tp);

        snapshot(frame);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_item_count"),
                "Expected not-supported error for JTabbedPane, got: " + ex.getMessage());
    }

    @Test
    void jComboBoxInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        frame.getContentPane().add(combo);

        snapshot(frame);
        assertEquals("3", getCount(context.getRefOf(combo)));
    }

    @Test
    void jTableInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        assertEquals("2", getCount(context.getRefOf(table)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Editable (filterable) JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void filterableComboBoxCountBeforeFiltering() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma", "Alphabet");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            assertEquals("4", getCount(context.getRefOf(combo)));
        } finally {
            frame.dispose();
        }
    }

    @Test
    void filterableComboBoxCountAfterSetText() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma", "Alphabet");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            setText(context.getRefOf(editor), "Al");
            snapshot(frame);
            assertEquals("2", getCount(context.getRefOf(combo)));
        } finally {
            frame.dispose();
        }
    }

    @Test
    void filterableComboBoxCountNoMatch() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            setText(context.getRefOf(editor), "ZZZ");
            snapshot(frame);
            assertEquals("0", getCount(context.getRefOf(combo)));
        } finally {
            frame.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jListInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        iframe.getContentPane().add(list);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        assertEquals("3", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jListInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JList<String> list = new JList<>(new String[]{"X", "Y", "Z"});
        dialog.getContentPane().add(list);

        snapshot(dialog);
        assertEquals("3", getCount(context.getRefOf(list)));
    }
}
