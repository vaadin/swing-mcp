# T-011: swing_close

**Status:** Implemented
**Date:** 2026-04-01

Dismisses a window, dialog, internal frame, or desktop icon (iconified internal frame) — respects the app's close handlers. See the JVM-termination hazard below.

---

## Warning

Closing a window may terminate the application (e.g. `EXIT_ON_CLOSE` on the main frame, or a `WindowListener` that calls `System.exit()`). Since swing-mcp runs inside the same JVM as the Swing app, it will be killed too and the AI client will see a dropped HTTP connection with no explanation. If this happens, the only way to recover is to re-run the Swing application.

`EXIT_ON_CLOSE` frames are refused proactively (BR-09). `JInternalFrame` cannot terminate the JVM (even with `EXIT_ON_CLOSE` — see BR-09), but the same refusal rule applies for consistency. Other termination paths (custom `WindowListener` calling `System.exit()`) cannot be detected in advance.

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | **Window (JFrame, JDialog):** the close is performed by dispatching `WindowEvent.WINDOW_CLOSING` to the window via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). **JInternalFrame:** the close is performed by calling `doDefaultCloseAction()` via `SwingUtilities.invokeLater()`. This fires `InternalFrameEvent.INTERNAL_FRAME_CLOSING` and then executes the frame's `defaultCloseOperation` — mirroring what happens when the user clicks the internal frame's close button. **JDesktopIcon:** resolves to the underlying JInternalFrame via `getInternalFrame()` and then follows the JInternalFrame path above. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The close dispatch (`WindowEvent.WINDOW_CLOSING` for Window, `doDefaultCloseAction()` for JInternalFrame) is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If `supportsClose()` returns false for the target (e.g. it is not a `Window`, `JInternalFrame`, or `JDesktopIcon`; or the window is undecorated / not closable / not showing / has `EXIT_ON_CLOSE`), the tool returns an MCP-level error (`isError: true`) with the message "<ClassName> does not support swing_close. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Closing is a window-level action; the app's `WindowListener`s and `defaultCloseOperation` decide whether the window actually closes. |
| BR-08 | `swing_close` is a **mutation tool** — it clears the ref map after successful execution (see **architecture.md §3 rule 3**). A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map. |
| BR-09 | `JFrame` with `defaultCloseOperation == EXIT_ON_CLOSE` is refused: `supportsClose()` returns `false` for it, so no `close` action is ever advertised in the snapshot and any attempt to call `swing_close` on such a frame returns the BR-05 error. Rationale: `EXIT_ON_CLOSE` terminates the JVM, which would kill the swing-mcp server in-process and drop the AI client connection with no explanation. `JInternalFrame` with `defaultCloseOperation == EXIT_ON_CLOSE` is also refused, for consistency. Note: `JInternalFrame.setDefaultCloseOperation()` silently accepts `EXIT_ON_CLOSE` without validation (JDK bug — no `IllegalArgumentException`), and `doDefaultCloseAction()` falls through the switch with no effect. The refusal is defensive. |
| BR-10 | `JInternalFrame` with `isClosable() == false` is refused: `supportsClose()` returns `false`. This is the JInternalFrame analog of the undecorated-window refusal — when `isClosable()` is false, the internal frame has no close button in its title bar, so the user cannot close it through normal UI. |
| BR-11 | **JDesktopIcon** (iconified JInternalFrame): `supportsClose()` checks `isShowing()` on the **icon itself** (the visible component on the desktop — the underlying frame is detached with `isShowing() == false`), then delegates `isClosable()` and `EXIT_ON_CLOSE` checks to the underlying JInternalFrame via `getInternalFrame()` (BR-09, BR-10). |
| BR-12 | **Tool description** must mention windows, dialogs, internal frames, and desktop icons (iconified internal frames). |
| BR-13 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Dispatched close on ref=<N> — call swing_snapshot to verify the outcome` (see **DR-dispatched-echo**). The echo confirms only that `WINDOW_CLOSING` (or `doDefaultCloseAction()`) was dispatched on the EDT — not that the window actually closed. The client calls `swing_snapshot` to verify outcome. |

### Algorithm: detecting and invoking close

See **architecture.md § 4 — Detecting Close Support** for the full algorithm and rationale, and
**architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-05** — `supportsClose(accessible)` — fail before any interaction.
3. Fire-and-forget close dispatch:
   - **Window (JFrame, JDialog):** `SwingUtilities.invokeLater(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)))` — return `null`.
   - **JInternalFrame:** `SwingUtilities.invokeLater(() -> internalFrame.doDefaultCloseAction())` — return `null`.
   - **JDesktopIcon:** resolve via `getInternalFrame()`, then follow the JInternalFrame path.

The client calls `swing_snapshot` after to determine whether the window/frame was dismissed. If it is
still present (e.g. `DO_NOTHING_ON_CLOSE`, or a listener vetoed the close), the AI
sees it in the snapshot and can decide how to proceed.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingCloseTest` (headless)
  - [x] Each non-window component from the component matrix returns an MCP error (`isError: true`) when `swing_close` is called on it.
  - [x] Calling `swing_close` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [x] `SwingCloseScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Calling `swing_close` on a JFrame ref (with `DISPOSE_ON_CLOSE`) fires the close event; frame is dismissed (verified after EDT drains).
  - [x] Calling `swing_close` on a JDialog ref fires the close event; dialog is dismissed (verified after EDT drains).
  - [x] Calling `swing_close` on a JFrame with `DO_NOTHING_ON_CLOSE` returns the DR-dispatched-echo echo `Dispatched close on ref=N — call swing_snapshot to verify the outcome`; window is still showing (verified via `isShowing()` after EDT drains).
  - [x] Calling `swing_close` on a JDialog with `DO_NOTHING_ON_CLOSE` returns the DR-dispatched-echo echo; dialog is still showing (verified via `isShowing()` after EDT drains).
  - [x] Calling `swing_close` on an undecorated JFrame returns an MCP error with `isError: true`.
  - [x] A JFrame with `EXIT_ON_CLOSE` does not appear with a `close` action in the snapshot.
  - [x] Calling `swing_close` on a JFrame with `EXIT_ON_CLOSE` via a stale ref returns an MCP error with `isError: true`.
  - [x] `JOptionPane` component matrix: `swing_close` returns an MCP error with `isError: true`.
  - [x] `JDesktopPane` component matrix: `swing_close` returns an MCP error with `isError: true`.
  - [x] Snapshot of a JFrame with `DISPOSE_ON_CLOSE` shows `close` in its actions and assigns it a ref.
  - [x] Snapshot of a visible JDialog shows `close` in its actions and assigns it a ref.
  - [x] Calling `swing_close` on a closable JInternalFrame (with `DISPOSE_ON_CLOSE`) fires the close event; internal frame is disposed (verified after EDT drains).
  - [x] Calling `swing_close` on a closable JInternalFrame with `DO_NOTHING_ON_CLOSE` returns the DR-dispatched-echo echo; internal frame is still showing (verified via `isShowing()` after EDT drains).
  - [x] Calling `swing_close` on a JInternalFrame with `isClosable() == false` returns an MCP error with `isError: true` (BR-10).
  - [x] Calling `swing_close` on a JInternalFrame with `EXIT_ON_CLOSE` via a stale ref returns an MCP error with `isError: true`.
  - [x] Calling `swing_close` on a JDesktopIcon (iconified closable JInternalFrame with `DISPOSE_ON_CLOSE`) closes the underlying internal frame (BR-11).
  - [x] Calling `swing_close` on a JDesktopIcon whose underlying JInternalFrame has `DO_NOTHING_ON_CLOSE` returns the DR-dispatched-echo echo; the JDesktopIcon is still showing on the desktop pane.
  - [x] Calling `swing_close` on a JDesktopIcon whose underlying JInternalFrame has `isClosable() == false` returns an MCP error with `isError: true` (BR-11 → BR-10).
  - [x] Calling `swing_close` on a JDesktopIcon whose underlying JInternalFrame has `EXIT_ON_CLOSE` returns an MCP error with `isError: true` (BR-11 → BR-09).
