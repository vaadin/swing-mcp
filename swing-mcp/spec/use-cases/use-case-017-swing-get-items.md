# UC-017: swing_get_items

**Status:** Implemented (amended 2026-04-13 — JTable decoupled from selection-mode gate; any JTable is now a valid target. Amended 2026-04-13 per P-001 — JTabbedPane dropped as a supported target; tabs are read inline from the snapshot via UC-002 SC-2. Amended 2026-04-13 per P-001 Wave B — tool renamed from `swing_get_selectable_items` to `swing_get_items`; the old name no longer overpromises on selectability.)
**Date:** 2026-04-08

Pages through all items of a `JList` / `JComboBox` / `JTable`. Necessary when the snapshot truncated the component's children (SC-3) and the AI needs to see beyond the first few entries.

**Tool description:** "List items of a UI component by ref. Supported components: JList, JComboBox, JTable. Returns a paged JSON array of items (0-based index + name). Indices are in the selection item index space — pass them directly to swing_set_selection. For JTable, this is the canonical way to page through rows regardless of selection mode: index is the row index and name is a pipe-separated summary of cell values (use this instead of swing_get_cells, which does not support JTable). For JTabbedPane, use the swing_snapshot tool — each tab already renders as `- (page_tab) N \"title\"` with its 0-based index and `[disabled]` / `[selected]` state; pass the index straight to swing_set_selection as `[N]`. Requires offset and length parameters for paging. If offset+length is bigger than the amount of data available, fewer items than requested may be returned. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. `offset` is required and must be a non-negative integer (0 or greater). `length` is required and must be a non-negative integer (0 or greater). No upper cap is enforced on `length` — the AI client is responsible for managing its own context window. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_items` (i.e. `SwingUtils.supportsGetItems(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message *"Component does not support get_items. Call swing_snapshot or swing_get_cells to verify the list of actions."* `supportsGetItems` accepts **`JList`, `JComboBox`, and `JTable`** only. Two explicit exclusions: (a) **`JTabbedPane` is rejected** — tabs are UI structure, not data, and are already rendered in the snapshot with their 0-based index and `[disabled]`/`[selected]` state (UC-002 SC-2). The AI passes the inline index straight to `swing_set_selection`. (b) **`JTable` is accepted regardless of selection mode** (row / column / cell / no-selection) — this decouples read-only row enumeration from the row-selection gate that `swing_set_selection` / `swing_clear_selection` / `swing_select_all` still enforce, because after UC-020's JTable ban these two read tools are the only paged content-access path for JTables in non-row-selection modes. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_items` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — listing items is always allowed, even on disabled components. |
| BR-07 | If `offset` is greater than or equal to `totalCount`, the `items` array is empty (not an error). This allows the AI to detect end-of-list. |
| BR-08 | The `items` array contains objects with `index` (integer — the 0-based item index in the selection item index space, suitable for passing directly to `swing_set_selection` / `addAccessibleSelection()`) and `name` (string or `null` — the accessible name of the item). |
| BR-09 | **JTable row enumeration.** When the target is a `JTable` (any selection mode — row / column / cell / none), items are **rows**, not cells. `totalCount` is the number of rows (`AccessibleTable.getAccessibleRowCount()`). Each item's `index` is the row index (0-based). Each item's `name` is built via `SwingUtils.buildTableRowText()` — pipe-separated cell accessible names for the first `MAX_ROW_NAME_COLUMNS` (10) columns (e.g. `"Alice \| 30 \| NY"`). `offset` and `length` refer to row indices. Note that the returned row `index` values can only round-trip with `swing_set_selection` when the table is in row-selection mode; in other modes the indices are still valid as read handles but cannot be used to select anything. |
| BR-10 | **JComboBox item enumeration.** The item count is determined via `((JComboBox<?>) accessible).getItemCount()`, not `getAccessibleChildrenCount()` (which returns 1 — the popup menu). Items are enumerated via the combo box's internal `AccessibleSelection` API: `ac.getAccessibleChild(0)` returns the popup menu; its `getAccessibleChildrenCount()` returns the true item count; `getAccessibleChild(i)` on the popup returns the i-th item. Alternatively, iterate `addAccessibleSelection(i)` / read / `clearAccessibleSelection()` — but this is a mutation and is undesirable for a read-only tool. **Preferred approach:** use `JComboBox.getItemAt(i).toString()` for the name and `i` for the index, since the item index space is simply `[0, itemCount)`. |
| BR-11 | **Generic enumeration (JList).** For `JList`, `totalCount` is `ac.getAccessibleChildrenCount()`. Each item is obtained via `ac.getAccessibleChild(i)` where `i` ranges over `[offset, min(offset + length, totalCount))`. The item's `index` is `i` (children index = item index — verified by UC-014 probe tests). The item's `name` is `child.getAccessibleContext().getAccessibleName()`. |
| ~~BR-12~~ | ~~Disabled item indicator (JTabbedPane-specific).~~ **Removed 2026-04-13 per P-001** — JTabbedPane is no longer a supported target (see BR-03). Disabled-tab state is now surfaced inline in the snapshot via UC-002 SC-2 + SC-4. No component left under `get_items` has a standard per-item disable API, so the `enabled` field is never emitted. |
| BR-13 | **Null child.** If `getAccessibleChild(i)` returns `null` (defensive case), the item's `name` is `null`. Do not skip the entry — the index must remain consistent with the item index space. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref`, `offset`, or `length` is missing/wrong type; reject negative values).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsGetItems(accessible)` — if `false`, fail with the generic error. JTable never fails this gate (any selection mode is accepted).
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

   c. **Otherwise (JList)** (BR-11):
      - `totalCount = ac.getAccessibleChildrenCount()`.
      - For each `i` in `[offset, min(offset + length, totalCount))`:
        - `Accessible child = ac.getAccessibleChild(i)`.
        - `name = child != null ? child.getAccessibleContext().getAccessibleName() : null`.
        - Add `{"index": i, "name": name}`.

