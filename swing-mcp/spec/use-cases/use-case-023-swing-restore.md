# UC-023: swing_restore

**Status:** Implemented
**Date:** 2026-04-14

Restores (de-iconifies) an iconified Frame (including JFrame) or JDesktopIcon (iconified JInternalFrame) — the dual of `swing_iconify` (UC-022). This is a secondary action: the AI client is only rarely expected to call this tool (very rarely for Frame/JFrame, rarely for JInternalFrame), but it should exist for symmetry with `swing_iconify`. Only iconified windows can be restored; a maximized-but-not-iconified window is not a valid target.

**Tool description:** "Restore (de-iconify) an iconified Frame (including JFrame) or JDesktopIcon (iconified JInternalFrame) by ref. Frame is restored from the OS taskbar; JDesktopIcon is replaced by its JInternalFrame on the JDesktopPane. The resulting window state depends on the pre-iconification state and the platform window manager — the window may be restored to normal or maximized. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | **Frame (including JFrame):** the restore is performed by calling `frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED)` via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). This clears only the ICONIFIED bit and preserves existing extended-state bits (e.g. `MAXIMIZED_BOTH`), so an iconified-maximized frame is restored to maximized rather than normal (see DR-009). **JDesktopIcon:** the underlying JInternalFrame is resolved via `desktopIcon.getInternalFrame()`, then `iframe.setIcon(false)` is called via `SwingUtilities.invokeLater()`. `PropertyVetoException` is silently caught — a `VetoableChangeListener` may reject the restore; the client calls `swing_snapshot` to check the outcome. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The restore dispatch is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If `supportsRestore()` returns false, the tool returns an MCP-level error (`isError: true`). The error message is derived by inspecting the component to pick the most specific explanation: **Frame — not iconified:** "Frame is not iconified". **Fallback:** "Component does not support restore. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Restoring is a window-level action; it does not depend on the component's enabled state. |
| BR-07 | `swing_restore` is a **mutation tool** — it clears the ref map in a `finally` block after execution, regardless of success or failure (see **architecture.md §3 rule 3**). |
| BR-08 | **Snapshot action:** `restore` is listed in the actions of a Frame or JDesktopIcon when `supportsRestore()` returns true. |
| BR-09 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Posted restore on ref=<N>` (see **DR-010**). |

### Algorithm: detecting restore support

See **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

`supportsRestore(Accessible)` delegates to `isIconified(Accessible)` for windows, and adds JDesktopIcon handling on top:

- **JDesktopIcon:** returns `true` when `isShowing()` is true. JDesktopIcon is a component, not a window — it is not considered iconified by `isIconified()`, but it *is* restorable because it is the visible representation of an iconified JInternalFrame.
- **Frame (including JFrame):** delegates to `isIconified()`, which returns `true` when all of:
  1. `isShowing()` is true
  2. `(getExtendedState() & Frame.ICONIFIED) != 0` (currently iconified)
- **JInternalFrame:** delegates to `isIconified()`, which returns `true` when `isShowing() && isIcon()`. In practice this is always `false` — when a JInternalFrame is iconified, it is removed from the component and accessibility trees and replaced by a JDesktopIcon (DR-008). The AI targets the JDesktopIcon, not the hidden JInternalFrame.
- **All other types** (JDialog, Window, other components): returns `false`.

### Execution order

1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-05** — `supportsRestore(accessible)` — fail before any interaction.
3. Fire-and-forget restore dispatch:
   - **Frame:** `SwingUtilities.invokeLater(() -> frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED))` — return `null`.
   - **JDesktopIcon:** resolve via `desktopIcon.getInternalFrame()`, then `SwingUtilities.invokeLater(() -> { try { iframe.setIcon(false); } catch (PropertyVetoException e) { /* silently ignored */ } })` — return `null`.

The client calls `swing_snapshot` after to determine whether the window was restored. For Frame, the snapshot no longer shows `[iconified]` and the `restore` action is no longer listed (the `iconify` action reappears). For JDesktopIcon, the icon is replaced by the JInternalFrame in the snapshot.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingRestoreToolTest` (headless)
  - [x] Each non-Frame/JDesktopIcon component from the component matrix returns an MCP error (`isError: true`) when `swing_restore` is called on it.
  - [x] Calling `swing_restore` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [x] `SwingRestoreScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Calling `swing_restore` on an iconified JFrame restores the frame; snapshot no longer shows `[iconified]` and no longer lists the `restore` action (verified after EDT drains).
  - [x] After restoring an iconified JFrame, the `iconify` action is listed in the snapshot actions.
  - [x] A non-iconified JFrame does not show `restore` in the snapshot actions.
  - [x] Calling `swing_restore` on a non-iconified JFrame via a stale ref returns an MCP error with `isError: true` and message "Frame is not iconified".
  - [x] Snapshot of an iconified JFrame shows `restore` in its actions.
  - [x] Restoring an iconified-maximized JFrame clears the ICONIFIED bit; snapshot shows the frame without `[iconified]` (verified after EDT drains). Note: MAXIMIZED_BOTH preservation is platform/WM-dependent — the tool passes `getExtendedState() & ~ICONIFIED` but the WM may drop MAXIMIZED bits (observed on Xvfb).
  - [x] Calling `swing_restore` on a JDesktopIcon restores the underlying JInternalFrame; the snapshot shows the JInternalFrame in place of the JDesktopIcon (verified after EDT drains).
  - [x] Snapshot of a JDesktopIcon shows `restore` in its actions.
  - [x] Calling `swing_restore` on a JDesktopIcon whose `VetoableChangeListener` rejects the restore returns the DR-010 echo `Posted restore on ref=N`; the JDesktopIcon is still present in the snapshot — verifies we call `setIcon(false)` which respects vetoes.
  - [x] JInternalFrame (non-iconified) does not show `restore` in its actions.
  - [x] `JDesktopPane` component matrix: `swing_restore` returns an MCP error with `isError: true`.
