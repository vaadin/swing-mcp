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
import javax.accessibility.AccessibleSelection;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_select_all}: selects every item of a multi-selection component by ref,
 * through the component's own select-all rather than a list of every index
 * (D_select_all_standalone). Refuses a single-selection or disabled component.
 */
public class SwingSelectAllTool extends AbstractSwingTool {

    public SwingSelectAllTool() {
        super(SwingTools.SWING_SELECT_ALL);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);
        requireMultiSelectable(accessible, "swing_select_all");

        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        if (accessible instanceof JTable) {
            // selectAllAccessibleSelection() is a no-op on JTable (R_accessible_selection_writes).
            JTable table = (JTable) accessible;
            SwingUtilities.invokeLater(table::selectAll);
        } else {
            AccessibleSelection as = accessible.getAccessibleContext().getAccessibleSelection();
            SwingUtilities.invokeLater(as::selectAllAccessibleSelection);
        }

        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
