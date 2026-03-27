# UC-002: swing_snapshot

---

**As an** AI agent, **I want to** obtain an accessibility tree snapshot of the Swing application **so that** I can understand the current UI structure and identify components for interaction.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_snapshot` tool with no parameters.
- The tool walks the `javax.accessibility` tree of each component returned by `SwingToolContext.getConsideredComponents()` (usually a Window or JFrame, but during testing it could be any component). Each considered component is an independent root — no component in the list is nested inside another.
- The tree walker applies the **Snapshot Inclusion Rules** (below) to decide which nodes appear in the output and which are pruned.
- The tool returns a compact indented text tree with each node showing role, name, states, and available actions.
  - Nodes that expose at least one `AccessibleAction` also receive a numeric ref.
  - Only include accessibility name and description if those are not blank. Description follows the name in the line format.

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
The `VIEWPORT` inside it IS pruned (TP-3), so the tree shows `scroll pane → content`
rather than `scroll pane → viewport → content`.

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
      - list [ref=2] actions: select
    - panel "Details"
      - text "Name" [ref=3] actions: type
    - push_button "Add" [ref=4] actions: click
```

### Stage 3 — Always Included

After Stages 1 and 2, any surviving node is included. The following criteria serve as a safety net — a node matching any of these must never be pruned, even if a future rule change would otherwise drop it:

| ID | Criterion | Swing Mechanism |
|----|-----------|-----------------|
| AI-1 | Has a non-structural accessible role | See **semantic roles** list below |
| AI-2 | Has an accessible name | `getAccessibleName()` non-null and non-empty |
| AI-3 | Has `AccessibleAction` | `getAccessibleAction()` non-null with action count > 0 |
| AI-4 | Has `AccessibleText` with content, or `AccessibleValue` | Component carries meaningful data |
| AI-5 | Is focused | `AccessibleState.FOCUSED` in state set |

**Semantic (non-structural) accessible roles — always included:**

- Interactive: `PUSH_BUTTON`, `TOGGLE_BUTTON`, `CHECK_BOX`, `RADIO_BUTTON`, `TEXT`, `PASSWORD_TEXT`, `COMBO_BOX`, `LIST`, `TABLE`, `TREE`, `MENU_BAR`, `MENU`, `MENU_ITEM`, `POPUP_MENU`, `SLIDER`, `SPINNER`, `PROGRESS_BAR`, `SCROLL_BAR`, `COLOR_CHOOSER`, `FILE_CHOOSER`, `DATE_EDITOR`
- Structural-semantic: `FRAME`, `DIALOG`, `INTERNAL_FRAME`, `OPTION_PANE`, `TOOL_BAR`, `TOOL_TIP`, `TAB_LIST`, `PAGE_TAB`, `PAGE_TAB_LIST`, `SPLIT_PANE`, `SCROLL_PANE`, `SEPARATOR`, `LABEL`, `STATUS_BAR`, `TABLE_HEADER`, `GROUP_BOX`
- Named containers: `PANEL` with accessible name, description, or `TitledBorder`

### Special Component Rules

| ID | Rule | Rationale |
|----|------|-----------|
| SC-1 | **JMenu items: include even when menu is closed.** Walk `AccessibleContext.getAccessibleChild(i)` for menus regardless of popup visibility. | The full menu structure is needed for migration. The accessibility API exposes menu items as accessible children even when the popup is not shown. |
| SC-2 | **JTabbedPane: include only the selected tab's content.** Walk `getAccessibleChild()` naturally — it exposes the tab items (`PAGE_TAB`) and the selected tab's content panel. Mark the selected tab via `SELECTED` state. | The user can only see and interact with the selected tab's content; non-selected content is not accessible via the standard accessibility API anyway. |
| SC-3 | **Large data components (JTable, JList, JTree): truncate to first N accessible children** where N is a static final constant in the tool class (initially **10**). Append a synthetic node `... and X more items` when truncated. | A table with thousands of rows would blow up the AI context window. 10 rows gives enough structural overview. |
| SC-4 | **Disabled components: included with `disabled` state.** | Already in spec. The AI needs to see disabled components to understand the full UI. |
| SC-5 | **JInternalFrame: include all, mark iconified ones** via `ICONIFIED` state. | Iconified internal frames are minimized but not invisible. |

### Accessible States — Display Rules

Which states from `AccessibleStateSet` appear in the snapshot output:

