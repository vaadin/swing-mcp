# UC-018: swing_get_item_count

**Status:** Implemented (amended 2026-04-13 — JTable decoupled from selection-mode gate; any JTable is now a valid target. Amended 2026-04-13 per P-001 — JTabbedPane dropped as a supported target; the tab count is derivable from the snapshot, which renders every tab inline per UC-002 SC-2. Amended 2026-04-13 per P-001 Wave B — tool renamed from `swing_get_selectable_items_count` to `swing_get_item_count`.)
**Date:** 2026-04-08

Returns the total item count so the AI can plan paging through `swing_get_items` without a throwaway first fetch.

**Tool description:** "Get the total number of items of a UI component by ref. Supported components: JList, JComboBox, JTable. Returns the count as a plain integer. For JTable, this is the canonical way to get the row count regardless of selection mode (use this instead of swing_get_cell_count, which does not support JTable). Note: swing_set_selection still requires the table to be in row-selection mode. For JTabbedPane, count the tabs directly from the snapshot — each tab renders as `- (page_tab) N \"title\"` with its 0-based index (UC-002 SC-2). Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_item_count` (i.e. `SwingUtils.supportsGetItems(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message *"Component does not support get_item_count. Call swing_snapshot or swing_get_cells to verify the list of actions."* `supportsGetItems` accepts `JList`, `JComboBox`, and `JTable` only — **`JTabbedPane` is rejected** (dropped per P-001; tabs are inline in the snapshot per UC-002 SC-2), and JTable is accepted regardless of selection mode. Same gate as UC-017 BR-03. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_item_count` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — counting items is always allowed, even on disabled components. |
| BR-07 | The count is determined via `SwingUtils.getItemCount(accessible)` — the same helper used by UC-017 — and returned as a plain integer string via `Content.text()`. For JTable: row count via `AccessibleTable.getAccessibleRowCount()`. For JComboBox: `JComboBox.getItemCount()`. For JList: `AccessibleContext.getAccessibleChildrenCount()`. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsGetItems(accessible)` — if `false`, fail with the generic error. JTable never fails this gate (any selection mode is accepted).
4. **BR-07** — compute `totalCount` via `SwingUtils.getItemCount(accessible)`.
5. Return the count as a plain text string via `Content.text(String.valueOf(totalCount))` (e.g. `"200"`).

### Design notes

- **Thin wrapper.** This tool is essentially the first half of `swing_get_items` (UC-017) — same validation, same `getItemCount()` call, but without item enumeration. It saves the AI a round-trip when it only needs the count to plan paging.
- **Not listed in snapshot actions.** Same as UC-017 — its availability is implied by the `single-selection` / `multi-selection` group labels and documented in the tool description.
- **JTree / JMenuBar / JMenu** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014/UC-017.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingGetItemCountTest` (headless)
  - [x] `JList` with 5 items returns `5`.
  - [x] `JList` with 200 items returns `200`.
  - [x] Empty `JList` returns `0`.
  - [x] `JTabbedPane` returns an MCP error (regression guard — dropped per P-001).
  - [x] `JComboBox` with 3 items returns `3`.
  - [x] Empty `JComboBox` returns `0`.
  - [x] `JTable` (row-selection mode) with 10 rows returns `10`.
  - [x] `JTable` in column-selection mode succeeds and returns the row count.
  - [x] `JTable` in cell-selection mode succeeds and returns the row count.
  - [x] `JTable` with no selection allowed succeeds and returns the row count.
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] `JButton` returns an MCP error.
  - [x] `JTree` returns an MCP error (suppressed).
  - [x] Ref map is preserved after the call (verified by calling twice with same ref).
  - [x] Disabled `JList` succeeds.
  - [x] Returned count matches `totalCount` from `swing_get_items` for same component.
  - [x] Each component from the component matrix is tested.

- [x] `SwingGetItemCountScreenTest` (`testSwing` — requires display)
  - [x] `JList` inside `JFrame` returns correct count.
  - [x] `JTabbedPane` inside `JFrame` returns an MCP error (regression guard — dropped per P-001).
  - [x] `JComboBox` inside `JFrame` returns correct count.
  - [x] `JTable` (row-selection mode) inside `JFrame` returns correct count.
  - [x] `JList` inside `JDialog` returns correct count.
  - [ ] `JList` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) returns correct count.

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`get_item_count` supported):** `JList`, `JComboBox`, `JTable` (any selection mode — read path is selection-mode agnostic per BR-03).

**Fail with "Component does not support get_item_count":**
- `JTabbedPane` — dropped per P-001.
- `JTree` — suppressed.
- All other matrix components.
