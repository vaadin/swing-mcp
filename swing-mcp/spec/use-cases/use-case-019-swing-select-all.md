# UC-019: swing_select_all

---

**As an** AI agent, **I want to** select all items in a multi-selection UI component by ref **so that** I can quickly select everything in a list or table without enumerating all indices manually.

**Status:** Approved
**Date:** 2026-04-08

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I see a component marked `multi-selection` in the snapshot (e.g. a `JList` in `MULTIPLE_INTERVAL_SELECTION` mode, or a `JTable` in multi-row-selection mode).
- I call `swing_select_all` with the `ref` parameter identifying the component.
- The tool validates the ref, capability, enabled state, and multi-selection support, then selects all items via `AccessibleSelection.selectAllAccessibleSelection()` (or JTable direct API) asynchronously. Returns `null` immediately.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

**Tool description:** "Select all items in a multi-selection UI component by ref. Only works on components marked multi-selection in the snapshot (list, table). Single-selection components are rejected. Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support selection (i.e. `SwingUtils.supportsSelection(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`). The error message depends on why selection is unsupported: (a) If the target is a `JTable` that fails the row-selection gate (UC-014 BR-10): *"JTable is not in row-selection mode. Only row selection is supported."* (b) Otherwise: *"Component does not support select_all. Call swing_snapshot to verify the list of actions."* Same detection logic as UC-014 BR-03. |
| BR-04 | If the target supports selection but is in **single-selection mode** (`SwingUtils.supportsSingleSelection(accessible)` returns `true`), the tool returns an MCP-level error (`isError: true`) with the message *"Component is in single-selection mode. select_all requires multi-selection."* |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-06 | All validation runs on the EDT inside `runInEDT()`. The selection mutation is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously (fire-and-forget). |
| BR-07 | `swing_select_all` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-08 | The tool returns `null` (empty content array) on success, consistent with other mutation tools (`swing_click`, `swing_set_text`, `swing_set_value`, `swing_set_selection`). |
| BR-09 | **JTable select-all.** When the target is a `JTable` in row-selection mode, the tool **must** use `table.selectAll()` directly. `AccessibleSelection.selectAllAccessibleSelection()` is a complete no-op on JTable — it selects nothing (probe-tested 2026-04-08). `table.selectAll()` works correctly: selects all rows, does not flip `columnSelectionAllowed`, and the accessible selection correctly reports all cells (which aggregate back to rows via UC-014 BR-11). |
| BR-10 | **Non-JTable select-all.** For `JList` (and any other multi-selectable component), the tool dispatches `as.selectAllAccessibleSelection()` via `SwingUtilities.invokeLater()` (fire-and-forget). |
| BR-11 | **Empty component.** If the component has zero selectable items (`SwingUtils.getSelectableItemsCount(accessible) == 0`), the tool succeeds trivially — dispatch still runs but has no observable effect. This is not an error. No explicit implementation guard is needed — the behavior falls out naturally from `selectAllAccessibleSelection()` / `table.selectAll()` on empty components (probe-tested 2026-04-08). |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03 + BR-04** — `requireMultiSelectable(accessible, "select_all")` — checks selection support first (with JTable-specific error), then rejects single-selection. Uses the shared helper from `AbstractSwingTool`.
4. **BR-05** — `SwingUtils.isEffectivelyEnabled(accessible)` — if `false`, fail with "disabled" error.
5. **Fire-and-forget dispatch** via `SwingUtilities.invokeLater()`:

**Validation ordering rationale.** Capability checks (steps 3–4) precede the enabled check (step 5). A disabled `JButton` gets "does not support select_all" — the real problem is lack of selection support, not the disabled state. The "disabled" error is only reachable for components that actually support multi-selection (JList, JTable) but happen to be disabled, which is the correct and most actionable error message.

   a. **If the target is a JTable** — call `table.selectAll()` (BR-09).
   b. **Otherwise** — obtain `AccessibleSelection as = ac.getAccessibleSelection()`, call `as.selectAllAccessibleSelection()` (BR-10).
