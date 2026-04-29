# T-002: swing_snapshot

---

**As an** AI agent, **I want to** obtain an accessibility tree snapshot of the Swing application **so that** I can understand the current UI structure and identify components for interaction.

**Status:** Implemented (amended 2026-04-13 — `get_cells` advertising narrowed to JList/JTree; component class identity prefix added — see BR-11; amended 2026-04-15 — `get_text` suppressed for `AccessibleRole.PASSWORD_TEXT` per DR-011; amended 2026-04-15 — inline `text="..."`/`value=N` previews added per DR-013; amended 2026-04-15 — quoted slots always sanitized, name uncapped, description stays capped per DR-014; amended 2026-04-15 — `get_text` suppressed for `AccessibleRole.LABEL` per DR-015; amended 2026-04-15 — modal-stack header on modal roots per DR-016 / BR-14, interactable-windows-only contract formalised as DR-017; amended 2026-04-16 — redundant heavyweight popup windows excluded from snapshot roots per DR-017 amendment; amended 2026-04-16 — iconified Frame children suppressed per DR-019 / SC-8)
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_snapshot` tool with an optional `filter_substring` parameter.
- The tool walks the `javax.accessibility` tree of each component returned by `SwingToolContext.getConsideredComponents()` (usually a Window or JFrame, but during testing it could be any component). Each considered component is an independent root — no component in the list is nested inside another. Heavyweight popup windows (`JWindow` instances created by Swing's `PopupFactory` to host a `JPopupMenu` for a `JComboBox` or `JMenu`) are excluded from the root list because their content is already exposed as accessible children of the invoking component — see DR-017 amendment.
- The tree walker applies the **Snapshot Inclusion Rules** (below) to decide which nodes appear in the output and which are pruned.
- The tool returns a compact indented text tree with each node showing component class identity (Swing class name + accessible role), name, states, and available actions.
  - Nodes that expose at least one `AccessibleAction` also receive a numeric ref.
  - Only include accessibility name and description if those are not blank. Description follows the name in the line format.
- When a root is a modal `Dialog` whose `getOwner()` chain contains at least one visible ancestor, the renderer emits a `[modal stack (N, topmost first): Class0 "Name0" / Class1 "Name1" / ... / ClassN-1 "NameN-1"]` header immediately above the root's first line. Per-root, so each modal (should `getConsideredComponents()` ever return more than one — see DR-017) carries its own chain. See BR-14 / DR-016.
- If `filter_substring` is provided, the tool applies **tree filtering** after Phase 4 (render): it walks the rendered tree and includes every node whose rendered line contains the substring (case-insensitive), plus all **ancestors** of matching nodes (for structural context) and all **descendants** of matching nodes (so children like table rows, list items, or combo-box entries are not stripped). Non-matching sibling branches are dropped. The first line of filtered output is a notice: `[filter active: only nodes matching "<filter>" and their ancestors/descendants are shown]`. Root separators (`---`) are dropped from filtered output. If no nodes match, a short message is returned instead of an empty string.

---

## Snapshot Inclusion Rules

The tree walker applies three stages of filtering, inspired by
[Playwright MCP's accessibility snapshot](https://github.com/anthropics/playwright-mcp)
but adapted for Swing's `javax.accessibility` API and the migration use case.

### Differences from the Web World

| Web / Playwright concept | Swing equivalent | Notes |
|--------------------------|------------------|-------|
| `aria-hidden="true"` (hard exclusion incl. descendants) | **No equivalent.** All visible Swing components participate in accessibility. | — |
| Off-screen elements pruned when no role/name | **Not pruned.** Components scrolled out of a JScrollPane viewport are still `isVisible() == true`. Migration needs the full UI structure. | — |
| `role="presentation"` / `role="none"` | **No direct equivalent.** `AccessibleRole.FILLER` is the closest — handled in Stage 2. | — |
| `<script>`, `<style>`, `<head>` | **No equivalent.** Swing has no non-visual elements. | — |

### Stage 1 — Hard Exclusions (node + all descendants dropped)

| ID | Rule | Mechanism | Rationale |
|----|------|-----------|-----------|
| HE-1 | **Non-visible components** | `Component.isVisible() == false` | Invisible components are not part of the user-facing UI. Children of an invisible container inherit invisibility. |
| HE-2 | **CellRendererPane** | `component instanceof CellRendererPane` | Internal rendering artifact used by JTable, JList, JTree, JComboBox for stamp-painting cells. Not a real UI element; actual cell data comes from the accessibility API's virtual children. |
| HE-3 | **Empty glass pane** | Glass pane of a `JRootPane` with zero accessible children | Almost always an empty transparent overlay. If it *does* have accessible children (custom overlay UI), apply transparent pruning instead — drop the glass pane node but promote its children. |
| HE-4 | **JTableHeader** | `component instanceof JTableHeader` | Column header names are shown via the `columns:` annotation on the JTable node (SC-6). The header component itself (which appears as a panel with label children inside the JScrollPane) is a rendering artifact, not semantic content. |
| HE-5 | **`JPopupMenu` belonging to a `JMenu`** | `component instanceof JPopupMenu` AND `getInvoker() instanceof JMenu` | The `JMenu` already exposes its `JMenuItem` instances as accessible children; the popup (whether rendered on the root pane or in a heavyweight popup window) would duplicate them. See DR-012. Right-click / context popups, whose invoker is a `JButton`, `JTable`, etc., are unaffected. |

### Stage 2 — Transparent Pruning (node removed, children promoted to parent)

These components are framework-internal or pure-layout wrappers. Removing them
and re-parenting their children produces a cleaner tree without losing semantic information.

| ID | Rule | Rationale |
|----|------|-----------|
| TP-1 | **`AccessibleRole.ROOT_PANE`** | JRootPane wrapper present in every top-level container. Pure framework plumbing. |
| TP-2 | **`AccessibleRole.LAYERED_PANE`** | JLayeredPane between root pane and content pane. Pure framework plumbing. |
| TP-3 | **`AccessibleRole.VIEWPORT`** | JViewport inside JScrollPane. The content is the interesting part; the viewport is a clipping rect. |
| TP-4 | **`AccessibleRole.FILLER`** | Spacer component (e.g., `Box.createRigidArea()`). No semantic meaning. Closest Swing equivalent to `role="presentation"`. |
| TP-5 | **Unnamed panel** — role is `PANEL` and **all** of: `getAccessibleName()` is null/empty, `getAccessibleDescription()` is null/empty, component has no `TitledBorder` | Layout-only container. Equivalent to Playwright pruning unnamed `<div>`/`<span>` wrappers. Named panels (accessible name, description, or titled border) are kept because they represent logical UI sections. |

**`SCROLL_PANE` is NOT pruned** even when unnamed. Scrollability is a semantic property
that can influence migration decisions (e.g., wrapping content in a Vaadin `Scroller`).
The `VIEWPORT` inside it IS pruned (TP-3), so the tree shows `scroll_pane → content`
rather than `scroll_pane → viewport → content`.

#### Example — before and after pruning

Before (raw accessibility tree):
```
- frame "Invoice Editor"
  - root_pane                          ← TP-1
    - layered_pane                     ← TP-2
      - panel                          ← TP-5 (content pane, unnamed)
        - panel                        ← TP-5 (unnamed toolbar wrapper)
          - tool_bar "Main"
            - push_button "Save"
        - panel                        ← TP-5 (unnamed main area)
          - split_pane
            - panel                    ← TP-5 (unnamed left side)
              - label "Invoices"
              - scroll_pane
                - viewport             ← TP-3
                  - list
            - panel                    ← TP-5 (unnamed right side)
              - panel "Details"        ← KEPT (has titled border)
                - text "Name"
              - panel                  ← TP-5 (unnamed button bar)
                - push_button "Add"
```

After pruning (component identity format per BR-11):
```
- JFrame (frame) "Invoice Editor"
  - JToolBar (tool_bar) "Main"
    - JButton (push_button) "Save" [ref=1] actions: click
  - JSplitPane (split_pane)
    - JLabel (label) "Invoices"
    - JScrollPane (scroll_pane)
      - JList (list) [ref=2] actions: multi-selection, get_cell_count, get_cells
    - JPanel (panel) "Details"
      - JTextField (text) "Name" [ref=3] text="" actions: get_text, set_text
    - JButton (push_button) "Add" [ref=4] actions: click
