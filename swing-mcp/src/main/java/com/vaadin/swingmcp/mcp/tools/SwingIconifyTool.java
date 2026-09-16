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
 * MCP tool {@code swing_iconify}: iconifies (minimizes) a Frame or
 * JInternalFrame identified by ref.
 *
 * <p>Validates the ref and iconify support, then dispatches the iconify
 * via {@code SwingUtilities.invokeLater()} (fire-and-forget). For Frame,
 * sets the {@link Frame#ICONIFIED} extended state; for JInternalFrame,
 * calls {@code setIcon(true)}. The client observes the result via
 * {@code swing_snapshot}.</p>
 *
 * @see <a href="tool-022-swing-iconify.md">T-022</a>
 */
public class SwingIconifyTool extends AbstractSwingTool {

    public SwingIconifyTool() {
        super(SwingTools.SWING_ICONIFY);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01/BR-02: ref lookup
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-05: gate on supportsIconify, then derive specific error message
        if (!SwingUtils.supportsIconify(accessible)) {
            throw new MCPErrorResponseException(iconifyErrorMessage(accessible));
        }

        // BR-03: fire-and-forget iconify dispatch
        if (accessible instanceof Frame) {
            Frame frame = (Frame) accessible;
            SwingUtilities.invokeLater(() -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
        } else {
            JInternalFrame iframe = (JInternalFrame) accessible;
            SwingUtilities.invokeLater(() -> {
                try {
                    iframe.setIcon(true);
                } catch (PropertyVetoException e) {
                    // Silently ignored — a VetoableChangeListener rejected the iconify.
                    // The client calls swing_snapshot to check the outcome.
                }
            });
        }
        // BR-09: D_dispatched_echo success echo
        return echo(ref);
    }

    private static String iconifyErrorMessage(Accessible accessible) {
        if (accessible instanceof Frame) {
            Frame frame = (Frame) accessible;
            if (frame.isUndecorated()) return "Frame is undecorated and cannot be iconified. Call swing_snapshot to verify the current state";
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) return "Frame is already iconified. Call swing_snapshot to verify the current state";
        } else if (accessible instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) accessible;
            if (!iframe.isIconifiable()) return "JInternalFrame is not iconifiable. Call swing_snapshot to verify the current state";
            if (iframe.isIcon()) return "JInternalFrame is already iconified. Call swing_snapshot to verify the current state";
        }
        return ComponentClassResolver.resolveClassName(accessible)
                + " does not support swing_iconify. Call swing_snapshot or swing_get_cells to verify the list of actions";
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
