# UC-011: swing_close

**Status:** Implemented
**Date:** 2026-04-01

Dismisses a window or dialog via `WINDOW_CLOSING` — respects the app's close handlers. See the JVM-termination hazard below.

---

## Warning

Closing a window may terminate the application (e.g. `EXIT_ON_CLOSE` on the main frame, or a `WindowListener` that calls `System.exit()`). Since swing-mcp runs inside the same JVM as the Swing app, it will be killed too and the AI client will see a dropped HTTP connection with no explanation. If this happens, the only way to recover is to re-run the Swing application.

`EXIT_ON_CLOSE` frames are refused proactively (BR-09), but other termination paths (custom `WindowListener` calling `System.exit()`) cannot be detected in advance.

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The close is performed by dispatching `WindowEvent.WINDOW_CLOSING` to the window via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). The target component is always a `Window` instance (JFrame or JDialog). |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The `WindowEvent.WINDOW_CLOSING` dispatch is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If `supportsClose()` returns false for the target (e.g. it is not a `Window`, or the window is undecorated, not showing, or has `EXIT_ON_CLOSE`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support close. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Closing is a window-level action; the app's `WindowListener`s and `defaultCloseOperation` decide whether the window actually closes. |
| BR-08 | `swing_close` is a **mutation tool** — it clears the ref map in a `finally` block after execution, regardless of success or failure (see **architecture.md §3 rule 3**). |
| BR-09 | `JFrame` with `defaultCloseOperation == EXIT_ON_CLOSE` is refused: `supportsClose()` returns `false` for it, so no `close` action is ever advertised in the snapshot and any attempt to call `swing_close` on such a frame returns the BR-05 error. Rationale: `EXIT_ON_CLOSE` terminates the JVM, which would kill the swing-mcp server in-process and drop the AI client connection with no explanation. |

### Algorithm: detecting and invoking close

See **architecture.md § 4 — Detecting Close Support** for the full algorithm and rationale, and
**architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-05** — `supportsClose(accessible)` — fail before any window interaction.
3. `SwingUtilities.invokeLater(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)))` — fire-and-forget; return `null`.

The client calls `swing_snapshot` after to determine whether the window was dismissed. If it is
still present (e.g. `DO_NOTHING_ON_CLOSE`, or a `WindowListener` vetoed the close), the AI
sees it in the snapshot and can decide how to proceed.

---

## Acceptance Criteria

- [x] Calling `swing_close` on a JFrame with `DISPOSE_ON_CLOSE` fires the close event; the frame is dismissed (verified via snapshot after EDT drains).
- [x] Calling `swing_close` on a JDialog fires the close event; the dialog is dismissed (verified via snapshot after EDT drains).
- [x] Calling `swing_close` on a window with `DO_NOTHING_ON_CLOSE` fires the close event and returns `null`; the window remains visible (verified via snapshot — no MCP error).
- [x] Calling `swing_close` on an undecorated window returns an MCP error (`isError: true`).
- [x] A JFrame with `EXIT_ON_CLOSE` does not receive a `close` action in the snapshot and has no ref assigned for it.
- [x] Calling `swing_close` on a JFrame with `EXIT_ON_CLOSE` (via a stale ref) returns an MCP error (`isError: true`).
- [x] Calling `swing_close` with an invalid ref returns an MCP error with a recovery message suggesting to call `swing_snapshot`.
- [x] Calling `swing_close` on a component that does not support close (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [x] The tool returns `null` on success (fire-and-forget).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingCloseTest` (headless)
  - [x] Each non-window component from the component matrix returns an MCP error (`isError: true`) when `swing_close` is called on it.
  - [x] Calling `swing_close` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [x] `SwingCloseScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Calling `swing_close` on a JFrame ref (with `DISPOSE_ON_CLOSE`) fires the close event; frame is dismissed (verified after EDT drains).
  - [x] Calling `swing_close` on a JDialog ref fires the close event; dialog is dismissed (verified after EDT drains).
  - [x] Calling `swing_close` on a JFrame with `DO_NOTHING_ON_CLOSE` returns `null`; window is still showing (verified via `isShowing()` after EDT drains).
  - [x] Calling `swing_close` on a JDialog with `DO_NOTHING_ON_CLOSE` returns `null`; dialog is still showing (verified via `isShowing()` after EDT drains).
  - [x] Calling `swing_close` on an undecorated JFrame returns an MCP error with `isError: true`.
  - [x] A JFrame with `EXIT_ON_CLOSE` does not appear with a `close` action in the snapshot.
  - [x] Calling `swing_close` on a JFrame with `EXIT_ON_CLOSE` via a stale ref returns an MCP error with `isError: true`.
  - [x] `JOptionPane` component matrix: `swing_close` returns an MCP error with `isError: true`.
  - [x] Snapshot of a JFrame with `DISPOSE_ON_CLOSE` shows `close` in its actions and assigns it a ref.
  - [x] Snapshot of a visible JDialog shows `close` in its actions and assigns it a ref.
