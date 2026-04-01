package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetTextTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSetTextTool setTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        setTextTool = new SwingSetTextTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void setText(int ref, String text) throws Exception {
        try {
            setTextTool.execute(new Parameters(Map.of("ref", ref, "text", text)), context);
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void settingTextOnJTextFieldReplacesContent() throws Exception {
        JTextField field = new JTextField("old");
        snapshot(field);
        setText(context.getRefOf(field), "new value");
        assertEquals("new value", field.getText());
    }

    @Test
    void settingMultiLineTextOnJTextArea() throws Exception {
        JTextArea area = new JTextArea("old");
        snapshot(area);
        setText(context.getRefOf(area), "line1\nline2\nline3");
        assertEquals("line1\nline2\nline3", area.getText());
    }

    @Test
    void settingEmptyStringClearsTextField() throws Exception {
        JTextField field = new JTextField("something");
        snapshot(field);
        setText(context.getRefOf(field), "");
        assertEquals("", field.getText());
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setText(999, "value"));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutSetTextSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(slider), "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledFieldReturnsMcpErrorExplainingDisabled() throws Exception {
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(field), "new"));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void nonEditableFieldReturnsMcpErrorSayingNotEditable() throws Exception {
        JTextField field = new JTextField("read only");
        field.setEditable(false);
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(field), "new"));
        assertEquals("Component is not editable", ex.getMessage());
    }

    @Test
    void refMapClearedAfterSuccessfulSetText() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        int ref = context.getRefOf(field);
        setText(ref, "new");

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setText(ref, "another"));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void refMapClearedAfterFailedSetTextOnDisabledComponent() throws Exception {
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        snapshot(field);
        int ref = context.getRefOf(field);

        assertThrows(MCPErrorResponseException.class, () -> setText(ref, "new"));

        // ref map should be cleared — next use of same ref fails with INVALID_PARAMS
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setText(ref, "another"));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void successReturnsNull() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);

        MCPProtocol.Content result = setTextTool.execute(
                new Parameters(Map.of("ref", context.getRefOf(field), "text", "new")), context);
        assertNull(result, "Successful set_text should return null");
    }

    @Test
    void settingTextFiresDocumentListener() throws Exception {
        JTextField field = new JTextField("old");
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { listenerFired.set(true); }
            @Override public void removeUpdate(DocumentEvent e) { listenerFired.set(true); }
            @Override public void changedUpdate(DocumentEvent e) { listenerFired.set(true); }
        });

        snapshot(field);
        setText(context.getRefOf(field), "new");
        assertTrue(listenerFired.get(), "DocumentListener should fire after set_text");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("old");
        snapshot(field);
        setText(context.getRefOf(field), "updated");
        assertEquals("updated", field.getText());
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("old");
        snapshot(field);
        setText(context.getRefOf(field), "newpass");
        // BR-12: setTextContents writes the real password
        assertArrayEquals("newpass".toCharArray(), field.getPassword());
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("old");
        snapshot(area);
        setText(context.getRefOf(area), "multi\nline");
        assertEquals("multi\nline", area.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertSetTextNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // No ref (no actions) — cannot call set_text, skip
            return;
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertSetTextNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertSetTextNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertSetTextNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertSetTextNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertSetTextNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertSetTextNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertSetTextNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        snapshot(panel);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        snapshot(sp);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(sp));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        snapshot(tp);
        try {
            int ref = context.getRefOf(tp);
            MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                    () -> setText(ref, "value"));
            assertEquals(
                    "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                    ex.getMessage());
        } catch (IllegalStateException e) {
            // No ref assigned — acceptable
        }
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertSetTextNotSupported(
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
        assertSetTextNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
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
                () -> setText(ref, "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        snapshot(tb);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "value"));
        assertEquals(
                "Component does not support set_text. Call swing_snapshot to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertSetTextNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertSetTextNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSetTextViaMcpClient() {
        JTextField field = new JTextField("old");
        mcpServer.setConsideredComponents(List.of(field));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_set_text", Map.of("ref", 1, "text", "via mcp")));

        assertNotEquals(Boolean.TRUE, result.isError(), "set_text should succeed");
        assertEquals("via mcp", field.getText());
    }
}
