# UC-002: swing_snapshot

---

**As an** AI agent, **I want to** obtain an accessibility tree snapshot of the Swing application **so that** I can understand the current UI structure and identify components for interaction.

**Status:** Implemented
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_snapshot` tool with an optional `filter_substring` parameter.
- The tool walks the `javax.accessibility` tree of each component returned by `SwingToolContext.getConsideredComponents()` (usually a Window or JFrame, but during testing it could be any component). Each considered component is an independent root — no component in the list is nested inside another.
- The tree walker applies the **Snapshot Inclusion Rules** (below) to decide which nodes appear in the output and which are pruned.
- The tool returns a compact indented text tree with each node showing role, name, states, and available actions.
  - Nodes that expose at least one `AccessibleAction` also receive a numeric ref.
  - Only include accessibility name and description if those are not blank. Description follows the name in the line format.
- If `filter_substring` is provided, the rendered output is post-filtered: only lines whose text contains the substring (case-insensitive) are returned. Root separators (`---`) are dropped from filtered output. If no lines match, a short message is returned instead of an empty string.

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

After pruning:
```
- frame "Invoice Editor"
  - tool_bar "Main"
    - push_button "Save" [ref=1] actions: click
  - split_pane
    - label "Invoices"
    - scroll_pane
      - list [ref=2] actions: multi-selection, get_cell_count, get_cells
    - panel "Details"
      - text "Name" [ref=3] actions: get_text, set_text
    - push_button "Add" [ref=4] actions: click
