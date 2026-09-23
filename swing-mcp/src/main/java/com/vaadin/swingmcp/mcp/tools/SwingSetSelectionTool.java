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
 * MCP tool {@code swing_set_selection}: replaces a component's selection by ref with the given
 * item indices — duplicates collapse, an empty array clears, a {@code JTable} index is a row.
 * Refuses a disabled component or tab, an out-of-range index, more than one index in
 * single-selection mode, and clearing a {@code JTabbedPane} (R_accessible_selection_writes).
 */
public class SwingSetSelectionTool extends AbstractSwingTool {

    public SwingSetSelectionTool() {
        super(SwingTools.SWING_SET_SELECTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        List<Integer> indices = params.getIntArray("indices");

        Accessible accessible = context.getAccessibleByRef(ref);
        requireSelectable(accessible, "swing_set_selection");

        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleSelection as = ac.getAccessibleSelection();

        Set<Integer> deduplicated = new LinkedHashSet<>(indices);

        if (deduplicated.isEmpty()) {
            if (accessible instanceof JTabbedPane && ((JTabbedPane) accessible).getTabCount() > 0) {
                throw new MCPErrorResponseException(
                        "This component does not allow the selection to be empty.");
            }
            SwingUtilities.invokeLater(as::clearAccessibleSelection);
            return echo(ref, renderEchoIntArray(deduplicated));
        }

        if (SwingUtils.supportsSingleSelection(accessible) && deduplicated.size() > 1) {
            throw new MCPErrorResponseException(
                    "Component is in single-selection mode. Pass exactly one index (or an empty array to clear).");
        }

        int itemCount = SwingUtils.getItemCount(accessible);
        for (int index : deduplicated) {
            if (index < 0 || index >= itemCount) {
                throw new MCPErrorResponseException(
                        "Index " + index + " is out of bounds. Valid range is [0, " + itemCount + ").");
            }
        }

        // A tab is standard Swing's only per-item disable (R_disabled_not_propagated).
        if (accessible instanceof JTabbedPane) {
            JTabbedPane tabbedPane = (JTabbedPane) accessible;
            for (int index : deduplicated) {
                if (!tabbedPane.isEnabledAt(index)) {
                    throw new MCPErrorResponseException(
                            "Tab at index " + index + " is disabled.");
                }
            }
        }

        if (accessible instanceof JTable) {
            // Not addAccessibleSelection: inside invokeLater a JTable silently drops it
            // (R_jtable_selection_in_invokelater).
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

        return echo(ref, renderEchoIntArray(deduplicated));
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
