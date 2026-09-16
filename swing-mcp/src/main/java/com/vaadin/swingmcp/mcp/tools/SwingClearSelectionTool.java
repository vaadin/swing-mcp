package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import java.util.Collections;
import java.util.Map;

/**
 * MCP tool {@code swing_clear_selection}: clears the selection of a UI component by ref.
 *
 * <p>Delegates to {@link SwingSetSelectionTool} with an empty {@code indices} array.
 * Every rule that tool enforces applies here too — including the refusal on
 * {@link javax.swing.JTabbedPane}, which has no empty selection to clear
 * (R_accessible_selection_writes). The dispatch echo names {@code clear-selection}, not the
 * {@code set-selection} it delegates to (D_dispatched_echo).</p>
 */
public class SwingClearSelectionTool extends AbstractSwingTool {

    public SwingClearSelectionTool() {
        super(SwingTools.SWING_CLEAR_SELECTION);
    }

    private final SwingSetSelectionTool delegate = new SwingSetSelectionTool();

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Parameters delegateParams = new Parameters(
                Map.of("ref", ref, "indices", Collections.emptyList()));
        // Delegate performs validation + fire-and-forget dispatch (or throws on error).
        // Discard the delegate's echo — it says "Dispatched set-selection ..." with the wrong
        // action name. Per D_dispatched_echo the echo reflects the MCP-exposed tool name, which here
        // is swing_clear_selection.
        delegate.execute(delegateParams, context);
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
