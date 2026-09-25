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
import javax.accessibility.AccessibleValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool {@code swing_get_value}: a component's {@link AccessibleValue} by ref, as JSON —
 * {@code {"current":42,"min":0,"max":100}}, each integer-when-whole; a missing bound is left
 * out, meaning unbounded.
 */
public class SwingGetValueTool extends AbstractSwingTool {

    public SwingGetValueTool() {
        super(SwingTools.SWING_GET_VALUE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        if (!SwingUtils.supportsGetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_get_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // The same read as the snapshot's inline preview (D_inline_value_preview).
        Number current = SwingUtils.readValue(accessible);

        AccessibleValue av = accessible.getAccessibleContext().getAccessibleValue();
        Number min = av.getMinimumAccessibleValue();
        Number max = av.getMaximumAccessibleValue();

        Map<String, Number> result = new LinkedHashMap<>();
        result.put("current", SwingUtils.serializeNumber(current));
        if (min != null) {
            result.put("min", SwingUtils.serializeNumber(min));
        }
        if (max != null) {
            result.put("max", SwingUtils.serializeNumber(max));
        }

        return MCPProtocol.Content.json(result);
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
