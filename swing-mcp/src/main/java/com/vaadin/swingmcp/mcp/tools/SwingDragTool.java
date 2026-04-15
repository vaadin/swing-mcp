package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import javax.accessibility.Accessible;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.util.logging.Logger;

/**
 * MCP tool {@code swing_drag}: drags from one location to another.
 * <p>
 * Automatically selects the best dispatch strategy:
 * <ul>
 *   <li>When {@code source_ref} is used and a graphical display is available
 *       with the source component showing on screen, uses {@link java.awt.Robot}
 *       for real OS-level mouse events (compatible with both MouseListener-based
 *       drag and Java's DnD framework). Otherwise falls back to synthetic
 *       {@link Component#dispatchEvent} calls.</li>
 *   <li>When {@code source_x}/{@code source_y} are used, always uses
 *       {@link java.awt.Robot} (requires a graphical display).</li>
 * </ul>
 *
 * @see <a href="use-case-024-swing-drag.md">UC-024</a>
 */
public class SwingDragTool extends AbstractSwingTool {

    private static final Logger LOG = Logger.getLogger(SwingDragTool.class.getName());

    @Override
    public String getName() {
        return TOOL_SWING_DRAG;
    }

    @Override
    public String getDescription() {
        return "Drag from one location to another. Dispatches mouse drag events "
                + "(PRESSED \u2192 DRAGGED \u2192 RELEASED). "
                + "Source: provide source_ref (component reference, drags from center) "
                + "or source_x/source_y (window-relative pixel coordinates, "
                + "for custom-painted items without refs). "
                + "Target: provide target_ref (component reference, drops at center) "
                + "or target_x/target_y (window-relative pixel coordinates). "
                + "Refs are obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .optionalInteger("source_ref",
                        "The element reference number of the component to drag from (drags from center). "
                                + "If provided, source_x/source_y are ignored.")
                .optionalInteger("source_x",
                        "Window-relative X pixel coordinate for the drag start position. "
                                + "Use for custom-painted items that have no ref. "
                                + "Must be provided together with source_y when source_ref is not specified.")
                .optionalInteger("source_y",
                        "Window-relative Y pixel coordinate for the drag start position. "
                                + "Must be provided together with source_x when source_ref is not specified.")
                .optionalInteger("target_ref",
                        "The element reference number of the component to drop onto (uses center). "
                                + "If provided, target_x/target_y are ignored.")
                .optionalInteger("target_x",
                        "Window-relative X pixel coordinate for the drop position. "
                                + "Must be provided together with target_y when target_ref is not specified.")
                .optionalInteger("target_y",
                        "Window-relative Y pixel coordinate for the drop position. "
                                + "Must be provided together with target_x when target_ref is not specified.")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        Integer sourceRef = params.getIntOrNull("source_ref");
        Integer sourceX = params.getIntOrNull("source_x");
        Integer sourceY = params.getIntOrNull("source_y");

