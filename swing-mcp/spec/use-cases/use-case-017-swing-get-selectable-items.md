# UC-017: swing_get_selectable_items

---

**As an** AI agent, **I want to** enumerate all selectable items of a UI component by ref **so that** I can discover the available options in lists, combo boxes, tables, and tabbed panes before making a selection — especially when the snapshot truncated the component's children.

**Status:** Implemented
**Date:** 2026-04-08

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I see a component marked `single-selection` or `multi-selection` in the snapshot, but the snapshot may have truncated its children (e.g. a JList with 200 items shows only the first 5).
- I call `swing_get_selectable_items` with the `ref`, `offset`, and `length` parameters to page through the full list of selectable items.
- The tool returns a JSON object containing `totalCount` (total number of selectable items) and `items` (array of objects, each with `index` and `name`).
- I use the `index` values from the response to call `swing_set_selection` or to understand what the component contains.

**Tool description:** "List selectable items of a UI component by ref. Returns a paged JSON array of items (0-based index + name). Indices are in the selection item index space — pass them directly to swing_set_selection. For JTable, index is the row index and name is a pipe-separated summary of cell values. Requires offset and length parameters for paging. If offset+length is bigger than the amount of data available, fewer items than requested may be returned. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. `offset` is required and must be a non-negative integer (0 or greater). `length` is required and must be a non-negative integer (0 or greater). No upper cap is enforced on `length` — the AI client is responsible for managing its own context window. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support selection (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (UC-014 BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"Component does not support get_selectable_items. Call swing_snapshot or swing_get_cells to verify the list of actions."* Same detection logic as UC-014 BR-03. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_selectable_items` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — listing selectable items is always allowed, even on disabled components. |
| BR-07 | If `offset` is greater than or equal to `totalCount`, the `items` array is empty (not an error). This allows the AI to detect end-of-list. |
| BR-08 | The `items` array contains objects with `index` (integer — the 0-based item index in the selection item index space, suitable for passing directly to `swing_set_selection` / `addAccessibleSelection()`) and `name` (string or `null` — the accessible name of the item). |
| BR-09 | **JTable row enumeration.** When the target is a `JTable` in row-selection mode, items are **rows**, not cells. `totalCount` is the number of rows (`AccessibleTable.getAccessibleRowCount()`). Each item's `index` is the row index (0-based). Each item's `name` is built via `SwingUtils.buildTableRowText()` — pipe-separated cell accessible names for the first `MAX_ROW_NAME_COLUMNS` (10) columns (e.g. `"Alice \| 30 \| NY"`). `offset` and `length` refer to row indices. |
| BR-10 | **JComboBox item enumeration.** The item count is determined via `((JComboBox<?>) accessible).getItemCount()`, not `getAccessibleChildrenCount()` (which returns 1 — the popup menu). Items are enumerated via the combo box's internal `AccessibleSelection` API: `ac.getAccessibleChild(0)` returns the popup menu; its `getAccessibleChildrenCount()` returns the true item count; `getAccessibleChild(i)` on the popup returns the i-th item. Alternatively, iterate `addAccessibleSelection(i)` / read / `clearAccessibleSelection()` — but this is a mutation and is undesirable for a read-only tool. **Preferred approach:** use `JComboBox.getItemAt(i).toString()` for the name and `i` for the index, since the item index space is simply `[0, itemCount)`. |
| BR-11 | **Generic enumeration (JList, JTabbedPane).** For these components, `totalCount` is `ac.getAccessibleChildrenCount()`. Each item is obtained via `ac.getAccessibleChild(i)` where `i` ranges over `[offset, min(offset + length, totalCount))`. The item's `index` is `i` (children index = item index for these components — verified by UC-014 probe tests). The item's `name` is `child.getAccessibleContext().getAccessibleName()`. |
| BR-12 | **Disabled item indicator.** For JTabbedPane, if `((JTabbedPane) accessible).isEnabledAt(index)` returns `false`, the item object includes `"enabled": false`. When the tab is enabled, the `enabled` field is omitted (absence means enabled). For all other components (JList, JComboBox, JTable), the `enabled` field is never emitted — they have no standard per-item disable API, so all items are implicitly enabled. |
| BR-13 | **Null child.** If `getAccessibleChild(i)` returns `null` (defensive case), the item's `name` is `null`. Do not skip the entry — the index must remain consistent with the item index space. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref`, `offset`, or `length` is missing/wrong type; reject negative values).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsSelection(accessible)` — if `false`, fail with error (check `instanceof JTable` first for specific message).
4. **Determine totalCount and enumerate items** based on component type:

   a. **If the target is a `JTable`** (BR-09):
      - `totalCount = ac.getAccessibleTable().getAccessibleRowCount()`.
      - For each row `r` in `[offset, min(offset + length, totalCount))`:
        - Build `name` via `buildTableRowText(at, r, cols)`.
        - Add `{"index": r, "name": "Alice | 30 | NY"}`.

   b. **If the target is a `JComboBox`** (BR-10):
      - `totalCount = ((JComboBox<?>) accessible).getItemCount()`.
      - For each `i` in `[offset, min(offset + length, totalCount))`:
        - `Object item = ((JComboBox<?>) accessible).getItemAt(i)`.
        - `name = item != null ? item.toString() : null`.
        - Add `{"index": i, "name": name}`.

   c. **Otherwise (JList, JTabbedPane)** (BR-11):
      - `totalCount = ac.getAccessibleChildrenCount()`.
      - For each `i` in `[offset, min(offset + length, totalCount))`:
        - `Accessible child = ac.getAccessibleChild(i)`.
        - `name = child != null ? child.getAccessibleContext().getAccessibleName() : null`.
        - Add `{"index": i, "name": name}`.
      - **If `instanceof JTabbedPane`** (BR-12): if `!tabbedPane.isEnabledAt(i)`, add `"enabled": false` to the item. Omit the field when the tab is enabled.

