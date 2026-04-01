package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_click}: clicks a UI component identified by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the click action
 * and is effectively enabled, then invokes the matching
 * {@link AccessibleAction}.</p>
 *
 * @see <a href="use-case-004-swing-click.md">UC-004</a>
 */
public class SwingClickTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_CLICK;
    }

    @Override
    public String getDescription() {
        return "Click a UI component by ref. Call swing_snapshot first to obtain refs.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-06: check click support before walking the parent chain
        int actionIndex = SwingUtils.supportsClick(accessible);
        if (actionIndex < 0) {
            throw new MCPErrorResponseException(
                    "Component does not support click. Call swing_snapshot to verify the list of actions");
        }

        // BR-05: check effectively enabled
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be clicked");
        }

        // BR-03: fire the click action asynchronously (fire-and-forget)
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleAction aa = ac.getAccessibleAction();
        SwingUtilities.invokeLater(() -> aa.doAccessibleAction(actionIndex));
        return null;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
