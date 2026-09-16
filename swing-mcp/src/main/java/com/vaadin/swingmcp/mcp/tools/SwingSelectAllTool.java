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
import javax.accessibility.AccessibleSelection;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_select_all}: selects all items in a multi-selection
 * UI component by ref.
 *
 * <p>Only works on components marked {@code multi-selection} in the snapshot.
 * Single-selection components are rejected. For JTable, uses
 * {@link JTable#selectAll()} directly because the accessibility API's
 * {@code selectAllAccessibleSelection()} is a no-op on JTable.</p>
 *
 */
public class SwingSelectAllTool extends AbstractSwingTool {

    public SwingSelectAllTool() {
        super(SwingTools.SWING_SELECT_ALL);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // parameter validation
        int ref = params.getInt("ref");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // selection support + multi-selection check
        requireMultiSelectable(accessible, "swing_select_all");

        // effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // Step 5: fire-and-forget dispatch
        if (accessible instanceof JTable) {
            // JTable.selectAll() — accessibility API is broken (no-op)
            JTable table = (JTable) accessible;
            SwingUtilities.invokeLater(table::selectAll);
        } else {
            // AccessibleSelection.selectAllAccessibleSelection()
            AccessibleSelection as = accessible.getAccessibleContext().getAccessibleSelection();
            SwingUtilities.invokeLater(as::selectAllAccessibleSelection);
        }

        // D_dispatched_echo success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        // mutation tool, ref map IS cleared
        return true;
    }
}
