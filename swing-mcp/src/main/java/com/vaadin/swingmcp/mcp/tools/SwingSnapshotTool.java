package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

/**
 * MCP tool {@code swing_snapshot}: returns a compact accessibility-tree snapshot
 * of the Swing application so an AI agent can understand the UI structure and
 * identify components for interaction.
 *
 * <p>The snapshot pipeline has four phases:</p>
 * <ol>
 *   <li><b>Build</b> — mirror the accessibility tree into {@link SnapshotNode}s.</li>
 *   <li><b>Prune</b> — drop invisible/internal nodes; flatten transparent wrappers.</li>
 *   <li><b>AssignRefs</b> — number every action-bearing node 1…N.</li>
 *   <li><b>Render</b> — serialise to indented text.</li>
 * </ol>
 */
public class SwingSnapshotTool extends AbstractSwingTool {

    /** Maximum accessible children shown for JTable / JList / JTree. */
    public static final int MAX_DATA_CHILDREN = 5;

    // ── Roles that are always included (AI-1) ──────────────────────────────────

    private static final Set<AccessibleRole> SEMANTIC_ROLES;

    // ── Role → action-label fallback table (BR-06 Step 2) ─────────────────────

    private static final Map<AccessibleRole, List<String>> ROLE_ACTION_MAP;

    // ── Known AccessibleAction constant values (BR-06 Step 1) ─────────────────

    private static final Set<String> ACTION_CONSTANTS;

    // ── States shown in the snapshot ──────────────────────────────────────────

