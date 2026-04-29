package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tool {@code swing_get_cells}: enumerates accessible children of a large
 * data component (JTable, JList, JTree) with paging support.
 *
 * <p>Returns a paged accessibility tree (same format as {@code swing_snapshot})
 * rooted at the requested children. <b>Replaces the ref map</b> — the parent
 * component gets ref=1 and child refs start from 2.</p>
 *
 * @see <a href="tool-020-swing-get-cells.md">T-020</a>
 */
public class SwingGetCellsTool extends AbstractSwingTool {

    public SwingGetCellsTool() {
        super(SwingTools.SWING_GET_CELLS);
    }

    @Override
    public boolean isMutation() {
        return false;
    }

    @Override
    public MCPProtocol.Content execute(Parameters params,
                                       SwingToolContext context) throws Exception {
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

        // Step 2 (BR-02): ref lookup — uses existing ref map
        Accessible accessible = context.getAccessibleByRef(ref);

        // Step 3 (BR-03): eligibility check — role must be LIST or TREE. JTable
        // is rejected with a redirect to swing_get_items.
        requireGetCellsSupported(accessible, "swing_get_cells", "swing_get_items");

        AccessibleContext ac = accessible.getAccessibleContext();

        // Step 4 (BR-05, BR-08, BR-11): clear ref map and register parent as ref=1
        context.clearRefMap();
        context.putRef(1, accessible);

        // Step 5: compute iteration range
        int totalChildren = ac.getAccessibleChildrenCount();
        int end = (int) Math.min((long) offset + length, totalChildren);

        // Resolve role name for header
        AccessibleRole role = ac.getAccessibleRole();
        String roleName = role != null ? AccessibleNames.roleName(role) : "unknown";

        // Early return for empty result (BR-07)
        if (offset >= totalChildren) {
            int shown = 0;
            String header = "Showing " + shown + " children from offset " + offset
                    + " (total " + totalChildren + ") for " + roleName + " [ref=1]";
            return MCPProtocol.Content.text(header);
        }

        // Step 6: enumerate children — build and prune each subtree
        List<SnapshotNode> childRoots = new ArrayList<>();
        List<Integer> nullIndices = new ArrayList<>();

        for (int i = offset; i < end; i++) {
            Accessible child = ac.getAccessibleChild(i);
            if (child == null) {
                // BR-13: null child — record for placeholder emission
                nullIndices.add(i);
            } else {
                SnapshotNode node = SnapshotNode.build(child);
                node.pruneChildren();
                childRoots.add(node);
            }
        }

        // Step 7 (BR-11): assign refs starting from 2 (ref 1 is the parent)
        int nextRef = 2;
        for (SnapshotNode root : childRoots) {
            nextRef = root.assignRefs(nextRef, context);
        }

        // Step 8: render
        int shown = end - offset;
        String header = "Showing " + shown + " children from offset " + offset
                + " (total " + totalChildren + ") for " + roleName + " [ref=1]";

        StringBuilder sb = new StringBuilder();
        sb.append(header).append('\n');

        // Render children in order, interleaving null placeholders
        int childRootIdx = 0;
        for (int i = offset; i < end; i++) {
            if (nullIndices.contains(i)) {
                // BR-13: null placeholder
                sb.append("- null\n");
            } else {
                childRoots.get(childRootIdx).render(0, sb);
                childRootIdx++;
            }
        }

        return MCPProtocol.Content.text(SnapshotNode.stripTrailingNewlines(sb.toString()));
    }
}
