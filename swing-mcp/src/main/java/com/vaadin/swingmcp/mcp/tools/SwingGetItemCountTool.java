package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_item_count}: returns the total number
 * of items of a UI component by ref, as a plain integer.
 *
 * <p>This is a thin wrapper around {@link SwingUtils#getItemCount}
 * that lets the AI client learn the item count without fetching any items.</p>
 *
 */
public class SwingGetItemCountTool extends AbstractSwingTool {

    public SwingGetItemCountTool() {
        super(SwingTools.SWING_GET_ITEM_COUNT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // parameter validation
        int ref = params.getInt("ref");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // read-only gate — any JTable passes (regardless of
        // selection mode); other components must satisfy supportsSelection.
        requireGetItemsSupported(accessible, "swing_get_item_count");

        // compute count
        int totalCount = SwingUtils.getItemCount(accessible);

        // Step 5: return as plain text integer
        return MCPProtocol.Content.text(String.valueOf(totalCount));
    }

    @Override
    public boolean isMutation() {
        // read-only tool, ref map is NOT cleared
        return false;
    }
}
