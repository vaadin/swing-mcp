# UC-015: swing_set_selection

---

**As an** AI agent, **I want to** set the selection of a UI component by ref and item indices **so that** I can select items in lists, combo boxes, tables, and tabbed panes during Swing app migration.

**Status:** Draft
**Date:** 2026-04-07

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I optionally call `swing_get_selection` to learn what is currently selected.
- I call `swing_set_selection` with the `ref` parameter identifying the component and an `indices` parameter containing an array of 0-based item indices to select.
- The tool validates the ref, capability, enabled state, and selection mode compatibility, then clears the current selection and adds the requested indices via `AccessibleSelection` asynchronously. Returns `null` immediately.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

**Tool description:** "Set the selection of a UI component by ref. Pass 0-based item indices (as returned by swing_get_selection). For single-selection components, pass exactly one index. For JTable, pass row indices — the tool translates to cell indices internally. Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. The `indices` parameter is required and must be a JSON array of integers. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support selection (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (UC-014 BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"Component does not support set_selection. Call swing_snapshot to verify the list of actions."* **Implementation note:** same detection logic as UC-014 BR-03 — check `instanceof JTable` before the generic `supportsSelection()` check. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The selection mutation is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously (fire-and-forget). |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-06 | `swing_set_selection` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-07 | **Empty indices array.** If `indices` is an empty array `[]`, the tool clears the selection via `clearAccessibleSelection()` using fire-and-forget (`invokeLater`). **Exception: `JTabbedPane` with tabs.** If the target is a `JTabbedPane` (`instanceof JTabbedPane`) and `((JTabbedPane) accessible).getTabCount() > 0`, the tool returns an MCP-level error (`isError: true`) with the message *"This component does not allow the selection to be empty."* — because `clearAccessibleSelection()` is a no-op on a non-empty `JTabbedPane` (probe-tested 2026-04-07). An empty `JTabbedPane` (0 tabs) has no selection to clear and succeeds trivially. For all other components (`JList`, `JComboBox`, `JTable`), clearing works and is dispatched asynchronously. |
| BR-08 | **Single-selection enforcement.** If the component is in single-selection mode (`SwingUtils.supportsSingleSelection(accessible)` returns `true`) and `indices` contains more than one element, the tool returns an MCP-level error (`isError: true`) with the message *"Component is in single-selection mode. Pass exactly one index (or an empty array to clear)."* |
| BR-09 | The tool returns `null` (empty content array) on success, consistent with other mutation tools (`swing_click`, `swing_set_text`, `swing_set_value`). |
| BR-10 | **JTable row-to-cell translation.** When the target is a `JTable` in row-selection mode, the `indices` array contains **row indices** (matching the `index` field from `swing_get_selection` BR-11). The tool translates each row index `r` to cell indices by calling `addAccessibleSelection(r * cols + c)` for each column `c` in `[0, cols)`, where `cols = ac.getAccessibleTable().getAccessibleColumnCount()`. |
| BR-11 | **Index bounds validation.** Before performing the selection, the tool validates that every index in `indices` is within bounds: `0 <= index < itemCount`. For non-JTable components, `itemCount = as.getAccessibleSelectionCount()` is **not** used — instead, use the total number of selectable items. For JList, JTabbedPane, JComboBox, the item count is obtained from the component-specific API (see Algorithm). For JTable, the item count is the row count: `ac.getAccessibleTable().getAccessibleRowCount()`. If any index is out of bounds, the tool returns an MCP-level error (`isError: true`) with the message *"Index N is out of bounds. Valid range is [0, M)."* where N is the first invalid index and M is the item count. The bounds check runs after the single-selection check (BR-08). |
| BR-12 | **Negative index validation.** If any index in `indices` is negative, the tool returns an MCP-level error (`isError: true`) with the message *"Index N is out of bounds. Valid range is [0, M)."* This is caught by the same bounds check as BR-11. |
| BR-13 | **Duplicate indices.** Duplicate indices in the array are silently deduplicated (using insertion-ordered set). This is not an error. |
| BR-14 | **Disabled tab check (JTabbedPane only).** If the target is a `JTabbedPane` (`instanceof JTabbedPane`), the tool checks each requested index against `((JTabbedPane) accessible).isEnabledAt(index)`. If any tab is disabled, the tool returns an MCP-level error (`isError: true`) with the message *"Tab at index N is disabled."* where N is the first disabled index. This mirrors the real-user constraint: a user cannot click a disabled tab. This check runs after bounds validation (BR-11). Only JTabbedPane has per-item disable via the standard API (`setEnabledAt`); JList, JComboBox, and JTable have no equivalent — their `isEffectivelyEnabled` component-level check (BR-05) is sufficient. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` or `indices` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsSelection(accessible)` — if `false`, fail with error (check `instanceof JTable` first for specific message).
4. **BR-05** — `SwingUtils.isEffectivelyEnabled(accessible)` — if `false`, fail with "disabled" error.
5. Obtain `AccessibleSelection as = ac.getAccessibleSelection()`.
6. **BR-13** — deduplicate `indices` (insertion-ordered set).
7. **BR-07** — **if deduplicated indices is empty** (clear path):
   a. If `accessible instanceof JTabbedPane` and `((JTabbedPane) accessible).getTabCount() > 0`, fail with *"This component does not allow the selection to be empty."*
   b. Dispatch `as.clearAccessibleSelection()` via `SwingUtilities.invokeLater()` (fire-and-forget).
   c. Return `null` (success).
