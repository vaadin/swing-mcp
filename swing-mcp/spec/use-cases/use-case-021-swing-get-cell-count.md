# UC-021: swing_get_cell_count

**Status:** Implemented (amended 2026-04-13 — JTable removed)
**Date:** 2026-04-08

Returns the total cell count so the AI can plan paging through `swing_get_cells` without a throwaway first fetch.

**Tool description:** "Get the total number of accessible children (cells) of a large data component (JList, JTree) by ref. Returns the count as a plain integer in the same index space as swing_get_cells. For JList, this is the item count. For JTree, this is the top-level visible node count. For JTable, use swing_get_item_count instead — table cells are plain text labels with no actionable children. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_cell_count`, the tool returns an MCP-level error (`isError: true`). JTable targets receive the message *"JTable does not support get_cell_count. Use swing_get_item_count to get the row count."* All other unsupported targets receive *"Component does not support get_cell_count. Call swing_snapshot or swing_get_cells to verify the list of actions."* `get_cell_count` is supported when the component's role is `LIST` or `TREE` (`isGetCellsSupported` — same eligibility as UC-020 BR-03). `TABLE` is explicitly excluded — see UC-020 BR-03 for the rationale. No child count threshold is enforced at runtime. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_cell_count` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — counting cells is always allowed, even on disabled components. |
| BR-07 | The count is determined via `AccessibleContext.getAccessibleChildrenCount()` — the same method used by `swing_get_cells` to compute `totalChildren` — and returned as a plain integer string via `Content.text()`. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — eligibility check: verify the component's role is `LIST` or `TREE` (`isGetCellsSupported`). If the component is a `JTable`, return the JTable-specific error that redirects to `swing_get_item_count`. For any other unsupported target, return the generic error.
4. **BR-07** — compute `totalChildren` via `accessible.getAccessibleContext().getAccessibleChildrenCount()`.
5. Return the count as a plain text string via `Content.text(String.valueOf(totalChildren))` (e.g. `"2000"`).

### Design notes

- **Thin wrapper.** This tool is the count-only companion to `swing_get_cells` (UC-020), just as UC-018 (`swing_get_item_count`) is the count-only companion to UC-017 (`swing_get_items`). It saves the AI a round-trip when it only needs the count to plan paging.
- **Same index space as `swing_get_cells`.** The count returned matches the total children count that `swing_get_cells` reports in its header line. For JList, the count matches the item count. For JTree, it's the top-level visible node count.
- **JTable is excluded.** See UC-020 BR-03 design notes. For JTable row count, use `swing_get_item_count`.
- **Not listed in snapshot actions.** Its availability is implied by the `get_cells` action on large data components and documented in the tool description.
- **No child count threshold.** Same as UC-020 BR-03 — any supported large data component is accepted, regardless of child count.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingGetCellCountTest` (headless)
  - [x] `JTable` returns an MCP error redirecting to `swing_get_item_count`.
  - [x] `JList` with 200 items returns `200`.
  - [x] Empty `JList` returns `0`.
  - [x] `JTree` returns top-level visible node count.
  - [x] Non-truncated `JList` (3 items) succeeds.
  - [x] `JButton` returns an MCP error.
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] Error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Ref map is preserved after the call (verified by calling twice with same ref).
  - [x] Disabled `JList` succeeds.
  - [x] Returned count matches `total` from `swing_get_cells` header for same component.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingGetCellCountScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] `JTable` inside `JFrame` returns an MCP error redirecting to `swing_get_item_count`.
  - [x] `JList` inside `JFrame` returns correct count.
  - [x] `JList` inside `JDialog` returns correct count.
  - [ ] `JList` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) returns correct count.

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`get_cell_count` supported):** `JList`, `JTree` — any supported large data component regardless of child count.

**Fail with a JTable-specific error redirecting to `swing_get_item_count`:** `JTable`.

**Fail with "Component does not support get_cell_count":** all other matrix components.
