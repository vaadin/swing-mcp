package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.SwingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen-mode tests for {@link SwingUtils#supportsIconify}.
 * <p>
 * Most of the logic in {@code supportsIconify} hinges on {@link Window#isShowing()},
 * which requires a real display, so these tests live alongside the other
 * screen-mode {@code SwingUtils} tests.
 */
class SwingUtilsSupportsIconifyTest extends AbstractScreenTest {

    private final List<Window> createdWindows = new ArrayList<>();

    @AfterEach
    void disposeCreatedWindows() throws Exception {
        for (Window w : createdWindows) {
            SwingUtilities.invokeAndWait(() -> {
                w.setVisible(false);
                w.dispose();
            });
        }
        createdWindows.clear();
    }

    private void show(Window w) throws Exception {
        createdWindows.add(w);
        SwingUtilities.invokeAndWait(() -> {
            w.setSize(200, 100);
            w.setVisible(true);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — positive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void showingDecoratedJFrame_supportsIconify() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertTrue(SwingUtils.supportsIconify(frame));
    }

    /**
     * A disabled JFrame must still advertise {@code iconify}: the OS
     * window decorations (the minimize button) remain functional regardless of
     * {@code setEnabled(false)}, so the AI client must be able to iconify the
     * window the same way a human user would.
     */
    @Test
    void disabledJFrame_stillSupportsIconify() throws Exception {
        JFrame frame = new JFrame("Disabled");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setEnabled(false));

        assertFalse(frame.isEnabled(), "precondition: frame is disabled");
        assertTrue(SwingUtils.supportsIconify(frame),
                "disabled JFrame should still support iconify (OS decorations stay live)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonFrameComponent_doesNotSupportIconify() {
        assertFalse(SwingUtils.supportsIconify(new JButton("OK")));
    }

    @Test
    void hiddenJFrame_doesNotSupportIconify() {
        JFrame frame = new JFrame("Hidden");
        createdWindows.add(frame);

        assertFalse(SwingUtils.supportsIconify(frame));
    }

    @Test
    void undecoratedJFrame_doesNotSupportIconify() throws Exception {
        JFrame frame = new JFrame("Undecorated");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertFalse(SwingUtils.supportsIconify(frame),
                "undecorated frames have no minimize button");
    }

    @Test
    void alreadyIconifiedJFrame_doesNotSupportIconify() throws Exception {
        JFrame frame = new JFrame("Iconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));

        assertFalse(SwingUtils.supportsIconify(frame),
                "already-iconified JFrame should not advertise iconify");
    }

    @Test
    void jDialog_doesNotSupportIconify() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Dialog", false);
        show(dialog);

        assertFalse(SwingUtils.supportsIconify(dialog),
                "JDialog cannot be iconified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — positive cases
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a JInternalFrame inside a JDesktopPane hosted by a JFrame.
     * The 4th constructor arg controls {@code iconifiable}.
     */
    private JInternalFrame showInternalFrame(boolean iconifiable) throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        // JInternalFrame(title, resizable, closable, maximizable, iconifiable)
        JInternalFrame iframe = new JInternalFrame("Doc", false, true, false, iconifiable);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        createdWindows.add(host);
        SwingUtilities.invokeAndWait(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
        });
        return iframe;
    }

    @Test
    void showingIconifiableJInternalFrame_supportsIconify() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        assertTrue(SwingUtils.supportsIconify(iframe));
    }

    @Test
    void disabledIconifiableJInternalFrame_stillSupportsIconify() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> iframe.setEnabled(false));

        assertFalse(iframe.isEnabled(), "precondition: iframe is disabled");
        assertTrue(SwingUtils.supportsIconify(iframe),
                "disabled JInternalFrame should still support iconify");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonIconifiableJInternalFrame_doesNotSupportIconify() throws Exception {
        JInternalFrame iframe = showInternalFrame(false);
        assertFalse(SwingUtils.supportsIconify(iframe),
                "non-iconifiable JInternalFrame has no iconify button");
    }

    @Test
    void hiddenJInternalFrame_doesNotSupportIconify() {
        JInternalFrame iframe = new JInternalFrame("Hidden", false, true, false, true);
        assertFalse(SwingUtils.supportsIconify(iframe),
                "not-showing JInternalFrame should not support iconify");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — always false (already iconified)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIcon_doesNotSupportIconify() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> {
            try { iframe.setIcon(true); } catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });
        JInternalFrame.JDesktopIcon icon = iframe.getDesktopIcon();

        assertFalse(SwingUtils.supportsIconify(icon),
                "JDesktopIcon is already iconified — iconify makes no sense");
    }
}
