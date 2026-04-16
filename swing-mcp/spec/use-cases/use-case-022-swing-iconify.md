# UC-022: swing_iconify

**Status:** Implemented
**Date:** 2026-04-14

Iconifies (minimizes) a Frame (including JFrame) or JInternalFrame — needed because the snapshot previously exposed no window-management actions beyond `close`, so the AI could not minimize windows.

**Tool description:** "Iconify (minimize) a Frame (including JFrame) or JInternalFrame by ref. Frame is minimized to the OS taskbar; JInternalFrame is replaced by a JDesktopIcon on its JDesktopPane. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | **Frame (including JFrame):** the iconify is performed by calling `frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED)` via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). Preserves existing extended-state bits (e.g. `MAXIMIZED_BOTH`). **JInternalFrame:** the iconify is performed by calling `iframe.setIcon(true)` via `SwingUtilities.invokeLater()`. `PropertyVetoException` is silently caught — a `VetoableChangeListener` may reject the iconify; the client calls `swing_snapshot` to check the outcome. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The iconify dispatch is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If `supportsIconify()` returns false, the tool returns an MCP-level error (`isError: true`). The error message is derived by inspecting the component to pick the most specific explanation: **Frame — undecorated:** "Frame is undecorated and cannot be iconified". **Frame — already iconified:** "Frame is already iconified". **JInternalFrame — not iconifiable:** "JInternalFrame is not iconifiable". **JInternalFrame — already iconified:** "JInternalFrame is already iconified". **Fallback:** "<ClassName> does not support iconify. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-06 | `isEffectivelyEnabled()` is **not** checked. Iconifying is a window-level action; it does not depend on the component's enabled state. |
| BR-07 | `swing_iconify` is a **mutation tool** — it clears the ref map after successful execution (see **architecture.md §3 rule 3**). A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map. |
| BR-08 | **Snapshot action:** `iconify` is listed in the actions of a Frame or JInternalFrame when `supportsIconify()` returns true. |
| BR-09 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Posted iconify on ref=<N>` (see **DR-010**). |

### Algorithm: detecting iconify support

See **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

`supportsIconify(Accessible)`:

- **Frame (including JFrame):** returns `true` when all of:
  1. `isShowing()` is true
  2. `isUndecorated()` is false (undecorated frames have no minimize button — same rationale as `supportsClose`)
  3. `(getExtendedState() & Frame.ICONIFIED) == 0` (not already iconified)
- **JInternalFrame:** returns `true` when all of:
  1. `isShowing()` is true
  2. `isIconifiable()` is true (the internal frame has an iconify button in its title bar)
  3. `isIcon()` is false (not already iconified — an iconified JInternalFrame becomes a JDesktopIcon in the tree, so this check is defensive)
- **All other types** (JDialog, Window, JDesktopIcon, other components): returns `false`.

### Execution order

1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-05** — `supportsIconify(accessible)` — fail before any interaction.
3. Fire-and-forget iconify dispatch:
   - **Frame:** `SwingUtilities.invokeLater(() -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED))` — return `null`.
   - **JInternalFrame:** `SwingUtilities.invokeLater(() -> { try { iframe.setIcon(true); } catch (PropertyVetoException e) { /* silently ignored */ } })` — return `null`.

The client calls `swing_snapshot` after to determine whether the frame was iconified. For Frame, the snapshot shows `[iconified]` (DR-009) and the `iconify` action is no longer listed. For JInternalFrame, the frame is replaced by a `JDesktopIcon` in the snapshot (DR-008).

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingIconifyToolTest` (headless)
  - [x] Each non-Frame/JInternalFrame component from the component matrix returns an MCP error (`isError: true`) when `swing_iconify` is called on it.
  - [x] Calling `swing_iconify` with an invalid ref returns an MCP error with `isError: true` and a recovery message.

- [x] `SwingIconifyScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Calling `swing_iconify` on a decorated JFrame iconifies the frame; snapshot shows `[iconified]` and no longer lists the `iconify` action (verified after EDT drains).
  - [x] Calling `swing_iconify` on an undecorated JFrame returns an MCP error with `isError: true` and message "Frame is undecorated and cannot be iconified".
  - [x] An already-iconified JFrame does not show `iconify` in the snapshot actions.
  - [x] Calling `swing_iconify` on an already-iconified JFrame via a stale ref returns an MCP error with `isError: true` and message "Frame is already iconified".
  - [x] Snapshot of a visible decorated JFrame shows `iconify` in its actions.
  - [x] Calling `swing_iconify` on an iconifiable JInternalFrame iconifies it; the snapshot shows a JDesktopIcon in its place (verified after EDT drains).
  - [x] Calling `swing_iconify` on a JInternalFrame with `isIconifiable() == false` returns an MCP error with `isError: true` and message "JInternalFrame is not iconifiable".
  - [x] Snapshot of a visible iconifiable JInternalFrame shows `iconify` in its actions.
  - [x] Snapshot of a JInternalFrame with `isIconifiable() == false` does not show `iconify` in its actions.
  - [x] Calling `swing_iconify` on a JInternalFrame whose `VetoableChangeListener` rejects the iconify returns the DR-010 echo `Posted iconify on ref=N`; the internal frame is still showing (not replaced by JDesktopIcon) — verifies we call `setIcon(true)` which respects vetoes.
  - [x] JDesktopIcon does not show `iconify` in its actions.
  - [x] `JDesktopPane` component matrix: `swing_iconify` returns an MCP error with `isError: true`.