```

### Stage 3 — Always Included

After Stages 1 and 2, any surviving node is included. The following criteria serve as a safety net — a node matching any of these must never be pruned, even if a future rule change would otherwise drop it:

| ID | Criterion | Swing Mechanism |
|----|-----------|-----------------|
| AI-1 | Has a non-structural accessible role | See **semantic roles** list below |
| AI-2 | Has an accessible name | `getAccessibleName()` non-null and non-empty |
| AI-3 | Has at least one action | Any action detected by the BR-06 algorithm (click, toggle_popup, increment, decrement, toggle_expand, get_text, set_text, get_value, set_value, single-selection, multi-selection, get_cell_count, get_cells, close) |
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
| SC-2 | **JTabbedPane: include only the selected tab's content.** Walk `getAccessibleChild()` naturally — it exposes the tab items (`PAGE_TAB`) and the selected tab's content panel. Mark the selected tab via `SELECTED` state. | The user can only see and interact with the selected tab's content; non-selected content is not accessible via the standard accessibility API anyway. |
| SC-3 | **Large data components (JTable, JList, JTree): truncate to first N accessible children** where N is a static final constant in the tool class (initially **10**). When truncated, Phase 4 emits a render-only `... and X more items` summary line (not a `SnapshotNode`). | A table with thousands of rows would blow up the AI context window. 10 rows gives enough structural overview. |
| SC-4 | **Disabled components: included with `disabled` state.** The `disabled` state is derived from `SwingUtils.isEffectivelyEnabled()` (parent-chain walk), not from the component's own `AccessibleStateSet`. This ensures that a locally-enabled component inside a disabled ancestor is correctly marked `disabled`. | The AI needs to see disabled components to understand the full UI. Using `isEffectivelyEnabled()` is consistent with the check mutation tools perform — if a tool call would fail with "disabled", the snapshot already shows it. |
| SC-5 | **JInternalFrame: include all, mark iconified ones** via `ICONIFIED` state. | Iconified internal frames are minimized but not invisible. |
| SC-6 | **JTable: row-based rendering with column headers.** When the table header is visible (`SwingUtils.isTableHeaderVisible()`), append `columns: Col1, Col2, …` to the table's node line using column names from `SwingUtils.getTableColumnNames()` (display order, respects user column reordering). Table children are rendered as pipe-separated rows via `SwingUtils.buildTableRowText()` instead of individual cell labels. Each row line is `- row N: Val1 \| Val2 \| Val3` where N is the **0-based row index** (consistent with selection tool indices). Truncation is by **rows** (not cells): `… and N more rows`. | Flat cell labels are ambiguous — the AI must guess column boundaries. Row-based output is unambiguous, more compact, and consistent with row-based selection (UC-014). Column headers tell the AI what each column means without having to infer it from data. 0-based indices let the AI pass row numbers directly to selection tools without conversion. |
| SC-7 | **JTableHeader: suppress from snapshot.** The `JTableHeader` component (which Swing renders as a panel with label children inside the JScrollPane's column header viewport) is suppressed from the snapshot tree. Its information is redundant with the `columns:` annotation on the JTable node (SC-6). | Without suppression, the AI sees column names twice — once in the `columns:` annotation and once in the header panel. The header panel's structure (panel → labels) is a Swing rendering artifact, not semantic content. |

### Accessible States — Display Rules

Which states from `AccessibleStateSet` appear in the snapshot output:

**Included (meaningful for AI understanding):**
`FOCUSED`, `SELECTED`, `CHECKED`, `EXPANDED`, `COLLAPSED`, `MODAL`, `MULTI_LINE`, `ICONIFIED`, `HORIZONTAL`, `VERTICAL`, `BUSY`, `INDETERMINATE`

**Synthetic states (derived, not from `AccessibleStateSet` directly):**
`DISABLED` — emitted when `SwingUtils.isEffectivelyEnabled()` returns `false`. This walks the accessible parent chain, so a locally-enabled component inside a disabled ancestor is correctly marked `disabled`. This is consistent with the check that mutation tools perform at execution time.

`READ_ONLY` — emitted for text components that expose `AccessibleEditableText` but lack the `EDITABLE` state in their `AccessibleStateSet` (e.g. `JTextField` with `setEditable(false)`). Most text fields are editable by default, so the absence of `read_only` means editable — no `editable` flag is shown. This keeps the snapshot concise: only the exceptional read-only case is annotated.

**Omitted (noise or always-true for included nodes):**
`VISIBLE`, `SHOWING`, `ENABLED` (superseded by synthetic `DISABLED` derived from `isEffectivelyEnabled()`), `EDITABLE` (default for text fields — only show its absence as `READ_ONLY`), `OPAQUE`, `RESIZABLE`, `ARMED`, `TRANSIENT`, `MANAGES_DESCENDANTS`

---

## Swing Component Reference

Quick-reference mapping every common Swing component to its `AccessibleRole`, whether it
survives pruning, and what it looks like in the snapshot output.

### Top-Level Containers

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JFrame` | `FRAME` | No | `- frame "My App" [ref=1] actions: close` |
| `JDialog` | `DIALOG` | No | `- dialog "Confirm" [ref=1, modal] actions: close` |
| `JInternalFrame` | `INTERNAL_FRAME` | No | `- internal_frame "Document" [ref=1] actions: close` |
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
| `JPanel` (named / titled border) | `PANEL` | No | `- panel "Details"` |
| `JScrollPane` | `SCROLL_PANE` | No | `- scroll_pane` |
| `JSplitPane` | `SPLIT_PANE` | No | `- split_pane` |
| `JTabbedPane` | `PAGE_TAB_LIST` | No | `- page_tab_list "General" [ref=1] actions: single-selection` |
| *(tab within JTabbedPane)* | `PAGE_TAB` | No | `- page_tab "General" [selected]` |
| `JToolBar` | `TOOL_BAR` | No | `- tool_bar "Main"` |
| `JOptionPane` | `OPTION_PANE` | No | `- option_pane` |

### Buttons

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JButton` | `PUSH_BUTTON` | No | `- push_button "Save" [ref=1] actions: click` |
| `JToggleButton` | `TOGGLE_BUTTON` | No | `- toggle_button "Bold" [ref=1] actions: click` |
| `JCheckBox` | `CHECK_BOX` | No | `- check_box "Remember me" [ref=1, checked] actions: click` |
| `JRadioButton` | `RADIO_BUTTON` | No | `- radio_button "Option A" [ref=1] actions: click` |

### Text Input

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JTextField` | `TEXT` | No | `- text "Name" [ref=1, editable] actions: get_text, set_text` |
| `JPasswordField` | `PASSWORD_TEXT` | No | `- password_text "Password" [ref=1, editable] actions: get_text, set_text` |
| `JTextArea` | `TEXT` | No | `- text "Notes" [ref=1, editable, multi_line] actions: get_text, set_text` |
| `JEditorPane` | `TEXT` | No | `- text "Content" [ref=1, editable, multi_line] actions: get_text, set_text` |

