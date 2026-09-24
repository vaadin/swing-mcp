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
 * MCP tool {@code swing_get_cells}: a page of a {@code JList}'s or {@code JTree}'s accessible
 * children by ref, each rendered as a snapshot subtree ({@code offset=5, length=3}):
 *
 * <pre>
 * Showing 3 children from offset 5 (total 20) for list [ref=1]
 * - (label) "Item-5" [ref=2] actions: click
 * - (label) "Item-6" [ref=3] actions: click
 * - (label) "Item-7" [ref=4] actions: click
 * </pre>
 *
 * The format is owned by {@code design/snapshot-format.md}; a {@code JTable} is refused
 * (D_no_jtable_cells).
 *
 * <p>Read-only, yet it <b>replaces the ref map</b>: the parent becomes ref 1, so the model can
 * page on without a new snapshot.
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
        int ref = params.getInt("ref");
        int offset = params.getInt("offset");
        int length = params.getInt("length");
        if (offset < 0) {
            throw new MCPErrorResponseException("offset must be non-negative, got " + offset);
        }
        if (length < 0) {
            throw new MCPErrorResponseException("length must be non-negative, got " + length);
        }

        Accessible accessible = context.getAccessibleByRef(ref);
        requireGetCellsSupported(accessible, "swing_get_cells", "swing_get_items");

        AccessibleContext ac = accessible.getAccessibleContext();

        context.clearRefMap();
        context.putRef(1, accessible);

        int totalChildren = ac.getAccessibleChildrenCount();
        // long: offset + length can overflow int.
        int end = (int) Math.min((long) offset + length, totalChildren);

        AccessibleRole role = ac.getAccessibleRole();
        String roleName = role != null ? AccessibleNames.roleName(role) : "unknown";

        if (offset >= totalChildren) {
            int shown = 0;
            String header = "Showing " + shown + " children from offset " + offset
                    + " (total " + totalChildren + ") for " + roleName + " [ref=1]";
            return MCPProtocol.Content.text(header);
        }

        List<SnapshotNode> childRoots = new ArrayList<>();
        List<Integer> nullIndices = new ArrayList<>();

        for (int i = offset; i < end; i++) {
            Accessible child = ac.getAccessibleChild(i);
            if (child == null) {
                nullIndices.add(i);
            } else {
                SnapshotNode node = SnapshotNode.build(child);
                node.pruneChildren();
                childRoots.add(node);
            }
        }

        int nextRef = 2;
        for (SnapshotNode root : childRoots) {
            nextRef = root.assignRefs(nextRef, context);
        }

        int shown = end - offset;
        String header = "Showing " + shown + " children from offset " + offset
                + " (total " + totalChildren + ") for " + roleName + " [ref=1]";

        StringBuilder sb = new StringBuilder();
        sb.append(header).append('\n');

        int childRootIdx = 0;
        for (int i = offset; i < end; i++) {
            if (nullIndices.contains(i)) {
                sb.append("- null\n");
            } else {
                childRoots.get(childRootIdx).render(0, sb);
                childRootIdx++;
            }
        }

        return MCPProtocol.Content.text(SnapshotNode.stripTrailingNewlines(sb.toString()));
    }
}
