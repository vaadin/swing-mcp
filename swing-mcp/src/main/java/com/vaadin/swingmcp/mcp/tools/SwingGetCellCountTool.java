package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_cell_count}: returns the total number of accessible
 * children (cells) of a large data component by ref, as a plain integer.
 *
 * <p>This is a thin wrapper around
 * {@code AccessibleContext.getAccessibleChildrenCount()} that lets the AI client
 * learn the cell count without fetching any cells.</p>
 *
 */
public class SwingGetCellCountTool extends AbstractSwingTool {

    public SwingGetCellCountTool() {
        super(SwingTools.SWING_GET_CELL_COUNT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // parameter validation
        int ref = params.getInt("ref");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // eligibility check — role must be LIST or TREE. JTable
        // is rejected with a redirect to swing_get_item_count.
        requireGetCellsSupported(accessible, "swing_get_cell_count", "swing_get_item_count");

        // compute count
        int totalChildren = accessible.getAccessibleContext().getAccessibleChildrenCount();

        // Step 5: return as plain text integer
        return MCPProtocol.Content.text(String.valueOf(totalChildren));
    }

    @Override
    public boolean isMutation() {
        // read-only tool, ref map is NOT cleared
        return false;
    }
}
