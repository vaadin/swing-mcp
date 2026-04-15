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
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOG = Logger.getLogger(SnapshotNode.class.getName());

    /** Maximum child SnapshotNodes for large data components (JTable rows, JList items, JTree nodes). */
    static final int MAX_DATA_ROW_NODES = 5;

    /**
     * Maximum length of a rendered description (BR-10). Strings longer than
     * this are truncated to this many characters and a trailing U+2026
     * appended. Applies symmetrically to real {@code accessibleDescription}
     * values and tooltip-fallback values so the AI cannot distinguish the
     * two sources from the rendered output.
     */
    static final int MAX_DESCRIPTION_LENGTH = 120;

    /**
     * Maximum length of an inline {@code text="..."} preview (BR-12 / DR-013).
     * Strings of this length or less are emitted in full; longer strings are
     * truncated to the first {@code PREVIEW_MAX_LENGTH - 1} characters plus a
     * trailing U+2026. Matches DR-010's value-render convention for
     * consistency across mutation echoes and snapshot previews.
     */
    static final int PREVIEW_MAX_LENGTH = 15;

    /**
     * Raw read budget for the preview read (BR-12). Wider than
     * {@link #PREVIEW_MAX_LENGTH} so that whitespace-heavy content still
     * produces a meaningful preview after collapsing. A pathological case
     * where all {@value #PREVIEW_RAW_READ} raw chars collapse to ≤
     * {@link #PREVIEW_MAX_LENGTH} is signalled by an appended ellipsis.
     */
    private static final int PREVIEW_RAW_READ = 64;

    // ── Roles that are always included (AI-1) ──────────────────────────────────

    private static final Set<AccessibleRole> SEMANTIC_ROLES;

    // ── Known AccessibleAction constants for Step 3 normalization (BR-06) ────

    private static final Map<String, String> STEP3_CONSTANTS = Map.of(
            AccessibleAction.INCREMENT, "increment",
            AccessibleAction.DECREMENT, "decrement",
            AccessibleAction.TOGGLE_EXPAND, "toggle_expand"
    );

    // ── Mutation availability gates (BR-08) ─────────────────────────────────
    // Maps mutation action names to predicates that check whether the action
    // can succeed right now.  Actions absent from this map are never "!"-prefixed.
    // Window-level actions (close, iconify) are intentionally absent — they work
    // regardless of the component's enabled state.

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
            // ICONIFIED is synthetic (DR-009) — derived from Frame.getExtendedState(),
            // not from AccessibleStateSet (JDK never sets it). See below.
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
     * {@code 0} means this node has no ref. null means ref hasn't been calculated yet.
     */
    protected Integer ref = null;

    /**
     * True when this node's children were capped at {@link #MAX_DATA_ROW_NODES}
     * during Phase 1 (build).
     */
    boolean truncated = false;

    /**
     * Number of accessible children that were NOT built due to the cap.
     * Valid only when {@link #truncated} is true.
     */
    int truncatedCount = 0;

    /**
     * Lazily-computed cached action list for this node (BR-06 Action Label
     * Algorithm output, without the BR-08 {@code "!"} prefix). {@code null}
     * until {@link #actions()} is first called. Cached so {@link #hasAnyAction()}
     * and {@link #render} share a single walk of the BR-06 pipeline per node.
     */
    private List<String> actions;

    SnapshotNode(Accessible accessible) {
        this.accessible = accessible;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Phase 1 — Build
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recursively builds a SnapshotNode tree from an accessibility tree root.
     * Large data components (JTable, JList, JTree) are truncated to
     * {@link #MAX_DATA_ROW_NODES} children. JTable gets a specialised subclass
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
     * Large data components are truncated to {@link #MAX_DATA_ROW_NODES} children.
     * Subclasses (e.g. {@link JTableSnapshotNode}) override this to provide
     * component-specific child construction.
     */
    void buildChildren() {
        AccessibleContext ctx = accessible.getAccessibleContext();
        if (ctx == null) {
            return;
        }

        // SC-5(c): JDesktopIcon's children (L&F button + label) are rendering
        // artifacts, not semantic content. Skip child-walking entirely.
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

        // Some L&Fs (macOS Aqua) nest JDesktopIcon inside a non-accessible
        // wrapper (Dock), so iconified internal frames don't appear in the
        // accessibility tree.  Supplement with JDesktopPane's own API.
        if (accessible instanceof JDesktopPane) {
            Set<Accessible> alreadyFound = new HashSet<>(children.size());
            for (SnapshotNode child : children) {
                alreadyFound.add(child.accessible);
            }
            for (JInternalFrame f : ((JDesktopPane) accessible).getAllFrames()) {
                if (f.isIcon()) {
                    JInternalFrame.JDesktopIcon icon = f.getDesktopIcon();
                    if (icon != null && !alreadyFound.contains(icon)) {
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

        // HE-5: JPopupMenu belonging to an open JMenu. The JMenu already exposes
        // the same JMenuItem instances as its accessible children (via its
        // internal popupMenu), so the popup would duplicate every entry once
        // the menu is open. Right-click / context popups — whose invoker is not
        // a JMenu — are kept as usual.
        if (accessible instanceof JPopupMenu
                && ((JPopupMenu) accessible).getInvoker() instanceof JMenu) {
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

    /**
     * Stage 3 safety net: returns true if this node must always be kept.
     */
    private static boolean mustKeep(SnapshotNode node, AccessibleContext ctx) {
        if (ctx == null) {
            return false;
        }
        Accessible accessible = node.accessible;
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
        if (node.hasAnyAction()) {
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
     * <br/>
     * The function must assign a value to {@link #ref}.
     *
     * @param nextRef the first ref value available for assignment
     * @param context the tool context that holds the ref → accessible map
     * @return the next free ref value after processing this subtree
     */
    int assignRefs(int nextRef, SwingToolContext context) {
        // Dropped the historical `|| truncated` tail: every truncated container
        // now has at least one action via the action list (LIST/TREE emit
        // get_cell_count/get_cells in step 6b; TABLE always has a selection
        // group label), so truncation is already covered by hasAnyAction().
        if (hasAnyAction()) {
            ref = nextRef;
            context.putRef(nextRef, accessible);
            nextRef++;
        } else {
            ref = 0;
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
     * Computes the inline {@code text="..."} / {@code value=N} preview for
     * this node's rendered line, per BR-12 / DR-013.
     *
     * <p>Gate parity with BR-06: emits {@code text="..."} iff
     * {@link SwingUtils#supportsGetText} returns {@code true}, and
     * {@code value=N} iff {@link SwingUtils#supportsGetValue} returns
     * {@code true}. In standard Swing these are disjoint, so at most one of
     * the two labels appears; a custom widget exposing both is permitted to
     * emit both (text first, then value).
     *
     * <p>Defensive read: every accessibility-API call is wrapped in try/catch.
     * If a custom widget's {@code AccessibleText}/{@code AccessibleValue}
     * throws (document lock contention, misbehaving custom impl, etc.), the
     * affected annotation is omitted and a {@code FINE} log line records the
     * failure — the rest of the snapshot still renders.
     *
     * @return the preview string (without leading/trailing spaces) or
     *         {@code ""} if neither gate fires
     */
    private String computeInlinePreview() {
        StringBuilder preview = new StringBuilder();

        // text="..." — gated by supportsGetText (DR-011 password exclusion
        // and DR-015 label exclusion are already baked into supportsGetText,
        // so password fields and JLabels produce no annotation for free).
        if (SwingUtils.supportsGetText(accessible)) {
            try {
                String raw = SwingUtils.readText(accessible, PREVIEW_RAW_READ);
                // Sanitise per BR-13 / DR-014: collapse whitespace runs to
                // single spaces, strip leading/trailing whitespace, escape
                // embedded quotes. Null-or-blank result → no preview.
                String sanitized = SwingUtils.sanitizeForQuotedSlot(raw);
                // Cap to PREVIEW_MAX_LENGTH chars (DR-013 convention). The
                // cap counts rendered chars including the backslash in an
                // escaped quote — a field containing a single '"' produces
                // the two-char sequence `\"` in the preview; we cap on the
                // rendered length, not the pre-escape length, so the 15-char
                // budget is an upper bound on on-screen characters.
                String truncated;
                if (sanitized == null) {
                    truncated = "";
                } else if (sanitized.length() <= PREVIEW_MAX_LENGTH) {
                    // Did we read everything? If raw hit the raw-read budget
                    // there may be more content that collapsed away; mark
                    // truncation so the AI knows to call swing_get_text.
                    if (raw.length() < PREVIEW_RAW_READ) {
                        truncated = sanitized;
                    } else {
                        truncated = sanitized + "…";
                    }
                } else {
                    // Strip trailing whitespace inside the cap window so the
                    // preview does not render " …" (space + ellipsis) when the
                    // 14-char prefix happens to end in whitespace. Also avoid
                    // splitting an escape sequence: if the cap would land
                    // between `\` and `"`, drop the dangling backslash so the
                    // preview never ends in a half-finished escape.
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

        // value=N — gated by supportsGetValue. Progress bars with a non-null
        // maximum render as value=current/max (BR-12 progress-bar exception).
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

    /**
     * Renders this node and its children as indented text lines.
     *
     * @param depth current indentation depth
     * @param sb    the target buffer
     */
    void render(int depth, StringBuilder sb) {
        renderSelfLine(depth, sb);

        // Children
        for (SnapshotNode child : children) {
            child.render(depth + 1, sb);
        }

        // Truncation summary (render-only, no node, no ref)
        renderTruncationSummary(depth, sb);
    }

    private String selfLine = null;

    /**
     * Renders only this node's own line (no children, no truncation summary).
     */
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
        // UC-002 BR-11: component identity slot is "JClass (role)",
        // "Concrete -> JClass (role)", or "(role)" for non-Component accessibles.
        sb.append(ComponentClassResolver.resolveIdentitySlot(accessible));

        // UC-002 SC-2: emit the 0-based tab index inline for JTabbedPane pages,
        // so an AI client can pass it straight to swing_set_selection as [N]
        // without a separate enumeration call (UC-015). Mirrors the JTable
        // row rendering pattern (- row N: …).
        if (role == AccessibleRole.PAGE_TAB && ctx != null) {
            int tabIndex = ctx.getAccessibleIndexInParent();
            if (tabIndex >= 0) {
                sb.append(' ').append(tabIndex);
            }
        }

        // Name (omit if blank) — uses getEffectiveAccessibleName for
        // JInternalFrame (accessible name → title) and JDesktopIcon
        // (icon name → frame name → frame title). See UC-002 SC-5.
        // Sanitised per BR-13 / DR-014: whitespace collapsed, embedded
        // quotes escaped. Uncapped — name is identity (DR-014 §2).
        String name = SwingUtils.sanitizeForQuotedSlot(
                SwingUtils.getEffectiveAccessibleName(accessible));
        if (name != null) {
            sb.append(" \"").append(name).append('"');
        }

        // Description (BR-10): real accessibleDescription if non-blank,
        // else the tooltip via getTooltipAsText (covers JTabbedPane per-tab
        // tooltips and other cases the JDK's auto-fallback misses). HTML
        // cleanup is applied unconditionally because
        // JComponent.AccessibleJComponent.getAccessibleDescription() already
        // auto-falls-back to getToolTipText() inside the JDK — so an
        // "explicit-looking" description may actually be a (potentially HTML)
        // tooltip. Sanitised per BR-13 / DR-014 (covers non-HTML descriptions
        // with embedded newlines/quotes that htmlToPlainText passes through).
        // Result is capped at MAX_DESCRIPTION_LENGTH chars symmetrically
        // across all sources (DR-014 §3 — description stays capped).
        String desc = ctx != null
                ? SwingUtils.htmlToPlainText(ctx.getAccessibleDescription())
                : null;
        if (desc == null) {
            desc = SwingUtils.getTooltipAsText(accessible);
        }
        desc = SwingUtils.sanitizeForQuotedSlot(desc);
        if (desc != null) {
            sb.append(" \"").append(capDescription(desc)).append('"');
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
        // Synthetic ICONIFIED (DR-009): JDK never sets it in AccessibleStateSet.
        // Derived from Frame.getExtendedState() for JFrame.
        if (accessible instanceof Frame
                && (((Frame) accessible).getExtendedState() & Frame.ICONIFIED) != 0) {
            bracketParts.add("iconified");
        }
        if (!bracketParts.isEmpty()) {
            sb.append(" [").append(String.join(", ", bracketParts)).append(']');
        }

        // Additional component-specific info (e.g. column headers for JTable)
        String additionalInfo = getAdditionalInfo();
        if (!additionalInfo.isEmpty()) {
            sb.append(' ').append(additionalInfo);
        }

        // Inline value preview (BR-12 / DR-013). Emitted after additionalInfo
        // so that on the (spec-permitted but never-actually-seen) co-occurrence
        // case `columns:` appears first, then `text=`/`value=`.
        String preview = computeInlinePreview();
        if (!preview.isEmpty()) {
            sb.append(' ').append(preview);
        }

        // Actions (BR-06) with "!" prefix for unavailable mutations (BR-08)
        List<String> acts = actions();
        if (!acts.isEmpty()) {
            List<String> prefixed = prefixUnavailable(acts, accessible);
            sb.append(" actions: ").append(String.join(", ", prefixed));
        }

        sb.append('\n');
        return sb.toString();
    }

    /**
     * Renders the truncation summary line if this node was truncated.
     */
    void renderTruncationSummary(int depth, StringBuilder sb) {
        if (truncated) {
            sb.append("  ".repeat(depth + 1))
              .append("... and ").append(truncatedCount).append(" more ").append(getTruncationLabel()).append('\n');
        }
    }

    // ── Tree filtering (BR-09) ────────────────────────────────────────────────

    /**
     * Returns whether this node's rendered self-line contains the given
     * filter substring (case-insensitive). Used by the tree filter algorithm.
     */
    boolean matchesFilter(String filterLower) {
        return getSelfLine().toLowerCase().contains(filterLower);
    }

    /**
     * Checks whether this node or any descendant matches the filter.
     * Returns {@code true} if this node should be included in filtered output.
     */
    boolean subtreeMatchesFilter(String filterLower) {
        if (matchesFilter(filterLower)) {
            return true;
        }
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).subtreeMatchesFilter(filterLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders this node's filtered subtree. If this node directly matches
     * the filter, it and all descendants are rendered unconditionally.
     * Otherwise, only the self-line is rendered (as an ancestor providing
     * context) and filtering continues into children.
     *
     * @param filterLower the lowercase filter substring
     * @param depth       current indentation depth
     * @param sb          the target buffer
     */
    void renderFiltered(String filterLower, int depth, StringBuilder sb) {
        if (matchesFilter(filterLower)) {
            // Direct match — render this node and ALL descendants unconditionally
            render(depth, sb);
        } else {
            // Ancestor of a match — render self-line, recurse only into matching branches
            renderSelfLine(depth, sb);
            for (SnapshotNode child : children) {
                if (child.subtreeMatchesFilter(filterLower)) {
                    child.renderFiltered(filterLower, depth + 1, sb);
                }
            }
            // Don't render truncation summary for ancestor-only nodes —
            // the truncated children are not part of the filtered output
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Action detection (BR-06 / BR-07)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns this node's action labels (BR-06 Action Label Algorithm output,
     * without the BR-08 {@code "!"} prefix). Lazily computed on first call and
     * cached for reuse — see {@link #actions}.
     *
     * <p>Serves as the single source of truth for {@link #hasAnyAction()}
     * (ref-assignment gate) and {@link #render} (snapshot output). Previously
     * the two were kept in sync by hand — an OR chain of {@code SwingUtils.supportsX}
     * predicates mirroring the BR-06 steps — which drifted under DR-011 and had
     * to be patched. Caching the list collapses both into one walk.
     */
    List<String> actions() {
        if (actions == null) {
            actions = computeActions();
        }
        return actions;
    }

    /**
     * Computes the action labels for this node using the six-step Action Label
     * Algorithm from BR-06. Called exactly once per node via {@link #actions()}.
     */
    private List<String> computeActions() {
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

        // Step 4: text.
        // get_text and set_text are independent capabilities: each branch
        // gates its action on the matching support predicate. SwingUtils.supportsGetText
        // returns false for components where reading yields garbage (DR-011
        // password fields), so step 4 does not need an explicit password check.
        // Read-only text fields (hasEditableText but not EDITABLE) emit set_text
        // too; BR-08 prefixes with "!" since the component is read-only.
        if (SwingUtils.supportsSetText(accessible)) {
            if (SwingUtils.supportsGetText(accessible)) actions.add("get_text");
            actions.add("set_text");
        } else if (SwingUtils.hasEditableText(accessible)) {
            // Read-only text field: has AccessibleEditableText but lacks EDITABLE state
            if (SwingUtils.supportsGetText(accessible)) actions.add("get_text");
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

        // Step 6b: content discovery for truncated large data components.
        // JTable is excluded — its cells are stamp-painted plain text labels
        // with no actionable children; use swing_get_items instead
        // (UC-002 step 6b, UC-020 BR-03).
        if (truncated && SwingUtils.isGetCellsSupported(accessible)) {
            actions.add("get_cell_count");
            actions.add("get_cells");
        }

        // Step 7: close (synthetic, for windows only)
        if (SwingUtils.supportsClose(accessible)) {
            actions.add("close");
        }

        // Step 8: iconify (synthetic, for frames only)
        if (SwingUtils.supportsIconify(accessible)) {
            actions.add("iconify");
        }

        // Step 9: restore (synthetic, for iconified frames and JDesktopIcon)
        if (SwingUtils.supportsRestore(accessible)) {
            actions.add("restore");
        }

        return actions;
    }

    /**
     * Prefixes mutation actions with "!" when they would fail validation (BR-08).
     * Each action in {@link #MUTATION_AVAILABILITY} has its own predicate that
     * determines availability.  Actions absent from the map (read-only tools,
     * window-level actions like close/iconify) are never prefixed.
     */
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

    /**
     * Returns {@code true} if this node has at least one action under the BR-06
     * algorithm (BR-07 ref-assignment gate). Derived from {@link #actions()}.
     */
    boolean hasAnyAction() {
        return !actions().isEmpty();
    }

    /**
     * Caps a description string at {@link #MAX_DESCRIPTION_LENGTH} characters,
     * appending a trailing U+2026 ('…') when truncation occurs (BR-10).
     */
    static String capDescription(String s) {
        if (s.length() <= MAX_DESCRIPTION_LENGTH) {
            return s;
        }
        return s.substring(0, MAX_DESCRIPTION_LENGTH) + "\u2026";
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
     * {@link JTableRowSnapshotNode}s (one per row, capped at {@link #MAX_DATA_ROW_NODES}),
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
            ref = 0;
            return nextRef;
        }

        @Override
        protected String calculateSelfLine() {
            return "row " + rowIndex + ": " + rowText + '\n';
        }
    }
}
