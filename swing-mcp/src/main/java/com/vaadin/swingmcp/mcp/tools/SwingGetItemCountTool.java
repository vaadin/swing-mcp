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

/**
 * MCP tool {@code swing_get_item_count}: the item count of a {@code JList}, {@code JComboBox} or
 * {@code JTable} by ref, as plain text — the index space {@code swing_get_items} pages through.
 */
public class SwingGetItemCountTool extends AbstractSwingTool {

    public SwingGetItemCountTool() {
        super(SwingTools.SWING_GET_ITEM_COUNT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);
        requireGetItemsSupported(accessible, "swing_get_item_count");

        int totalCount = SwingUtils.getItemCount(accessible);
        return MCPProtocol.Content.text(String.valueOf(totalCount));
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
