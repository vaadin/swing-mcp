package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_selectable_items_count}: returns the total number
 * of selectable items of a UI component by ref, as a plain integer.
 *
 * <p>This is a thin wrapper around {@link SwingUtils#getSelectableItemsCount}
 * that lets the AI client learn the item count without fetching any items.</p>
 *
 * @see <a href="use-case-018-swing-get-selectable-items-count.md">UC-018</a>
 */
public class SwingGetSelectableItemsCountTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_GET_SELECTABLE_ITEMS_COUNT;
    }

    @Override
    public String getDescription() {
        return "Get the total number of selectable items of a UI component by ref. "
                + "Returns the count as a plain integer. For JTable, this is the row count "
                + "(only row-selection mode is supported). Call swing_snapshot first to obtain refs.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // Step 1 (BR-01): parameter validation
        int ref = params.getInt("ref");

        // Step 2 (BR-02): ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03): selection support check with JTable-specific error
        requireSelectable(accessible, "get_selectable_items_count");

        // Step 4 (BR-07): compute count
        int totalCount = SwingUtils.getSelectableItemsCount(accessible);

        // Step 5: return as plain text integer
        return MCPProtocol.Content.text(String.valueOf(totalCount));
    }

    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
