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

class SwingSetSelectionTest extends AbstractHeadlessTest {

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
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void setSelection(int ref, List<Object> indices) throws Exception {
        setSelectionTool.execute(
                new Parameters(Map.of("ref", ref, "indices", indices)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
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
    void jList_singleSelection_selectsItem() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of(1.0));
        // Fire-and-forget: flush EDT

        assertEquals(1, list.getSelectedIndex());
        assertEquals("Beta", list.getSelectedValue());
    }

    @Test
    void jList_multiSelection_selectsAllItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma", "Delta"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of(0.0, 2.0, 3.0));

        assertArrayEquals(new int[]{0, 2, 3}, list.getSelectedIndices());
    }

    @Test
    void jList_emptyArray_clearsSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta"});
        list.setSelectedIndex(0);
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of());

        assertTrue(list.isSelectionEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_selectsTab() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("General", new JPanel());
        tp.addTab("Advanced", new JPanel());
        tp.setSelectedIndex(0);
        snapshot(tp);
        int ref = context.getRefOf(tp);
        setSelection(ref, List.of(1.0));

        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    void jTabbedPane_withTabs_emptyArray_returnsError() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of()));
        assertEquals("This component does not allow the selection to be empty.", ex.getMessage());
    }

    @Test
    void jTabbedPane_empty_emptyArray_succeeds() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        snapshot(tp);
        // Empty JTabbedPane has no actions, so no ref. Force one.
        context.putRef(99, tp);
        // Should not throw
        setSelection(99, List.of());
    }

    @Test
    void jTabbedPane_disabledTab_returnsError() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Enabled", new JPanel());
        tp.addTab("Disabled", new JPanel());
        tp.setEnabledAt(1, false);
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(1.0)));
        assertEquals("Tab at index 1 is disabled.", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBox_selectsItem() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(0);
        snapshot(combo);
        int ref = context.getRefOf(combo);
        setSelection(ref, List.of(2.0));

        assertEquals(2, combo.getSelectedIndex());
        assertEquals("Blue", combo.getSelectedItem());
    }

    @Test
    void jComboBox_emptyArray_clearsSelection() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(1);
        snapshot(combo);
        int ref = context.getRefOf(combo);
        setSelection(ref, List.of());

        assertEquals(-1, combo.getSelectedIndex());
        assertNull(combo.getSelectedItem());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection mode
    // ══════════════════════════════════════════════════════════════════════════

    // Note: JTable's addAccessibleSelection() is a no-op in headless mode
    // (table must be isShowing()). These tests verify the tool returns success;
    // actual selection verification is in SwingSetSelectionScreenTest.

    @Test
    void jTable_singleRow_returnsSuccess() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPProtocol.Content result = setSelectionTool.execute(
                new Parameters(Map.of("ref", ref, "indices", List.of(1.0))), context);
        assertEquals("Dispatched set-selection on ref=" + ref + " to [1] — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void jTable_multipleRows_returnsSuccess() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPProtocol.Content result = setSelectionTool.execute(
                new Parameters(Map.of("ref", ref, "indices", List.of(0.0, 2.0))), context);
        assertEquals("Dispatched set-selection on ref=" + ref + " to [0, 2] — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void jTable_emptyArray_returnsSuccess() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPProtocol.Content result = setSelectionTool.execute(
                new Parameters(Map.of("ref", ref, "indices", List.of())), context);
        assertEquals("Dispatched set-selection on ref=" + ref + " to [] — call swing_snapshot to verify the outcome", result.getText());
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
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(99, List.of(0.0)));
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
                () -> setSelection(99, List.of(0.0)));
        assertTrue(ex.getMessage().contains("row-selection mode"));
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
                () -> setSelection(99, List.of(0.0)));
        assertTrue(ex.getMessage().contains("row-selection mode"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setSelection(999, List.of(0.0)));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void componentWithoutSelectionSupport_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"),
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
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(99, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"));
    }

    @Test
    void disabledList_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setEnabled(false);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(0.0)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Expected disabled error, got: " + ex.getMessage());
    }

    @Test
    void singleSelectionTabbedPane_multipleIndices_returnsError() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.addTab("Tab3", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(0.0, 1.0)));
        assertTrue(ex.getMessage().contains("single-selection mode"),
                "Expected single-selection error, got: " + ex.getMessage());
    }

    @Test
    void singleSelectionComboBox_multipleIndices_returnsError() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        snapshot(combo);
        int ref = context.getRefOf(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(0.0, 1.0)));
        assertTrue(ex.getMessage().contains("single-selection mode"));
    }

    @Test
    void outOfBoundsIndex_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(10.0)));
        assertEquals("Index 10 is out of bounds. Valid range is [0, 3).", ex.getMessage());
    }

    @Test
    void negativeIndex_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(-1.0)));
        assertEquals("Index -1 is out of bounds. Valid range is [0, 3).", ex.getMessage());
    }

    @Test
    void duplicateIndices_deduplicatedSilently() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        // No error expected
        setSelection(ref, List.of(1.0, 1.0, 2.0, 2.0));

        assertArrayEquals(new int[]{1, 2}, list.getSelectedIndices());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ref map clearing (mutation tool)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapClearedAfterSuccessfulSetSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of(0.0));
        // Ref map should now be cleared — using old ref should fail
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setSelection(ref, List.of(0.0)));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
    }

    @Test
    void refMapPreservedAfterFailedSetSelectionOnDisabledComponent() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setEnabled(false);
        snapshot(list);
        int ref = context.getRefOf(list);
        assertThrows(MCPErrorResponseException.class,
                () -> setSelectionTool.execute(
                        new Parameters(Map.of("ref", ref, "indices", List.of(0.0))), context));
        // Validation error — ref map must still be intact so the AI can retry
        list.setEnabled(true);
        setSelection(ref, List.of(0.0));
        assertArrayEquals(new int[]{0}, list.getSelectedIndices());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Round-trip
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void roundTrip_jList() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of(0.0, 2.0));

        // Re-snapshot to get fresh refs
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":2,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alpha\"},"
                        + "{\"index\":2,\"name\":\"Gamma\"}"
                        + "]}",
                json);
    }

    // JTable round-trip test is in SwingSetSelectionScreenTest
    // (addAccessibleSelection is a no-op in headless mode)

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        setSelection(ref, List.of(1.0));

        assertEquals(1, list.getSelectedIndex());
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("First", new JPanel());
        tp.addTab("Second", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        setSelection(ref, List.of(1.0));

        assertEquals(1, tp.getSelectedIndex());
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y"});
        snapshot(combo);
        int ref = context.getRefOf(combo);
        setSelection(ref, List.of(1.0));

        assertEquals(1, combo.getSelectedIndex());
    }

    @Test
    void componentMatrix_JTable() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Col1", "Col2"});
        JTable table = new JTable(model);
        snapshot(table);
        int ref = context.getRefOf(table);
        // addAccessibleSelection is a no-op in headless mode — just verify success
        MCPProtocol.Content result = setSelectionTool.execute(
                new Parameters(Map.of("ref", ref, "indices", List.of(0.0))), context);
        assertEquals("Dispatched set-selection on ref=" + ref + " to [0] — call swing_snapshot to verify the outcome", result.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertSetSelectionNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // No ref — force one for testing
            context.putRef(99, (javax.accessibility.Accessible) component);
            ref = 99;
        }
        int finalRef = ref;
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(finalRef, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection")
                        || ex.getMessage().contains("row-selection mode"),
                "Expected not-supported error for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertSetSelectionNotSupported(new JButton("OK"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertSetSelectionNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertSetSelectionNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertSetSelectionNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertSetSelectionNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertSetSelectionNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertSetSelectionNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertSetSelectionNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertSetSelectionNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertSetSelectionNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertSetSelectionNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertSetSelectionNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertSetSelectionNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertSetSelectionNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // DR-012: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(99, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"));
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
                () -> setSelection(ref, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton btn = new JButton("Tool");
        tb.add(btn);
        snapshot(tb);
        int ref = context.getRefOf(btn);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(ref, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        snapshot(tree);
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setSelection(99, List.of(0.0)));
        assertTrue(ex.getMessage().contains("does not support swing_set_selection"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> setSelection(99, List.of(0.0)));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> setSelection(99, List.of(0.0)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSetSelectionViaMcpClient() {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_set_selection",
                        Map.of("ref", 1, "indices", List.of(0, 2))));

        assertNotEquals(Boolean.TRUE, result.isError(), "set_selection should succeed");
        // DR-010: mutation tools echo "Dispatched <action> on ref=<N> [to <value>]"
        assertEquals(1, result.content().size(), "Mutation tools return a one-item echo");
        assertEquals("Dispatched set-selection on ref=1 to [0, 2] — call swing_snapshot to verify the outcome",
                ((McpSchema.TextContent) result.content().get(0)).text());
    }
}
