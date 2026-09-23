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

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * One node of the snapshot tree, mirroring one accessible. Each of the four passes runs over
 * every root before the next starts, and refs number across all roots:
 *
 * <pre>{@code
 * SnapshotNode root = SnapshotNode.build(window);
 * root.pruneChildren();
 * nextRef = root.assignRefs(nextRef, context);
 * root.render(0, sb);                  // or renderFiltered(filterLower, 0, sb)
 * }</pre>
 *
 * The passes are described in {@code design/architecture.md}; the text they emit is owned by
 * {@code design/snapshot-format.md}.
 *
 * @apiNote Rendering or filtering before {@link #assignRefs} throws: a node's line carries its
 *          ref, and is cached on first use.
 */
class SnapshotNode {

    private static final Logger LOG = Logger.getLogger(SnapshotNode.class.getName());

    /** Cap on the children built for a large data component: JTable rows, JList items, JTree nodes. */
    static final int MAX_DATA_ROW_NODES = 5;

    /**
     * Placeholder line emitted under an iconified Frame in place of its
     * suppressed children (D_iconified_children_hidden).
     */
    static final String ICONIFIED_PLACEHOLDER =
            "[Contents hidden — window is iconified. Call swing_restore to interact with this window.]";

    /** Cap on the rendered description, whichever source it came from (D_quoted_slot_sanitizing). */
    static final int MAX_DESCRIPTION_LENGTH = 120;

    /**
     * Cap on the inline {@code text="…"} preview, counted after quote escaping
     * (D_inline_value_preview) — the same cap as the mutation echo.
     */
    static final int PREVIEW_MAX_LENGTH = 15;

    /**
     * Characters read for the preview, wider than {@link #PREVIEW_MAX_LENGTH} so that
     * whitespace-heavy content still fills the preview after collapsing.
     */
    private static final int PREVIEW_RAW_READ = 64;

    // ── Roles that are always kept ──────────────────────────────────────

    private static final Set<AccessibleRole> SEMANTIC_ROLES;

    // ── AccessibleAction descriptions advertised as actions ─────────────

    private static final Map<String, String> STEP3_CONSTANTS = Map.of(
            AccessibleAction.INCREMENT, "increment",
            AccessibleAction.DECREMENT, "decrement",
            AccessibleAction.TOGGLE_EXPAND, "toggle_expand"
    );

    // ── Mutation availability gates ─────────────────────────────────
    // A failing gate prefixes the action with "!". The window actions (close, iconify,
    // restore) are absent on purpose: they work regardless of the enabled state.

    private static final Map<String, Predicate<Accessible>> MUTATION_AVAILABILITY = Map.of(
            "click",          SwingUtils::isEffectivelyEnabled,
            "toggle_popup",   SwingUtils::isEffectivelyEnabled,
            "increment",      SwingUtils::isEffectivelyEnabled,
            "decrement",      SwingUtils::isEffectivelyEnabled,
            "toggle_expand",  SwingUtils::isEffectivelyEnabled,
            "set_value",      SwingUtils::isEffectivelyEnabled,
            "set_text",       a -> SwingUtils.isEffectivelyEnabled(a)
                                   && SwingUtils.supportsSetText(a)
    );

    // ── States shown in the snapshot ──────────────────────────────────────────

    // disabled, read_only and iconified are synthesized in calculateSelfLine rather than
    // read from the state set.
    private static final List<AccessibleState> DISPLAYED_STATES = List.of(
            AccessibleState.FOCUSED,
            AccessibleState.SELECTED,
            AccessibleState.CHECKED,
            AccessibleState.EXPANDED,
            AccessibleState.COLLAPSED,
            AccessibleState.MODAL,
            AccessibleState.MULTI_LINE,
            AccessibleState.HORIZONTAL,
            AccessibleState.VERTICAL,
            AccessibleState.BUSY,
            AccessibleState.INDETERMINATE
    );

    static {
        // PANEL is absent: it is kept only when named or titled (isUnnamedPanel).
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
        roles.add(AccessibleRole.DESKTOP_PANE);
        roles.add(AccessibleRole.DESKTOP_ICON);
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
        // AccessibleRole has no TABLE_HEADER.
        roles.add(AccessibleRole.COLUMN_HEADER);
        roles.add(AccessibleRole.ROW_HEADER);
        roles.add(AccessibleRole.HEADER);
        roles.add(AccessibleRole.GROUP_BOX);
        SEMANTIC_ROLES = Collections.unmodifiableSet(roles);
    }

    // ── Instance fields ────────────────────────────────────────────────────────

    /** {@code null} only on a {@link JTableRowSnapshotNode}, which mirrors no accessible. */
    final @Nullable Accessible accessible;

    List<SnapshotNode> children = new ArrayList<>();

    /** {@code 0} for no ref; {@code null} until {@link #assignRefs} runs. */
    protected Integer ref = null;

    /** True when {@link #build} stopped at {@link #MAX_DATA_ROW_NODES} children. */
    boolean truncated = false;

    /** Children left unbuilt by the cap; meaningful only when {@link #truncated}. */
    int truncatedCount = 0;

    /** Cache behind {@link #actions()}; {@code null} until its first call. */
    private @Nullable List<String> actions;

    SnapshotNode(@Nullable Accessible accessible) {
        this.accessible = accessible;
    }

    /**
     * True for an iconified {@link Frame}, whose children the snapshot hides. Never for a
     * {@link JInternalFrame}: either its {@code JDesktopIcon} replaced it in the tree, or it was
     * iconified in place and its children are still on screen (R_iconified_windows).
     */
    boolean isIconifiedFrame() {
        return accessible instanceof Frame
                && (((Frame) accessible).getExtendedState() & Frame.ICONIFIED) != 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 1 — Build
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Mirrors the accessibility tree under {@code accessible}; a {@link JTable} becomes a
     * {@link JTableSnapshotNode} with one child per row.
     */
    static SnapshotNode build(Accessible accessible) {
        SnapshotNode node = (accessible instanceof JTable)
                ? new JTableSnapshotNode((JTable) accessible)
                : new SnapshotNode(accessible);
        node.buildChildren();
        return node;
    }

    /** Builds this node's children, stopping at {@link #MAX_DATA_ROW_NODES} for a large data component. */
    void buildChildren() {
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return;
        }

        // The icon's L&F button and label are rendering artifacts (D_desktop_icon_as_itself).
        if (accessible instanceof JInternalFrame.JDesktopIcon) {
            return;
        }

        int totalChildren = ctx.getAccessibleChildrenCount();

        boolean isLargeDataComponent = SwingUtils.isLargeDataComponent(accessible);

        int limit = isLargeDataComponent
                ? Math.min(totalChildren, MAX_DATA_ROW_NODES)
                : totalChildren;

        for (int i = 0; i < limit; i++) {
            Accessible child = ctx.getAccessibleChild(i);
            if (child != null) {
                children.add(build(child));
            }
        }

        if (isLargeDataComponent && totalChildren > MAX_DATA_ROW_NODES) {
            truncated = true;
            truncatedCount = totalChildren - MAX_DATA_ROW_NODES;
        }

        // Aqua nests the icons in a non-accessible Dock, hiding them from the walk above
        // (D_desktop_icon_as_itself). An icon with no parent was never added: its frame was
        // iconified in place (R_iconified_windows).
        if (accessible instanceof JDesktopPane) {
            Set<Accessible> alreadyFound = new HashSet<>(children.size());
            for (SnapshotNode child : children) {
                alreadyFound.add(child.accessible);
            }
            for (JInternalFrame f : ((JDesktopPane) accessible).getAllFrames()) {
                if (f.isIcon()) {
                    JInternalFrame.JDesktopIcon icon = f.getDesktopIcon();
                    if (icon != null && icon.getParent() != null && !alreadyFound.contains(icon)) {
                        children.add(build(icon));
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 2 — Prune
    // ══════════════════════════════════════════════════════════════════════════

    private enum PruneResult { KEEP, DROP, TRANSPARENT }

    /** Prunes the subtree below this node; the node itself is its parent's to judge. */
    void pruneChildren() {
        List<SnapshotNode> newChildren = new ArrayList<>();
        for (SnapshotNode child : children) {
            PruneResult decision = pruneDecision(child);
            if (decision == PruneResult.DROP) {
                continue;
            }
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

        if (!SwingUtils.isVisible(accessible)) {
            return PruneResult.DROP;
        }

        if (accessible instanceof CellRendererPane) {
            return PruneResult.DROP;
        }

        if (accessible instanceof JTableHeader) {
            return PruneResult.DROP;
        }

        // An open JMenu already exposes the same JMenuItem instances as its own children.
        if (accessible instanceof JPopupMenu
                && ((JPopupMenu) accessible).getInvoker() instanceof JMenu) {
            return PruneResult.DROP;
        }

        if (accessible instanceof Component) {
            Component comp = (Component) accessible;
            if (isGlassPane(comp)) {
                int accessibleChildCount = ctx != null ? ctx.getAccessibleChildrenCount() : 0;
                return accessibleChildCount == 0 ? PruneResult.DROP : PruneResult.TRANSPARENT;
            }
        }

        // ── Stage 3 safety net, checked first: it overrides Stage 2 ──────────
        if (mustKeep(node, ctx)) {
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

    /** Stage 3: a node with any of these is kept, whatever Stage 2 would say. */
    private static boolean mustKeep(SnapshotNode node, AccessibleContext ctx) {
        if (ctx == null) {
            return false;
        }
        Accessible accessible = node.accessible;
        AccessibleRole role = ctx.getAccessibleRole();

        if (role != null && SEMANTIC_ROLES.contains(role)) {
            return true;
        }

        if (role == AccessibleRole.PANEL && !isUnnamedPanel(accessible, ctx)) {
            return true;
        }

        String name = ctx.getAccessibleName();
        if (name != null && !name.isEmpty()) {
            return true;
        }

        if (node.hasAnyAction()) {
            return true;
        }

        AccessibleText at = ctx.getAccessibleText();
        if (at != null && at.getCharCount() > 0) {
            return true;
        }
        if (ctx.getAccessibleValue() != null) {
            return true;
        }

        AccessibleStateSet states = ctx.getAccessibleStateSet();
        if (states != null && states.contains(AccessibleState.FOCUSED)) {
            return true;
        }

        return false;
    }

    /** True for a pure layout panel: no accessible name, no description, no {@link TitledBorder}. */
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
     * Numbers every node with an action depth-first, from {@code nextRef}, and registers each in
     * {@code context}. An override must still set {@link #ref} on every node it covers.
     *
     * @return the next free ref
     */
    int assignRefs(int nextRef, SwingToolContext context) {
        if (hasAnyAction()) {
            ref = nextRef;
            context.putRef(nextRef, accessible);
            nextRef++;
        } else {
            ref = 0;
        }
        // D_iconified_children_hidden
        if (isIconifiedFrame()) {
            markChildRefsZero();
            return nextRef;
        }
        for (SnapshotNode child : children) {
            nextRef = child.assignRefs(nextRef, context);
        }
        return nextRef;
    }

    /**
     * Sets every descendant's {@link #ref} to {@code 0}, so that {@link #getSelfLine()} cannot
     * throw on a hidden child.
     */
    private void markChildRefsZero() {
        for (SnapshotNode child : children) {
            child.ref = 0;
            child.markChildRefsZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 4 — Render
    // ══════════════════════════════════════════════════════════════════════════

    /** The line's {@code <extra>} slot, e.g. a JTable's {@code columns: [ID, Name]}; {@code ""} for none. */
    String getAdditionalInfo() {
        return "";
    }

    /** The noun in {@code ... and N more items}. */
    String getTruncationLabel() {
        return "items";
    }

    /**
     * Computes the inline {@code text="…"} / {@code value=N} preview (D_inline_value_preview),
     * gated by the same {@link SwingUtils#supportsGetText} / {@link SwingUtils#supportsGetValue}
     * as the {@code get_text} / {@code get_value} actions. A custom widget passing both gates
     * gets both, text first.
     *
     * @return {@code ""} when neither gate passes; a read that throws drops only its own part
     */
    private String computeInlinePreview() {
        StringBuilder preview = new StringBuilder();

        if (SwingUtils.supportsGetText(accessible)) {
            try {
                String raw = SwingUtils.readText(accessible, PREVIEW_RAW_READ);
                // Capped after escaping, so the cap bounds the characters on screen.
                String sanitized = SwingUtils.sanitizeForQuotedSlot(raw);
                String truncated;
                if (sanitized == null) {
                    truncated = "";
                } else if (sanitized.length() <= PREVIEW_MAX_LENGTH) {
                    // A full raw read may have more text behind it.
                    if (raw.length() < PREVIEW_RAW_READ) {
                        truncated = sanitized;
                    } else {
                        truncated = sanitized + "…";
                    }
                } else {
                    // Never " …", and never a dangling backslash from a split \" escape.
                    String prefix = sanitized.substring(0, PREVIEW_MAX_LENGTH - 1).stripTrailing();
                    if (prefix.endsWith("\\")) {
                        prefix = prefix.substring(0, prefix.length() - 1).stripTrailing();
                    }
                    truncated = prefix + "…";
                }
                preview.append("text=\"").append(truncated).append('"');
            } catch (Exception e) {
                LOG.log(Level.FINE,
                        "Snapshot inline text preview failed for " + accessible.getClass(), e);
            }
        }

        if (SwingUtils.supportsGetValue(accessible)) {
            try {
                Number current = SwingUtils.readValue(accessible);
                Number serializedCurrent = SwingUtils.serializeNumber(current);
                AccessibleContext ac = accessible.getAccessibleContext();
                AccessibleRole role = ac != null ? ac.getAccessibleRole() : null;
                Number max = null;
                if (AccessibleRole.PROGRESS_BAR.equals(role) && ac != null) {
                    AccessibleValue av = ac.getAccessibleValue();
                    if (av != null) {
                        max = av.getMaximumAccessibleValue();
                    }
                }
                if (preview.length() > 0) {
                    preview.append(' ');
                }
                preview.append("value=").append(serializedCurrent);
                if (max != null) {
                    preview.append('/').append(SwingUtils.serializeNumber(max));
                }
            } catch (Exception e) {
                LOG.log(Level.FINE,
                        "Snapshot inline value preview failed for " + accessible.getClass(), e);
            }
        }

        return preview.toString();
    }

    /** Appends this subtree's lines to {@code sb}, indented two spaces per {@code depth}. */
    void render(int depth, StringBuilder sb) {
        renderSelfLine(depth, sb);

        // D_iconified_children_hidden
        if (isIconifiedFrame()) {
            sb.append("  ".repeat(depth + 1)).append("- ").append(ICONIFIED_PLACEHOLDER).append('\n');
            return;
        }

        for (SnapshotNode child : children) {
            child.render(depth + 1, sb);
        }

        renderTruncationSummary(depth, sb);
    }

    private @Nullable String selfLine = null;

    private void renderSelfLine(int depth, StringBuilder sb) {
        String indent = "  ".repeat(depth);
        sb.append(indent).append("- ");
        sb.append(getSelfLine());
    }

    final String getSelfLine() {
        if (selfLine == null) {
            Objects.requireNonNull(ref, "ref hasn't been calculated yet");
            selfLine = calculateSelfLine();
        }
        return selfLine;
    }

    protected String calculateSelfLine() {
        final StringBuilder sb = new StringBuilder();
        AccessibleContext ctx = accessible.getAccessibleContext();
        AccessibleRole role = ctx != null ? ctx.getAccessibleRole() : null;
        sb.append(ComponentClassResolver.resolveIdentitySlot(accessible));

        if (role == AccessibleRole.PAGE_TAB && ctx != null) {
            int tabIndex = ctx.getAccessibleIndexInParent();
            if (tabIndex >= 0) {
                sb.append(' ').append(tabIndex);
            }
        }

        // Uncapped: the name is identity (D_quoted_slot_sanitizing).
        String name = SwingUtils.sanitizeForQuotedSlot(
                SwingUtils.getEffectiveAccessibleName(accessible));
        if (name != null) {
            sb.append(" \"").append(name).append('"');
        }

        String desc = SwingUtils.resolveDescription(accessible);
        if (desc != null) {
            sb.append(" \"").append(capDescription(desc)).append('"');
        }

        List<String> bracketParts = new ArrayList<>();
        if (ref > 0) {
            bracketParts.add("ref=" + ref);
        }
        boolean effectivelyEnabled = SwingUtils.isEffectivelyEnabled(accessible);
        boolean readOnly = ctx != null && SwingUtils.hasEditableText(accessible)
                && ctx.getAccessibleStateSet() != null
                && !ctx.getAccessibleStateSet().contains(AccessibleState.EDITABLE);

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
        // The JDK never puts ICONIFIED in the state set (D_synthetic_iconified_state).
        if (isIconifiedFrame()
                || (accessible instanceof JInternalFrame && ((JInternalFrame) accessible).isIcon())) {
            bracketParts.add("iconified");
        }
        if (!bracketParts.isEmpty()) {
            sb.append(" [").append(String.join(", ", bracketParts)).append(']');
        }

        String additionalInfo = getAdditionalInfo();
        if (!additionalInfo.isEmpty()) {
            sb.append(' ').append(additionalInfo);
        }

        String preview = computeInlinePreview();
        if (!preview.isEmpty()) {
            sb.append(' ').append(preview);
        }

        List<String> acts = actions();
        if (!acts.isEmpty()) {
            List<String> prefixed = prefixUnavailable(acts, accessible);
            sb.append(" actions: ").append(String.join(", ", prefixed));
        }

        sb.append('\n');
        return sb.toString();
    }

    /** Appends the render-only {@code ... and N more items} line, if {@link #truncated}. */
    void renderTruncationSummary(int depth, StringBuilder sb) {
        if (truncated) {
            sb.append("  ".repeat(depth + 1))
              .append("... and ").append(truncatedCount).append(" more ").append(getTruncationLabel()).append('\n');
        }
    }

    // ── Tree filtering ────────────────────────────────────────────────

    /** True when this node's rendered line contains {@code filterLower}, which the caller lowercases. */
    boolean matchesFilter(String filterLower) {
        return getSelfLine().toLowerCase().contains(filterLower);
    }

    /**
     * True when this node or a descendant {@linkplain #matchesFilter matches}; an iconified
     * frame's children are never searched (D_iconified_children_hidden).
     */
    boolean subtreeMatchesFilter(String filterLower) {
        if (matchesFilter(filterLower)) {
            return true;
        }
        if (isIconifiedFrame()) {
            return false;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).subtreeMatchesFilter(filterLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders a matching node with its whole subtree, and a non-matching one as its own line
     * above its matching branches. An iconified frame renders as {@link #render} does, its
     * placeholder included, match or not (D_iconified_children_hidden). Call only where
     * {@link #subtreeMatchesFilter} or {@link #isIconifiedFrame} holds.
     */
    void renderFiltered(String filterLower, int depth, StringBuilder sb) {
        if (matchesFilter(filterLower) || isIconifiedFrame()) {
            render(depth, sb);
        } else {
            renderSelfLine(depth, sb);
            for (SnapshotNode child : children) {
                if (child.subtreeMatchesFilter(filterLower)) {
                    child.renderFiltered(filterLower, depth + 1, sb);
                }
            }
            // No truncation summary: it would count children the filter hid.
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Action detection
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns this node's action labels, in the order {@code design/snapshot-format.md} fixes,
     * without the {@code "!"} prefix.
     *
     * @implNote The one source for both {@link #hasAnyAction()} (the ref gate) and the
     * rendered {@code actions:} slot. An OR chain of {@code SwingUtils.supportsX} predicates
     * looks equivalent but drifts silently: under D_password_not_readable it advertised a ref
     * on a node that then rendered no action.
     */
    List<String> actions() {
        if (actions == null) {
            actions = computeActions();
        }
        return actions;
    }

    private List<String> computeActions() {
        Accessible accessible = this.accessible;
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return Collections.emptyList();
        }

        List<String> actions = new ArrayList<>();

        if (SwingUtils.supportsClick(accessible) != null) {
            actions.add("click");
        }

        if (SwingUtils.supportsTogglePopup(accessible) >= 0) {
            actions.add("toggle_popup");
        }

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

        // A read-only field still advertises set_text, rendered "!set_text".
        if (SwingUtils.supportsSetText(accessible)) {
            if (SwingUtils.supportsGetText(accessible)) actions.add("get_text");
            actions.add("set_text");
        } else if (SwingUtils.hasEditableText(accessible)) {
            if (SwingUtils.supportsGetText(accessible)) actions.add("get_text");
            actions.add("set_text");
        } else if (SwingUtils.supportsGetText(accessible)) {
            actions.add("get_text");
        }

        if (SwingUtils.supportsGetValue(accessible)) {
            actions.add("get_value");
            if (SwingUtils.supportsSetValue(accessible)) {
                actions.add("set_value");
            }
        }

        // Group labels, not callable actions.
        if (SwingUtils.supportsMultiSelection(accessible)) {
            actions.add("multi-selection");
        } else if (SwingUtils.supportsSingleSelection(accessible)) {
            actions.add("single-selection");
        }

        // Never on a JTable (D_no_jtable_cells).
        if (truncated && SwingUtils.isGetCellsSupported(accessible)) {
            actions.add("get_cell_count");
            actions.add("get_cells");
        }

        if (SwingUtils.supportsClose(accessible)) {
            actions.add("close");
        }

        if (SwingUtils.supportsIconify(accessible)) {
            actions.add("iconify");
        }

        if (SwingUtils.supportsRestore(accessible)) {
            actions.add("restore");
        }

        // Only when capDescription will bite. It may be a node's sole action — a
        // JLabel with a long tooltip — and so give it a ref it would not otherwise have.
        String desc = SwingUtils.resolveDescription(accessible);
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            actions.add("get_description");
        }

        return actions;
    }

    /** Prefixes {@code "!"} to each action whose {@link #MUTATION_AVAILABILITY} gate fails. */
    private static List<String> prefixUnavailable(List<String> actions,
                                                   Accessible accessible) {
        List<String> result = null; // lazy — most nodes have no unavailable actions
        for (int i = 0; i < actions.size(); i++) {
            String action = actions.get(i);
            Predicate<Accessible> gate = MUTATION_AVAILABILITY.get(action);
            if (gate != null && !gate.test(accessible)) {
                if (result == null) {
                    result = new ArrayList<>(actions.size());
                    result.addAll(actions.subList(0, i));
                }
                result.add("!" + action);
            } else if (result != null) {
                result.add(action);
            }
        }
        return result != null ? result : actions;
    }

    /** The ref gate. */
    boolean hasAnyAction() {
        return !actions().isEmpty();
    }

    /** @return {@code s}, or its first {@link #MAX_DESCRIPTION_LENGTH} characters plus {@code …} */
    static String capDescription(String s) {
        if (s.length() <= MAX_DESCRIPTION_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_DESCRIPTION_LENGTH) + "\u2026";
    }

    static String stripTrailingNewlines(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable-specific subclasses
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * A {@link JTable}: one {@link JTableRowSnapshotNode} per row rather than one node per cell
     * (D_no_jtable_cells), and {@code columns: […]} when the header is visible.
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
            int rowLimit = Math.min(totalRows, MAX_DATA_ROW_NODES);

            for (int row = 0; row < rowLimit; row++) {
                String rowText = SwingUtils.buildTableRowText(at, row, cols);
                children.add(new JTableRowSnapshotNode(row, rowText));
            }

            if (totalRows > MAX_DATA_ROW_NODES) {
                truncated = true;
                truncatedCount = totalRows - MAX_DATA_ROW_NODES;
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
     * One {@link JTable} row, rendered as {@code - row 0: 1 | Alice | NY}. It mirrors no
     * accessible, and has no children, actions or ref.
     */
    static final class JTableRowSnapshotNode extends SnapshotNode {

        private final int rowIndex;
        private final String rowText;

        JTableRowSnapshotNode(int rowIndex, String rowText) {
            super(null);
            this.rowIndex = rowIndex;
            this.rowText = rowText;
        }

        @Override
        int assignRefs(int nextRef, SwingToolContext context) {
            ref = 0;
            return nextRef;
        }

        @Override
        protected String calculateSelfLine() {
            return "row " + rowIndex + ": " + rowText + '\n';
        }
    }
}
