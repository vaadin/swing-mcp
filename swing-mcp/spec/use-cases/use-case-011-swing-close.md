# UC-011: swing_close

---

**As an** AI agent, **I want to** close a window or dialog by ref **so that** I can dismiss dialogs, option panes, and frames that I've finished with.

**Status:** Draft
**Date:** 2026-04-01

---

## Main Flow

- I call `swing_snapshot` and see a `frame`, `dialog`, or `option_pane` node with `close` in its actions list.
- I call `swing_close` with the `ref` parameter identifying the component to close.
- The tool dispatches `WindowEvent.WINDOW_CLOSING` to the window, which respects the app's `WindowListener`s and `defaultCloseOperation`.
- The tool returns an empty string on success (meaning the event was dispatched; the window may or may not have closed depending on the app's logic).
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The close is performed by dispatching `WindowEvent.WINDOW_CLOSING` to the window. For a JFrame or JDialog, the event is dispatched to the component itself. For a JOptionPane, the event is dispatched to its containing window (found via `SwingUtilities.windowForComponent()`). |
| BR-04 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-05 | If `supportsClose()` returns false for the target (e.g. it is not a window or JOptionPane, or the window is undecorated or not showing), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support close. Call swing_snapshot to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Closing is a window-level action; the app's `WindowListener`s and `defaultCloseOperation` decide whether the window actually closes. |
| BR-07 | "Success" means the event was dispatched without throwing an exception — not that the window was disposed. If the app has set `DO_NOTHING_ON_CLOSE` or a `WindowListener` vetoes the close, the window remains open. The AI should call `swing_snapshot` to verify the result. |

### Algorithm: detecting and invoking close

See **architecture.md § 4 — Detecting Close Support** for the full algorithm and rationale, and
**architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. **BR-05** — `supportsClose(accessible)` — fail before any window interaction
3. **BR-03** — resolve the target `Window` (direct for JFrame/JDialog; via `windowForComponent` for JOptionPane)
4. Dispatch `new WindowEvent(window, WindowEvent.WINDOW_CLOSING)` to the window

---

## Acceptance Criteria

- [ ] Calling `swing_close` on a JFrame with `DISPOSE_ON_CLOSE` dispatches the event and the frame is disposed.
- [ ] Calling `swing_close` on a JDialog ref closes (disposes) the dialog.
- [ ] Calling `swing_close` on a JOptionPane ref dispatches `WINDOW_CLOSING` to its containing JDialog.
- [ ] Calling `swing_close` on an undecorated window returns an MCP error (`isError: true`).
- [ ] Calling `swing_close` with an invalid ref returns an MCP error with a recovery message suggesting to call `swing_snapshot`.
- [ ] Calling `swing_close` on a component that does not support close (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingCloseTest` (headless)
  - [ ] Each non-window component from the component matrix returns an MCP error (`isError: true`) when `swing_close` is called on it.
  - [ ] Calling `swing_close` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [ ] `SwingCloseScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Calling `swing_close` on a JFrame ref (with `DISPOSE_ON_CLOSE`) disposes the frame.
  - [ ] Calling `swing_close` on a JDialog ref disposes the dialog.
  - [ ] Calling `swing_close` on a JOptionPane ref dispatches `WINDOW_CLOSING` to its containing JDialog.
  - [ ] Calling `swing_close` on an undecorated JFrame returns an MCP error with `isError: true`.
  - [ ] Snapshot of a visible JFrame shows `close` in its actions and assigns it a ref.
  - [ ] Snapshot of a visible JDialog shows `close` in its actions and assigns it a ref.
  - [ ] Snapshot of a JOptionPane inside a JDialog shows `close` in its actions and assigns it a ref.
