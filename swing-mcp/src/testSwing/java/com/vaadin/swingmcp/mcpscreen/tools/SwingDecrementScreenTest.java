package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingDecrementTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Screen-mode tests for {@code swing_decrement} — JFrame/JDialog coverage.
 * Happy-path tests live here per the component matrix in T-009 BR-10.
 */
class SwingDecrementScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDecrementTool decrementTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        decrementTool = new SwingDecrementTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private MCPProtocol.Content decrement(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(() -> decrementTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        frame.getContentPane().add(spinner);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = decrement(ref);
            assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(4, spinner.getValue());
        } finally {
            frame.dispose();
        }
    }

    @Test
    void decrementSliderInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 50);
        frame.getContentPane().add(slider);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(slider);
            MCPProtocol.Content result = decrement(ref);
            assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(49, slider.getValue());
        } finally {
            frame.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
        dialog.getContentPane().add(spinner);
        dialog.pack();
        dialog.setVisible(true);
        try {
            snapshot(dialog);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = decrement(ref);
            assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(2, spinner.getValue());
        } finally {
            dialog.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        iframe.getContentPane().add(spinner);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        executeOnEDT(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
            return null;
        });
        try {
            snapshot(host);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = decrement(ref);
            assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(4, spinner.getValue());
        } finally {
            host.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no decrement action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no decrement action — it has no ref
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
        // JOptionPane itself has no decrement action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(optionPane));
    }
}