6. Return `null`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleSelection()` — selection manipulation (non-JTable path)
- `AccessibleSelection.selectAllAccessibleSelection()` — select all items (non-JTable path)
- `JTable.selectAll()` — JTable-specific: select all rows (direct API)
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check
- `SwingUtils.supportsSelection(Accessible)` — capability check
- `SwingUtils.supportsSingleSelection(Accessible)` — single-selection mode check (to reject)
- `SwingUtils.supportsMultiSelection(Accessible)` — multi-selection mode check

### Design notes

- **Thin convenience tool.** `swing_select_all` could be achieved by the AI calling `swing_get_selectable_items_count` then `swing_set_selection` with all indices. However, `select_all` is a common operation and this saves two round-trips. The implementation is also simpler: a single `selectAllAccessibleSelection()` call rather than enumerating indices.
- **Only multi-selection.** The architecture (§ 6 "Selection Action Groups") specifies that `swing_select_all` is only available under the `multi-selection` group label. Single-selection components are explicitly rejected (BR-04) to prevent confusion — "select all" on a single-selection component has no clear semantics.
- **`JList`** — `selectAllAccessibleSelection()` selects all items. Works in both `SINGLE_INTERVAL_SELECTION` and `MULTIPLE_INTERVAL_SELECTION` modes (both report `MULTISELECTABLE`). Probe-tested (2026-04-08): works correctly in headless mode, including empty lists (0 items) and large lists (200 items). No fallback needed.
- **`JTable`** — uses `JTable.selectAll()` directly because `AccessibleSelection.selectAllAccessibleSelection()` is **broken on JTable** — it's a complete no-op (selects nothing). `table.selectAll()` works correctly in row-selection mode: selects all rows, does not flip `columnSelectionAllowed`, and the accessible selection reports all cells (rows x cols) which aggregate back to rows correctly. Probe-tested (2026-04-08).
- **`JTabbedPane`** — always single-selection, so it is rejected by BR-04. `selectAllAccessibleSelection()` would be a no-op anyway.
- **`JComboBox`** — always single-selection, so it is rejected by BR-04.
- **`JTree`** — suppressed by `SUPPRESSED_SELECTION_ROLES` in `supportsSelection()`. Same as UC-014.
- **Delegation vs standalone.** `swing_clear_selection` delegates to `SwingSetSelectionTool` with empty indices. A similar pattern here (delegate with all indices `[0, itemCount)`) was considered but rejected: some table implementations treat "select all" as a special state (everything selected) rather than holding IDs of all rows; passing in all indices would bypass this optimization and be wasteful for large tables. The standalone calls (`selectAllAccessibleSelection()` / `table.selectAll()`) are simpler and preserve implementation-specific select-all semantics.
- **Shared validation helpers (implemented).** The JTable-check-then-generic-error pattern for selection support was duplicated across `get_selection`, `set_selection`, `get_selectable_items`, and `get_selectable_items_count`. Extracted into `AbstractSwingTool` and retrofitted into all four existing tools (commit `d874355`): (1) `requireSelectable(Accessible, String toolName)` — throws `MCPErrorResponseException` if `!supportsSelection()`, with JTable-specific message vs generic message (substituting `toolName`); (2) `requireMultiSelectable(Accessible, String toolName)` — calls `requireSelectable` first, then throws if `supportsSingleSelection()`. `select_all` uses `requireMultiSelectable` directly.
- **Registration.** Tool registration is explicit in `MCPServer.registerTools()`. Implementation must add `registerTool(new SwingSelectAllTool())` there and `TOOL_SWING_SELECT_ALL` to `AbstractSwingTool` constants.

### Probe test findings (2026-04-08)

Verified empirically on Java 21 OpenJDK in headless mode (`JTableSelectAllProbeTest`, `JListSelectAllProbeTest`).

#### `JTable.selectAll()` vs `AccessibleSelection.selectAllAccessibleSelection()` on JTable

| Aspect | `table.selectAll()` | `accSel.selectAllAccessibleSelection()` |
|--------|---------------------|------------------------------------------|
| Selected rows (5-row, 3-col table) | 5 (all) | 0 (none — **broken**) |
| Selected columns | 3 (all) | 0 (none) |
| Accessible selection count | 15 (all cells = rows x cols) | 0 |
| Mutates `rowSelectionAllowed`? | No | No |
| Mutates `columnSelectionAllowed`? | No | No |

**Conclusion:** `selectAllAccessibleSelection()` is a complete no-op on JTable. Must use `table.selectAll()` directly (BR-09).

#### `selectAllAccessibleSelection()` on JList

| Selection mode | Items | Selected after call | `MULTISELECTABLE`? | Notes |
|---|---|---|---|---|
| `MULTIPLE_INTERVAL_SELECTION` | 10 | 10 (all) | Yes | Works correctly |
| `SINGLE_INTERVAL_SELECTION` | 10 | 10 (all) | Yes | "Select all" produces one contiguous range `[0, N-1]`, valid for single-interval |
| `SINGLE_SELECTION` | 10 | 1 (last item only) | No | Each `addSelectionInterval` replaces previous; last item wins. Academic — BR-04 rejects single-selection before dispatch. |
| `MULTIPLE_INTERVAL_SELECTION` | 0 | 0 | Yes | Succeeds without error |
| `MULTIPLE_INTERVAL_SELECTION` | 200 | 200 (all) | Yes | No performance issues |

**Conclusion:** `selectAllAccessibleSelection()` works correctly on JList for all multi-selection modes. No fallback needed (BR-10 confirmed).

---

## Acceptance Criteria

- [ ] Calling `swing_select_all` with a valid ref for a `JList` (multi-selection) selects all items.
- [ ] Calling `swing_select_all` with a valid ref for a `JTable` (row-selection, multi-selection) selects all rows.
- [ ] Calling `swing_select_all` on an empty `JList` (0 items) succeeds without error.
- [ ] Calling `swing_select_all` on an empty `JTable` (0 rows) succeeds without error.
- [ ] Calling `swing_select_all` on a single-selection `JList` returns an MCP error: *"Component is in single-selection mode. select_all requires multi-selection."*
- [ ] Calling `swing_select_all` on a `JTabbedPane` returns an MCP error (single-selection).
- [ ] Calling `swing_select_all` on a `JComboBox` returns an MCP error (single-selection).
- [ ] Calling `swing_select_all` on a `JTable` in column-selection mode returns an MCP error (unsupported).
- [ ] Calling `swing_select_all` on a `JTree` returns an MCP error (suppressed).
- [ ] Calling `swing_select_all` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_select_all` on a component that does not support selection (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_select_all` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] The ref map is cleared after a successful `swing_select_all` call (mutation tool).
- [ ] The ref map is cleared even after a failed `swing_select_all` call that passed ref lookup.
- [ ] The tool returns `null` (empty content array) on success.
- [ ] After `swing_select_all`, `swing_get_selection` returns all items as selected (round-trip verification).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingSelectAllTest` (headless)
  - [ ] `JList` (multi-selection) with 5 items: after `select_all`, all 5 items are selected.
  - [ ] `JList` (multi-selection) with 200 items: after `select_all`, all 200 items are selected (verified via `swing_get_selection` count).
  - [ ] Empty `JList` (multi-selection, 0 items): succeeds without error.
  - [ ] `JTable` (row-selection, multi-selection) with 10 rows: returns success (selection verified in screen test).
  - [ ] Empty `JTable` (0 rows): succeeds without error.
  - [ ] `JList` (single-selection): returns an MCP error with "single-selection mode" message.
  - [ ] `JTabbedPane`: returns an MCP error with "single-selection mode" message.
  - [ ] `JComboBox`: returns an MCP error with "single-selection mode" message.
  - [ ] `JTable` (single-row-selection mode): returns an MCP error with "single-selection mode" message.
  - [ ] `JTable` in column-selection mode: returns an MCP error with JTable-specific message.
  - [ ] `JTable` in cell-selection mode: returns an MCP error.
  - [ ] `JTable` with no selection allowed: returns an MCP error.
  - [ ] Invalid ref: returns an MCP error with `isError: true`.
  - [ ] `JButton`: returns an MCP error.
  - [ ] `JTree`: returns an MCP error (suppressed).
  - [ ] Disabled `JList` (multi-selection): returns an MCP error explaining the component is disabled.
  - [ ] Ref map is cleared after successful call (verified by attempting to use the same ref again).
  - [ ] Ref map is cleared after a failed call on a disabled component.
  - [ ] Round-trip: `swing_select_all` followed by `swing_get_selection` on same `JList` returns all items.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingSelectAllScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] `JList` (multi-selection) inside `JFrame`: all items selected after `select_all`.
  - [ ] `JTable` (row-selection, multi-selection) inside `JFrame`: all rows selected after `select_all`.
  - [ ] `JList` (multi-selection) inside `JDialog`: all items selected after `select_all`.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`select_all` supported):**
`JList` (multi-selection), `JTable` (row-selection, multi-selection mode)

**Expected to fail with "single-selection mode" error:**
`JList` (single-selection), `JTabbedPane`, `JComboBox`, `JTable` (single-row-selection)

**Expected to fail with "Component does not support select_all" error:**
`JTree` (suppressed), `JTable` (column/cell/no-selection modes — suppressed by row-selection gate), `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
