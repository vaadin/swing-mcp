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
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_click}: clicks a UI component identified by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the click action
 * and is effectively enabled, then invokes the click via the {@link Runnable}
 * returned by {@link SwingUtils#supportsClick(Accessible)}.</p>
 *
 */
public class SwingClickTool extends AbstractSwingTool {

    public SwingClickTool() {
        super(SwingTools.SWING_CLICK);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // ref is required integer
        int ref = params.getInt("ref");

        // look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // check click support (Tier 1: AccessibleAction, Tier 2: MouseListener)
        Runnable click = SwingUtils.supportsClick(accessible);
        if (click == null) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // check effectively enabled
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be clicked");
        }

        // fire the click action asynchronously (fire-and-forget)
        SwingUtilities.invokeLater(click);
        // D_dispatched_echo success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