### Selection & Data Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JComboBox` | `COMBO_BOX` | No | `- combo_box "Country" [ref=1, collapsed] actions: toggle_popup, single-selection` |
| `JList` | `LIST` | No | `- list [ref=1] actions: multi-selection, get_cell_count, get_cells` |
| *(child of JList)* | `LABEL` | No | `  - label "Item 1" [ref=2] actions: click` |
| `JTree` | `TREE` | No | `- tree [ref=1] actions: get_cell_count, get_cells` |
| *(non-leaf tree node)* | varies | No | `  - label "Folder" [ref=2] actions: toggle_expand, click` |
| `JTable` | `TABLE` | No | `- table [ref=1] columns: ID, Name, City actions: multi-selection, get_cell_count, get_cells` (with row children: `- row 0: 1 \| Alice \| NY`) |

### Value Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JSlider` | `SLIDER` | No | `- slider "Volume" [ref=1, horizontal] actions: increment, decrement, get_value, set_value` |
| `JSpinner` | `SPIN_BOX` | No | `- spin_box "Quantity" [ref=1] actions: increment, decrement, get_value, set_value` |
| `JProgressBar` | `PROGRESS_BAR` | No | `- progress_bar "Loading" [horizontal] actions: get_value` |

### Display Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLabel` | `LABEL` | No | `- label "Status: OK"` |
| `JToolTip` | `TOOL_TIP` | No | `- tool_tip "Click to save"` |
| `JSeparator` | `SEPARATOR` | No | `- separator` |
| `JScrollBar` | `SCROLL_BAR` | No | `- scroll_bar [ref=1, vertical] actions: increment, decrement, get_value, set_value` |

### Menu Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JMenuBar` | `MENU_BAR` | No | `- menu_bar` |
| `JMenu` | `MENU` | No | `- menu "File" [ref=1] actions: click` |
| `JMenuItem` | `MENU_ITEM` | No | `- menu_item "Open" [ref=2] actions: click` |
| `JCheckBoxMenuItem` | `CHECK_BOX` | No | `- check_box "Word Wrap" [ref=3, checked] actions: click` |
| `JRadioButtonMenuItem` | `RADIO_BUTTON` | No | `- radio_button "Light Theme" [ref=4] actions: click` |
| `JPopupMenu` | `POPUP_MENU` | No | `- popup_menu` |

**Notes:**
- `JLabel` has no actions and therefore no ref — it appears in the snapshot for context but is not interactable.
- `JProgressBar` exposes `get_value` but not `set_value` (read-only value role).
- `JCheckBoxMenuItem` and `JRadioButtonMenuItem` share roles with their non-menu counterparts (`CHECK_BOX`, `RADIO_BUTTON`).
- `JTextArea` and `JEditorPane` share the `TEXT` role with `JTextField` but include the `multi_line` state.
- Snapshot examples show typical states; actual output depends on the component's runtime configuration.
- Mutation actions prefixed with `!` are unavailable because the component is disabled or read-only. For example, a disabled button shows `actions: !click`; a read-only text field shows `actions: get_text, !set_text`.

---

## Implementation Notes

### Internal Representation: `SnapshotNode`

Because Swing's accessibility tree is read-only (owned by the framework), the tool builds its own mutable tree of `SnapshotNode` instances. Each node holds a reference to an `Accessible` (from which `AccessibleContext` is retrieved on demand via `getAccessibleContext()`) and an ordered list of `SnapshotNode` children. The node is the natural home for all pruning and ref-assignment logic.

### Role and State Name Resolution

