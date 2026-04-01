# UC-011: swing_close

---

**As an** AI agent, **I want to** close a window or dialog by ref **so that** I can dismiss dialogs and frames that I've finished with.

**Status:** Draft
**Date:** 2026-04-01

---

## Warning

Closing a window may terminate the application (e.g. `EXIT_ON_CLOSE` on the main frame, or a `WindowListener` that calls `System.exit()`). Since swing-mcp runs inside the same JVM as the Swing app, it will be killed too and the AI client will see a dropped HTTP connection with no explanation. If this happens, the only way to recover is to re-run the Swing application.

`EXIT_ON_CLOSE` frames are refused proactively (BR-09), but other termination paths (custom `WindowListener` calling `System.exit()`) cannot be detected in advance.

## Main Flow

- I call `swing_snapshot` and see a `frame` or `dialog` node with `close` in its actions list.
- I call `swing_close` with the `ref` parameter identifying the component to close.
- The tool dispatches `WindowEvent.WINDOW_CLOSING` to the window, which respects the app's `WindowListener`s and `defaultCloseOperation`.
- The tool returns a `String`: empty on complete success, or a non-empty informational message if the window was not dismissed (see BR-07, BR-10). Informational messages are not MCP errors (`isError: false`).
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The close is performed by dispatching `WindowEvent.WINDOW_CLOSING` to the window. The target component is always a `Window` instance (JFrame or JDialog). |
| BR-04 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-08 | `swing_close` is a **mutation tool** — it clears the ref map in a `finally` block after execution, regardless of success or failure (see **architecture.md §3 rule 3**). |
| BR-05 | If `supportsClose()` returns false for the target (e.g. it is not a `Window`, or the window is undecorated, not showing, or has `EXIT_ON_CLOSE`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support close. Call swing_snapshot to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Closing is a window-level action; the app's `WindowListener`s and `defaultCloseOperation` decide whether the window actually closes. |
| BR-10 | If the target window has `defaultCloseOperation == DO_NOTHING_ON_CLOSE` (checked on the EDT before dispatching), the tool does **not** dispatch the event and returns `"The window has DO_NOTHING_ON_CLOSE set and was not closed."` No polling is performed. |
| BR-09 | `JFrame` with `defaultCloseOperation == EXIT_ON_CLOSE` is refused: `supportsClose()` returns `false` for it, so no `close` action is ever advertised in the snapshot and any attempt to call `swing_close` on such a frame returns the BR-05 error. Rationale: `EXIT_ON_CLOSE` terminates the JVM, which would kill the swing-mcp server in-process and drop the AI client connection with no explanation. |
| BR-07 | After dispatching `WINDOW_CLOSING`, the tool sets `postVerification` (see **architecture.md § 2 — PostVerification**) with `isDone = () -> !window.isShowing()`, delay schedule `{100, 200, 700}` ms, and `pendingMessage = "Window close was requested but the window is still showing — the application may have a DO_NOTHING_ON_CLOSE policy, a WindowListener that vetoed the close, or the window is still closing."` MCPServer polls on the HTTP thread so the EDT is free to drain any `invokeLater()` disposal tasks. Returns `""` when `isDone` becomes true; returns `pendingMessage` if the budget is exhausted. |

### Algorithm: detecting and invoking close

See **architecture.md § 4 — Detecting Close Support** for the full algorithm and rationale, and
**architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. On EDT: **BR-05** — `supportsClose(accessible)` — fail before any window interaction
3. On EDT: **BR-10** — check `DO_NOTHING_ON_CLOSE`; if set, return note immediately (no dispatch, no polling)
4. On EDT: dispatch `new WindowEvent((Window) accessible, WindowEvent.WINDOW_CLOSING)` to the window
5. **BR-07** — set `postVerification` so MCPServer polls `isShowing()` on the HTTP thread

`dispatchEvent()` is synchronous — it calls `WindowListener.windowClosing()` immediately — but
listeners may schedule disposal via `invokeLater()`, so the window may still be showing when
the EDT phase returns. The `PostVerification` mechanism (see **architecture.md § 2**) handles
this: MCPServer sleeps on the HTTP thread between checks so the EDT can drain queued tasks.
The delay schedule `{100, 200, 700}` ms front-loads short waits (common fast-dispose path)
and ends with a longer wait (slow or animated close transitions), for a total budget of ~1 s.

---

## Acceptance Criteria

- [ ] Calling `swing_close` on a JFrame with `DISPOSE_ON_CLOSE` dismisses the frame.
- [ ] Calling `swing_close` on a JDialog ref dismisses the dialog.
- [ ] Calling `swing_close` on a window with `DO_NOTHING_ON_CLOSE` returns an empty string (not an error) with the informational note from BR-10.
- [ ] Calling `swing_close` on an undecorated window returns an MCP error (`isError: true`).
- [ ] A JFrame with `EXIT_ON_CLOSE` does not receive a `close` action in the snapshot and has no ref assigned for it.
- [ ] Calling `swing_close` on a JFrame with `EXIT_ON_CLOSE` (via a stale ref) returns an MCP error (`isError: true`).
- [ ] Calling `swing_close` with an invalid ref returns an MCP error with a recovery message suggesting to call `swing_snapshot`.
- [ ] Calling `swing_close` on a component that does not support close (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] The tool returns an empty string when the window is no longer showing after dispatch.
- [ ] If the window is still showing after the polling budget is exhausted, the tool returns an empty string (not an error) with the informational note from BR-07.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingCloseTest` (headless)
  - [ ] Each non-window component from the component matrix returns an MCP error (`isError: true`) when `swing_close` is called on it.
  - [ ] Calling `swing_close` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [ ] `SwingCloseScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Calling `swing_close` on a JFrame ref (with `DISPOSE_ON_CLOSE`) dismisses the frame.
  - [ ] Calling `swing_close` on a JDialog ref dismisses the dialog.
  - [ ] Calling `swing_close` on a JFrame with `DO_NOTHING_ON_CLOSE` returns an informational note and does not dispatch a window event.
  - [ ] Calling `swing_close` on a JDialog with `DO_NOTHING_ON_CLOSE` returns an informational note and does not dispatch a window event.
  - [ ] Calling `swing_close` on an undecorated JFrame returns an MCP error with `isError: true`.
  - [ ] A JFrame with `EXIT_ON_CLOSE` does not appear with a `close` action in the snapshot.
  - [ ] Snapshot of a JFrame with `DISPOSE_ON_CLOSE` shows `close` in its actions and assigns it a ref.
  - [ ] Snapshot of a visible JDialog shows `close` in its actions and assigns it a ref.