8. **BR-08** — if `SwingUtils.supportsSingleSelection(accessible)` and deduplicated indices size > 1, fail with "single-selection mode" error.
9. **Determine item count** for bounds checking:
   - **JTable:** `itemCount = ac.getAccessibleTable().getAccessibleRowCount()`.
   - **JComboBox:** `itemCount = ((JComboBox<?>) accessible).getItemCount()`. (JComboBox's `getAccessibleChildrenCount()` returns 1 — the popup menu — so it cannot be used.)
   - **All others:** `itemCount = ac.getAccessibleChildrenCount()`. (For JList and JTabbedPane, the accessible children count equals the item count — verified by probe tests.)
10. **BR-11 / BR-12** — validate all indices are in `[0, itemCount)`. If any index is out of bounds, fail with error naming the first invalid index.
11. **BR-14** — if `accessible instanceof JTabbedPane`, check `isEnabledAt(index)` for each index. If any tab is disabled, fail with *"Tab at index N is disabled."*
12. **Fire-and-forget dispatch** via `SwingUtilities.invokeLater()`:
    a. `as.clearAccessibleSelection()` — clear existing selection.
    b. **If the target is a JTable** — for each row index `r` in the deduplicated set: compute `cols = ac.getAccessibleTable().getAccessibleColumnCount()`, then call `as.addAccessibleSelection(r * cols + c)` for each `c` in `[0, cols)`.
    c. **Otherwise** — for each index `i` in the deduplicated set: call `as.addAccessibleSelection(i)`.
13. Return `null`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection and selection manipulation
- `AccessibleSelection.clearAccessibleSelection()` — clear existing selection before setting new one
- `AccessibleSelection.addAccessibleSelection(int i)` — select an item by index
- `AccessibleContext.getAccessibleTable()` — JTable-specific: row/column structure
- `AccessibleTable.getAccessibleRowCount()` — JTable-specific: for bounds validation
- `AccessibleTable.getAccessibleColumnCount()` — JTable-specific: for row-to-cell translation
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check
- `SwingUtils.supportsSelection(Accessible)` — capability check
- `SwingUtils.supportsSingleSelection(Accessible)` — single-selection mode check

### Design notes

- **Index space consistency.** `swing_set_selection` accepts indices in the same **item index space** that `swing_get_selection` returns. For non-JTable components, these are the 0-based item indices that `addAccessibleSelection(i)` expects directly. For JTable, these are **row indices** — the tool performs the row-to-cell translation internally (BR-10). This means the AI can take indices from `swing_get_selection` and pass them directly to `swing_set_selection` without any conversion.
- **Clear-then-add pattern.** The tool always clears the selection first, then adds the requested indices. This means `swing_set_selection` is a **replace** operation, not an **append**. If the AI wants to add to an existing selection, it must read the current selection via `swing_get_selection`, merge indices, and pass the combined set. This is simpler and less error-prone than separate add/remove semantics.
- **`JList`** — `addAccessibleSelection(i)` works correctly for both `SINGLE_SELECTION` and `MULTIPLE_INTERVAL_SELECTION` modes. In single-selection mode, adding a second index silently replaces the first — but BR-08 prevents this scenario by rejecting multi-index calls on single-selection components.
- **`JTabbedPane`** — always single-selection. `addAccessibleSelection(i)` switches to the tab at index `i`. The tab switch is immediate and fires a `ChangeEvent` on the `JTabbedPane`.
- **`JComboBox`** — always single-selection. `addAccessibleSelection(i)` selects the item at index `i` in the popup list. This works even when the popup is closed — the combo box updates its displayed value. Note: `clearAccessibleSelection()` on JComboBox may be a no-op (combo boxes always have a selection unless empty) — this is harmless.
- **`JTable`** — row-to-cell translation (BR-10) is the inverse of the cell-to-row aggregation in UC-014 BR-11. The tool selects all cells in each requested row by iterating columns, which matches what `JTable.setRowSelectionInterval()` would do at the Swing API level. The accessibility API does not expose a row-level selection method, so cell-by-cell selection is necessary.
- **`JTree`** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014.
- **`clearAccessibleSelection()` behaviour (probe-tested 2026-04-07).** `clearAccessibleSelection()` fully clears the selection on JList (single and multi), JComboBox (`selectedIndex=-1`), and JTable (single and multi row). On JTabbedPane with tabs it is a **no-op** — the selected tab remains unchanged, `selectionCount` stays at 1. BR-07 handles this with an `instanceof JTabbedPane` pre-check rather than a post-clear verification, to stay consistent with the fire-and-forget principle. JComboBox clears to no selection (which is a valid state — the display shows blank).
- **`Parameters.getIntArray()` prerequisite (implemented).** The `Parameters` class provides a `getIntArray(String key)` method that extracts a JSON array of integers from the raw parameter map. Gson deserializes JSON arrays as `List<?>` (with numbers as `Double`). The method: (1) checks that the value is a `List`; (2) checks each element is a `Number`; (3) validates each number is a whole number (`doubleValue() % 1 != 0` rejects fractionals — e.g. `2.7` is always a bug when the tool expects integers); (4) converts to `int` via `Number.intValue()`. Throws `MCPServerException(INVALID_PARAMS)` if the key is missing, the value is not a list, any element is not a number, or any element is fractional. The fractional error message includes the offending element index and value: `"Parameter 'indices' must be an array of integers, but element at index 1 is 2.7"`.

---

## Acceptance Criteria

- [ ] Calling `swing_set_selection` with a valid ref for a `JList` and a single index selects that item.
- [ ] Calling `swing_set_selection` with a valid ref for a `JList` (multi-selection) and multiple indices selects all specified items.
- [ ] Calling `swing_set_selection` with an empty `indices` array on a `JList` clears the selection.
- [ ] Calling `swing_set_selection` with an empty `indices` array on a `JComboBox` clears the selection (`selectedIndex=-1`).
- [ ] Calling `swing_set_selection` with an empty `indices` array on a `JTable` clears the selection.
- [ ] Calling `swing_set_selection` with an empty `indices` array on a `JTabbedPane` (with tabs) returns an MCP error: *"This component does not allow the selection to be empty."*
- [ ] Calling `swing_set_selection` with an empty `indices` array on an empty `JTabbedPane` (0 tabs) succeeds.
- [ ] Calling `swing_set_selection` with a valid ref for a `JTabbedPane` and one index switches to that tab.
- [ ] Calling `swing_set_selection` with a valid ref for a `JComboBox` and one index selects that item.
- [ ] Calling `swing_set_selection` with a valid ref for a `JTable` (row-selection mode) and row indices selects those rows (all cells in each row).
- [ ] Calling `swing_set_selection` with multiple indices on a single-selection component (e.g. `JTabbedPane`) returns an MCP error.
- [ ] Calling `swing_set_selection` with an out-of-bounds index returns an MCP error naming the invalid index and the valid range.
- [ ] Calling `swing_set_selection` with a negative index returns an MCP error.
- [ ] Calling `swing_set_selection` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_set_selection` on a component that does not support selection (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_set_selection` on a `JTree` returns an MCP error (suppressed).
- [ ] Calling `swing_set_selection` on a `JTable` in column-selection mode returns an MCP error.
- [ ] Calling `swing_set_selection` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] Calling `swing_set_selection` on a `JTabbedPane` with a disabled tab index returns an MCP error: *"Tab at index N is disabled."*
- [ ] The ref map is cleared after a successful `swing_set_selection` call (mutation tool).
- [ ] The ref map is cleared even after a failed `swing_set_selection` call that passed ref lookup.
- [ ] The tool returns `null` (empty content array) on success.
- [ ] Duplicate indices are silently deduplicated (selecting the same index twice does not cause an error).
- [ ] The selection set by `swing_set_selection` matches what `swing_get_selection` subsequently returns (round-trip).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `ParametersTest` update (implemented)
  - [x] `getIntArray()` returns a list of integers for a valid JSON array of whole numbers (from `Double` and `Integer`).
  - [x] `getIntArray()` returns an empty list for an empty array.
  - [x] `getIntArray()` throws `MCPServerException` when key is missing.
  - [x] `getIntArray()` throws `MCPServerException` when value is not a list.
  - [x] `getIntArray()` throws `MCPServerException` when value is a scalar number (not an array).
  - [x] `getIntArray()` throws `MCPServerException` when an element is not a number.
  - [x] `getIntArray()` throws `MCPServerException` when an element is fractional (e.g. `2.7`), with message naming the element index and value.

