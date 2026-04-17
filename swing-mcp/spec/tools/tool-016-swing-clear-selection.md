# T-016: swing_clear_selection

**Status:** Implemented
**Date:** 2026-04-07

Convenience wrapper: `swing_set_selection` with an empty indices array.

**Tool description:** "Clear the selection of a UI component by ref. Works with multi-select components (JList, JTable) and some single-select components (JComboBox). JTabbedPane does not allow an empty selection. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

This tool is a convenience wrapper around `swing_set_selection` (T-015) with `indices=[]`. All business rules from T-015 apply — in particular BR-06 (mutation tool) and BR-07 (empty indices behavior including JTabbedPane refusal). See T-015 for the full specification.

**Return message.** On success, the dispatch wrapper returns a single text-content item: `Dispatched clear-selection on ref=<N> — call swing_snapshot to verify the outcome` (see **DR-010**). The echo uses this tool's MCP-exposed name (`clear-selection`), not the underlying `set-selection` it delegates to — DR-010's action name is derived from the public tool name, not from the implementation. T-015 BR-09's echo (`Dispatched set-selection on ref=<N> to [] — call swing_snapshot to verify the outcome`) does not apply here.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingClearSelectionTest` (headless)
  - [x] Clearing the selection of a `JList` with a selected item deselects the item (happy path).
  - [x] Calling `swing_clear_selection` via the MCP client clears the selection.
  - [x] Clearing the selection of a `JList` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) deselects the item.