**Included (meaningful for AI understanding):**
`DISABLED`, `FOCUSED`, `SELECTED`, `CHECKED`, `EDITABLE`, `EXPANDED`, `COLLAPSED`, `MODAL`, `MULTI_LINE`, `ICONIFIED`, `HORIZONTAL`, `VERTICAL`, `BUSY`, `INDETERMINATE`

**Omitted (noise or always-true for included nodes):**
`VISIBLE`, `SHOWING`, `ENABLED` (default — only show its absence as `DISABLED`), `OPAQUE`, `RESIZABLE`, `ARMED`, `TRANSIENT`, `MANAGES_DESCENDANTS`

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
- SC-3: cap large-data components (`JTable`, `JList`, `JTree`) at `MAX_DATA_CHILDREN` accessible children; only the first `MAX_DATA_CHILDREN` children become `SnapshotNode`s. The `... and N more items` summary is a render-only artifact emitted by Phase 4 — it is never a `SnapshotNode` and can never receive a ref.

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
A depth-first traversal over the pruned tree. Each node that exposes at least one `AccessibleAction` (`getAccessibleAction() != null && getActionCount() > 0`) receives the next integer ref. `assignRefs` accepts a `startRef` parameter (the first ref it may assign) so that multiple roots share a single global sequence: root 1 calls `assignRefs(1)` and returns the next free ref; root 2 calls `assignRefs` with that value, and so on.

**Phase 4 — render**
A depth-first traversal that serialises each node to a line of text per BR-03, using indentation depth to represent the tree structure. After rendering the last `SnapshotNode` child of a truncated large-data component, emits a synthetic `... and N more items` line (not a node — no ref, no pruning). When multiple roots are present, their rendered trees are separated by a `---` line.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Refs are short integers starting from 1, assigned fresh with each snapshot call, globally across all roots. Only nodes that expose at least one `AccessibleAction` receive a ref. |
| BR-02 | The entire four-phase pipeline runs on the EDT via a single `SwingUtilities.invokeAndWait()` call. All phases — including prune, assignRefs, and render — execute inside that call. Off-EDT optimisation is deferred until a performance problem is demonstrated. |
| BR-03 | The output format is a compact indented text tree (not YAML), mimicking Playwright MCP. Line format: `- role "name" [ref=N, state1, state2] actions: action1, action2`. Role is the `AccessibleRole` field name lowercased with underscores (e.g. `push_button`, `text`, `scroll_pane`) — never `toDisplayString()`, which is locale-sensitive. Ref and states share one bracket, comma-separated, lowercase. Omit the bracket entirely if there is no ref and no states. Omit `actions:` if none. Field values (`AccessibleText` content, `AccessibleValue`) are **not** shown in the output — only the accessible name (caption) is shown, consistent with Playwright MCP's approach. Revisit if the AI needs field values in future. |
| BR-04 | The tree walker walks the `javax.accessibility` tree via `AccessibleContext.getAccessibleChild(i)`, **not** the `Component.getComponents()` component tree. The accessibility tree provides virtual children for complex components (table cells, list items, tree nodes). |
| BR-05 | Large data components (JTable, JList, JTree) are truncated to `MAX_DATA_CHILDREN` accessible children (static final constant, initially 10). When truncated, a synthetic `... and N more items` node is appended. |
| BR-06 | Action names are a **fixed English vocabulary** derived from the component's accessible role — they are never taken from `AccessibleAction.getAccessibleActionDescription()`, which is locale-sensitive. Only actions from the table below are shown; custom component actions outside this set are silently omitted. See **Role → Action mapping** below. |

### Role → Action Mapping (BR-06)

Actions shown in the snapshot are determined solely by the node's `AccessibleRole`, not by `AccessibleAction.getAccessibleActionDescription()`.

**Why not use `AccessibleAction` descriptions directly?**
- `AbstractButton.getAccessibleActionDescription()` delegates to `UIManager.getString("AbstractButton.clickText")` — locale-sensitive.
- `AccessibleJTextComponent` exposes ~58 actions sourced from the Swing `Action` API (caret-forward, selection-down, page-up, …) — useless noise for an AI agent.
- `AccessibleHyperlink` returns the link caption as the action description — unpredictable.

**Two-step resolution algorithm:**

**Step 1 — Constants check.** Collect all action descriptions via `getAccessibleActionDescription(i)`. If **every** description equals one of the static `String` constants declared in `AccessibleAction` (e.g. `CLICK`, `TOGGLE_EXPAND`, `INCREMENT`, `DECREMENT`, `TOGGLE`, …), use those constant values as the displayed action names. This handles components like `AccessibleJTreeNode` whose action set is variable but always drawn from the constants.

