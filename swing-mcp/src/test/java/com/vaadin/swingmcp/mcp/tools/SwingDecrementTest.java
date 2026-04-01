package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@code swing_decrement}.
 * All happy-path cases can run headless; {@code doAccessibleAction} for decrement
 * works correctly in headless mode for both {@code JSpinner} and {@code JSlider}
 * (see UC-009 BR-10).
 */
class SwingDecrementTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDecrementTool decrementTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        decrementTool = new SwingDecrementTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content decrement(int ref) throws Exception {
        try {
            MCPProtocol.Content result = decrementTool.execute(new Parameters(Map.of("ref", ref)), context);
            SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
            return result;
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — JSpinner variants
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerNumberModel() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        MCPProtocol.Content result = decrement(context.getRefOf(spinner));
        assertNull(result, "Success should return null");
        assertEquals(4, spinner.getValue());
    }

    @Test
    void decrementSpinnerListModel() throws Exception {
        SpinnerListModel model = new SpinnerListModel(List.of("A", "B", "C"));
        JSpinner spinner = new JSpinner(model);
        spinner.setValue("B");
        snapshot(spinner);
        decrement(context.getRefOf(spinner));
        assertEquals("A", spinner.getValue());
    }

    @Test
    void decrementSpinnerDateModel() throws Exception {
        Date initial = new Date(1_000_000L + 86_400_000L); // one day above epoch-ish
        SpinnerDateModel model = new SpinnerDateModel(initial, null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        decrement(context.getRefOf(spinner));
        Date after = (Date) spinner.getValue();
        assertTrue(after.before(initial), "Date spinner should move back after decrement");
    }

    @Test
    void decrementSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        MCPProtocol.Content result = decrement(context.getRefOf(slider));
        assertNull(result, "Success should return null");
        assertEquals(49, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> decrement(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutDecrementSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(context.getRefOf(button)));
        assertEquals(
                "Component does not support decrement. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledSpinnerReturnsMcpError() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        spinner.setEnabled(false);
        snapshot(spinner);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void successReturnsNull() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        assertNull(decrement(context.getRefOf(spinner)));
    }

    @Test
    void refMapClearedAfterSuccessfulDecrement() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        decrement(ref);

        // Ref map cleared — same ref should now be invalid
        assertThrows(MCPServerException.class, () -> decrement(ref));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingDecrementViaMcpClient() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        mcpServer.setConsideredComponents(List.of(spinner));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_decrement", Map.of("ref", 1)));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.isError(), "swing_decrement should succeed");
        assertEquals(4, spinner.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported components
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        MCPProtocol.Content result = decrement(context.getRefOf(spinner));
        assertNull(result);
        assertEquals(4, spinner.getValue());
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        MCPProtocol.Content result = decrement(context.getRefOf(slider));
        assertNull(result);
        assertEquals(49, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — unsupported components (no decrement support)
    // ══════════════════════════════════════════════════════════════════════════

    private void assertDecrementNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            return; // no ref (no actions) — cannot call decrement
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(ref));
        assertEquals(
                "Component does not support decrement. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertDecrementNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertDecrementNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertDecrementNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertDecrementNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertDecrementNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertDecrementNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertDecrementNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertDecrementNotSupported(new JToggleButton("Toggle"));
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
        // JScrollPane has no ref — no further action needed
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertDecrementNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertDecrementNotSupported(
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
        assertDecrementNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(ref));
        assertEquals(
                "Component does not support decrement. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        assertDecrementNotSupported(menu);
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
                () -> decrement(ref));
        assertEquals(
                "Component does not support decrement. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Tool"));
        snapshot(tb);
        int ref = context.getRefOf(tb.getComponent(0));
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(ref));
        assertEquals(
                "Component does not support decrement. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertDecrementNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertDecrementNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }
}
