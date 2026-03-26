# UC-006: swing_set_value

---

**As an** AI agent, **I want to** set the value of a component by ref **so that** I can manipulate sliders, spinners, scroll bars, and other value-bearing components.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_set_value` with the `ref` parameter identifying the component and a `value` parameter containing the value to set.
- The tool looks up the component by ref and sets its value via the `AccessibleValue` interface.
- The tool returns an empty string on success.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | The `value` parameter is required and must be a number. |
| BR-03 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-04 | If the ref points to a component that does not support `AccessibleValue`, the tool returns an MCP error. |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |

---

## Acceptance Criteria

- [ ] Calling `swing_set_value` with a valid ref for a slider sets its value.
- [ ] Calling `swing_set_value` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_set_value` on a component that does not support `AccessibleValue` returns an MCP error.
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingSetValueTest`
  - [ ] Setting a value on a JSlider ref updates its current value.
  - [ ] Setting a value with an invalid ref returns an MCP error with `isError: true`.
  - [ ] Setting a value on a component without `AccessibleValue` returns an MCP error.
