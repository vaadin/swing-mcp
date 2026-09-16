package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.*;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingCloseScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingCloseTool closeTool;
    private SwingToolContext context;
    private Window currentWindow;
    private final List<Window> extraWindows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        closeTool = new SwingCloseTool();
        context = new SwingToolContext(Runnable::run);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (currentWindow != null) {
            Window w = currentWindow;
            currentWindow = null;
            executeOnEDT(() -> { w.dispose(); return null; });
        }
        for (Window w : extraWindows) {
            executeOnEDT(() -> { w.dispose(); return null; });
        }
        extraWindows.clear();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    /**
     * Calls swing_close and drains the EDT so the fire-and-forget invokeLater has run.
     * Returns the tool's success Content (D_dispatched_echo echo) so callers can assert on it.
     */
    private MCPProtocol.Content close(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> closeTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        // Drain the EDT: this no-op is queued after the fire-and-forget invokeLater
        executeOnEDT(() -> null);
        return result;
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
        int ref = context.getRefOf(frame);
        MCPProtocol.Content result = close(ref);

        assertEquals("Dispatched close on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "D_dispatched_echo: tool returns echo on dispatch even when listener vetoes");
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
        int ref = context.getRefOf(dialog);
        MCPProtocol.Content result = close(ref);

        assertEquals("Dispatched close on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "D_dispatched_echo: tool returns echo on dispatch even when listener vetoes");
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
        assertTrue(ex.getMessage().contains("does not support swing_close"));
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

        // Frame gets a ref (supportsIconify returns true), but close should still fail
        int ref = context.getRefOf(frame);
        assertTrue(ref > 0, "EXIT_ON_CLOSE frame should have a ref (iconify action)");
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", ref)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
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
        assertTrue(ex.getMessage().contains("does not support swing_close"));
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
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopPane component matrix
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        extraWindows.add(host);
        executeOnEDT(() -> { host.setSize(400, 300); host.setVisible(true); return null; });

        snapshot(host);
        context.putRef(99, (Accessible) desktop);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame helpers
    // ══════════════════════════════════════════════════════════════════════════

    private JInternalFrame showInternalFrame(boolean closable, int defaultCloseOp) throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", false, closable);
        iframe.setDefaultCloseOperation(defaultCloseOp);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        extraWindows.add(host);
        executeOnEDT(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
            return null;
        });
        return iframe;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame with DISPOSE_ON_CLOSE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jinternalFrameWithDisposeOnCloseIsDismissed() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.DISPOSE_ON_CLOSE);

        snapshot(SwingUtilities.getWindowAncestor(iframe));
        close(context.getRefOf(iframe));

        assertTrue(iframe.isClosed(), "internal frame should be closed (disposed)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame with DO_NOTHING_ON_CLOSE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jinternalFrameDoNothingOnCloseStaysShowing() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.DO_NOTHING_ON_CLOSE);

        snapshot(SwingUtilities.getWindowAncestor(iframe));
        int ref = context.getRefOf(iframe);
        MCPProtocol.Content result = close(ref);

        assertEquals("Dispatched close on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "D_dispatched_echo: tool returns echo on dispatch even when listener vetoes");
        assertTrue(iframe.isShowing(), "internal frame should still be showing — DO_NOTHING_ON_CLOSE");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame not closable (BR-10)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonClosableJInternalFrameReturnsMcpError() throws Exception {
        JInternalFrame iframe = showInternalFrame(false, WindowConstants.DISPOSE_ON_CLOSE);

        // Non-closable internal frame has no close action → no ref in snapshot
        snapshot(SwingUtilities.getWindowAncestor(iframe));

        // Force a ref to test the error path directly
        context.putRef(99, (Accessible) iframe);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame EXIT_ON_CLOSE — stale ref returns MCP error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jinternalFrameWithExitOnCloseViaStaleRefReturnsMcpError() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.EXIT_ON_CLOSE);

        // Manually force a ref to simulate a stale ref
        context.putRef(99, (Accessible) iframe);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a JInternalFrame inside a JDesktopPane, iconifies it, and returns
     * the JDesktopIcon. The frame must be iconifiable (4th constructor arg).
     */
    private JInternalFrame.JDesktopIcon showIconifiedFrame(boolean closable, int defaultCloseOp) throws Exception {
        // iconifiable=true is the 4th JInternalFrame constructor arg
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", false, closable, false, true);
        iframe.setDefaultCloseOperation(defaultCloseOp);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        extraWindows.add(host);
        executeOnEDT(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
            return null;
        });
        // Iconify
        executeOnEDT(() -> { iframe.setIcon(true); return null; });
        return iframe.getDesktopIcon();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon with DISPOSE_ON_CLOSE (BR-11)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIconWithDisposeOnCloseClosesUnderlyingFrame() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.DISPOSE_ON_CLOSE);
        JInternalFrame iframe = icon.getInternalFrame();

        snapshot(SwingUtilities.getWindowAncestor(icon));
        close(context.getRefOf(icon));

        assertTrue(iframe.isClosed(), "underlying internal frame should be closed (disposed)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon with DO_NOTHING_ON_CLOSE
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIconDoNothingOnCloseIconStaysShowing() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.DO_NOTHING_ON_CLOSE);

        snapshot(SwingUtilities.getWindowAncestor(icon));
        int ref = context.getRefOf(icon);
        MCPProtocol.Content result = close(ref);

        assertEquals("Dispatched close on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "D_dispatched_echo: tool returns echo on dispatch even when listener vetoes");
        assertTrue(icon.isShowing(), "desktop icon should still be showing — DO_NOTHING_ON_CLOSE");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon not closable (BR-11 → BR-10)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIconNotClosableReturnsMcpError() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(false, WindowConstants.DISPOSE_ON_CLOSE);

        // Non-closable → no close action → no ref in snapshot; force a ref
        context.putRef(99, (Accessible) icon);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon EXIT_ON_CLOSE (BR-11 → BR-09)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIconExitOnCloseReturnsMcpError() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.EXIT_ON_CLOSE);

        // EXIT_ON_CLOSE → no close action → no ref; force a ref
        context.putRef(99, (Accessible) icon);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }
}
