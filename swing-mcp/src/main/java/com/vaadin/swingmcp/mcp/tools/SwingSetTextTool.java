package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleEditableText;
import javax.accessibility.AccessibleState;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_set_text}: replaces the full text content of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports {@code set_text}, is effectively
 * enabled, and is editable, then delegates to
 * {@link AccessibleEditableText#setTextContents(String)}.</p>
 *
 * @see <a href="use-case-006-swing-set-text.md">UC-006</a>
 */
public class SwingSetTextTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_SET_TEXT;
    }

    @Override
    public String getDescription() {
        return "Set the text content of a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .requiredString("text", "The text to set")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: both params required
        int ref = params.getInt("ref");
        String text = params.getString("text");

        // BR-02: ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-04: set_text structural support check
        if (!SwingUtils.hasEditableText(accessible)) {
            throw new MCPErrorResponseException(
                    "Component does not support set_text. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-06: effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be edited");
        }

        // BR-07: editable state check
        AccessibleContext ac = accessible.getAccessibleContext();
        if (!ac.getAccessibleStateSet().contains(AccessibleState.EDITABLE)) {
            throw new MCPErrorResponseException("Component is not editable");
        }

        // BR-03: fire the text replacement asynchronously (fire-and-forget)
        AccessibleEditableText aet = ac.getAccessibleEditableText();
        SwingUtilities.invokeLater(() -> aet.setTextContents(text));
        return null;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
