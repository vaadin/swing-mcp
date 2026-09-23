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
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleTable;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool {@code swing_get_selection}: a component's selection by ref, as JSON:
 *
 * <pre>{@code
 * {"selectedCount":1,"selected":[{"index":1,"name":"Beta"}]}
 * }</pre>
 *
 * Past {@code MAX_SELECTION_ITEMS} it adds {@code "truncated":true}, and {@code selectedCount}
 * counts only the items returned. A {@code JTable} selection is reported by row, named by a
 * pipe-separated summary of its cells.
 */
public class SwingGetSelectionTool extends AbstractSwingTool {

    static final int MAX_SELECTION_ITEMS = 100;

    public SwingGetSelectionTool() {
        super(SwingTools.SWING_GET_SELECTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);
        requireSelectable(accessible, "swing_get_selection");

        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleSelection as = ac.getAccessibleSelection();

        List<Map<String, Object>> selected;
        boolean truncated;

        if (accessible instanceof JTable) {
            AccessibleTable at = ac.getAccessibleTable();
            int cols = at.getAccessibleColumnCount();
            int selCount = as.getAccessibleSelectionCount();

            // A table selects cells, row-major (R_selection_index_spaces); fold them into rows.
            Set<Integer> rows = new LinkedHashSet<>();
            for (int i = 0; i < selCount; i++) {
                Accessible cell = as.getAccessibleSelection(i);
                if (cell == null) continue;
                int cellIndex = cell.getAccessibleContext().getAccessibleIndexInParent();
                rows.add(cellIndex / cols);
            }

            truncated = rows.size() > MAX_SELECTION_ITEMS;
            selected = new ArrayList<>();
            int count = 0;
            for (int row : rows) {
                if (count >= MAX_SELECTION_ITEMS) break;
                String name = SwingUtils.buildTableRowText(at, row, cols);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", row);
                item.put("name", name);
                selected.add(item);
                count++;
            }
        } else {
            int selCount = as.getAccessibleSelectionCount();
            truncated = selCount > MAX_SELECTION_ITEMS;
            int limit = Math.min(selCount, MAX_SELECTION_ITEMS);
            selected = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Accessible child = as.getAccessibleSelection(i);
                if (child == null) continue;
                AccessibleContext childCtx = child.getAccessibleContext();
                int itemIndex = childCtx.getAccessibleIndexInParent();
                String name = childCtx.getAccessibleName();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", itemIndex);
                item.put("name", name);
                selected.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedCount", selected.size());
        result.put("selected", selected);
        if (truncated) {
            result.put("truncated", true);
        }
        return MCPProtocol.Content.json(result);
    }


    @Override
    public boolean isMutation() {
        return false;
    }
}