```

### Stage 3 — Always Included

After Stages 1 and 2, any surviving node is included. The following criteria serve as a safety net — a node matching any of these must never be pruned, even if a future rule change would otherwise drop it:

| ID | Criterion | Swing Mechanism |
|----|-----------|-----------------|
| AI-1 | Has a non-structural accessible role | See **semantic roles** list below |
| AI-2 | Has an accessible name | `getAccessibleName()` non-null and non-empty |
| AI-3 | Has at least one action | Any action detected by the BR-06 algorithm (click, toggle_popup, increment, decrement, toggle_expand, get_text, set_text, get_value, set_value, single-selection, multi-selection, get_cell_count, get_cells, close, get_description). Note: `get_cell_count`/`get_cells` are only advertised for JList/JTree (not JTable) — see step 6b. `get_description` is only advertised when the description was capped — see step 8 and T-024. |
| AI-4 | Has `AccessibleText` with content, or `AccessibleValue` | Component carries meaningful data |
| AI-5 | Is focused | `AccessibleState.FOCUSED` in state set |

**Semantic (non-structural) accessible roles — always included:**

- Interactive: `PUSH_BUTTON`, `TOGGLE_BUTTON`, `CHECK_BOX`, `RADIO_BUTTON`, `TEXT`, `PASSWORD_TEXT`, `COMBO_BOX`, `LIST`, `TABLE`, `TREE`, `MENU_BAR`, `MENU`, `MENU_ITEM`, `POPUP_MENU`, `SLIDER`, `SPIN_BOX`, `PROGRESS_BAR`, `SCROLL_BAR`, `COLOR_CHOOSER`, `FILE_CHOOSER`, `DATE_EDITOR`
- Structural-semantic: `FRAME`, `DIALOG`, `INTERNAL_FRAME`, `OPTION_PANE`, `TOOL_BAR`, `TOOL_TIP`, `TAB_LIST`, `PAGE_TAB`, `PAGE_TAB_LIST`, `SPLIT_PANE`, `SCROLL_PANE`, `SEPARATOR`, `LABEL`, `STATUS_BAR`, `TABLE_HEADER`, `GROUP_BOX`
- Named containers: `PANEL` with accessible name, description, or `TitledBorder`

### Special Component Rules

| ID | Rule | Rationale |
|----|------|-----------|
| SC-1 | **JMenu items: include even when menu is closed.** Walk `AccessibleContext.getAccessibleChild(i)` for menus regardless of popup visibility. | The full menu structure is needed for migration. The accessibility API exposes menu items as accessible children even when the popup is not shown. |
| SC-2 | **JTabbedPane: include only the selected tab's content; render each tab with its 0-based index.** Walk `getAccessibleChild()` naturally — it exposes the tab items (`PAGE_TAB`) and the selected tab's content panel. Mark the selected tab via `SELECTED` state. Render each tab as `- (page_tab) N "title"` where `N` is the 0-based tab index, obtained from `AccessibleContext.getAccessibleIndexInParent()` (empirically verified equivalent to the JTabbedPane tab index). Tab-header accessibles are `JTabbedPane.Page` — a package-private class that is **not** a `Component` subclass, so BR-11 Case C applies: the class prefix is omitted and the identity slot is `(page_tab)` alone. Mirrors the JTable row rendering pattern (`- row N: …` — SC-6). Tab-header-disabled state is covered by SC-4 (narrower semantics: `[disabled]` on a `page_tab` means only the header is non-navigable; the tab's children, when displayed, are live — Swing-fidelity, see architecture.md § 4). | The user can only see and interact with the selected tab's content; non-selected content is not accessible via the standard accessibility API anyway. Emitting the tab index inline lets the AI pass it directly to `swing_set_selection` as `[N]` without a separate enumeration call (T-015). This is the only `PAGE_TAB`-specific rendering rule — disabled-tab semantics live in SC-4 to keep disabled behavior in one place across the spec. |
| SC-3 | **Large data components (JTable, JList, JTree): truncate to first N accessible children** where N is a static final constant in the tool class (initially **5**). When truncated, Phase 4 emits a render-only `... and X more items` summary line (not a `SnapshotNode`). | A table with thousands of rows would blow up the AI context window. 10 rows gives enough structural overview. |
| SC-4 | **Disabled components: included with `disabled` state, mirroring Swing's non-propagating semantics.** The `disabled` state is derived from `SwingUtils.isEffectivelyEnabled()` — see **architecture.md § 4 — Effectively Enabled Check** for the full rule. In short: `setEnabled(false)` does not propagate through real-`Component` ancestors in Swing, and neither does `[disabled]` in the snapshot. A button inside a disabled `JPanel` is **not** marked `disabled` (and `swing_click` would accept it). Two carveouts: (a) `JTabbedPane` tabs disabled via `setEnabledAt` carry `[disabled]` on their `page_tab` line (only the header is non-navigable; the tab's children, when displayed, are live); (b) virtual accessible children (e.g. `JTable` cells) do inherit their host component's disabled state. | The AI needs to see disabled components to understand the full UI. `[disabled]` in the snapshot is consistent with what mutation tools will actually refuse — if a tool call would fail with "disabled", the snapshot shows it; if a tool call would succeed, the snapshot does not lie by propagating `[disabled]` to its parents. See `feedback_swing_fidelity.md` — MCP mirrors what Swing allows, not what would be "cleaner". |
| SC-5 | **JDesktopPane / JDesktopIcon / JInternalFrame.** (a) `JDesktopPane` is in `SEMANTIC_ROLES` (`DESKTOP_PANE`) — always retained, never pruned, no ref (no actions). Signals MDI semantics. (b) When a JInternalFrame is iconified, Swing removes it from the accessibility tree and replaces it with a `JDesktopIcon` child on the JDesktopPane (see DR-008). `JDesktopIcon` is in `SEMANTIC_ROLES` (`DESKTOP_ICON`) — always retained. Its own accessible name is `null`; the snapshot resolves the name via `SwingUtils.getEffectiveAccessibleName()` with a three-step fallback: (1) `desktopIcon.getAccessibleContext().getAccessibleName()`, (2) `desktopIcon.getInternalFrame().getAccessibleContext().getAccessibleName()`, (3) `desktopIcon.getInternalFrame().getTitle()`. In practice this collapses to the frame's title, but respects explicit overrides — matching the BR-10 pattern of "respect explicit, fall back to derived". Rendered as `JDesktopIcon (desktop_icon) "Title" [ref=N] actions: close`. (c) **JDesktopIcon's children are hard-excluded** — the button and label inside are L&F rendering artifacts, not semantic content. (d) JInternalFrame's `AccessibleValue` (Z-order layer) is suppressed via `SUPPRESSED_VALUE_ROLES` (see DR-008). JDesktopIcon's `AccessibleValue` is also suppressed (same rationale — delegates to the frame's layer). | Iconified frames disappear from the accessibility tree (`isShowing()` → `false`, parent → `null`, `ICONIFIED` is **not** set in `AccessibleStateSet` — empirically verified, Java 21 OpenJDK, 2026-04-14). Embracing `JDesktopIcon` as-is avoids fighting the framework; the title annotation provides identity continuity. See DR-008. |
| SC-6 | **JTable: row-based rendering with column headers.** When the table header is visible (`SwingUtils.isTableHeaderVisible()`), append `columns: [Col1, Col2, …]` after the bracket and before `actions:` on the table's node line, using column names from `SwingUtils.getTableColumnNames()` (display order, respects user column reordering). Table children are rendered as pipe-separated rows via `SwingUtils.buildTableRowText()` instead of individual cell labels. Each row line is `- row N: Val1 \| Val2 \| Val3` where N is the **0-based row index** (consistent with selection tool indices). Truncation is by **rows** (not cells): `… and N more rows`. | Flat cell labels are ambiguous — the AI must guess column boundaries. Row-based output is unambiguous, more compact, and consistent with row-based selection (T-014). Column headers tell the AI what each column means without having to infer it from data. Square brackets around column names visually separate them from the `actions:` section. 0-based indices let the AI pass row numbers directly to selection tools without conversion. |
| SC-7 | **JTableHeader: suppress from snapshot.** The `JTableHeader` component (which Swing renders as a panel with label children inside the JScrollPane's column header viewport) is suppressed from the snapshot tree. Its information is redundant with the `columns:` annotation on the JTable node (SC-6). | Without suppression, the AI sees column names twice — once in the `columns:` annotation and once in the header panel. The header panel's structure (panel → labels) is a Swing rendering artifact, not semantic content. |
| SC-8 | **Iconified Frame: suppress children, show placeholder (DR-019).** When a snapshot root or sub-root is a `Frame` (including `JFrame`) with `(getExtendedState() & Frame.ICONIFIED) != 0`, the Frame node itself is rendered normally (with `[iconified]`, its actions like `restore`/`close`, and a ref), but **all of its children are suppressed**. In their place, a single indented placeholder line is emitted: `[Contents hidden — window is iconified. Call swing_restore to interact with this window.]`. The suppressed children receive **no refs** — they are not walked during Phase 3 (assignRefs) and not rendered during Phase 4 (render). **Phase 2 (prune) still runs** on the children so that if the frame is later restored, the pruned tree is ready — but Phase 3 and Phase 4 skip the children and emit the placeholder instead. Does not apply to `JInternalFrame` — iconified internal frames are already replaced by `JDesktopIcon` in the tree (SC-5 / DR-008). | `swing_screenshot` renders iconified frames via `printAll()` which produces a full image identical to the normal state. Without SC-8 the snapshot would list clickable children (with refs) that contradict the `[iconified]` state — the AI would attempt interactions that cannot succeed on a minimized window. Suppressing children and showing the recovery action (`swing_restore`) prevents this confusion. The Frame node is preserved so that `restore` and `close` remain available. |

### Accessible States — Display Rules

Which states from `AccessibleStateSet` appear in the snapshot output:

**Included (meaningful for AI understanding):**
`FOCUSED`, `SELECTED`, `CHECKED`, `EXPANDED`, `COLLAPSED`, `MODAL`, `MULTI_LINE`, `HORIZONTAL`, `VERTICAL`, `BUSY`, `INDETERMINATE`

**Synthetic states (derived, not from `AccessibleStateSet` directly):**
`DISABLED` — emitted when `SwingUtils.isEffectivelyEnabled()` returns `false`. Per **architecture.md § 4**, Swing's `setEnabled(false)` does not propagate to children, so the snapshot mirrors that: a locally-enabled component inside a disabled `JPanel`/`JScrollPane`/`JToolBar`/`JTabbedPane` tab is **not** marked `disabled`. Carveouts: `JTabbedPane` tabs disabled via `setEnabledAt` carry `[disabled]` on the `page_tab` line (header-only semantics), and virtual children (e.g. `JTable` cells) do inherit their host's disabled state. This is consistent with the check mutation tools perform at execution time — if the snapshot shows `[disabled]`, the mutation tool will refuse; if it does not, the tool will accept.

`READ_ONLY` — emitted for text components that expose `AccessibleEditableText` but lack the `EDITABLE` state in their `AccessibleStateSet` (e.g. `JTextField` with `setEditable(false)`). Most text fields are editable by default, so the absence of `read_only` means editable — no `editable` flag is shown. This keeps the snapshot concise: only the exceptional read-only case is annotated.

`ICONIFIED` — emitted for JFrame when `frame.getExtendedState() & Frame.ICONIFIED != 0`. The JDK's `AccessibleStateSet` never contains `ICONIFIED` (verified Java 21 OpenJDK, 2026-04-14 — JDK bug, will never be fixed). Does not apply to JInternalFrame — iconified internal frames are replaced by `JDesktopIcon` in the tree (DR-008). See DR-009.

**Omitted (noise or always-true for included nodes):**
`VISIBLE`, `SHOWING`, `ENABLED` (superseded by synthetic `DISABLED` derived from `isEffectivelyEnabled()`), `EDITABLE` (default for text fields — only show its absence as `READ_ONLY`), `OPAQUE`, `RESIZABLE`, `ARMED`, `TRANSIENT`, `MANAGES_DESCENDANTS`

---

## Swing Component Reference

Quick-reference mapping every common Swing component to its `AccessibleRole`, whether it
survives pruning, and what it looks like in the snapshot output.

### Top-Level Containers

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JFrame` | `FRAME` | No | `- JFrame (frame) "My App" [ref=1] actions: close` (normal); when iconified: children suppressed, placeholder shown (SC-8) |
| `JDialog` | `DIALOG` | No | `- JDialog (dialog) "Confirm" [ref=1, modal] actions: close` |
| `JInternalFrame` | `INTERNAL_FRAME` | No | `- JInternalFrame (internal_frame) "Document" [ref=1] actions: close` |
| `JDesktopPane` | `DESKTOP_PANE` | No | `- JDesktopPane (desktop_pane)` |
| `JDesktopIcon` | `DESKTOP_ICON` | No (children hard-excluded) | `- JDesktopIcon (desktop_icon) "Document" [ref=1] actions: close` |
| `JRootPane` | `ROOT_PANE` | Transparent (TP-1) | *(children promoted to parent)* |

