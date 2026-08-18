package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.awt.event.WindowEvent;

/**
 * MCP tool {@code swing_close}: closes a window, dialog, internal frame, or
 * desktop icon (iconified internal frame) identified by ref.
 *
 * <p>Validates the ref and close support, then dispatches the close event
 * via {@code SwingUtilities.invokeLater()} (fire-and-forget). For windows,
 * posts {@link WindowEvent#WINDOW_CLOSING}; for internal frames and desktop
 * icons, calls {@code doDefaultCloseAction()}. The client observes the
 * result via {@code swing_snapshot}.</p>
 *
 * @see <a href="tool-011-swing-close.md">T-011</a>
 */
public class SwingCloseTool extends AbstractSwingTool {

    public SwingCloseTool() {
        super(SwingTools.SWING_CLOSE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01/BR-02: ref lookup
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-05: check close support
        if (!SwingUtils.supportsClose(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_close. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-03: fire the close event asynchronously (fire-and-forget)
        if (accessible instanceof Window) {
            Window window = (Window) accessible;
            SwingUtilities.invokeLater(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
        } else {
            // JInternalFrame or JDesktopIcon — resolve to frame, call doDefaultCloseAction
            JInternalFrame iframe = (accessible instanceof JInternalFrame.JDesktopIcon)
                    ? ((JInternalFrame.JDesktopIcon) accessible).getInternalFrame()
                    : (JInternalFrame) accessible;
            SwingUtilities.invokeLater(iframe::doDefaultCloseAction);
        }
        // BR-13: DR-dispatched-echo success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