5. Build and return the JSON object via `Content.json()`:
   ```json
   {
     "totalCount": 200,
     "items": [{"index": 0, "name": "Alpha"}, ...]
   }
   ```

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection (via `supportsSelection()`)
- `AccessibleContext.getAccessibleChildrenCount()` — item count (JList, JTabbedPane)
- `AccessibleContext.getAccessibleChild(int i)` — item enumeration (JList, JTabbedPane)
- `AccessibleContext.getAccessibleName()` — item name
- `AccessibleContext.getAccessibleTable()` — JTable-specific: row/column structure
- `AccessibleTable.getAccessibleRowCount()` — JTable-specific: total row count
- `AccessibleTable.getAccessibleColumnCount()` — JTable-specific: for `buildTableRowText()`
- `AccessibleTable.getAccessibleAt(int row, int col)` — JTable-specific: for building row name summaries
- `JComboBox.getItemCount()` — JComboBox-specific: true item count
- `JComboBox.getItemAt(int i)` — JComboBox-specific: item access
- `JTabbedPane.isEnabledAt(int i)` — JTabbedPane-specific: disabled tab detection

### Design notes

- **Relationship to `get_cells`.** `get_cells` / `get_cell_count` operate in the **accessible children index space** and are advertised only when the snapshot truncated a large data component. `get_selectable_items` operates in the **selection item index space** and is available on any component with `single-selection` or `multi-selection`. For JList and JTabbedPane, the two index spaces are identical. For JComboBox, they diverge (children index has only 1 child — the popup menu). For JTable, `get_cells` enumerates cells while `get_selectable_items` enumerates rows. The tools serve different purposes: `get_cells` is for content discovery (finding a button in a table), `get_selectable_items` is for selection browsing (seeing what can be selected).
- **Not listed in snapshot actions.** Per architecture.md § 6 "Selection Action Groups", `get_selectable_items` is not listed as a snapshot action. Its availability is documented in the tool description and is implied by the `single-selection` / `multi-selection` group labels.
- **Paging rationale.** `offset`/`length` are required parameters with no upper cap. The AI client is in charge of its own context window — if it wants to request all 10,000 rows at once, that's its choice. The server does not second-guess the client.
- **`buildTableRowText()` reuse.** The row name construction logic uses `SwingUtils.buildTableRowText()` (pipe-separated), shared by the snapshot tool (SC-6), selection tools (UC-014), and selectable-items tools.
- **Integer overflow.** When computing the iteration end index (`offset + length`), use `long` arithmetic to avoid overflow: `int end = (int) Math.min((long) offset + length, totalCount);`
- **JComboBox enumeration.** Using `JComboBox.getItemAt(i)` is the simplest and most reliable approach. The accessibility API path (`getAccessibleChild(0).getAccessibleContext().getAccessibleChild(i)`) navigates through the popup menu, which is fragile and may not work when the popup is closed. The direct `JComboBox` API works regardless of popup state.
- **JTree** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014.
- **`JMenuBar` / `JMenu`** — suppressed by `SUPPRESSED_SELECTION_ROLES`. Same as UC-014.

---

## Acceptance Criteria

