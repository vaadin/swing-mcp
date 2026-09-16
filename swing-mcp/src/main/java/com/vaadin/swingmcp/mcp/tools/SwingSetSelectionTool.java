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
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP tool {@code swing_set_selection}: sets the selection of a UI component by ref.
 *
 * <p>Accepts an array of 0-based item indices. For single-selection components,
 * at most one index is allowed. For JTable, indices are row indices — the tool
 * translates to cell indices internally. An empty array clears the selection.</p>
 *
 */
public class SwingSetSelectionTool extends AbstractSwingTool {

    public SwingSetSelectionTool() {
        super(SwingTools.SWING_SET_SELECTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // parameter validation
        int ref = params.getInt("ref");
        List<Integer> indices = params.getIntArray("indices");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // selection support check with JTable-specific error
        requireSelectable(accessible, "swing_set_selection");

        // effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // Step 5: obtain AccessibleSelection
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleSelection as = ac.getAccessibleSelection();

        // deduplicate indices
        Set<Integer> deduplicated = new LinkedHashSet<>(indices);

        // empty indices = clear path
        if (deduplicated.isEmpty()) {
            if (accessible instanceof JTabbedPane && ((JTabbedPane) accessible).getTabCount() > 0) {
                throw new MCPErrorResponseException(
                        "This component does not allow the selection to be empty.");
            }
            SwingUtilities.invokeLater(as::clearAccessibleSelection);
            // D_dispatched_echo success echo
            return echo(ref, renderEchoIntArray(deduplicated));
        }

        // single-selection enforcement
        if (SwingUtils.supportsSingleSelection(accessible) && deduplicated.size() > 1) {
            throw new MCPErrorResponseException(
                    "Component is in single-selection mode. Pass exactly one index (or an empty array to clear).");
        }

        // Step 9: determine item count for bounds checking
        int itemCount = SwingUtils.getItemCount(accessible);

        // Step 10: bounds validation
        for (int index : deduplicated) {
            if (index < 0 || index >= itemCount) {
                throw new MCPErrorResponseException(
                        "Index " + index + " is out of bounds. Valid range is [0, " + itemCount + ").");
            }
        }

        // disabled tab check (JTabbedPane only)
        if (accessible instanceof JTabbedPane) {
            JTabbedPane tabbedPane = (JTabbedPane) accessible;
            for (int index : deduplicated) {
                if (!tabbedPane.isEnabledAt(index)) {
                    throw new MCPErrorResponseException(
                            "Tab at index " + index + " is disabled.");
                }
            }
        }

        // Step 12: fire-and-forget dispatch
        // JTable: use direct API (JTable.addRowSelectionInterval) because
        // AccessibleSelection.addAccessibleSelection delegates to changeSelection()
        // which is unreliable inside invokeLater (selection not applied).
        if (accessible instanceof JTable) {
            JTable table = (JTable) accessible;
            Set<Integer> rows = deduplicated;
            SwingUtilities.invokeLater(() -> {
                table.clearSelection();
                for (int row : rows) {
                    table.addRowSelectionInterval(row, row);
                }
            });
        } else {
            Set<Integer> items = deduplicated;
            SwingUtilities.invokeLater(() -> {
                as.clearAccessibleSelection();
                for (int i : items) {
                    as.addAccessibleSelection(i);
                }
            });
        }

        // D_dispatched_echo success echo
        return echo(ref, renderEchoIntArray(deduplicated));
    }

    @Override
    public boolean isMutation() {
        // mutation tool, ref map IS cleared
        return true;
    }
}