`AccessibleRole` and `AccessibleState` are classes with public static final instances, not enums, so there is no built-in way to get a field name from an instance. For both, a static `Map<T, String>` is populated once at class-load time via reflection over the class's declared fields, mapping each instance to its field name lowercased (e.g. `PUSH_BUTTON` → `"push_button"`, `DISABLED` → `"disabled"`). Unknown instances (custom subclasses) fall back to `"unknown"`. These maps live in a dedicated utility class (e.g. `AccessibleNames`).

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
- SC-3: cap large-data components (`JTable`, `JList`, `JTree`) at `MAX_DATA_CHILDREN` accessible children; only the first `MAX_DATA_CHILDREN` children become `SnapshotNode`s. The `... and N more items` summary is a render-only artifact emitted by Phase 4 — it is never a `SnapshotNode` and can never receive a ref. For JTable, the cap is applied to **rows** (not cells) — see SC-6.

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
A depth-first traversal that serialises each node to a line of text per BR-03, using indentation depth to represent the tree structure. After rendering the last `SnapshotNode` child of a truncated large-data component, emits a synthetic `... and N more items` line (not a node — no ref, no pruning). For JTable, Phase 4 applies SC-6: if the header is visible, appends `columns: …` to the table node line; children are rendered as pipe-separated row lines via `SwingUtils.buildTableRowText()` instead of individual cell labels; truncation counts rows (`… and N more rows`). When multiple roots are present, their rendered trees are separated by a `---` line.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Refs are short integers starting from 1, assigned fresh with each snapshot call, globally across all roots. Only nodes that expose at least one action under the BR-06/BR-07 algorithm receive a ref. |
| BR-02 | The entire four-phase pipeline runs on the EDT via `runInEDT()` (which uses `SwingUtilities.invokeLater()` + a `CountDownLatch`). All phases — including prune, assignRefs, and render — execute inside that single call. Off-EDT optimisation is deferred until a performance problem is demonstrated. Tool calls always arrive from an HTTP thread (via `TinyMCPServer`) — never from the EDT — so `runInEDT()` will never deadlock. Do **not** add EDT detection (`SwingUtilities.isEventDispatchThread()`) as a "helpful" fallback; it would mask bugs and is not needed. |
| BR-03 | The output format is a compact indented text tree (not YAML), mimicking Playwright MCP. Line format: `- role "name" "description" [ref=N, state1, state2] actions: action1, !action2`. Role is the `AccessibleRole` field name lowercased with underscores (e.g. `push_button`, `text`, `scroll_pane`) — never `toDisplayString()`, which is locale-sensitive. Omit `"name"` if blank; omit `"description"` if blank. Ref and states share one bracket, comma-separated, lowercase. Omit the bracket entirely if there is no ref and no states. Omit `actions:` if none. Mutation actions that would fail validation are prefixed with `!` (see BR-08). Field values (`AccessibleText` content, `AccessibleValue`) are **not** shown in the output — only name and description are shown, consistent with Playwright MCP's approach. Revisit if the AI needs field values in future. |
| BR-04 | The tree walker walks the `javax.accessibility` tree via `AccessibleContext.getAccessibleChild(i)`, **not** the `Component.getComponents()` component tree. The accessibility tree provides virtual children for complex components (table cells, list items, tree nodes). |
| BR-05 | Large data components (JTable, JList, JTree) are truncated to `MAX_DATA_CHILDREN` accessible children (static final constant, initially 5). When truncated, a synthetic `... and N more items` node is appended. For JTable, truncation is by **rows** and the summary reads `... and N more rows` (SC-6). |
| BR-06 | Action labels displayed in the snapshot are determined by the **Action Label Algorithm** below. Detection methods, Java mechanisms, and MCP tool names are defined in **architecture.md §6**. All action names use lower-case underscore-separated format. |
| BR-07 | A node receives a ref if it exposes at least one action under the BR-06 algorithm — i.e. any of: `supportsClick()` returns non-null (covers both AccessibleAction and MouseListener fallback), `supportsTogglePopup()`, a known `AccessibleAction` constant, `supportsGetText()`, `supportsSetText()`, `supportsGetValue()`, `supportsSetValue()`, `supportsSelection()`, `supportsClose()`, or the `isLargeDataComponent` truncation gate (step 6b) returns non-null/true. `supportsSelection()` maps to one group label (`single-selection` or `multi-selection`). This supersedes the `AccessibleAction`-only gate in BR-01. Nodes where all actions are `!`-prefixed still receive a ref. |
| BR-08 | **Unavailable action prefix (`!`).** After the BR-06 algorithm produces the action list, each **mutation action** is checked: if the action would fail validation when invoked, it is prefixed with `!` (e.g. `!click`, `!set_text`). A mutation action is unavailable when: (a) `SwingUtils.isEffectivelyEnabled()` returns `false` (component or an ancestor is disabled), or (b) the action is `set_text` and the component is read-only (has `AccessibleEditableText` but lacks the `EDITABLE` state). **Read-only actions** (`get_text`, `get_value`, `get_selection`, `get_selectable_items`, `get_selectable_items_count`, `get_cell_count`, `get_cells`) are never prefixed — they always succeed. **Selection group labels** (`single-selection`, `multi-selection`) are never prefixed — they are informational labels, not directly invocable actions. The set of mutation actions is: `click`, `toggle_popup`, `increment`, `decrement`, `toggle_expand`, `set_text`, `set_value`, `close`. This set is stored as a constant (`MUTATION_ACTIONS`) in `SnapshotNode`. |
| BR-09 | **Snapshot filtering (`filter_substring`).** When the optional `filter_substring` parameter is provided, the tool applies a post-processing filter after the four-phase pipeline completes. Each rendered line is tested with a case-insensitive substring match (`String.toLowerCase().contains()`). Only matching lines are included in the output. Root separators (`---`) are excluded from filtered output. The ref map is unaffected — filtering does not change ref assignment. If no lines match, the tool returns the message `No lines matched filter_substring 'X'` (where X is the provided value). The filter operates on the full rendered line (role, name, states, actions — everything). |

