package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_cell_count}: returns the total number of accessible
 * children (cells) of a large data component by ref, as a plain integer.
 *
 * <p>This is a thin wrapper around
 * {@code AccessibleContext.getAccessibleChildrenCount()} that lets the AI client
 * learn the cell count without fetching any cells.</p>
 *
 * @see <a href="tool-021-swing-get-cell-count.md">T-021</a>
 */
public class SwingGetCellCountTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_GET_CELL_COUNT;
    }

    @Override
    public String getDescription() {
        return "Get the total number of accessible children (cells) of a large data component "
                + "(JList, JTree) by ref. Returns the count as a plain integer in the same "
                + "index space as swing_get_cells. For JList, this is the item count. For "
                + "JTree, this is the top-level visible node count. For JTable, use "
                + "swing_get_item_count instead \u2014 table cells are plain text "
                + "labels with no actionable children. Requires a ref obtained from "
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

        // Step 3 (BR-03): eligibility check — role must be LIST or TREE. JTable
        // is rejected with a redirect to swing_get_item_count.
        requireGetCellsSupported(accessible, "swing_get_cell_count", "swing_get_item_count");

        // Step 4 (BR-07): compute count
        int totalChildren = accessible.getAccessibleContext().getAccessibleChildrenCount();

        // Step 5: return as plain text integer
        return MCPProtocol.Content.text(String.valueOf(totalChildren));
    }

    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
