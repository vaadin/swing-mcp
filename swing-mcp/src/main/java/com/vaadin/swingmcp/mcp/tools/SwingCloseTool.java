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
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.awt.event.WindowEvent;

/**
 * MCP tool {@code swing_close}: closes a window, dialog, internal frame or desktop icon by ref the
 * way its close button would — a window gets {@link WindowEvent#WINDOW_CLOSING}, an internal frame
 * (or a desktop icon's frame) {@code doDefaultCloseAction()} — so the application's own close
 * handling decides.
 */
public class SwingCloseTool extends AbstractSwingTool {

    public SwingCloseTool() {
        super(SwingTools.SWING_CLOSE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        if (!SwingUtils.supportsClose(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_close. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        if (accessible instanceof Window) {
            Window window = (Window) accessible;
            SwingUtilities.invokeLater(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        } else {
            JInternalFrame iframe = (accessible instanceof JInternalFrame.JDesktopIcon)
                    ? ((JInternalFrame.JDesktopIcon) accessible).getInternalFrame()
                    : (JInternalFrame) accessible;
            SwingUtilities.invokeLater(iframe::doDefaultCloseAction);
        }
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
