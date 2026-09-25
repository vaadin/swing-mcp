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
import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.github.mvysny.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleTable;
import javax.swing.JComboBox;
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
 * {@code selectedCount} is the whole selection; past {@code MAX_SELECTION_ITEMS} the list stops
 * and {@code "truncated":true} is added. A {@code JTable} selection is reported by row, named by
 * a pipe-separated summary of its cells. A {@code JComboBox} showing a value outside its model,
 * typed into an editable one, is reported as index -1:
 *
 * <pre>{@code
 * {"selectedCount":1,"selected":[{"index":-1,"name":"Magenta"}]}
 * }</pre>
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
        int selectedCount;
        boolean truncated;

        if (isValueOutsideModel(accessible)) {
            // Swing counts this value but yields a null selection (R_selection_null_entries).
            Object value = ((JComboBox<?>) accessible).getSelectedItem();
            selected = List.of(item(-1, String.valueOf(value)));
            selectedCount = 1;
            truncated = false;
        } else if (accessible instanceof JTable) {
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

            selectedCount = rows.size();
            truncated = selectedCount > MAX_SELECTION_ITEMS;
            selected = new ArrayList<>();
            for (int row : rows) {
                if (selected.size() >= MAX_SELECTION_ITEMS) break;
                selected.add(item(row, SwingUtils.buildTableRowText(at, row, cols)));
            }
        } else {
            selectedCount = as.getAccessibleSelectionCount();
            truncated = selectedCount > MAX_SELECTION_ITEMS;
            int limit = Math.min(selectedCount, MAX_SELECTION_ITEMS);
            selected = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Accessible child = as.getAccessibleSelection(i);
                // Null for an index past a model that shrank silently (R_selection_null_entries).
                if (child == null) continue;
                AccessibleContext childCtx = child.getAccessibleContext();
                selected.add(item(childCtx.getAccessibleIndexInParent(), childCtx.getAccessibleName()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedCount", selectedCount);
        result.put("selected", selected);
        if (truncated) {
            result.put("truncated", true);
        }
        return MCPProtocol.Content.json(result);
    }

    /**
     * Returns {@code true} for a {@code JComboBox} whose selected item is in none of its rows —
     * typed into an editable combo, or set on its model directly.
     */
    private static boolean isValueOutsideModel(Accessible accessible) {
        if (!(accessible instanceof JComboBox)) return false;
        JComboBox<?> combo = (JComboBox<?>) accessible;
        return combo.getSelectedIndex() == -1 && combo.getSelectedItem() != null;
    }

    private static Map<String, Object> item(int index, String name) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("name", name);
        return item;
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
