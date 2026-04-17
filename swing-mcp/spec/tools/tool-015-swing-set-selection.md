# T-015: swing_set_selection

**Status:** Implemented
**Date:** 2026-04-07

Replaces a component's selection with a caller-supplied array of item indices (row indices for `JTable`). Paired with `swing_get_selection` — the indices round-trip cleanly.

**Tool description:** "Set the selection of a UI component by ref. Pass 0-based item indices (as returned by swing_get_selection). For single-selection components, pass at most one index. For JTable, pass row indices — the tool translates to cell indices internally. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. The `indices` parameter is required and must be a JSON array of integers. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support selection (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (T-014 BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"<ClassName> does not support set_selection. Call swing_snapshot or swing_get_cells to verify the list of actions."* **Implementation note:** same detection logic as T-014 BR-03 — check `instanceof JTable` before the generic `supportsSelection()` check. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The selection mutation is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously (fire-and-forget). |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-06 | `swing_set_selection` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after successful invocation. A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map — the UI state hasn't changed, so existing refs remain valid and the AI can retry without re-snapshotting. |
| BR-07 | **Empty indices array.** If `indices` is an empty array `[]`, the tool clears the selection via `clearAccessibleSelection()` using fire-and-forget (`invokeLater`). **Exception: `JTabbedPane` with tabs.** If the target is a `JTabbedPane` (`instanceof JTabbedPane`) and `((JTabbedPane) accessible).getTabCount() > 0`, the tool returns an MCP-level error (`isError: true`) with the message *"This component does not allow the selection to be empty."* — because `clearAccessibleSelection()` is a no-op on a non-empty `JTabbedPane` (probe-tested 2026-04-07). An empty `JTabbedPane` (0 tabs) has no selection to clear and succeeds trivially. For all other components (`JList`, `JComboBox`, `JTable`), clearing works and is dispatched asynchronously. |
| BR-08 | **Single-selection enforcement.** If the component is in single-selection mode (`SwingUtils.supportsSingleSelection(accessible)` returns `true`) and `indices` contains more than one element, the tool returns an MCP-level error (`isError: true`) with the message *"Component is in single-selection mode. Pass exactly one index (or an empty array to clear)."* |
| BR-09 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Dispatched set-selection on ref=<N> to [<idx>, <idx>, …] — call swing_snapshot to verify the outcome` — JSON-style array of the **deduplicated** indices (BR-13), bare numbers, truncated when the rendered value exceeds 15 characters (≤15 → full; else `[2, 5, 8, …]`). For an empty `indices` array (clear path), the echo is `Dispatched set-selection on ref=<N> to [] — call swing_snapshot to verify the outcome`. See **DR-010** for value-rendering rules. |
| BR-10 | **JTable row selection.** When the target is a `JTable` in row-selection mode, the `indices` array contains **row indices** (matching the `index` field from `swing_get_selection` BR-11). The tool uses `JTable.addRowSelectionInterval(row, row)` directly rather than `AccessibleSelection.addAccessibleSelection()`, because the latter delegates to `changeSelection()` which is unreliable inside `invokeLater()` (selection silently not applied — probe-tested 2026-04-07). |
| BR-11 | **Index bounds validation.** Before performing the selection, the tool validates that every index in `indices` is within bounds: `0 <= index < itemCount`. The item count is determined per component type: **JTable** uses `ac.getAccessibleTable().getAccessibleRowCount()`; **JComboBox** uses `((JComboBox<?>) accessible).getItemCount()`; **all others** use `ac.getAccessibleChildrenCount()` (see Algorithm). If any index is out of bounds, the tool returns an MCP-level error (`isError: true`) with the message *"Index N is out of bounds. Valid range is [0, M)."* where N is the first invalid index and M is the item count. The bounds check runs after the single-selection check (BR-08). |
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
    a. **If the target is a JTable** — call `table.clearSelection()`, then for each row index `r` in the deduplicated set: call `table.addRowSelectionInterval(r, r)`. Uses the direct JTable API because `AccessibleSelection.addAccessibleSelection()` is unreliable inside `invokeLater()`.
    b. **Otherwise** — call `as.clearAccessibleSelection()`, then for each index `i` in the deduplicated set: call `as.addAccessibleSelection(i)`.
13. Return `null`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection and selection manipulation
- `AccessibleSelection.clearAccessibleSelection()` — clear existing selection (non-JTable path)
- `AccessibleSelection.addAccessibleSelection(int i)` — select an item by index (non-JTable path)
- `AccessibleContext.getAccessibleTable()` — JTable-specific: for bounds validation (`getAccessibleRowCount()`)
- `JTable.clearSelection()` — JTable-specific: clear existing selection (direct API, used because `AccessibleSelection` is unreliable inside `invokeLater`)
- `JTable.addRowSelectionInterval(int, int)` — JTable-specific: select a row (direct API)
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check
- `SwingUtils.supportsSelection(Accessible)` — capability check
- `SwingUtils.supportsSingleSelection(Accessible)` — single-selection mode check

### Design notes

- **Index space consistency.** `swing_set_selection` accepts indices in the same **item index space** that `swing_get_selection` returns. For non-JTable components, these are the 0-based item indices that `addAccessibleSelection(i)` expects directly. For JTable, these are **row indices** — the tool performs the row-to-cell translation internally (BR-10). This means the AI can take indices from `swing_get_selection` and pass them directly to `swing_set_selection` without any conversion.
- **Clear-then-add pattern.** The tool always clears the selection first, then adds the requested indices. This means `swing_set_selection` is a **replace** operation, not an **append**. If the AI wants to add to an existing selection, it must read the current selection via `swing_get_selection`, merge indices, and pass the combined set. This is simpler and less error-prone than separate add/remove semantics.
- **`JList`** — `addAccessibleSelection(i)` works correctly for both `SINGLE_SELECTION` and `MULTIPLE_INTERVAL_SELECTION` modes. In single-selection mode, adding a second index silently replaces the first — but BR-08 prevents this scenario by rejecting multi-index calls on single-selection components.
- **`JTabbedPane`** — always single-selection. `addAccessibleSelection(i)` switches to the tab at index `i`. The tab switch is immediate and fires a `ChangeEvent` on the `JTabbedPane`.
- **`JComboBox`** — always single-selection. `addAccessibleSelection(i)` selects the item at index `i` in the popup list. This works even when the popup is closed — the combo box updates its displayed value. `clearAccessibleSelection()` sets `selectedIndex=-1` (probe-tested 2026-04-07), leaving the combo box with no selection (blank display). The clear-then-add in the non-empty path is harmless — the add immediately follows.
- **`JTable`** — the tool uses `JTable.clearSelection()` and `JTable.addRowSelectionInterval(row, row)` directly instead of the accessibility API (`addAccessibleSelection`). This is necessary because `AccessibleSelection.addAccessibleSelection()` delegates to `JTable.changeSelection()`, which is unreliable inside `SwingUtilities.invokeLater()` — the selection is silently not applied (verified by probe test, 2026-04-07). The direct JTable API works correctly inside `invokeLater`. The row indices from the AI map directly to `addRowSelectionInterval` — no cell-index translation is needed.
- **`JTree`** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as T-014.
- **`clearAccessibleSelection()` behaviour (probe-tested 2026-04-07).** `clearAccessibleSelection()` fully clears the selection on JList (single and multi), JComboBox (`selectedIndex=-1`), and JTable (single and multi row). On JTabbedPane with tabs it is a **no-op** — the selected tab remains unchanged, `selectionCount` stays at 1. BR-07 handles this with an `instanceof JTabbedPane` pre-check rather than a post-clear verification, to stay consistent with the fire-and-forget principle. JComboBox clears to no selection (which is a valid state — the display shows blank).
- **`Parameters.getIntArray()` prerequisite (implemented).** The `Parameters` class provides a `getIntArray(String key)` method that extracts a JSON array of integers from the raw parameter map. Gson deserializes JSON arrays as `List<?>` (with numbers as `Double`). The method: (1) checks that the value is a `List`; (2) checks each element is a `Number`; (3) validates each number is a whole number (`doubleValue() % 1 != 0` rejects fractionals — e.g. `2.7` is always a bug when the tool expects integers); (4) converts to `int` via `Number.intValue()`. Throws `MCPServerException(INVALID_PARAMS)` if the key is missing, the value is not a list, any element is not a number, or any element is fractional. The fractional error message includes the offending element index and value: `"Parameter 'indices' must be an array of integers, but element at index 1 is 2.7"`.
- **`Parameters.getInt()` / `getIntOrNull()` fix (implemented).** These methods previously silently truncated fractional values (e.g. `3.7` → `3`). Fixed during T-015 to reject non-whole numbers with the same `doubleValue() % 1 != 0` check, consistent with `getIntArray()`. Error message: `"Parameter 'ref' must be an integer, got 3.7"`.
- **Per-item disable.** Only `JTabbedPane` has a standard per-item disable API (`setEnabledAt(int, boolean)`). `JList`, `JComboBox`, and `JTable` have no equivalent — disabling individual items requires custom renderers and selection model overrides, which are not part of the standard API. Therefore BR-14 (disabled-item check) is scoped to JTabbedPane only.

### Probe test findings (2026-04-07)

#### `clearAccessibleSelection()` behaviour

| Component | Selection before | Effect of `clearAccessibleSelection()` | `selectionCount` after | Model state after |
|---|---|---|---|---|
| `JList` (SINGLE_SELECTION) | index=2 | **Clears** | 0 | `selectedIndex=-1`, `isSelectionEmpty=true` |
| `JList` (MULTIPLE_INTERVAL_SELECTION) | indices=[0,2,3] | **Clears** | 0 | `selectedIndices=[]`, `isSelectionEmpty=true` |
| `JTabbedPane` | index=1 | **No-op** — selection unchanged | 1 (unchanged) | `selectedIndex=1` (unchanged) |
| `JComboBox` | index=2 | **Clears** | 0 | `selectedIndex=-1`, `selectedItem=null` |
| `JTable` (row-selection, multi) | rows=[1,2] | **Clears** | 0 | `selectedRows=[]`, `selectionEmpty=true` |
| `JTable` (row-selection, single) | rows=[1] | **Clears** | 0 | `selectedRows=[]`, `selectionEmpty=true` |

#### `AccessibleState.MULTISELECTABLE` on JList

| JList selection mode | `MULTISELECTABLE` in state set? |
|---|---|
| `SINGLE_SELECTION` | No |
| `SINGLE_INTERVAL_SELECTION` | Yes |
| `MULTIPLE_INTERVAL_SELECTION` | Yes |

`MULTISELECTABLE` correctly distinguishes single vs. multi, but does not distinguish contiguous-only vs. arbitrary ranges.

#### `addAccessibleSelection()` on JTable inside `invokeLater`

`JTable.AccessibleJTable.addAccessibleSelection(int i)` delegates to `JTable.changeSelection()`. When called synchronously on the EDT, it works correctly. However, when called inside `SwingUtilities.invokeLater()`, the selection is **silently not applied** — `getSelectedRows()` returns an empty array. The direct JTable API (`addRowSelectionInterval`) works correctly inside `invokeLater`. This is a JDK quirk, likely related to `changeSelection()` relying on event dispatch state that is not available inside a `invokeLater` block. The tool uses the direct API for JTable as a workaround.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `ParametersTest` update (implemented)
  - [x] `getIntArray()` returns a list of integers for a valid JSON array of whole numbers (from `Double` and `Integer`).
  - [x] `getIntArray()` returns an empty list for an empty array.
  - [x] `getIntArray()` throws `MCPServerException` when key is missing.
  - [x] `getIntArray()` throws `MCPServerException` when value is not a list.
  - [x] `getIntArray()` throws `MCPServerException` when value is a scalar number (not an array).
  - [x] `getIntArray()` throws `MCPServerException` when an element is not a number.
  - [x] `getIntArray()` throws `MCPServerException` when an element is fractional (e.g. `2.7`), with message naming the element index and value.

- [x] `SwingSetSelectionTest` (headless)
  - [x] Setting a `JList` (single-selection) to one index selects that item.
  - [x] Setting a `JList` (multi-selection) to multiple indices selects all items.
  - [x] Setting a `JList` with an empty array clears the selection.
  - [x] Setting a `JComboBox` with an empty array clears the selection.
  - [x] Setting a `JTable` with an empty array returns success (addAccessibleSelection is no-op in headless — verified in screen test).
  - [x] Setting a `JTabbedPane` (with tabs) with an empty array returns an MCP error about non-empty selection.
  - [x] Setting an empty `JTabbedPane` (0 tabs) with an empty array succeeds.
  - [x] Setting a `JTabbedPane` to one index switches to that tab.
  - [x] Setting a `JComboBox` to one index selects that item.
  - [x] Setting a `JTable` (row-selection mode) to one row index returns success (selection verified in screen test).
  - [x] Setting a `JTable` (row-selection mode) to multiple row indices returns success (selection verified in screen test).
  - [x] Setting with an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Setting on a component without selection support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Setting on a `JTree` returns an MCP error (suppressed).
  - [x] Setting on a `JTable` in column-selection mode returns an MCP error with JTable-specific message.
  - [x] Setting on a `JTable` in cell-selection mode returns an MCP error.
  - [x] Setting on a `JTable` with no selection allowed returns an MCP error.
  - [x] Setting on a disabled `JList` returns an MCP error explaining the component is disabled.
  - [x] Setting on a `JTabbedPane` with a disabled tab returns an MCP error naming the disabled tab index.
  - [x] Setting multiple indices on a single-selection `JTabbedPane` returns an MCP error.
  - [x] Setting multiple indices on a single-selection `JComboBox` returns an MCP error.
  - [x] Setting an out-of-bounds index (e.g. index 10 on a 3-item JList) returns an MCP error.
  - [x] Setting a negative index returns an MCP error.
  - [x] Duplicate indices are deduplicated silently (no error).
  - [x] The ref map is cleared after a successful `swing_set_selection` call (verified by attempting to use the same ref again).
  - [x] The ref map is preserved after a failed call on a disabled component (refs remain valid for retry).
  - [x] Round-trip: `swing_set_selection` followed by `swing_get_selection` on same component returns the same indices.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingSetSelectionScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Setting a `JList` selection inside `JFrame` selects the item.
  - [x] Setting a `JTabbedPane` selection inside `JFrame` switches to the tab.
  - [x] Setting a `JComboBox` selection inside `JFrame` selects the item.
  - [x] Setting a `JTable` (row-selection mode) selection inside `JFrame` selects the row.
  - [x] Setting a `JList` selection inside `JDialog` selects the item.
  - [x] Setting a `JList` selection inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) selects the item.

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`set_selection` supported):** `JList`, `JTabbedPane`, `JComboBox`, `JTable` (row-selection mode only — the default).

**Fail with "<ClassName> does not support set_selection":**
- `JTree` — suppressed.
- `JTable` in column/cell/no-selection modes — suppressed by row-selection gate.
- All other matrix components.
