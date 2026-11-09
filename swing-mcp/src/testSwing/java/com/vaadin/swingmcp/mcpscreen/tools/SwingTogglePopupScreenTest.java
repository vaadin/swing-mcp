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
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingTogglePopupTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The happy paths live here because {@code doAccessibleAction} on a {@code JComboBox}
 * throws {@code HeadlessException} in headless mode.
 */
class SwingTogglePopupScreenTest extends AbstractScreenTest {

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
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private MCPProtocol.Content togglePopup(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(() -> togglePopupTool.execute(new Parameters(Map.of("ref", ref)), context));
        context.clearRefMap();
        executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — non-editable JComboBox inside JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void togglePopupOpensNonEditableComboInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(combo);
            MCPProtocol.Content result = togglePopup(ref);
            assertEquals("Dispatched toggle-popup on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
            assertTrue(combo.isPopupVisible(), "Popup should be open after toggle");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void togglePopupOpenThenCloseInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(combo);

            // execute directly, not togglePopup(), so the ref survives for the second toggle
            executeOnEDT(() -> togglePopupTool.execute(new Parameters(Map.of("ref", ref)), context));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
            assertTrue(combo.isPopupVisible(), "Popup should be open");

            executeOnEDT(() -> togglePopupTool.execute(new Parameters(Map.of("ref", ref)), context));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
            assertFalse(combo.isPopupVisible(), "Popup should be closed after second toggle");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void refMapClearedAfterSuccessfulToggle() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            int ref = context.getRefOf(combo);
            togglePopup(ref);

            MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                    () -> togglePopup(ref));
            assertEquals("Component with ref " + ref + " invalid — the ref map is empty: no swing_snapshot yet, or a successful mutation cleared it. Call swing_snapshot to rebuild it.",
                    ex.getMessage());
        } finally {
            frame.dispose();
        }
    }

    @Test
    void togglePopupOpensEditableComboInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        combo.setEditable(true);
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            togglePopup(context.getRefOf(combo));
            assertTrue(combo.isPopupVisible(), "Popup should be open on editable JComboBox");
        } finally {
            frame.dispose();
        }
    }

    @Test
    void togglePopupOpensComboInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y", "Z"});
        dialog.getContentPane().add(combo);
        dialog.pack();
        dialog.setVisible(true);
        try {
            snapshot(dialog);
            togglePopup(context.getRefOf(combo));
            assertTrue(combo.isPopupVisible(), "Popup should be open inside JDialog");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    void togglePopupOpensComboInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B", "C"});
        iframe.getContentPane().add(combo);
        iframe.setSize(200, 100);
        desktop.add(iframe);
        host.setSize(400, 300);
        host.setVisible(true);
        iframe.setVisible(true);
        try {
            snapshot(host);
            togglePopup(context.getRefOf(combo));
            assertTrue(combo.isPopupVisible(), "Popup should be open inside JInternalFrame");
        } finally {
            host.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertTogglePopupNotSupported(java.awt.Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> togglePopup(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        assertTogglePopupNotSupported(frame);
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        assertTogglePopupNotSupported(dialog);
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        assertTogglePopupNotSupported(optionPane);
    }

    /**
     * A heavyweight popup is a {@code Window} of its own, so {@code Window.getWindows()}
     * yields it as a root besides its place under the {@code JComboBox}; the root copy
     * must be filtered out.
     */
    @Test
    void openComboPopupShouldNotDuplicateInSnapshot() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Alpha", "Beta", "Gamma"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            executeOnEDT(() -> { combo.setPopupVisible(true); return null; });
            assertTrue(combo.isPopupVisible(), "Popup should be open");

            // The window filter of SwingMCP.getConsideredComponents()
            java.util.List<Component> allVisible = new java.util.ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (com.vaadin.swingmcp.mcp.SwingUtils.isVisible(w)
                        && !com.vaadin.swingmcp.mcp.SwingUtils.isRedundantPopupWindow(w)) {
                    allVisible.add(w);
                }
            }

            context.setConsideredComponents(allVisible);
            String output = executeOnEDT(() ->
                    snapshotTool.execute(new Parameters(Map.of()), context).getText());

            long popupMenuCount = output.lines()
                    .filter(l -> l.contains("JPopupMenu") || l.contains("popup_menu"))
                    .count();
            long jlistCount = output.lines()
                    .filter(l -> l.contains("JList") || l.contains("(list)"))
                    .count();

            assertTrue(popupMenuCount <= 1,
                    "JPopupMenu should appear at most once, but appeared " + popupMenuCount
                    + " times. Snapshot:\n" + output);
            assertTrue(jlistCount <= 1,
                    "JList should appear at most once, but appeared " + jlistCount
                    + " times. Snapshot:\n" + output);
        } finally {
            frame.dispose();
        }
    }

    @Test
    void swingTogglePopupViaMcpClient() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            mcpServer.setConsideredComponents(List.of(frame));

            MCPProtocol.CallToolResult snapshotResult = mcpClient.callTool("swing_snapshot", Map.of());
            String snapshotText = snapshotResult.getContent().get(0).getText();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ref=(\\d+)[^\\n]*toggle_popup").matcher(snapshotText);
            assertTrue(m.find(), "Snapshot should contain a toggle_popup component");
            int comboRef = Integer.parseInt(m.group(1));
            MCPProtocol.CallToolResult result = mcpClient.callTool("swing_toggle_popup", Map.of("ref", comboRef));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run

            assertNotEquals(Boolean.TRUE, result.getIsError(), "toggle_popup should succeed");
            assertTrue(combo.isPopupVisible(), "Popup should be open via MCP client");
        } finally {
            frame.dispose();
        }
    }
}