### Framework-Internal (Always Pruned)

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLayeredPane` | `LAYERED_PANE` | Transparent (TP-2) | *(children promoted to parent)* |
| `JViewport` | `VIEWPORT` | Transparent (TP-3) | *(children promoted to parent)* |
| `Box.Filler` / rigid area | `FILLER` | Transparent (TP-4) | *(children promoted to parent)* |
| `CellRendererPane` | *(n/a)* | Hard-excluded (HE-2) | *(dropped with descendants)* |

### Containers & Layout

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JPanel` (unnamed) | `PANEL` | Transparent (TP-5) | *(children promoted to parent)* |
| `JPanel` (named / titled border) | `PANEL` | No | `- JPanel (panel) "Details"` |
| `JScrollPane` | `SCROLL_PANE` | No | `- JScrollPane (scroll_pane)` |
| `JSplitPane` | `SPLIT_PANE` | No | `- JSplitPane (split_pane)` |
| `JTabbedPane` | `PAGE_TAB_LIST` | No | `- JTabbedPane (page_tab_list) "General" [ref=1] actions: single-selection` |
| *(tab within JTabbedPane)* | `PAGE_TAB` | No | `- (page_tab) 0 "General" [selected]` — non-Component accessible (`JTabbedPane.Page`), BR-11 Case C; index is 0-based, emitted inline (SC-2) |
| `JToolBar` | `TOOL_BAR` | No | `- JToolBar (tool_bar) "Main"` |
| `JOptionPane` | `OPTION_PANE` | No | `- JOptionPane (option_pane)` |

### Buttons

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JButton` | `PUSH_BUTTON` | No | `- JButton (push_button) "Save" [ref=1] actions: click` |
| `JToggleButton` | `TOGGLE_BUTTON` | No | `- JToggleButton (toggle_button) "Bold" [ref=1] actions: click` |
| `JCheckBox` | `CHECK_BOX` | No | `- JCheckBox (check_box) "Remember me" [ref=1, checked] actions: click` |
| `JRadioButton` | `RADIO_BUTTON` | No | `- JRadioButton (radio_button) "Option A" [ref=1] actions: click` |

### Text Input

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JTextField` | `TEXT` | No | `- JTextField (text) "Name" [ref=1, editable] text="admin" actions: get_text, set_text` — inline `text="..."` preview per BR-12 (DR-013), 15-char cap with `…` suffix (`text="Lorem ipsum d…"`); empty/null document renders as `text=""`. |
| `JPasswordField` | `PASSWORD_TEXT` | No | `- JPasswordField (password_text) "Password" [ref=1, editable] actions: set_text` — `get_text` is suppressed (DR-011); no inline `text="..."` preview either, since BR-12 keys off `supportsGetText()` which already excludes `PASSWORD_TEXT`. A non-editable password field (`setEditable(false)`) shows `actions: !set_text` only. |
| `JTextArea` | `TEXT` | No | `- JTextArea (text) "Notes" [ref=1, editable, multi_line] text="line one line…" actions: get_text, set_text` — newlines collapsed to spaces in the preview (BR-12); call `swing_get_text` for full multi-line content. |
| `JEditorPane` | `TEXT` | No | `- JEditorPane (text) "Content" [ref=1, editable, multi_line] text="Rendered body…" actions: get_text, set_text` — HTML-stripped rendered text in the preview (BR-12 + T-005 BR-11). |

### Selection & Data Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JComboBox` | `COMBO_BOX` | No | `- JComboBox (combo_box) "Country" [ref=1, collapsed] actions: toggle_popup, single-selection` |
| `JList` | `LIST` | No | `- JList (list) [ref=1] actions: multi-selection, get_cell_count, get_cells` |
| *(child of JList)* | `LABEL` | No | `  - (label) "Item 1" [ref=2] actions: click` — non-Component accessible (`JList.AccessibleJListChild`), BR-11 Case C. Never advertises `get_text` (DR-015 — LABEL role). The item text is read from the name slot. |
| `JTree` | `TREE` | No | `- JTree (tree) [ref=1] actions: get_cell_count, get_cells` |
| *(non-leaf tree node)* | varies | No | `  - (label) "Folder" [ref=2] actions: toggle_expand, click` — non-Component accessible (`JTree.AccessibleJTreeNode`), BR-11 Case C |
| `JTable` | `TABLE` | No | `- JTable (table) [ref=1] columns: [ID, Name, City] actions: multi-selection` (with row children: `- row 0: 1 \| Alice \| NY` — row rendering per SC-6 does not use the component-identity slot). Row-level access is via `swing_get_items` (implicit from `single-selection`/`multi-selection`); JTable does NOT advertise `get_cell_count` or `get_cells`. |

### Value Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JSlider` | `SLIDER` | No | `- JSlider (slider) "Volume" [ref=1, horizontal] value=42 actions: increment, decrement, get_value, set_value` — inline `value=N` preview per BR-12 (DR-013). |
| `JSpinner` | `SPIN_BOX` | No | `- JSpinner (spin_box) "Quantity" [ref=1] value=10 actions: increment, decrement, get_value, set_value` — inline `value=N` per BR-12. Fractional spinner values render with a decimal point (e.g. `value=3.5`). |
| `JProgressBar` | `PROGRESS_BAR` | No | `- JProgressBar (progress_bar) "Loading" [horizontal] value=37/100 actions: get_value` — `PROGRESS_BAR` renders as `value=current/max` per BR-12 when the maximum is non-null; bare `value=N` otherwise. |

### Display Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLabel` | `LABEL` | No | `- JLabel (label) "Status: OK"` — never advertises `get_text` and never emits `text="..."` preview, regardless of whether the label is plain text or HTML-wrapped (DR-015). The label's content is read from the name slot (uncapped per DR-014). No ref unless some other action applies (none do in practice). |
| `JToolTip` | `TOOL_TIP` | No | `- JToolTip (tool_tip) "Click to save"` |
| `JSeparator` | `SEPARATOR` | No | `- JSeparator (separator)` |
| `JScrollBar` | `SCROLL_BAR` | No | `- JScrollBar (scroll_bar) [ref=1, vertical] value=0 actions: increment, decrement, get_value, set_value` — inline `value=N` per BR-12. |

### Menu Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JMenuBar` | `MENU_BAR` | No | `- JMenuBar (menu_bar)` |
| `JMenu` | `MENU` | No (structural) | `- JMenu (menu) "File"` — no `click` action, no ref. See **DR-012**. The menu's `JMenuItem` children render underneath it. |
| `JMenuItem` | `MENU_ITEM` | No | `- JMenuItem (menu_item) "Open" [ref=1] actions: click` |
| `JCheckBoxMenuItem` | `CHECK_BOX` | No | `- JCheckBoxMenuItem (check_box) "Word Wrap" [ref=2, checked] actions: click` |
| `JRadioButtonMenuItem` | `RADIO_BUTTON` | No | `- JRadioButtonMenuItem (radio_button) "Light Theme" [ref=3] actions: click` |
| `JPopupMenu` | `POPUP_MENU` | Pruned (HE-5) when `getInvoker() instanceof JMenu`; otherwise kept | `- JPopupMenu (popup_menu)` |

### Custom Subclasses and Non-Component Accessibles

Beyond the standard Swing components above, two additional identity-slot forms arise (see BR-11):

