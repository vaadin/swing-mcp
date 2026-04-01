# UC-004: swing_click

---

**As an** AI agent, **I want to** click a UI component by ref **so that** I can interact with buttons, checkboxes, and other clickable elements.

**Status:** Approved
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_click` with the `ref` parameter identifying the component to click.
- The tool validates the ref and component, then fires the click action asynchronously and returns `null` immediately.
- I call `swing_snapshot` again to observe any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The click is performed by finding the component's `AccessibleAction` index whose description matches the click action (see algorithm below), then posting `doAccessibleAction(i)` via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The action itself is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be clicked. See also **architecture.md § 6** — Tool execution level. |
| BR-06 | If the target has no matching click action (i.e. `supportsClick()` returns -1), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support click. Call swing_snapshot to verify the list of actions". |

### Algorithm: detecting and invoking the click action

See **architecture.md § 4 — Detecting Click Support** for the full algorithm and rationale, and **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. **BR-06** — `int i = supportsClick(accessible)` — if `i < 0`, fail before walking the parent chain
3. **BR-05** — `isEffectivelyEnabled(accessible)` — only checked when click action exists
4. `SwingUtilities.invokeLater(() -> aa.doAccessibleAction(i))` — fire-and-forget; return `null`

---

## Acceptance Criteria

- [ ] Calling `swing_click` with a valid ref for a button fires the click action (fire-and-forget) and returns `null`.
- [ ] Calling `swing_click` with a valid ref for a checkbox fires the click action (fire-and-forget) and returns `null`.
- [ ] Calling `swing_click` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_click` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] Calling `swing_click` on a component that does not support click returns an MCP error suggesting to call `swing_snapshot`.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingClickTest`
  - [ ] Clicking a button ref fires the action (verified by observing the button's action listener was called after the EDT drains).
  - [ ] Clicking a checkbox ref fires the action (verified by observing state change after EDT drains).
  - [ ] Clicking an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Clicking a disabled button returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Clicking a component without click support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [ ] Each component from the component matrix is tested.

- [ ] `SwingClickScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Clicking a button inside `JFrame` fires its action listener (verified after EDT drains).
  - [ ] Clicking a checkbox inside `JFrame` toggles its state (verified after EDT drains).
  - [ ] Clicking a disabled button inside `JFrame` returns an MCP error.
  - [ ] Clicking a button inside `JDialog` fires its action listener.
  - [ ] Clicking a checkbox inside `JDialog` toggles its state.
  - [ ] Clicking a disabled button inside `JDialog` returns an MCP error.

