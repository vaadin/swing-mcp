package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import javax.accessibility.Accessible;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * MCP tool {@code swing_drag}: drags from one location to another.
 * <p>
 * Automatically selects the best dispatch strategy:
 * <ul>
 *   <li>When a graphical display is available and the source component is showing
 *       on screen, uses {@link java.awt.Robot} for real OS-level mouse events
 *       (compatible with both MouseListener-based drag and Java's DnD framework).</li>
 *   <li>Otherwise (headless environment or component not showing on screen), falls
 *       back to synthetic {@link Component#dispatchEvent} calls.</li>
 * </ul>
 *
 * @see <a href="tool-025-swing-drag.md">T-025</a>
 */
public class SwingDragTool extends AbstractSwingTool {

    private static final Logger LOG = Logger.getLogger(SwingDragTool.class.getName());

    @Override
    public String getName() {
        return TOOL_SWING_DRAG;
    }

    @Override
    public String getDescription() {
        return "Drag a UI component to another location. Dispatches mouse drag events "
                + "(PRESSED \u2192 DRAGGED \u2192 RELEASED). "
                + "Source: source_ref identifies the component; by default drags from its center. "
                + "Provide optional source_x/source_y (component-relative pixel offsets) to start "
                + "from a specific point within the component (e.g. a painted node on a canvas). "
                + "Target: target_ref identifies the drop component; by default drops at its center. "
                + "Provide optional target_x/target_y (component-relative pixel offsets) to drop "
                + "at a specific point within the target component. "
                + "Optional via: flat array of [ref, x, y, ...] triplets defining intermediate "
                + "waypoints the drag passes through (e.g. for self-edges that must exit and "
                + "re-enter a node). "
                + "Refs are obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("source_ref",
                        "The element reference number of the component to drag from. "
                                + "By default drags from the component's center.")
                .optionalInteger("source_x",
                        "Component-relative X pixel offset for the drag start position within "
                                + "the source component. Defaults to the component's center X. "
                                + "Must be provided together with source_y.")
                .optionalInteger("source_y",
                        "Component-relative Y pixel offset for the drag start position within "
                                + "the source component. Defaults to the component's center Y. "
                                + "Must be provided together with source_x.")
                .requiredInteger("target_ref",
                        "The element reference number of the component to drop onto. "
                                + "By default drops at the component's center.")
                .optionalInteger("target_x",
                        "Component-relative X pixel offset for the drop position within "
                                + "the target component. Defaults to the component's center X. "
                                + "Must be provided together with target_y.")
                .optionalInteger("target_y",
                        "Component-relative Y pixel offset for the drop position within "
                                + "the target component. Defaults to the component's center Y. "
                                + "Must be provided together with target_x.")
                .optionalArray("via",
                        "Flat array of [ref, x, y, ...] triplets defining intermediate waypoints "
                                + "the drag passes through before reaching the target. Each triplet: "
                                + "ref identifies the component, x/y are component-relative pixel offsets. "
                                + "Length must be divisible by 3. Example for a self-edge: "
                                + "[canvasRef, 200, 100] to route the drag outside a node and back.")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int sourceRef = params.getInt("source_ref");
        Integer sourceX = params.getIntOrNull("source_x");
        Integer sourceY = params.getIntOrNull("source_y");

        // BR-01: validate source_x/source_y pair
        if ((sourceX == null) != (sourceY == null)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Both 'source_x' and 'source_y' must be provided together");
        }

        int targetRef = params.getInt("target_ref");
        Integer targetX = params.getIntOrNull("target_x");
        Integer targetY = params.getIntOrNull("target_y");

        // BR-03: validate target_x/target_y pair
        if ((targetX == null) != (targetY == null)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Both 'target_x' and 'target_y' must be provided together");
        }

        // BR-13: validate via (if provided)
        List<Integer> viaRaw = params.getIntArrayOrNull("via");
        if (viaRaw != null && viaRaw.size() % 3 != 0) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter 'via' must contain triplets [ref, x, y, ...]; "
                            + "length " + viaRaw.size() + " is not divisible by 3");
        }

        // BR-02: look up source by ref
        Accessible sourceAccessible = context.getAccessibleByRef(sourceRef);

        // BR-06: resolve source to Component + press point (defaults to center)
        SwingUtils.ComponentAndPoint source = SwingUtils.resolveComponentAndPoint(sourceAccessible);
        if (source == null) {
            throw new MCPErrorResponseException(
                    "Source component could not be resolved to a displayable Component. "
                            + "Call swing_snapshot to verify available components");
        }

        // BR-01: override press point with component-relative offsets if provided
        if (sourceX != null) {
            source = new SwingUtils.ComponentAndPoint(source.component, sourceX, sourceY);
        }

        // BR-04: look up target by ref
        Accessible targetAccessible = context.getAccessibleByRef(targetRef);

        // BR-06: resolve target to Component + drop point (defaults to center)
        SwingUtils.ComponentAndPoint target = SwingUtils.resolveComponentAndPoint(targetAccessible);
        if (target == null) {
            throw new MCPErrorResponseException(
                    "Target component could not be resolved to a displayable Component. "
                            + "Call swing_snapshot to verify available components");
        }

        // BR-03: override drop point with component-relative offsets if provided
        if (targetX != null) {
            target = new SwingUtils.ComponentAndPoint(target.component, targetX, targetY);
        }

        // BR-13: resolve waypoint triplets
        List<SwingUtils.ComponentAndPoint> waypoints = resolveWaypoints(viaRaw, context);

        // BR-05: check effectively enabled
        if (!SwingUtils.isEffectivelyEnabled(sourceAccessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be dragged");
        }

        // BR-12: auto-detect dispatch strategy
        boolean useRobot = !GraphicsEnvironment.isHeadless() && source.component.isShowing();

        if (useRobot) {
            dispatchViaRobot(source, target, waypoints);
        } else {
            dispatchViaSynthetic(source, target, waypoints);
        }

        // BR-10: DR-010 success echo
        return echo(sourceRef, "ref=" + targetRef);
    }

    /**
     * Resolves the flat triplet array into a list of {@link SwingUtils.ComponentAndPoint}.
     */
    private List<SwingUtils.ComponentAndPoint> resolveWaypoints(List<Integer> viaRaw,
                                                                 SwingToolContext context) throws Exception {
        if (viaRaw == null || viaRaw.isEmpty()) {
            return Collections.emptyList();
        }

        List<SwingUtils.ComponentAndPoint> result = new ArrayList<>(viaRaw.size() / 3);
        for (int i = 0; i < viaRaw.size(); i += 3) {
            int ref = viaRaw.get(i);
            int x = viaRaw.get(i + 1);
            int y = viaRaw.get(i + 2);

            Accessible acc = context.getAccessibleByRef(ref);
            SwingUtils.ComponentAndPoint resolved = SwingUtils.resolveComponentAndPoint(acc);
            if (resolved == null) {
                throw new MCPErrorResponseException(
                        "Waypoint component (ref " + ref + ") could not be resolved to a displayable Component. "
                                + "Call swing_snapshot to verify available components");
            }
            result.add(new SwingUtils.ComponentAndPoint(resolved.component, x, y));
        }
        return result;
    }

    /**
     * Robot dispatch — real OS-level mouse events via {@link java.awt.Robot}.
     * All coordinates converted to screen-absolute.
     */
    private void dispatchViaRobot(SwingUtils.ComponentAndPoint source,
                                  SwingUtils.ComponentAndPoint target,
                                  List<SwingUtils.ComponentAndPoint> waypoints) {
        Point sourceOnScreen = source.component.getLocationOnScreen();
        int pressScreenX = sourceOnScreen.x + source.x;
        int pressScreenY = sourceOnScreen.y + source.y;

        Point targetOnScreen = target.component.getLocationOnScreen();
        int dropScreenX = targetOnScreen.x + target.x;
        int dropScreenY = targetOnScreen.y + target.y;

        List<int[]> screenWaypoints = new ArrayList<>(waypoints.size());
        for (SwingUtils.ComponentAndPoint wp : waypoints) {
            Point wpOnScreen = wp.component.getLocationOnScreen();
            screenWaypoints.add(new int[]{wpOnScreen.x + wp.x, wpOnScreen.y + wp.y});
        }

        LOG.fine(() -> String.format("Robot drag: (%d,%d) \u2192 %d waypoints \u2192 (%d,%d)",
                pressScreenX, pressScreenY, waypoints.size(), dropScreenX, dropScreenY));

        Runnable robotDrag = SwingUtils.createRobotDragAction(
                pressScreenX, pressScreenY, dropScreenX, dropScreenY, screenWaypoints);
        Thread t = new Thread(() -> {
            try {
                robotDrag.run();
            } catch (Exception e) {
                LOG.warning("Robot drag failed: " + e.getMessage());
            }
        }, "swing-drag-robot");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Synthetic dispatch — all events sent to the source component.
     * Used in headless environments or when the source component is not showing.
     */
    private void dispatchViaSynthetic(SwingUtils.ComponentAndPoint source,
                                      SwingUtils.ComponentAndPoint target,
                                      List<SwingUtils.ComponentAndPoint> waypoints) {
        // Convert target point to source-component-local coordinates
        Point targetPoint = new Point(target.x, target.y);
        Point converted = SwingUtilities.convertPoint(target.component, targetPoint, source.component);
        int localTargetX = converted.x;
        int localTargetY = converted.y;

        // Convert waypoints to source-component-local coordinates
        List<int[]> localWaypoints = new ArrayList<>(waypoints.size());
        for (SwingUtils.ComponentAndPoint wp : waypoints) {
            Point wpPoint = new Point(wp.x, wp.y);
            Point wpConverted = SwingUtilities.convertPoint(wp.component, wpPoint, source.component);
            localWaypoints.add(new int[]{wpConverted.x, wpConverted.y});
        }

        LOG.fine(() -> String.format("Synthetic drag on %s: (%d,%d) \u2192 %d waypoints \u2192 (%d,%d)",
                source.component.getClass().getSimpleName(),
                source.x, source.y, waypoints.size(), localTargetX, localTargetY));

        Runnable dragAction = SwingUtils.createDragAction(
                source.component, source.x, source.y, localTargetX, localTargetY, localWaypoints);
        SwingUtilities.invokeLater(dragAction);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
