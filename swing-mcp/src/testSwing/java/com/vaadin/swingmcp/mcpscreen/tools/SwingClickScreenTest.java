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
import com.vaadin.swingmcp.mcp.tools.SwingClickTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingClickScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClickTool clickTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clickTool = new SwingClickTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void click(int ref) throws Exception {
        executeOnEDT(() -> clickTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        frame.getContentPane().add(button);

        snapshot(frame);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JFrame should be clickable");
    }

    @Test
    void clickCheckBoxInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JCheckBox checkBox = new JCheckBox("Accept");
        frame.getContentPane().add(checkBox);
        assertFalse(checkBox.isSelected());

        snapshot(frame);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JFrame should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        frame.getContentPane().add(button);

        snapshot(frame);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.github.mvysny.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Confirm");
        JButton button = new JButton("Yes");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        dialog.getContentPane().add(button);

        snapshot(dialog);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JDialog should be clickable");
    }

    @Test
    void clickCheckBoxInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        JCheckBox checkBox = new JCheckBox("Remember me");
        dialog.getContentPane().add(checkBox);
        assertFalse(checkBox.isSelected());

        snapshot(dialog);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JDialog should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJDialogFails() throws Exception {
        JDialog dialog = new JDialog();
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        dialog.getContentPane().add(button);

        snapshot(dialog);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.github.mvysny.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickButtonInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        iframe.getContentPane().add(button);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Button inside JInternalFrame should be clickable");
    }

    @Test
    void clickCheckBoxInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JCheckBox checkBox = new JCheckBox("Accept");
        iframe.getContentPane().add(checkBox);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertFalse(checkBox.isSelected());

        snapshot(host);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox inside JInternalFrame should toggle on click");
    }

    @Test
    void clickDisabledButtonInsideJInternalFrameFails() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        iframe.getContentPane().add(button);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        int ref = context.getRefOf(button);
        var ex = assertThrows(com.github.mvysny.tinymcpserver.MCPErrorResponseException.class,
                () -> click(ref));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertClickNotSupported(java.awt.Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        assertClickNotSupported(frame);
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        assertClickNotSupported(dialog);
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        assertClickNotSupported(optionPane);
    }
}
