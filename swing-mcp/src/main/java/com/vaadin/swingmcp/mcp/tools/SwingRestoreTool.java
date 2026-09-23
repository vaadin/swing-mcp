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
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.beans.PropertyVetoException;

/**
 * MCP tool {@code swing_restore}: de-iconifies an iconified {@link Frame}, {@link JInternalFrame}
 * or {@code JDesktopIcon} by ref — clearing only the frame's {@link Frame#ICONIFIED} bit, keeping
 * {@code MAXIMIZED_BOTH} and the rest, or calling the internal frame's {@code setIcon(false)}.
 * An internal frame is the target only when it was iconified in place (R_iconified_windows);
 * otherwise its icon is.
 */
public class SwingRestoreTool extends AbstractSwingTool {

    public SwingRestoreTool() {
        super(SwingTools.SWING_RESTORE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        if (!SwingUtils.supportsRestore(accessible)) {
            throw new MCPErrorResponseException(restoreErrorMessage(accessible));
        }

        if (accessible instanceof Frame) {
            Frame frame = (Frame) accessible;
            SwingUtilities.invokeLater(() ->
                    frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED));
        } else {
            JInternalFrame iframe = accessible instanceof JInternalFrame
                    ? (JInternalFrame) accessible
                    : ((JInternalFrame.JDesktopIcon) accessible).getInternalFrame();
            SwingUtilities.invokeLater(() -> {
                try {
                    iframe.setIcon(false);
                } catch (PropertyVetoException e) {
                    // The application vetoed it, as it may; the follow-up snapshot shows that.
                }
            });
        }
        return echo(ref);
    }

    private static String restoreErrorMessage(Accessible accessible) {
        if (accessible instanceof Frame) {
            return "Frame is not iconified. Call swing_snapshot to verify the current state";
        }
        if (accessible instanceof JInternalFrame) {
            return "JInternalFrame is not iconified. Call swing_snapshot to verify the current state";
        }
        return ComponentClassResolver.resolveClassName(accessible)
                + " does not support swing_restore. Call swing_snapshot or swing_get_cells to verify the list of actions";
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
