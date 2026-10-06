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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.util.Arrays;
import java.util.Calendar;
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
class SwingDecrementTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDecrementTool decrementTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        decrementTool = new SwingDecrementTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content decrement(int ref) throws Exception {
        MCPProtocol.Content result = decrementTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — JSpinner variants
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerNumberModel() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(4, spinner.getValue());
    }

    @Test
    void decrementSpinnerListModel() throws Exception {
        SpinnerListModel model = new SpinnerListModel(List.of("A", "B", "C"));
        JSpinner spinner = new JSpinner(model);
        spinner.setValue("B");
        snapshot(spinner);
        decrement(context.getRefOf(spinner));
        assertEquals("A", spinner.getValue());
    }

    @Test
    void decrementSpinnerDateModel() throws Exception {
        Date initial = new Date(1_000_000L + 86_400_000L); // one day above epoch-ish
        SpinnerDateModel model = new SpinnerDateModel(initial, null, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        decrement(context.getRefOf(spinner));
        Date after = (Date) spinner.getValue();
        assertTrue(after.before(initial), "Date spinner should move back after decrement");
    }

    @Test
    void decrementSlider() throws Exception {
        assumeTrue(SLIDER_HAS_ACCESSIBLE_ACTIONS,
                "JSlider exposes decrement only from Java 17 (R_jslider_actions_since_17)");
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(49, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Boundary — value stays at minimum; the dispatch still echoes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void decrementSpinnerNumberModelAtMinimumIsNoOp() throws Exception {
        // SpinnerNumberModel.getPreviousValue() returns null here, so the action is a silent no-op;
        // the echo confirms only the dispatch (D_dispatched_echo).
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(0, spinner.getValue(), "Value should stay at min");
    }

    @Test
    void decrementSpinnerDateModelAtMinimumIsNoOp() throws Exception {
        // start == value, so getPreviousValue() returns null here too.
        Date min = new Date(1_000_000L);
        SpinnerDateModel model = new SpinnerDateModel(min, min, null, Calendar.DAY_OF_MONTH);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(min, spinner.getValue(), "Date should stay at min");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> decrement(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void componentWithoutDecrementSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(context.getRefOf(button)));
        assertEquals(
                "JButton does not support swing_decrement. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledSpinnerReturnsMcpError() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        spinner.setEnabled(false);
        snapshot(spinner);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void successReturnsEcho() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void refMapClearedAfterSuccessfulDecrement() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        decrement(ref);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> decrement(ref));
        assertEquals("Component with ref " + ref + " invalid \u2014 the ref map is empty: no swing_snapshot yet, or a successful mutation cleared it. Call swing_snapshot to rebuild it.", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingDecrementViaMcpClient() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        mcpServer.setConsideredComponents(List.of(spinner));

        mcpClient.callTool("swing_snapshot", Map.of());
        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_decrement", Map.of("ref", 1));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.getIsError(), "swing_decrement should succeed");
        assertEquals(4, spinner.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported components
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        int ref = context.getRefOf(spinner);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(4, spinner.getValue());
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        // JSlider gained AccessibleAction in Java 17 (R_jslider_actions_since_17).
        // Below that the matrix answer is a refusal, and that is the correct result.
        if (!SLIDER_HAS_ACCESSIBLE_ACTIONS) {
            assertDecrementNotSupported(new JSlider(0, 100, 50));
            return;
        }
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);
        MCPProtocol.Content result = decrement(ref);
        assertEquals("Dispatched decrement on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
        assertEquals(49, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — unsupported components (no decrement support)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertDecrementNotSupported(Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(99));
        assertEquals(
                component.getClass().getSimpleName() + " does not support swing_decrement. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertDecrementNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertDecrementNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        assertDecrementNotSupported(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertDecrementNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertDecrementNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertDecrementNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertDecrementNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertDecrementNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertDecrementNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertDecrementNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertDecrementNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertDecrementNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertDecrementNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertDecrementNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        assertDecrementNotSupported(new JMenuBar());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        assertDecrementNotSupported(menu);
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
                () -> decrement(ref));
        assertEquals(
                "JMenuItem does not support swing_decrement. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("Tool"));
        snapshot(tb);
        int ref = context.getRefOf(tb.getComponent(0));
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> decrement(ref));
        assertEquals(
                "JButton does not support swing_decrement. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertDecrementNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertDecrementNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertDecrementNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertDecrementNotSupported(iframe);
    }
}
