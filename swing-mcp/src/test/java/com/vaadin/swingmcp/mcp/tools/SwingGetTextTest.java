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

import static org.junit.jupiter.api.Assertions.*;

class SwingGetTextTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetTextTool getTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getTextTool = new SwingGetTextTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getText(int ref) throws Exception {
        MCPProtocol.Content result = getTextTool.execute(new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJTextFieldReturnsContent() throws Exception {
        JTextField field = new JTextField("hello world");
        snapshot(field);
        assertEquals("hello world", getText(context.getRefOf(field)));
    }

    @Test
    void readJTextAreaReturnsFullContentWithNewlines() throws Exception {
        JTextArea area = new JTextArea("line1\nline2\nline3");
        snapshot(area);
        assertEquals("line1\nline2\nline3", getText(context.getRefOf(area)));
    }

    @Test
    void readJPasswordFieldReturnsEchoCharacters() throws Exception {
        JPasswordField field = new JPasswordField("secret");
        snapshot(field);
        String result = getText(context.getRefOf(field));
        assertNotNull(result);
        // The result should be masked (echo characters), not the actual password
        assertNotEquals("secret", result, "Password should be masked");
        assertEquals(6, result.length(), "Echo chars should have same length as password");
    }

    @Test
    void readEmptyJTextFieldReturnsEmptyString() throws Exception {
        JTextField field = new JTextField("");
        snapshot(field);
        MCPProtocol.Content result = getTextTool.execute(
                new Parameters(Map.of("ref", context.getRefOf(field))), context);
        assertNotNull(result, "Result should not be null for empty field");
        assertEquals("", result.getText(), "Empty field should return empty string");
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> getText(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutTextSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(context.getRefOf(slider)));
        assertTrue(ex.getMessage().contains("does not support get_text"),
                "Error should mention get_text, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void refMapPreservedAfterGetText() throws Exception {
        JTextField field = new JTextField("preserved");
        snapshot(field);
        int ref = context.getRefOf(field);

        // Call get_text twice with same ref — should succeed both times (map not cleared)
        assertEquals("preserved", getText(ref));
        assertEquals("preserved", getText(ref));
    }

    @Test
    void readDisabledJTextFieldSucceeds() throws Exception {
        JTextField field = new JTextField("disabled content");
        field.setEnabled(false);
        snapshot(field);

        assertEquals("disabled content", getText(context.getRefOf(field)));
    }

    @Test
    void textExceedingMaxLengthIsTruncated() throws Exception {
        String longText = "x".repeat(SwingGetTextTool.MAX_TEXT_LENGTH + 100);
        JTextField field = new JTextField(longText);
        snapshot(field);

        String result = getText(context.getRefOf(field));
        assertNotNull(result);
        assertTrue(result.contains("truncated"), "Result should contain truncation notice");
        assertTrue(result.contains(String.valueOf(SwingGetTextTool.MAX_TEXT_LENGTH + 100)),
                "Result should mention total character count");
        // The actual text part should be exactly MAX_TEXT_LENGTH chars
        int newlineIdx = result.indexOf('\n');
        assertTrue(newlineIdx > 0);
        assertEquals(SwingGetTextTool.MAX_TEXT_LENGTH, newlineIdx);
    }

    @Test
    void textExactlyAtMaxLengthNotTruncated() throws Exception {
        String exactText = "y".repeat(SwingGetTextTool.MAX_TEXT_LENGTH);
        JTextField field = new JTextField(exactText);
        snapshot(field);

        String result = getText(context.getRefOf(field));
        assertEquals(exactText, result, "Text at exactly MAX_TEXT_LENGTH should not be truncated");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text content");
        snapshot(field);
        assertEquals("text content", getText(context.getRefOf(field)));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("pass");
        snapshot(field);
        String result = getText(context.getRefOf(field));
        assertNotNull(result);
        assertEquals(4, result.length());
        assertNotEquals("pass", result);
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("multi\nline");
        snapshot(area);
        assertEquals("multi\nline", getText(context.getRefOf(area)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertGetTextNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // Component has no ref (no actions) — cannot call get_text, skip
            return;
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"),
                "Expected get_text not supported for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertGetTextNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertGetTextNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertGetTextNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertGetTextNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertGetTextNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        // JSpinner.AccessibleJSpinner implements AccessibleText (delegates to inner editor),
        // so get_text succeeds and returns the spinner's formatted value.
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        String result = getText(context.getRefOf(spinner));
        assertNotNull(result, "JSpinner should return its formatted value as text");
        assertFalse(result.isBlank(), "JSpinner text should not be blank");
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertGetTextNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.setName("TestPanel");
        snapshot(panel);
        // JPanel has no ref — cannot call get_text (no actions)
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
        // JTabbedPane may or may not get a ref; if it does, get_text should fail
        snapshot(tp);
        try {
            int ref = context.getRefOf(tp);
            MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                    () -> getText(ref));
            assertTrue(ex.getMessage().contains("does not support get_text"));
        } catch (IllegalStateException e) {
            // No ref assigned — acceptable
        }
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertGetTextNotSupported(
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
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertGetTextNotSupported(pb);
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        // JMenuBar itself has no ref; JMenu has click but not get_text
        int ref = context.getRefOf(menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"));
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"));
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
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        snapshot(tb);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support get_text"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        snapshot(tree);
        // JTree itself has no actions (selection suppressed, not truncated) — no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetTextViaMcpClient() {
        JTextField field = new JTextField("via mcp client");
        mcpServer.setConsideredComponents(List.of(field));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_get_text", Map.of("ref", 1)));

        assertNotEquals(Boolean.TRUE, result.isError(), "get_text should succeed");
        assertFalse(result.content().isEmpty(), "Result should have content");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertEquals("via mcp client", text);
    }
}
