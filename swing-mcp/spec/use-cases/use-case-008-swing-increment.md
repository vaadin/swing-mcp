# UC-008: swing_increment

---

**As an** AI agent, **I want to** increment the value of a UI component by ref **so that** I can increase the value of a spinner or slider one step at a time.

**Status:** Approved
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_increment` with the `ref` parameter identifying the component.
- The tool validates the ref and component, then fires the increment action asynchronously and returns `null` immediately.
- I call `swing_snapshot` again to observe the updated value, or use `swing_get_value` to read the new value without invalidating refs.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The increment action is fired by scanning the component's `AccessibleAction` descriptions for `AccessibleAction.INCREMENT` (`"increment"`) to get action index `i`, then posting `doAccessibleAction(i)` via `SwingUtilities.invokeLater()`. No UIManager lookup is needed — `JSlider` and `JSpinner` use the static constant directly. See **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**. |
| BR-04 | If the target does not support increment (i.e. `SwingUtils.supportsIncrement(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support increment. Call swing_snapshot to verify the list of actions". |
| BR-05 | All validation runs on the EDT inside `runInEDT()`. The action is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-08 | `swing_increment` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | The step size and boundary behaviour are determined entirely by the component and the accessibility API. The tool invokes the action once per call and accepts whatever the API does. The AI must call `swing_increment` multiple times to increment by more than one step. |
| BR-10 | Unlike `swing_toggle_popup`, `doAccessibleAction` for increment works correctly in headless mode for both `JSpinner` and `JSlider`. All happy-path tests can therefore run headless; `SwingIncrementScreenTest` exists solely for `JFrame`/`JDialog` coverage required by the component matrix. |

### Algorithm: detecting and invoking the increment action

`SwingUtils.supportsIncrement(Accessible a)` is a utility method on `SwingUtils` (alongside `supportsClick`, `supportsTogglePopup`, etc.) that scans `AccessibleAction` descriptions for `AccessibleAction.INCREMENT` and returns the action index or `-1`:

```java
public static int supportsIncrement(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return -1;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return -1;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        if (AccessibleAction.INCREMENT.equals(aa.getAccessibleActionDescription(i)))
            return i;
    }
    return -1;
}
```

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsIncrement(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. `SwingUtilities.invokeLater(() -> aa.doAccessibleAction(i))` — fire-and-forget; return `null`.

---

## Acceptance Criteria

- [ ] Calling `swing_increment` on a `JSpinner` fires the increment action; the value increases (verified after EDT drains).
- [ ] Calling `swing_increment` on a `JSlider` fires the increment action; the value increases (verified after EDT drains).
- [ ] Calling `swing_increment` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_increment` on a component that does not support increment (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_increment` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] The tool returns `null` on success.
- [ ] The ref map is cleared after every `swing_increment` call (mutation tool).
- [ ] Calling `swing_increment` on a `JSpinner` at its maximum silently does nothing (no MCP error — the client observes the unchanged value via `swing_snapshot`).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingIncrementTest` (headless — all happy-path tests can run headless; `HeadlessException` does not occur for increment/decrement)
  - [ ] Incrementing a `JSpinner` (`SpinnerNumberModel`) fires the action; value increases (verified after EDT drains).
  - [ ] Incrementing a `JSpinner` (`SpinnerListModel`) fires the action; advances to next item (verified after EDT drains).
  - [ ] Incrementing a `JSpinner` (`SpinnerDateModel`) fires the action; advances by one date unit (verified after EDT drains).
  - [ ] Incrementing a `JSlider` fires the action; value increases (verified after EDT drains).
  - [ ] Incrementing a `JSpinner` at its maximum returns `null` (fire-and-forget — no MCP error; value stays at max).
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Component without increment support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] Disabled component returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Success returns `null`.
  - [ ] Ref map is cleared after a successful call.
  - [ ] MCP client smoke test.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingIncrementScreenTest` (`testSwing` — JFrame/JDialog coverage per `verification.md` matrix; no tests here that cannot run headless)
  - [ ] Incrementing a `JSpinner` inside `JFrame` increases its value (verified after EDT drains).
  - [ ] Incrementing a `JSlider` inside `JFrame` increases its value (verified after EDT drains).
  - [ ] Incrementing a `JSpinner` inside `JDialog` increases its value (verified after EDT drains).

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`increment` supported):**
`JSpinner`, `JSlider`

**Expected to fail with "Component does not support increment" error:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
