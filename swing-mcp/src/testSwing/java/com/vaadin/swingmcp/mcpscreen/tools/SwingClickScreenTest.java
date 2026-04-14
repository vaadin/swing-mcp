package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingClickTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingClickScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClickTool clickTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clickTool = new SwingClickTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void click(int ref) throws Exception {
        try {
            executeOnEDT(() -> clickTool.execute(new Parameters(Map.of("ref", ref)), context));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        frame.getContentPane().add(button);

        snapshot(frame);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JFrame should be clickable");
    }

    @Test
    void clickCheckBoxInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JCheckBox checkBox = new JCheckBox("Accept");
        frame.getContentPane().add(checkBox);
        assertFalse(checkBox.isSelected());

        snapshot(frame);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JFrame should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        frame.getContentPane().add(button);

        snapshot(frame);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Confirm");
        JButton button = new JButton("Yes");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        dialog.getContentPane().add(button);

        snapshot(dialog);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JDialog should be clickable");
    }

    @Test
    void clickCheckBoxInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        JCheckBox checkBox = new JCheckBox("Remember me");
        dialog.getContentPane().add(checkBox);
        assertFalse(checkBox.isSelected());

        snapshot(dialog);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JDialog should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJDialogFails() throws Exception {
        JDialog dialog = new JDialog();
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        dialog.getContentPane().add(button);

        snapshot(dialog);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        iframe.getContentPane().add(button);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JInternalFrame should be clickable");
    }

    @Test
    void clickCheckBoxInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JCheckBox checkBox = new JCheckBox("Accept");
        iframe.getContentPane().add(checkBox);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertFalse(checkBox.isSelected());

        snapshot(host);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JInternalFrame should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJInternalFrameFails() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        iframe.getContentPane().add(button);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no click action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no click action — it has no ref
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
        // JOptionPane itself has no click action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(optionPane));
    }
}
