package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_toggle_popup}: opens or closes the popup of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the toggle-popup action
 * and is effectively enabled, then invokes the matching {@link AccessibleAction}.</p>
 *
 * @see <a href="tool-007-swing-toggle-popup.md">T-007</a>
 */
public class SwingTogglePopupTool extends AbstractSwingTool {

    public SwingTogglePopupTool() {
        super(SwingTools.SWING_TOGGLE_POPUP);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-04: toggle-popup support check
        int actionIndex = SwingUtils.supportsTogglePopup(accessible);
        if (actionIndex < 0) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_toggle_popup. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-06: effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be interacted with");
        }

        // BR-03: fire the action asynchronously (fire-and-forget)
        AccessibleAction aa = accessible.getAccessibleContext().getAccessibleAction();
        SwingUtilities.invokeLater(() -> aa.doAccessibleAction(actionIndex));
        // BR-12: D_dispatched_echo success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
