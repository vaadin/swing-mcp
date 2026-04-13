package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MCP tool {@code swing_set_selection}: sets the selection of a UI component by ref.
 *
 * <p>Accepts an array of 0-based item indices. For single-selection components,
 * at most one index is allowed. For JTable, indices are row indices — the tool
 * translates to cell indices internally. An empty array clears the selection.</p>
 *
 * @see <a href="use-case-015-swing-set-selection.md">UC-015</a>
 */
public class SwingSetSelectionTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_SET_SELECTION;
    }

    @Override
    public String getDescription() {
        return "Set the selection of a UI component by ref. Pass 0-based item indices "
                + "(as returned by swing_get_selection). For single-selection components, "
                + "pass at most one index. For JTable, pass row indices \u2014 the tool translates "
                + "to cell indices internally. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .requiredArray("indices", "Array of 0-based item indices to select (empty array clears selection)")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // Step 1 (BR-01): parameter validation
        int ref = params.getInt("ref");
        List<Integer> indices = params.getIntArray("indices");

        // Step 2 (BR-02): ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03): selection support check with JTable-specific error
        requireSelectable(accessible, "swing_set_selection");

        // Step 4 (BR-05): effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // Step 5: obtain AccessibleSelection
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleSelection as = ac.getAccessibleSelection();

        // Step 6 (BR-13): deduplicate indices
        Set<Integer> deduplicated = new LinkedHashSet<>(indices);

        // Step 7 (BR-07): empty indices = clear path
        if (deduplicated.isEmpty()) {
            if (accessible instanceof JTabbedPane && ((JTabbedPane) accessible).getTabCount() > 0) {
                throw new MCPErrorResponseException(
                        "This component does not allow the selection to be empty.");
            }
            SwingUtilities.invokeLater(as::clearAccessibleSelection);
            return null;
        }

        // Step 8 (BR-08): single-selection enforcement
        if (SwingUtils.supportsSingleSelection(accessible) && deduplicated.size() > 1) {
            throw new MCPErrorResponseException(
                    "Component is in single-selection mode. Pass exactly one index (or an empty array to clear).");
        }

        // Step 9: determine item count for bounds checking
        int itemCount = SwingUtils.getItemCount(accessible);

        // Step 10 (BR-11/BR-12): bounds validation
        for (int index : deduplicated) {
            if (index < 0 || index >= itemCount) {
                throw new MCPErrorResponseException(
                        "Index " + index + " is out of bounds. Valid range is [0, " + itemCount + ").");
            }
        }

        // Step 11 (BR-14): disabled tab check (JTabbedPane only)
        if (accessible instanceof JTabbedPane) {
            JTabbedPane tabbedPane = (JTabbedPane) accessible;
            for (int index : deduplicated) {
                if (!tabbedPane.isEnabledAt(index)) {
                    throw new MCPErrorResponseException(
                            "Tab at index " + index + " is disabled.");
                }
            }
        }

        // Step 12: fire-and-forget dispatch
        // JTable: use direct API (JTable.addRowSelectionInterval) because
        // AccessibleSelection.addAccessibleSelection delegates to changeSelection()
        // which is unreliable inside invokeLater (selection not applied).
        if (accessible instanceof JTable) {
            JTable table = (JTable) accessible;
            Set<Integer> rows = deduplicated;
            SwingUtilities.invokeLater(() -> {
                table.clearSelection();
                for (int row : rows) {
                    table.addRowSelectionInterval(row, row);
                }
            });
        } else {
            Set<Integer> items = deduplicated;
            SwingUtilities.invokeLater(() -> {
                as.clearAccessibleSelection();
                for (int i : items) {
                    as.addAccessibleSelection(i);
                }
            });
        }

        // Step 13
        return null;
    }

    @Override
    public boolean isMutation() {
        // BR-06: mutation tool, ref map IS cleared
        return true;
    }
}
