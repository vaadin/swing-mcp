package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetSelectableItemsTool;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetSelectableItemsScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetSelectableItemsTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetSelectableItemsTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private String getItems(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> tool.execute(new Parameters(Map.of("ref", ref, "offset", offset, "length", length)), context));
        return result == null ? null : result.getText();
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
                + "{\"index\":0,\"name\":\"Alice, 30\"},"
                + "{\"index\":1,\"name\":\"Bob, 25\"}"
                + "]}", json);
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
