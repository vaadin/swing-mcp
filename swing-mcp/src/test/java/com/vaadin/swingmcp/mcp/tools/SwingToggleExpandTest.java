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
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@code swing_toggle_expand}.
 * The happy-path (actually expanding/collapsing a JTree node) runs headless
 * because JTree expansion is a model-level operation (see UC-010 BR-03).
 */
class SwingToggleExpandTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingToggleExpandTool toggleExpandTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        toggleExpandTool = new SwingToggleExpandTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content toggleExpand(int ref) throws Exception {
        MCPProtocol.Content result = toggleExpandTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void toggleExpandExpandsCollapsedNode() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.collapsePath(new TreePath(root));

        Accessible rootNodeAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, rootNodeAcc);

        MCPProtocol.Content result = toggleExpand(1);
        assertEquals("Posted toggle-expand on ref=1", result.getText());
        assertTrue(tree.isExpanded(new TreePath(root)), "Root should be expanded after toggle");
    }

    @Test
    void toggleExpandCollapsesExpandedNode() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.expandPath(new TreePath(root));

        Accessible rootNodeAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, rootNodeAcc);

        MCPProtocol.Content result = toggleExpand(1);
        assertEquals("Posted toggle-expand on ref=1", result.getText());
        assertFalse(tree.isExpanded(new TreePath(root)), "Root should be collapsed after toggle");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        snapshot(tree);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> toggleExpand(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void leafNodeReturnsMcpError() throws Exception {
        // A root node with no children is a leaf — accessible at index 0 without any expansion
        JTree tree = new JTree(new DefaultMutableTreeNode("leaf-root"));
        Accessible leafAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, leafAcc);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(1));
        assertEquals(
                "Component does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentWithoutToggleExpandSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(context.getRefOf(button)));
        assertEquals(
                "JButton does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledJTreeNodeReturnsMcpError() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.setEnabled(false);

        Accessible rootNodeAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, rootNodeAcc);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(1));
        assertEquals("Component is disabled and cannot be interacted with", ex.getMessage());
    }

    @Test
    void successReturnsEcho() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.collapsePath(new TreePath(root));

        Accessible rootNodeAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, rootNodeAcc);

        assertEquals("Posted toggle-expand on ref=1", toggleExpand(1).getText());
    }

    @Test
    void refMapClearedAfterSuccessfulCall() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.collapsePath(new TreePath(root));

        Accessible rootNodeAcc = tree.getAccessibleContext().getAccessibleChild(0);
        context.putRef(1, rootNodeAcc);

        toggleExpand(1); // succeeds, clears ref map

        // Ref map cleared — same ref should now be invalid
        MCPServerException ex = assertThrows(MCPServerException.class, () -> toggleExpand(1));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingToggleExpandViaMcpClient() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        tree.collapsePath(new TreePath(root));

        mcpServer.setConsideredComponents(List.of(tree));

        // After snapshot: JTree has no ref (selection suppressed), root node (toggle_expand) → ref=1
        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_toggle_expand", Map.of("ref", 1)));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.isError(), "swing_toggle_expand should succeed");
        assertTrue(tree.isExpanded(new TreePath(root)), "Root should be expanded via MCP client");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — all standard 20 components fail
    // ══════════════════════════════════════════════════════════════════════════

    private void assertToggleExpandNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            return; // no ref (no actions) — cannot call toggle_expand
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(ref));
        String expectedClass = ComponentClassResolver.resolveClassName((javax.accessibility.Accessible) component);
        assertEquals(
                expectedClass + " does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertToggleExpandNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertToggleExpandNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertToggleExpandNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertToggleExpandNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertToggleExpandNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertToggleExpandNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertToggleExpandNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertToggleExpandNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertToggleExpandNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertToggleExpandNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        snapshot(panel);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        snapshot(new JScrollPane(new JTextArea("content")));
        // JScrollPane has no ref
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertToggleExpandNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertToggleExpandNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");
        snapshot(label);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertToggleExpandNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // DR-012: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(99));
        assertEquals(
                "JMenu does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        assertToggleExpandNotSupported(menu);
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
                () -> toggleExpand(ref));
        assertEquals(
                "JMenuItem does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Tool"));
        snapshot(tb);
        int ref = context.getRefOf(tb.getComponent(0));
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> toggleExpand(ref));
        assertEquals(
                "JButton does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertToggleExpandNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        // JTree itself (not its nodes) — has selection but no toggle_expand
        assertToggleExpandNotSupported(new JTree(new DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> toggleExpand(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> toggleExpand(99));
    }
}
