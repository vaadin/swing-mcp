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
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import com.vaadin.swingmcp.tools.SwingTools;

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
 * MCP tool {@code swing_drag}: drags from a point on one component, through optional waypoints,
 * to a point on another; each point defaults to its component's center. Refuses a disabled
 * source.
 *
 * <p>With a display and a showing source it drives a {@link java.awt.Robot} off the EDT, on the
 * context's executor: real OS mouse events, which serve Java's DnD framework as well as a
 * {@code MouseListener}-based drag. Otherwise it posts synthetic events with
 * {@link Component#dispatchEvent}, every one of them to the source component and each in its
 * own EDT task, so a listener that throws on one loses that event only.
 */
public class SwingDragTool extends AbstractSwingTool {

    private static final Logger LOG = Logger.getLogger(SwingDragTool.class.getName());

    public SwingDragTool() {
        super(SwingTools.SWING_DRAG);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int sourceRef = params.getInt("source_ref");
        Integer sourceX = params.getIntOrNull("source_x");
        Integer sourceY = params.getIntOrNull("source_y");

        if ((sourceX == null) != (sourceY == null)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Both 'source_x' and 'source_y' must be provided together");
        }

        int targetRef = params.getInt("target_ref");
        Integer targetX = params.getIntOrNull("target_x");
        Integer targetY = params.getIntOrNull("target_y");

        if ((targetX == null) != (targetY == null)) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Both 'target_x' and 'target_y' must be provided together");
        }

        List<Integer> viaRaw = params.getIntArrayOrNull("via");
        if (viaRaw != null && viaRaw.size() % 3 != 0) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Parameter 'via' must contain triplets [ref, x, y, ...]; "
                            + "length " + viaRaw.size() + " is not divisible by 3");
        }

        Accessible sourceAccessible = context.getAccessibleByRef(sourceRef);

        SwingUtils.ComponentAndPoint source = SwingUtils.resolveComponentAndPoint(sourceAccessible);
        if (source == null) {
            throw new MCPErrorResponseException(
                    "Source component could not be resolved to a displayable Component. "
                            + "Call swing_snapshot to verify available components");
        }

        if (sourceX != null) {
            source = new SwingUtils.ComponentAndPoint(source.component, sourceX, sourceY);
        }

        Accessible targetAccessible = context.getAccessibleByRef(targetRef);

        SwingUtils.ComponentAndPoint target = SwingUtils.resolveComponentAndPoint(targetAccessible);
        if (target == null) {
            throw new MCPErrorResponseException(
                    "Target component could not be resolved to a displayable Component. "
                            + "Call swing_snapshot to verify available components");
        }

        if (targetX != null) {
            target = new SwingUtils.ComponentAndPoint(target.component, targetX, targetY);
        }

        List<SwingUtils.ComponentAndPoint> waypoints = resolveWaypoints(viaRaw, context);

        if (!SwingUtils.isEffectivelyEnabled(sourceAccessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be dragged");
        }

        boolean useRobot = !GraphicsEnvironment.isHeadless() && source.component.isShowing();

        if (useRobot) {
            dispatchViaRobot(source, target, waypoints, context);
        } else {
            dispatchViaSynthetic(source, target, waypoints);
        }

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

    private void dispatchViaRobot(SwingUtils.ComponentAndPoint source,
                                  SwingUtils.ComponentAndPoint target,
                                  List<SwingUtils.ComponentAndPoint> waypoints,
                                  SwingToolContext context) {
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
        context.getExecutor().execute(() -> {
            try {
                robotDrag.run();
            } catch (Exception e) {
                LOG.warning("Robot drag failed: " + e.getMessage());
            }
        });
    }

    private void dispatchViaSynthetic(SwingUtils.ComponentAndPoint source,
                                      SwingUtils.ComponentAndPoint target,
                                      List<SwingUtils.ComponentAndPoint> waypoints) {
        Point targetPoint = new Point(target.x, target.y);
        Point converted = SwingUtilities.convertPoint(target.component, targetPoint, source.component);
        int localTargetX = converted.x;
        int localTargetY = converted.y;

        List<int[]> localWaypoints = new ArrayList<>(waypoints.size());
        for (SwingUtils.ComponentAndPoint wp : waypoints) {
            Point wpPoint = new Point(wp.x, wp.y);
            Point wpConverted = SwingUtilities.convertPoint(wp.component, wpPoint, source.component);
            localWaypoints.add(new int[]{wpConverted.x, wpConverted.y});
        }

        LOG.fine(() -> String.format("Synthetic drag on %s: (%d,%d) \u2192 %d waypoints \u2192 (%d,%d)",
                SwingUtils.getComponentClassName(source.component),
                source.x, source.y, waypoints.size(), localTargetX, localTargetY));

        // All posted now, not chained: FIFO keeps the next tool call's EDT turn behind the release.
        for (Runnable step : SwingUtils.createDragSteps(
                source.component, source.x, source.y, localTargetX, localTargetY, localWaypoints)) {
            SwingUtilities.invokeLater(step);
        }
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
