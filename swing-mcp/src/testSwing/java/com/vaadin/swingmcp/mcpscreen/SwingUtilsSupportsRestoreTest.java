package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.SwingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Screen-mode tests for {@link SwingUtils#supportsRestore}.
 */
class SwingUtilsSupportsRestoreTest extends AbstractScreenTest {

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

    private JInternalFrame showInternalFrame(boolean iconifiable) throws Exception {
        JFrame host = new JFrame("Host");
        host.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
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

    // ══════════════════════════════════════════════════════════════════════════
    // Frame (including JFrame) — positive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void iconifiedJFrame_supportsRestore() throws Exception {
        JFrame frame = new JFrame("Iconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));

        assertTrue(SwingUtils.supportsRestore(frame));
    }

    @Test
    void iconifiedAndMaximizedJFrame_supportsRestore() throws Exception {
        JFrame frame = new JFrame("Max+Icon");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() ->
                frame.setExtendedState(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED));

        assertTrue(SwingUtils.supportsRestore(frame));
    }

    /**
     * A disabled JFrame must still advertise {@code restore}: the OS
     * window decorations remain functional regardless of
     * {@code setEnabled(false)}.
     */
    @Test
    void disabledIconifiedJFrame_stillSupportsRestore() throws Exception {
        JFrame frame = new JFrame("Disabled");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> {
            frame.setExtendedState(Frame.ICONIFIED);
            frame.setEnabled(false);
        });

        assertFalse(frame.isEnabled(), "precondition: frame is disabled");
        assertTrue(SwingUtils.supportsRestore(frame),
                "disabled iconified JFrame should still support restore");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Frame (including JFrame) — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void normalJFrame_doesNotSupportRestore() throws Exception {
        JFrame frame = new JFrame("Normal");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertFalse(SwingUtils.supportsRestore(frame));
    }

    @Test
    void maximizedJFrame_doesNotSupportRestore() throws Exception {
        JFrame frame = new JFrame("Maximized");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() ->
                frame.setExtendedState(Frame.MAXIMIZED_BOTH));

        assertFalse(SwingUtils.supportsRestore(frame),
                "maximized-but-not-iconified frame cannot be restored");
    }

    @Test
    void hiddenJFrame_doesNotSupportRestore() {
        JFrame frame = new JFrame("Hidden");
        createdWindows.add(frame);

        assertFalse(SwingUtils.supportsRestore(frame));
    }

    @Test
    void hiddenButIconifiedJFrame_doesNotSupportRestore() throws Exception {
        JFrame frame = new JFrame("HiddenIconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));
        SwingUtilities.invokeAndWait(() -> frame.setVisible(false));
        assertFalse(frame.isShowing(), "precondition: frame is hidden");

        assertFalse(SwingUtils.supportsRestore(frame));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — positive case
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void showingDesktopIcon_supportsRestore() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> {
            try { iframe.setIcon(true); }
            catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });
        JInternalFrame.JDesktopIcon icon = iframe.getDesktopIcon();

        assertTrue(icon.isShowing(), "precondition: icon is showing");
        assertTrue(SwingUtils.supportsRestore(icon));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void normalJInternalFrame_doesNotSupportRestore() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);

        assertFalse(SwingUtils.supportsRestore(iframe));
    }

    /**
     * An iconified JInternalFrame is not showing (DR-008), so it does
     * not support restore. The JDesktopIcon is the restorable target.
     */
    @Test
    void iconifiedJInternalFrame_doesNotSupportRestore() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> {
            try { iframe.setIcon(true); }
            catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });

        assertFalse(iframe.isShowing(), "precondition: iconified JInternalFrame is hidden");
        assertFalse(SwingUtils.supportsRestore(iframe));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Non-window components — always false
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportRestore() {
        assertFalse(SwingUtils.supportsRestore(new JButton("OK")));
    }

    @Test
    void jPanel_doesNotSupportRestore() {
        assertFalse(SwingUtils.supportsRestore(new JPanel()));
    }

    @Test
    void jLabel_doesNotSupportRestore() {
        assertFalse(SwingUtils.supportsRestore(new JLabel("Hi")));
    }

    @Test
    void jDialog_doesNotSupportRestore() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Dialog", false);
        show(dialog);

        assertFalse(SwingUtils.supportsRestore(dialog));
    }
}
