package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.DragRecordingPanel;
import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingDragTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingDragScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDragTool dragTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        dragTool = new SwingDragTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void drag(int sourceRef, int targetRef) throws Exception {
        try {
            executeOnEDT(() -> dragTool.execute(
                    new Parameters(Map.of("source_ref", sourceRef, "target_ref", targetRef)),
                    context));
            executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
        } finally {
            context.clearRefMap();
        }
    }

    private void dragToCoords(int sourceRef, int x, int y) throws Exception {
        try {
            executeOnEDT(() -> dragTool.execute(
                    new Parameters(Map.of("source_ref", sourceRef, "target_x", x, "target_y", y)),
                    context));
            executeOnEDT(() -> null); // drain EDT
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dragBetweenPanelsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Drag Test");
        frame.setLayout(null);
        frame.setSize(400, 200);

        DragRecordingPanel source = new DragRecordingPanel();
        source.setBounds(10, 10, 100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setBounds(250, 10, 100, 50);

        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(source);
        frame.getContentPane().add(target);

        snapshot(frame);
        drag(context.getRefOf(source), context.getRefOf(target));
        assertTrue(source.wasDragged(), "Drag between panels in JFrame should work");
    }

    @Test
    void dragToCoordsInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Drag Test");
        frame.setSize(400, 200);

        DragRecordingPanel source = new DragRecordingPanel();
        source.setBounds(10, 10, 100, 50);

        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(source);

        snapshot(frame);
        dragToCoords(context.getRefOf(source), 300, 100);
        assertTrue(source.wasDragged(),
                "Drag to coordinates in JFrame should work");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dragBetweenPanelsInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Drag Test");
        dialog.setSize(400, 200);

        DragRecordingPanel source = new DragRecordingPanel();
        source.setBounds(10, 10, 100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setBounds(250, 10, 100, 50);

        dialog.getContentPane().setLayout(null);
        dialog.getContentPane().add(source);
        dialog.getContentPane().add(target);

        snapshot(dialog);
        drag(context.getRefOf(source), context.getRefOf(target));
        assertTrue(source.wasDragged(), "Drag between panels in JDialog should work");
    }
}
