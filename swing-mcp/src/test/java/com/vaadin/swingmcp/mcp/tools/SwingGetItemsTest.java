package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetItemsTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetItemsTool tool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetItemsTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getItems(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("ref", ref, "offset", offset, "length", length)),
                context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_allItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma", "Delta", "Echo"});
        snapshot(list);
        String json = getItems(context.getRefOf(list), 0, 5);
        assertEquals("{\"totalCount\":5,\"items\":["
                + "{\"index\":0,\"name\":\"Alpha\"},"
                + "{\"index\":1,\"name\":\"Beta\"},"
                + "{\"index\":2,\"name\":\"Gamma\"},"
                + "{\"index\":3,\"name\":\"Delta\"},"
                + "{\"index\":4,\"name\":\"Echo\"}"
                + "]}", json);
    }

    @Test
    void jList_200items_firstPage() throws Exception {
        String[] items = new String[200];
        for (int i = 0; i < 200; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String json = getItems(context.getRefOf(list), 0, 50);
        assertTrue(json.startsWith("{\"totalCount\":200,\"items\":["));
        assertTrue(json.contains("{\"index\":0,\"name\":\"Item-0\"}"));
        assertTrue(json.contains("{\"index\":49,\"name\":\"Item-49\"}"));
        assertFalse(json.contains("\"index\":50"));
    }

    @Test
    void jList_200items_secondPage() throws Exception {
        String[] items = new String[200];
        for (int i = 0; i < 200; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String json = getItems(context.getRefOf(list), 50, 50);
        assertTrue(json.startsWith("{\"totalCount\":200,\"items\":["));
        assertTrue(json.contains("{\"index\":50,\"name\":\"Item-50\"}"));
        assertTrue(json.contains("{\"index\":99,\"name\":\"Item-99\"}"));
        assertFalse(json.contains("\"index\":100"));
    }

    @Test
    void jList_offsetBeyondCount_emptyItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        String json = getItems(context.getRefOf(list), 10, 5);
        assertEquals("{\"totalCount\":3,\"items\":[]}", json);
    }

    @Test
    void jList_lengthZero_emptyItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        String json = getItems(context.getRefOf(list), 0, 0);
        assertEquals("{\"totalCount\":3,\"items\":[]}", json);
    }

    @Test
    void jList_lengthExceedsRemaining() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        // Request more items than available from offset 1
        String json = getItems(context.getRefOf(list), 1, 100);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":1,\"name\":\"B\"},"
                + "{\"index\":2,\"name\":\"C\"}"
                + "]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane — dropped as a supported target per P-001 Wave A.
    // The three former positive tests (all-tabs, disabled-tab, enabled-tab)
    // are replaced by a single regression guard below + componentMatrix_JTabbedPane.
    // Tabs are now read inline from the snapshot per UC-002 SC-2.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_isRejected() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab0", new JPanel());
        tp.addTab("Tab1", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(ref, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"),
                "Expected not-supported error for JTabbedPane, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBox_allItems() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        snapshot(combo);
        String json = getItems(context.getRefOf(combo), 0, 3);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"Red\"},"
                + "{\"index\":1,\"name\":\"Green\"},"
                + "{\"index\":2,\"name\":\"Blue\"}"
                + "]}", json);
    }

    @Test
    void jComboBox_empty() throws Exception {
        JComboBox<String> combo = new JComboBox<>();
        snapshot(combo);
        String json = getItems(context.getRefOf(combo), 0, 10);
        assertEquals("{\"totalCount\":0,\"items\":[]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_allRows() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        snapshot(table);
        String json = getItems(context.getRefOf(table), 0, 3);
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"Alice | 30\"},"
                + "{\"index\":1,\"name\":\"Bob | 25\"},"
                + "{\"index\":2,\"name\":\"Carol | 35\"}"
                + "]}", json);
    }

    @Test
    void jTable_paging() throws Exception {
        Object[][] data = new Object[100][2];
        for (int i = 0; i < 100; i++) {
            data[i] = new Object[]{"Name-" + i, String.valueOf(i)};
        }
        JTable table = new JTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}));
        snapshot(table);
        String json = getItems(context.getRefOf(table), 10, 5);
        assertTrue(json.startsWith("{\"totalCount\":100,\"items\":["));
        assertTrue(json.contains("{\"index\":10,\"name\":\"Name-10 | 10\"}"));
        assertTrue(json.contains("{\"index\":14,\"name\":\"Name-14 | 14\"}"));
        assertFalse(json.contains("\"index\":15"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — all selection modes succeed (UC-017 BR-03 decouples read path
    // from the row-selection gate that swing_set_selection still enforces)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_columnSelectionMode_returnsRows() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a", "b"}, {"c", "d"}}, new Object[]{"col1", "col2"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        context.putRef(99, table);
        String json = getItems(99, 0, 10);
        assertTrue(json.startsWith("{\"totalCount\":2,\"items\":["),
                "Expected totalCount=2, got: " + json);
        assertTrue(json.contains("{\"index\":0,\"name\":\"a | b\"}"), json);
        assertTrue(json.contains("{\"index\":1,\"name\":\"c | d\"}"), json);
    }

    @Test
    void jTable_cellSelectionMode_returnsRows() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a", "b"}, {"c", "d"}}, new Object[]{"col1", "col2"}));
        table.setCellSelectionEnabled(true);
        context.putRef(99, table);
        String json = getItems(99, 0, 10);
        assertTrue(json.startsWith("{\"totalCount\":2,\"items\":["), json);
        assertTrue(json.contains("{\"index\":0,\"name\":\"a | b\"}"), json);
        assertTrue(json.contains("{\"index\":1,\"name\":\"c | d\"}"), json);
    }

    @Test
    void jTable_noSelectionAllowed_returnsRows() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a", "b"}, {"c", "d"}}, new Object[]{"col1", "col2"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        context.putRef(99, table);
        String json = getItems(99, 0, 10);
        assertTrue(json.startsWith("{\"totalCount\":2,\"items\":["), json);
        assertTrue(json.contains("{\"index\":0,\"name\":\"a | b\"}"), json);
        assertTrue(json.contains("{\"index\":1,\"name\":\"c | d\"}"), json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> getItems(999, 0, 10));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void componentWithoutSelectionSupport_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(ref, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void jTree_returnsError() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(99, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"));
    }

    @Test
    void negativeOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(ref, -1, 10));
        assertTrue(ex.getMessage().contains("offset"));
    }

    @Test
    void negativeLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(ref, 0, -1));
        assertTrue(ex.getMessage().contains("length"));
    }

    @Test
    void missingOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", ref, "length", 10)), context));
        assertTrue(ex.getMessage().contains("offset"));
    }

    @Test
    void missingLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", ref, "offset", 0)), context));
        assertTrue(ex.getMessage().contains("length"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read-only / ref preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapPreservedAfterCall() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        int ref = context.getRefOf(list);
        // Call twice with same ref — should not fail
        String json1 = getItems(ref, 0, 2);
        String json2 = getItems(ref, 0, 2);
        assertEquals(json1, json2);
    }

    @Test
    void disabledList_stillReturnsItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setEnabled(false);
        snapshot(list);
        String json = getItems(context.getRefOf(list), 0, 2);
        assertTrue(json.contains("\"totalCount\":2"));
        assertTrue(json.contains("\"name\":\"A\""));
        assertTrue(json.contains("\"name\":\"B\""));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Index round-trip with swing_set_selection / swing_get_selection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_indexRoundTrip() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        snapshot(list);
        int ref = context.getRefOf(list);

        // Get items to learn indices
        String itemsJson = getItems(ref, 0, 3);
        assertTrue(itemsJson.contains("{\"index\":1,\"name\":\"Beta\"}"));

        // Select index 1 directly (fire-and-forget from swing_set_selection
        // may not execute in headless mode without an EDT pump)
        list.setSelectedIndex(1);

        // Verify via swing_get_selection that the index from get_items works
        MCPProtocol.Content selResult = getSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        String selJson = selResult.getText();
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}", selJson);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // totalCount accuracy
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void totalCount_reflectsTotalRegardlessOfPaging() throws Exception {
        String[] items = new String[100];
        for (int i = 0; i < 100; i++) items[i] = "X" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        int ref = context.getRefOf(list);

        // Different pages should all report totalCount: 100
        assertTrue(getItems(ref, 0, 10).contains("\"totalCount\":100"));
        assertTrue(getItems(ref, 50, 10).contains("\"totalCount\":100"));
        assertTrue(getItems(ref, 99, 1).contains("\"totalCount\":100"));
        assertTrue(getItems(ref, 100, 10).contains("\"totalCount\":100"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        String json = getItems(context.getRefOf(list), 0, 2);
        assertTrue(json.contains("\"totalCount\":2"));
        assertTrue(json.contains("\"name\":\"A\""));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        // Dropped per P-001 — JTabbedPane is no longer a supported target.
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("First", new JPanel());
        tp.addTab("Second", new JPanel());
        assertNotSupported(tp);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y"});
        snapshot(combo);
        String json = getItems(context.getRefOf(combo), 0, 2);
        assertTrue(json.contains("\"totalCount\":2"));
        assertTrue(json.contains("\"name\":\"X\""));
    }

    @Test
    void componentMatrix_JTable() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Col1", "Col2"}));
        snapshot(table);
        String json = getItems(context.getRefOf(table), 0, 2);
        assertTrue(json.contains("\"totalCount\":2"));
        assertTrue(json.contains("\"name\":\"A | 1\""));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            return; // No ref — acceptable
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(ref, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items")
                        || ex.getMessage().contains("row-selection mode"),
                "Expected not-supported error for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test void componentMatrix_JButton() throws Exception { assertNotSupported(new JButton("OK")); }
    @Test void componentMatrix_JCheckBox() throws Exception { assertNotSupported(new JCheckBox("Check")); }
    @Test void componentMatrix_JRadioButton() throws Exception { assertNotSupported(new JRadioButton("Option")); }
    @Test void componentMatrix_JTextField() throws Exception { assertNotSupported(new JTextField("text")); }
    @Test void componentMatrix_JTextArea() throws Exception { assertNotSupported(new JTextArea("text")); }
    @Test void componentMatrix_JToggleButton() throws Exception { assertNotSupported(new JToggleButton("Toggle")); }
    @Test void componentMatrix_JSlider() throws Exception { assertNotSupported(new JSlider(0, 100, 50)); }
    @Test void componentMatrix_JPanel() throws Exception { assertNotSupported(new JPanel()); }
    @Test void componentMatrix_JScrollPane() throws Exception { assertNotSupported(new JScrollPane(new JTextArea("c"))); }
    @Test void componentMatrix_JSplitPane() throws Exception { assertNotSupported(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())); }
    @Test void componentMatrix_JLabel() throws Exception { assertNotSupported(new JLabel("Hello")); }
    @Test void componentMatrix_JProgressBar() throws Exception { assertNotSupported(new JProgressBar(0, 100)); }
    @Test void componentMatrix_JSpinner() throws Exception { assertNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))); }
    @Test void componentMatrix_JToolBar() throws Exception { JToolBar tb = new JToolBar(); tb.add(new JButton("T")); assertNotSupported(tb); }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // DR-012: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(99, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"));
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
                () -> getItems(ref, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getItems(99, 0, 10));
        assertTrue(ex.getMessage().contains("does not support swing_get_items"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> getItems(99, 0, 1));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> getItems(99, 0, 1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetItemsViaMcpClient() {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_get_items",
                        Map.of("ref", 1, "offset", 0, "length", 3)));

        assertNotEquals(Boolean.TRUE, result.isError(), "get_items should succeed");
        assertFalse(result.content().isEmpty(), "Result should have content");
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals("{\"totalCount\":3,\"items\":["
                + "{\"index\":0,\"name\":\"Alpha\"},"
                + "{\"index\":1,\"name\":\"Beta\"},"
                + "{\"index\":2,\"name\":\"Gamma\"}"
                + "]}", json);
    }
}
