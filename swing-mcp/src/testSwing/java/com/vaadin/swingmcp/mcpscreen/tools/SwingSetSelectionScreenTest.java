package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetSelectionTool;
import com.vaadin.swingmcp.mcp.tools.SwingSetSelectionTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetSelectionScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSetSelectionTool setSelectionTool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        setSelectionTool = new SwingSetSelectionTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void setSelection(int ref, List<Object> indices) throws Exception {
        executeOnEDT(() -> {
            setSelectionTool.execute(
                    new Parameters(Map.of("ref", ref, "indices", indices)), context);
            return null;
        });
        // drain EDT so fire-and-forget action has run
        SwingUtilities.invokeAndWait(() -> {});
    }

    private String getSelection(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> getSelectionTool.execute(new Parameters(Map.of("ref", ref)), context));
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setJListSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        frame.getContentPane().add(list);

        snapshot(frame);
        setSelection(context.getRefOf(list), List.of(1.0));
        assertEquals(1, list.getSelectedIndex());
    }

    @Test
    void setJTabbedPaneSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        frame.getContentPane().add(tp);

        snapshot(frame);
        setSelection(context.getRefOf(tp), List.of(1.0));
        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    void setJComboBoxSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        frame.getContentPane().add(combo);

        snapshot(frame);
        setSelection(context.getRefOf(combo), List.of(2.0));
        assertEquals(2, combo.getSelectedIndex());
    }

    @Test
    void setJTableRowSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        setSelection(context.getRefOf(table), List.of(1.0));
        assertArrayEquals(new int[]{1}, table.getSelectedRows());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setJListSelectionInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JList<String> list = new JList<>(new String[]{"X", "Y", "Z"});
        dialog.getContentPane().add(list);

        snapshot(dialog);
        setSelection(context.getRefOf(list), List.of(2.0));
        assertEquals(2, list.getSelectedIndex());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable round-trip (requires screen — addAccessibleSelection is no-op headless)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void roundTrip_jTable() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        setSelection(context.getRefOf(table), List.of(0.0, 2.0));

        // Re-snapshot to get fresh refs
        snapshot(frame);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":2,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alice, 30\"},"
                        + "{\"index\":2,\"name\":\"Carol, 35\"}"
                        + "]}",
                json);
    }
}
