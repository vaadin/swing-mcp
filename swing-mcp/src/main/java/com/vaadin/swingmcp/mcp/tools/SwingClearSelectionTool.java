package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import java.util.Collections;
import java.util.Map;

/**
 * MCP tool {@code swing_clear_selection}: clears the selection of a UI component by ref.
 *
 * <p>Delegates to {@link SwingSetSelectionTool} with an empty {@code indices} array.
 * See UC-016 / UC-015 for full specification.</p>
 */
public class SwingClearSelectionTool extends AbstractSwingTool {

    private final SwingSetSelectionTool delegate = new SwingSetSelectionTool();

    @Override
    public String getName() {
        return TOOL_SWING_CLEAR_SELECTION;
    }

    @Override
    public String getDescription() {
        return "Clear the selection of a UI component by ref. "
                + "Works with multi-select components (list, table) and some single-select "
                + "components (combo_box). page_tab_list with tabs does not allow an empty "
                + "selection. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Parameters delegateParams = new Parameters(
                Map.of("ref", ref, "indices", Collections.emptyList()));
        return delegate.execute(delegateParams, context);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
