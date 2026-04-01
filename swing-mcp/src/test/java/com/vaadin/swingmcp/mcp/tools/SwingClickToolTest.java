package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingClickToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClickTool clickTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clickTool = new SwingClickTool();
        context = new SwingToolContext();
    }

    /**
     * Runs a snapshot to populate ref map, then returns the snapshot text.
     */
    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    /**
     * Clicks the given ref. Assumes snapshot was already called to populate refs.
     * Clears the ref map afterwards (mirroring MCPServer.registerTool behaviour
     * for mutation tools).
     */
    private void click(int ref) throws Exception {
        try {
            clickTool.execute(new Parameters(Map.of("ref", ref)), context);
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickingButtonTriggersActionListener() throws Exception {
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));

        snapshot(button);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Action listener should have been triggered");
    }

    @Test
    void clickingCheckboxTogglesState() throws Exception {
        JCheckBox checkBox = new JCheckBox("Accept");
        assertFalse(checkBox.isSelected());

        snapshot(checkBox);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox should be selected after click");
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> click(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void disabledButtonReturnsMcpErrorExplainingDisabled() throws Exception {
        JButton button = new JButton("Disabled");
        button.setEnabled(false);

        snapshot(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutClickSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);

        snapshot(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(slider)));
        assertEquals("Component does not support click. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void successReturnsNull() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPProtocol.Content result = clickTool.execute(
                new Parameters(Map.of("ref", context.getRefOf(button))), context);
        assertNull(result, "Successful click should return null (empty string)");
    }

    @Test
    void refsInvalidatedAfterClick() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        click(ref);

        // Refs should be cleared after mutation — isMutation() == true
        MCPServerException ex = assertThrows(MCPServerException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot after invalidation");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingClickViaMcpClient() {
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        mcpServer.setConsideredComponents(List.of(button));

        // First take a snapshot to populate refs
        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        // Then click ref 1
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_click", Map.of("ref", 1)));

        assertNotEquals(Boolean.TRUE, result.isError(), "Click should succeed");
        assertTrue(clicked.get(), "Action listener should have been triggered via MCP client");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Effectively enabled — parent chain
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledParentPreventsClick() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Child");
        panel.add(button);
        panel.setEnabled(false);
        // Button itself is still enabled, but parent is disabled

        snapshot(panel);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled when parent is disabled");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JButton() throws Exception {
        JButton button = new JButton("Click me");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));

        snapshot(button);
        click(context.getRefOf(button));
        assertTrue(clicked.get());
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text");

        snapshot(field);
        // JTextField has dynamic text actions, not click
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("secret");

        snapshot(field);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("text");

        snapshot(area);
        int ref = context.getRefOf(area);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Check");
        assertFalse(cb.isSelected());

        snapshot(cb);
        click(context.getRefOf(cb));
        assertTrue(cb.isSelected());
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        assertFalse(rb.isSelected());

        snapshot(rb);
        click(context.getRefOf(rb));
        assertTrue(rb.isSelected());
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});

        snapshot(combo);
        // JComboBox has toggle_popup, not click
        int ref = context.getRefOf(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        JToggleButton tb = new JToggleButton("Toggle");
        assertFalse(tb.isSelected());

        snapshot(tb);
        click(context.getRefOf(tb));
        assertTrue(tb.isSelected());
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));

        snapshot(spinner);
        // JSpinner has increment/decrement, not click
        int ref = context.getRefOf(spinner);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);

        snapshot(slider);
        // JSlider has increment/decrement, not click
        int ref = context.getRefOf(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.setName("TestPanel");

        snapshot(panel);
        // JPanel has no actions — should not receive a ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));

        snapshot(sp);
        // Scroll pane itself is structural — no click ref expected on the scroll pane itself
        assertThrows(IllegalStateException.class, () -> context.getRefOf(sp));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());

        snapshot(tp);
        // JTabbedPane.Page has no AccessibleAction — tabs do not get click refs
        // (Tab switching would use selection actions, not click)
        // The JTabbedPane itself may or may not get a ref depending on selection support
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());

        snapshot(sp);
        // JSplitPane has AccessibleValue (get_value/set_value) but no click
        int ref = context.getRefOf(sp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");

        snapshot(label);
        // JLabel has no actions
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);

        snapshot(pb);
        // JProgressBar has get_value but no click
        int ref = context.getRefOf(pb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);

        snapshot(mb);
        // JMenuBar is structural; JMenu gets a click ref
        int ref = context.getRefOf(menu);
        click(ref);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);

        snapshot(mb);
        // JMenu supports click (inherited from AbstractButton)
        click(context.getRefOf(menu));
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        AtomicBoolean clicked = new AtomicBoolean(false);
        item.addActionListener(e -> clicked.set(true));
        menu.add(item);
        mb.add(menu);

        snapshot(mb);
        click(context.getRefOf(item));
        assertTrue(clicked.get());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);

        snapshot(tb);
        // JToolBar is structural; button inside gets click ref
        click(context.getRefOf(button));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));

        snapshot(tree);
        // JTree itself has selection but no click support
        int ref = context.getRefOf(tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support click"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});

        snapshot(list);
        // JList itself gets a ref (selection actions, no click)
        int listRef = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(listRef));
        assertTrue(ex.getMessage().contains("does not support click"));

        // Re-snapshot to get fresh refs after the failed click cleared the map
        snapshot(list);

        // JList children have click action — ref is listRef + 1
        int childRef = listRef + 1;
        click(childRef);
    }
}
