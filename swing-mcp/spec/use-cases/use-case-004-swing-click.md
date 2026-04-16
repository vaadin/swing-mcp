# UC-004: swing_click

**Status:** Implemented
**Date:** 2026-03-31

A click primitive for buttons, checkboxes, menu items, and any other clickable element — the most common interaction in any Swing UI.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The click is performed by calling `supportsClick(accessible)` which returns a `Runnable` encapsulating the click action (see **architecture.md § 4 — Detecting Click Support**). The `Runnable` is posted via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). The caller does not need to know whether the click uses `AccessibleAction` (Tier 1) or synthetic `MouseEvent` (Tier 2) — this is captured inside the `Runnable`. |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The action itself is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be clicked. See also **architecture.md § 6** — Tool execution level. |
| BR-06 | If the target does not support clicking (i.e. `supportsClick()` returns `null`), the tool returns an MCP-level error (`isError: true`) with the message "<ClassName> does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-07 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Posted click on ref=<N>` (see **DR-010**). |
| BR-08 | **`JMenu` is not clickable (DR-012).** `supportsClick()` returns `null` for `JMenu`, so the snapshot never advertises `click` on a menu title and the component does not receive a ref. A stale-ref call to `swing_click` on a `JMenu` hits BR-06 and returns the generic "JMenu does not support swing_click" error — no dedicated message. Menu items (`JMenuItem`, `JCheckBoxMenuItem`, `JRadioButtonMenuItem`) retain `click` as usual. |

### Algorithm: detecting and invoking the click action

See **architecture.md § 4 — Detecting Click Support** for the full algorithm and rationale (`supportsClick()` returns a `Runnable` or `null`), and **architecture.md § 6 — Action Detection Summary** for the authoritative action-to-tool mapping.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid)
2. **BR-06** — `Runnable click = supportsClick(accessible)` — if `null`, return error
3. **BR-05** — `isEffectivelyEnabled(accessible)` — only checked when click is supported
4. `SwingUtilities.invokeLater(click)` — fire-and-forget; return `null`

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingClickTest` (headless)
  - [x] Clicking a button ref fires the action (verified by observing the button's action listener was called after the EDT drains).
  - [x] Clicking a checkbox ref fires the action (verified by observing state change after EDT drains).
  - [x] Clicking an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Clicking a disabled button returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Clicking a component without click support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [x] Each component from the component matrix is tested.
  - [x] Clicking a `ClickRecordingPanel` (see below) dispatches the full mouse event sequence and the panel reports `wasClicked() == true`.
  - [x] The synthetic MouseEvent coordinates are at the center of the component.
  - [x] The synthetic MouseEvent uses BUTTON1 with click count 1.
  - [x] Clicking a disabled `ClickRecordingPanel` returns an MCP error with `isError: true`.
  - [x] Clicking a component with no AccessibleAction click and no application MouseListener returns an MCP error with `isError: true`.
  - [x] A component with an interactive role (e.g. `JSlider`) and an application MouseListener but no AccessibleAction click is not clickable (Tier 2 skipped for interactive roles).
  - [x] `JMenu` is **not** clickable (DR-012). `componentMatrix_JMenu` registers a `JMenu` under a testing ref and asserts `swing_click` returns the generic "JMenu does not support swing_click" error. `componentMatrix_JMenuBar` verifies the `JMenu` on a menubar does not receive a snapshot ref. `JMenuItem` continues to be clickable and fires its `ActionListener`.

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
  - [x] Clicking a button inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) fires its action listener.
  - [x] Clicking a checkbox inside `JInternalFrame` toggles its state.
  - [x] Clicking a disabled button inside `JInternalFrame` returns an MCP error.