- [ ] `SwingSetSelectionTest` (headless)
  - [ ] Setting a `JList` (single-selection) to one index selects that item.
  - [ ] Setting a `JList` (multi-selection) to multiple indices selects all items.
  - [ ] Setting a `JList` with an empty array clears the selection.
  - [ ] Setting a `JComboBox` with an empty array clears the selection.
  - [ ] Setting a `JTable` with an empty array clears the selection.
  - [ ] Setting a `JTabbedPane` (with tabs) with an empty array returns an MCP error about non-empty selection.
  - [ ] Setting an empty `JTabbedPane` (0 tabs) with an empty array succeeds.
  - [ ] Setting a `JTabbedPane` to one index switches to that tab.
  - [ ] Setting a `JComboBox` to one index selects that item.
  - [ ] Setting a `JTable` (row-selection mode) to one row index selects that row.
  - [ ] Setting a `JTable` (row-selection mode) to multiple row indices selects those rows.
  - [ ] Setting with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Setting on a component without selection support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] Setting on a `JTree` returns an MCP error (suppressed).
  - [ ] Setting on a `JTable` in column-selection mode returns an MCP error with JTable-specific message.
  - [ ] Setting on a `JTable` in cell-selection mode returns an MCP error.
  - [ ] Setting on a `JTable` with no selection allowed returns an MCP error.
  - [ ] Setting on a disabled `JList` returns an MCP error explaining the component is disabled.
  - [ ] Setting on a `JTabbedPane` with a disabled tab returns an MCP error naming the disabled tab index.
  - [ ] Setting multiple indices on a single-selection `JTabbedPane` returns an MCP error.
  - [ ] Setting multiple indices on a single-selection `JComboBox` returns an MCP error.
  - [ ] Setting an out-of-bounds index (e.g. index 10 on a 3-item JList) returns an MCP error.
  - [ ] Setting a negative index returns an MCP error.
  - [ ] Duplicate indices are deduplicated silently (no error).
  - [ ] The ref map is cleared after a successful `swing_set_selection` call (verified by attempting to use the same ref again).
  - [ ] The ref map is cleared after a failed call on a disabled component.
  - [ ] Round-trip: `swing_set_selection` followed by `swing_get_selection` on same component returns the same indices.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingSetSelectionScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Setting a `JList` selection inside `JFrame` selects the item.
  - [ ] Setting a `JTabbedPane` selection inside `JFrame` switches to the tab.
  - [ ] Setting a `JComboBox` selection inside `JFrame` selects the item.
  - [ ] Setting a `JTable` (row-selection mode) selection inside `JFrame` selects the row.
  - [ ] Setting a `JList` selection inside `JDialog` selects the item.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`set_selection` supported):**
`JList`, `JTabbedPane`, `JComboBox`, `JTable` (row-selection mode only — the default)

**Expected to fail with "Component does not support set_selection" error:**
`JTree` (suppressed), `JTable` (column/cell/no-selection modes — suppressed by row-selection gate), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
