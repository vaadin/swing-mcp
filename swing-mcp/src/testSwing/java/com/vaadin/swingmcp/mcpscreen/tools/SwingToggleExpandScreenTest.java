package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
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
 * Screen-mode tests for {@code swing_toggle_expand}.
 * Verifies that the tool fails cleanly on JFrame and JDialog themselves
 * (not JTree nodes), which require a display.
 */
class SwingToggleExpandScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingToggleExpandTool toggleExpandTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        toggleExpandTool = new SwingToggleExpandTool();
        context = new SwingToolContext();
    }

    private void snapshot(Object... roots) throws Exception {
        context.setConsideredComponents(
                Arrays.stream(roots)
                        .map(r -> (java.awt.Component) r)
                        .collect(java.util.stream.Collectors.toList()));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no toggle_expand action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
        frame.dispose();
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no toggle_expand action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(dialog));
        dialog.dispose();
    }
}
