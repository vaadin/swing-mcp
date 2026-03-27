package com.vaadin.swingmcp.mcp.tools;

import javax.accessibility.Accessible;
import java.util.ArrayList;
import java.util.List;

/**
 * Internal tree node used during the four-phase snapshot pipeline.
 * Each node mirrors one node in the accessibility tree and holds its
 * pruned children, an optional numeric ref, and truncation metadata
 * for large-data components.
 */
class SnapshotNode {

    /** The accessibility object this node mirrors. Never null. */
    final Accessible accessible;

    /** Mutable children list; replaced during Phase 2 (prune). */
    List<SnapshotNode> children = new ArrayList<>();

    /**
     * Numeric ref assigned during Phase 3 (assignRefs).
     * {@code 0} means this node has no ref.
     */
    int ref = 0;

    /**
     * True when this node's children were capped at {@link SwingSnapshotTool#MAX_DATA_CHILDREN}
     * during Phase 1 (build).
     */
    boolean truncated = false;

    /**
     * Number of accessible children that were NOT built due to the cap.
     * Valid only when {@link #truncated} is true.
     */
    int truncatedCount = 0;

    SnapshotNode(Accessible accessible) {
        this.accessible = accessible;
    }
}