### Action Label Algorithm (BR-06)

For each node, collect actions by running the following checks in order. All detection methods are defined in **architecture.md §§ 4–5**.

1. `supportsClick()` returns non-null → add `click` (covers both AccessibleAction and MouseListener fallback — see **architecture.md § 4 "Detecting Click Support"**)
2. `supportsTogglePopup()` → add `toggle_popup`
3. Iterate `AccessibleAction` descriptions; for each that equals a known constant (`AccessibleAction.INCREMENT`, `DECREMENT`, `TOGGLE_EXPAND`), normalize to lower-case underscore format and add it (`increment`, `decrement`, `toggle_expand`)
4. `supportsSetText()` **and** `AccessibleStateSet` contains `EDITABLE` → add `get_text`, `set_text`; else if `supportsSetText()` without `EDITABLE` (read-only text field) → add `get_text`, `set_text` (the `set_text` will be prefixed with `!` by BR-08 since the component is read-only); else `supportsGetText()` → add `get_text`
5. `supportsGetValue()` → add `get_value`; additionally `supportsSetValue()` → add `set_value`
6. **Selection group labels:**
   - `supportsMultiSelection()` → add `multi-selection`
   - else `supportsSingleSelection()` → add `single-selection`
   
   These are **group labels**, not individual actions. The AI learns which individual selection tools are available from the tool descriptions (sent once at MCP session start). `single-selection` means `swing_get_selection`, `swing_set_selection`, `swing_clear_selection`, `swing_get_selectable_items`, `swing_get_selectable_items_count` are callable. `multi-selection` means all of those plus `swing_select_all`. See **architecture.md § 6 "Selection Action Groups"** for detection methods and `SwingUtils` API.

6b. **Content discovery (cells):** If the component is a large data component (`isLargeDataComponent`: role is `TABLE`, `LIST`, or `TREE`) **and** its accessible children count exceeds `MAX_DATA_CHILDREN` (i.e. the snapshot truncated its children) → add `get_cell_count`, `get_cells`. These are independent of selection — they operate in the accessible children index space for discovering content beyond the snapshot cap.

7. `supportsClose()` → add `close`

**`get_cells` ref map replacement:** `swing_get_cells` assigns a fresh local ref numbering and **replaces the MCPServer ref map** with only the refs visible in its output window. This is analogous to scrolling a JTable to a certain offset: children outside the `offset`/`length` window are not interactable. The AI must call `swing_snapshot` again to return to the full-tree ref map.

`getAccessibleActionDescription()` is **never** used to derive display labels directly — it is only compared against known constants in step 3.

