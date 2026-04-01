package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingSetTextTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetTextScreenTest extends AbstractScreenTest {

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
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void setText(int ref, String text) throws Exception {
        try {
            executeOnEDT(() -> setTextTool.execute(new Parameters(Map.of("ref", ref, "text", text)), context));
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextOnJTextFieldInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("old");
        frame.getContentPane().add(field);

        snapshot(frame);
        setText(context.getRefOf(field), "new value");
        assertEquals("new value", field.getText());
    }

    @Test
    void setTextOnJPasswordFieldInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Login");
        JPasswordField password = new JPasswordField("old");
        frame.getContentPane().add(password);

        snapshot(frame);
        setText(context.getRefOf(password), "newpass");
        assertArrayEquals("newpass".toCharArray(), password.getPassword());
    }

    @Test
    void setTextOnJTextAreaInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Editor");
        JTextArea area = new JTextArea("old");
        frame.getContentPane().add(new JScrollPane(area));

        snapshot(frame);
        setText(context.getRefOf(area), "line1\nline2");
        assertEquals("line1\nline2", area.getText());
    }

    @Test
    void setTextOnDisabledFieldInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        frame.getContentPane().add(field);

        snapshot(frame);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "new"));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void setTextOnNonEditableFieldInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("read only");
        field.setEditable(false);
        frame.getContentPane().add(field);

        snapshot(frame);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "new"));
        assertEquals("Component is not editable", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextOnJTextFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Input");
        JTextField field = new JTextField("old");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        setText(context.getRefOf(field), "dialog value");
        assertEquals("dialog value", field.getText());
    }

    @Test
    void setTextOnJPasswordFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Login");
        JPasswordField password = new JPasswordField("old");
        dialog.getContentPane().add(password);

        snapshot(dialog);
        setText(context.getRefOf(password), "secret");
        assertArrayEquals("secret".toCharArray(), password.getPassword());
    }

    @Test
    void setEmptyStringClearsFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        JTextField field = new JTextField("something");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        setText(context.getRefOf(field), "");
        assertEquals("", field.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no set_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no set_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(dialog));
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        snapshot(dialog);
        // JOptionPane itself has no set_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(optionPane));
    }
}
