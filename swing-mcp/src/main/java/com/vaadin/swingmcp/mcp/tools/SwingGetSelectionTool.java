package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleSelection;
import javax.accessibility.AccessibleTable;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP tool {@code swing_get_selection}: reads the current selection of a UI
 * component by ref.
 *
 * <p>Returns JSON with {@code selectedCount} and {@code selected} items
 * (0-based index + name). For JTable, index is the row index and name is a
 * pipe-separated summary of cell values.</p>
 *
 * @see <a href="tool-014-swing-get-selection.md">T-014</a>
 */
public class SwingGetSelectionTool extends AbstractSwingTool {

    /** Maximum number of selected items returned before truncation (BR-09). */
    static final int MAX_SELECTION_ITEMS = 100;

    public SwingGetSelectionTool() {
        super(SwingTools.SWING_GET_SELECTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: ref lookup (fail fast)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-03: check selection support with JTable-specific error message
        requireSelectable(accessible, "swing_get_selection");

        // BR-04: all access on EDT (guaranteed by SwingMCP.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleSelection as = ac.getAccessibleSelection();

        List<Map<String, Object>> selected;
        boolean truncated;

        // Step 4: JTable row aggregation path (BR-11)
        if (accessible instanceof JTable) {
            AccessibleTable at = ac.getAccessibleTable();
            int cols = at.getAccessibleColumnCount();
            int selCount = as.getAccessibleSelectionCount();

            // Collect unique row indices (insertion-ordered)
            Set<Integer> rows = new LinkedHashSet<>();
            for (int i = 0; i < selCount; i++) {
                Accessible cell = as.getAccessibleSelection(i);
                if (cell == null) continue;
                int cellIndex = cell.getAccessibleContext().getAccessibleIndexInParent();
                rows.add(cellIndex / cols);
            }

            truncated = rows.size() > MAX_SELECTION_ITEMS;
            selected = new ArrayList<>();
            int count = 0;
            for (int row : rows) {
                if (count >= MAX_SELECTION_ITEMS) break;
                String name = SwingUtils.buildTableRowText(at, row, cols);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", row);
                item.put("name", name);
                selected.add(item);
                count++;
            }
        } else {
            // Step 5: generic path
            int selCount = as.getAccessibleSelectionCount();
            truncated = selCount > MAX_SELECTION_ITEMS;
            int limit = Math.min(selCount, MAX_SELECTION_ITEMS);
            selected = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                Accessible child = as.getAccessibleSelection(i);
                if (child == null) continue;
                AccessibleContext childCtx = child.getAccessibleContext();
                int itemIndex = childCtx.getAccessibleIndexInParent();
                String name = childCtx.getAccessibleName();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", itemIndex);
                item.put("name", name);
                selected.add(item);
            }
        }

        // Step 6: build JSON
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("selectedCount", selected.size());
        result.put("selected", selected);
        if (truncated) {
            result.put("truncated", true);
        }
        return MCPProtocol.Content.json(result);
    }


    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