**Why step 3 only matches known constants:** `JTextComponent` subclasses expose dozens of dynamic `AccessibleAction` descriptions derived from `Action.NAME` (e.g. `"cut-to-clipboard"`, `"paste-from-clipboard"`, `"select-all"`). These are deliberately ignored. The primary interaction for any text component is reading and writing its value via `get_text`/`set_text` (step 4). An AI agent filling a form will set field values and move on — it has no need to invoke cut, copy, paste, or select-all via the accessibility API.

> **`JListChild` action note:** `click` is always present on `JListChild` (via `AccessibleAction`) and is always legitimate — a list item can always be clicked. Selection group labels (`single-selection`, `multi-selection`) appear on a `JListChild` only when that child's `getAccessibleSelection()` is non-null, which occurs only in unusual cases where the cell renderer itself contains a selectable component (e.g. a nested `JList`). In that case the selection tools are also legitimate. No special-casing of `JListChild` is needed — the Action Label Algorithm handles it correctly.

---

## Acceptance Criteria

- [x] Calling `swing_snapshot` returns a text tree containing role, name, states, and actions for each accessible node.
- [x] Only nodes exposing at least one `AccessibleAction` receive a ref; purely structural nodes (e.g., panels, labels) do not.
- [x] A panel with a button and a text field produces a tree with the expected structure and refs.
- [x] Nested component hierarchies are represented with correct indentation.
- [x] Non-visible components (`setVisible(false)`) are excluded from the tree, including all descendants.
- [x] Disabled components (`setEnabled(false)`) are included in the tree; their state reflects that they are disabled. The `disabled` state uses `isEffectivelyEnabled()` (parent-chain walk).
- [ ] Mutation actions on disabled components are prefixed with `!` (e.g. `!click`). Read-only actions and selection group labels are never prefixed.
- [ ] A read-only text field shows `!set_text` (not suppressed) alongside `get_text`.
- [ ] An enabled component inside a disabled parent shows `disabled` state and `!`-prefixed mutation actions.
- [x] Framework-internal containers (`root_pane`, `layered_pane`, `viewport`, `filler`) are transparently pruned — their children appear under the parent.
- [x] Unnamed panels (no accessible name, no accessible description, no titled border) are transparently pruned.
- [x] Named panels (with accessible name, description, or titled border) are kept in the tree.
- [x] `scroll_pane` is kept in the tree even when unnamed; `viewport` inside it is pruned.
- [x] `CellRendererPane` instances and their descendants are excluded.
- [x] Menu items are included even when the menu is closed.
- [x] JTabbedPane shows tab items with the selected tab marked `SELECTED`; only the selected tab's content is included.
- [x] A JTable/JList/JTree with more than `MAX_DATA_CHILDREN` rows shows only the first `MAX_DATA_CHILDREN` rows plus a `... and N more items` summary.
- [x] Only meaningful accessible states are shown (see **Accessible States — Display Rules**).
- [ ] An unnamed JPanel with an application MouseListener receives the `click` action and a ref (not pruned by TP-5 — AI-3 safety net applies).
- [ ] An unnamed JPanel with only framework MouseListeners (e.g. ToolTipManager) and no AccessibleAction is pruned normally by TP-5.
- [ ] A component with both AccessibleAction click and a MouseListener shows `click` (Tier 1 takes precedence — no duplication).
- [ ] A component with an interactive role and an application MouseListener but no AccessibleAction click does NOT get a `click` action (Tier 2 skipped for interactive roles).
- [ ] When `filter_substring` is provided, only lines containing the substring (case-insensitive) are returned.
- [ ] When `filter_substring` matches no lines, a descriptive message is returned instead of empty output.
- [ ] Filtering does not affect ref assignment — refs remain the same as in the unfiltered snapshot.
- [ ] Root separators (`---`) are excluded from filtered output.
- [ ] When `filter_substring` is omitted or empty, the full snapshot is returned (no change to existing behavior).
- [ ] A JTable inside a JScrollPane shows `columns: Col1, Col2, …` on the table node line (SC-6).
- [ ] A JTable NOT inside a JScrollPane (header not visible) does NOT show `columns:` (SC-6).
- [ ] JTable children are rendered as pipe-separated row lines with 0-based index (`- row 0: Val1 | Val2 | Val3`), not individual cell labels (SC-6).
- [ ] JTable truncation summary reads `... and N more rows` (not `... and N more items`) (SC-6).
- [ ] The JTableHeader panel (with column name labels) is suppressed from the snapshot tree when SC-6 applies (SC-7).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

