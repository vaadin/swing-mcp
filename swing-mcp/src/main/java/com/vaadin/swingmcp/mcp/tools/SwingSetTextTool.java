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
import javax.accessibility.AccessibleEditableText;
import javax.accessibility.AccessibleState;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_set_text}: replaces a component's whole text by ref, through
 * {@link AccessibleEditableText#setTextContents(String)}. Refuses a disabled or non-editable
 * component, each with its own message.
 */
public class SwingSetTextTool extends AbstractSwingTool {

    public SwingSetTextTool() {
        super(SwingTools.SWING_SET_TEXT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        String text = params.getString("text");

        Accessible accessible = context.getAccessibleByRef(ref);

        if (!SwingUtils.hasEditableText(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be edited");
        }

        AccessibleContext ac = accessible.getAccessibleContext();
        if (!ac.getAccessibleStateSet().contains(AccessibleState.EDITABLE)) {
            throw new MCPErrorResponseException("Component is not editable");
        }

        AccessibleEditableText aet = ac.getAccessibleEditableText();
        SwingUtilities.invokeLater(() -> aet.setTextContents(text));
        // A password is echoed too: the agent supplied it, so nothing is revealed.
        return echo(ref, renderEchoString(text));
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
