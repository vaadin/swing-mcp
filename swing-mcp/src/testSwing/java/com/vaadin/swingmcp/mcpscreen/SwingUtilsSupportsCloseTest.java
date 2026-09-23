/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.SwingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SwingUtils#supportsClose} hinges on {@link Window#isShowing()}, which needs a
 * real display.
 */
class SwingUtilsSupportsCloseTest extends AbstractScreenTest {

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

    /**
     * The OS [×] button stays live under {@code setEnabled(false)}, so a user can still
     * close a disabled window.
     */
    @Test
    void disabledJFrame_stillSupportsClose() throws Exception {
        JFrame frame = new JFrame("Disabled");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);
        SwingUtilities.invokeAndWait(() -> frame.setEnabled(false));

        assertFalse(frame.isEnabled(), "precondition: frame is disabled");
        assertTrue(SwingUtils.supportsClose(frame),
                "disabled JFrame should still support close (OS decorations stay live)");
    }

    @Test
    void disabledJDialog_stillSupportsClose() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Disabled", false);
        show(dialog);
        SwingUtilities.invokeAndWait(() -> dialog.setEnabled(false));

        assertFalse(dialog.isEnabled(), "precondition: dialog is disabled");
        assertTrue(SwingUtils.supportsClose(dialog),
                "disabled JDialog should still support close (OS decorations stay live)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Baseline positive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void showingJFrame_supportsClose() throws Exception {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertTrue(SwingUtils.supportsClose(frame));
    }

    @Test
    void showingJDialog_supportsClose() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Test", false);
        show(dialog);

        assertTrue(SwingUtils.supportsClose(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonWindow_doesNotSupportClose() {
        assertFalse(SwingUtils.supportsClose(new JButton("OK")));
    }

    @Test
    void hiddenJFrame_doesNotSupportClose() {
        JFrame frame = new JFrame("Hidden");
        createdWindows.add(frame);

        assertFalse(SwingUtils.supportsClose(frame));
    }

    @Test
    void undecoratedJFrame_doesNotSupportClose() throws Exception {
        JFrame frame = new JFrame("Undecorated");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        show(frame);

        assertFalse(SwingUtils.supportsClose(frame),
                "undecorated frames have no [×] button");
    }

    @Test
    void undecoratedJDialog_doesNotSupportClose() throws Exception {
        JDialog dialog = new JDialog((JFrame) null, "Undecorated", false);
        dialog.setUndecorated(true);
        show(dialog);

        assertFalse(SwingUtils.supportsClose(dialog),
                "undecorated dialogs have no [×] button");
    }

    @Test
    void showingJWindow_doesNotSupportClose() throws Exception {
        JWindow window = new JWindow();
        show(window);

        assertFalse(SwingUtils.supportsClose(window),
                "a JWindow is always undecorated, so it has no [×] button");
    }

    @Test
    void exitOnCloseJFrame_doesNotSupportClose() throws Exception {
        JFrame frame = new JFrame("Exit");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        show(frame);

        assertFalse(SwingUtils.supportsClose(frame),
                "EXIT_ON_CLOSE would terminate the JVM and kill the MCP server");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — positive cases
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
        createdWindows.add(host);
        SwingUtilities.invokeAndWait(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
        });
        return iframe;
    }

    @Test
    void showingClosableJInternalFrame_supportsClose() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.DISPOSE_ON_CLOSE);
        assertTrue(SwingUtils.supportsClose(iframe));
    }

    @Test
    void disabledClosableJInternalFrame_stillSupportsClose() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.DISPOSE_ON_CLOSE);
        SwingUtilities.invokeAndWait(() -> iframe.setEnabled(false));

        assertFalse(iframe.isEnabled(), "precondition: iframe is disabled");
        assertTrue(SwingUtils.supportsClose(iframe),
                "disabled JInternalFrame should still support close");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonClosableJInternalFrame_doesNotSupportClose() throws Exception {
        JInternalFrame iframe = showInternalFrame(false, WindowConstants.DISPOSE_ON_CLOSE);
        assertFalse(SwingUtils.supportsClose(iframe),
                "non-closable JInternalFrame has no close button");
    }

    @Test
    void hiddenJInternalFrame_doesNotSupportClose() {
        JInternalFrame iframe = new JInternalFrame("Hidden", false, true);
        assertFalse(SwingUtils.supportsClose(iframe),
                "not-showing JInternalFrame should not support close");
    }

    @Test
    void exitOnCloseJInternalFrame_doesNotSupportClose() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.EXIT_ON_CLOSE);
        assertFalse(SwingUtils.supportsClose(iframe),
                "EXIT_ON_CLOSE JInternalFrame is refused defensively");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — positive cases
    // ══════════════════════════════════════════════════════════════════════════

    private JInternalFrame.JDesktopIcon showIconifiedFrame(boolean closable, int defaultCloseOp) throws Exception {
        JInternalFrame iframe = showInternalFrame(closable, defaultCloseOp);
        // showInternalFrame's frame is not iconifiable; swap in one that is
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);
        JDesktopPane desktop = (JDesktopPane) host.getContentPane();
        desktop.remove(iframe);
        JInternalFrame iconifiable = new JInternalFrame("Doc", false, closable, false, true);
        iconifiable.setDefaultCloseOperation(defaultCloseOp);
        iconifiable.setSize(150, 80);
        desktop.add(iconifiable);
        SwingUtilities.invokeAndWait(() -> {
            iconifiable.setVisible(true);
            try { iconifiable.setIcon(true); } catch (java.beans.PropertyVetoException e) { throw new RuntimeException(e); }
        });
        return iconifiable.getDesktopIcon();
    }

    @Test
    void showingClosableDesktopIcon_supportsClose() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.DISPOSE_ON_CLOSE);
        assertTrue(SwingUtils.supportsClose(icon),
                "showing JDesktopIcon with closable frame should support close");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — negative cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonClosableDesktopIcon_doesNotSupportClose() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(false, WindowConstants.DISPOSE_ON_CLOSE);
        assertFalse(SwingUtils.supportsClose(icon),
                "JDesktopIcon with non-closable frame should not support close");
    }

    @Test
    void exitOnCloseDesktopIcon_doesNotSupportClose() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.EXIT_ON_CLOSE);
        assertFalse(SwingUtils.supportsClose(icon),
                "JDesktopIcon with EXIT_ON_CLOSE frame should not support close");
    }
}
