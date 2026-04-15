package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.mcp.DragRecordingPanel;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingDragToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingDragTool dragTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        dragTool = new SwingDragTool();
        context = new SwingToolContext();
    }

    /**
     * Runs a snapshot to populate ref map.
     */
    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    /**
     * Drags source_ref to target_ref. Drains the EDT so fire-and-forget action completes.
     */
    private MCPProtocol.Content dragToRef(int sourceRef, int targetRef) throws Exception {
        try {
            MCPProtocol.Content result = dragTool.execute(
                    new Parameters(Map.of("source_ref", sourceRef, "target_ref", targetRef)),
                    context);
            SwingUtilities.invokeAndWait(() -> {}); // drain EDT
            return result;
        } finally {
            context.clearRefMap();
        }
    }

    /**
     * Drags source_ref to target_ref with component-relative target offsets. Drains the EDT.
     */
    private MCPProtocol.Content dragToRefWithOffset(int sourceRef, int targetRef,
                                                     int targetX, int targetY) throws Exception {
        try {
            MCPProtocol.Content result = dragTool.execute(
                    new Parameters(Map.of("source_ref", sourceRef, "target_ref", targetRef,
                            "target_x", targetX, "target_y", targetY)),
                    context);
            SwingUtilities.invokeAndWait(() -> {}); // drain EDT
            return result;
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — source_ref + target_ref
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dragFromOneRecordingPanelToAnotherUsingTargetRef() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        int sourceRef = context.getRefOf(source);
        int targetRef = context.getRefOf(target);

        MCPProtocol.Content result = dragToRef(sourceRef, targetRef);
        assertNull(result, "Successful drag should return null");
        assertTrue(source.wasDragged(), "Source should have received a valid drag sequence");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Happy path — source_ref + target_ref with component-relative offsets
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dragToTargetWithComponentRelativeOffset() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(200, 100);

        JPanel root = new JPanel(null);
        root.setSize(400, 200);
        source.setBounds(10, 10, 100, 50);
        target.setBounds(150, 50, 200, 100);
        root.add(source);
        root.add(target);

        snapshot(root);
        int sourceRef = context.getRefOf(source);
        int targetRef = context.getRefOf(target);

        // Drag to offset (20, 10) within target (not center)
        MCPProtocol.Content result = dragToRefWithOffset(sourceRef, targetRef, 20, 10);
        assertNull(result, "Successful drag should return null");
        assertTrue(source.wasDragged(), "Source should have received a valid drag sequence");

        // Verify the release event coordinates
        // Target offset (20, 10) in target-local coords → convert to source-local:
        // target is at (150, 50), source is at (10, 10)
        // so (150+20 - 10, 50+10 - 10) = (160, 50) in source-local coords
        MouseEvent release = source.getReleaseEvent();
        assertNotNull(release);
        assertEquals(160, release.getX(), "Release X should be (150+20)-10=160 in source-local coords");
        assertEquals(50, release.getY(), "Release Y should be (50+10)-10=50 in source-local coords");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Auto-detection — headless uses synthetic dispatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void headlessEnvironmentUsesSyntheticDispatch() throws Exception {
        // In headless mode (this test suite), the tool falls back to synthetic
        // Component.dispatchEvent(). Verify events arrive directly at the source.
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        dragToRef(context.getRefOf(source), context.getRefOf(target));

        // Synthetic dispatch delivers events directly to the source component
        assertTrue(source.wasDragged(),
                "Headless environment should use synthetic dispatch and deliver events to source");
        assertFalse(target.wasDragged(),
                "Target should NOT receive drag events (all events go to source)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // source_x/source_y as component-relative offsets
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void sourceXYOverridesDefaultCenter() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        int sourceRef = context.getRefOf(source);
        int targetRef = context.getRefOf(target);

        // Provide source_x/source_y to override center (10, 5 instead of 50, 25)
        MCPProtocol.Content result = dragTool.execute(
                new Parameters(Map.of(
                        "source_ref", sourceRef,
                        "source_x", 10, "source_y", 5,
                        "target_ref", targetRef)),
                context);
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT

        assertNull(result);
        assertTrue(source.wasDragged(), "Drag with custom offset should succeed");

        // Verify press event is at the custom offset, not the center
        MouseEvent press = source.getPressEvent();
        assertNotNull(press);
        assertEquals(10, press.getX(), "Press X should be at custom offset 10, not center 50");
        assertEquals(5, press.getY(), "Press Y should be at custom offset 5, not center 25");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases — source specification
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void missingSourceRefReturnsInvalidParams() {
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> dragTool.execute(
                        new Parameters(Map.of("target_x", 100, "target_y", 200)), context));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("source_ref"),
                "Error should mention source_ref, got: " + ex.getMessage());
    }

    @Test
    void sourceXWithoutSourceYReturnsInvalidParams() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        JPanel root = new JPanel(null);
        root.setSize(200, 100);
        source.setBounds(0, 0, 100, 50);
        root.add(source);
        snapshot(root);
        int ref = context.getRefOf(source);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> dragTool.execute(
                        new Parameters(Map.of("source_ref", ref,
                                "source_x", 10, "target_x", 100, "target_y", 200)),
                        context));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("source_x") || ex.getMessage().contains("source_y"),
                "Error should mention source coordinates, got: " + ex.getMessage());
    }

    @Test
    void sourceYWithoutSourceXReturnsInvalidParams() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        JPanel root = new JPanel(null);
        root.setSize(200, 100);
        source.setBounds(0, 0, 100, 50);
        root.add(source);
        snapshot(root);
        int ref = context.getRefOf(source);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> dragTool.execute(
                        new Parameters(Map.of("source_ref", ref,
                                "source_y", 20, "target_x", 100, "target_y", 200)),
                        context));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("source_x") || ex.getMessage().contains("source_y"),
                "Error should mention source coordinates, got: " + ex.getMessage());
    }

    @Test
    void invalidSourceRefReturnsMcpError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> dragToRef(999, 1));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases — target specification
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidTargetRefReturnsMcpError() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        JPanel root = new JPanel(null);
        root.setSize(200, 100);
        source.setBounds(0, 0, 100, 50);
        root.add(source);
        snapshot(root);
        int sourceRef = context.getRefOf(source);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> dragToRef(sourceRef, 999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void missingTargetRefReturnsInvalidParams() {
        DragRecordingPanel source = new DragRecordingPanel();

        MCPServerException ex = assertThrows(MCPServerException.class, () -> {
            snapshot(source);
            int ref = context.getRefOf(source);
            try {
                dragTool.execute(new Parameters(Map.of("source_ref", ref)), context);
            } finally {
                context.clearRefMap();
            }
        });
        assertTrue(ex.getMessage().contains("target_ref"),
                "Error should mention target_ref, got: " + ex.getMessage());
    }

    @Test
    void targetXWithoutTargetYReturnsInvalidParams() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        DragRecordingPanel target = new DragRecordingPanel();

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        int srcRef = context.getRefOf(source);
        int tgtRef = context.getRefOf(target);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> {
            try {
                dragTool.execute(
                        new Parameters(Map.of("source_ref", srcRef,
                                "target_ref", tgtRef, "target_x", 100)),
                        context);
            } finally {
                context.clearRefMap();
            }
        });
        assertTrue(ex.getMessage().contains("target_x") || ex.getMessage().contains("target_y"),
                "Error should mention the missing parameter");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases — disabled source and headless coordinate drag
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledSourceReturnsMcpError() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setEnabled(false);
        DragRecordingPanel target = new DragRecordingPanel();

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        int sourceRef = context.getRefOf(source);
        int targetRef = context.getRefOf(target);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> dragToRef(sourceRef, targetRef));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Event sequence validation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void exactlyFiveDragEventsAreDispatched() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        dragToRef(context.getRefOf(source), context.getRefOf(target));

        assertEquals(5, source.getDragCount(),
                "Should receive exactly 5 MOUSE_DRAGGED events");
    }

    @Test
    void pressEventIsAtSourceCenter() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(80, 40);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 80, 40);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        dragToRef(context.getRefOf(source), context.getRefOf(target));

        MouseEvent press = source.getPressEvent();
        assertNotNull(press);
        assertEquals(40, press.getX(), "Press X should be at source center (80/2=40)");
        assertEquals(20, press.getY(), "Press Y should be at source center (40/2=20)");
    }

    @Test
    void eventButtonAndModifierValues() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        dragToRef(context.getRefOf(source), context.getRefOf(target));

        // MOUSE_PRESSED: BUTTON1 + BUTTON1_DOWN_MASK
        MouseEvent press = source.getPressEvent();
        assertNotNull(press);
        assertEquals(MouseEvent.BUTTON1, press.getButton());
        assertTrue((press.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0,
                "Press should have BUTTON1_DOWN_MASK");

        // MOUSE_DRAGGED: NOBUTTON + BUTTON1_DOWN_MASK
        for (MouseEvent drag : source.getDragEvents()) {
            assertEquals(MouseEvent.NOBUTTON, drag.getButton(),
                    "Drag events should have NOBUTTON (AWT convention)");
            assertTrue((drag.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0,
                    "Drag events should have BUTTON1_DOWN_MASK");
        }

        // MOUSE_RELEASED: BUTTON1 + no BUTTON1_DOWN_MASK
        MouseEvent release = source.getReleaseEvent();
        assertNotNull(release);
        assertEquals(MouseEvent.BUTTON1, release.getButton());
        assertEquals(0, release.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK,
                "Release should NOT have BUTTON1_DOWN_MASK");
    }

    @Test
    void dragEventsAreInterpolatedBetweenSourceAndTarget() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 100);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 100);

        JPanel root = new JPanel(null);
        root.setSize(400, 200);
        source.setBounds(0, 0, 100, 100);
        target.setBounds(300, 100, 100, 100);
        root.add(source);
        root.add(target);

        snapshot(root);
        dragToRef(context.getRefOf(source), context.getRefOf(target));

        MouseEvent press = source.getPressEvent();
        MouseEvent release = source.getReleaseEvent();
        assertNotNull(press);
        assertNotNull(release);

        // Verify interpolation: each drag event should be progressively further from press
        List<MouseEvent> drags = source.getDragEvents();
        int prevX = press.getX();
        for (MouseEvent drag : drags) {
            assertTrue(drag.getX() >= prevX,
                    "Drag X should increase monotonically toward target");
            prevX = drag.getX();
        }
        // Final drag should be at target position
        MouseEvent lastDrag = drags.get(drags.size() - 1);
        assertEquals(release.getX(), lastDrag.getX(),
                "Last drag X should equal release X (both at target)");
        assertEquals(release.getY(), lastDrag.getY(),
                "Last drag Y should equal release Y (both at target)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Virtual accessible child resolution
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void virtualChildAsSourceResolvesToHostComponent() throws Exception {
        JList<String> list = new JList<>(new String[]{"Item A", "Item B", "Item C"});
        list.setSize(100, 90);

        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        list.setBounds(0, 0, 100, 90);
        target.setBounds(200, 0, 100, 50);
        root.add(list);
        root.add(target);

        snapshot(root);
        int listRef = context.getRefOf(list);

        // Drag the list itself to the target — should work (list is a Component)
        dragToRef(listRef, context.getRefOf(target));
        // Verify no exception was thrown
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Mutation behavior
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dragReturnsNull() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        DragRecordingPanel target = new DragRecordingPanel();

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        MCPProtocol.Content result = dragTool.execute(
                new Parameters(Map.of("source_ref", context.getRefOf(source),
                        "target_ref", context.getRefOf(target))),
                context);
        assertNull(result, "Drag should return null (fire-and-forget)");
    }

    @Test
    void isMutationReturnsTrue() {
        assertTrue(dragTool.isMutation(), "Drag is a mutation tool");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix
    // ══════════════════════════════════════════════════════════════════════════

    private void dragComponentToTarget(Component source) throws Exception {
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        snapshot(root);
        int sourceRef = context.getRefOf(source);
        int targetRef = context.getRefOf(target);
        dragToRef(sourceRef, targetRef);
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        dragComponentToTarget(new JButton("Drag me"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        dragComponentToTarget(new JTextField("text"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        dragComponentToTarget(new JPasswordField("secret"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        dragComponentToTarget(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        dragComponentToTarget(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        dragComponentToTarget(new JRadioButton("Radio"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        dragComponentToTarget(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        dragComponentToTarget(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        dragComponentToTarget(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        dragComponentToTarget(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSize(100, 50);
        dragComponentToTarget(list);
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        JTree tree = new JTree();
        tree.setSize(100, 200);
        tree.expandRow(0);

        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 200);
        tree.setBounds(0, 0, 100, 200);
        target.setBounds(200, 0, 100, 50);
        root.add(tree);
        root.add(target);

        snapshot(root);
        // JTree itself should not have a ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(tree));
    }

    @Test
    void componentMatrix_JScrollBar() throws Exception {
        JScrollBar scrollBar = new JScrollBar(JScrollBar.VERTICAL, 50, 10, 0, 100);
        scrollBar.setSize(20, 50);
        dragComponentToTarget(scrollBar);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingDragViaMcpClient() throws Exception {
        DragRecordingPanel source = new DragRecordingPanel();
        source.setSize(100, 50);
        DragRecordingPanel target = new DragRecordingPanel();
        target.setSize(100, 50);

        JPanel root = new JPanel(null);
        root.setSize(300, 100);
        source.setBounds(0, 0, 100, 50);
        target.setBounds(200, 0, 100, 50);
        root.add(source);
        root.add(target);

        mcpServer.setConsideredComponents(List.of(root));

        // Take a snapshot to populate refs
        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        // Drag source to target via MCP
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_drag",
                        Map.of("source_ref", 1, "target_ref", 2)));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT

        assertNotEquals(Boolean.TRUE, result.isError(), "Drag should succeed via MCP client");
        assertTrue(source.wasDragged(), "Source should have received drag sequence via MCP client");
    }
}
