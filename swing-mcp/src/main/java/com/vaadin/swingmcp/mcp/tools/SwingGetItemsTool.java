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
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.JComboBox;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool {@code swing_get_items}: a page of a {@code JList}'s, {@code JComboBox}'s or
 * {@code JTable}'s items by ref, as JSON:
 *
 * <pre>{@code
 * {"totalCount":5,"items":[{"index":0,"name":"Alpha"},{"index":1,"name":"Beta"}]}
 * }</pre>
 *
 * The index is what {@code swing_set_selection} takes; a {@code JTable} item is a row, named by a
 * pipe-separated summary of its cells. A {@code null} item keeps its slot with a {@code null}
 * name.
 */
public class SwingGetItemsTool extends AbstractSwingTool {

    public SwingGetItemsTool() {
        super(SwingTools.SWING_GET_ITEMS);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        int offset = params.getInt("offset");
        int length = params.getInt("length");
        if (offset < 0) {
            throw new MCPErrorResponseException("offset must be non-negative, got " + offset);
        }
        if (length < 0) {
            throw new MCPErrorResponseException("length must be non-negative, got " + length);
        }

        Accessible accessible = context.getAccessibleByRef(ref);
        requireGetItemsSupported(accessible, "swing_get_items");

        AccessibleContext ac = accessible.getAccessibleContext();
        int totalCount = SwingUtils.getItemCount(accessible);

        // long: offset + length can overflow int.
        int end = (int) Math.min((long) offset + length, totalCount);
        int start = Math.min(offset, totalCount);

        List<Map<String, Object>> items = new ArrayList<>();

        if (accessible instanceof JTable) {
            AccessibleTable at = ac.getAccessibleTable();
            int cols = at.getAccessibleColumnCount();
            for (int r = start; r < end; r++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", r);
                item.put("name", SwingUtils.buildTableRowText(at, r, cols));
                items.add(item);
            }
        } else if (accessible instanceof JComboBox) {
            // Not accessible children: a combo's only child is its popup (R_selection_index_spaces).
            JComboBox<?> combo = (JComboBox<?>) accessible;
            for (int i = start; i < end; i++) {
                Object obj = combo.getItemAt(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("name", obj != null ? obj.toString() : null);
                items.add(item);
            }
        } else {
            for (int i = start; i < end; i++) {
                Accessible child = ac.getAccessibleChild(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("name", child != null ? child.getAccessibleContext().getAccessibleName() : null);
                items.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("items", items);
        return MCPProtocol.Content.json(result);
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
