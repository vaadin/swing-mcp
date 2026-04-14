package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

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
 * @see <a href="use-case-022-swing-iconify.md">UC-022</a>
 */
public class SwingIconifyTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_ICONIFY;
    }

    @Override
    public String getDescription() {
        return "Iconify (minimize) a Frame (including JFrame) or JInternalFrame by ref. " +
                "Frame is minimized to the OS taskbar; JInternalFrame is replaced by a JDesktopIcon on its JDesktopPane. " +
                "Requires a ref obtained from swing_snapshot or swing_get_cells.";
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

        // BR-05: specific error messages per refusal reason
        if (accessible instanceof Frame) {
            Frame frame = (Frame) accessible;
            if (!frame.isShowing()) {
                throw new MCPErrorResponseException(
                        "Component does not support iconify. Call swing_snapshot or swing_get_cells to verify the list of actions");
            }
            if (frame.isUndecorated()) {
                throw new MCPErrorResponseException("Frame is undecorated and cannot be iconified");
            }
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
                throw new MCPErrorResponseException("Frame is already iconified");
            }
            // BR-03: fire-and-forget iconify dispatch
            SwingUtilities.invokeLater(() -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
            return null;
        }

        if (accessible instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) accessible;
            if (!iframe.isShowing()) {
                throw new MCPErrorResponseException(
                        "Component does not support iconify. Call swing_snapshot or swing_get_cells to verify the list of actions");
            }
            if (!iframe.isIconifiable()) {
                throw new MCPErrorResponseException("JInternalFrame is not iconifiable");
            }
            if (iframe.isIcon()) {
                throw new MCPErrorResponseException("JInternalFrame is already iconified");
            }
            // BR-03: fire-and-forget iconify dispatch
            SwingUtilities.invokeLater(() -> {
                try {
                    iframe.setIcon(true);
                } catch (PropertyVetoException e) {
                    // Silently ignored — a VetoableChangeListener rejected the iconify.
                    // The client calls swing_snapshot to check the outcome.
                }
            });
            return null;
        }

        // Not a Frame or JInternalFrame
        throw new MCPErrorResponseException(
                "Component does not support iconify. Call swing_snapshot or swing_get_cells to verify the list of actions");
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