**Step 2 — Role table fallback.** If any description does not match a constant (localized strings, arbitrary Action names), ignore all descriptions and look up the role in the table below. This handles `AbstractButton` on non-English JVMs and the noisy `AccessibleJTextComponent`.

`AccessibleAction` is still used to gate **ref assignment** (BR-01) and **node inclusion** (AI-3) — this algorithm only governs what action labels are *displayed*.

| Accessible Role | Actions shown (fallback) |
|-----------------|--------------------------|
| `PUSH_BUTTON` | `click` |
| `TOGGLE_BUTTON` | `click` |
| `CHECK_BOX` | `click` |
| `RADIO_BUTTON` | `click` |
| `MENU_ITEM` | `click` |
| `MENU` | `click` |
| `TEXT`, `PASSWORD_TEXT` | `type` |
| `COMBO_BOX` | `select` |
| `LIST` | `select` |
| `PAGE_TAB` | `click` |

Notes:
- `SLIDER` is absent from this table because `JSlider` exposes `INCREMENT`/`DECREMENT` as `AccessibleAction` constants — Step 1 handles it, producing `increment, decrement`.
- `SPINNER` is absent for the same reason — `JSpinner` exposes `INCREMENT`/`DECREMENT` constants, handled by Step 1.
- Any role not in this table and not passing the constants check: no actions shown.

---

## Acceptance Criteria

- [ ] Calling `swing_snapshot` returns a text tree containing role, name, states, and actions for each accessible node.
- [ ] Only nodes exposing at least one `AccessibleAction` receive a ref; purely structural nodes (e.g., panels, labels) do not.
- [ ] A panel with a button and a text field produces a tree with the expected structure and refs.
- [ ] Nested component hierarchies are represented with correct indentation.
- [ ] Non-visible components (`setVisible(false)`) are excluded from the tree, including all descendants.
- [ ] Disabled components (`setEnabled(false)`) are included in the tree; their state reflects that they are disabled.
- [ ] Framework-internal containers (`root_pane`, `layered_pane`, `viewport`, `filler`) are transparently pruned — their children appear under the parent.
- [ ] Unnamed panels (no accessible name, no accessible description, no titled border) are transparently pruned.
- [ ] Named panels (with accessible name, description, or titled border) are kept in the tree.
- [ ] `scroll_pane` is kept in the tree even when unnamed; `viewport` inside it is pruned.
- [ ] `CellRendererPane` instances and their descendants are excluded.
- [ ] Menu items are included even when the menu is closed.
- [ ] JTabbedPane shows tab items with the selected tab marked `SELECTED`; only the selected tab's content is included.
- [ ] A JTable/JList/JTree with more than `MAX_DATA_CHILDREN` rows shows only the first `MAX_DATA_CHILDREN` rows plus a `... and N more items` summary.
- [ ] Only meaningful accessible states are shown (see **Accessible States — Display Rules**).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

Understand that headless mode is on, which means you have to use JPanel instead of Window/Dialog/JFrame for testing.

- [ ] `SwingSnapshotTest`
  - [ ] A simple hierarchy (panel with button and text field) produces a tree with correct roles, names, and refs.
  - [ ] Refs are assigned starting from 1.
  - [ ] Nested containers produce correctly indented output.
  - [ ] Components with `setVisible(false)` are excluded from the tree.
  - [ ] Unnamed panels are transparently pruned — their children appear under the grandparent.
  - [ ] Named panels (with titled border or accessible name) are kept in the tree.
  - [ ] `CellRendererPane` instances are excluded.
  - [ ] Framework-internal roles (`root_pane`, `layered_pane`, `viewport`, `filler`) are transparently pruned.
  - [ ] `scroll_pane` is kept; `viewport` inside it is pruned.
  - [ ] A JTable with more than `MAX_DATA_CHILDREN` rows is truncated with a summary node.
  - [ ] Menu items appear in the tree even when the menu is not open.
  - [ ] JTabbedPane shows tab items; selected tab has `SELECTED` state; non-selected tab content is not included.
  - [ ] Only meaningful states are shown (e.g., `disabled` appears, `visible`/`enabled` do not).
  - [ ] Disabled components appear in the tree with `disabled` state.
  - [ ] Calling `swing_snapshot` via the MCP client returns a valid text response.
