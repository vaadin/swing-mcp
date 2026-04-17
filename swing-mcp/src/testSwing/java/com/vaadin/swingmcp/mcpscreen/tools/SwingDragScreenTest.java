package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.DragRecordingPanel;
import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingDragTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SwingDragScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDragTool dragTool;
    private SwingToolContext context;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        dragTool = new SwingDragTool();
        context = new SwingToolContext();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "swing-drag-test");
            t.setDaemon(true);
            return t;
        });
        context.setExecutor(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
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

    private void dragWithTargetOffset(int sourceRef, int targetRef,
                                      int targetX, int targetY) throws Exception {
        try {
            executeOnEDT(() -> dragTool.execute(
                    new Parameters(Map.of("source_ref", sourceRef, "target_ref", targetRef,
                            "target_x", targetX, "target_y", targetY)),
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
    void dragWithTargetOffsetInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Drag Test");
        frame.setSize(400, 200);

        DragRecordingPanel source = new DragRecordingPanel();
        source.setBounds(10, 10, 100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setBounds(200, 50, 150, 100);

        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(source);
        frame.getContentPane().add(target);

        snapshot(frame);
        // Drag to offset (10, 10) within target instead of center
        dragWithTargetOffset(context.getRefOf(source), context.getRefOf(target), 10, 10);
        assertTrue(source.wasDragged(),
                "Drag to target with component-relative offset in JFrame should work");
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