        // BR-01: source specification validation
        if (sourceRef == null) {
            if (sourceX == null && sourceY == null) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Either 'source_ref' or both 'source_x' and 'source_y' must be provided");
            }
            if (sourceX == null || sourceY == null) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Both 'source_x' and 'source_y' must be provided together");
            }
        }

        // BR-03: target specification validation
        Integer targetRef = params.getIntOrNull("target_ref");
        Integer targetX = params.getIntOrNull("target_x");
        Integer targetY = params.getIntOrNull("target_y");

        if (targetRef == null) {
            if (targetX == null && targetY == null) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Either 'target_ref' or both 'target_x' and 'target_y' must be provided");
            }
            if (targetX == null || targetY == null) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Both 'target_x' and 'target_y' must be provided together");
            }
        }

        if (sourceRef != null) {
            executeWithSourceRef(sourceRef, targetRef, targetX, targetY, context);
        } else {
            executeWithSourceCoords(sourceX, sourceY, targetRef, targetX, targetY, context);
        }

        return null;
    }

    /**
     * Source identified by ref — resolve accessible, check enabled,
     * auto-detect Robot vs synthetic.
     */
    private void executeWithSourceRef(int sourceRef,
                                      Integer targetRef, Integer targetX, Integer targetY,
                                      SwingToolContext context) throws Exception {
        // BR-02: look up source by ref
        Accessible sourceAccessible = context.getAccessibleByRef(sourceRef);

        // BR-06: resolve source to Component + press point
        SwingUtils.ComponentAndPoint source = SwingUtils.resolveComponentAndPoint(sourceAccessible);
        if (source == null) {
            throw new MCPErrorResponseException(
                    "Source component could not be resolved to a displayable Component. "
                            + "Call swing_snapshot to verify available components");
        }

        // BR-05: check effectively enabled
        if (!SwingUtils.isEffectivelyEnabled(sourceAccessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be dragged");
        }

        // BR-12: auto-detect dispatch strategy
        boolean useRobot = !GraphicsEnvironment.isHeadless() && source.component.isShowing();

        if (useRobot) {
            Window window = (Window) SwingUtilities.getAncestorOfClass(Window.class, source.component);
            Point sourceOnScreen = source.component.getLocationOnScreen();
            int pressScreenX = sourceOnScreen.x + source.x;
            int pressScreenY = sourceOnScreen.y + source.y;

            int[] targetScreen = resolveTargetScreenCoords(targetRef, targetX, targetY, window, context);

            LOG.fine(() -> String.format("Robot drag (ref): (%d,%d) \u2192 (%d,%d)",
                    pressScreenX, pressScreenY, targetScreen[0], targetScreen[1]));

            Runnable robotDrag = SwingUtils.createRobotDragAction(
                    pressScreenX, pressScreenY, targetScreen[0], targetScreen[1]);
            new Thread(robotDrag, "swing-drag-robot").start();
        } else {
            dispatchViaSynthetic(source, targetRef, targetX, targetY, context);
        }
    }

    /**
     * Source identified by pixel coordinates — always uses Robot.
     */
    private void executeWithSourceCoords(int sourceX, int sourceY,
                                         Integer targetRef, Integer targetX, Integer targetY,
                                         SwingToolContext context) throws Exception {
        // BR-13: coordinate-only source requires a display
        if (GraphicsEnvironment.isHeadless()) {
            throw new MCPErrorResponseException(
                    "Coordinate-based drag requires a graphical display but the environment is headless. "
                            + "Use source_ref instead, or provide a display (e.g. Xvfb).");
        }

        Window window = findShowingWindow(context);
        if (window == null) {
            throw new MCPErrorResponseException(
                    "No showing window found for coordinate-based drag");
        }

        Point winOnScreen = window.getLocationOnScreen();
        int pressScreenX = winOnScreen.x + sourceX;
        int pressScreenY = winOnScreen.y + sourceY;

        int[] targetScreen = resolveTargetScreenCoords(targetRef, targetX, targetY, window, context);

        LOG.fine(() -> String.format("Robot drag (coords): (%d,%d) \u2192 (%d,%d)",
                pressScreenX, pressScreenY, targetScreen[0], targetScreen[1]));

        Runnable robotDrag = SwingUtils.createRobotDragAction(
                pressScreenX, pressScreenY, targetScreen[0], targetScreen[1]);
        new Thread(robotDrag, "swing-drag-robot").start();
    }

    /**
     * Resolves target to screen-absolute coordinates for Robot dispatch.
     */
    private int[] resolveTargetScreenCoords(Integer targetRef, Integer targetX, Integer targetY,
                                            Window window, SwingToolContext context) throws Exception {
        if (targetRef != null) {
            Accessible targetAccessible = context.getAccessibleByRef(targetRef);
            SwingUtils.ComponentAndPoint target = SwingUtils.resolveComponentAndPoint(targetAccessible);
            if (target == null) {
                throw new MCPErrorResponseException(
                        "Target component could not be resolved to a displayable Component. "
                                + "Call swing_snapshot to verify available components");
            }
            Point tgtOnScreen = target.component.getLocationOnScreen();
            return new int[]{tgtOnScreen.x + target.x, tgtOnScreen.y + target.y};
        } else {
            Point winOnScreen = window.getLocationOnScreen();
            return new int[]{winOnScreen.x + targetX, winOnScreen.y + targetY};
        }
    }

    /**
     * Synthetic dispatch — all events sent to the source component.
     * Used in headless environments or when the source component is not showing.
     */
    private void dispatchViaSynthetic(SwingUtils.ComponentAndPoint source,
                                      Integer targetRef, Integer targetX, Integer targetY,
                                      SwingToolContext context) throws Exception {
        int localTargetX;
        int localTargetY;

        if (targetRef != null) {
            Accessible targetAccessible = context.getAccessibleByRef(targetRef);
            SwingUtils.ComponentAndPoint target = SwingUtils.resolveComponentAndPoint(targetAccessible);
            if (target == null) {
                throw new MCPErrorResponseException(
                        "Target component could not be resolved to a displayable Component. "
                                + "Call swing_snapshot to verify available components");
            }
            Point targetPoint = new Point(target.x, target.y);
            Point converted = SwingUtilities.convertPoint(target.component, targetPoint, source.component);
            localTargetX = converted.x;
            localTargetY = converted.y;
        } else {
            // Convert window-relative coordinates to source-component-local
            Container ancestor = source.component.getParent();
            while (ancestor != null && !(ancestor instanceof Window)) {
                ancestor = ancestor.getParent();
            }
            // If no Window ancestor (e.g. headless test), use the topmost container
            if (ancestor == null) {
                Component top = source.component;
                Container parent = source.component.getParent();
                while (parent != null) {
                    top = parent;
                    parent = parent.getParent();
                }
                ancestor = (top instanceof Container) ? (Container) top : source.component.getParent();
            }
            Point windowPoint = new Point(targetX, targetY);
            Point converted = SwingUtilities.convertPoint(ancestor, windowPoint, source.component);
            localTargetX = converted.x;
            localTargetY = converted.y;
        }

        LOG.fine(() -> String.format("Synthetic drag on %s: (%d,%d) \u2192 (%d,%d)",
                source.component.getClass().getSimpleName(),
                source.x, source.y, localTargetX, localTargetY));

        Runnable dragAction = SwingUtils.createDragAction(
                source.component, source.x, source.y, localTargetX, localTargetY);
        SwingUtilities.invokeLater(dragAction);
    }

    /**
     * Finds the first showing Window from the considered components list.
     */
    private Window findShowingWindow(SwingToolContext context) {
        for (Component c : context.getConsideredComponents()) {
            Window w = (c instanceof Window) ? (Window) c
                    : (Window) SwingUtilities.getAncestorOfClass(Window.class, c);
            if (w != null && w.isShowing()) {
                return w;
            }
        }
        return null;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
