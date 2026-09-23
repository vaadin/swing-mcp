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

import com.vaadin.swingmcp.mcp.DragRecordingPanel;
import com.vaadin.swingmcp.mcp.MouseEventRecorder;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingDragTool;
import com.vaadin.swingmcp.mcp.tools.SwingGetCellsTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.vaadin.swingmcp.mcp.JdkCapabilities.COMBO_IGNORES_PRESS_WHILE_NOT_SHOWING;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SwingDragScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDragTool dragTool;
    private SwingToolContext context;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        dragTool = new SwingDragTool();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "swing-drag-test");
            t.setDaemon(true);
            return t;
        });
        context = new SwingToolContext(executor);
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

    // ══════════════════════════════════════════════════════════════════════════
    // The drag matrix's JList and JComboBox rows — here, not in SwingDragToolTest: headless,
    // their UI delegate's press handler throws HeadlessException, which aborts the drag.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBoxAsSourceReceivesTheWholeDrag() throws Exception {
        assumeTrue(COMBO_IGNORES_PRESS_WHILE_NOT_SHOWING,
                "below Java 17 the press opens the popup of a combo not showing, which throws"
                        + " and aborts the drag (R_ui_delegate_press_throws)");
        JFrame frame = new JFrame("Drag Test");
        frame.setSize(400, 200);
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setBounds(0, 0, 100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setBounds(200, 0, 100, 50);
        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(combo);
        frame.getContentPane().add(target);

        snapshot(frame);
        MouseEventRecorder recorder = executeOnEDT(() -> MouseEventRecorder.attachTo(combo));
        drag(context.getRefOf(combo), context.getRefOf(target));

        assertEquals(MouseEventRecorder.SYNTHETIC_DRAG, recorder.getEventIds());
    }

    private static Point centerOf(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    @Test
    void jListAsSourceDragsFromItsCenter() throws Exception {
        JFrame frame = new JFrame("Drag Test");
        frame.setSize(400, 200);
        JList<String> list = new JList<>(new String[]{"Item A", "Item B", "Item C"});
        list.setBounds(0, 0, 100, 90);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setBounds(200, 0, 100, 50);
        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(list);
        frame.getContentPane().add(target);

        snapshot(frame);
        MouseEventRecorder recorder = executeOnEDT(() -> MouseEventRecorder.attachTo(list));
        drag(context.getRefOf(list), context.getRefOf(target));

        assertEquals(MouseEventRecorder.SYNTHETIC_DRAG, recorder.getEventIds());
        List<MouseEvent> events = recorder.getEvents();
        assertEquals(new Point(50, 45), events.get(0).getPoint(), "press: the list's center");
        // The target's center, in list-local coordinates.
        assertEquals(new Point(250, 25), events.get(6).getPoint(), "release: the target's center");
        assertFalse(target.wasDragged(), "every event goes to the source");
    }

    @Test
    void virtualChildItemAsSourceResolvesToHostJList() throws Exception {
        // A JList item is not a Component; the tool must walk getAccessibleParent() up to the JList.
        JFrame frame = new JFrame("Drag Test");
        frame.setSize(400, 200);
        JList<String> list = new JList<>(new String[]{"Item A", "Item B", "Item C"});
        list.setBounds(0, 0, 100, 90);
        frame.getContentPane().setLayout(null);
        frame.getContentPane().add(list);

        snapshot(frame);
        // get_cells replaces the ref map: ref 1 is the list, refs 2.. its items.
        executeOnEDT(() -> new SwingGetCellsTool().execute(
                new Parameters(Map.of("ref", context.getRefOf(list), "offset", 0, "length", 3)),
                context));
        MouseEventRecorder recorder = executeOnEDT(() -> MouseEventRecorder.attachTo(list));
        drag(2, 4); // Item A onto Item C

        assertEquals(MouseEventRecorder.SYNTHETIC_DRAG, recorder.getEventIds());
        List<MouseEvent> events = recorder.getEvents();
        assertEquals(executeOnEDT(() -> centerOf(list.getCellBounds(0, 0))),
                events.get(0).getPoint(), "press: Item A's center");
        assertEquals(executeOnEDT(() -> centerOf(list.getCellBounds(2, 2))),
                events.get(6).getPoint(), "release: Item C's center");
        // BasicListUI moves the selection with a drag, so it ends on the drop cell.
        assertEquals(2, (int) executeOnEDT(list::getSelectedIndex));
    }
}
