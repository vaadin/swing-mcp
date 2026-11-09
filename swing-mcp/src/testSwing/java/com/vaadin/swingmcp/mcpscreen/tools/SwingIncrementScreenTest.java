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

import com.github.mvysny.tinymcpserver.Parameters;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.mcp.tools.SwingIncrementTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static com.vaadin.swingmcp.mcp.JdkCapabilities.SLIDER_HAS_ACCESSIBLE_ACTIONS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

class SwingIncrementScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingIncrementTool incrementTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        incrementTool = new SwingIncrementTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private MCPProtocol.Content increment(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(() -> incrementTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void incrementSpinnerInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        frame.getContentPane().add(spinner);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = increment(ref);
            assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(6, spinner.getValue());
        } finally {
            frame.dispose();
        }
    }

    @Test
    void incrementSliderInsideJFrame() throws Exception {
        assumeTrue(SLIDER_HAS_ACCESSIBLE_ACTIONS,
                "JSlider exposes increment only from Java 17 (R_jslider_actions_since_17)");
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 50);
        frame.getContentPane().add(slider);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(slider);
            MCPProtocol.Content result = increment(ref);
            assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(51, slider.getValue());
        } finally {
            frame.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void incrementSpinnerInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(3, 0, 10, 1));
        dialog.getContentPane().add(spinner);
        dialog.pack();
        dialog.setVisible(true);
        try {
            snapshot(dialog);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = increment(ref);
            assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(4, spinner.getValue());
        } finally {
            dialog.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void incrementSpinnerInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        iframe.getContentPane().add(spinner);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        executeOnEDT(() -> {
            host.setSize(400, 300);
            host.setVisible(true);
            iframe.setVisible(true);
            return null;
        });
        try {
            snapshot(host);
            int ref = context.getRefOf(spinner);
            MCPProtocol.Content result = increment(ref);
            assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertEquals(6, spinner.getValue());
        } finally {
            host.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertIncrementNotSupported(java.awt.Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        assertIncrementNotSupported(frame);
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        assertIncrementNotSupported(dialog);
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        assertIncrementNotSupported(optionPane);
    }
}