### Headless tests (`src/test`) — `SwingSnapshotToolTest`

In headless mode, use `JPanel` as the root instead of `JFrame`/`JDialog` (top-level windows require a display).

- [x] `SwingSnapshotToolTest`
  - [x] A simple hierarchy (panel with button and text field) produces a tree with correct roles, names, and refs.
  - [x] Refs are assigned starting from 1.
  - [x] Nested containers produce correctly indented output.
  - [x] Components with `setVisible(false)` are excluded from the tree.
  - [x] Unnamed panels are transparently pruned — their children appear under the grandparent.
  - [x] Named panels (with titled border or accessible name) are kept in the tree.
  - [x] `CellRendererPane` instances are excluded.
  - [x] Framework-internal roles (`root_pane`, `layered_pane`, `viewport`, `filler`) are transparently pruned.
  - [x] `scroll_pane` is kept; `viewport` inside it is pruned.
  - [x] A JTable with more than `MAX_DATA_CHILDREN` rows is truncated with a summary node.
  - [x] Menu items appear in the tree even when the menu is not open.
  - [x] JTabbedPane shows tab items; selected tab has `SELECTED` state; non-selected tab content is not included.
  - [x] Only meaningful states are shown (e.g., `disabled` appears, `visible`/`enabled` do not).
  - [x] Disabled components appear in the tree with `disabled` state.
  - [ ] Disabled button shows `!click` (mutation action prefixed with `!`).
  - [ ] Disabled slider shows `!increment`, `!decrement`, `get_value`, `!set_value` (read-only actions unprefixed).
  - [ ] Read-only text field shows `get_text, !set_text`.
  - [ ] Enabled button inside a disabled panel shows `disabled` state and `!click`.
  - [ ] Disabled component with only `!`-prefixed actions still receives a ref.
  - [x] When two roots are provided, their trees are separated by a `---` line and refs are numbered globally (not reset between roots).
  - [x] Calling `swing_snapshot` via the MCP client returns a valid text response.
  - [ ] `filter_substring` returns only matching lines (case-insensitive substring match on full rendered line).
  - [ ] `filter_substring` with no matches returns a descriptive message.
  - [ ] `filter_substring` does not affect ref numbering — a filtered component has the same ref as in the unfiltered snapshot.
  - [ ] `filter_substring` drops root separators (`---`) from the output.
  - [ ] Omitting `filter_substring` (or passing empty/null) returns the full unfiltered snapshot.
  - [ ] An unnamed JPanel with an application `MouseListener` appears in the snapshot with `click` action and a ref.
  - [ ] An unnamed JPanel with only framework `MouseListener`s (e.g. from setting a tooltip) is pruned as usual.
  - [ ] A JButton (which has AccessibleAction click) with an additional application `MouseListener` shows `click` once (Tier 1 wins).
  - [ ] A disabled component with an application `MouseListener` shows `!click` (mutation action prefix applies).
  - [ ] A component with an interactive role (e.g. `JSlider`) and an application `MouseListener` but no AccessibleAction click does NOT get a `click` action from Tier 2 (interactive role exclusion).
  - [ ] A JTable inside a JScrollPane shows `columns: Col1, Col2` on the table node line (SC-6).
  - [ ] A JTable NOT inside a JScrollPane does NOT show `columns:` on the table node line (SC-6).
  - [ ] JTable children are rendered as pipe-separated row lines with 0-based index, not individual cell labels (SC-6).
  - [ ] JTable truncation summary reads `... and N more rows` (SC-6).
  - [ ] The JTableHeader panel is suppressed from the snapshot when the table is in a JScrollPane (SC-7).

### Screen-mode tests (`src/testSwing`) — `SwingSnapshotToolWithScreenTest`

Uses real `JFrame`/`JDialog` instances on an actual display. The snapshot tool is called with the frame or dialog as the considered component.

- [x] A visible `JFrame` with child components produces a snapshot tree rooted at the frame's content (framework-internal wrappers pruned).
- [x] A visible `JDialog` with child components produces a snapshot tree rooted at the dialog's content (framework-internal wrappers pruned).