| Scenario | Accessible | Snapshot Example |
|---|---|---|
| User subclass of a standard Swing component (e.g. a company component library's `SearchField extends JTextField`) | concrete subclass, `Component` | `- SearchField -> JTextField (text) "search the catalog" [ref=1] text="widgets" actions: get_text, set_text` (BR-11 Case B; inline `text="..."` per BR-12) |
| Third-party subclass (e.g. SwingX `JXTable extends JTable`) | concrete subclass, `Component` | `- JXTable -> JTable (table) [ref=1] columns: [Name, Email] actions: multi-selection` (BR-11 Case B) |
| Anonymous subclass (`new JButton() { ... }`) | anonymous, `Component` | `- JButton (push_button) "OK" [ref=1] actions: click` — anonymous class name stripped (BR-11) |
| CGLIB / ByteBuddy / Hibernate runtime proxy wrapping a user subclass | proxy, `Component` | `- SearchField -> JTextField (text) [ref=1] text="" actions: get_text, set_text` — proxy name stripped (BR-11) |
| `JTabbedPane` tab | `JTabbedPane.Page`, **not** a `Component` | `- (page_tab) 0 "General" [selected]` (BR-11 Case C; SC-2) |
| `JList` item | `JList.AccessibleJListChild`, **not** a `Component` | `- (label) "Item 1" [ref=2] actions: click` (BR-11 Case C) |
| `JTree` node | `JTree.AccessibleJTreeNode`, **not** a `Component` | `- (label) "Folder" [ref=2] actions: toggle_expand, click` (BR-11 Case C) |

**Notes:**
- `JLabel` has no actions and therefore no ref — it appears in the snapshot for context but is not interactable.
- `JProgressBar` exposes `get_value` but not `set_value` (read-only value role).
- `JCheckBoxMenuItem` and `JRadioButtonMenuItem` share roles with their non-menu counterparts (`CHECK_BOX`, `RADIO_BUTTON`).
- `JTextArea` and `JEditorPane` share the `TEXT` role with `JTextField` but include the `multi_line` state.
- Snapshot examples show typical states; actual output depends on the component's runtime configuration.
- Mutation actions prefixed with `!` are unavailable because the component is disabled or read-only. For example, a disabled button shows `actions: !click`; a read-only text field shows `actions: get_text, !set_text`.
- Inline `text="..."` / `value=N` previews (BR-12 / DR-013) appear between the states bracket and `actions:`. The two labels are mutually exclusive across every standard Swing component: text components emit `text`, value components emit `value`. `JPasswordField` never emits `text="..."` (DR-011 gate). `JCheckBox` / `JRadioButton` do not emit a value preview — their state is already carried by `[checked]`/`[selected]`.

---

## Implementation Notes

### Internal Representation: `SnapshotNode`

Because Swing's accessibility tree is read-only (owned by the framework), the tool builds its own mutable tree of `SnapshotNode` instances. Each node holds a reference to an `Accessible` (from which `AccessibleContext` is retrieved on demand via `getAccessibleContext()`) and an ordered list of `SnapshotNode` children. The node is the natural home for all pruning and ref-assignment logic.

### Role and State Name Resolution

`AccessibleRole` and `AccessibleState` are classes with public static final instances, not enums, so there is no built-in way to get a field name from an instance. For both, a static `Map<T, String>` is populated once at class-load time via reflection over the class's declared fields, mapping each instance to its field name lowercased (e.g. `PUSH_BUTTON` → `"push_button"`, `DISABLED` → `"disabled"`). Unknown instances (custom subclasses) fall back to `"unknown"`. These maps live in a dedicated utility class (e.g. `AccessibleNames`).

This reflection-based mapping is deliberate: `AccessibleRole.toDisplayString()` is locale-sensitive (returns `"Drucktaste"` on a German JVM) and unsuitable for an LLM-facing stable vocabulary. Field-name lowercasing produces the same English identifier on every JVM.

### Component Identity Resolution

Per BR-11, every node's rendered line begins with a component-identity slot of the form `JClass (role)`, `ConcreteClass -> JClass (role)`, or `(role)`. The algorithm for computing this slot operates on the node's `Accessible` instance:

**Step 1 — Concrete class.** `concrete = accessible.getClass()`.

**Step 2 — Display-class selection (strip runtime artefacts and framework internals).** Starting at `concrete`, walk `getSuperclass()` while the current class is one of:

- `isAnonymousClass()` — anonymous subclass like `new JButton() { ... }` whose simple name is empty
- `isSynthetic()` — compiler-generated classes
- `isLocalClass()` — classes declared inside a method body
- a runtime-generated proxy: `className.chars().filter(c -> c == '$').count() >= 2 && getEnclosingClass() == null`. This catches CGLIB (`$$EnhancerBySpringCGLIB$$`), Hibernate (`$HibernateProxy$`), ByteBuddy (`$ByteBuddy$`), Mockito, and similar frameworks. Named nested classes (`MyApp$Panel$CloseButton`) are preserved because they have a non-null `getEnclosingClass()`.
- a class in `javax.swing.plaf` or any subpackage — L&F implementation classes like `MetalScrollButton`, `BasicArrowButton`, `BasicComboPopup`. These are Swing implementation detail rather than the widget vocabulary the LLM knows; the walk continues up to the real widget (`JButton`, `JComboBox`, …).
- a class whose `getEnclosingClass()` is itself in `javax.swing.*` or `java.awt.*` — JDK-internal nested classes like `JScrollPane.ScrollBar`, `JSpinner.DefaultEditor`, `JFormattedTextField.AbstractFormatter`. These are framework internals the end user did not write; user-authored nested classes (enclosing class in user packages) are preserved.

The first class that is not one of the above is the **display class**. (If every ancestor up to `Object` is stripped, fall through to Case C below — but in practice at least one non-stripped superclass always exists because proxies, anonymous/synthetic/local classes, and JDK internals always extend a real widget class by construction.)

**Step 3 — Qualifying-ancestor walk-up.** Starting at the display class, walk `getSuperclass()` until a class is found that satisfies **all** of:

- `public`
- not nested (the class is a top-level class — not an inner, static nested, local, or anonymous class)
- package is `javax.swing`, OR package starts with `javax.swing.` but does **not** start with `javax.swing.plaf` (plaf classes like `BasicArrowButton` are L&F implementation details; walking up past them lands on the real Swing widget the LLM should see), OR package is `java.awt`
- assignable to `java.awt.Component` OR `java.awt.MenuComponent` (AWT menu classes extend `MenuComponent`, not `Component`; including them preserves identity for the old-AWT corner of ancient Swing apps)

Abstract classes qualify: `AbstractButton` and `JTextComponent` are the canonical cases. Instantiability is not a requirement — what matters is that the class is recognisable to an LLM trained on Swing.

The first class that satisfies every criterion is the **qualifying ancestor**. If no ancestor qualifies (the walk reaches `Object`), there is no qualifying ancestor — Case C applies.

**Step 4 — Format selection.** Let `role` be the lowercased-underscore role name (per Role and State Name Resolution).

- **Case A — standard component.** Display class equals qualifying ancestor. Identity slot: `JClass (role)`.
  *Example:* standard `JButton` → `JButton (push_button)`.
- **Case B — meaningful custom subclass.** Display class differs from qualifying ancestor. Identity slot: `ConcreteSimpleName -> JClass (role)`, using `displayClass.getSimpleName()` for the concrete side.
  *Example:* `com.acme.ui.SearchField extends JTextField` → `SearchField -> JTextField (text)`.
- **Case C — non-Component accessible.** No qualifying ancestor (the accessible does not extend `Component` or `MenuComponent`). Identity slot: `(role)` — parentheses preserved, class name omitted.
  *Example:* `JTabbedPane.Page` → `(page_tab)`; `JList.AccessibleJListChild` → `(label)`.

**Display-class name format.** `displayClass.getSimpleName()` is used for the concrete side (Case B), not `getCanonicalName()`. Nested classes collapse to the innermost simple name (`MyApp.Panel.CloseButton` → `"CloseButton"`), which trades uniqueness for readability. Collisions are disambiguated by accessible name and context in practice; if real-world usage shows confusion, revisit with a fully-qualified form.

**Why walk past `javax.swing.plaf.*`.** L&F implementation classes like `BasicArrowButton`, `BasicComboPopup`, and `MetalComboBoxButton` are public and qualify under every other rule, but they are Swing *implementation detail* rather than the widget vocabulary the LLM knows. Walking up past them to `JButton`/`JPopupMenu`/`JComboBox` yields the name the LLM can actually reason about.

**Why abstract classes qualify.** Users occasionally subclass `AbstractButton` or `JTextComponent` directly to implement a new button/text widget without the appearance chrome of `JButton`/`JTextField`. Excluding abstracts would send the walk-up all the way to `JComponent`, losing the "button-family" or "text-component-family" signal.

**Unknown-fallback policy.** If the concrete `Accessible` has no `AccessibleContext` or the role cannot be resolved, the role slot falls back to `"unknown"` (same fallback as Role Name Resolution). Class resolution proceeds normally.

A dedicated utility class (e.g. `ComponentClassResolver`) implements steps 1–4; `SnapshotNode.calculateSelfLine()` consumes its output at the start of the line. An **audit test** in `src/test` enumerates every class in `javax.swing` and its subpackages (excluding `javax.swing.plaf`) plus `java.awt`, applies the qualifying-ancestor predicate, and asserts the resulting set matches a checked-in fixture. This test fails on JDK drift (new or removed classes), forcing a deliberate update rather than silent behaviour change.

### Four-Phase Pipeline

```
Accessible tree
      │  build()
      ▼
SnapshotNode tree   (full mirror of the accessibility tree)
      │  prune()
      ▼
Pruned tree         (hard exclusions dropped, transparent nodes flattened)
      │  assignRefs()
      ▼
Ref-annotated tree  (action-bearing nodes numbered 1…N)
      │  render()
      ▼
Text output
```

**Phase 1 — build**
Recursively walks `AccessibleContext.getAccessibleChild(i)`, constructing one `SnapshotNode` per accessible child. Special cases that affect *which* children to walk are applied here:
- SC-1: walk menu children even when popup is closed
- SC-2: walk `JTabbedPane` children naturally via `getAccessibleChild()` — only the selected tab's content is exposed
- SC-3: cap large-data components (`JTable`, `JList`, `JTree`) at `MAX_DATA_ROW_NODES` accessible children; only the first `MAX_DATA_ROW_NODES` children become `SnapshotNode`s. The `... and N more items` summary is a render-only artifact emitted by Phase 4 — it is never a `SnapshotNode` and can never receive a ref. For JTable, the cap is applied to **rows** (not cells) — see SC-6.

The output of this phase is a complete, unfiltered mirror of the accessibility tree.

**Phase 2 — prune**
Each `SnapshotNode` evaluates itself by returning one of three outcomes:

| Outcome | Meaning |
|---------|---------|
| `Keep` | Include this node; recurse into children and prune them too |
| `Drop` | Hard-exclude this node and all its descendants (Stages 1) |
| `Transparent` | Drop this node; its pruned children are promoted to the grandparent (Stage 2) |

The parent processes its children list, substituting each child's result: `Keep` → add child, `Drop` → omit, `Transparent` → add the child's (recursively pruned) children in its place.

Stage 3 (Always Included) acts as a safety guard inside the `Keep`/`Transparent` decision: a node matching any AI-1…AI-5 criterion must return `Keep` regardless of other rules.

**Phase 3 — assignRefs**
A depth-first traversal over the pruned tree. Each node that exposes at least one action under the BR-06 algorithm receives the next integer ref. `assignRefs` accepts a `startRef` parameter (the first ref it may assign) so that multiple roots share a single global sequence: root 1 calls `assignRefs(1)` and returns the next free ref; root 2 calls `assignRefs` with that value, and so on.

**Phase 4 — render**
A depth-first traversal that serialises each node to a line of text per BR-03, using indentation depth to represent the tree structure. After rendering the last `SnapshotNode` child of a truncated large-data component, emits a synthetic `... and N more items` line (not a node — no ref, no pruning). For JTable, Phase 4 applies SC-6: if the header is visible, appends `columns: [Col1, Col2, …]` after the bracket and before `actions:` on the table node line; children are rendered as pipe-separated row lines via `SwingUtils.buildTableRowText()` instead of individual cell labels; truncation counts rows (`… and N more rows`). When multiple roots are present, their rendered trees are separated by a `---` line.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | Refs are short integers starting from 1, assigned fresh with each snapshot call, globally across all roots. Only nodes that expose at least one action under the BR-06/BR-07 algorithm receive a ref. |
| BR-02 | The entire four-phase pipeline runs on the EDT via `runInEDT()` (which uses `SwingUtilities.invokeLater()` + a `CountDownLatch`). All phases — including prune, assignRefs, and render — execute inside that single call. Off-EDT optimisation is deferred until a performance problem is demonstrated. Tool calls always arrive from an HTTP thread (via `HttpMCPServer`) — never from the EDT — so `runInEDT()` will never deadlock. Do **not** add EDT detection (`SwingUtilities.isEventDispatchThread()`) as a "helpful" fallback; it would mask bugs and is not needed. |
| BR-03 | The output format is a compact indented text tree (not YAML), mimicking Playwright MCP. Line format: `- <component-identity> "name" "description" [ref=N, state1, state2] additional-info actions: action1, !action2`. The `<component-identity>` slot is defined in BR-11 and renders as `JClass (role)`, `ConcreteClass -> JClass (role)`, or `(role)` depending on the case. Role is the `AccessibleRole` field name lowercased with underscores (e.g. `push_button`, `text`, `scroll_pane`) — never `toDisplayString()`, which is locale-sensitive. Omit `"name"` if blank; omit `"description"` if blank — see BR-10 for the description's source resolution (tooltip fallback) and 120-character cap. Ref and states share one bracket, comma-separated, lowercase. Omit the bracket entirely if there is no ref and no states. Additional component-specific info appears after the bracket and before `actions:`; omitted when empty. This slot carries `columns: [ID, Name, City]` for JTable (SC-6) and the **inline value preview** for components whose content is exposed via `AccessibleText` or `AccessibleValue` (BR-12, DR-013): `text="..."` when `SwingUtils.supportsGetText()` is true (string capped at 15 chars per the DR-010 convention: `≤15 → full; else first 14 + …`; newlines/whitespace runs collapsed to a single space; null content rendered as `""`); `value=N` when `SwingUtils.supportsGetValue()` is true (bare number; `PROGRESS_BAR` renders as `value=N/M` when the maximum is non-null). The two labels are mutually exclusive in practice — standard Swing never gates both true — but a custom widget exposing both is permitted to emit both. When `columns:` and a value preview co-occur (no standard component triggers this), `columns:` is emitted first. Omit `actions:` if none. Mutation actions that would fail validation are prefixed with `!` (see BR-08). |
| BR-04 | The tree walker walks the `javax.accessibility` tree via `AccessibleContext.getAccessibleChild(i)`, **not** the `Component.getComponents()` component tree. The accessibility tree provides virtual children for complex components (table cells, list items, tree nodes). |
| BR-05 | Large data components (JTable, JList, JTree) are truncated to `MAX_DATA_ROW_NODES` accessible children (static final constant, initially 5). When truncated, a synthetic `... and N more items` node is appended. For JTable, truncation is by **rows** and the summary reads `... and N more rows` (SC-6). |
| BR-06 | Action labels displayed in the snapshot are determined by the **Action Label Algorithm** below. Detection methods, Java mechanisms, and MCP tool names are defined in **architecture.md §6**. All action names use lower-case underscore-separated format. |
| BR-07 | A node receives a ref if it exposes at least one action under the BR-06 algorithm — i.e. any of: `supportsClick()` returns non-null (covers both AccessibleAction and MouseListener fallback), `supportsTogglePopup()`, a known `AccessibleAction` constant, `supportsGetText()`, `supportsSetText()`, `supportsGetValue()`, `supportsSetValue()`, `supportsSelection()`, `supportsClose()`, or the `isLargeDataComponent` truncation gate (step 6b) returns non-null/true. `supportsSelection()` maps to one group label (`single-selection` or `multi-selection`). This supersedes the `AccessibleAction`-only gate in BR-01. Nodes where all actions are `!`-prefixed still receive a ref. |
| BR-08 | **Unavailable action prefix (`!`).** After the BR-06 algorithm produces the action list, each **mutation action** is checked: if the action would fail validation when invoked, it is prefixed with `!` (e.g. `!click`, `!set_text`). A mutation action is unavailable when: (a) `SwingUtils.isEffectivelyEnabled()` returns `false` (component or an ancestor is disabled), or (b) the action is `set_text` and the component is read-only (has `AccessibleEditableText` but lacks the `EDITABLE` state). **Read-only actions** (`get_text`, `get_value`, `get_selection`, `get_items`, `get_item_count`, `get_cell_count`, `get_cells`, `get_description`) are never prefixed — they always succeed. **Selection group labels** (`single-selection`, `multi-selection`) are never prefixed — they are informational labels, not directly invocable actions. The set of mutation actions is: `click`, `toggle_popup`, `increment`, `decrement`, `toggle_expand`, `set_text`, `set_value`, `close`. This set is stored as a constant (`MUTATION_ACTIONS`) in `SnapshotNode`. |
| BR-09 | **Snapshot filtering (`filter_substring`).** When the optional `filter_substring` parameter is provided, the tool applies **tree filtering** after Phase 4 (render). The algorithm walks the rendered tree (split into lines, using indentation depth to reconstruct parent/child relationships) and marks every node whose rendered line contains the substring (case-insensitive `String.toLowerCase().contains()`) as a **match**. The output includes: (a) every matched node, (b) every **ancestor** of a matched node (to preserve structural context / path from root), and (c) every **descendant** of a matched node (so children like table rows, list items, combo-box entries are always shown with their parent). Non-matching sibling branches are dropped entirely. The first line of filtered output is a notice: `[filter active: only nodes matching "<filter>" and their ancestors/descendants are shown]`. Root separators (`---`) are excluded from filtered output. The ref map is unaffected — filtering does not change ref assignment. If no nodes match, the tool returns the message `No lines matched filter_substring 'X'` (where X is the provided value). |
| BR-10 | **Description source resolution, HTML cleanup, and 120-character cap (per DR-014).** The `"description"` slot in the BR-03 line format is filled from the following sources, in order: (a) `AccessibleContext.getAccessibleDescription()` if non-blank after HTML cleanup, else (b) the tooltip via `SwingUtils.getTooltipAsText()` if non-blank — which resolves the tooltip from the JComponent or, for `PAGE_TAB` nodes, from `JTabbedPane.getToolTipTextAt(index)`. **HTML cleanup applies to both sources** via `SwingUtils.htmlToPlainText()`: any value starting with `<html>` (case-insensitive) has each tag replaced by a single space, the four standard entities (`&amp;`, `&lt;`, `&gt;`, `&nbsp;`) decoded, runs of whitespace collapsed, and the result trimmed; non-HTML strings pass through verbatim so a literal value like `"List<String>"` is preserved. The result then passes through the BR-13 sanitizer (whitespace collapse + `"` escape) — this second pass handles non-HTML descriptions containing newlines, tabs, or embedded quotes that would otherwise break the snapshot line format. **Why cleanup applies to (a):** `JComponent.AccessibleJComponent.getAccessibleDescription()` already auto-falls-back to `getToolTipText()` inside the JDK when no explicit description is set — so an "explicit-looking" value returned from (a) may in fact be a (potentially HTML) tooltip. The resolved, sanitized description is then capped at **120 characters**, with truncation indicated by a trailing `…` (U+2026). The cap applies symmetrically to all sources so the AI cannot distinguish a real description from a tooltip-fallback. Per-cell, per-row, per-node and per-item tooltips on `JTable`, `JList`, `JTree` and `JTableHeader` are **not** exposed — those are computed on the fly by the cell renderer in response to a `MouseEvent`, which is not available during snapshot building. **Rationale for the cap:** older Swing apps frequently never set `accessibleDescription`; the tooltip is often the only descriptive signal available. Symmetric capping prevents long tooltips/descriptions from bloating the snapshot while preserving the most useful prefix. The 120-char limit is specific to the description slot — names are uncapped (DR-014) because they are identity. |
| BR-11 | **Component identity slot (class prefix + role).** Every node's rendered line begins with a component-identity slot computed by the algorithm in **Implementation Notes — Component Identity Resolution**. Three cases, exhaustive: **Case A** — standard Swing component, slot is `JClass (role)` (e.g. `JButton (push_button)`). **Case B** — meaningful custom subclass, slot is `ConcreteSimpleName -> JClass (role)` (e.g. `SearchField -> JTextField (text)`); the concrete class is stripped of anonymous, synthetic, local, proxy artefacts (`$$`-containing runtime classes with no enclosing class), `javax.swing.plaf.*` L&F internals, and JDK-internal nested classes (enclosing class in `javax.swing.*`/`java.awt.*`) before display. **Case C** — non-Component accessible (e.g. `JTabbedPane.Page`, `JList.AccessibleJListChild`, `JTree.AccessibleJTreeNode`), slot is `(role)` alone — class name omitted, parentheses retained so the role still reads as "type info in parens." The parenthesised role is unconditional across all three cases, including when the role is a tautological lowercasing of the class (`JButton (push_button)`), so that a non-standard role override (`JButton (button_with_dropdown)`) becomes a clean attention signal. Qualifying-ancestor criteria (public, non-nested, in `javax.swing` or its subpackages except `javax.swing.plaf.*`, or in `java.awt`; assignable to `Component` or `MenuComponent`; abstract classes qualify) and the full walk-up/strip algorithm are specified in the Component Identity Resolution section. |
| BR-12 | **Inline value preview — full specification (DR-013).** The `text="..."` / `value=N` annotations introduced in BR-03 are produced by shared read helpers `SwingUtils.readText(Accessible) → String` and `SwingUtils.readValue(Accessible) → Number`, which are also called by `swing_get_text` (T-005) and `swing_get_value` (T-012) — so snapshot preview and round-trip tool can never disagree. Snapshot truncates; the tools return whole. **Gate parity with actions:** `text="..."` is emitted iff the BR-06 algorithm would advertise `get_text` or `set_text` (including `!set_text`); `value=N` is emitted iff it would advertise `get_value`. **Password fields:** `SwingUtils.supportsGetText()` already excludes `AccessibleRole.PASSWORD_TEXT` (DR-011), so password fields emit no `text="..."` — the gate parity handles this without a new carveout. **Labels:** `SwingUtils.supportsGetText()` also excludes `AccessibleRole.LABEL` (DR-015), so JLabels (plain or HTML) emit no `text="..."` either — same gate-parity logic. **Sanitization:** the preview string passes through the BR-13 sanitizer (whitespace collapse + `"` escape) before the 15-char cap, so a field containing `say "hi"` renders as `text="say \"hi\""` — quote-safe. **Defensive read:** the inline preview is wrapped in try/catch; if `readText`/`readValue` throws (document lock contention, misbehaving custom impl), the single annotation is omitted and a `FINE` JUL log line records the failure — the rest of the snapshot still renders. **Rationale, alternatives (including the rejected `include_values` flag and bulk-getter), and trade-offs** live in DR-013. |
| BR-13 | **Quoted-slot sanitization (DR-014).** Every string emitted inside double quotes in the BR-03 line format — the `"name"` slot, the `"description"` slot, and the `text="..."` preview payload — passes through `SwingUtils.sanitizeForQuotedSlot(String)` before emission. The helper (a) replaces any run of whitespace characters with a single ASCII space — covers Java's ASCII `\s` (`\n`, `\r`, `\t`, vertical tab, form feed) plus the Unicode line separators U+0085/U+2028/U+2029 (added explicitly because Java's default `\s` is ASCII-only); (b) escapes embedded `"` as `\"`; (c) strips leading/trailing whitespace; (d) returns `null` if the result is empty or blank (so existing null-checks at call sites continue to work). Backslashes are **not** escaped — literal `\` passes through. The helper is **not idempotent** — re-running it on already-sanitized input would double-escape embedded quotes; the render path calls it exactly once per slot to prevent that. **Length policies are slot-specific, not unified:** name is uncapped (identity — DR-014 §2); description is capped at 120 per BR-10 (supplementary — DR-014 §3); the BR-12 preview is capped at 15 per DR-013. Sanitization runs **before** any cap so truncation always operates on printable content. **Rationale:** component-controlled text (accessible name, title, cell renderer output, accessible description, tooltip, text-field content) can contain arbitrary user/backend-supplied characters — newlines would break the one-line-per-node invariant that the snapshot's indent-based tree structure depends on, and embedded `"` would terminate a quoted slot visually. Full rationale, alternatives (including full JSON-style backslash escaping, quote replacement with `'` or `…`, and unconditionally capping the name slot), and the asymmetry between name and description live in DR-014. |
| BR-14 | **Modal-stack header (DR-016).** When a snapshot root is a modal `Dialog` (`dialog.isModal() == true`) whose `getOwner()` chain contains at least one visible ancestor (`SwingUtils.isVisible(owner) == true`), the renderer emits a single-line header immediately above the root's tree: `[modal stack (N, topmost first): Class0 "Name0" / Class1 "Name1" / ... / ClassN-1 "NameN-1"]`. `N` counts the rendered root plus all visible ancestors; header is suppressed when `N < 2`. The literal `topmost first` annotation inside the parenthesis states the ordering convention inline so the header is self-describing. Each chain entry uses the concrete simple class name resolved via `ComponentClassResolver.resolveDisplayClass(...).getSimpleName()` (BR-11 strip rules — anonymous / synthetic / proxy / `javax.swing.plaf.*` / JDK-internal nested classes walked through), followed by the `AccessibleContext.getAccessibleName()` sanitised per BR-13 and wrapped in double quotes; the quoted slot is omitted when the name is null/blank. Separator is ASCII ` / ` (space-slash-space) — deliberately not an arrow, because the BR-11 identity slot already uses `->` for the `Concrete -> JClass` form and reusing arrow glyphs invites miscategorisation. Header is emitted **per-root** so that multiple coexisting modals (should DR-017's current topmost-only scope ever be revisited) each carry their own chain, separated from neighbouring roots by the existing `---` line. Header is **dropped from filtered output** (`filter_substring`) — the filter matches content, and the header carries none. Rationale, alternatives (flat window inventory, ancestors-as-stub-roots, Unicode arrow, non-modal scope, comma + `(active)` marker, `over` preposition), and the division of labour with DR-017 (refs vs. metadata) live in DR-016. |