    // Note: AccessibleState has no DISABLED constant; disability is the absence of ENABLED.
    // "disabled" is injected manually in render when ENABLED is absent (see renderNode).
    private static final List<AccessibleState> DISPLAYED_STATES = List.of(
            AccessibleState.FOCUSED,
            AccessibleState.SELECTED,
            AccessibleState.CHECKED,
            AccessibleState.EDITABLE,
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

        // ── ROLE_ACTION_MAP ─────────────────────────────────────────────────────
        Map<AccessibleRole, List<String>> roleMap = new LinkedHashMap<>();
        roleMap.put(AccessibleRole.PUSH_BUTTON,   List.of("click"));
        roleMap.put(AccessibleRole.TOGGLE_BUTTON, List.of("click"));
        roleMap.put(AccessibleRole.CHECK_BOX,     List.of("click"));
        roleMap.put(AccessibleRole.RADIO_BUTTON,  List.of("click"));
        roleMap.put(AccessibleRole.MENU_ITEM,     List.of("click"));
        roleMap.put(AccessibleRole.MENU,          List.of("click"));
        roleMap.put(AccessibleRole.TEXT,          List.of("type"));
        roleMap.put(AccessibleRole.PASSWORD_TEXT, List.of("type"));
        roleMap.put(AccessibleRole.COMBO_BOX,     List.of("select"));
        roleMap.put(AccessibleRole.LIST,          List.of("select"));
        roleMap.put(AccessibleRole.PAGE_TAB,      List.of("click"));
        ROLE_ACTION_MAP = Collections.unmodifiableMap(roleMap);

        // ── ACTION_CONSTANTS ────────────────────────────────────────────────────
        Set<String> constants = new HashSet<>();
        for (Field field : AccessibleAction.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && field.getType() == String.class) {
                try {
                    String value = (String) field.get(null);
                    if (value != null) {
                        constants.add(value);
                    }
                } catch (IllegalAccessException e) {
                    // skip
                }
            }
        }
        ACTION_CONSTANTS = Collections.unmodifiableSet(constants);
    }

    // ── AbstractSwingTool ──────────────────────────────────────────────────────

    @Override
    public String getName() {
        return TOOL_SWING_SNAPSHOT;
    }

    @Override
    public String getDescription() {
        return "Returns an accessibility tree snapshot of the Swing application. "
                + "Use this to understand the current UI structure and identify "
                + "components for interaction via their numeric refs.";
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
    public MCPProtocol.Content execute(Map<String, Object> params,
                                       SwingToolContext context) throws Exception {
        context.clearRefMap();

        List<Component> components = context.getConsideredComponents();
        List<SnapshotNode> roots = new ArrayList<>();

        // Phase 1: build
        for (Component c : components) {
            if (c instanceof Accessible) {
                roots.add(buildNode((Accessible) c));
            }
        }

        // Phase 2: prune children of each root (roots themselves are always kept)
        for (SnapshotNode root : roots) {
            pruneChildren(root);
        }

        // Phase 3: assignRefs — globally sequenced across all roots
        int nextRef = 1;
        for (SnapshotNode root : roots) {
            nextRef = assignRefs(root, nextRef, context);
        }

        // Phase 4: render
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) {
                sb.append("---\n");
            }
            renderNode(roots.get(i), 0, sb);
        }

        // Strip trailing newline for a clean result
        String result = sb.toString();
        while (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return MCPProtocol.Content.text(result);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 1 — Build
    // ══════════════════════════════════════════════════════════════════════════

    private SnapshotNode buildNode(Accessible accessible) {
        SnapshotNode node = new SnapshotNode(accessible);
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return node;
        }

        AccessibleRole role = ctx.getAccessibleRole();
        int totalChildren = ctx.getAccessibleChildrenCount();

        boolean isLargeDataComponent = role == AccessibleRole.TABLE
                || role == AccessibleRole.LIST
                || role == AccessibleRole.TREE;

        int limit = isLargeDataComponent
                ? Math.min(totalChildren, MAX_DATA_CHILDREN)
                : totalChildren;

        for (int i = 0; i < limit; i++) {
            Accessible child = ctx.getAccessibleChild(i);
            if (child != null) {
                node.children.add(buildNode(child));
            }
        }

        if (isLargeDataComponent && totalChildren > MAX_DATA_CHILDREN) {
            node.truncated = true;
            node.truncatedCount = totalChildren - MAX_DATA_CHILDREN;
        }

        return node;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 2 — Prune
    // ══════════════════════════════════════════════════════════════════════════

    private enum PruneResult { KEEP, DROP, TRANSPARENT }

    /**
     * Recursively replaces {@code node.children} with the pruned list.
     * The node itself is not evaluated here — that is the responsibility of its parent.
     */
    private void pruneChildren(SnapshotNode node) {
        List<SnapshotNode> newChildren = new ArrayList<>();
        for (SnapshotNode child : node.children) {
            PruneResult decision = pruneDecision(child);
            if (decision == PruneResult.DROP) {
                continue;
            }
            // Recurse before promoting/keeping so grandchildren are also pruned.
            pruneChildren(child);
            if (decision == PruneResult.TRANSPARENT) {
                newChildren.addAll(child.children);
            } else { // KEEP
                newChildren.add(child);
            }
        }
        node.children = newChildren;
    }

    private PruneResult pruneDecision(SnapshotNode node) {
        Accessible accessible = node.accessible;
        AccessibleContext ctx = accessible.getAccessibleContext();

        // ── Stage 1: hard exclusions ──────────────────────────────────────────

        // HE-1: non-visible component
        if (accessible instanceof Component && !((Component) accessible).isVisible()) {
            return PruneResult.DROP;
        }

        // HE-2: CellRendererPane (rendering artifact)
        if (accessible instanceof CellRendererPane) {
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

    private boolean isGlassPane(Component comp) {
        Container parent = comp.getParent();
        return parent instanceof JRootPane && ((JRootPane) parent).getGlassPane() == comp;
    }

    /**
     * Stage 3 safety net: returns true if this node must always be kept.
     */
    private boolean mustKeep(Accessible accessible, AccessibleContext ctx) {
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

        // AI-3: has accessible action
        AccessibleAction aa = ctx.getAccessibleAction();
        if (aa != null && aa.getAccessibleActionCount() > 0) {
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
    private boolean isUnnamedPanel(Accessible accessible, AccessibleContext ctx) {
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

    private boolean hasTitledBorder(Accessible accessible) {
        if (accessible instanceof JComponent) {
            return containsTitledBorder(((JComponent) accessible).getBorder());
        }
        return false;
    }

    private boolean containsTitledBorder(Border border) {
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
     * at least one {@link AccessibleAction}, and stores the mapping in {@code context}.
     *
     * @param node    the node to process
     * @param nextRef the first ref value available for assignment
     * @param context the tool context that holds the ref → accessible map
     * @return the next free ref value after processing this subtree
     */
    private int assignRefs(SnapshotNode node, int nextRef, SwingToolContext context) {
        AccessibleContext ctx = node.accessible.getAccessibleContext();
        if (ctx != null) {
            AccessibleAction aa = ctx.getAccessibleAction();
            if (aa != null && aa.getAccessibleActionCount() > 0) {
                node.ref = nextRef;
                context.putRef(nextRef, node.accessible);
                nextRef++;
            }
        }
        for (SnapshotNode child : node.children) {
            nextRef = assignRefs(child, nextRef, context);
        }
        return nextRef;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 4 — Render
    // ══════════════════════════════════════════════════════════════════════════

    private void renderNode(SnapshotNode node, int depth, StringBuilder sb) {
        String indent = "  ".repeat(depth);
        sb.append(indent).append("- ");

        AccessibleContext ctx = node.accessible.getAccessibleContext();
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
        if (node.ref > 0) {
            bracketParts.add("ref=" + node.ref);
        }
        if (ctx != null) {
            AccessibleStateSet stateSet = ctx.getAccessibleStateSet();
            if (stateSet != null) {
                // "disabled" is represented by the ABSENCE of ENABLED (no DISABLED constant)
                if (!stateSet.contains(AccessibleState.ENABLED)) {
                    bracketParts.add("disabled");
                }
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

        // Actions
        List<String> actions = resolveActions(ctx);
        if (!actions.isEmpty()) {
            sb.append(" actions: ").append(String.join(", ", actions));
        }

        sb.append('\n');

        // Children
        for (SnapshotNode child : node.children) {
            renderNode(child, depth + 1, sb);
        }

        // Truncation summary (render-only, no node, no ref)
        if (node.truncated) {
            sb.append("  ".repeat(depth + 1))
              .append("... and ").append(node.truncatedCount).append(" more items\n");
        }
    }

    /**
     * Resolves the action labels to show for a node, using the two-step algorithm
     * from BR-06:
     * <ol>
     *   <li>If all {@link AccessibleAction} descriptions match known
     *       {@link AccessibleAction} constant values, use those.</li>
     *   <li>Otherwise fall back to the role-based table.</li>
     * </ol>
     */
    private List<String> resolveActions(AccessibleContext ctx) {
        if (ctx == null) {
            return Collections.emptyList();
        }
        AccessibleAction aa = ctx.getAccessibleAction();
        if (aa == null || aa.getAccessibleActionCount() == 0) {
            return Collections.emptyList();
        }

        // Step 1: check if all descriptions are known AccessibleAction constants
        int count = aa.getAccessibleActionCount();
        List<String> descriptions = new ArrayList<>(count);
        boolean allConstants = true;
        for (int i = 0; i < count; i++) {
            String d = aa.getAccessibleActionDescription(i);
            descriptions.add(d);
            if (d == null || !ACTION_CONSTANTS.contains(d)) {
                allConstants = false;
            }
        }
        if (allConstants) {
            return descriptions;
        }

        // Step 2: role-based fallback
        AccessibleRole role = ctx.getAccessibleRole();
        return ROLE_ACTION_MAP.getOrDefault(role, Collections.emptyList());
    }
}
