# UC-014: swing_get_selection

---

**As an** AI agent, **I want to** read the current selection of a UI component by ref **so that** I can understand which items are selected in lists, combo boxes, tables, and tabbed panes without relying on the snapshot (which omits selection state per UC-002 BR-03).

**Status:** Approved
**Date:** 2026-04-07

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_selection` with the `ref` parameter identifying the component whose selection I want to read.
- The tool looks up the component by ref and reads its selection via the accessibility API (`AccessibleSelection`).
- The tool returns a JSON object containing `selectedCount` (integer) and `selected` (array of objects, each with `index` and `name`).

**Tool description:** "Read the current selection of a UI component by ref. Returns JSON with selectedCount and selected items (0-based index + name). For JTable, index is the row index (not cell index) and name is a comma-separated summary of cell values. Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_selection` (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"Component does not support get_selection. Call swing_snapshot to verify the list of actions."* **Implementation prerequisites:** (1) `JTree` (`AccessibleRole.TREE`) must be added to `SUPPRESSED_SELECTION_ROLES` — see design notes. (2) `supportsSelection()` must check `JTable` row-selection mode — see BR-10. **Implementation note:** the JTable-specific message requires the tool to detect the JTable case separately from the generic `supportsSelection()` check — either by checking `instanceof JTable` before calling `supportsSelection()`, or by having `supportsSelection()` return a reason code. |
| BR-10 | **JTable row-selection gate.** If the target is a `JTable`, `supportsSelection()` returns `true` only when `table.getRowSelectionAllowed() == true && table.getColumnSelectionAllowed() == false` (row-only mode). In column-only, cell, or no-selection modes, the table is treated as not supporting selection. Detection: check if the `Accessible` is an instance of `JTable`, then query selection flags. |
| BR-11 | **JTable row aggregation.** When the target is a `JTable` in row-selection mode, the tool does **not** return raw cell-level items. Instead, it aggregates cells into rows: (1) obtain `AccessibleTable at = ac.getAccessibleTable()` and `int cols = at.getAccessibleColumnCount()`; (2) for each cell returned by `getAccessibleSelection(i)`, compute `row = cellIndex / cols`; (3) deduplicate rows (use insertion-ordered set); (4) for each unique row, build `name` by concatenating `getAccessibleName()` of `at.getAccessibleAt(row, col)` for `col` in `[0, min(cols, MAX_ROW_NAME_COLUMNS))`, separated by `", "` (e.g. `"Bob, 25, LA"`). `MAX_ROW_NAME_COLUMNS` is a static final constant, initially **10**. If the table has more columns, only the first 10 are included in the name. If `getAccessibleAt(row, col)` returns `null` or the cell's `getAccessibleName()` returns `null`, use the literal string `"null"` in the concatenation; (5) `index` is the zero-based row index. The `selectedCount` field reflects the number of **rows**, not cells. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_selection` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — reading the selection is always allowed, even on disabled components. |
| BR-07 | The tool returns a JSON object via `Content.json()`. The object contains `selectedCount` (integer — the number of selected items) and `selected` (a JSON array of selected-item objects). Each item object has `index` (integer — the zero-based item index, obtained via `getAccessibleIndexInParent()`, suitable for passing directly to `swing_set_selection` / `addAccessibleSelection()`) and `name` (string — the accessible name of the selected child, or `null` if the name is `null`). For JTable, `index` is the row index instead — see BR-11. |
| BR-08 | If no items are selected, the tool returns `{"selectedCount": 0, "selected": []}` — an explicit empty result, not an error. |
| BR-09 | The `selected` array is capped at `MAX_SELECTION_ITEMS` (initially **100**). If the selection contains more items, only the first `MAX_SELECTION_ITEMS` are returned, and an additional `truncated` field is set to `true` in the JSON response. When not truncated, the `truncated` field is omitted. |

### Algorithm

**Index semantics.** All indices returned by `swing_get_selection` are in the **item index space** — the 0-based index that `addAccessibleSelection(i)` and `isAccessibleChildSelected(i)` expect. This is critical: `swing_set_selection` must be able to pass these indices directly to `addAccessibleSelection()`.

The `AccessibleSelection` API mixes three distinct index spaces:
1. **Selection-relative index** — `getAccessibleSelection(int i)` returns the i-th *selected* item; `i` ranges over `[0, getAccessibleSelectionCount())`.
2. **Accessible children index** — `getAccessibleChild(int i)` / `getAccessibleChildrenCount()` enumerate the component's structural children. For JComboBox, child 0 is the popup menu (childrenCount=1), which is completely different from the item list.
3. **Item index** — `addAccessibleSelection(int i)`, `isAccessibleChildSelected(int i)`, and `getAccessibleIndexInParent()` on a selected item all use the same 0-based item index. This is the index space we use.

Empirically verified (probe test, 2026-04-07): for all supported components (JList, JTabbedPane, JComboBox, JTable), `getAccessibleIndexInParent()` on an item returned by `getAccessibleSelection(i)` equals the item's index in the `addAccessibleSelection()` space. This identity must be covered by tests.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-03** — `SwingUtils.supportsSelection(accessible)` — if `false`, fail with error.
3. Obtain `AccessibleSelection as = ac.getAccessibleSelection()`.
4. **If the target is a `JTable`** — use the **JTable row aggregation path** (BR-11):
   a. Obtain `AccessibleTable at = ac.getAccessibleTable()` and `int cols = at.getAccessibleColumnCount()`.
   b. Read `int selCount = as.getAccessibleSelectionCount()`.
   c. Iterate `i` in `[0, selCount)`: get `Accessible cell = as.getAccessibleSelection(i)`, compute `cellIndex = cell.getAccessibleContext().getAccessibleIndexInParent()`, then `row = cellIndex / cols`. Collect unique rows (insertion-ordered set).
   d. Cap at `MAX_SELECTION_ITEMS` rows. If more, set `truncated = true`.
   e. For each unique row, build `name` by concatenating cell names from `at.getAccessibleAt(row, col)` for `col` in `[0, min(cols, MAX_ROW_NAME_COLUMNS))`, separated by `", "`. Use literal `"null"` for null cells/names.
   f. Each entry: `{"index": row, "name": "Alice, 30, NY"}`. Here `index` is the **row index** (not the cell index), because `swing_set_selection` for JTable will need to translate rows back to cell indices internally.
   g. `selectedCount` = number of unique rows.
5. **Otherwise** — use the **generic path**:
   a. Read `int selCount = as.getAccessibleSelectionCount()`.
   b. Iterate `i` in `[0, min(selCount, MAX_SELECTION_ITEMS))`:
      - `Accessible child = as.getAccessibleSelection(i)` — the i-th selected item.
      - `int itemIndex = child.getAccessibleContext().getAccessibleIndexInParent()` — the item index.
      - `String name = child.getAccessibleContext().getAccessibleName()`.
      - Add `{"index": itemIndex, "name": name}` to the array.
   c. If `selCount > MAX_SELECTION_ITEMS`, set `truncated = true`.
   d. `selectedCount` = size of the `selected` array.
6. Build and return the JSON object via `Content.json()`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection and selection retrieval
- `AccessibleSelection.getAccessibleSelectionCount()` — number of selected items (for efficient iteration)
- `AccessibleSelection.getAccessibleSelection(int i)` — the i-th selected item (selection-relative index)
- `AccessibleContext.getAccessibleIndexInParent()` — maps from selected item to item index (verified to equal `addAccessibleSelection` index for all supported components)
- `AccessibleContext.getAccessibleName()` — human-readable name of the selected child
- `AccessibleContext.getAccessibleTable()` — JTable-specific: row/column structure
- `AccessibleTable.getAccessibleColumnCount()` — JTable-specific: for cell-to-row mapping
- `AccessibleTable.getAccessibleAt(int row, int col)` — JTable-specific: for building row name summaries

### Design notes

- **Index semantics.** The `AccessibleSelection` API mixes three index spaces (see Algorithm section). This tool returns indices in the **item index space** — the same 0-based index that `addAccessibleSelection(i)` expects. The item index is obtained via `getAccessibleIndexInParent()` on each selected item returned by `getAccessibleSelection(i)`. Probe testing (2026-04-07) confirmed that `indexInParent` equals the `addAccessibleSelection` index for all supported components (JList, JTabbedPane, JComboBox, JTable). For JComboBox, the selected item's parent is the internal popup `list` (not the combo box), so `indexInParent` correctly reflects the item position (0-based) even though `getAccessibleChild(0)` on the combo box returns the popup menu.
- **`JList`** — supports `AccessibleSelection`. Single and multi-selection modes both work. Selected children have role `label`. `getAccessibleName()` returns the `toString()` of the list element (e.g. `"Alpha"`, `"42"` for an Integer). Null list items produce an empty-string name. Works in headless mode.
- **`JTable`** — supports `AccessibleSelection`, but the raw API reports **cell-level** selection, not row-level. Selecting row 1 in a 3-column table returns 3 selected cells (one per column). This is too chatty for the AI context window. **Decision: only support row-selection mode.** If `table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed()`, the table is in row-selection mode and `supportsSelection()` returns `true`. In all other modes (column-only, cell, no-selection), `supportsSelection()` returns `false`. The tool aggregates cells into rows: it computes `row = cellIndex / columnCount` via `AccessibleTable`, deduplicates, and returns row indices. For each selected row, `name` is built by concatenating cell values from `AccessibleTable.getAccessibleAt(row, col)` for the first `MAX_ROW_NAME_COLUMNS` (10) columns (e.g. `"Bob, 25, LA"`). Null cells/names use the literal string `"null"`. `getAccessibleSelection()` is non-null even in no-selection mode, so the null-check alone is insufficient — the row-selection mode check is required. Works in headless mode.
- **`JTabbedPane`** — supports `AccessibleSelection` (role `PAGE_TAB_LIST`). Always single-selection. `getAccessibleName()` returns the tab title. Selected children have role `page tab`. Works in headless mode.
- **`JComboBox`** — supports `AccessibleSelection`. `getAccessibleName()` returns the selected item's `toString()` (e.g. `"Blue"`). Selected children have role `label`. The combo box's `getAccessibleChildrenCount()` is 1 (the popup menu), but the selection API works correctly even when the popup is closed — it reports the currently selected item. Works in headless mode.
- **`JTree`** — **deferred / unsupported.** `supportsSelection()` returns `true` (the tree's `getAccessibleSelection()` is non-null), but the tree-level `AccessibleSelection` is non-functional: `getAccessibleSelectionCount()` always returns 0, even after programmatic selection. The actual selection lives on **tree node** accessible children — each parent node exposes its own `AccessibleSelection` reporting which of its children are selected. For example, with Root → {Alpha, Beta} and Beta selected: the tree itself reports `selectionCount=0`, but the Root node reports `selectionCount=1` with Beta as the selected child. This split-level API is messy and would require walking the accessible hierarchy to collect the selection — unlike JList/JTable/JTabbedPane where the component-level `AccessibleSelection` works directly. **JTree must be added to `SUPPRESSED_SELECTION_ROLES`** so that `get_selection` is not advertised for it. Tree selection support may be revisited in a future use case.
- **`JMenuBar` / `JMenu`** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `SwingUtils.supportsSelection()`. These components use `AccessibleSelection` internally for keyboard navigation, but the selection is not user-facing. The AI interacts with menus via `swing_click`.
- **Prerequisite TODOs in `architecture.md` § 6.** The JTree/JTable suppression introduced by this UC has two side-effects on the snapshot action list that must be resolved before or during implementation: (1) `get_children`/`get_children_count` must be decoupled from `supportsSelection()` (TODO-1); (2) `select_all` must be gated on multi-select capability (TODO-2). See `architecture.md` § 6 "TODOs — Selection Action Decoupling" for details.
- **Null child.** If `getAccessibleSelection(i)` returns `null` (defensive case), skip that entry and continue. Do not fail the entire call.
- **Null name.** If `getAccessibleName()` returns `null`, the `name` field in the JSON is serialized as JSON `null`. The AI can still use the `index` to address the item.

### Probe test findings (2026-04-07)

Verified empirically on Java 21 OpenJDK in headless mode (`AccessibleSelectionProbeTest`,
`AccessibleSelectionProbeTest2`, `AccessibleSelectionProbeTreeTest`, `AccessibleSelectionProbeIndexTest`).

| Component | `supportsSelection()` | `getAccessibleSelectionCount()` | `getAccessibleName()` of selected child | `indexInParent` correct? | Notes |
|---|---|---|---|---|---|
| `JList` | `true` | Correct | Item's `toString()` | Yes | Role `label`. Works for single, multi, and empty selection. `indexInParent` = item index = `addAccessibleSelection` index. Children index = item index. |
| `JTabbedPane` | `true` | Always 1 | Tab title | Yes | Role `page tab`. Single-selection only. `indexInParent` = tab index = `addAccessibleSelection` index. Children index = tab index. |
| `JComboBox` | `true` | 1 (always) | Selected item's `toString()` | Yes | Role `label`. Works even with popup closed. **Three index spaces diverge:** `getAccessibleChildrenCount()=1` (popup menu), but `indexInParent` = item index = `addAccessibleSelection` index. Selected item's parent is `list` (popup), not the combo box. `isAccessibleChildSelected(i)` uses item index. |
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
- [ ] The `index` field in each selected item is the zero-based item index (verified by: `addAccessibleSelection(index)` selects the same item, and `isAccessibleChildSelected(index)` returns `true`).
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
  - [ ] Reading an empty `JComboBox` (`new JComboBox<>()`) returns `selectedCount: 0` and an empty array (or handles gracefully if `getAccessibleSelection(0)` returns null).
  - [ ] Reading a `JTable` (row-selection mode) with a single row selected returns `selectedCount: 1` with row index and comma-separated cell values as name.
  - [ ] Reading a `JTable` (row-selection mode) with multiple rows selected returns all rows.
  - [ ] Reading a `JTable` in column-selection mode returns an MCP error.
  - [ ] Reading a `JTable` in cell-selection mode returns an MCP error.
  - [ ] Reading a `JTable` with no selection allowed returns an MCP error.
  - [ ] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Reading a component without selection support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] The ref map is preserved after a successful `swing_get_selection` call (verified by calling `swing_get_selection` twice with the same ref).
  - [ ] Reading a disabled `JList` succeeds and returns its selection.
  - [ ] The `index` values are item indices: `isAccessibleChildSelected(index)` returns `true` and `addAccessibleSelection(index)` selects the same item.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingGetSelectionScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Reading a `JList` with selection inside `JFrame` returns the selection.
  - [ ] Reading a `JTabbedPane` inside `JFrame` returns the selected tab.
  - [ ] Reading a `JComboBox` inside `JFrame` returns the selected item.
  - [ ] Reading a `JTable` (row-selection mode) inside `JFrame` returns row-level selection.
  - [ ] Reading a `JList` inside `JDialog` returns the selection.

### Component matrix

Each component from the verification matrix gets a dedicated test method.
Validated by probe tests (`AccessibleSelectionProbeTest`, `AccessibleSelectionProbeTest2`,
`AccessibleSelectionProbeTreeTest`) on 2026-04-07.

**Expected to succeed (`get_selection` supported):**
`JList`, `JTabbedPane`, `JComboBox`, `JTable` (row-selection mode only — the default)

**Expected to fail with "Component does not support get_selection" error:**
`JTree` (suppressed — tree-level `AccessibleSelection` is non-functional; see design notes), `JTable` (column/cell/no-selection modes — suppressed by BR-10), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