### Action Label Algorithm (BR-06)

For each node, collect actions by running the following checks in order. All detection methods are defined in **architecture.md §§ 4–5**.

1. `supportsClick()` returns non-null → add `click` (covers both AccessibleAction and MouseListener fallback — see **architecture.md § 4 "Detecting Click Support"**). **Exception — `JMenu` (DR-012):** `supportsClick()` returns `null` for `JMenu`, so `click` is never emitted on a menu title. The menu's items remain directly clickable via their own refs.
2. `supportsTogglePopup()` → add `toggle_popup`
3. Iterate `AccessibleAction` descriptions; for each that equals a known constant (`AccessibleAction.INCREMENT`, `DECREMENT`, `TOGGLE_EXPAND`), normalize to lower-case underscore format and add it (`increment`, `decrement`, `toggle_expand`)
4. `supportsSetText()` **and** `AccessibleStateSet` contains `EDITABLE` → add `get_text`, `set_text`; else if `supportsSetText()` without `EDITABLE` (read-only text field) → add `get_text`, `set_text` (the `set_text` will be prefixed with `!` by BR-08 since the component is read-only); else `supportsGetText()` → add `get_text`. **Exception — password fields (DR-011):** if the accessible's role is `AccessibleRole.PASSWORD_TEXT`, `get_text` is suppressed in every branch. Password fields thus advertise `set_text` alone when editable and `!set_text` alone when non-editable — never `get_text`. Non-editable password fields retain their ref (see DR-011 "pathological case") so the AI can see the component exists. **Exception — labels (DR-015):** if the accessible's role is `AccessibleRole.LABEL`, `get_text` is suppressed regardless of whether `AccessibleText` is exposed. This covers `JLabel` (both plain and HTML-wrapped), `JList.AccessibleJListChild`, `JTree.AccessibleJTreeNode`, and any custom component adopting the LABEL role. The label's content is read from the name slot (uncapped per DR-014); no tool is needed.
5. `supportsGetValue()` → add `get_value`; additionally `supportsSetValue()` → add `set_value`
6. **Selection group labels:**
   - `supportsMultiSelection()` → add `multi-selection`
   - else `supportsSingleSelection()` → add `single-selection`
   
   These are **group labels**, not individual actions. The AI learns which individual selection tools are available from the tool descriptions (sent once at MCP session start). `single-selection` means `swing_get_selection`, `swing_set_selection`, `swing_clear_selection`, `swing_get_items`, `swing_get_item_count` are callable. `multi-selection` means all of those plus `swing_select_all`. See **architecture.md § 6 "Selection Action Groups"** for detection methods and `SwingUtils` API.

