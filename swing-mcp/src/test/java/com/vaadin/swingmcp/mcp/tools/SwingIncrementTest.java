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
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.vaadin.swingmcp.mcp.JdkCapabilities.SLIDER_HAS_ACCESSIBLE_ACTIONS;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code doAccessibleAction} works headless on both {@code JSpinner} and {@code JSlider}, so
 * every happy path lives here rather than in a screen test.
 */
class SwingIncrementTest extends AbstractHeadlessTest {

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
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content increment(int ref) throws Exception {
        MCPProtocol.Content result = incrementTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — JSpinner variants
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void incrementSpinnerNumberModel() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(6, spinner.getValue());
    }

    @Test
    void incrementSpinnerListModel() throws Exception {
        SpinnerListModel model = new SpinnerListModel(List.of("A", "B", "C"));
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        increment(context.getRefOf(spinner));
        assertEquals("B", spinner.getValue());
    }

    @Test
    void incrementSpinnerDateModel() throws Exception {
        Date initial = new Date(1000000L);
        SpinnerDateModel model = new SpinnerDateModel(initial, null, null, java.util.Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        increment(context.getRefOf(spinner));
        Date after = (Date) spinner.getValue();
        assertTrue(after.after(initial), "Date spinner should advance after increment");
    }

    @Test
    void incrementSlider() throws Exception {
        assumeTrue(SLIDER_HAS_ACCESSIBLE_ACTIONS,
                "JSlider exposes increment only from Java 17 (R_jslider_actions_since_17)");
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(51, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Boundary — value stays at maximum; the dispatch still echoes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void incrementSpinnerAtMaximumIsNoOp() throws Exception {
        // SpinnerNumberModel.getNextValue() returns null here, so the action is a silent no-op;
        // the echo confirms only the dispatch (D_dispatched_echo).
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(10, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText(),
                "Tool returns D_dispatched_echo echo on dispatch; the EDT action's no-op outcome is not reflected");
        assertEquals(10, spinner.getValue(), "Value should stay at max");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> increment(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void componentWithoutIncrementSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(context.getRefOf(button)));
        assertEquals(
                "JButton does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledSpinnerReturnsMcpError() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        spinner.setEnabled(false);
        snapshot(spinner);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void successReturnsEcho() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void refMapClearedAfterSuccessfulIncrement() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        increment(ref);

        assertThrows(MCPServerException.class, () -> increment(ref));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingIncrementViaMcpClient() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        mcpServer.setConsideredComponents(List.of(spinner));

        mcpClient.callTool("swing_snapshot", Map.of());
        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_increment", Map.of("ref", 1));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.getIsError(), "swing_increment should succeed");
        assertEquals(6, spinner.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported components
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(6, spinner.getValue());
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        // JSlider gained AccessibleAction in Java 17 (R_jslider_actions_since_17).
        // Below that the matrix answer is a refusal, and that is the correct result.
        if (!SLIDER_HAS_ACCESSIBLE_ACTIONS) {
            assertIncrementNotSupported(new JSlider(0, 100, 50));
            return;
        }
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);
        MCPProtocol.Content result = increment(ref);
        assertEquals("Dispatched increment on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(51, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — unsupported components (no increment support)
    // ══════════════════════════════════════════════════════════════════════════

    private void assertIncrementNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            return; // no ref (no actions) — cannot call increment
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(ref));
        String expectedClass = ComponentClassResolver.resolveClassName((javax.accessibility.Accessible) component);
        assertEquals(
                expectedClass + " does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertIncrementNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertIncrementNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertIncrementNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertIncrementNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertIncrementNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertIncrementNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertIncrementNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertIncrementNotSupported(new JToggleButton("Toggle"));
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
        assertIncrementNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertIncrementNotSupported(
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
        assertIncrementNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(99));
        assertEquals(
                "JMenu does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        snapshot(mb);
        assertIncrementNotSupported(menu);
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
                () -> increment(ref));
        assertEquals(
                "JMenuItem does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Tool"));
        snapshot(tb);
        int ref = context.getRefOf(tb.getComponent(0));
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> increment(ref));
        assertEquals(
                "JButton does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertIncrementNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertIncrementNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> increment(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> increment(99));
    }
}
