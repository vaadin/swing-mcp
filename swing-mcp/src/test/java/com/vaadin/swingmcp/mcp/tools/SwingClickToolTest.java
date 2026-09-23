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
import com.vaadin.swingmcp.mcp.ClickRecordingPanel;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingClickToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClickTool clickTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clickTool = new SwingClickTool();
        context = new SwingToolContext(Runnable::run);
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    /** Clicks, then clears the ref map as {@code SwingMCP.registerTool} does after a successful mutation. */
    private void click(int ref) throws Exception {
        clickTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickingButtonTriggersActionListener() throws Exception {
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));

        snapshot(button);
        click(context.getRefOf(button));
        assertTrue(clicked.get(), "Action listener should have been triggered");
    }

    @Test
    void clickingCheckboxTogglesState() throws Exception {
        JCheckBox checkBox = new JCheckBox("Accept");
        assertFalse(checkBox.isSelected());

        snapshot(checkBox);
        click(context.getRefOf(checkBox));
        assertTrue(checkBox.isSelected(), "Checkbox should be selected after click");
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> click(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void disabledButtonReturnsMcpErrorExplainingDisabled() throws Exception {
        JButton button = new JButton("Disabled");
        button.setEnabled(false);

        snapshot(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void componentWithoutClickSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);

        snapshot(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(slider)));
        assertEquals("JSlider does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void successReturnsEcho() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);

        MCPProtocol.Content result = clickTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched click on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void refsInvalidatedAfterClick() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        click(ref);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot after invalidation");
    }

    @Test
    void refsPreservedAfterValidationError() throws Exception {
        JButton disabled = new JButton("No");
        disabled.setEnabled(false);
        JButton enabled = new JButton("OK");
        JPanel panel = new JPanel();
        panel.add(disabled);
        panel.add(enabled);
        snapshot(panel);

        int disabledRef = context.getRefOf(disabled);
        int enabledRef = context.getRefOf(enabled);

        assertThrows(MCPErrorResponseException.class,
                () -> clickTool.execute(new Parameters(Map.of("ref", disabledRef)), context));

        AtomicBoolean clicked = new AtomicBoolean(false);
        enabled.addActionListener(e -> clicked.set(true));
        click(enabledRef);
        assertTrue(clicked.get(),
                "Button should still be clickable via its original ref after a validation error on another ref");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingClickViaMcpClient() throws Exception {
        JButton button = new JButton("OK");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        mcpServer.setConsideredComponents(List.of(button));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_click", Map.of("ref", 1));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.getIsError(), "Click should succeed");
        assertTrue(clicked.get(), "Action listener should have been triggered via MCP client");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Effectively enabled — parent chain
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledParentDoesNotPreventClick() throws Exception {
        // setEnabled(false) does not propagate to children (JDK-4177727, won't fix):
        // a user can still click this button. See D_mirror_swing_semantics.
        JPanel panel = new JPanel();
        JButton button = new JButton("Child");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));
        panel.add(button);
        panel.setEnabled(false);

        snapshot(panel);
        click(context.getRefOf(button));
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(clicked.get(),
                "Click on a button inside a disabled panel must still fire (Swing semantics)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JButton() throws Exception {
        JButton button = new JButton("Click me");
        AtomicBoolean clicked = new AtomicBoolean(false);
        button.addActionListener(e -> clicked.set(true));

        snapshot(button);
        click(context.getRefOf(button));
        assertTrue(clicked.get());
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text");

        snapshot(field);
        // JTextField has dynamic text actions, not click
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("secret");

        snapshot(field);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("text");

        snapshot(area);
        int ref = context.getRefOf(area);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Check");
        assertFalse(cb.isSelected());

        snapshot(cb);
        click(context.getRefOf(cb));
        assertTrue(cb.isSelected());
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        assertFalse(rb.isSelected());

        snapshot(rb);
        click(context.getRefOf(rb));
        assertTrue(rb.isSelected());
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});

        snapshot(combo);
        // JComboBox has toggle_popup, not click
        int ref = context.getRefOf(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        JToggleButton tb = new JToggleButton("Toggle");
        assertFalse(tb.isSelected());

        snapshot(tb);
        click(context.getRefOf(tb));
        assertTrue(tb.isSelected());
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));

        snapshot(spinner);
        // JSpinner has increment/decrement, not click
        int ref = context.getRefOf(spinner);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);

        snapshot(slider);
        // JSlider has increment/decrement, not click
        int ref = context.getRefOf(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.setName("TestPanel");

        snapshot(panel);
        // No actions, so no ref.
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));

        snapshot(sp);
        // Structural, so no ref.
        assertThrows(IllegalStateException.class, () -> context.getRefOf(sp));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());

        snapshot(tp);
        // Nothing to assert: a JTabbedPane.Page has no AccessibleAction, so no tab gets a
        // click ref; switching tabs goes through the selection tools.
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());

        snapshot(sp);
        // JSplitPane has AccessibleValue (get_value/set_value) but no click
        int ref = context.getRefOf(sp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");

        snapshot(label);
        // No actions, so no ref.
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);

        snapshot(pb);
        // JProgressBar has get_value but no click
        int ref = context.getRefOf(pb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenuBar is structural; the JMenu inside it does NOT get a click ref.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);

        snapshot(mb);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(menu),
                "JMenu must not receive a click ref per D_jmenu_not_clickable");
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable. The snapshot gives the JMenu no ref, so a test ref reaches
        // the swing_click refusal itself (design/architecture.md § Testing).
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);

        snapshot(mb);
        context.putRef(99, menu);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(99));
        assertEquals("JMenu does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        AtomicBoolean clicked = new AtomicBoolean(false);
        item.addActionListener(e -> clicked.set(true));
        menu.add(item);
        mb.add(menu);

        snapshot(mb);
        click(context.getRefOf(item));
        assertTrue(clicked.get());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);

        snapshot(tb);
        click(context.getRefOf(button));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));

        snapshot(tree);
        // JTree itself has no actions (selection suppressed, not truncated) — no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(tree));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});

        snapshot(list);
        // JList itself gets a ref (selection actions, no click)
        int listRef = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(listRef));
        assertTrue(ex.getMessage().contains("does not support swing_click"));

        snapshot(list);

        // JList children have click action — ref is listRef + 1
        int childRef = listRef + 1;
        click(childRef);
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> click(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> click(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tier 2 — MouseListener fallback
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void clickingPanelWithMouseListenerFiresSyntheticEvents() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();

        snapshot(panel);
        click(context.getRefOf(panel));
        assertTrue(panel.wasClicked(),
                "ClickRecordingPanel should report wasClicked() after swing_click");
    }

    @Test
    void syntheticMouseEventCoordinatesAtCenter() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        panel.setSize(200, 100);

        final int[] coords = new int[2];
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                coords[0] = e.getX();
                coords[1] = e.getY();
            }
        });

        snapshot(panel);
        click(context.getRefOf(panel));

        assertEquals(100, coords[0], "X should be at center (width/2)");
        assertEquals(50, coords[1], "Y should be at center (height/2)");
    }

    @Test
    void syntheticMouseEventUsesButton1() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();

        final int[] button = new int[1];
        panel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                button[0] = e.getButton();
            }
        });

        snapshot(panel);
        click(context.getRefOf(panel));

        assertEquals(java.awt.event.MouseEvent.BUTTON1, button[0], "Should use BUTTON1");
    }

    @Test
    void disabledPanelWithMouseListenerReturnsMcpError() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        panel.setEnabled(false);

        snapshot(panel);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> click(context.getRefOf(panel)));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
        assertFalse(panel.wasClicked(), "Panel should not have been clicked");
    }

    @Test
    void componentWithNoClickSupportAndNoMouseListenerReturnsMcpError() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());

        snapshot(sp);
        int ref = context.getRefOf(sp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }

    @Test
    void interactiveRoleWithMouseListenerIsNotClickable() throws Exception {
        // An interactive role skips Tier 2, app MouseListener or not.
        JSlider slider = new JSlider(0, 100, 50);
        slider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {}
        });

        snapshot(slider);
        int ref = context.getRefOf(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> click(ref));
        assertTrue(ex.getMessage().contains("does not support swing_click"));
    }
}
