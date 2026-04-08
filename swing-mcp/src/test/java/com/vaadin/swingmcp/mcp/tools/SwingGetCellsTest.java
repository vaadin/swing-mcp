package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetCellsTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetCellsTool tool;
    private SwingClickTool clickTool;
    private SwingGetSelectableItemsTool getSelectableItemsTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetCellsTool();
        clickTool = new SwingClickTool();
        getSelectableItemsTool = new SwingGetSelectableItemsTool();
        context = new SwingToolContext();
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    private String getCells(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("ref", ref, "offset", offset, "length", length)),
                context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — truncated
    // ══════════════════════════════════════════════════════════════════════════

    private JTable createTable(int rows, int cols) {
        DefaultTableModel model = new DefaultTableModel(rows, cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                model.setValueAt("r" + r + "c" + c, r, c);
            }
        }
        return new JTable(model);
    }

    @Test
    void truncatedJTable_firstPage() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        String output = getCells(context.getRefOf(table), 0, 5);
        assertEquals(
                "Showing 5 children from offset 0 (total 15) for table [ref=1]\n"
                + "- label \"r0c0\"\n"
                + "- label \"r1c0\"\n"
                + "- label \"r2c0\"\n"
                + "- label \"r3c0\"\n"
                + "- label \"r4c0\"",
                output);
    }

    @Test
    void truncatedJTable_middlePage() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        String output = getCells(context.getRefOf(table), 10, 5);
        assertEquals(
                "Showing 5 children from offset 10 (total 15) for table [ref=1]\n"
                + "- label \"r10c0\"\n"
                + "- label \"r11c0\"\n"
                + "- label \"r12c0\"\n"
                + "- label \"r13c0\"\n"
                + "- label \"r14c0\"",
                output);
    }

    @Test
    void truncatedJTable_offsetBeyondEnd() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        String output = getCells(context.getRefOf(table), 100, 5);
        assertEquals(
                "Showing 0 children from offset 100 (total 15) for table [ref=1]",
                output);
    }

    @Test
    void truncatedJTable_multiColumn_rowMajorOrder() throws Exception {
        JTable table = createTable(10, 3);
        snapshot(table);
        // Request first 6 cells: row0col0, row0col1, row0col2, row1col0, row1col1, row1col2
        String output = getCells(context.getRefOf(table), 0, 6);
        assertEquals(
                "Showing 6 children from offset 0 (total 30) for table [ref=1]\n"
                + "- label \"r0c0\"\n"
                + "- label \"r0c1\"\n"
                + "- label \"r0c2\"\n"
                + "- label \"r1c0\"\n"
                + "- label \"r1c1\"\n"
                + "- label \"r1c2\"",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList — truncated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncatedJList_returnsChildren() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 5, 3);
        assertEquals(
                "Showing 3 children from offset 5 (total 20) for list [ref=1]\n"
                + "- label \"Item-5\" [ref=2] actions: click\n"
                + "- label \"Item-6\" [ref=3] actions: click\n"
                + "- label \"Item-7\" [ref=4] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTree — truncated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncatedJTree_returnsTopLevelNodes() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        String output = getCells(context.getRefOf(tree), 5, 3);
        assertTrue(output.startsWith("Showing 3 children from offset 5 (total 10) for tree [ref=1]"),
                "Header should show correct paging info, got: " + output);
        assertTrue(output.contains("\"Node-5\""), "Should contain Node-5, got: " + output);
        assertTrue(output.contains("\"Node-7\""), "Should contain Node-7, got: " + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Parent ref=1 and follow-up calls
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void parentGetsRef1_childRefsStartFrom2() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        // Parent is ref=1 (not rendered but in ref map), children start from 2
        assertTrue(output.contains("[ref=2]"), "First child should have ref=2, got: " + output);
        assertTrue(output.contains("[ref=3]"), "Second child should have ref=3, got: " + output);
        assertTrue(output.contains("[ref=4]"), "Third child should have ref=4, got: " + output);
    }

    @Test
    void getCellsAgainWithRef1_noPriorSnapshot() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        // First get_cells
        getCells(context.getRefOf(list), 0, 3);
        // Call again with ref=1 (parent) — should work without swing_snapshot
        String output2 = getCells(1, 10, 3);
        assertEquals(
                "Showing 3 children from offset 10 (total 20) for list [ref=1]\n"
                + "- label \"Item-10\" [ref=2] actions: click\n"
                + "- label \"Item-11\" [ref=3] actions: click\n"
                + "- label \"Item-12\" [ref=4] actions: click",
                output2);
    }

    @Test
    void getCells_thenGetSelectableItems_withRef1() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        getCells(context.getRefOf(list), 0, 3);
        // Call swing_get_selectable_items with ref=1
        MCPProtocol.Content result = getSelectableItemsTool.execute(
                new Parameters(Map.of("ref", 1, "offset", 0, "length", 3)),
                context);
        String json = result.getText();
        assertTrue(json.contains("\"totalCount\":20"), "Should see all 20 items");
        assertTrue(json.contains("\"Item-0\""), "Should contain Item-0");
    }

    @Test
    void getCells_thenClickChildRef() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        getCells(context.getRefOf(list), 0, 3);
        // Click child ref=2 (first item in output) — should not throw
        assertDoesNotThrow(() -> clickTool.execute(new Parameters(Map.of("ref", 2)), context));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ref map replacement
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void oldRefsInvalidAfterGetCells() throws Exception {
        JTable table = createTable(15, 1);
        JPanel panel = new JPanel();
        panel.add(new JButton("Btn"));
        panel.add(table);
        snapshot(panel);

        // Remember the button's ref — it's ref=1 (first actionable node)
        JButton btn = (JButton) panel.getComponent(0);
        int btnRef = context.getRefOf(btn);
        assertEquals(1, btnRef, "Button should be ref=1 in original snapshot");

        // Now call get_cells — replaces ref map. Ref=1 now maps to the JTable, not the button.
        getCells(context.getRefOf(table), 0, 3);

        // Ref=1 now resolves to JTable, not JButton. The old mapping is gone.
        // Verify by checking that ref=1 is no longer the button:
        Accessible resolved = context.getAccessibleByRef(1);
        assertNotSame(btn, resolved, "ref=1 should no longer point to the button");
        assertSame(table, resolved, "ref=1 should now point to the JTable parent");
    }

    @Test
    void snapshotRestoresFullRefMap() throws Exception {
        JTable table = createTable(15, 1);
        JPanel panel = new JPanel();
        JButton btn = new JButton("Btn");
        panel.add(btn);
        panel.add(table);
        snapshot(panel);

        // get_cells replaces ref map
        getCells(context.getRefOf(table), 0, 3);

        // Snapshot restores full ref map
        snapshot(panel);

        // Button should be accessible again
        int btnRef = context.getRefOf(btn);
        assertDoesNotThrow(() -> clickTool.execute(new Parameters(Map.of("ref", btnRef)), context));
    }

    @Test
    void refMapReplacedEvenWhenOutputEmpty() throws Exception {
        JTable table = createTable(15, 1);
        JPanel panel = new JPanel();
        panel.add(new JButton("Btn"));
        panel.add(table);
        snapshot(panel);

        int tableRef = context.getRefOf(table);

        // Offset beyond end — empty output, but ref map is still replaced
        getCells(tableRef, 100, 5);

        // Only ref=1 (parent) should exist; ref=2 and higher should be gone
        // since no children were returned
        assertThrows(MCPServerException.class,
                () -> context.getAccessibleByRef(2));
        // ref=1 is the parent table
        assertSame(table, context.getAccessibleByRef(1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Non-truncated large data component — succeeds
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonTruncatedJList_succeeds() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 10);
        assertEquals(
                "Showing 3 children from offset 0 (total 3) for list [ref=1]\n"
                + "- label \"A\" [ref=2] actions: click\n"
                + "- label \"B\" [ref=3] actions: click\n"
                + "- label \"C\" [ref=4] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Empty large data component
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void emptyJList_succeeds() throws Exception {
        JList<String> list = new JList<>(new String[]{});
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 10);
        assertEquals(
                "Showing 0 children from offset 0 (total 0) for list [ref=1]",
                output);
    }

    @Test
    void emptyJTable_succeeds() throws Exception {
        JTable table = new JTable(new DefaultTableModel(0, 3));
        snapshot(table);
        String output = getCells(context.getRefOf(table), 0, 10);
        assertEquals(
                "Showing 0 children from offset 0 (total 0) for table [ref=1]",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonLargeDataComponent_returnsError() throws Exception {
        JButton btn = new JButton("Click");
        snapshot(btn);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(btn), 0, 5));
        assertEquals("Component does not support swing_get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test
    void invalidRef_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPServerException.class,
                () -> getCells(9999, 0, 5));
    }

    @Test
    void invalidRef_messagesSuggestSnapshot() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> getCells(9999, 0, 5));
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void disabledComponent_succeeds() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        list.setEnabled(false);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "Should succeed on disabled component");
    }

    @Test
    void negativeOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(list), -1, 5));
    }

    @Test
    void negativeLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(list), 0, -1));
    }

    @Test
    void missingOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", context.getRefOf(list), "length", 5)), context));
    }

    @Test
    void missingLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", context.getRefOf(list), "offset", 0)), context));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Header line
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void headerLineShowsCorrectInfo() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 5, 3);
        String firstLine = output.split("\n")[0];
        assertEquals("Showing 3 children from offset 5 (total 20) for list [ref=1]", firstLine);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — expected to succeed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTable() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        String output = getCells(context.getRefOf(table), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "JTable should succeed");
    }

    @Test
    void componentMatrix_JList() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "JList should succeed");
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        String output = getCells(context.getRefOf(tree), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "JTree should succeed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — expected to fail
    // ══════════════════════════════════════════════════════════════════════════

    private void assertNotSupported(Component comp) throws Exception {
        snapshot(comp);
        int ref = context.getRefOf(comp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(ref, 0, 5));
        assertEquals("Component does not support swing_get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test void componentMatrix_JButton() throws Exception { assertNotSupported(new JButton("B")); }
    @Test void componentMatrix_JCheckBox() throws Exception { assertNotSupported(new JCheckBox("C")); }
    @Test void componentMatrix_JRadioButton() throws Exception { assertNotSupported(new JRadioButton("R")); }
    @Test void componentMatrix_JTextField() throws Exception { assertNotSupported(new JTextField("T")); }
    @Test void componentMatrix_JTextArea() throws Exception { assertNotSupported(new JTextArea("A")); }
    @Test void componentMatrix_JComboBox() throws Exception { assertNotSupported(new JComboBox<>(new String[]{"A", "B"})); }
    @Test void componentMatrix_JToggleButton() throws Exception { assertNotSupported(new JToggleButton("T")); }
    @Test void componentMatrix_JSlider() throws Exception { assertNotSupported(new JSlider(0, 100, 50)); }
    @Test void componentMatrix_JPanel() throws Exception {
        // JPanel has no actions (no ref) — verify via SwingUtils
        JPanel p = new JPanel();
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isLargeDataComponent(p));
    }
    @Test void componentMatrix_JScrollPane() throws Exception {
        // JScrollPane has no actions (no ref) — verify via SwingUtils
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isLargeDataComponent(new JScrollPane(new JTextArea("c"))));
    }
    @Test void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("T1", new JPanel());
        assertNotSupported(tp);
    }
    @Test void componentMatrix_JSplitPane() throws Exception { assertNotSupported(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())); }
    @Test void componentMatrix_JLabel() throws Exception {
        // JLabel has no actions -> no ref -> can't test via assertNotSupported.
        // Verify it's not a large data component by checking SwingUtils directly.
        JLabel label = new JLabel("Hello");
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isLargeDataComponent(label));
    }
    @Test void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertNotSupported(pb);
    }
    @Test void componentMatrix_JSpinner() throws Exception { assertNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))); }
    @Test void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("T"));
        // JToolBar has no actions (no ref) — verify via SwingUtils
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isLargeDataComponent(tb));
    }
    @Test void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        // JMenuBar has no actions (no ref) — verify via SwingUtils
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isLargeDataComponent(mb));
    }
    @Test void componentMatrix_JMenu() throws Exception {
        JMenu m = new JMenu("File");
        m.add(new JMenuItem("Open"));
        assertNotSupported(m);
    }
    @Test void componentMatrix_JMenuItem() throws Exception { assertNotSupported(new JMenuItem("Open")); }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetCellsViaMcpClient() {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_get_cells",
                        Map.of("ref", 1, "offset", 0, "length", 3)));

        assertNotEquals(Boolean.TRUE, result.isError(), "get_cells should succeed");
        assertFalse(result.content().isEmpty(), "Result should have content");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("Showing 3 children from offset 0 (total 20) for list [ref=1]"),
                "Should have header, got: " + text);
        assertTrue(text.contains("\"Item-0\""), "Should contain Item-0, got: " + text);
    }
}
