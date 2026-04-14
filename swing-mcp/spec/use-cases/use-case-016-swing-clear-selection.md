# UC-016: swing_clear_selection

**Status:** Implemented
**Date:** 2026-04-07

Convenience wrapper: `swing_set_selection` with an empty indices array.

**Tool description:** "Clear the selection of a UI component by ref. Works with multi-select components (JList, JTable) and some single-select components (JComboBox). JTabbedPane does not allow an empty selection. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

This tool is a convenience wrapper around `swing_set_selection` (UC-015) with `indices=[]`. All business rules from UC-015 apply — in particular BR-06 (mutation tool), BR-07 (empty indices behavior including JTabbedPane refusal), and BR-09 (returns `null` on success). See UC-015 for the full specification.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingClearSelectionTest` (headless)
  - [x] Clearing the selection of a `JList` with a selected item deselects the item (happy path).
  - [x] Calling `swing_clear_selection` via the MCP client clears the selection.
  - [ ] Clearing the selection of a `JList` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) deselects the item.
