package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;

/**
 * MCP tool {@code swing_decrement}: decrements the value of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the decrement action
 * and is effectively enabled, then invokes the matching {@link AccessibleAction}.</p>
 *
 * @see <a href="use-case-009-swing-decrement.md">UC-009</a>
 */
public class SwingDecrementTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_DECREMENT;
    }

    @Override
    public String getDescription() {
        return "Decrement the value of a UI component (e.g. JSpinner, JSlider) by one step. Call swing_snapshot first to obtain refs.";
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

        // BR-02: ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-04: decrement support check
        int actionIndex = SwingUtils.supportsDecrement(accessible);
        if (actionIndex < 0) {
            throw new MCPErrorResponseException(
                    "Component does not support decrement. Call swing_snapshot to verify the list of actions");
        }

        // BR-06: effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be interacted with");
        }

        // BR-07: invoke the action
        AccessibleAction aa = accessible.getAccessibleContext().getAccessibleAction();
        boolean performed = aa.doAccessibleAction(actionIndex);
        if (!performed) {
            throw new MCPErrorResponseException(
                    "The action was not performed, no additional information has been provided. Probable causes: component hit min value and refused to decrement further");
        }

        return null;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
