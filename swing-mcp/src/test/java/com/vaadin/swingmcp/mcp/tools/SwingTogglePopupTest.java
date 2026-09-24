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
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Error cases only: showing a popup needs a display, so the happy path is
 * {@code SwingTogglePopupScreenTest}.
 */
class SwingTogglePopupTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingTogglePopupTool togglePopupTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        togglePopupTool = new SwingTogglePopupTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void togglePopup(int ref) throws Exception {
        togglePopupTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        snapshot(combo);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> togglePopup(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void componentWithoutTogglePopupSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(context.getRefOf(button)));
        assertEquals(
                "JButton does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledComboBoxReturnsMcpError() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setEnabled(false);
        snapshot(combo);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(context.getRefOf(combo)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — every component refuses
    // ══════════════════════════════════════════════════════════════════════════

    private void assertTogglePopupNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            return; // no ref (no actions) — cannot call toggle_popup
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(ref));
        String expectedClass = ComponentClassResolver.resolveClassName((javax.accessibility.Accessible) component);
        assertEquals(
                expectedClass + " does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertTogglePopupNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertTogglePopupNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertTogglePopupNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertTogglePopupNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertTogglePopupNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertTogglePopupNotSupported(rb);
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertTogglePopupNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertTogglePopupNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertTogglePopupNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        snapshot(panel);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        snapshot(new JScrollPane(new JTextArea("content")));
        // Nothing to assert: a JScrollPane gets no ref.
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertTogglePopupNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertTogglePopupNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");
        snapshot(label);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertTogglePopupNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(99));
        assertEquals(
                "JMenu does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        assertTogglePopupNotSupported(menu);
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(item);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(ref));
        assertEquals(
                "JMenuItem does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Tool"));
        snapshot(tb);
        int ref = context.getRefOf(tb.getComponent(0));
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(ref));
        assertEquals(
                "JButton does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertTogglePopupNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertTogglePopupNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> togglePopup(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> togglePopup(99));
    }
}
