# UC-002: swing_snapshot

---

**As an** AI agent, **I want to** obtain an accessibility tree snapshot of the Swing application **so that** I can understand the current UI structure and identify components for interaction.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_snapshot` tool with no parameters.
- The tool walks the `javax.accessibility` tree of each considered component (usually a Window or JFrame, but during testing it could be any component).
- The tree walker applies the **Snapshot Inclusion Rules** (below) to decide which nodes appear in the output and which are pruned.
- The tool returns a compact indented text tree with each node showing role, name, states, available actions, and current value.
  - Nodes that expose at least one `AccessibleAction` also receive a numeric ref.
  - Only include accessibility name and description if those are not blank.

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
frame "Invoice Editor"
  root pane                            ← TP-1
    layered pane                       ← TP-2
      panel                            ← TP-5 (content pane, unnamed)
        panel                          ← TP-5 (unnamed toolbar wrapper)
          toolbar "Main"
            button "Save"
        panel                          ← TP-5 (unnamed main area)
          split pane
            panel                      ← TP-5 (unnamed left side)
              label "Invoices"
              scroll pane
                viewport               ← TP-3
                  list
            panel                      ← TP-5 (unnamed right side)
              panel "Details"          ← KEPT (has titled border)
                text field "Name"
              panel                    ← TP-5 (unnamed button bar)
                button "Add"
```

After pruning:
```
frame "Invoice Editor"
  toolbar "Main"
    button "Save" [ref=1]
  split pane
    label "Invoices"
    scroll pane
      list [ref=2]
    panel "Details"
      text field "Name" [ref=3]
    button "Add" [ref=4]
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
| SC-2 | **JTabbedPane: include ALL tab pages, not just selected.** Mark the selected tab via `SELECTED` state. | Migration needs the complete tab structure. |
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

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Refs are short integers starting from 1, assigned fresh with each snapshot call. Only nodes that expose at least one `AccessibleAction` receive a ref. |
| BR-02 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-03 | The output format is a compact indented text tree (not YAML), mimicking Playwright MCP. Line format: `- role "name" value="val" [ref=N] [states] actions: action1, action2`. Omit each segment if empty/not applicable (e.g., no `value=` for a button, no `[ref=]` for a panel). |
| BR-04 | The tree walker walks the `javax.accessibility` tree via `AccessibleContext.getAccessibleChild(i)`, **not** the `Component.getComponents()` component tree. The accessibility tree provides virtual children for complex components (table cells, list items, tree nodes). |
| BR-05 | Large data components (JTable, JList, JTree) are truncated to `MAX_DATA_CHILDREN` accessible children (static final constant, initially 10). When truncated, a synthetic `... and N more items` node is appended. |

---

## Acceptance Criteria

- [ ] Calling `swing_snapshot` returns a text tree containing role, name, states, actions, and value for each accessible node.
- [ ] Only nodes exposing at least one `AccessibleAction` receive a ref; purely structural nodes (e.g., panels, labels) do not.
- [ ] A panel with a button and a text field produces a tree with the expected structure and refs.
- [ ] Nested component hierarchies are represented with correct indentation.
- [ ] Non-visible components (`setVisible(false)`) are excluded from the tree, including all descendants.
- [ ] Disabled components (`setEnabled(false)`) are included in the tree; their state reflects that they are disabled.
- [ ] Framework-internal containers (`ROOT_PANE`, `LAYERED_PANE`, `VIEWPORT`, `FILLER`) are transparently pruned — their children appear under the parent.
- [ ] Unnamed panels (no accessible name, no accessible description, no titled border) are transparently pruned.
- [ ] Named panels (with accessible name, description, or titled border) are kept in the tree.
- [ ] `SCROLL_PANE` is kept in the tree even when unnamed; `VIEWPORT` inside it is pruned.
- [ ] `CellRendererPane` instances and their descendants are excluded.
- [ ] Menu items are included even when the menu is closed.
- [ ] JTabbedPane includes all tab pages; the selected tab is marked with `SELECTED` state.
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
  - [ ] Framework-internal roles (`ROOT_PANE`, `LAYERED_PANE`, `VIEWPORT`, `FILLER`) are transparently pruned.
  - [ ] `SCROLL_PANE` is kept; `VIEWPORT` inside it is pruned.
  - [ ] A JTable with more than `MAX_DATA_CHILDREN` rows is truncated with a summary node.
  - [ ] Menu items appear in the tree even when the menu is not open.
  - [ ] JTabbedPane shows all tabs; selected tab has `SELECTED` state.
  - [ ] Only meaningful states are shown (e.g., `disabled` appears, `visible`/`enabled` do not).
  - [ ] Disabled components appear in the tree with `disabled` state.
  - [ ] Calling `swing_snapshot` via the MCP client returns a valid text response.
