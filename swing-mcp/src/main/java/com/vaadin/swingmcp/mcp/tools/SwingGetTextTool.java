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

/**
 * MCP tool {@code swing_get_text}: a component's text by ref, capped at {@code MAX_TEXT_LENGTH}
 * characters with a notice quoting the real length:
 *
 * <pre>
 * &lt;first 1000 characters&gt;
 * ... (truncated, 1100 total characters)
 * </pre>
 *
 * A password field is refused with its own message (D_password_not_readable).
 */
public class SwingGetTextTool extends AbstractSwingTool {

    static final int MAX_TEXT_LENGTH = 1000;

    public SwingGetTextTool() {
        super(SwingTools.SWING_GET_TEXT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        if (SwingUtils.hasPasswordRole(accessible)) {
            throw new MCPErrorResponseException(
                    "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.");
        }

        if (!SwingUtils.supportsGetText(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_get_text. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        AccessibleContext ac = accessible.getAccessibleContext();
        int len = ac.getAccessibleText().getCharCount();
        if (len == 0) {
            return MCPProtocol.Content.text("");
        }

        // The same read as the snapshot's inline preview (D_inline_value_preview).
        String text = SwingUtils.readText(accessible, MAX_TEXT_LENGTH);
        if (len > MAX_TEXT_LENGTH) {
            text = text + "\n... (truncated, " + len + " total characters)";
        }

        return MCPProtocol.Content.text(text);
    }

    @Override
    public boolean isMutation() {
        return false;
    }
}
