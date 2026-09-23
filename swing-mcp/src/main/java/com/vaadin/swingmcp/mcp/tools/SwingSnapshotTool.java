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

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;
import org.jspecify.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tool {@code swing_snapshot}: runs the four {@link SnapshotNode} passes — build, prune,
 * assign refs, render — over every considered component and replaces the session's ref map.
 * The pipeline is in {@code design/architecture.md}; the text it emits is owned by
 * {@code design/snapshot-format.md}.
 */
public class SwingSnapshotTool extends AbstractSwingTool {

    public SwingSnapshotTool() {
        super(SwingTools.SWING_SNAPSHOT);
    }

    @Override
    public boolean isMutation() {
        return false;
    }

    // ── Execute ────────────────────────────────────────────────────────────────

    @Override
    public MCPProtocol.Content execute(Parameters params,
                                       SwingToolContext context) throws Exception {
        context.clearRefMap();

        List<Component> components = context.getConsideredComponents();
        List<SnapshotNode> roots = new ArrayList<>();

        for (Component c : components) {
            if (c instanceof Accessible) {
                roots.add(SnapshotNode.build((Accessible) c));
            }
        }

        for (SnapshotNode root : roots) {
            root.pruneChildren();
        }

        int nextRef = 1;
        for (SnapshotNode root : roots) {
            nextRef = root.assignRefs(nextRef, context);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) {
                sb.append("---\n");
            }
            SnapshotNode root = roots.get(i);
            String modalHeader = buildModalStackHeader(root.accessible);
            if (modalHeader != null) {
                sb.append(modalHeader);
            }
            root.render(0, sb);
        }

        String rendered = SnapshotNode.stripTrailingNewlines(sb.toString());

        // Filters the node graph, not the rendered lines (D_tree_filter_over_line_grep).
        String filter = params.getStringOrNull("filter_substring");
        if (filter != null && !filter.isEmpty()) {
            String filterLower = filter.toLowerCase();

            // An iconified frame is listed whether or not it matches: what the filter looks for
            // may sit among its hidden children (D_iconified_children_hidden).
            List<SnapshotNode> shown = new ArrayList<>();
            for (SnapshotNode root : roots) {
                if (root.isIconifiedFrame() || root.subtreeMatchesFilter(filterLower)) {
                    shown.add(root);
                }
            }
            if (shown.isEmpty()) {
                return MCPProtocol.Content.text(
                        "No lines matched filter_substring '" + filter + "'");
            }

            StringBuilder filtered = new StringBuilder();
            filtered.append("[filter active: only nodes matching \"")
                    .append(filter)
                    .append("\" and their ancestors/descendants are shown]")
                    .append('\n');

            for (SnapshotNode root : shown) {
                root.renderFiltered(filterLower, 0, filtered);
            }

            return MCPProtocol.Content.text(
                    SnapshotNode.stripTrailingNewlines(filtered.toString()));
        }

        return MCPProtocol.Content.text(rendered);
    }

    // ── D_modal_stack_header: modal-stack header ──────────────────────

    /**
     * Builds the {@code [modal stack (N, topmost first): …]} line for a modal-dialog root
     * (D_modal_stack_header). Only the root must be modal; invisible owners are skipped.
     *
     * @return the line, ending in {@code '\n'}, or {@code null} when the root gets no header
     */
    private static @Nullable String buildModalStackHeader(Accessible rootAccessible) {
        if (!(rootAccessible instanceof Dialog)) {
            return null;
        }
        Dialog rootDialog = (Dialog) rootAccessible;
        if (!rootDialog.isModal()) {
            return null;
        }

        List<Window> chain = new ArrayList<>();
        chain.add(rootDialog);
        for (Window w = rootDialog.getOwner(); w != null; w = w.getOwner()) {
            if (SwingUtils.isVisible(w)) {
                chain.add(w);
            }
        }

        if (chain.size() < 2) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[modal stack (")
                .append(chain.size())
                .append(", topmost first): ");
        for (int i = 0; i < chain.size(); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            Window w = chain.get(i);
            Class<?> displayClass = ComponentClassResolver.resolveDisplayClass(w);
            sb.append(displayClass.getSimpleName());
            AccessibleContext ctx = w.getAccessibleContext();
            String name = SwingUtils.sanitizeForQuotedSlot(
                    ctx != null ? ctx.getAccessibleName() : null);
            if (name != null) {
                sb.append(" \"").append(name).append('"');
            }
        }
        sb.append("]\n");
        return sb.toString();
    }
}
