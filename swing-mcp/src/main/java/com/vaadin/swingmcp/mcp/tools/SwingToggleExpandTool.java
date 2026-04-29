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
 * MCP tool {@code swing_toggle_expand}: expands or collapses a JTree node by ref.
 *
 * <p>Looks up the node by ref, verifies it is enabled and supports the toggle-expand
 * action, then invokes the matching {@link AccessibleAction}.</p>
 *
 * @see <a href="tool-010-swing-toggle-expand.md">T-010</a>
 */
public class SwingToggleExpandTool extends AbstractSwingTool {

    public SwingToggleExpandTool() {
        super(SwingTools.SWING_TOGGLE_EXPAND);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-06: effectively enabled check (before BR-04: disabled nodes may strip their actions)
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be interacted with");
        }

        // BR-04: toggle-expand support check
        int actionIndex = SwingUtils.supportsToggleExpand(accessible);
        if (actionIndex < 0) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-03: fire the action asynchronously (fire-and-forget)
        AccessibleAction aa = accessible.getAccessibleContext().getAccessibleAction();
        SwingUtilities.invokeLater(() -> aa.doAccessibleAction(actionIndex));
        // BR-11: DR-010 success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
