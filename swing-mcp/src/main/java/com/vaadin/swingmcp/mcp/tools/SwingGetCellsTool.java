/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
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
        // parameter validation
        int ref = params.getInt("ref");
        int offset = params.getInt("offset");
        int length = params.getInt("length");
        if (offset < 0) {
            throw new MCPErrorResponseException("offset must be non-negative, got " + offset);
        }
        if (length < 0) {
            throw new MCPErrorResponseException("length must be non-negative, got " + length);
        }

        // ref lookup — uses existing ref map
        Accessible accessible = context.getAccessibleByRef(ref);

        // eligibility check — role must be LIST or TREE. JTable
        // is rejected with a redirect to swing_get_items.
        requireGetCellsSupported(accessible, "swing_get_cells", "swing_get_items");

        AccessibleContext ac = accessible.getAccessibleContext();

        // Step 4: clear ref map and register parent as ref=1
        context.clearRefMap();
        context.putRef(1, accessible);

        // Step 5: compute iteration range
        int totalChildren = ac.getAccessibleChildrenCount();
        int end = (int) Math.min((long) offset + length, totalChildren);

        // Resolve role name for header
        AccessibleRole role = ac.getAccessibleRole();
        String roleName = role != null ? AccessibleNames.roleName(role) : "unknown";

        // Early return for empty result
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
                // null child — record for placeholder emission
                nullIndices.add(i);
            } else {
                SnapshotNode node = SnapshotNode.build(child);
                node.pruneChildren();
                childRoots.add(node);
            }
        }

        // assign refs starting from 2 (ref 1 is the parent)
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
                // null placeholder
                sb.append("- null\n");
            } else {
                childRoots.get(childRootIdx).render(0, sb);
                childRootIdx++;
            }
        }

        return MCPProtocol.Content.text(SnapshotNode.stripTrailingNewlines(sb.toString()));
    }
}
