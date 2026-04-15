package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_toggle_expand}: expands or collapses a JTree node by ref.
 *
 * <p>Looks up the node by ref, verifies it is enabled and supports the toggle-expand
 * action, then invokes the matching {@link AccessibleAction}.</p>
 *
 * @see <a href="use-case-010-swing-toggle-expand.md">UC-010</a>
 */
public class SwingToggleExpandTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_TOGGLE_EXPAND;
    }

    @Override
    public String getDescription() {
        return "Toggles (expand or collapses based on current state) a JTree node by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .build();
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
                    "Component does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions");
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
