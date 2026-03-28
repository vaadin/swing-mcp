# UC-004: swing_click

---

**As an** AI agent, **I want to** click a UI component by ref **so that** I can interact with buttons, checkboxes, and other clickable elements.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_click` with the `ref` parameter identifying the component to click.
- The tool looks up the component by ref and performs a click action on it.
- The tool returns an empty string on success.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The click is performed by finding and invoking the component's `AccessibleAction` whose description matches the click action (see algorithm below). |
| BR-04 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |

### Algorithm: detecting and invoking the click action

See **architecture.md § 4 — Detecting Click Support** for the full algorithm and rationale.

To invoke the click, call `doAccessibleAction(i)` on the matching index `i`.

---

## Acceptance Criteria

- [ ] Calling `swing_click` with a valid ref for a button triggers the button's action.
- [ ] Calling `swing_click` with a valid ref for a checkbox toggles its state.
- [ ] Calling `swing_click` with an invalid ref returns an MCP error with a recovery message.
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingClickTest`
  - [ ] Clicking a button ref triggers the button's action listener.
  - [ ] Clicking a checkbox ref toggles its selected state.
  - [ ] Clicking an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Each component from the component matrix is tested.

