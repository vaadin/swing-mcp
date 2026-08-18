package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleSelection;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_select_all}: selects all items in a multi-selection
 * UI component by ref.
 *
 * <p>Only works on components marked {@code multi-selection} in the snapshot.
 * Single-selection components are rejected. For JTable, uses
 * {@link JTable#selectAll()} directly because the accessibility API's
 * {@code selectAllAccessibleSelection()} is a no-op on JTable.</p>
 *
 * @see <a href="tool-019-swing-select-all.md">T-019</a>
 */
public class SwingSelectAllTool extends AbstractSwingTool {

    public SwingSelectAllTool() {
        super(SwingTools.SWING_SELECT_ALL);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // Step 1 (BR-01): parameter validation
        int ref = params.getInt("ref");

        // Step 2 (BR-02): ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03 + BR-04): selection support + multi-selection check
        requireMultiSelectable(accessible, "swing_select_all");

        // Step 4 (BR-05): effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // Step 5: fire-and-forget dispatch
        if (accessible instanceof JTable) {
            // BR-09: JTable.selectAll() — accessibility API is broken (no-op)
            JTable table = (JTable) accessible;
            SwingUtilities.invokeLater(table::selectAll);
        } else {
            // BR-10: AccessibleSelection.selectAllAccessibleSelection()
            AccessibleSelection as = accessible.getAccessibleContext().getAccessibleSelection();
            SwingUtilities.invokeLater(as::selectAllAccessibleSelection);
        }

        // Step 6 (BR-08): DR-dispatched-echo success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        // BR-07: mutation tool, ref map IS cleared
        return true;
    }
}