- [x] Calling `swing_get_selectable_items` with a valid ref for a `JList`, `offset: 0`, `length: 5` returns all 5 items with correct indices and names.
- [x] Calling `swing_get_selectable_items` with `offset: 0, length: 50` on a 200-item `JList` returns the first 50 items and `totalCount: 200`.
- [x] Calling `swing_get_selectable_items` with `offset: 50, length: 50` on a 200-item `JList` returns items 50–99.
- [x] Calling `swing_get_selectable_items` with `offset` beyond `totalCount` returns an empty `items` array (not an error).
- [x] Calling `swing_get_selectable_items` with a valid ref for a `JTabbedPane` returns all tabs with indices and names.
- [x] A disabled tab in `JTabbedPane` has `"enabled": false` in its item object.
- [x] An enabled tab in `JTabbedPane` does **not** have the `enabled` field (absence means enabled).
- [x] Calling `swing_get_selectable_items` with a valid ref for a `JComboBox` returns all items with correct indices and names.
- [x] Calling `swing_get_selectable_items` with a valid ref for a `JTable` (row-selection mode) returns rows with pipe-separated cell values as names.
- [x] Calling `swing_get_selectable_items` on a `JTable` in column-selection mode returns an MCP error (unsupported).
- [x] Calling `swing_get_selectable_items` on a `JTree` returns an MCP error (suppressed).
- [x] Calling `swing_get_selectable_items` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_get_selectable_items` on a component that does not support selection (e.g. `JButton`) returns an MCP error.
- [x] The ref map is **not** cleared after a `swing_get_selectable_items` call (read-only tool).
- [x] Calling `swing_get_selectable_items` on a disabled component succeeds (no enabled check).
- [x] The `index` values are in the selection item index space: passing them to `swing_set_selection` selects the expected item.
- [x] Negative `offset` returns an MCP error.
- [x] Negative `length` returns an MCP error.
- [x] Missing `offset` or `length` returns an MCP error.
- [x] The `totalCount` field correctly reflects the total number of selectable items regardless of paging.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingGetSelectableItemsTest` (headless)
  - [x] Reading a `JList` with 5 items (`offset: 0, length: 5`) returns all 5 items with correct indices and names.
  - [x] Reading a `JList` with 200 items (`offset: 0, length: 50`) returns first 50 items with `totalCount: 200`.
  - [x] Reading a `JList` with `offset: 50, length: 50` returns items 50–99.
  - [x] Reading a `JList` with `offset` beyond item count returns empty `items` array.
  - [x] Reading a `JTabbedPane` returns tabs with indices and names.
  - [x] A disabled tab has `"enabled": false`; an enabled tab omits the `enabled` field.
  - [x] Reading a `JComboBox` returns all items with correct indices and names.
  - [x] Reading an empty `JComboBox` returns `totalCount: 0` and empty `items`.
  - [x] Reading a `JTable` (row-selection mode) returns rows with pipe-separated cell names.
  - [x] Reading a `JTable` with `offset` and `length` returns the correct row page.
  - [x] Reading a `JTable` in column-selection mode returns an MCP error.
  - [x] Reading a `JTable` in cell-selection mode returns an MCP error.
  - [x] Reading a `JTable` with no selection allowed returns an MCP error.
  - [x] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [x] Reading a component without selection support (e.g. `JButton`) returns an MCP error.
  - [x] Reading a `JTree` returns an MCP error (suppressed).
  - [x] The ref map is preserved after the call (verified by calling twice with the same ref).
  - [x] Reading a disabled `JList` succeeds.
  - [x] Negative `offset` returns an MCP error.
  - [x] Negative `length` returns an MCP error.
  - [x] Missing `offset` or `length` returns an MCP error.
  - [x] The `index` values round-trip with `swing_set_selection` (select by returned index, then `swing_get_selection` confirms).
  - [x] Each component from the component matrix is tested.

- [x] `SwingGetSelectableItemsScreenTest` (`testSwing` — requires display)
  - [x] Reading a `JList` with items inside `JFrame` returns all items.
  - [x] Reading a `JTabbedPane` inside `JFrame` returns tabs (disabled tabs have `"enabled": false`).
  - [x] Reading a `JComboBox` inside `JFrame` returns items.
  - [x] Reading a `JTable` (row-selection mode) inside `JFrame` returns rows.
  - [x] Reading a `JList` inside `JDialog` returns items.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_selectable_items` supported):**
`JList`, `JTabbedPane`, `JComboBox`, `JTable` (row-selection mode only — the default)

**Expected to fail with "Component does not support get_selectable_items" error:**
`JTree` (suppressed), `JTable` (column/cell/no-selection modes), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