6b. **Content discovery (cells):** If the component's role is `LIST` or `TREE` (`isGetCellsSupported`) **and** its accessible children count exceeds `MAX_DATA_ROW_NODES` (i.e. the snapshot truncated its children) → add `get_cell_count`, `get_cells`. These are independent of selection — they operate in the accessible children index space for discovering content beyond the snapshot cap and returning refs for actionable children inside cell renderers. **JTable is excluded** from this rule: table cells are stamp-painted via `CellRendererPane` and surface as plain text `LABEL`s with no actions, so `get_cells` can never return an actionable ref on a JTable; the canonical row-access tool for JTable is `swing_get_items` (T-017 BR-09), which is always implicitly available via the `single-selection`/`multi-selection` group labels.

7. `supportsClose()` → add `close`

8. **Description retrieval (T-024).** If the component's resolved description (BR-10) was capped at 120 characters during render (i.e. the pre-cap sanitized description exceeded 120 chars) → add `get_description`. This is evaluated during or after Phase 4 (render), since capping occurs at render time. The `SnapshotNode` tracks whether its description was capped (e.g. via a boolean flag set during `calculateSelfLine`). `get_description` can be the **sole action** on a node — in that case the node receives a ref it would not otherwise have (e.g. a `JLabel` or `JPanel` with a long tooltip). The AI calls `swing_get_description` (T-024) to retrieve the full uncapped text.

**`get_cells` ref map replacement:** `swing_get_cells` assigns a fresh local ref numbering and **replaces the SwingMCP ref map** with only the refs visible in its output window. This is analogous to scrolling a JTable to a certain offset: children outside the `offset`/`length` window are not interactable. The AI must call `swing_snapshot` again to return to the full-tree ref map.

`getAccessibleActionDescription()` is **never** used to derive display labels directly — it is only compared against known constants in step 3.

**Why step 3 only matches known constants:** `JTextComponent` subclasses expose dozens of dynamic `AccessibleAction` descriptions derived from `Action.NAME` (e.g. `"cut-to-clipboard"`, `"paste-from-clipboard"`, `"select-all"`). These are deliberately ignored. The primary interaction for any text component is reading and writing its value via `get_text`/`set_text` (step 4). An AI agent filling a form will set field values and move on — it has no need to invoke cut, copy, paste, or select-all via the accessibility API.

> **`JListChild` action note:** `click` is always present on `JListChild` (via `AccessibleAction`) and is always legitimate — a list item can always be clicked. Selection group labels (`single-selection`, `multi-selection`) appear on a `JListChild` only when that child's `getAccessibleSelection()` is non-null, which occurs only in unusual cases where the cell renderer itself contains a selectable component (e.g. a nested `JList`). In that case the selection tools are also legitimate. No special-casing of `JListChild` is needed — the Action Label Algorithm handles it correctly.

### Design notes

**Why tree filtering replaced line-grep filtering (BR-09).**
The original `filter_substring` implementation was a simple post-render line grep: render the full tree, then return only lines whose text contained the substring. This caused two problems in practice:

1. **AI confusion loop.** The grep returned isolated lines without structural context — the AI could not tell *where* a matched component sat in the hierarchy. This led to repeated `swing_snapshot` calls with different filters, trying to orient itself.
2. **Missing children.** Filtering on a container (e.g. a JTable, JComboBox, or JList) returned the container's own line but dropped all its children (rows, items, entries). The result was structurally incomplete and misleading — the AI saw a table with no rows.

Tree filtering fixes both problems: ancestors give the AI a path from the root (orientation), and descendants give it the full content of matched containers (completeness). The `[filter active: …]` header line tells the AI that sibling branches were dropped, so it knows to re-snapshot without a filter if a complete tree is needed.

---

## Tests

> See `architecture.md` § Testing for conventions.

### Headless tests (`src/test`) — `SwingSnapshotToolTest`

In headless mode, use `JPanel` as the root instead of `JFrame`/`JDialog` (top-level windows require a display).

