package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_item_count}: returns the total number
 * of items of a UI component by ref, as a plain integer.
 *
 * <p>This is a thin wrapper around {@link SwingUtils#getItemCount}
 * that lets the AI client learn the item count without fetching any items.</p>
 *
 * @see <a href="tool-018-swing-get-item-count.md">T-018</a>
 */
public class SwingGetItemCountTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_GET_ITEM_COUNT;
    }

    @Override
    public String getDescription() {
        return "Get the total number of items of a UI component by ref. "
                + "Supported components: JList, JComboBox, JTable. Returns the count as a "
                + "plain integer. For JTable, this is the canonical way to get the row count "
                + "regardless of selection mode (use this instead of swing_get_cell_count, "
                + "which does not support JTable). Note: swing_set_selection still requires "
                + "the table to be in row-selection mode. For JTabbedPane, count the tabs "
                + "directly from the snapshot \u2014 each tab renders as `- (page_tab) N "
                + "\"title\"` with its 0-based index. Requires a ref obtained from "
                + "swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // Step 1 (BR-01): parameter validation
        int ref = params.getInt("ref");

        // Step 2 (BR-02): ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03): read-only gate — any JTable passes (regardless of
        // selection mode); other components must satisfy supportsSelection.
        requireGetItemsSupported(accessible, "swing_get_item_count");

        // Step 4 (BR-07): compute count
        int totalCount = SwingUtils.getItemCount(accessible);

        // Step 5: return as plain text integer
        return MCPProtocol.Content.text(String.valueOf(totalCount));
    }

    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
