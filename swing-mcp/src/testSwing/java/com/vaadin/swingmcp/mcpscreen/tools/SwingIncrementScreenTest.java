package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingIncrementTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Screen-mode tests for {@code swing_increment} — JFrame/JDialog coverage.
 * Happy-path tests live here per the component matrix in UC-008 BR-10.
 */
class SwingIncrementScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingIncrementTool incrementTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        incrementTool = new SwingIncrementTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private MCPProtocol.Content increment(int ref) throws Exception {
        try {
            return incrementTool.execute(new Parameters(Map.of("ref", ref)), context);
        } finally {
            context.clearRefMap();
        }
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
            MCPProtocol.Content result = increment(context.getRefOf(spinner));
            assertNull(result, "Success should return null");
            assertEquals(6, spinner.getValue());
        } finally {
            frame.dispose();
        }
    }

    @Test
    void incrementSliderInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 50);
        frame.getContentPane().add(slider);
        frame.pack();
        frame.setVisible(true);
        try {
            snapshot(frame);
            MCPProtocol.Content result = increment(context.getRefOf(slider));
            assertNull(result, "Success should return null");
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
            MCPProtocol.Content result = increment(context.getRefOf(spinner));
            assertNull(result, "Success should return null");
            assertEquals(4, spinner.getValue());
        } finally {
            dialog.dispose();
        }
    }
}
