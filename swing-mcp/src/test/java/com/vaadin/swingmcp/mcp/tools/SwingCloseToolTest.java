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
import com.github.mvysny.tinymcpserver.Parameters;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingCloseToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingCloseTool closeTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        closeTool = new SwingCloseTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void close(int ref) throws Exception {
        closeTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Invalid ref
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> close(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — non-window components all return MCP error
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertCloseNotSupported(Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> close(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_close. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertCloseNotSupported(new JButton("OK"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertCloseNotSupported(new JCheckBox("Accept"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertCloseNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertCloseNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertCloseNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertCloseNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertCloseNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertCloseNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertCloseNotSupported(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertCloseNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option A");
        new ButtonGroup().add(rb);
        assertCloseNotSupported(rb);
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertCloseNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertCloseNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertCloseNotSupported(new JScrollPane(new JTextArea("text")));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertCloseNotSupported(tp);
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertCloseNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        assertCloseNotSupported(new JMenuBar());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        mb.add(menu);
        assertCloseNotSupported(menu);
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        assertCloseNotSupported(new JMenuItem("Open"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("B"));
        assertCloseNotSupported(tb);
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertCloseNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertCloseNotSupported(new JTree());
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        assertCloseNotSupported(new JOptionPane("Test"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertCloseNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertCloseNotSupported(iframe);
    }
}
