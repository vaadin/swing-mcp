package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

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
 * @see <a href="use-case-011-swing-close.md">UC-011</a>
 */
public class SwingCloseTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_CLOSE;
    }

    @Override
    public String getDescription() {
        return "Close a window, dialog, internal frame, or desktop icon (iconified internal frame) by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.\n" +
                "  Note: Closing a window may terminate the app; since Swing-MCP runs as a part of that app it will be killed too, and\n" +
                "  the client will see a dropped HTTP connection. If this happens, the only way to recover is to re-run the Swing app";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01/BR-02: ref lookup
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-05: check close support
        if (!SwingUtils.supportsClose(accessible)) {
            throw new MCPErrorResponseException(
                    "Component does not support close. Call swing_snapshot or swing_get_cells to verify the list of actions");
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
        return null;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
