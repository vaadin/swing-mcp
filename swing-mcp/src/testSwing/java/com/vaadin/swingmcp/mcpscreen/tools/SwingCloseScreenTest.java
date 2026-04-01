package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.*;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingCloseScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingCloseTool closeTool;
    private SwingToolContext context;
    private Window currentWindow;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        closeTool = new SwingCloseTool();
        context = new SwingToolContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (currentWindow != null) {
            Window w = currentWindow;
            currentWindow = null;
            executeOnEDT(() -> { w.dispose(); return null; });
        }
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    /**
     * Calls swing_close and drains the EDT so the fire-and-forget invokeLater has run.
     */
    private void close(int ref) throws Exception {
        try {
            executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", ref)), context));
            // Drain the EDT: this no-op is queued after the fire-and-forget invokeLater
            executeOnEDT(() -> null);
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame with DISPOSE_ON_CLOSE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeWithDisposeOnCloseIsDismissed() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);
        close(context.getRefOf(frame));

        assertFalse(frame.isShowing(), "frame should no longer be showing");
    }

    @Test
    void jframeSnapshotShowsCloseAction() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);

        // Frame should have a ref (close action makes it ref-eligible)
        int ref = context.getRefOf(frame);
        assertTrue(ref > 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jdialogIsDismissed() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Confirm");
        currentWindow = dialog;
        executeOnEDT(() -> { dialog.setSize(200, 100); dialog.setVisible(true); return null; });

        snapshot(dialog);
        close(context.getRefOf(dialog));

        assertFalse(dialog.isShowing(), "dialog should no longer be showing");
    }

    @Test
    void jdialogSnapshotShowsCloseAction() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Confirm");
        currentWindow = dialog;
        executeOnEDT(() -> { dialog.setSize(200, 100); dialog.setVisible(true); return null; });

        snapshot(dialog);

        int ref = context.getRefOf(dialog);
        assertTrue(ref > 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DO_NOTHING_ON_CLOSE — tool dispatches WINDOW_CLOSING; window stays showing
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeDoNothingOnCloseWindowStaysShowing() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);
        close(context.getRefOf(frame));

        assertTrue(frame.isShowing(), "frame should still be showing — DO_NOTHING_ON_CLOSE");
    }

    @Test
    void jdialogDoNothingOnCloseWindowStaysShowing() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        currentWindow = dialog;
        executeOnEDT(() -> { dialog.setSize(200, 100); dialog.setVisible(true); return null; });

        snapshot(dialog);
        close(context.getRefOf(dialog));

        assertTrue(dialog.isShowing(), "dialog should still be showing — DO_NOTHING_ON_CLOSE");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Undecorated window
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void undecoratedJframeReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Undecorated");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        // Undecorated frame has no close action → no ref in snapshot
        snapshot(frame);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame),
                "Undecorated frame should have no ref (no close action)");

        // Force a ref into the map to test the error path directly
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXIT_ON_CLOSE — never gets close action in snapshot
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeWithExitOnCloseHasNoCloseActionInSnapshot() throws Exception {
        JFrame frame = new JFrame("Exit");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);

        // Frame should have no ref (supportsClose returns false for EXIT_ON_CLOSE)
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame),
                "EXIT_ON_CLOSE frame should have no ref");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EXIT_ON_CLOSE — stale ref returns MCP error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeWithExitOnCloseViaStaleRefReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Exit");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        // Manually force a ref to simulate a stale ref pointing at an EXIT_ON_CLOSE frame
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JOptionPane (screen test; no ref by default)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        currentWindow = dialog;
        executeOnEDT(() -> { dialog.setSize(200, 100); dialog.setVisible(true); return null; });

        snapshot(dialog);
        // JOptionPane has no close action and no ref — force a ref to test error path
        context.putRef(99, (Accessible) optionPane);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support close"));
    }
}
