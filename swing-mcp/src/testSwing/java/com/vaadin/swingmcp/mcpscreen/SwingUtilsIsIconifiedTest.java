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
 * Screen-mode tests for {@link SwingUtils#isIconified}.
 * <p>
 * Frame and JInternalFrame iconification requires a real display
 * (the extended-state bits and {@code setIcon()} need a native peer),
 * so all tests live in the screen-mode source set.
 */
class SwingUtilsIsIconifiedTest extends AbstractScreenTest {

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
    // Frame (including JFrame) — positive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void iconifiedJFrame_isIconified() throws Exception {
        JFrame frame = new JFrame("Iconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));

        assertTrue(SwingUtils.isIconified(frame));
    }

    @Test
    void iconifiedAndMaximizedJFrame_isIconified() throws Exception {
        JFrame frame = new JFrame("Max+Icon");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() ->
                frame.setExtendedState(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED));

        assertTrue(SwingUtils.isIconified(frame),
                "MAXIMIZED_BOTH | ICONIFIED should still be detected as iconified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Frame (including JFrame) — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void normalJFrame_isNotIconified() throws Exception {
        JFrame frame = new JFrame("Normal");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertFalse(SwingUtils.isIconified(frame));
    }

    @Test
    void maximizedJFrame_isNotIconified() throws Exception {
        JFrame frame = new JFrame("Maximized");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() ->
                frame.setExtendedState(Frame.MAXIMIZED_BOTH));

        assertFalse(SwingUtils.isIconified(frame),
                "maximized-but-not-iconified frame is not iconified");
    }

    @Test
    void hiddenJFrame_isNotIconified() {
        JFrame frame = new JFrame("Hidden");
        createdWindows.add(frame);

        assertFalse(SwingUtils.isIconified(frame));
    }

    @Test
    void hiddenButIconifiedJFrame_isNotIconified() throws Exception {
        JFrame frame = new JFrame("HiddenIconified");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));
        assertTrue((frame.getExtendedState() & Frame.ICONIFIED) != 0, "precondition: ICONIFIED bit is set");
        SwingUtilities.invokeAndWait(() -> frame.setVisible(false));
        assertFalse(frame.isShowing(), "precondition: frame is hidden");

        assertFalse(SwingUtils.isIconified(frame),
                "hidden frame with ICONIFIED bit is not considered iconified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — positive case
    // ══════════════════════════════════════════════════════════════════════════

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

    /**
     * An iconified JInternalFrame is removed from the component tree and
     * replaced by a JDesktopIcon (DR-008), so {@code isShowing()} returns
     * {@code false} — and a hidden frame is not considered iconified.
     * The JDesktopIcon is the showing iconified representation.
     */
    @Test
    void iconifiedJInternalFrame_isNotIconifiedBecauseHidden() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> {
            try { iframe.setIcon(true); }
            catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });

        assertTrue(iframe.isIcon(), "precondition: frame is iconified");
        assertFalse(iframe.isShowing(), "precondition: iconified JInternalFrame is not showing");
        assertFalse(SwingUtils.isIconified(iframe));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — negative case
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void normalJInternalFrame_isNotIconified() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);

        assertFalse(SwingUtils.isIconified(iframe));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — not a window, never iconified
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIcon_isNotIconified() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        SwingUtilities.invokeAndWait(() -> {
            try { iframe.setIcon(true); }
            catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });
        JInternalFrame.JDesktopIcon icon = iframe.getDesktopIcon();

        assertFalse(SwingUtils.isIconified(icon),
                "JDesktopIcon is a component, not a window — not considered iconified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Non-window components — always false
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_isNotIconified() {
        assertFalse(SwingUtils.isIconified(new JButton("OK")));
    }

    @Test
    void jPanel_isNotIconified() {
        assertFalse(SwingUtils.isIconified(new JPanel()));
    }

    @Test
    void jLabel_isNotIconified() {
        assertFalse(SwingUtils.isIconified(new JLabel("Hi")));
    }

    @Test
    void jDialog_isNotIconified() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Dialog", false);
        show(dialog);

        assertFalse(SwingUtils.isIconified(dialog),
                "JDialog cannot be iconified");
    }
}
