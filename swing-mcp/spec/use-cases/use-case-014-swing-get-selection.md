# UC-014: swing_get_selection

---

**As an** AI agent, **I want to** read the current selection of a UI component by ref **so that** I can understand which items are selected in lists, combo boxes, tables, and tabbed panes without relying on the snapshot (which omits selection state per UC-002 BR-03).

**Status:** Draft
**Date:** 2026-04-07

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_selection` with the `ref` parameter identifying the component whose selection I want to read.
- The tool looks up the component by ref and reads its selection via the accessibility API (`AccessibleSelection`).
- The tool returns a JSON object containing `selectedCount` (integer) and `selected` (array of objects, each with `index` and `name`).

**Tool description:** "Read the current selection of a UI component by ref. Returns JSON with selectedCount and selected items (index + name). Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_selection` (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support get_selection. Call swing_snapshot to verify the list of actions". **Implementation prerequisites:** (1) `JTree` (`AccessibleRole.TREE`) must be added to `SUPPRESSED_SELECTION_ROLES` — see design notes. (2) `supportsSelection()` must check `JTable` row-selection mode — see BR-10. |
| BR-10 | **JTable row-selection gate.** If the target is a `JTable`, `supportsSelection()` returns `true` only when `table.getRowSelectionAllowed() == true && table.getColumnSelectionAllowed() == false` (row-only mode). In column-only, cell, or no-selection modes, the table is treated as not supporting selection. Detection: check if the `Accessible` is an instance of `JTable`, then query selection flags. |
| BR-11 | **JTable row aggregation.** When the target is a `JTable` in row-selection mode, the tool does **not** return raw cell-level items. Instead, it aggregates cells into rows: (1) obtain `AccessibleTable at = ac.getAccessibleTable()` and `int cols = at.getAccessibleColumnCount()`; (2) for each cell returned by `getAccessibleSelection(i)`, compute `row = cellIndex / cols`; (3) deduplicate rows (use insertion-ordered set); (4) for each unique row, build `name` by concatenating `getAccessibleName()` of `at.getAccessibleAt(row, col)` for `col` in `[0, cols)`, separated by `", "` (e.g. `"Bob, 25, LA"`); (5) `index` is the zero-based row index. The `selectedCount` field reflects the number of **rows**, not cells. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_selection` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — reading the selection is always allowed, even on disabled components. |
| BR-07 | The tool returns a JSON object via `Content.json()`. The object contains `selectedCount` (integer — the number of selected items) and `selected` (a JSON array of selected-item objects). Each item object has `index` (integer — the zero-based index of the selected child within the parent's accessible children) and `name` (string — the accessible name of the selected child, or `null` if the name is `null`). |
| BR-08 | If no items are selected, the tool returns `{"selectedCount": 0, "selected": []}` — an explicit empty result, not an error. |
| BR-09 | The `selected` array is capped at `MAX_SELECTION_ITEMS` (initially **100**). If the selection contains more items, only the first `MAX_SELECTION_ITEMS` are returned, and an additional `truncated` field is set to `true` in the JSON response. When not truncated, the `truncated` field is omitted. |

### Algorithm

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-03** — `SwingUtils.supportsSelection(accessible)` — if `false`, fail with error.
3. Obtain `AccessibleSelection as = ac.getAccessibleSelection()`.
4. **If the target is a `JTable`** — use the **JTable row aggregation path** (BR-11):
   a. Obtain `AccessibleTable at = ac.getAccessibleTable()` and `int cols = at.getAccessibleColumnCount()`.
   b. Read `int cellCount = as.getAccessibleSelectionCount()`.
   c. Iterate selected cells, compute `row = cellIndex / cols` for each, collect unique rows (insertion-ordered).
   d. Cap at `MAX_SELECTION_ITEMS` rows. If more, set `truncated = true`.
   e. For each unique row, build `name` by concatenating cell names from `at.getAccessibleAt(row, col)` for all columns, separated by `", "`.
   f. Each entry: `{"index": row, "name": "Alice, 30, NY"}`.
   g. `selectedCount` = number of unique rows.
5. **Otherwise** — use the **generic path**:
   a. Read `int count = as.getAccessibleSelectionCount()`.
   b. Build the `selected` array: for `i` in `[0, min(count, MAX_SELECTION_ITEMS))`:
      - `Accessible child = as.getAccessibleSelection(i)` — returns the i-th *selected* child.
      - Get the child's `AccessibleContext`, read `getAccessibleName()` → `name`.
      - Determine the child's **zero-based index** within the parent via `getAccessibleIndexInParent()`.
      - Add `{"index": indexInParent, "name": name}` to the array.
   c. If `count > MAX_SELECTION_ITEMS`, include `"truncated": true` in the response (**BR-09**).
6. Build and return the JSON object via `Content.json()`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection and selection retrieval
- `AccessibleSelection.getAccessibleSelectionCount()` — number of selected items
- `AccessibleSelection.getAccessibleSelection(int i)` — the i-th selected child (returns `Accessible`)
- `AccessibleContext.getAccessibleIndexInParent()` — position of the selected child within its parent's child list
- `AccessibleContext.getAccessibleName()` — human-readable name of the selected child
- `AccessibleContext.getAccessibleTable()` — JTable-specific: row/column structure
- `AccessibleTable.getAccessibleColumnCount()` — JTable-specific: for cell-to-row mapping
- `AccessibleTable.getAccessibleAt(int row, int col)` — JTable-specific: for building row name summaries

### Design notes

- **Index semantics.** `AccessibleSelection.getAccessibleSelection(int i)` returns the i-th *selected* item, not the i-th child. The index the AI needs for `swing_set_selection` is the child's position within the parent, obtained via `getAccessibleIndexInParent()`. This distinction is critical.
- **`JList`** — supports `AccessibleSelection`. Single and multi-selection modes both work. Selected children have role `label`. `getAccessibleName()` returns the `toString()` of the list element (e.g. `"Alpha"`, `"42"` for an Integer). Null list items produce an empty-string name. Works in headless mode.
- **`JTable`** — supports `AccessibleSelection`, but the raw API reports **cell-level** selection, not row-level. Selecting row 1 in a 3-column table returns 3 selected cells (one per column). This is too chatty for the AI context window. **Decision: only support row-selection mode.** If `table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed()`, the table is in row-selection mode and `supportsSelection()` returns `true`. In all other modes (column-only, cell, no-selection), `supportsSelection()` returns `false`. The tool aggregates cells into rows: it computes `row = cellIndex / columnCount` via `AccessibleTable`, deduplicates, and returns row indices. For each selected row, `name` is built by concatenating cell values from `AccessibleTable.getAccessibleAt(row, col)` for all columns (e.g. `"Bob, 25, LA"`). `getAccessibleSelection()` is non-null even in no-selection mode, so the null-check alone is insufficient — the row-selection mode check is required. Works in headless mode.
- **`JTabbedPane`** — supports `AccessibleSelection` (role `PAGE_TAB_LIST`). Always single-selection. `getAccessibleName()` returns the tab title. Selected children have role `page tab`. Works in headless mode.
- **`JComboBox`** — supports `AccessibleSelection`. `getAccessibleName()` returns the selected item's `toString()` (e.g. `"Blue"`). Selected children have role `label`. The combo box's `getAccessibleChildrenCount()` is 1 (the popup menu), but the selection API works correctly even when the popup is closed — it reports the currently selected item. Works in headless mode.
- **`JTree`** — **deferred / unsupported.** `supportsSelection()` returns `true` (the tree's `getAccessibleSelection()` is non-null), but the tree-level `AccessibleSelection` is non-functional: `getAccessibleSelectionCount()` always returns 0, even after programmatic selection. The actual selection lives on **tree node** accessible children — each parent node exposes its own `AccessibleSelection` reporting which of its children are selected. For example, with Root → {Alpha, Beta} and Beta selected: the tree itself reports `selectionCount=0`, but the Root node reports `selectionCount=1` with Beta as the selected child. This split-level API is messy and would require walking the accessible hierarchy to collect the selection — unlike JList/JTable/JTabbedPane where the component-level `AccessibleSelection` works directly. **JTree must be added to `SUPPRESSED_SELECTION_ROLES`** so that `get_selection` is not advertised for it. Tree selection support may be revisited in a future use case.
- **`JMenuBar` / `JMenu`** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `SwingUtils.supportsSelection()`. These components use `AccessibleSelection` internally for keyboard navigation, but the selection is not user-facing. The AI interacts with menus via `swing_click`.
- **Null child.** If `getAccessibleSelection(i)` returns `null` (defensive case), skip that entry and continue. Do not fail the entire call.
- **Null name.** If `getAccessibleName()` returns `null`, the `name` field in the JSON is serialized as JSON `null`. The AI can still use the `index` to address the item.

### Probe test findings (2026-04-07)

Verified empirically on Java 21 OpenJDK in headless mode (`AccessibleSelectionProbeTest`,
`AccessibleSelectionProbeTest2`, `AccessibleSelectionProbeTreeTest`).

| Component | `supportsSelection()` | `getAccessibleSelectionCount()` | `getAccessibleName()` of selected child | `indexInParent` correct? | Notes |
|---|---|---|---|---|---|
| `JList` | `true` | Correct | Item's `toString()` | Yes | Role `label`. Works for single, multi, and empty selection. |
| `JTabbedPane` | `true` | Always 1 | Tab title | Yes | Role `page tab`. Single-selection only. |
| `JComboBox` | `true` | 1 (always) | Selected item's `toString()` | Yes | Role `label`. Works even with popup closed. `childrenCount=1` (popup menu). |
| `JTable` (row mode) | `true` | Cell count (not row count) | Cell value's `toString()` | Yes (cell-level, row-major) | Role `label`. Selecting 1 row in 3-col table → 3 cells. Tool aggregates to rows (BR-11). `getAccessibleSelection()` non-null even in no-selection mode. `AccessibleTable` available for row/col structure. Row header is `null`; row name built from cell values. |
| `JTable` (col mode) | `true` (suppressed) | Cell count (one per row in selected col) | Cell value's `toString()` | N/A | Column-only: `rowSelectionAllowed=false, columnSelectionAllowed=true`. Suppressed by BR-10. |
| `JTable` (cell mode) | `true` (suppressed) | Individual cell count | Cell value's `toString()` | N/A | Cell: `cellSelectionEnabled=true`. Suppressed by BR-10. |
| `JTable` (no sel) | `true` (suppressed) | 0 | N/A | N/A | `rowSelectionAllowed=false, columnSelectionAllowed=false`. `getAccessibleSelection()` still non-null. Suppressed by BR-10. |
| `JTree` | `true` (but broken) | Always 0 at tree level | N/A | N/A | Selection lives on tree **nodes**, not the tree itself. Deferred — must be suppressed. |
| `JMenuBar` | `true` (suppressed) | 0 | N/A | N/A | Suppressed by `SUPPRESSED_SELECTION_ROLES`. |
| `JMenu` | `true` (suppressed) | 0 | N/A | N/A | Suppressed by `SUPPRESSED_SELECTION_ROLES`. |

---

## Acceptance Criteria

- [ ] Calling `swing_get_selection` with a valid ref for a `JList` with items selected returns the selected indices and names.
- [ ] Calling `swing_get_selection` with a valid ref for a `JList` with no selection returns `{"selectedCount": 0, "selected": []}`.
- [ ] Calling `swing_get_selection` with a valid ref for a `JList` with multiple items selected returns all selected items.
- [ ] Calling `swing_get_selection` with a valid ref for a `JTabbedPane` returns the currently selected tab index and name.
- [ ] Calling `swing_get_selection` with a valid ref for a `JComboBox` returns the currently selected item index and name.
- [ ] Calling `swing_get_selection` with a valid ref for a `JTable` (row-selection mode) with a row selected returns row-level selection (aggregated from cells, with comma-separated cell values as name).
- [ ] Calling `swing_get_selection` with a valid ref for a `JTable` (row-selection mode) with multiple rows selected returns all selected rows.
- [ ] Calling `swing_get_selection` on a `JTable` in column-selection mode returns an MCP error (unsupported).
- [ ] Calling `swing_get_selection` on a `JTable` in cell-selection mode returns an MCP error (unsupported).
- [ ] Calling `swing_get_selection` on a `JTable` with no selection allowed returns an MCP error (unsupported).
- [ ] Calling `swing_get_selection` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_get_selection` on a component that does not support `get_selection` (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_get_selection` on a `JTree` returns an MCP error (suppressed — see design notes).
- [ ] The ref map is **not** cleared after a `swing_get_selection` call (read-only tool).
- [ ] Calling `swing_get_selection` on a disabled but selection-readable component succeeds (no enabled check).
- [ ] The `index` field in each selected item is the zero-based index within the parent's accessible children (suitable for passing to `swing_set_selection`).
- [ ] When the selection exceeds `MAX_SELECTION_ITEMS`, the response contains only the first 100 items and includes `"truncated": true`.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingUtilsSupportsSelectionTest` update — triggered by `JTree` suppression and `JTable` mode gate
  - [ ] `JTree` returns `false` for `supportsSelection()`.
  - [ ] `JTable` (row-selection mode, the default) returns `true` for `supportsSelection()`.
  - [ ] `JTable` (column-selection mode) returns `false` for `supportsSelection()`.
  - [ ] `JTable` (cell-selection mode) returns `false` for `supportsSelection()`.
  - [ ] `JTable` (no selection) returns `false` for `supportsSelection()`.

- [ ] `SwingGetSelectionTest` (headless)
  - [ ] Reading a `JList` with a single selected item returns `selectedCount: 1` and the correct index and name.
  - [ ] Reading a `JList` with multiple selected items returns all selected indices and names.
  - [ ] Reading a `JList` with no selection returns `selectedCount: 0` and an empty array.
  - [ ] Reading a `JTabbedPane` returns the selected tab's index and title.
  - [ ] Reading a `JComboBox` returns the selected item's index and name.
  - [ ] Reading a `JTable` with a row selected returns cell-level selection (2 items for a 2-column table).
  - [ ] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Reading a component without selection support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] The ref map is preserved after a successful `swing_get_selection` call (verified by calling `swing_get_selection` twice with the same ref).
  - [ ] Reading a disabled `JList` succeeds and returns its selection.
  - [ ] The `index` values match `getAccessibleIndexInParent()` of the selected children.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingGetSelectionScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Reading a `JList` with selection inside `JFrame` returns the selection.
  - [ ] Reading a `JTabbedPane` inside `JFrame` returns the selected tab.
  - [ ] Reading a `JComboBox` inside `JFrame` returns the selected item.
  - [ ] Reading a `JTable` inside `JFrame` returns cell-level selection.
  - [ ] Reading a `JList` inside `JDialog` returns the selection.

### Component matrix

Each component from the verification matrix gets a dedicated test method.
Validated by probe tests (`AccessibleSelectionProbeTest`, `AccessibleSelectionProbeTest2`,
`AccessibleSelectionProbeTreeTest`) on 2026-04-07.

**Expected to succeed (`get_selection` supported):**
`JList`, `JTabbedPane`, `JComboBox`, `JTable`

**Expected to fail with "Component does not support get_selection" error:**
`JTree` (suppressed — tree-level `AccessibleSelection` is non-functional; see design notes), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
