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
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.github.mvysny.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_increment}: steps a component's value up by ref, through its
 * {@link AccessibleAction#INCREMENT} action. Refuses a disabled component.
 */
public class SwingIncrementTool extends AbstractSwingTool {

    public SwingIncrementTool() {
        super(SwingTools.SWING_INCREMENT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        int actionIndex = SwingUtils.supportsIncrement(accessible);
        if (actionIndex < 0) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be interacted with");
        }

        AccessibleAction aa = accessible.getAccessibleContext().getAccessibleAction();
        SwingUtilities.invokeLater(() -> aa.doAccessibleAction(actionIndex));
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
