package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetSelectionTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getSelection(int ref) throws Exception {
        MCPProtocol.Content result = getSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_singleSelectedItem() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}", json);
    }

    @Test
    void jList_noSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":0,\"selected\":[]}", json);
    }

    @Test
    void jList_multipleSelectedItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma", "Delta"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(new int[]{0, 2, 3});
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":3,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alpha\"},"
                        + "{\"index\":2,\"name\":\"Gamma\"},"
                        + "{\"index\":3,\"name\":\"Delta\"}"
                        + "]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_selectedTab() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("General", new JPanel());
        tp.addTab("Advanced", new JPanel());
        tp.setSelectedIndex(1);
        snapshot(tp);
        String json = getSelection(context.getRefOf(tp));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Advanced\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBox_selectedItem() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(2);
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Blue\"}]}", json);
    }

    @Test
    void jComboBox_empty() throws Exception {
        JComboBox<String> combo = new JComboBox<>();
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":0,\"selected\":[]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_singleRowSelected() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(1, 1);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Bob | 25\"}]}",
                json);
    }

    @Test
    void jTable_multipleRowsSelected() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        table.addRowSelectionInterval(2, 2);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":2,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alice | 30\"},"
                        + "{\"index\":2,\"name\":\"Carol | 35\"}"
                        + "]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — unsupported modes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_columnSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        snapshot(table);
        // JTable in column mode has no selection action, but may still have a ref
        // if truncated. Force a ref for testing the error path.
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    @Test
    void jTable_cellSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    @Test
    void jTable_noSelectionAllowed_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> getSelection(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutSelectionSupport_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"),
                "Expected generic error, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void jTree_returnsError() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        snapshot(tree);
        // JTree itself has no ref (selection suppressed). Force a ref.
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"),
                "Expected generic error for JTree, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read-only / ref preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapPreservedAfterGetSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSelectedIndex(0);
        snapshot(list);
        int ref = context.getRefOf(list);

        // Call twice — ref map should not be cleared (read-only tool)
        String json1 = getSelection(ref);
        String json2 = getSelection(ref);
        assertEquals(json1, json2);
    }

    @Test
    void disabledList_selectionStillReadable() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setEnabled(false);
        list.setSelectedIndex(1);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"B\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Truncation (BR-09)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncation_whenSelectionExceedsMax() throws Exception {
        // Create a JList with more items than MAX_SELECTION_ITEMS and select all
        int count = SwingGetSelectionTool.MAX_SELECTION_ITEMS + 5;
        String[] items = new String[count];
        int[] allIndices = new int[count];
        for (int i = 0; i < count; i++) {
            items[i] = "Item" + i;
            allIndices[i] = i;
        }
        JList<String> list = new JList<>(items);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(allIndices);
        snapshot(list);

        String json = getSelection(context.getRefOf(list));
        assertTrue(json.contains("\"truncated\":true"),
                "Response should contain truncated:true, got: " + json);
        assertTrue(json.contains("\"selectedCount\":" + SwingGetSelectionTool.MAX_SELECTION_ITEMS),
                "selectedCount should be capped at MAX_SELECTION_ITEMS, got: " + json);
    }

    @Test
    void noTruncation_whenSelectionWithinMax() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(new int[]{0, 1, 2});
        snapshot(list);

        String json = getSelection(context.getRefOf(list));
        assertFalse(json.contains("truncated"),
                "Response should not contain truncated field, got: " + json);
        assertTrue(json.contains("\"selectedCount\":3"), "selectedCount should be 3");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Index identity verification
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_indexMatchesAddAccessibleSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(2);
        snapshot(list);

        // Verify the index from get_selection matches isAccessibleChildSelected
        AccessibleContext ac = list.getAccessibleContext();
        assertTrue(ac.getAccessibleSelection().isAccessibleChildSelected(2));

        // Clear and re-select using addAccessibleSelection with the same index
        ac.getAccessibleSelection().clearAccessibleSelection();
        ac.getAccessibleSelection().addAccessibleSelection(2);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Gamma\"}]}", json);
    }

    @Test
    void jComboBox_indexMatchesAddAccessibleSelection() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(1);
        snapshot(combo);

        AccessibleContext ac = combo.getAccessibleContext();
        // Verify item index round-trips through addAccessibleSelection
        ac.getAccessibleSelection().addAccessibleSelection(0);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"Red\"}]}", json);
    }

    @Test
    void jTabbedPane_indexMatchesAddAccessibleSelection() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab0", new JPanel());
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.setSelectedIndex(0);
        snapshot(tp);

        // Select tab 2 via AccessibleSelection API
        tp.getAccessibleContext().getAccessibleSelection().addAccessibleSelection(2);
        String json = getSelection(context.getRefOf(tp));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Tab2\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectedIndex(0);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertTrue(json.contains("\"selectedCount\":1"));
        assertTrue(json.contains("\"name\":\"A\""));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("First", new JPanel());
        tp.addTab("Second", new JPanel());
        snapshot(tp);
        String json = getSelection(context.getRefOf(tp));
        assertTrue(json.contains("\"selectedCount\":1"));
        assertTrue(json.contains("\"name\":\"First\""));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y"});
        combo.setSelectedIndex(0);
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertTrue(json.contains("\"selectedCount\":1"));
        assertTrue(json.contains("\"name\":\"X\""));
    }

    @Test
    void componentMatrix_JTable() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Col1", "Col2"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertTrue(json.contains("\"selectedCount\":1"));
        assertTrue(json.contains("\"name\":\"A | 1\""));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertGetSelectionNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // No ref — cannot call get_selection, acceptable
            return;
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection")
                        || ex.getMessage().contains("row-selection mode"),
                "Expected not-supported error for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertGetSelectionNotSupported(new JButton("OK"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertGetSelectionNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertGetSelectionNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertGetSelectionNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertGetSelectionNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertGetSelectionNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertGetSelectionNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertGetSelectionNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertGetSelectionNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertGetSelectionNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertGetSelectionNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertGetSelectionNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertGetSelectionNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertGetSelectionNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // DR-012: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(item);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton btn = new JButton("Tool");
        tb.add(btn);
        snapshot(tb);
        int ref = context.getRefOf(btn);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        snapshot(tree);
        // JTree has no ref (selection suppressed). Force a ref for testing.
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> getSelection(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> getSelection(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetSelectionViaMcpClient() {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_get_selection", Map.of("ref", 1)));

        assertNotEquals(Boolean.TRUE, result.isError(), "get_selection should succeed");
        assertFalse(result.content().isEmpty(), "Result should have content");
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}",
                json);
    }
}
