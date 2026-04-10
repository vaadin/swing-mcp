package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetSelectableItemsTool;
import com.vaadin.swingmcp.mcp.tools.SwingSetTextTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.mcpscreen.JFilterableComboBox;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetSelectableItemsScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetSelectableItemsTool tool;
    private SwingSetTextTool setTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetSelectableItemsTool();
        setTextTool = new SwingSetTextTool();
        context = new SwingToolContext();
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
        return result.getText();
    }

    private String getItems(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> tool.execute(new Parameters(Map.of("ref", ref, "offset", offset, "length", length)), context));
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
    void readJListItemsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        frame.getContentPane().add(list);

        snapshot(frame);
        String json = getItems(context.getRefOf(list), 0, 3);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"Alpha\"},"
                + "{\"index\":1,\"name\":\"Beta\"},"
                + "{\"index\":2,\"name\":\"Gamma\"}"
                + "]}", json);
    }

    @Test
    void readJTabbedPaneItemsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.setEnabledAt(1, false);
        frame.getContentPane().add(tp);

        snapshot(frame);
        String json = getItems(context.getRefOf(tp), 0, 2);
        assertEquals("{\"totalCount\":2,\"items\":["
                + "{\"index\":0,\"name\":\"Tab1\"},"
                + "{\"index\":1,\"name\":\"Tab2\",\"enabled\":false}"
                + "]}", json);
    }

    @Test
    void readJComboBoxItemsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        frame.getContentPane().add(combo);

        snapshot(frame);
        String json = getItems(context.getRefOf(combo), 0, 3);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"Red\"},"
                + "{\"index\":1,\"name\":\"Green\"},"
                + "{\"index\":2,\"name\":\"Blue\"}"
                + "]}", json);
    }

    @Test
    void readJTableRowsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        String json = getItems(context.getRefOf(table), 0, 2);
        assertEquals("{\"totalCount\":2,\"items\":["
                + "{\"index\":0,\"name\":\"Alice | 30\"},"
                + "{\"index\":1,\"name\":\"Bob | 25\"}"
                + "]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Editable (filterable) JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void filterableComboBoxShowsAllItemsBeforeFiltering() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma", "Alphabet");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            String json = getItems(context.getRefOf(combo), 0, 10);
            assertEquals("{\"totalCount\":4,\"items\":["
                    + "{\"index\":0,\"name\":\"Alpha\"},"
                    + "{\"index\":1,\"name\":\"Beta\"},"
                    + "{\"index\":2,\"name\":\"Gamma\"},"
                    + "{\"index\":3,\"name\":\"Alphabet\"}"
                    + "]}", json);
        } finally {
            frame.dispose();
        }
    }

    @Test
    void filterableComboBoxFiltersItemsAfterSetText() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma", "Alphabet");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            // Step 1: snapshot to discover combo and its editor child
            String snap = snapshot(frame);
            assertTrue(snap.contains("combo_box"), "snapshot must contain the combo_box");
            assertTrue(snap.contains("text"), "snapshot must contain the editor text child");

            // Step 2: find the editor text ref and type a filter prefix
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            int editorRef = context.getRefOf(editor);
            setText(editorRef, "Al");

            // Step 3: re-snapshot (refs may have changed after mutation)
            snapshot(frame);

            // Step 4: verify filtered items via get_selectable_items on the combo ref
            assertEquals("Al", combo.getFilterText());
            String json = getItems(context.getRefOf(combo), 0, 10);
            assertEquals("{\"totalCount\":2,\"items\":["
                    + "{\"index\":0,\"name\":\"Alpha\"},"
                    + "{\"index\":1,\"name\":\"Alphabet\"}"
                    + "]}", json);
        } finally {
            frame.dispose();
        }
    }

    @Test
    void filterableComboBoxEmptyFilterRestoresAllItems() throws Exception {
        JFrame frame = new JFrame("Test");
        JFilterableComboBox combo = new JFilterableComboBox("Alpha", "Beta", "Gamma");
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            // Filter down
            snapshot(frame);
            JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
            setText(context.getRefOf(editor), "B");
            snapshot(frame);
            assertEquals("{\"totalCount\":1,\"items\":["
                    + "{\"index\":0,\"name\":\"Beta\"}"
                    + "]}", getItems(context.getRefOf(combo), 0, 10));

            // Clear filter — all items should reappear
            setText(context.getRefOf(editor), "");
            snapshot(frame);
            assertEquals("{\"totalCount\":3,\"items\":["
                    + "{\"index\":0,\"name\":\"Alpha\"},"
                    + "{\"index\":1,\"name\":\"Beta\"},"
                    + "{\"index\":2,\"name\":\"Gamma\"}"
                    + "]}", getItems(context.getRefOf(combo), 0, 10));
        } finally {
            frame.dispose();
        }
    }

    @Test
    void filterableComboBoxNoMatchReturnsEmpty() throws Exception {
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
            assertEquals("{\"totalCount\":0,\"items\":[]}", getItems(context.getRefOf(combo), 0, 10));
        } finally {
            frame.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJListItemsInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JList<String> list = new JList<>(new String[]{"X", "Y", "Z"});
        dialog.getContentPane().add(list);

        snapshot(dialog);
        String json = getItems(context.getRefOf(list), 0, 3);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"X\"},"
                + "{\"index\":1,\"name\":\"Y\"},"
                + "{\"index\":2,\"name\":\"Z\"}"
                + "]}", json);
    }
}
