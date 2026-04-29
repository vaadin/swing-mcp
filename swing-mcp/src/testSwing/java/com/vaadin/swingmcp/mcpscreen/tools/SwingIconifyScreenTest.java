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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingIconifyScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingIconifyTool iconifyTool;
    private SwingToolContext context;
    private Window currentWindow;
    private final List<Window> extraWindows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        iconifyTool = new SwingIconifyTool();
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

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(
                () -> snapshotTool.execute(new Parameters(Map.of()), context));
        return result == null ? null : result.getText();
    }

    /**
     * Calls swing_iconify and drains the EDT so the fire-and-forget invokeLater has run.
     * Returns the tool's success Content (DR-010 echo) so callers can assert on it.
     */
    private MCPProtocol.Content iconify(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> iconifyTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        // Drain the EDT: this no-op is queued after the fire-and-forget invokeLater
        executeOnEDT(() -> null);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeIsIconified() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);
        iconify(context.getRefOf(frame));

        assertTrue((frame.getExtendedState() & Frame.ICONIFIED) != 0,
                "frame should be iconified");

        // Snapshot should show [iconified] and no longer list iconify action
        String text = snapshot(frame);
        assertTrue(text.contains("iconified"), "snapshot should show [iconified]");
        assertFalse(text.contains("actions: ") && text.contains("iconify"),
                "iconified frame should not list iconify action");
    }

    @Test
    void jframeSnapshotShowsIconifyAction() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        String text = snapshot(frame);

        int ref = context.getRefOf(frame);
        assertTrue(ref > 0, "decorated JFrame should have a ref");
        assertTrue(text.contains("iconify"), "snapshot should list iconify action");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void undecoratedJframeReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Undecorated");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        // Undecorated frame has no iconify action; force a ref to test the error path
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertEquals("Frame is undecorated and cannot be iconified. Call swing_snapshot to verify the current state", ex.getMessage());
    }

    @Test
    void alreadyIconifiedJframeDoesNotShowIconifyInSnapshot() throws Exception {
        JFrame frame = new JFrame("Iconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });
        executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });

        String text = snapshot(frame);
        // Should show [iconified] but not the iconify action
        assertTrue(text.contains("iconified"), "snapshot should show [iconified]");
        // The frame line should not contain "iconify" as an action
        for (String line : text.split("\n")) {
            if (line.contains("JFrame") && line.contains("actions:")) {
                assertFalse(line.contains("iconify"),
                        "already-iconified JFrame should not list iconify action");
            }
        }
    }

    @Test
    void alreadyIconifiedJframeViaStaleRefReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Iconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        // Take snapshot while normal — gets a ref with iconify action
        snapshot(frame);
        int ref = context.getRefOf(frame);

        // Now iconify the frame behind the tool's back
        executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });

        // Stale ref should fail
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", ref)), context)));
        assertEquals("Frame is already iconified. Call swing_snapshot to verify the current state", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — happy path
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a JInternalFrame inside a JDesktopPane hosted by a JFrame.
     * JInternalFrame(title, resizable, closable, maximizable, iconifiable)
     */
    private JInternalFrame showInternalFrame(boolean iconifiable) throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", false, true, false, iconifiable);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        currentWindow = host;
        executeOnEDT(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
            return null;
        });
        return iframe;
    }

    @Test
    void iconifiableJInternalFrameIsIconified() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        snapshot(host);
        iconify(context.getRefOf(iframe));

        // After iconification, the JInternalFrame becomes a JDesktopIcon
        assertTrue(iframe.isIcon(), "internal frame should be iconified");

        // Snapshot should show JDesktopIcon, not the original JInternalFrame
        String text = snapshot(host);
        assertTrue(text.contains("JDesktopIcon"), "snapshot should contain JDesktopIcon");
    }

    @Test
    void iconifiableJInternalFrameSnapshotShowsIconifyAction() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        String text = snapshot(host);

        int ref = context.getRefOf(iframe);
        assertTrue(ref > 0, "iconifiable JInternalFrame should have a ref");
        // Find the JInternalFrame line and check it has iconify
        boolean found = false;
        for (String line : text.split("\n")) {
            if (line.contains("JInternalFrame") && line.contains("actions:")) {
                assertTrue(line.contains("iconify"),
                        "iconifiable JInternalFrame should list iconify action");
                found = true;
            }
        }
        assertTrue(found, "should find a JInternalFrame line with actions");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonIconifiableJInternalFrameReturnsMcpError() throws Exception {
        JInternalFrame iframe = showInternalFrame(false);

        // Non-iconifiable internal frame — force a ref to test the error path
        context.putRef(99, (Accessible) iframe);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertEquals("JInternalFrame is not iconifiable. Call swing_snapshot to verify the current state", ex.getMessage());
    }

    @Test
    void nonIconifiableJInternalFrameSnapshotDoesNotShowIconify() throws Exception {
        JInternalFrame iframe = showInternalFrame(false);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        String text = snapshot(host);

        for (String line : text.split("\n")) {
            if (line.contains("JInternalFrame") && line.contains("actions:")) {
                assertFalse(line.contains("iconify"),
                        "non-iconifiable JInternalFrame should not list iconify action");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VetoableChangeListener rejects iconify
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void vetoedIconifyLeavesFrameShowing() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        // Install a VetoableChangeListener that rejects iconification
        iframe.addVetoableChangeListener(new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                if (JInternalFrame.IS_ICON_PROPERTY.equals(evt.getPropertyName())
                        && Boolean.TRUE.equals(evt.getNewValue())) {
                    throw new PropertyVetoException("Rejected", evt);
                }
            }
        });

        snapshot(host);
        int ref = context.getRefOf(iframe);
        MCPProtocol.Content result = iconify(ref);

        assertEquals("Dispatched iconify on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "DR-010: tool returns echo on dispatch even when listener vetoes");
        // Frame should still be showing, not iconified
        assertFalse(iframe.isIcon(), "vetoed iconify should leave frame non-iconified");
        assertTrue(iframe.isShowing(), "vetoed iconify should leave frame showing");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — does not support swing_iconify
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIconDoesNotShowIconifyInSnapshot() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        // Iconify the internal frame to create a JDesktopIcon
        executeOnEDT(() -> { iframe.setIcon(true); return null; });

        String text = snapshot(host);

        // JDesktopIcon should not have iconify action
        for (String line : text.split("\n")) {
            if (line.contains("JDesktopIcon")) {
                assertFalse(line.contains("iconify"),
                        "JDesktopIcon should not list iconify action (already iconified)");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopPane component matrix — does not support swing_iconify
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        currentWindow = host;
        executeOnEDT(() -> { host.setSize(400, 300); host.setVisible(true); return null; });

        snapshot(host);
        context.putRef(99, (Accessible) desktop);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }
}
