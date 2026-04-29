package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
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

class SwingGetCellCountTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetCellCountTool tool;
    private SwingGetCellsTool cellsTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetCellCountTool();
        cellsTool = new SwingGetCellsTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getCount(int ref) throws Exception {
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — rejected (T-021 BR-03): redirected to swing_get_item_count
    // ══════════════════════════════════════════════════════════════════════════

    private static final String JTABLE_REDIRECT_MESSAGE =
            "JTable does not support swing_get_cell_count. Table cells are plain text labels \u2014 "
                    + "use swing_get_item_count to page through rows.";

    @Test
    void jtable_returnsRedirectError() throws Exception {
        JTable table = new JTable(new DefaultTableModel(10, 5));
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertEquals(JTABLE_REDIRECT_MESSAGE, ex.getMessage());
    }

    @Test
    void jtable_refMapPreservedAfterRedirectError() throws Exception {
        JTable table = new JTable(new DefaultTableModel(3, 2));
        snapshot(table);
        int ref = context.getRefOf(table);
        assertThrows(MCPErrorResponseException.class, () -> getCount(ref));
        // Read-only: ref still resolves after the rejected call.
        assertSame(table, context.getAccessibleByRef(ref));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_200items() throws Exception {
        String[] items = new String[200];
        for (int i = 0; i < 200; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        assertEquals("200", getCount(context.getRefOf(list)));
    }

    @Test
    void jList_empty() throws Exception {
        JList<String> list = new JList<>();
        snapshot(list);
        assertEquals("0", getCount(context.getRefOf(list)));
    }

    @Test
    void jList_3items_nonTruncated() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        assertEquals("3", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTree
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTree_returnsTopLevelNodeCount() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        assertEquals("10", getCount(context.getRefOf(tree)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> getCount(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void nonLargeDataComponent_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_cell_count"));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read-only / ref preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapPreservedAfterCall() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        int ref = context.getRefOf(list);
        String count1 = getCount(ref);
        String count2 = getCount(ref);
        assertEquals(count1, count2);
    }

    @Test
    void disabledList_stillReturnsCount() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        snapshot(list);
        assertEquals("3", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Consistency with swing_get_cells header
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void countMatchesGetCellsHeader_JList() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        int ref = context.getRefOf(list);
        String count = getCount(ref);

        // get_cells replaces ref map, so call it after getCount
        String cellsOutput = cellsTool.execute(
                new Parameters(Map.of("ref", ref, "offset", 0, "length", 0)), context).getText();
        assertTrue(cellsOutput.contains("(total " + count + ")"),
                "get_cells header total should match get_cell_count, got: " + cellsOutput);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTable() throws Exception {
        // JTable gets a dedicated redirect error, not the generic one.
        JTable table = new JTable(new DefaultTableModel(5, 2));
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertEquals(JTABLE_REDIRECT_MESSAGE, ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        assertEquals("2", getCount(context.getRefOf(list)));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        root.add(new DefaultMutableTreeNode("B"));
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        // JTree with 2 nodes won't get a ref via snapshot (below MAX_DATA_ROW_NODES
        // and no selection actions), so register directly
        context.putRef(99, tree);
        assertEquals("2", getCount(99));
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
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_cell_count"),
                "Expected not-supported error for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test void componentMatrix_JButton() throws Exception { assertNotSupported(new JButton("OK")); }
    @Test void componentMatrix_JCheckBox() throws Exception { assertNotSupported(new JCheckBox("Check")); }
    @Test void componentMatrix_JRadioButton() throws Exception { assertNotSupported(new JRadioButton("Option")); }
    @Test void componentMatrix_JTextField() throws Exception { assertNotSupported(new JTextField("text")); }
    @Test void componentMatrix_JTextArea() throws Exception { assertNotSupported(new JTextArea("text")); }
    @Test void componentMatrix_JComboBox() throws Exception { assertNotSupported(new JComboBox<>(new String[]{"A", "B"})); }
    @Test void componentMatrix_JToggleButton() throws Exception { assertNotSupported(new JToggleButton("Toggle")); }
    @Test void componentMatrix_JSlider() throws Exception { assertNotSupported(new JSlider(0, 100, 50)); }
    @Test void componentMatrix_JPanel() throws Exception { assertNotSupported(new JPanel()); }
    @Test void componentMatrix_JScrollPane() throws Exception { assertNotSupported(new JScrollPane(new JTextArea("c"))); }
    @Test void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("T1", new JPanel());
        assertNotSupported(tp);
    }
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
                () -> getCount(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_cell_count"));
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
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_cell_count"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> getCount(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> getCount(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetCellCountViaMcpClient() {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_get_cell_count",
                        Map.of("ref", 1)));

        assertNotEquals(Boolean.TRUE, result.isError(), "get_cell_count should succeed");
        assertFalse(result.content().isEmpty(), "Result should have content");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals("20", text);
    }
}
