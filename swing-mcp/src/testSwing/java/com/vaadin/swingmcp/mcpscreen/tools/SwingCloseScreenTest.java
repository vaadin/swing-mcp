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

    /** Calls swing_close, then drains the EDT so its fire-and-forget action has run. */
    private MCPProtocol.Content close(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> closeTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null);
        return result;
    }

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

    @Test
    void undecoratedJframeReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Undecorated");
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame),
                "Undecorated frame should have no ref (no close action)");

        // No close action, so no ref: force one to reach the tool's own refusal.
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void jframeWithExitOnCloseHasNoCloseActionInSnapshot() throws Exception {
        JFrame frame = new JFrame("Exit");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        snapshot(frame);

        int ref = context.getRefOf(frame);
        assertTrue(ref > 0, "EXIT_ON_CLOSE frame should have a ref (iconify action)");
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", ref)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void jframeWithExitOnCloseViaStaleRefReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Exit");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });

        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

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
        context.putRef(99, (Accessible) optionPane);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

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

    @Test
    void jinternalFrameWithDisposeOnCloseIsDismissed() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.DISPOSE_ON_CLOSE);

        snapshot(SwingUtilities.getWindowAncestor(iframe));
        close(context.getRefOf(iframe));

        assertTrue(iframe.isClosed(), "internal frame should be closed (disposed)");
    }

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

    @Test
    void nonClosableJInternalFrameReturnsMcpError() throws Exception {
        JInternalFrame iframe = showInternalFrame(false, WindowConstants.DISPOSE_ON_CLOSE);

        snapshot(SwingUtilities.getWindowAncestor(iframe));

        context.putRef(99, (Accessible) iframe);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void jinternalFrameWithExitOnCloseViaStaleRefReturnsMcpError() throws Exception {
        JInternalFrame iframe = showInternalFrame(true, WindowConstants.EXIT_ON_CLOSE);

        context.putRef(99, (Accessible) iframe);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    /** Returns the desktop icon of an iconified JInternalFrame in a shown host frame. */
    private JInternalFrame.JDesktopIcon showIconifiedFrame(boolean closable, int defaultCloseOp) throws Exception {
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
        executeOnEDT(() -> { iframe.setIcon(true); return null; });
        return iframe.getDesktopIcon();
    }

    @Test
    void desktopIconWithDisposeOnCloseClosesUnderlyingFrame() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.DISPOSE_ON_CLOSE);
        JInternalFrame iframe = icon.getInternalFrame();

        snapshot(SwingUtilities.getWindowAncestor(icon));
        close(context.getRefOf(icon));

        assertTrue(iframe.isClosed(), "underlying internal frame should be closed (disposed)");
    }

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

    @Test
    void desktopIconNotClosableReturnsMcpError() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(false, WindowConstants.DISPOSE_ON_CLOSE);

        context.putRef(99, (Accessible) icon);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void desktopIconExitOnCloseReturnsMcpError() throws Exception {
        JInternalFrame.JDesktopIcon icon = showIconifiedFrame(true, WindowConstants.EXIT_ON_CLOSE);

        context.putRef(99, (Accessible) icon);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> closeTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }
}
