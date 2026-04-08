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
 * @see <a href="use-case-021-swing-get-cell-count.md">UC-021</a>
 */
public class SwingGetCellCountTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_GET_CELL_COUNT;
    }

    @Override
    public String getDescription() {
        return "Get the total number of accessible children (cells) of a large data component by ref. "
                + "Returns the count as a plain integer. For JTable, this is rows \u00d7 columns "
                + "(individual cells in row-major order \u2014 same index space as swing_get_cells). "
                + "For JList, this is the item count. For JTree, this is the top-level visible "
                + "node count. Requires a ref obtained from swing_snapshot or swing_get_cells.";
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

        // Step 3 (BR-03): eligibility check
        requireLargeDataComponent(accessible, "swing_get_cell_count");

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
