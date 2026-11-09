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
import com.github.mvysny.tinymcpserver.Parameters;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
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

    /** Calls swing_iconify, then drains the EDT so its fire-and-forget action has run. */
    private MCPProtocol.Content iconify(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> iconifyTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null);
        return result;
    }

    /**
     * Shows a decorated JFrame that never takes focus, so its snapshot line carries no
     * WM-dependent {@code [focused]} state.
     */
    private JFrame showFrame(String title) throws Exception {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setFocusableWindowState(false);
        currentWindow = frame;
        executeOnEDT(() -> { frame.setSize(200, 100); frame.setVisible(true); return null; });
        return frame;
    }

    /** Requests ICONIFIED directly, as the user would through the title bar, and waits for the WM. */
    private static void iconifyDirectly(JFrame frame) throws Exception {
        executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
        awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);
        assertEquals(Frame.ICONIFIED, frame.getExtendedState() & Frame.ICONIFIED, "precondition: frame is iconified");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame — happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jframeIsIconified() throws Exception {
        JFrame frame = showFrame("Test");

        snapshot(frame);
        iconify(context.getRefOf(frame));
        awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

        assertEquals(Frame.ICONIFIED, frame.getExtendedState() & Frame.ICONIFIED, "frame should be iconified");
        assertEquals("- JFrame (frame) \"Test\" [ref=1, iconified] actions: close, restore\n"
                        + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                snapshot(frame));
    }

    @Test
    void jframeSnapshotShowsIconifyAction() throws Exception {
        JFrame frame = showFrame("Test");

        assertEquals("- JFrame (frame) \"Test\" [ref=1] actions: close, iconify", snapshot(frame));
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

        // No iconify action, so no ref: force one to reach the tool's own refusal.
        context.putRef(99, (Accessible) frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertEquals("Frame is undecorated and cannot be iconified. Call swing_snapshot to verify the current state", ex.getMessage());
    }

    @Test
    void alreadyIconifiedJframeDoesNotShowIconifyInSnapshot() throws Exception {
        JFrame frame = showFrame("Iconified");
        iconifyDirectly(frame);

        assertEquals("- JFrame (frame) \"Iconified\" [ref=1, iconified] actions: close, restore\n"
                        + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                snapshot(frame));
    }

    @Test
    void alreadyIconifiedJframeViaStaleRefReturnsMcpError() throws Exception {
        JFrame frame = showFrame("Iconified");

        snapshot(frame);
        int ref = context.getRefOf(frame);

        iconifyDirectly(frame);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> iconifyTool.execute(new Parameters(Map.of("ref", ref)), context)));
        assertEquals("Frame is already iconified. Call swing_snapshot to verify the current state", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — happy path
    // ══════════════════════════════════════════════════════════════════════════

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

        assertTrue(iframe.isIcon(), "internal frame should be iconified");

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

    @Test
    void vetoedIconifyLeavesFrameShowing() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

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
                "D_dispatched_echo: tool returns echo on dispatch even when listener vetoes");
        assertFalse(iframe.isIcon(), "vetoed iconify should leave frame non-iconified");
        assertTrue(iframe.isShowing(), "vetoed iconify should leave frame showing");
    }

    @Test
    void desktopIconDoesNotShowIconifyInSnapshot() throws Exception {
        JInternalFrame iframe = showInternalFrame(true);
        JFrame host = (JFrame) SwingUtilities.getWindowAncestor(iframe);

        executeOnEDT(() -> { iframe.setIcon(true); return null; });

        String text = snapshot(host);

        for (String line : text.split("\n")) {
            if (line.contains("JDesktopIcon")) {
                assertFalse(line.contains("iconify"),
                        "JDesktopIcon should not list iconify action (already iconified)");
            }
        }
    }

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
