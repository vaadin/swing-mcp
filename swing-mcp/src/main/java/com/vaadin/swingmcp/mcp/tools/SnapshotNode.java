package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Internal tree node used during the four-phase snapshot pipeline.
 * Each node mirrors one node in the accessibility tree and holds its
 * pruned children, an optional numeric ref, and truncation metadata
 * for large-data components.
 *
 * <p>The pipeline phases are implemented as methods on this class:</p>
 * <ol>
 *   <li><b>{@link #build(Accessible)}</b> — mirror the accessibility tree into SnapshotNodes.</li>
 *   <li><b>{@link #pruneChildren()}</b> — drop invisible/internal nodes; flatten transparent wrappers.</li>
 *   <li><b>{@link #assignRefs(int, SwingToolContext)}</b> — number every action-bearing node.</li>
 *   <li><b>{@link #render(int, StringBuilder)}</b> — serialise to indented text.</li>
 * </ol>
 */
class SnapshotNode {

    /** Maximum accessible children shown for JTable / JList / JTree. */
    static final int MAX_DATA_CHILDREN = 5;

    // ── Roles that are always included (AI-1) ──────────────────────────────────

    private static final Set<AccessibleRole> SEMANTIC_ROLES;

    // ── Known AccessibleAction constants for Step 3 normalization (BR-06) ────

    private static final Map<String, String> STEP3_CONSTANTS = Map.of(
            AccessibleAction.INCREMENT, "increment",
            AccessibleAction.DECREMENT, "decrement",
            AccessibleAction.TOGGLE_EXPAND, "toggle_expand"
    );

    // ── Mutation actions that get "!" prefix when unavailable (BR-08) ────────

    private static final Set<String> MUTATION_ACTIONS = Set.of(
            "click", "toggle_popup", "increment", "decrement",
            "toggle_expand", "set_text", "set_value", "close"
    );

    // ── States shown in the snapshot ──────────────────────────────────────────

    // Note: AccessibleState has no DISABLED constant; disability is the absence of ENABLED.
    // "disabled" is injected manually in render when ENABLED is absent (see render).
    private static final List<AccessibleState> DISPLAYED_STATES = List.of(
            AccessibleState.FOCUSED,
            AccessibleState.SELECTED,
            AccessibleState.CHECKED,
            // EDITABLE is omitted — editable is the default for text fields.
            // Its absence is shown as "read_only" (synthetic, see render()).
            AccessibleState.EXPANDED,
            AccessibleState.COLLAPSED,
            AccessibleState.MODAL,
            AccessibleState.MULTI_LINE,
            AccessibleState.ICONIFIED,
            AccessibleState.HORIZONTAL,
            AccessibleState.VERTICAL,
            AccessibleState.BUSY,
            AccessibleState.INDETERMINATE
    );

    static {
        // ── SEMANTIC_ROLES ──────────────────────────────────────────────────────
        // Interactive and structural-semantic roles that are always kept (AI-1).
        // PANEL is handled separately (kept only when named/titled).
        Set<AccessibleRole> roles = new HashSet<>();
        // Interactive
        roles.add(AccessibleRole.PUSH_BUTTON);
        roles.add(AccessibleRole.TOGGLE_BUTTON);
        roles.add(AccessibleRole.CHECK_BOX);
        roles.add(AccessibleRole.RADIO_BUTTON);
        roles.add(AccessibleRole.TEXT);
        roles.add(AccessibleRole.PASSWORD_TEXT);
        roles.add(AccessibleRole.COMBO_BOX);
        roles.add(AccessibleRole.LIST);
        roles.add(AccessibleRole.TABLE);
        roles.add(AccessibleRole.TREE);
        roles.add(AccessibleRole.MENU_BAR);
        roles.add(AccessibleRole.MENU);
        roles.add(AccessibleRole.MENU_ITEM);
        roles.add(AccessibleRole.POPUP_MENU);
        roles.add(AccessibleRole.SLIDER);
        roles.add(AccessibleRole.SPIN_BOX);
        roles.add(AccessibleRole.PROGRESS_BAR);
        roles.add(AccessibleRole.SCROLL_BAR);
        roles.add(AccessibleRole.COLOR_CHOOSER);
        roles.add(AccessibleRole.FILE_CHOOSER);
        roles.add(AccessibleRole.DATE_EDITOR);
        // Structural-semantic
        roles.add(AccessibleRole.FRAME);
        roles.add(AccessibleRole.DIALOG);
        roles.add(AccessibleRole.INTERNAL_FRAME);
        roles.add(AccessibleRole.OPTION_PANE);
        roles.add(AccessibleRole.TOOL_BAR);
        roles.add(AccessibleRole.TOOL_TIP);
        roles.add(AccessibleRole.PAGE_TAB);
        roles.add(AccessibleRole.PAGE_TAB_LIST);
        roles.add(AccessibleRole.SPLIT_PANE);
        roles.add(AccessibleRole.SCROLL_PANE);
        roles.add(AccessibleRole.SEPARATOR);
        roles.add(AccessibleRole.LABEL);
        roles.add(AccessibleRole.STATUS_BAR);
        // TABLE_HEADER does not exist in Java 21; use COLUMN_HEADER, ROW_HEADER, HEADER
        roles.add(AccessibleRole.COLUMN_HEADER);
        roles.add(AccessibleRole.ROW_HEADER);
        roles.add(AccessibleRole.HEADER);
        roles.add(AccessibleRole.GROUP_BOX);
        SEMANTIC_ROLES = Collections.unmodifiableSet(roles);
    }

    // ── Instance fields ────────────────────────────────────────────────────────

    /**
     * The accessibility object this node mirrors. May be {@code null} for
     * virtual nodes that do not correspond to any real accessible (e.g.
     * {@link JTableRowSnapshotNode}).
     */
    final Accessible accessible;

    /** Mutable children list; replaced during Phase 2 (prune). */
    List<SnapshotNode> children = new ArrayList<>();

    /**
     * Numeric ref assigned during Phase 3 (assignRefs).
     * {@code 0} means this node has no ref.
     */
    int ref = 0;

    /**
     * True when this node's children were capped at {@link #MAX_DATA_CHILDREN}
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

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 1 — Build
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recursively builds a SnapshotNode tree from an accessibility tree root.
     * Large data components (JTable, JList, JTree) are truncated to
     * {@link #MAX_DATA_CHILDREN} children. JTable gets a specialised subclass
     * that renders rows instead of individual cells.
     */
    static SnapshotNode build(Accessible accessible) {
        SnapshotNode node = (accessible instanceof JTable)
                ? new JTableSnapshotNode((JTable) accessible)
                : new SnapshotNode(accessible);
        node.buildChildren();
        return node;
    }

    /**
     * Populates this node's children by walking the accessibility tree.
     * Large data components are truncated to {@link #MAX_DATA_CHILDREN} children.
     * Subclasses (e.g. {@link JTableSnapshotNode}) override this to provide
     * component-specific child construction.
     */
    void buildChildren() {
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return;
        }

        int totalChildren = ctx.getAccessibleChildrenCount();

        boolean isLargeDataComponent = SwingUtils.isLargeDataComponent(accessible);

        int limit = isLargeDataComponent
                ? Math.min(totalChildren, MAX_DATA_CHILDREN)
                : totalChildren;

        for (int i = 0; i < limit; i++) {
            Accessible child = ctx.getAccessibleChild(i);
            if (child != null) {
                children.add(build(child));
            }
        }

        if (isLargeDataComponent && totalChildren > MAX_DATA_CHILDREN) {
            truncated = true;
            truncatedCount = totalChildren - MAX_DATA_CHILDREN;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 2 — Prune
    // ══════════════════════════════════════════════════════════════════════════

    private enum PruneResult { KEEP, DROP, TRANSPARENT }

    /**
     * Recursively replaces {@code this.children} with the pruned list.
     * The node itself is not evaluated here — that is the responsibility of its parent.
     */
    void pruneChildren() {
        List<SnapshotNode> newChildren = new ArrayList<>();
        for (SnapshotNode child : children) {
            PruneResult decision = pruneDecision(child);
            if (decision == PruneResult.DROP) {
                continue;
            }
            // Recurse before promoting/keeping so grandchildren are also pruned.
            child.pruneChildren();
            if (decision == PruneResult.TRANSPARENT) {
                newChildren.addAll(child.children);
            } else { // KEEP
                newChildren.add(child);
            }
        }
        children = newChildren;
    }

    private static PruneResult pruneDecision(SnapshotNode node) {
        Accessible accessible = node.accessible;
        AccessibleContext ctx = accessible.getAccessibleContext();

        // ── Stage 1: hard exclusions ──────────────────────────────────────────

        // HE-1: non-visible component (invisible or zero-size)
        if (!SwingUtils.isVisible(accessible)) {
            return PruneResult.DROP;
        }

        // HE-2: CellRendererPane (rendering artifact)
        if (accessible instanceof CellRendererPane) {
            return PruneResult.DROP;
        }

        // HE-4: JTableHeader (SC-7) — column names are shown via columns: annotation on table node
        if (accessible instanceof JTableHeader) {
            return PruneResult.DROP;
        }

        // HE-3: glass pane of JRootPane
        if (accessible instanceof Component) {
            Component comp = (Component) accessible;
            if (isGlassPane(comp)) {
                // Empty glass pane → drop; non-empty → transparent (promote children)
                int accessibleChildCount = ctx != null ? ctx.getAccessibleChildrenCount() : 0;
                return accessibleChildCount == 0 ? PruneResult.DROP : PruneResult.TRANSPARENT;
            }
        }

        // ── Stage 3 safety net: always-included nodes override Stage 2 ────────
        if (mustKeep(accessible, ctx)) {
            return PruneResult.KEEP;
        }

        // ── Stage 2: transparent pruning ─────────────────────────────────────
        if (ctx != null) {
            AccessibleRole role = ctx.getAccessibleRole();
            if (role == AccessibleRole.ROOT_PANE
                    || role == AccessibleRole.LAYERED_PANE
                    || role == AccessibleRole.VIEWPORT
                    || role == AccessibleRole.FILLER) {
                return PruneResult.TRANSPARENT;
            }
            if (role == AccessibleRole.PANEL && isUnnamedPanel(accessible, ctx)) {
                return PruneResult.TRANSPARENT;
            }
        }

        return PruneResult.KEEP;
    }

    private static boolean isGlassPane(Component comp) {
        Container parent = comp.getParent();
        return parent instanceof JRootPane && ((JRootPane) parent).getGlassPane() == comp;
    }

    /**
     * Stage 3 safety net: returns true if this node must always be kept.
     */
    private static boolean mustKeep(Accessible accessible, AccessibleContext ctx) {
        if (ctx == null) {
            return false;
        }
        AccessibleRole role = ctx.getAccessibleRole();

        // AI-1: semantic (non-structural) role
        if (role != null && SEMANTIC_ROLES.contains(role)) {
            return true;
        }

        // AI-1: named panel (panel with accessible name, description, or TitledBorder)
        if (role == AccessibleRole.PANEL && !isUnnamedPanel(accessible, ctx)) {
            return true;
        }

        // AI-2: has accessible name
        String name = ctx.getAccessibleName();
        if (name != null && !name.isEmpty()) {
            return true;
        }

        // AI-3: has at least one action (BR-06 algorithm)
        if (hasAnyAction(accessible)) {
            return true;
        }

        // AI-4: has accessible text with content, or accessible value
        AccessibleText at = ctx.getAccessibleText();
        if (at != null && at.getCharCount() > 0) {
            return true;
        }
        if (ctx.getAccessibleValue() != null) {
            return true;
        }

        // AI-5: is focused
        AccessibleStateSet states = ctx.getAccessibleStateSet();
        if (states != null && states.contains(AccessibleState.FOCUSED)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true when the panel has no accessible name, no accessible description,
     * and no TitledBorder — i.e., it is a pure layout wrapper with no semantic meaning.
     */
    private static boolean isUnnamedPanel(Accessible accessible, AccessibleContext ctx) {
        String name = ctx.getAccessibleName();
        if (name != null && !name.isEmpty()) {
            return false;
        }
        String desc = ctx.getAccessibleDescription();
        if (desc != null && !desc.isEmpty()) {
            return false;
        }
        return !hasTitledBorder(accessible);
    }

    private static boolean hasTitledBorder(Accessible accessible) {
        if (accessible instanceof JComponent) {
            return containsTitledBorder(((JComponent) accessible).getBorder());
        }
        return false;
    }

    private static boolean containsTitledBorder(Border border) {
        if (border == null) {
            return false;
        }
        if (border instanceof TitledBorder) {
            return true;
        }
        if (border instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) border;
            return containsTitledBorder(compound.getOutsideBorder())
                    || containsTitledBorder(compound.getInsideBorder());
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 3 — AssignRefs
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Depth-first traversal; assigns the next integer ref to every node that exposes
     * at least one action under the BR-06 algorithm, and stores the mapping in
     * {@code context}.
     *
     * @param nextRef the first ref value available for assignment
     * @param context the tool context that holds the ref → accessible map
     * @return the next free ref value after processing this subtree
     */
    int assignRefs(int nextRef, SwingToolContext context) {
        if (hasAnyAction(accessible) || truncated) {
            ref = nextRef;
            context.putRef(nextRef, accessible);
            nextRef++;
        }
        for (SnapshotNode child : children) {
            nextRef = child.assignRefs(nextRef, context);
        }
        return nextRef;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 4 — Render
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns additional component-specific info to append after the name/description
     * on the node's rendered line (e.g. column headers for JTable).
     * Default returns empty string. Subclasses override to provide info.
     */
    String getAdditionalInfo() {
        return "";
    }

    /**
     * Returns the label used in truncation summaries (e.g. "items", "rows").
     * Default returns "items". Subclasses override for component-specific labels.
     */
    String getTruncationLabel() {
        return "items";
    }

    /**
     * Renders this node and its children as indented text lines.
     *
     * @param depth current indentation depth
     * @param sb    the target buffer
     */
    void render(int depth, StringBuilder sb) {
        String indent = "  ".repeat(depth);
        sb.append(indent).append("- ");

        AccessibleContext ctx = accessible.getAccessibleContext();
        AccessibleRole role = ctx != null ? ctx.getAccessibleRole() : null;
        sb.append(role != null ? AccessibleNames.roleName(role) : "unknown");

        // Name and description (omit if blank)
        String name = ctx != null ? ctx.getAccessibleName() : null;
        String desc = ctx != null ? ctx.getAccessibleDescription() : null;
        if (name != null && !name.isEmpty()) {
            sb.append(" \"").append(name).append('"');
        }
        if (desc != null && !desc.isEmpty()) {
            sb.append(" \"").append(desc).append('"');
        }

        // Bracket: [ref=N, state1, state2, ...]
        List<String> bracketParts = new ArrayList<>();
        if (ref > 0) {
            bracketParts.add("ref=" + ref);
        }
        // Compute enabled/read-only state once for both bracket and action prefixing
        boolean effectivelyEnabled = SwingUtils.isEffectivelyEnabled(accessible);
        boolean readOnly = ctx != null && SwingUtils.hasEditableText(accessible)
                && ctx.getAccessibleStateSet() != null
                && !ctx.getAccessibleStateSet().contains(AccessibleState.EDITABLE);

        // "disabled" uses isEffectivelyEnabled() — walks the parent chain (BR-08/SC-4)
        if (!effectivelyEnabled) {
            bracketParts.add("disabled");
        }
        if (readOnly) {
            bracketParts.add("read_only");
        }
        if (ctx != null) {
            AccessibleStateSet stateSet = ctx.getAccessibleStateSet();
            if (stateSet != null) {
                for (AccessibleState state : DISPLAYED_STATES) {
                    if (stateSet.contains(state)) {
                        bracketParts.add(AccessibleNames.stateName(state));
                    }
                }
            }
        }
        if (!bracketParts.isEmpty()) {
            sb.append(" [").append(String.join(", ", bracketParts)).append(']');
        }

        // Additional component-specific info (e.g. column headers for JTable)
        String additionalInfo = getAdditionalInfo();
        if (!additionalInfo.isEmpty()) {
            sb.append(' ').append(additionalInfo);
        }

        // Actions (BR-06) with "!" prefix for unavailable mutations (BR-08)
        List<String> actions = resolveActions();
        if (!actions.isEmpty()) {
            List<String> prefixed = prefixUnavailable(actions, effectivelyEnabled, readOnly);
            sb.append(" actions: ").append(String.join(", ", prefixed));
        }

        sb.append('\n');

        // Children
        for (SnapshotNode child : children) {
            child.render(depth + 1, sb);
        }

        // Truncation summary (render-only, no node, no ref)
        if (truncated) {
            sb.append("  ".repeat(depth + 1))
              .append("... and ").append(truncatedCount).append(" more ").append(getTruncationLabel()).append('\n');
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Action detection (BR-06 / BR-07)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves the action labels to show for this node, using the six-step
     * Action Label Algorithm from BR-06.
     */
    private List<String> resolveActions() {
        Accessible accessible = this.accessible;
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return Collections.emptyList();
        }

        List<String> actions = new ArrayList<>();

        // Step 1: click (Tier 1: AccessibleAction, Tier 2: MouseListener fallback)
        if (SwingUtils.supportsClick(accessible) != null) {
            actions.add("click");
        }

        // Step 2: toggle_popup
        if (SwingUtils.supportsTogglePopup(accessible) >= 0) {
            actions.add("toggle_popup");
        }

        // Step 3: known AccessibleAction constants (INCREMENT, DECREMENT, TOGGLE_EXPAND)
        AccessibleAction aa = ctx.getAccessibleAction();
        if (aa != null) {
            for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
                String desc = aa.getAccessibleActionDescription(i);
                String displayName = STEP3_CONSTANTS.get(desc);
                if (displayName != null) {
                    actions.add(displayName);
                }
            }
        }

        // Step 4: text
        // Read-only text fields (hasEditableText but not EDITABLE) now emit set_text too;
        // BR-08 will prefix it with "!" since the component is read-only.
        if (SwingUtils.supportsSetText(accessible)) {
            actions.add("get_text");
            actions.add("set_text");
        } else if (SwingUtils.hasEditableText(accessible)) {
            // Read-only text field: has AccessibleEditableText but lacks EDITABLE state
            actions.add("get_text");
            actions.add("set_text");
        } else if (SwingUtils.supportsGetText(accessible)) {
            actions.add("get_text");
        }

        // Step 5: value
        if (SwingUtils.supportsGetValue(accessible)) {
            actions.add("get_value");
            if (SwingUtils.supportsSetValue(accessible)) {
                actions.add("set_value");
            }
        }

        // Step 6: selection group labels
        if (SwingUtils.supportsMultiSelection(accessible)) {
            actions.add("multi-selection");
        } else if (SwingUtils.supportsSingleSelection(accessible)) {
            actions.add("single-selection");
        }

        // Step 6b: content discovery for truncated large data components
        if (truncated) {
            actions.add("get_cell_count");
            actions.add("get_cells");
        }

        // Step 7: close (synthetic, for windows only)
        if (SwingUtils.supportsClose(accessible)) {
            actions.add("close");
        }

        return actions;
    }

    /**
     * Prefixes mutation actions with "!" when they would fail validation (BR-08).
     * A mutation action is unavailable when:
     * (a) the component is not effectively enabled, or
     * (b) the action is "set_text" and the component is read-only.
     */
    private static List<String> prefixUnavailable(List<String> actions,
                                                   boolean effectivelyEnabled,
                                                   boolean readOnly) {
        if (effectivelyEnabled && !readOnly) {
            return actions; // fast path: nothing to prefix
        }
        List<String> result = new ArrayList<>(actions.size());
        for (String action : actions) {
            if (MUTATION_ACTIONS.contains(action)) {
                // All mutations blocked when disabled; only set_text blocked when read-only
                if (!effectivelyEnabled || (readOnly && "set_text".equals(action))) {
                    result.add("!" + action);
                } else {
                    result.add(action);
                }
            } else {
                result.add(action);
            }
        }
        return result;
    }

    /**
     * Returns true if the accessible has at least one action under the BR-06 algorithm
     * (BR-07 ref-assignment gate).
     */
    static boolean hasAnyAction(Accessible accessible) {
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) return false;
        return SwingUtils.supportsClick(accessible) != null
                || SwingUtils.supportsTogglePopup(accessible) >= 0
                || hasKnownActionConstant(ctx)
                || SwingUtils.supportsGetText(accessible)
                || SwingUtils.supportsGetValue(accessible)
                || SwingUtils.supportsSelection(accessible)
                || SwingUtils.supportsClose(accessible);
    }

    private static boolean hasKnownActionConstant(AccessibleContext ctx) {
        AccessibleAction aa = ctx.getAccessibleAction();
        if (aa == null) return false;
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            if (STEP3_CONSTANTS.containsKey(aa.getAccessibleActionDescription(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips all trailing newline characters from the given string.
     */
    static String stripTrailingNewlines(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable-specific subclasses (SC-6)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Snapshot node for a JTable. Overrides child construction to create
     * {@link JTableRowSnapshotNode}s (one per row, capped at {@link #MAX_DATA_CHILDREN}),
     * and provides column header info and row-based truncation labels.
     */
    static final class JTableSnapshotNode extends SnapshotNode {

        private final JTable table;

        JTableSnapshotNode(JTable table) {
            super(table);
            this.table = table;
        }

        @Override
        void buildChildren() {
            AccessibleContext ctx = table.getAccessibleContext();
            if (ctx == null) return;
            AccessibleTable at = ctx.getAccessibleTable();
            if (at == null) return;

            int totalRows = at.getAccessibleRowCount();
            int cols = at.getAccessibleColumnCount();
            int rowLimit = Math.min(totalRows, MAX_DATA_CHILDREN);

            for (int row = 0; row < rowLimit; row++) {
                String rowText = SwingUtils.buildTableRowText(at, row, cols);
                children.add(new JTableRowSnapshotNode(row, rowText));
            }

            if (totalRows > MAX_DATA_CHILDREN) {
                truncated = true;
                truncatedCount = totalRows - MAX_DATA_CHILDREN;
            }
        }

        /** Row children are synthetic — never pruned. */
        @Override
        void pruneChildren() { }

        @Override
        String getAdditionalInfo() {
            if (!SwingUtils.isTableHeaderVisible(table)) return "";
            List<String> names = SwingUtils.getTableColumnNames(table);
            return "columns: [" + String.join(", ", names) + "]";
        }

        @Override
        String getTruncationLabel() {
            return "rows";
        }
    }

    /**
     * Virtual snapshot node for a single JTable row. Stores the 0-based row
     * index and pre-built pipe-separated row text. Has no children, no actions,
     * no ref, and no backing accessible ({@code accessible} is {@code null}).
     * Render produces {@code - row 0: Val1 | Val2 | Val3}.
     */
    static final class JTableRowSnapshotNode extends SnapshotNode {

        private final int rowIndex;
        private final String rowText;

        JTableRowSnapshotNode(int rowIndex, String rowText) {
            super(null);
            this.rowIndex = rowIndex;
            this.rowText = rowText;
        }

        /** No actions, no children — nothing to assign. */
        @Override
        int assignRefs(int nextRef, SwingToolContext context) {
            return nextRef;
        }

        @Override
        void render(int depth, StringBuilder sb) {
            sb.append("  ".repeat(depth))
              .append("- row ").append(rowIndex).append(": ").append(rowText)
              .append('\n');
        }
    }
}
