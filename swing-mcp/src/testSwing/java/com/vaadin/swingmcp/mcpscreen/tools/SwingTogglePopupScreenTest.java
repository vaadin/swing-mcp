package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingTogglePopupTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Screen-mode tests for {@code swing_toggle_popup}.
 * Happy-path tests live here because {@code doAccessibleAction} on {@code JComboBox}
 * throws {@code HeadlessException} in headless mode (see UC-007 BR-10).
 */
class SwingTogglePopupScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingTogglePopupTool togglePopupTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        togglePopupTool = new SwingTogglePopupTool();
        context = new SwingToolContext();
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
            assertEquals("Posted toggle-popup on ref=" + ref, result.getText());
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

            // Open — call execute directly (no ref-clearing) to keep the ref for the close call
            executeOnEDT(() -> togglePopupTool.execute(new Parameters(Map.of("ref", ref)), context));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
            assertTrue(combo.isPopupVisible(), "Popup should be open");

            // Close — same ref, still valid (ref-clearing is tested separately)
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

            // Ref map cleared — same ref should now be invalid
            assertThrows(com.vaadin.swingmcp.tinymcpserver.MCPServerException.class,
                    () -> togglePopup(ref));
        } finally {
            frame.dispose();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Editable JComboBox (BR-11)
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

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

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no toggle_popup action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no toggle_popup action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(dialog));
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        snapshot(dialog);
        // JOptionPane itself has no toggle_popup action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(optionPane));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Combo box popup duplication (HE-6 candidate)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reproducer for the combo box popup duplication bug.
     * When Window.getWindows() is used to collect roots (as in production),
     * the heavyweight popup window appears as a separate root AND as a nested
     * child inside the JComboBox, causing duplicate JPopupMenu/JList nodes.
     */
    @Test
    void openComboPopupShouldNotDuplicateInSnapshot() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Alpha", "Beta", "Gamma"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            // Open the popup
            executeOnEDT(() -> { combo.setPopupVisible(true); return null; });
            assertTrue(combo.isPopupVisible(), "Popup should be open");

            // Simulate production: collect all visible windows, applying the
            // same isRedundantPopupWindow filter as MCPServer.getConsideredComponents()
            java.util.List<Component> allVisible = new java.util.ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (com.vaadin.swingmcp.mcp.SwingUtils.isVisible(w)
                        && !com.vaadin.swingmcp.mcp.SwingUtils.isRedundantPopupWindow(w)) {
                    allVisible.add(w);
                }
            }

            // Snapshot with all visible windows as roots
            context.setConsideredComponents(allVisible);
            String output = executeOnEDT(() ->
                    snapshotTool.execute(new Parameters(Map.of()), context).getText());

            System.out.println("=== Snapshot with open combo popup ===");
            System.out.println(output);
            System.out.println("=== Visible windows: " + allVisible.size() + " ===");
            for (Component c : allVisible) {
                System.out.println("  " + c.getClass().getSimpleName()
                        + " size=" + c.getWidth() + "x" + c.getHeight());
            }

            // Count JPopupMenu and JList occurrences — should appear at most once each
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

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingTogglePopupViaMcpClient() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        frame.getContentPane().add(combo);
        frame.pack();
        frame.setVisible(true);
        try {
            mcpServer.setConsideredComponents(List.of(frame));

            McpSchema.CallToolResult snapshotResult = mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));
            String snapshotText = ((McpSchema.TextContent) snapshotResult.content().get(0)).text();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ref=(\\d+)[^\\n]*toggle_popup").matcher(snapshotText);
            assertTrue(m.find(), "Snapshot should contain a toggle_popup component");
            int comboRef = Integer.parseInt(m.group(1));
            McpSchema.CallToolResult result = mcpClient.callTool(
                    new McpSchema.CallToolRequest("swing_toggle_popup", Map.of("ref", comboRef)));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run

            assertNotEquals(Boolean.TRUE, result.isError(), "toggle_popup should succeed");
            assertTrue(combo.isPopupVisible(), "Popup should be open via MCP client");
        } finally {
            frame.dispose();
        }
    }
}
