# UC-018: swing_get_selectable_items_count

---

**As an** AI agent, **I want to** get the total count of selectable items in a UI component by ref **so that** I can decide how to page through `swing_get_selectable_items` without first requesting any items — saving a round-trip when I only need the count.

**Status:** Approved
**Date:** 2026-04-08

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I see a component marked `single-selection` or `multi-selection` in the snapshot, but I don't know how many items it contains (the snapshot may have truncated its children).
- I call `swing_get_selectable_items_count` with the `ref` parameter.
- The tool returns the total count as a plain integer string (e.g. `"200"`).
- I use the count to decide whether and how to page through `swing_get_selectable_items`.

**Tool description:** "Get the total number of selectable items of a UI component by ref. Returns the count as a plain integer. For JTable, this is the row count (only row-selection mode is supported). Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support selection (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (UC-014 BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"Component does not support get_selectable_items_count. Call swing_snapshot to verify the list of actions."* Same detection logic as UC-014 BR-03. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_selectable_items_count` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | No enabled check is performed — counting selectable items is always allowed, even on disabled components. |
| BR-07 | The count is determined via `SwingUtils.getSelectableItemsCount(accessible)` — the same helper used by UC-017 — and returned as a plain integer string via `Content.text()`. For JTable: row count via `AccessibleTable.getAccessibleRowCount()`. For JComboBox: `JComboBox.getItemCount()`. For JList/JTabbedPane: `AccessibleContext.getAccessibleChildrenCount()`. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsSelection(accessible)` — if `false`, fail with error (check `instanceof JTable` first for specific message).
4. **BR-07** — compute `totalCount` via `SwingUtils.getSelectableItemsCount(accessible)`.
5. Return the count as a plain text string via `Content.text(String.valueOf(totalCount))` (e.g. `"200"`).

### Design notes

- **Thin wrapper.** This tool is essentially the first half of `swing_get_selectable_items` (UC-017) — same validation, same `getSelectableItemsCount()` call, but without item enumeration. It saves the AI a round-trip when it only needs the count to plan paging.
- **Not listed in snapshot actions.** Same as UC-017 — its availability is implied by the `single-selection` / `multi-selection` group labels and documented in the tool description.
- **JTree / JMenuBar / JMenu** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014/UC-017.

---

## Acceptance Criteria

- [ ] Calling `swing_get_selectable_items_count` with a valid ref for a `JList` with 5 items returns `5`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for a `JList` with 200 items returns `200`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for an empty `JList` returns `0`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for a `JTabbedPane` with 3 tabs returns `3`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for a `JComboBox` with 3 items returns `3`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for an empty `JComboBox` returns `0`.
- [ ] Calling `swing_get_selectable_items_count` with a valid ref for a `JTable` (row-selection mode) with 10 rows returns `10`.
- [ ] Calling `swing_get_selectable_items_count` on a `JTable` in column-selection mode returns an MCP error.
- [ ] Calling `swing_get_selectable_items_count` on a `JTree` returns an MCP error (suppressed).
- [ ] Calling `swing_get_selectable_items_count` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_get_selectable_items_count` on a component that does not support selection (e.g. `JButton`) returns an MCP error.
- [ ] The ref map is **not** cleared after a `swing_get_selectable_items_count` call (read-only tool).
- [ ] Calling `swing_get_selectable_items_count` on a disabled component succeeds.
- [ ] The returned count matches the `totalCount` field from `swing_get_selectable_items` for the same component.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingGetSelectableItemsCountTest` (headless)
  - [ ] `JList` with 5 items returns `5`.
  - [ ] `JList` with 200 items returns `200`.
  - [ ] Empty `JList` returns `0`.
  - [ ] `JTabbedPane` with 3 tabs returns `3`.
  - [ ] Empty `JTabbedPane` returns `0`.
  - [ ] `JComboBox` with 3 items returns `3`.
  - [ ] Empty `JComboBox` returns `0`.
  - [ ] `JTable` (row-selection mode) with 10 rows returns `10`.
  - [ ] `JTable` in column-selection mode returns an MCP error.
  - [ ] `JTable` in cell-selection mode returns an MCP error.
  - [ ] `JTable` with no selection allowed returns an MCP error.
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] `JButton` returns an MCP error.
  - [ ] `JTree` returns an MCP error (suppressed).
  - [ ] Ref map is preserved after the call (verified by calling twice with same ref).
  - [ ] Disabled `JList` succeeds.
  - [ ] Returned count matches `totalCount` from `swing_get_selectable_items` for same component.
  - [ ] Each component from the component matrix is tested.

- [ ] `SwingGetSelectableItemsCountScreenTest` (`testSwing` — requires display)
  - [ ] `JList` inside `JFrame` returns correct count.
  - [ ] `JTabbedPane` inside `JFrame` returns correct count.
  - [ ] `JComboBox` inside `JFrame` returns correct count.
  - [ ] `JTable` (row-selection mode) inside `JFrame` returns correct count.
  - [ ] `JList` inside `JDialog` returns correct count.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_selectable_items_count` supported):**
`JList`, `JTabbedPane`, `JComboBox`, `JTable` (row-selection mode only — the default)

**Expected to fail with "Component does not support get_selectable_items_count" error:**
`JTree` (suppressed), `JTable` (column/cell/no-selection modes), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
