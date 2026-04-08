# UC-016: swing_clear_selection

---

**As an** AI agent, **I want to** clear the selection of a UI component by ref **so that** I can deselect all items in lists, combo boxes, tables, and tabbed panes during Swing app migration.

**Status:** Implemented
**Date:** 2026-04-07

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_clear_selection` with the `ref` parameter identifying the component.
- The tool delegates to `swing_set_selection` with an empty `indices` array `[]`. All validation and behavior is as specified in UC-015.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

**Tool description:** "Clear the selection of a UI component by ref. Works with multi-select components (list, table) and some single-select components (combo_box). page_tab_list with tabs does not allow an empty selection. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Business Rules

This tool is a convenience wrapper around `swing_set_selection` (UC-015) with `indices=[]`. All business rules from UC-015 apply — in particular BR-06 (mutation tool), BR-07 (empty indices behavior including JTabbedPane refusal), and BR-09 (returns `null` on success). See UC-015 for the full specification.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingClearSelectionTest` (headless)
  - [x] Clearing the selection of a `JList` with a selected item deselects the item (happy path).
  - [x] Calling `swing_clear_selection` via the MCP client clears the selection.
