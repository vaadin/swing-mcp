package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool {@code swing_get_selectable_items}: lists selectable items of a UI
 * component by ref, with paging support.
 *
 * <p>Returns a JSON object with {@code totalCount} and {@code items} (array of
 * objects, each with {@code index} and {@code name}). For JTable, index is the
 * row index and name is a pipe-separated summary of cell values. For
 * JTabbedPane, disabled tabs include {@code "enabled": false}.</p>
 *
 * @see <a href="use-case-017-swing-get-selectable-items.md">UC-017</a>
 */
public class SwingGetSelectableItemsTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_GET_SELECTABLE_ITEMS;
    }

    @Override
    public String getDescription() {
        return "List selectable items of a UI component by ref. Returns a paged JSON array "
                + "of items (0-based index + name). Indices are in the selection item index "
                + "space \u2014 pass them directly to swing_set_selection. For JTable, this is "
                + "the canonical way to page through rows regardless of selection mode: index "
                + "is the row index and name is a pipe-separated summary of cell values (use "
                + "this instead of swing_get_cells, which does not support JTable). Note: "
                + "swing_set_selection still requires the table to be in row-selection mode. "
                + "Requires offset and length parameters for paging. If offset+length is bigger "
                + "than the amount of data available, fewer items than requested may be returned. "
                + "Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .requiredInteger("offset", "0-based start index for paging").withMinimum(0)
                .requiredInteger("length", "Number of items to return").withMinimum(0)
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // Step 1 (BR-01): parameter validation
        int ref = params.getInt("ref");
        int offset = params.getInt("offset");
        int length = params.getInt("length");
        if (offset < 0) {
            throw new MCPErrorResponseException("offset must be non-negative, got " + offset);
        }
        if (length < 0) {
            throw new MCPErrorResponseException("length must be non-negative, got " + length);
        }

        // Step 2 (BR-02): ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03): read-only gate — any JTable passes (regardless of
        // selection mode); other components must satisfy supportsSelection.
        requireGetSelectableItemsSupported(accessible, "swing_get_selectable_items");

        // BR-04: all access on EDT (guaranteed by MCPServer.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();

        // Step 4: determine totalCount and enumerate items
        int totalCount = SwingUtils.getSelectableItemsCount(accessible);

        // BR-07: offset beyond totalCount → empty items
        // Integer overflow guard: use long arithmetic for end bound
        int end = (int) Math.min((long) offset + length, totalCount);
        int start = Math.min(offset, totalCount);

        List<Map<String, Object>> items = new ArrayList<>();

        if (accessible instanceof JTable) {
            // BR-09: JTable row enumeration
            AccessibleTable at = ac.getAccessibleTable();
            int cols = at.getAccessibleColumnCount();
            for (int r = start; r < end; r++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", r);
                item.put("name", SwingUtils.buildTableRowText(at, r, cols));
                items.add(item);
            }
        } else if (accessible instanceof JComboBox) {
            // BR-10: JComboBox item enumeration
            JComboBox<?> combo = (JComboBox<?>) accessible;
            for (int i = start; i < end; i++) {
                Object obj = combo.getItemAt(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("name", obj != null ? obj.toString() : null);
                items.add(item);
            }
        } else if (accessible instanceof JTabbedPane) {
            // BR-11 + BR-12: JTabbedPane enumeration with disabled indicator
            JTabbedPane tp = (JTabbedPane) accessible;
            for (int i = start; i < end; i++) {
                Accessible child = ac.getAccessibleChild(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("name", child != null ? child.getAccessibleContext().getAccessibleName() : null);
                // BR-12: only emit enabled when false
                if (!tp.isEnabledAt(i)) {
                    item.put("enabled", false);
                }
                items.add(item);
            }
        } else {
            // BR-11: generic enumeration (JList)
            for (int i = start; i < end; i++) {
                Accessible child = ac.getAccessibleChild(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", i);
                item.put("name", child != null ? child.getAccessibleContext().getAccessibleName() : null);
                // BR-13: null child — name is null, entry not skipped
                items.add(item);
            }
        }

        // Build JSON response
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("items", items);
        return MCPProtocol.Content.json(result);
    }

    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
