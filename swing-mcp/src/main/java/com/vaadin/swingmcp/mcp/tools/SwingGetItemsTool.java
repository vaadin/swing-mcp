package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.JComboBox;
import javax.swing.JTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool {@code swing_get_items}: lists items of a UI
 * component by ref, with paging support.
 *
 * <p>Supported components: {@code JList}, {@code JComboBox}, {@code JTable}.
 * Returns a JSON object with {@code totalCount} and {@code items} (array of
 * objects, each with {@code index} and {@code name}). For JTable, index is
 * the row index and name is a pipe-separated summary of cell values.
 * {@code JTabbedPane} is not a supported target (dropped per P-001); tabs are
 * rendered inline in the snapshot.</p>
 *
 * @see <a href="tool-017-swing-get-items.md">T-017</a>
 */
public class SwingGetItemsTool extends AbstractSwingTool {

    public SwingGetItemsTool() {
        super(SwingTools.SWING_GET_ITEMS);
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
        requireGetItemsSupported(accessible, "swing_get_items");

        // BR-04: all access on EDT (guaranteed by SwingMCP.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();

        // Step 4: determine totalCount and enumerate items
        int totalCount = SwingUtils.getItemCount(accessible);

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
        } else {
            // BR-11: JList enumeration (only non-JTable, non-JComboBox target
            // left after JTabbedPane was dropped per P-001 Wave A).
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