5. Build and return the JSON object via `Content.json()`:
   ```json
   {
     "totalCount": 200,
     "items": [{"index": 0, "name": "Alpha"}, ...]
   }
   ```

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — detection (via `supportsSelection()`)
- `AccessibleContext.getAccessibleChildrenCount()` — item count (JList)
- `AccessibleContext.getAccessibleChild(int i)` — item enumeration (JList)
- `AccessibleContext.getAccessibleName()` — item name
- `AccessibleContext.getAccessibleTable()` — JTable-specific: row/column structure
- `AccessibleTable.getAccessibleRowCount()` — JTable-specific: total row count
- `AccessibleTable.getAccessibleColumnCount()` — JTable-specific: for `buildTableRowText()`
- `AccessibleTable.getAccessibleAt(int row, int col)` — JTable-specific: for building row name summaries
- `JComboBox.getItemCount()` — JComboBox-specific: true item count
- `JComboBox.getItemAt(int i)` — JComboBox-specific: item access

### Design notes

- **Relationship to `get_cells`.** `get_cells` / `get_cell_count` operate in the **accessible children index space** and are advertised only when the snapshot truncated a large data component. `get_items` operates in the **selection item index space** and is available on any component with `single-selection` or `multi-selection` (plus any JTable — see BR-03). For JList, the two index spaces are identical. For JComboBox, they diverge (children index has only 1 child — the popup menu). The tools serve different purposes: `get_cells` is for content discovery that *returns actionable refs* (finding a button inside a list cell renderer), `get_items` is for text-only selection browsing (seeing what can be selected). **For JTable, `get_items` is the canonical content-access tool** — `get_cells` does not support JTable (see UC-020 BR-03). Table cell renderers are stamp-painted via `CellRendererPane` and surface as plain text `LABEL`s, so `get_cells` can never return an actionable ref for a JTable; the row-based `get_items` output is both more informative and consistent with the snapshot's row-based view (UC-002 SC-6).
- **Not listed in snapshot actions.** Per architecture.md § 6 "Selection Action Groups", `get_items` is not listed as a snapshot action. Its availability is documented in the tool description and is implied by the `single-selection` / `multi-selection` group labels. **Discoverability caveat for JTable.** A JTable in column-selection, cell-selection or no-selection mode does *not* carry the `single-selection` / `multi-selection` group label in the snapshot (because `supportsSelection` is false for those modes), yet `get_items` still works on it (BR-03). The AI learns this from the tool description, which is sent once at session start.
- **Paging rationale.** `offset`/`length` are required parameters with no upper cap. The AI client is in charge of its own context window — if it wants to request all 10,000 rows at once, that's its choice. The server does not second-guess the client.
- **`buildTableRowText()` reuse.** The row name construction logic uses `SwingUtils.buildTableRowText()` (pipe-separated), shared by the snapshot tool (SC-6), selection tools (UC-014), and `get_items` / `get_item_count`.
- **Integer overflow.** When computing the iteration end index (`offset + length`), use `long` arithmetic to avoid overflow: `int end = (int) Math.min((long) offset + length, totalCount);`
- **JComboBox enumeration.** Using `JComboBox.getItemAt(i)` is the simplest and most reliable approach. The accessibility API path (`getAccessibleChild(0).getAccessibleContext().getAccessibleChild(i)`) navigates through the popup menu, which is fragile and may not work when the popup is closed. The direct `JComboBox` API works regardless of popup state.
- **JTree** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014.
- **`JMenuBar` / `JMenu`** — suppressed by `SUPPRESSED_SELECTION_ROLES`. Same as UC-014.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingGetItemsTest` (headless)
  - [x] Reading a `JList` with 5 items (`offset: 0, length: 5`) returns all 5 items with correct indices and names.
  - [x] Reading a `JList` with 200 items (`offset: 0, length: 50`) returns first 50 items with `totalCount: 200`.
  - [x] Reading a `JList` with `offset: 50, length: 50` returns items 50–99.
  - [x] Reading a `JList` with `offset` beyond item count returns empty `items` array.
  - [x] Reading a `JTabbedPane` returns an MCP error (regression guard — tabs are no longer a supported target; dropped per P-001).
  - [x] Reading a `JComboBox` returns all items with correct indices and names.
  - [x] Reading an empty `JComboBox` returns `totalCount: 0` and empty `items`.
  - [x] Reading a `JTable` (row-selection mode) returns rows with pipe-separated cell names.
  - [x] Reading a `JTable` with `offset` and `length` returns the correct row page.
  - [x] Reading a `JTable` in column-selection mode succeeds and returns rows.
  - [x] Reading a `JTable` in cell-selection mode succeeds and returns rows.
  - [x] Reading a `JTable` with no selection allowed succeeds and returns rows.
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

- [x] `SwingGetItemsScreenTest` (`testSwing` — requires display)
  - [x] Reading a `JList` with items inside `JFrame` returns all items.
  - [x] Reading a `JTabbedPane` inside `JFrame` returns an MCP error (regression guard — dropped per P-001).
  - [x] Reading a `JComboBox` inside `JFrame` returns items.
  - [x] Reading a `JTable` (row-selection mode) inside `JFrame` returns rows.
  - [x] Reading a `JList` inside `JDialog` returns items.
  - [ ] Reading a `JList` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) returns items.

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`get_items` supported):** `JList`, `JComboBox`, `JTable` (any selection mode — read path is selection-mode agnostic per BR-03).

**Fail with "Component does not support get_items":**
- `JTabbedPane` — dropped per P-001; tabs render inline in the snapshot.
- `JTree` — suppressed.
- All other matrix components.