- [x] `SwingSnapshotToolTest`
  - [x] A simple hierarchy (panel with button and text field) produces a tree with correct roles, names, and refs.
  - [x] Refs are assigned starting from 1. Only nodes exposing at least one action (BR-06/BR-07) receive a ref — purely structural nodes (e.g. panels, labels) do not.
  - [x] Nested containers produce correctly indented output.
  - [x] Components with `setVisible(false)` are excluded from the tree, including all descendants (HE-1).
  - [x] Unnamed panels are transparently pruned — their children appear under the grandparent. "Unnamed" means no accessible name, no accessible description, no titled border (TP-5).
  - [x] Named panels (with accessible name, accessible description, or titled border) are kept in the tree.
  - [x] `CellRendererPane` instances and all their descendants are excluded (HE-2).
  - [x] Framework-internal roles (`root_pane`, `layered_pane`, `viewport`, `filler`) are transparently pruned — their children appear under the parent.
  - [x] `scroll_pane` is kept (even when unnamed); `viewport` inside it is pruned. Also implicitly exercises BR-11 plaf/JDK-internal stripping: scroll-bar arrow buttons (plaf `MetalScrollButton`/`BasicArrowButton`) render as bare `- JButton (push_button)`, and the JDK-internal nested `JScrollPane.ScrollBar` renders as `- JScrollBar (scroll_bar)` — plaf name and nested-class name both stripped by the display-class walk-up.
  - [x] A JTable with more than `MAX_DATA_ROW_NODES` rows is truncated with a `... and N more rows` summary node, and the JTable line does **not** advertise `get_cell_count`/`get_cells` (BR-06 step 6b — JTable excluded; `largeJTableIsTruncatedWithRowSummary` asserts the full first line).
  - [x] A truncated `JList` advertises `get_cell_count` and `get_cells` (BR-06 step 6b) — `largeJListAdvertisesGetCellsAndGetCellCount`.
  - [x] A truncated `JTree` advertises `get_cell_count` and `get_cells` (BR-06 step 6b) — `largeJTreeAdvertisesGetCellsAndGetCellCount`.
  - [x] Menu items appear in the tree even when the menu is not open.
  - [x] A `JMenu` renders without a `click` action and without a ref (DR-012). Its `JMenuItem` children still receive refs with `click`. Locked in by `jMenuAppearsAsMenu` / `jMenuBarAppearsAsMenuBar` / `jMenuItemAppearsAsMenuItem` / `menuItemsAppearEvenWhenMenuIsClosed`.
  - [x] HE-5 prune (positive case, headless): a `JPopupMenu` whose `getInvoker()` is a `JMenu` is dropped from the snapshot — `jPopupMenuWithJMenuInvokerIsPruned`.
  - [x] HE-5 prune (negative case / regression guard, headless): a `JPopupMenu` whose `getInvoker()` is a `JButton` is NOT dropped — `contextJPopupMenuWithNonJMenuInvokerIsNotPruned`.
  - [x] JTabbedPane shows tab items; selected tab has `SELECTED` state; non-selected tab content is not included.
  - [x] JTabbedPane tabs render with 0-based index: `- (page_tab) N "title"` (SC-2; BR-11 Case C). Locked in by `tabbedPane_fourTabs_indicesAscendFromZero` plus the multi-tab / selected-tab / nested-content tests.
  - [x] A tab disabled via `setEnabledAt(i, false)` renders as `[disabled]` on the `page_tab` line (`tabbedPane_tabDisabledViaSetEnabledAt_marksOnlyThatTabDisabled` — commit 043a71b).
  - [x] A child button on a disabled-but-selected tab retains unprefixed `click` and no `[disabled]` state (`tabbedPane_buttonOnDisabledTab_isStillClickable` — commit 043a71b).
  - [x] Only meaningful states are shown (e.g., `disabled` appears, `visible`/`enabled` do not).
  - [x] Disabled components appear in the tree with `disabled` state. The `disabled` state is derived from `SwingUtils.isEffectivelyEnabled()` (architecture.md § 4).
  - [x] Virtual accessible children of a disabled `JTable` (cells) inherit the host's disabled state (architecture.md § 4 Quirk 2). Verified at the `SwingUtils` level by `SwingUtilsIsEffectivelyEnabledTest.jTable_virtualCell_disabledWhenTableDisabled` — the snapshot derives `[disabled]` directly from that API.
  - [x] Disabled button shows `!click` (mutation action prefixed with `!`). Read-only actions and selection group labels are never prefixed.
  - [x] Disabled slider shows `!increment`, `!decrement`, `get_value`, `!set_value` (read-only actions unprefixed).
  - [x] Read-only text field shows `get_text, !set_text`.
  - [x] An editable `JPasswordField` shows `actions: set_text` — `get_text` is suppressed per DR-011.
  - [x] A non-editable `JPasswordField` (`setEditable(false)`) shows `actions: !set_text` only and retains its ref (DR-011 pathological case).
  - [x] A custom component whose `AccessibleContext` returns role `PASSWORD_TEXT` (without extending `JPasswordField`) also has `get_text` suppressed (role-based gate, DR-011).
  - [x] A `JTextField` containing `"admin"` renders with `text="admin"` between the states bracket and `actions:` (BR-12 / DR-013) — `br12_jTextFieldWithContent_rendersInlineTextPreview`.
  - [x] A `JTextField` with a 30-character value renders with `text="<first 14 chars>…"` — 15-char cap, DR-010 truncation convention (BR-12) — `br12_jTextFieldLongerThanCap_truncatesTo14CharsPlusEllipsis`.
  - [x] A `JTextField` with a 15-character value renders the full value (no `…`) — boundary case (BR-12) — `br12_jTextFieldAt15Chars_rendersFullValue`.
  - [x] An empty `JTextField` renders with `text=""` — null/empty content normalised (BR-12) — `br12_emptyJTextField_rendersEmptyStringPreview`.
  - [x] A `JTextArea` containing `"line one\nline two"` renders with `text="line one line…"` — newlines and whitespace runs collapsed to single spaces before truncation; trailing whitespace inside the cap window is stripped before the ellipsis (BR-12) — `br12_jTextAreaWithNewlines_collapsesWhitespaceBeforeTruncation`.
  - [x] A `JPasswordField` does **not** emit `text="..."` — BR-12 gate-parity with `supportsGetText()` excludes `PASSWORD_TEXT` for free (DR-011 alignment) — `br12_editableJPasswordField_doesNotEmitTextPreview`.
  - [x] A `JSlider` at value 42 renders with `value=42` between the states bracket and `actions:` (BR-12 / DR-013) — `br12_jSliderWithValue_rendersInlineValuePreview`.
  - [x] A `JSpinner` holding `Double(3.5)` renders with `value=3.5` (fractional numbers keep the decimal point per DR-010's number convention) — `br12_jSpinnerWithFractionalValue_rendersDecimalValue`.
  - [x] A `JProgressBar` at 37/100 renders with `value=37/100` — `PROGRESS_BAR` renders `current/max` when `getMaximumAccessibleValue()` is non-null (BR-12 progress-bar exception) — `br12_jProgressBarWithMax_rendersCurrentOverMax`.
  - [x] A `JProgressBar` whose `getMaximumAccessibleValue()` returns `null` renders bare `value=N` (BR-12 progress-bar fallback) — `br12_jProgressBarWithNullMax_rendersBareValue`.
  - [x] A `JCheckBox` does **not** emit `value=N` or `text="..."` — neither gate fires; `[checked]` carries the signal (BR-12) — `br12_jCheckBox_emitsNoInlinePreview`.
  - [x] A `JButton` does **not** emit `value=` or `text=` — neither gate fires (BR-12) — `br12_jButton_emitsNoInlinePreview`.
  - [x] A component whose `AccessibleText.getCharCount()` / `getAtIndex` throws at snapshot time still produces a rendered line for the component — the `text="..."` annotation is omitted and the rest of the tree still renders (BR-12 defensive read) — `br12_defensiveRead_throwingAccessibleText_omitsAnnotationKeepsNode`.
  - [x] `SwingUtils.readText(Accessible, int)` returns `""` for null content and for components without `AccessibleText` (regression guard — BR-12 relies on null-normalisation) — `SwingUtilsSupportsTextTest.readText_*`.
  - [x] Enabled button inside a disabled `JPanel` is NOT marked `disabled` and shows unprefixed `click` — Swing's `setEnabled(false)` does not propagate to children.
  - [x] Disabled component with only `!`-prefixed actions still receives a ref.
  - [x] When two roots are provided, their trees are separated by a `---` line and refs are numbered globally (not reset between roots).
  - [x] Calling `swing_snapshot` via the MCP client returns a valid text response.
  - [x] `filter_substring` applies tree filtering: matched nodes plus ancestors and descendants are included; non-matching siblings are dropped.
  - [x] Filtered output starts with `[filter active: only nodes matching "<filter>" and their ancestors/descendants are shown]`.
  - [x] `filter_substring` with no matches returns a descriptive message.
  - [x] `filter_substring` does not affect ref numbering — a filtered component has the same ref as in the unfiltered snapshot.
  - [x] `filter_substring` drops root separators (`---`) from the output.
  - [x] Omitting `filter_substring` (or passing empty/null) returns the full unfiltered snapshot.
  - [x] Ancestors of a matched node appear in the output (structural path from root), but their non-matching children are omitted.
  - [x] All descendants of a matched node appear unconditionally (e.g. table rows under a matched table).
  - [x] An unnamed JPanel with an application `MouseListener` appears in the snapshot with `click` action and a ref.
  - [x] An unnamed JPanel with only framework `MouseListener`s (e.g. from setting a tooltip) is pruned as usual.
  - [x] A JButton (which has AccessibleAction click) with an additional application `MouseListener` shows `click` once (Tier 1 wins).
  - [x] A disabled component with an application `MouseListener` shows `!click` (mutation action prefix applies).
  - [x] A component with an interactive role (e.g. `JSlider`) and an application `MouseListener` but no AccessibleAction click does NOT get a `click` action from Tier 2 (interactive role exclusion).
  - [x] A JTable inside a JScrollPane shows `columns: [Col1, Col2]` on the table node line, after the bracket and before `actions:` (SC-6).
  - [x] A JTable NOT inside a JScrollPane does NOT show `columns:` on the table node line (SC-6).
  - [x] JTable children are rendered as pipe-separated row lines with 0-based index, not individual cell labels (SC-6).
  - [x] JTable truncation summary reads `... and N more rows` (SC-6).
  - [x] The JTableHeader panel is suppressed from the snapshot when the table is in a JScrollPane (SC-7).
  - [x] A component with no `accessibleDescription` and a plain-text tooltip renders that tooltip in the description slot (BR-10).
  - [x] A component with a non-blank `accessibleDescription` ignores the tooltip (BR-10 fallback order).
  - [x] A component with an `<html>...` tooltip renders cleaned plain text in the description slot — tags stripped to spaces, `&amp;`/`&lt;`/`&gt;`/`&nbsp;` decoded, whitespace collapsed (BR-10).
  - [x] A component with a tooltip longer than 120 characters renders the first 120 chars followed by `…` (U+2026) in the description slot (BR-10).
  - [x] A component with an `accessibleDescription` longer than 120 characters renders the first 120 chars followed by `…` (BR-10 — symmetric cap).
  - [x] A `JTabbedPane` tab with `setToolTipTextAt(i, "…")` and no per-tab description renders the tab tooltip in the description slot of the `page_tab` line (BR-10).
  - [x] A tab-content `JComponent` inside a `JTabbedPane` uses its own tooltip (or none) — never the surrounding tab's tooltip — for description fallback (BR-10 regression guard).
  - [x] A tooltip such as `"List<String>"` (no `<html>` prefix) is rendered verbatim in the description slot — angle brackets are preserved (BR-10).
  - [x] Regression guard: per-cell / per-row / per-node / per-item tooltips on `JTable`, `JList`, `JTree`, `JTableHeader` (delivered via the `MouseEvent`-aware `getToolTipText(MouseEvent)` overload) are **not** surfaced in the snapshot (BR-10). The snapshot walker has no `MouseEvent` and `SwingUtils#getTooltipAsText` calls the no-arg `getToolTipText()` — a future change that synthesised a fake `MouseEvent` to probe per-cell tooltips would bloat snapshots with hundreds of strings per data component. `br10_perCellPerRowPerNodePerItemTooltips_notSurfacedInSnapshot` sets a distinct sentinel on each of the four component types via the MouseEvent overload and asserts none appear in the output.
  - [x] A standard `JButton` renders as `- JButton (push_button) "Save" ...` (BR-11 Case A) — `br11_caseA_standardJButton_rendersJClassRole`.
  - [x] A user subclass `class FancyButton extends JButton {}` renders as `- FancyButton -> JButton (push_button) ...` (BR-11 Case B) — `br11_caseB_userSubclassOfJButton_usesArrowToJButton`.
  - [x] An anonymous subclass `new JButton("X") {}` renders as `- JButton (push_button) "X" ...` — anonymous class stripped (BR-11 strip rule) — `br11_anonymousJButtonSubclass_strippedToJButton`.
  - [x] A user subclass extending an abstract Swing class — `class BareButton extends AbstractButton` — renders as `- BareButton -> AbstractButton (push_button) ...` (BR-11 — abstract classes qualify) — `br11_caseB_userSubclassOfAbstractButton_usesArrowToAbstractButton`.
  - [x] A user subclass extending a `javax.swing.plaf.*` L&F class (e.g. `class MyArrow extends BasicArrowButton`) walks past the plaf class to the real Swing widget: `- MyArrow -> JButton (push_button) ...` (BR-11 — `javax.swing.plaf.*` excluded from qualifying) — `br11_caseB_userSubclassOfPlaf_walksPastPlafToJButton`.
  - [x] A class with a simulated proxy-style name (contains `$$`, null `getEnclosingClass()`) is stripped; walk-up starts at its superclass — `br11_runtimeProxyWithDoubleDollarName_isStrippedToRealSuperclass` uses ByteBuddy (test scope) to synthesise a top-level class named `com.vaadin.swingmcp.test.FancyButton$$EnhancerByCGLIB$$abc123` extending `FancyButton`. Third-party subclasses (e.g. SwingX `JXTable extends JTable`) render identically to user subclasses — equivalent by construction, since the resolver does not inspect the package (covered by the user-subclass tests above).
  - [x] A `JTabbedPane` tab renders with `(page_tab)` as its identity slot (no class prefix) — BR-11 Case C — `br11_caseC_jTabbedPaneTab_rendersAsParensPageTab`.
  - [x] A `JList` item renders with `(label)` as its identity slot — BR-11 Case C — `br11_caseC_jListItem_rendersAsParensLabel`.
  - [x] A `JTree` non-leaf node renders with `(label)` as its identity slot — BR-11 Case C — `br11_caseC_jTreeNode_rendersAsParensLabel`.
  - [x] A named `JPanel` renders as `- JPanel (panel) "Details"` (BR-11 Case A) — `br11_caseA_namedJPanel_rendersJClassRoleAndQuotedName`.
  - [x] `JScrollPane` with no accessible name renders as `- JScrollPane (scroll_pane)` — no empty quotes after the identity slot — `br11_caseA_unnamedJScrollPane_hasNoEmptyQuotesAfterIdentitySlot`.
  - [x] **BR-13 / DR-014 sanitization — name slot.** A `JButton` whose accessible name contains `\n` renders on a single line with the newline collapsed to a space (`"Save Changes"`, not two lines) — `br13_buttonNameWithNewline_collapsesToSingleSpace`.
  - [x] **BR-13 / DR-014 sanitization — embedded quotes.** A `JButton` whose accessible name is `Click "here"` renders as `"Click \"here\""` with quotes escaped — `br13_buttonNameWithEmbeddedQuote_escapesAsBackslashQuote`.
  - [x] **BR-13 / DR-014 sanitization — description slot.** A component with `accessibleDescription` containing `\n` or `"` is rendered sanitized in the description slot — `br13_descriptionWithNewline_collapsesToSingleSpace` and `br13_descriptionWithEmbeddedQuote_escapesAsBackslashQuote`.
  - [x] **BR-13 / DR-014 sanitization — text preview.** A `JTextField` whose content contains `"` renders the preview with escaped quotes (`text="say \"hi\""`) — `br13_textPreviewWithEmbeddedQuote_escapesAsBackslashQuote`.
  - [x] **BR-13 / DR-014 name uncapped.** A `JLabel` with a 300-character accessible name renders the full name in the name slot — not truncated — `br13_longLabelName_rendersFullNameUncapped`.
  - [x] **DR-015 LABEL suppresses `get_text` — plain `JLabel`.** A plain `JLabel` has no `[ref=N]`, no `actions: get_text`, and no `text="..."` preview — `dr015_plainJLabel_hasNoRefNoGetTextNoPreview`.
  - [x] **DR-015 LABEL suppresses `get_text` — HTML `JLabel`.** A `JLabel("<html>Hello <b>world</b></html>")` has no ref, no `get_text` action, and no `text="..."` preview despite `AccessibleText` being exposed by the HTML view — `dr015_htmlJLabel_hasNoRefNoGetTextNoPreview`.
  - [x] **DR-015 LABEL regression — JList cells keep `click` ref.** A `JList` item still receives a ref for its `click` action even after LABEL is excluded from `get_text` — `dr015_jListCell_retainsClickRef` (regression guard against accidentally removing refs for list/tree items).
- [x] `ComponentClassResolverTest` — direct unit tests of the BR-11 identity-slot algorithm (steps 1–4 of Component Identity Resolution): standard Case A widgets, Case B custom subclasses (`customJButtonSubclass_rendersAsCaseB`, `customJTextFieldSubclass_rendersAsCaseB`), abstract-class walk-up, anonymous/local stripping, runtime-proxy stripping, plaf rejection, Case C non-Component accessibles (`JTabbedPane.Page`, `JList.AccessibleJListChild`), and the qualifying-ancestor predicate's accept/reject behaviour for each of the BR-11 criteria. End-to-end snapshot rendering is covered by the `br11_*` cases in `SwingSnapshotToolTest` above.
- [x] `ComponentClassResolverAuditTest` — enumerates `javax.swing.*` (excluding `javax.swing.plaf.*`) and `java.awt.*`, applies the BR-11 qualifying-ancestor predicate, and asserts the resulting class set equals a checked-in fixture. Failing on JDK drift forces a deliberate fixture update.

### Screen-mode tests (`src/testSwing`) — `SwingSnapshotToolWithScreenTest`

Uses real `JFrame`/`JDialog` instances on an actual display. The snapshot tool is called with the frame or dialog as the considered component.

- [x] A visible `JFrame` with child components produces a snapshot tree rooted at the frame's content (framework-internal wrappers pruned).
- [x] A visible `JDialog` with child components produces a snapshot tree rooted at the dialog's content (framework-internal wrappers pruned).
- [x] A visible `JInternalFrame` inside a `JDesktopPane` (inside `JFrame`) produces a snapshot subtree for the internal frame with correct roles, names, and refs.
- [x] A `JDesktopPane` with multiple `JInternalFrame`s shows all internal frames in the snapshot.
- [x] HE-5 end-to-end (screen): when a `JMenu`'s popup is opened via real Swing mechanics (`JMenu.doClick()` on a visible `JFrame`), the resulting `JPopupMenu` node is pruned and the menu's `JMenuItem` children appear only once — under the `JMenu`, not duplicated under a sibling `JPopupMenu`. The `JMenu` carries `[selected, checked]` state to signal the popup is open. `openJMenuPopup_doesNotDuplicateItems_HE5`.
- [x] **DR-017 amendment — combo box popup deduplication (screen).** When a `JComboBox` popup is open and all visible windows are collected as roots (simulating production `getConsideredComponents()`), the heavyweight `JWindow` popup container is excluded. The `JPopupMenu`/`JList` appears only once — nested inside the `JComboBox` — not duplicated as a standalone root. `openComboPopupShouldNotDuplicateInSnapshot` (in `SwingTogglePopupScreenTest`).
- [x] **BR-14 / DR-016 — single modal over frame.** A modal `JDialog` owned by a visible `JFrame` emits `[modal stack (2, topmost first): JDialog "X" / JFrame "Y"]` as the first line above the dialog's tree — `dr016_singleModalOverFrame_emitsTwoEntryHeader`.
- [x] **BR-14 / DR-016 — nested modals.** A modal `JDialog` owned by another modal `JDialog` owned by a `JFrame` emits `[modal stack (3, topmost first): JDialog "X" / JDialog "Y" / JFrame "Z"]` — chain walked via `getOwner()` per visible-filter rule — `dr016_nestedModalsOverFrame_emitsThreeEntryHeader`. Exercised via `FakeSwingMCP.setConsideredComponents` pointing at the topmost modal, since live `SwingMCP.getConsideredComponents()` would have returned only the topmost per DR-017 (which is still the realistic case; the test validates the chain-walk rendering, not the enumeration).
- [x] **BR-14 / DR-016 — hidden shared frame owner.** `JOptionPane.showMessageDialog(null, ...)` produces a modal whose `getOwner()` is Swing's shared hidden frame (`isVisible() == false`). The visibility filter drops it, leaving the chain at length 1 → **no header emitted** — `dr016_modalWithHiddenSharedFrameOwner_emitsNoHeader`.
- [x] **BR-14 / DR-016 — frame root has no header.** A standalone `JFrame` root (no owner) emits no header — `dr016_jFrameRoot_emitsNoHeader`.
- [x] **BR-14 / DR-016 — non-modal dialog has no header.** A non-modal `JDialog` owned by a visible `JFrame` emits no header (modal-only scope per DR-016) — `dr016_nonModalDialog_emitsNoHeader`.
- [x] **BR-14 / DR-016 — concrete class resolution.** A `LoginDialog extends JDialog` renders as `LoginDialog "X"` in the chain entry (concrete simple name, BR-11 strip rules applied) — `dr016_customSubclassModal_usesConcreteSimpleName`.
- [x] **BR-14 / DR-016 — title sanitisation, newline.** A modal whose title contains `\n` renders the chain entry with the newline collapsed to a single space (BR-13 shared sanitiser) — `dr016_titleWithNewline_collapsedInHeader`.
- [x] **BR-14 / DR-016 — title sanitisation, embedded quote.** A modal whose title contains `"` renders the chain entry with the quote escaped as `\"` (BR-13) — `dr016_titleWithEmbeddedQuote_escaped`.
- [x] **BR-14 / DR-016 — blank title.** A modal whose `AccessibleContext.getAccessibleName()` is null/blank renders the chain entry as bare `JDialog` (no quoted slot) — `dr016_modalWithBlankTitle_omitsQuotedSlot`.
- [x] **BR-14 / DR-016 — filter drops header.** Under `filter_substring` matching a node inside the modal, the output begins with the `[filter active: ...]` notice and does **not** include the modal-stack header — `dr016_headerDroppedFromFilteredOutput`.
- [x] **BR-14 / DR-016 — per-root emission.** Via `FakeSwingMCP.setConsideredComponents` with a modal dialog and an unrelated `JFrame` as two independent roots, only the modal root carries a header; the `---` separator is unchanged; ref numbering is globally sequenced as before — `dr016_mixedRoots_headerOnlyOnModalRoot`.
- [x] **SC-8 / DR-019 — iconified JFrame children suppressed.** A decorated `JFrame` with child components (e.g. a `JButton` and a `JTextField`) is shown on screen, then iconified via `frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED)`. After the state change settles (poll via `awaitExtendedState`), the snapshot shows the Frame node with `[iconified]` and `actions: restore, close` (with a ref), but its children are replaced by the placeholder line `[Contents hidden — window is iconified. Call swing_restore to interact with this window.]`. The children receive no refs — `sc8_iconifiedJFrame_childrenSuppressedWithPlaceholder`.
- [x] **SC-8 / DR-019 — iconified JFrame children get no refs.** In the same iconified scenario, calling a tool with a ref that pointed at a child component in the pre-iconify snapshot returns a stale-ref error (the ref was never assigned) — `sc8_iconifiedJFrame_childRefsNotAssigned`.
- [x] **SC-8 / DR-019 — restored JFrame children reappear.** After restoring the iconified frame via `swing_restore`, the next snapshot shows the full child tree with refs, no placeholder — `sc8_restoredJFrame_childrenReappear`.
- [x] **SC-8 / DR-019 — iconified JFrame with second normal JFrame.** Two JFrames as roots: one iconified, one normal. The iconified frame shows the placeholder; the normal frame shows its full child tree. Refs are assigned only to the normal frame's children and the iconified frame node itself — `sc8_mixedIconifiedAndNormalFrames_refsOnlyOnNormal`.
- [x] **SC-8 / DR-019 — does not apply to JInternalFrame.** An iconified `JInternalFrame` inside a `JDesktopPane` is handled by SC-5 (becomes a `JDesktopIcon`), not SC-8. The `JDesktopIcon` node appears as before — `sc8_iconifiedJInternalFrame_handledBySC5NotSC8`.
