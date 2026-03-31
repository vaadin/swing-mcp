# UC-004: swing_click

---

**As an** AI agent, **I want to** click a UI component by ref **so that** I can interact with buttons, checkboxes, and other clickable elements.

**Status:** Implemented
**Date:** 2026-03-31

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
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be clicked. See also **architecture.md § 6** — Tool execution level. |
| BR-06 | If the target has no matching click action (i.e. `supportsClick()` returns false), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support click. Call swing_snapshot to verify the list of actions". |
| BR-07 | If `doAccessibleAction(i)` returns `false`, the tool returns an MCP-level error (`isError: true`) with the message "The action was not performed, no additional information has been provided". |

### Algorithm: detecting and invoking the click action

See **architecture.md § 4 — Detecting Click Support** for the full algorithm and rationale, and **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. **BR-06** — `int i = supportsClick(accessible)` — if `i < 0`, fail before walking the parent chain
3. **BR-05** — `isEffectivelyEnabled(accessible)` — only checked when click action exists
4. **BR-07** — `doAccessibleAction(i)` — if it returns `false`, report failure

---

## Acceptance Criteria

- [ ] Calling `swing_click` with a valid ref for a button triggers the button's action.
- [ ] Calling `swing_click` with a valid ref for a checkbox toggles its state.
- [ ] Calling `swing_click` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_click` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] Calling `swing_click` on a component that does not support click returns an MCP error suggesting to call `swing_snapshot`.
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingClickTest`
  - [ ] Clicking a button ref triggers the button's action listener.
  - [ ] Clicking a checkbox ref toggles its selected state.
  - [ ] Clicking an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Clicking a disabled button returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Clicking a component without click support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [ ] Each component from the component matrix is tested.

