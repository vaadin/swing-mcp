# UC-009: swing_decrement

**Status:** Implemented
**Date:** 2026-03-31

Steps a spinner or slider down by one unit — mirror of `swing_increment`.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The decrement action is fired by scanning the component's `AccessibleAction` descriptions for `AccessibleAction.DECREMENT` (`"decrement"`) to get action index `i`, then posting `doAccessibleAction(i)` via `SwingUtilities.invokeLater()`. No UIManager lookup is needed — `JSlider` and `JSpinner` use the static constant directly. See **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**. |
| BR-04 | If the target does not support decrement (i.e. `SwingUtils.supportsDecrement(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support decrement. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-05 | All validation runs on the EDT inside `runInEDT()`. The action is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-08 | `swing_decrement` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | The step size and boundary behaviour are determined entirely by the component and the accessibility API. The tool invokes the action once per call and accepts whatever the API does. The AI must call `swing_decrement` multiple times to decrement by more than one step. |
| BR-10 | `doAccessibleAction` for decrement works correctly in headless mode for both `JSpinner` and `JSlider`. All happy-path tests can therefore run headless; `SwingDecrementScreenTest` exists solely for `JFrame`/`JDialog` coverage required by the component matrix. |
| BR-11 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Posted decrement on ref=<N>` (see **DR-010**). |

### Algorithm: detecting and invoking the decrement action

`SwingUtils.supportsDecrement(Accessible a)` scans `AccessibleAction` descriptions for `AccessibleAction.DECREMENT` and returns the action index or `-1`.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsDecrement(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. `SwingUtilities.invokeLater(() -> aa.doAccessibleAction(i))` — fire-and-forget; return `null`.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingDecrementTest` (headless — all happy-path tests can run headless; `HeadlessException` does not occur for decrement)
  - [x] Decrementing a `JSpinner` (`SpinnerNumberModel`) fires the action; value decreases (verified after EDT drains).
  - [x] Decrementing a `JSpinner` (`SpinnerListModel`) fires the action; moves to previous item (verified after EDT drains).
  - [x] Decrementing a `JSpinner` (`SpinnerDateModel`) fires the action; moves back by one date unit (verified after EDT drains).
  - [x] Decrementing a `JSlider` fires the action; value decreases (verified after EDT drains).
  - [x] Decrementing a `JSpinner` (`SpinnerNumberModel`) at its minimum returns the DR-010 echo (fire-and-forget — no MCP error; value stays at min).
  - [x] Decrementing a `JSpinner` (`SpinnerDateModel`) at its minimum returns the DR-010 echo (fire-and-forget — no MCP error).
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Component without decrement support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Disabled component returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Success returns the DR-010 echo `Posted decrement on ref=N`.
  - [x] Ref map is cleared after a successful call.
  - [x] MCP client smoke test.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingDecrementScreenTest` (`testSwing` — JFrame/JDialog coverage per `verification.md` matrix; no tests here that cannot run headless)
  - [x] Decrementing a `JSpinner` inside `JFrame` decreases its value (verified after EDT drains).
  - [x] Decrementing a `JSlider` inside `JFrame` decreases its value (verified after EDT drains).
  - [x] Decrementing a `JSpinner` inside `JDialog` decreases its value (verified after EDT drains).
  - [x] Decrementing a `JSpinner` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) decreases its value (verified after EDT drains).

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`decrement` supported):** `JSpinner`, `JSlider`.

All other matrix components return `Component does not support decrement`.
