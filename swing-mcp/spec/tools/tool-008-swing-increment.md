# T-008: swing_increment

**Status:** Implemented
**Date:** 2026-03-31

Steps a spinner or slider up by one unit. The AI calls this repeatedly to reach a target value; step size and boundary behaviour are whatever the accessibility API does.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The increment action is fired by scanning the component's `AccessibleAction` descriptions for `AccessibleAction.INCREMENT` (`"increment"`) to get action index `i`, then posting `doAccessibleAction(i)` via `SwingUtilities.invokeLater()`. No UIManager lookup is needed — `JSlider` and `JSpinner` use the static constant directly. See **D_fire_and_forget_dispatch**. |
| BR-04 | If the target does not support swing_increment (i.e. `SwingUtils.supportsIncrement(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "<ClassName> does not support swing_increment. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-05 | All validation runs on the EDT inside `runInEDT()`. The action is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-06 | If the target is not effectively enabled (see **D_mirror_swing_semantics**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-08 | `swing_increment` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after successful invocation. A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map — the UI state hasn't changed, so existing refs remain valid and the AI can retry without re-snapshotting. |
| BR-09 | The step size and boundary behaviour are determined entirely by the component and the accessibility API. The tool invokes the action once per call and accepts whatever the API does. The AI must call `swing_increment` multiple times to increment by more than one step. |
| BR-10 | Unlike `swing_toggle_popup`, `doAccessibleAction` for increment works correctly in headless mode for both `JSpinner` and `JSlider`. All happy-path tests can therefore run headless; `SwingIncrementScreenTest` exists solely for `JFrame`/`JDialog` coverage required by the component matrix. |
| BR-11 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Dispatched increment on ref=<N> — call swing_snapshot to verify the outcome` (see **D_dispatched_echo**). |

### Algorithm: detecting and invoking the increment action

`SwingUtils.supportsIncrement(Accessible a)` scans `AccessibleAction` descriptions for `AccessibleAction.INCREMENT` and returns the action index or `-1`.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsIncrement(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. `SwingUtilities.invokeLater(() -> aa.doAccessibleAction(i))` — fire-and-forget; return `null`.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingIncrementTest` (headless — all happy-path tests can run headless; `HeadlessException` does not occur for increment/decrement)
  - [x] Incrementing a `JSpinner` (`SpinnerNumberModel`) fires the action; value increases (verified after EDT drains).
  - [x] Incrementing a `JSpinner` (`SpinnerListModel`) fires the action; advances to next item (verified after EDT drains).
  - [x] Incrementing a `JSpinner` (`SpinnerDateModel`) fires the action; advances by one date unit (verified after EDT drains).
  - [x] Incrementing a `JSlider` fires the action; value increases (verified after EDT drains).
  - [x] Incrementing a `JSpinner` at its maximum returns the D_dispatched_echo echo (fire-and-forget — no MCP error; value stays at max).
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Component without increment support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Disabled component returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Success returns the D_dispatched_echo echo `Dispatched increment on ref=N — call swing_snapshot to verify the outcome`.
  - [x] Ref map is cleared after a successful call.
  - [x] MCP client smoke test.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingIncrementScreenTest` (`testSwing` — JFrame/JDialog coverage per `design/architecture.md` § Testing matrix; no tests here that cannot run headless)
  - [x] Incrementing a `JSpinner` inside `JFrame` increases its value (verified after EDT drains).
  - [x] Incrementing a `JSlider` inside `JFrame` increases its value (verified after EDT drains).
  - [x] Incrementing a `JSpinner` inside `JDialog` increases its value (verified after EDT drains).
  - [x] Incrementing a `JSpinner` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) increases its value (verified after EDT drains).

### Component matrix

Each matrix component in `design/architecture.md` § Testing gets a dedicated test method.

**Succeed (`increment` supported):** `JSpinner`, `JSlider`.

All other matrix components return `<ClassName> does not support swing_increment`.
