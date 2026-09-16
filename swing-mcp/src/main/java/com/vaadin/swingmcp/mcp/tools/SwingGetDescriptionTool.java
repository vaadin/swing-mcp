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
 * MCP tool {@code swing_get_description}: reads the full description of a UI
 * component by ref.
 *
 * <p>The snapshot caps descriptions at 120 characters (D_quoted_slot_sanitizing). When
 * the AI needs the full text it calls this tool. The description is resolved
 * using the same logic as the snapshot description slot: accessible description
 * first, tooltip fallback second, HTML cleanup and sanitisation applied —
 * but without the 120-character cap.</p>
 *
 */
public class SwingGetDescriptionTool extends AbstractSwingTool {

    static final int MAX_DESCRIPTION_LENGTH = 1000;

    public SwingGetDescriptionTool() {
        super(SwingTools.SWING_GET_DESCRIPTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // ref is required integer
        int ref = params.getInt("ref");

        // look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // resolve description using the same logic as the snapshot
        // (accessible description -> tooltip fallback -> HTML cleanup -> sanitize).
        // no gate — every component has a description (possibly empty).
        String desc = SwingUtils.resolveDescription(accessible);

        if (desc == null || desc.isEmpty()) {
            // explicit empty string, not null
            return MCPProtocol.Content.text("");
        }

        // cap at MAX_DESCRIPTION_LENGTH with truncation notice
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            int totalLength = desc.length();
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH)
                    + "\n... (truncated, " + totalLength + " total characters)";
        }

        return MCPProtocol.Content.text(desc);
    }

    @Override
    public boolean isMutation() {
        // read-only tool, ref map is NOT cleared
        return false;
    }
}
