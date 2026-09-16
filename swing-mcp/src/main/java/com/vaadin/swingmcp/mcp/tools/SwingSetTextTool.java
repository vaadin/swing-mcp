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
 * MCP tool {@code swing_set_text}: replaces the full text content of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports {@code set_text}, is effectively
 * enabled, and is editable, then delegates to
 * {@link AccessibleEditableText#setTextContents(String)}.</p>
 *
 */
public class SwingSetTextTool extends AbstractSwingTool {

    public SwingSetTextTool() {
        super(SwingTools.SWING_SET_TEXT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // both params required
        int ref = params.getInt("ref");
        String text = params.getString("text");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // set_text structural support check
        if (!SwingUtils.hasEditableText(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be edited");
        }

        // editable state check
        AccessibleContext ac = accessible.getAccessibleContext();
        if (!ac.getAccessibleStateSet().contains(AccessibleState.EDITABLE)) {
            throw new MCPErrorResponseException("Component is not editable");
        }

        // fire the text replacement asynchronously (fire-and-forget)
        AccessibleEditableText aet = ac.getAccessibleEditableText();
        SwingUtilities.invokeLater(() -> aet.setTextContents(text));
        // D_dispatched_echo success echo — same format for all text components
        // including password fields (the agent already supplied the value).
        return echo(ref, renderEchoString(text));
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
