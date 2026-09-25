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

/**
 * MCP tool {@code swing_get_description}: a component's description by ref, resolved as the
 * snapshot's description slot is ({@link SwingUtils#resolveDescription}). Any component
 * qualifies; one without a description yields empty text.
 *
 * <p>The slot's 120-character cap (D_quoted_slot_sanitizing) becomes
 * {@code MAX_DESCRIPTION_LENGTH}, with a notice quoting the real length:
 *
 * <pre>
 * &lt;first 1000 characters&gt;
 * ... (truncated, 1100 total characters)
 * </pre>
 */
public class SwingGetDescriptionTool extends AbstractSwingTool {

    static final int MAX_DESCRIPTION_LENGTH = 1000;

    public SwingGetDescriptionTool() {
        super(SwingTools.SWING_GET_DESCRIPTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        String desc = SwingUtils.resolveDescription(accessible);
        if (desc == null || desc.isEmpty()) {
            return MCPProtocol.Content.text("");
        }

        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            int totalLength = desc.length();
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH)
                    + "\n... (truncated, " + totalLength + " total characters)";
        }

        return MCPProtocol.Content.text(desc);
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
