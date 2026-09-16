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
import javax.accessibility.AccessibleValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool {@code swing_get_value}: reads the numeric value of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the {@code get_value} action,
 * then reads the value via the accessibility API ({@link AccessibleValue}).</p>
 *
 */
public class SwingGetValueTool extends AbstractSwingTool {

    public SwingGetValueTool() {
        super(SwingTools.SWING_GET_VALUE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // ref is required integer
        int ref = params.getInt("ref");

        // look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // check get_value support
        if (!SwingUtils.supportsGetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_get_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // all access happens on EDT (guaranteed by SwingMCP.registerTool)
        // Step 4: read current value via the shared helper (D_inline_value_preview
        // shared-read with snapshot inline preview). supportsGetValue already
        // verified getCurrentAccessibleValue() is non-null so readValue
        // succeeds here; the IllegalStateException branch is a gate-violation
        // safety net.
        Number current = SwingUtils.readValue(accessible);

        // Steps 5-6: read optional min/max
        AccessibleValue av = accessible.getAccessibleContext().getAccessibleValue();
        Number min = av.getMinimumAccessibleValue();
        Number max = av.getMaximumAccessibleValue();

        // Steps 7-8: build JSON via Content.json()
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
        // read-only tool, ref map is NOT cleared
        return false;
    }
}
