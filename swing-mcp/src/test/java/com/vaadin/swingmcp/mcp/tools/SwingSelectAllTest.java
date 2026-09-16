package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSelectAllTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSelectAllTool selectAllTool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        selectAllTool = new SwingSelectAllTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content selectAll(int ref) throws Exception {
        MCPProtocol.Content result = selectAllTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
        return result;
    }

    private String getSelection(int ref) throws Exception {
        MCPProtocol.Content result = getSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList — multi-selection (happy path)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_multiSelection_5items_selectsAll() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C", "D", "E"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        selectAll(ref);

        assertArrayEquals(new int[]{0, 1, 2, 3, 4}, list.getSelectedIndices());
    }

    @Test
    void jList_multiSelection_200items_selectsAll() throws Exception {
        String[] items = new String[200];
        for (int i = 0; i < 200; i++) items[i] = "Item " + i;
        JList<String> list = new JList<>(items);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        selectAll(ref);

        assertEquals(200, list.getSelectedIndices().length);
    }

    @Test
    void jList_multiSelection_empty_succeeds() throws Exception {
        JList<String> list = new JList<>(new String[]{});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        // Empty list may not get a ref from snapshot; force one
        context.putRef(99, list);
        MCPProtocol.Content result = selectAll(99);

        assertEquals("Dispatched select-all on ref=99 — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(0, list.getSelectedIndices().length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — multi-row-selection (happy path)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_multiRowSelection_returnsSuccess() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{
                        {"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"},
                        {"Dave", "40"}, {"Eve", "45"}, {"Frank", "50"},
                        {"Grace", "55"}, {"Heidi", "60"}, {"Ivan", "65"}, {"Judy", "70"}
                },
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPProtocol.Content result = selectAllTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched select-all on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void jTable_empty_succeeds() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        snapshot(table);
        // Empty table may not get a ref; force one
        context.putRef(99, table);
        MCPProtocol.Content result = selectAll(99);
        assertEquals("Dispatched select-all on ref=99 — call swing_snapshot to verify the outcome", result.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Single-selection — rejected (BR-04)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_singleSelection_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("single-selection mode"),
                "Expected single-selection error, got: " + ex.getMessage());
    }

    @Test
    void jTabbedPane_returnsError() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("single-selection mode"),
                "Expected single-selection error, got: " + ex.getMessage());
    }

    @Test
    void jComboBox_returnsError() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        snapshot(combo);
        int ref = context.getRefOf(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("single-selection mode"),
                "Expected single-selection error, got: " + ex.getMessage());
    }

    @Test
    void jTable_singleRowSelection_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("single-selection mode"),
                "Expected single-selection error, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — unsupported modes (BR-03)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_columnSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    @Test
    void jTable_cellSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(99));
        assertTrue(ex.getMessage().contains("row-selection mode"));
    }

    @Test
    void jTable_noSelectionAllowed_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(99));
        assertTrue(ex.getMessage().contains("row-selection mode"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // No selection support (BR-03)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> selectAll(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void jButton_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("does not support swing_select_all"),
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
                () -> selectAll(99));
        assertTrue(ex.getMessage().contains("does not support swing_select_all"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Disabled component (BR-05)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledList_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setEnabled(false);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> selectAll(ref));
        assertTrue(ex.getMessage().contains("disabled"),
                "Expected disabled error, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ref map clearing (BR-07 — mutation tool)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapClearedAfterSuccessfulSelectAll() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        selectAll(ref);
        // Ref map should now be cleared — using old ref should fail
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> selectAll(ref));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
    }

    @Test
    void refMapPreservedAfterFailedSelectAllOnDisabledComponent() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setEnabled(false);
        snapshot(list);
        int ref = context.getRefOf(list);
        assertThrows(MCPErrorResponseException.class,
                () -> selectAllTool.execute(new Parameters(Map.of("ref", ref)), context));
        // Validation error — ref map must still be intact so the AI can retry
        list.setEnabled(true);
        selectAll(ref);
        assertArrayEquals(new int[]{0, 1}, list.getSelectedIndices());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Round-trip (BR-08 + verification)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void roundTrip_jList() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        selectAll(ref);

        // Re-snapshot to get fresh refs
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":3,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alpha\"},"
                        + "{\"index\":1,\"name\":\"Beta\"},"
                        + "{\"index\":2,\"name\":\"Gamma\"}"
                        + "]}",
                json);
    }

    // JTable round-trip is in SwingSelectAllScreenTest
    // (table.selectAll() in headless may not be readable via AccessibleSelection)

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList_multiSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPProtocol.Content result = selectAllTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched select-all on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void componentMatrix_JTable_multiRowSelection() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        // Default is multi-row-selection
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPProtocol.Content result = selectAllTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched select-all on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — single-selection (rejected by BR-04)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList_singleSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshot(list);
        int ref = context.getRefOf(list);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("T1", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A"});
        snapshot(combo);
        int ref = context.getRefOf(combo);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JTable_singleRowSelection() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        snapshot(table);
        int ref = context.getRefOf(table);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — no selection support (rejected by BR-03)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Check");
        snapshot(cb);
        int ref = context.getRefOf(cb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Radio");
        snapshot(rb);
        int ref = context.getRefOf(rb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField tf = new JTextField("text");
        snapshot(tf);
        int ref = context.getRefOf(tf);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea ta = new JTextArea("text");
        snapshot(ta);
        int ref = context.getRefOf(ta);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        JToggleButton tb = new JToggleButton("Toggle");
        snapshot(tb);
        int ref = context.getRefOf(tb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider();
        snapshot(slider);
        int ref = context.getRefOf(slider);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner();
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Label");
        snapshot(label);
        context.putRef(99, label);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar();
        snapshot(pb);
        context.putRef(99, pb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField pf = new JPasswordField("secret");
        snapshot(pf);
        int ref = context.getRefOf(pf);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        snapshot(panel);
        context.putRef(99, panel);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane();
        snapshot(sp);
        context.putRef(99, sp);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane();
        snapshot(sp);
        context.putRef(99, sp);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        context.putRef(99, mb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        snapshot(menu);
        context.putRef(99, menu);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuItem item = new JMenuItem("Open");
        snapshot(item);
        int ref = context.getRefOf(item);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(ref));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Btn"));
        snapshot(tb);
        context.putRef(99, tb);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> selectAll(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void selectAllViaMcpClient() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        mcpServer.setConsideredComponents(java.util.List.of(list));

        mcpClient.callTool(
                "swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool(
                        "swing_select_all", Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "select_all should succeed");
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
        assertArrayEquals(new int[]{0, 1, 2}, list.getSelectedIndices());
    }
}
