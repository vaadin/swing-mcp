# UC-004: swing_click

---

**As an** AI agent, **I want to** click a UI component by ref **so that** I can interact with buttons, checkboxes, and other clickable elements.

**Status:** Implemented
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
| BR-03 | The click is performed by calling `supportsClick(accessible)` which returns a `Runnable` encapsulating the click action (see **architecture.md § 4 — Detecting Click Support**). The `Runnable` is posted via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). The caller does not need to know whether the click uses `AccessibleAction` (Tier 1) or synthetic `MouseEvent` (Tier 2) — this is captured inside the `Runnable`. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The action itself is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be clicked. See also **architecture.md § 6** — Tool execution level. |
| BR-06 | If the target does not support clicking (i.e. `supportsClick()` returns `null`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support click. Call swing_snapshot or swing_get_cells to verify the list of actions". |

### Algorithm: detecting and invoking the click action

See **architecture.md § 4 — Detecting Click Support** for the full algorithm and rationale (`supportsClick()` returns a `Runnable` or `null`), and **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. **BR-06** — `Runnable click = supportsClick(accessible)` — if `null`, return error
3. **BR-05** — `isEffectivelyEnabled(accessible)` — only checked when click is supported
4. `SwingUtilities.invokeLater(click)` — fire-and-forget; return `null`

---

## Acceptance Criteria

- [x] Calling `swing_click` with a valid ref for a button fires the click action (fire-and-forget) and returns `null`.
- [x] Calling `swing_click` with a valid ref for a checkbox fires the click action (fire-and-forget) and returns `null`.
- [x] Calling `swing_click` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_click` on a disabled component returns an MCP error explaining the component is disabled.
- [x] Calling `swing_click` on a component that does not support click returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_click` on a JPanel with an application MouseListener fires the synthetic mouse event sequence and returns `null`.
- [ ] Calling `swing_click` on a disabled JPanel with an application MouseListener returns an MCP error explaining the component is disabled.
- [ ] Calling `swing_click` on a component with no AccessibleAction click and no application MouseListener returns an MCP error.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingClickTest`
  - [x] Clicking a button ref fires the action (verified by observing the button's action listener was called after the EDT drains).
  - [x] Clicking a checkbox ref fires the action (verified by observing state change after EDT drains).
  - [x] Clicking an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Clicking a disabled button returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Clicking a component without click support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [x] Each component from the component matrix is tested.
  - [ ] Clicking a `ClickRecordingPanel` (see below) dispatches the full mouse event sequence and the panel reports `wasClicked() == true`.
  - [ ] The synthetic MouseEvent coordinates are at the center of the component.
  - [ ] The synthetic MouseEvent uses BUTTON1 with click count 1.
  - [ ] Clicking a disabled `ClickRecordingPanel` returns an MCP error with `isError: true`.
  - [ ] Clicking a component with no AccessibleAction click and no application MouseListener returns an MCP error with `isError: true`.
  - [ ] A component with an interactive role (e.g. `JSlider`) and an application MouseListener but no AccessibleAction click is not clickable (Tier 2 skipped for interactive roles).

### `ClickRecordingPanel` — Reusable Test Component

A `JPanel` subclass in `src/test` that registers a `MouseAdapter` on itself and records the
mouse event sequence. It verifies internally that events arrive in the correct order
(`MOUSE_PRESSED` → `MOUSE_RELEASED` → `MOUSE_CLICKED`), all with `BUTTON1` and click count 1.
The test asserts via `wasClicked()` — returns `true` only if the full sequence was received
correctly. Lives in `src/test` so it is visible to both headless and `testSwing` source sets.

- [x] `SwingClickScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Clicking a button inside `JFrame` fires its action listener (verified after EDT drains).
  - [x] Clicking a checkbox inside `JFrame` toggles its state (verified after EDT drains).
  - [x] Clicking a disabled button inside `JFrame` returns an MCP error.
  - [x] Clicking a button inside `JDialog` fires its action listener.
  - [x] Clicking a checkbox inside `JDialog` toggles its state.
  - [x] Clicking a disabled button inside `JDialog` returns an MCP error.

