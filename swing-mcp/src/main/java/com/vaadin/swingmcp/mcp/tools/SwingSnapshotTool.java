package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP tool {@code swing_snapshot}: returns a compact accessibility-tree snapshot
 * of the Swing application so an AI agent can understand the UI structure and
 * identify components for interaction.
 *
 * <p>The snapshot pipeline has four phases, implemented in {@link SnapshotNode}:</p>
 * <ol>
 *   <li><b>Build</b> — mirror the accessibility tree into {@link SnapshotNode}s.</li>
 *   <li><b>Prune</b> — drop invisible/internal nodes; flatten transparent wrappers.</li>
 *   <li><b>AssignRefs</b> — number every action-bearing node 1…N.</li>
 *   <li><b>Render</b> — serialise to indented text.</li>
 * </ol>
 */
public class SwingSnapshotTool extends AbstractSwingTool {

    /**
     * Maximum accessible children shown for JTable / JList / JTree.
     * Delegates to {@link SnapshotNode#MAX_DATA_CHILDREN}.
     */
    public static final int MAX_DATA_CHILDREN = SnapshotNode.MAX_DATA_CHILDREN;

    // ── AbstractSwingTool ──────────────────────────────────────────────────────

    @Override
    public String getName() {
        return TOOL_SWING_SNAPSHOT;
    }

    @Override
    public String getDescription() {
        return "Returns an accessibility tree snapshot of the Swing application. "
                + "Use this to understand the current UI structure and identify "
                + "components for interaction via their numeric refs. "
                + "Mutation actions prefixed with ! are unavailable because "
                + "the component is disabled or read-only.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder().build();
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

        // Phase 1: build
        for (Component c : components) {
            if (c instanceof Accessible) {
                roots.add(SnapshotNode.build((Accessible) c));
            }
        }

        // Phase 2: prune children of each root (roots themselves are always kept)
        for (SnapshotNode root : roots) {
            root.pruneChildren();
        }

        // Phase 3: assignRefs — globally sequenced across all roots
        int nextRef = 1;
        for (SnapshotNode root : roots) {
            nextRef = root.assignRefs(nextRef, context);
        }

        // Phase 4: render
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) {
                sb.append("---\n");
            }
            roots.get(i).render(0, sb);
        }

        return MCPProtocol.Content.text(SnapshotNode.stripTrailingNewlines(sb.toString()));
    }
}
