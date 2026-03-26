# UC-005: swing_set_text

---

**As an** AI agent, **I want to** set the text of a text component by ref **so that** I can fill in text fields and text areas.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_set_text` with the `ref` parameter identifying the text component and a `value` parameter containing the text to set.
- The tool looks up the component by ref and sets its text content.
- The tool returns an empty string on success.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | The `value` parameter is required and must be a string. |
| BR-03 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-04 | If the ref points to a component that does not support text (no `AccessibleEditableText` or `AccessibleText`), the tool returns an MCP error. |
| BR-05 | The text is set by replacing the entire existing text content. |
| BR-06 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |

---

## Acceptance Criteria

- [ ] Calling `swing_set_text` with a valid ref for a text field sets its text content.
- [ ] The previous text content is fully replaced by the new value.
- [ ] Calling `swing_set_text` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_set_text` on a non-text component (e.g., a button) returns an MCP error.
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingSetTextTest`
  - [ ] Setting text on a JTextField ref updates its content.
  - [ ] Setting text replaces existing content entirely.
  - [ ] Setting text with an invalid ref returns an MCP error with `isError: true`.
  - [ ] Setting text on a non-text component returns an MCP error.
