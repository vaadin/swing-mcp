# UC-021: swing_get_cell_count

---

**As an** AI agent, **I want to** get the total count of accessible children (cells) in a large data component by ref **so that** I can decide how to page through `swing_get_cells` without first requesting any cells — saving a round-trip when I only need the count.

**Status:** Draft
**Date:** 2026-04-08

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I see a large data component (JTable, JList, or JTree) with `get_cells` in its actions — this means its children were truncated by the snapshot.
- I call `swing_get_cell_count` with the `ref` parameter.
- The tool returns the total accessible children count as a plain integer string (e.g. `"2000"`).
- I use the count to decide whether and how to page through `swing_get_cells`. For JTable, I can compute the number of rows as `count / columnCount` if I know the column count from the snapshot.

**Tool description:** "Get the total number of accessible children (cells) of a large data component by ref. Returns the count as a plain integer. For JTable, this is rows × columns (individual cells in row-major order — same index space as swing_get_cells). For JList, this is the item count. For JTree, this is the top-level visible node count. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target is not a large data component, the tool returns an MCP-level error (`isError: true`) with the message "Component does not support get_cell_count. Call swing_snapshot or swing_get_cells to verify the list of actions." A large data component is one whose role is `TABLE`, `LIST`, or `TREE` (`isLargeDataComponent`). Same eligibility logic as UC-020 BR-03 — no child count threshold is enforced at runtime. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_cell_count` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — counting cells is always allowed, even on disabled components. |
| BR-07 | The count is determined via `AccessibleContext.getAccessibleChildrenCount()` — the same method used by `swing_get_cells` to compute `totalChildren` — and returned as a plain integer string via `Content.text()`. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — eligibility check: verify the component is a large data component (`isLargeDataComponent` — role is TABLE, LIST, or TREE). If not, return error.
4. **BR-07** — compute `totalChildren` via `accessible.getAccessibleContext().getAccessibleChildrenCount()`.
5. Return the count as a plain text string via `Content.text(String.valueOf(totalChildren))` (e.g. `"2000"`).

### Design notes

- **Thin wrapper.** This tool is the count-only companion to `swing_get_cells` (UC-020), just as UC-018 (`swing_get_selectable_items_count`) is the count-only companion to UC-017 (`swing_get_selectable_items`). It saves the AI a round-trip when it only needs the count to plan paging.
- **Same index space as `swing_get_cells`.** The count returned matches the total children count that `swing_get_cells` reports in its header line. For JTable, this is `rows × columns` (individual cells in row-major order), not the row count. For JList, the count matches the item count. For JTree, it's the top-level visible node count.
- **Not listed in snapshot actions.** Its availability is implied by the `get_cells` action on large data components and documented in the tool description.
- **No child count threshold.** Same as UC-020 BR-03 — any large data component is accepted, regardless of child count.

---

## Acceptance Criteria

- [ ] Calling `swing_get_cell_count` with a valid ref for a `JTable` with 10 rows and 5 columns returns `50`.
- [ ] Calling `swing_get_cell_count` with a valid ref for a `JList` with 200 items returns `200`.
- [ ] Calling `swing_get_cell_count` with a valid ref for an empty `JList` returns `0`.
- [ ] Calling `swing_get_cell_count` with a valid ref for a `JTree` returns the top-level visible node count.
- [ ] Calling `swing_get_cell_count` with a valid ref for a non-truncated `JList` (e.g. 3 items) succeeds — no child count threshold enforced.
- [ ] Calling `swing_get_cell_count` on a non-large-data component (e.g. `JButton`, `JPanel`) returns an MCP error.
- [ ] Calling `swing_get_cell_count` with an invalid ref returns an MCP error with a recovery message.
- [ ] The ref map is **not** cleared after a `swing_get_cell_count` call (read-only tool).
- [ ] Calling `swing_get_cell_count` on a disabled component succeeds.
- [ ] The returned count matches the `total` from `swing_get_cells`'s header line for the same component.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingGetCellCountTest` (headless)
  - [ ] `JTable` with 10 rows and 5 columns returns `50`.
  - [ ] `JList` with 200 items returns `200`.
  - [ ] Empty `JList` returns `0`.
  - [ ] `JTree` returns top-level visible node count.
  - [ ] Non-truncated `JList` (3 items) succeeds.
  - [ ] `JButton` returns an MCP error.
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] Error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Ref map is preserved after the call (verified by calling twice with same ref).
  - [ ] Disabled `JList` succeeds.
  - [ ] Returned count matches `total` from `swing_get_cells` header for same component.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingGetCellCountScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] `JTable` inside `JFrame` returns correct count.
  - [ ] `JList` inside `JFrame` returns correct count.
  - [ ] `JTable` inside `JDialog` returns correct count.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_cell_count` supported — any large data component regardless of child count):**
`JTable`, `JList`, `JTree`

**Expected to fail with "Component does not support get_cell_count" error:**
`JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JComboBox`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
