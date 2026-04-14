package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.*;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
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
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingRestoreScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingRestoreTool restoreTool;
    private SwingToolContext context;
    private Window currentWindow;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        restoreTool = new SwingRestoreTool();
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

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(
                () -> snapshotTool.execute(new Parameters(Map.of()), context));
        return result == null ? null : result.getText();
    }

    /**
     * Calls swing_restore and drains the EDT so the fire-and-forget invokeLater has run.
     */
    private void restore(int ref) throws Exception {
        try {
            executeOnEDT(() -> restoreTool.execute(new Parameters(Map.of("ref", ref)), context));
            // Drain the EDT: this no-op is queued after the fire-and-forget invokeLater
            executeOnEDT(() -> null);
        } finally {
            context.clearRefMap();
        }
    }

    private JFrame showIconifiedJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> {
            frame.setSize(200, 100);
            frame.setVisible(true);
            return null;
        });
        executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
        return frame;
    }

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

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void restoringIconifiedJFrameRestoresTheFrame() throws Exception {
        JFrame frame = showIconifiedJFrame();
        assertTrue((frame.getExtendedState() & Frame.ICONIFIED) != 0, "precondition: frame is iconified");

        snapshot(frame);
        restore(context.getRefOf(frame));

        assertEquals(0, frame.getExtendedState() & Frame.ICONIFIED,
                "frame should no longer be iconified");

        String text = snapshot(frame);
        assertFalse(text.contains("iconified"), "snapshot should not show [iconified]");
        // restore action should no longer be listed
        for (String line : text.split("\n")) {
            if (line.contains("JFrame") && line.contains("actions:")) {
                assertFalse(line.contains("restore"),
                        "restored frame should not list restore action");
            }
        }
    }

    @Test
    void afterRestoringJFrameIconifyActionReappears() throws Exception {
        JFrame frame = showIconifiedJFrame();

        snapshot(frame);
        restore(context.getRefOf(frame));

        String text = snapshot(frame);
        for (String line : text.split("\n")) {
            if (line.contains("JFrame") && line.contains("actions:")) {
                assertTrue(line.contains("iconify"),
                        "restored frame should list iconify action again");
            }
        }
    }

    /**
     * Verifies that restoring an iconified+maximized JFrame clears only the ICONIFIED bit.
     * The tool passes {@code getExtendedState() & ~ICONIFIED} (preserving MAXIMIZED_BOTH)
     * to {@code setExtendedState}; however, whether MAXIMIZED_BOTH is actually preserved
     * depends on the platform window manager (Xvfb may drop it). The hard assertion is
     * that ICONIFIED is cleared — that's the tool's responsibility.
     */
    @Test
    void restoringIconifiedMaximizedJFrameClearsIconifiedBit() throws Exception {
        JFrame frame = new JFrame("MaxIcon");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> {
            frame.setSize(200, 100);
            frame.setVisible(true);
            return null;
        });
        // Maximize first, then iconify — mirrors real user interaction
        executeOnEDT(() -> { frame.setExtendedState(Frame.MAXIMIZED_BOTH); return null; });
        executeOnEDT(() -> {
            frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED);
            return null;
        });

        int preState = frame.getExtendedState();
        assertTrue((preState & Frame.ICONIFIED) != 0,
                "precondition: frame is iconified (state=" + preState + ")");

        snapshot(frame);
        restore(context.getRefOf(frame));

        int state = frame.getExtendedState();
        assertEquals(0, state & Frame.ICONIFIED, "ICONIFIED bit should be cleared");

        String text = snapshot(frame);
        assertFalse(text.contains("iconified"), "snapshot should not show [iconified]");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — snapshot actions
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void iconifiedJFrameSnapshotShowsRestoreAction() throws Exception {
        JFrame frame = showIconifiedJFrame();

        String text = snapshot(frame);

        for (String line : text.split("\n")) {
            if (line.contains("JFrame") && line.contains("actions:")) {
                assertTrue(line.contains("restore"),
                        "iconified JFrame should list restore action");
            }
        }
    }

    @Test
    void normalJFrameDoesNotShowRestoreInSnapshot() throws Exception {
        JFrame frame = new JFrame("Normal");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        String text = snapshot(frame);

        for (String line : text.split("\n")) {
            if (line.contains("JFrame") && line.contains("actions:")) {
                assertFalse(line.contains("restore"),
                        "non-iconified JFrame should not list restore action");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — negative case (stale ref)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonIconifiedJFrameViaStaleRefReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Normal");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        // Force a ref to a non-iconified frame to test the error path
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> restoreTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertEquals("Frame is not iconified", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void restoringDesktopIconRestoresJInternalFrame() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        // Iconify the internal frame to create a JDesktopIcon
        executeOnEDT(() -> { iframe.setIcon(true); return null; });
        assertTrue(iframe.isIcon(), "precondition: internal frame is iconified");

        // Snapshot shows JDesktopIcon, get its ref
        String text = snapshot(host);
        assertTrue(text.contains("JDesktopIcon"), "precondition: snapshot has JDesktopIcon");

        JInternalFrame.JDesktopIcon icon = iframe.getDesktopIcon();
        restore(context.getRefOf(icon));

        // After restore, the JInternalFrame should be back
        assertFalse(iframe.isIcon(), "internal frame should no longer be iconified");

        text = snapshot(host);
        assertTrue(text.contains("JInternalFrame"),
                "snapshot should show JInternalFrame after restore");
        assertFalse(text.contains("JDesktopIcon"),
                "snapshot should not contain JDesktopIcon after restore");
    }

    @Test
    void desktopIconSnapshotShowsRestoreAction() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        executeOnEDT(() -> { iframe.setIcon(true); return null; });

        String text = snapshot(host);

        for (String line : text.split("\n")) {
            if (line.contains("JDesktopIcon")) {
                assertTrue(line.contains("restore"),
                        "JDesktopIcon should list restore action");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VetoableChangeListener rejects restore
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void vetoedRestoreLeavesDesktopIconPresent() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        executeOnEDT(() -> { iframe.setIcon(true); return null; });

        // Install a VetoableChangeListener that rejects de-iconification
        iframe.addVetoableChangeListener(new VetoableChangeListener() {
            @Override
            public void vetoableChange(PropertyChangeEvent evt) throws PropertyVetoException {
                if (JInternalFrame.IS_ICON_PROPERTY.equals(evt.getPropertyName())
                        && Boolean.FALSE.equals(evt.getNewValue())) {
                    throw new PropertyVetoException("Rejected", evt);
                }
            }
        });

        snapshot(host);
        JInternalFrame.JDesktopIcon icon = iframe.getDesktopIcon();
        restore(context.getRefOf(icon));

        // Frame should still be iconified
        assertTrue(iframe.isIcon(), "vetoed restore should leave frame iconified");

        String text = snapshot(host);
        assertTrue(text.contains("JDesktopIcon"),
                "JDesktopIcon should still be present after vetoed restore");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame (non-iconified) — no restore action
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void normalJInternalFrameDoesNotShowRestoreInSnapshot() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        String text = snapshot(host);

        for (String line : text.split("\n")) {
            if (line.contains("JInternalFrame") && line.contains("actions:")) {
                assertFalse(line.contains("restore"),
                        "non-iconified JInternalFrame should not list restore action");
            }
        }
    }
}
