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

import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.github.mvysny.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import java.util.Collections;
import java.util.Map;

/**
 * MCP tool {@code swing_clear_selection}: clears the selection of a component by ref — a
 * {@link SwingSetSelectionTool} call with an empty {@code indices} array, so every rule that tool
 * enforces applies here, including the refusal on a {@link javax.swing.JTabbedPane}, which has no
 * empty selection to reach (R_accessible_selection_writes).
 */
public class SwingClearSelectionTool extends AbstractSwingTool {

    public SwingClearSelectionTool() {
        super(SwingTools.SWING_CLEAR_SELECTION);
    }

    private final SwingSetSelectionTool delegate = new SwingSetSelectionTool();

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Parameters delegateParams = new Parameters(
                Map.of("ref", ref, "indices", Collections.emptyList()));
        delegate.execute(delegateParams, context);
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
