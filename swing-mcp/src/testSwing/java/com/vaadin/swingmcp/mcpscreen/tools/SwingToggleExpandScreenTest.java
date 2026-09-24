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

import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToggleExpandTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only the top-level windows, which need a display; the {@code JTree} cases run headless
 * in {@code SwingToggleExpandTest}.
 */
class SwingToggleExpandScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingToggleExpandTool toggleExpandTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        toggleExpandTool = new SwingToggleExpandTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Object... roots) throws Exception {
        context.setConsideredComponents(
                Arrays.stream(roots)
                        .map(r -> (java.awt.Component) r)
                        .collect(java.util.stream.Collectors.toList()));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertToggleExpandNotSupported(java.awt.Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> executeOnEDT(() -> toggleExpandTool.execute(new Parameters(Map.of("ref", 99)), context)));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        assertToggleExpandNotSupported(frame);
        frame.dispose();
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        assertToggleExpandNotSupported(dialog);
        dialog.dispose();
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        host.setSize(400, 300);
        host.setVisible(true);
        try {
            snapshot(host);
            // It has a ref through its close/iconify actions, but no toggle_expand
            int ref = context.getRefOf(iframe);
            var ex = assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException.class,
                    () -> executeOnEDT(() -> {
                        toggleExpandTool.execute(new Parameters(Map.of("ref", ref)), context);
                        return null;
                    }));
            assertTrue(ex.getMessage().contains("does not support swing_toggle_expand"));
        } finally {
            host.dispose();
        }
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        host.setSize(400, 300);
        host.setVisible(true);
        try {
            snapshot(host);
            context.putRef(99, desktop);
            var ex = assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException.class,
                    () -> executeOnEDT(() -> {
                        toggleExpandTool.execute(new Parameters(Map.of("ref", 99)), context);
                        return null;
                    }));
            assertTrue(ex.getMessage().contains("does not support swing_toggle_expand"));
        } finally {
            host.dispose();
        }
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        assertToggleExpandNotSupported(optionPane);
        dialog.dispose();
    }
}
