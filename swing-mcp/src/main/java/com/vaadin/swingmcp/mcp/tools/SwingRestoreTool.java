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
 * MCP tool {@code swing_restore}: restores (de-iconifies) an iconified Frame
 * or JDesktopIcon (iconified JInternalFrame) identified by ref.
 *
 * <p>Validates the ref and restore support via {@link SwingUtils#supportsRestore},
 * then dispatches the restore via {@code SwingUtilities.invokeLater()} (fire-and-forget).
 * For Frame, clears the {@link Frame#ICONIFIED} bit preserving other extended-state bits;
 * for JDesktopIcon, resolves to the underlying JInternalFrame and calls
 * {@code setIcon(false)}. The client observes the result via {@code swing_snapshot}.</p>
 *
 * @see <a href="tool-023-swing-restore.md">T-023</a>
 */
public class SwingRestoreTool extends AbstractSwingTool {

    public SwingRestoreTool() {
        super(SwingTools.SWING_RESTORE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01/BR-02: ref lookup
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-05: gate on supportsRestore, then derive specific error message
        if (!SwingUtils.supportsRestore(accessible)) {
            throw new MCPErrorResponseException(restoreErrorMessage(accessible));
        }

        // BR-03: fire-and-forget restore dispatch
        if (accessible instanceof Frame) {
            Frame frame = (Frame) accessible;
            SwingUtilities.invokeLater(() ->
                    frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED));
        } else {
            // JDesktopIcon — resolve to underlying JInternalFrame
            JInternalFrame.JDesktopIcon icon = (JInternalFrame.JDesktopIcon) accessible;
            JInternalFrame iframe = icon.getInternalFrame();
            SwingUtilities.invokeLater(() -> {
                try {
                    iframe.setIcon(false);
                } catch (PropertyVetoException e) {
                    // Silently ignored — a VetoableChangeListener rejected the restore.
                    // The client calls swing_snapshot to check the outcome.
                }
            });
        }
        // BR-09: DR-dispatched-echo success echo
        return echo(ref);
    }

    private static String restoreErrorMessage(Accessible accessible) {
        if (accessible instanceof Frame) {
            return "Frame is not iconified. Call swing_snapshot to verify the current state";
        }
        return ComponentClassResolver.resolveClassName(accessible)
                + " does not support swing_restore. Call swing_snapshot or swing_get_cells to verify the list of actions";
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
