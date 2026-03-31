# UC-009: swing_decrement

---

**As an** AI agent, **I want to** decrement the value of a UI component by ref **so that** I can decrease the value of a spinner or slider one step at a time.

**Status:** Approved
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_decrement` with the `ref` parameter identifying the component.
- The tool looks up the component by ref and invokes the decrement `AccessibleAction`.
- The tool returns `null` (empty content array) on success.
- I call `swing_snapshot` again to observe the updated value, or use `swing_get_value` to read the new value without invalidating refs.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The decrement action is invoked by scanning the component's `AccessibleAction` descriptions for `AccessibleAction.DECREMENT` (`"decrement"`) and calling `doAccessibleAction(i)`. No UIManager lookup is needed — `JSlider` and `JSpinner` use the static constant directly. |
| BR-04 | If the target does not support decrement (i.e. `SwingUtils.supportsDecrement(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support decrement. Call swing_snapshot to verify the list of actions". |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-07 | If `doAccessibleAction(i)` returns `false`, the tool returns an MCP-level error (`isError: true`) with the message "The action was not performed, no additional information has been provided. Probable causes: component hit min value and refused to decrement further". Note: `JSpinner` at its minimum value returns `false` (confirmed for `SpinnerNumberModel`, `SpinnerListModel`, and `SpinnerDateModel`); `JSlider` at minimum returns `true` and silently stays at min. |
| BR-08 | `swing_decrement` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | The step size and boundary behaviour are determined entirely by the component and the accessibility API. The tool invokes the action once per call and accepts whatever the API does. The AI must call `swing_decrement` multiple times to decrement by more than one step. |
| BR-10 | `doAccessibleAction` for decrement works correctly in headless mode for both `JSpinner` and `JSlider`. All happy-path tests can therefore run headless; `SwingDecrementScreenTest` exists solely for `JFrame`/`JDialog` coverage required by the component matrix. |

### Algorithm: detecting and invoking the decrement action

`SwingUtils.supportsDecrement(Accessible a)` is a utility method on `SwingUtils` (alongside `supportsIncrement`, `supportsClick`, `supportsTogglePopup`, etc.) that scans `AccessibleAction` descriptions for `AccessibleAction.DECREMENT` and returns the action index or `-1`:

```java
public static int supportsDecrement(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return -1;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return -1;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        if (AccessibleAction.DECREMENT.equals(aa.getAccessibleActionDescription(i)))
            return i;
    }
    return -1;
}
```

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsDecrement(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. **BR-07** — `doAccessibleAction(i)` — if it returns `false`, report failure.

---

## Acceptance Criteria

- [ ] Calling `swing_decrement` on a `JSpinner` decreases its value by one step.
- [ ] Calling `swing_decrement` on a `JSlider` decreases its value by one step.
- [ ] Calling `swing_decrement` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_decrement` on a component that does not support decrement (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_decrement` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] The tool returns `null` on success.
- [ ] The ref map is cleared after every `swing_decrement` call (mutation tool).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingDecrementTest` (headless — all happy-path tests can run headless; `HeadlessException` does not occur for decrement)
  - [ ] Decrementing a `JSpinner` (`SpinnerNumberModel`) decreases its value by one step.
  - [ ] Decrementing a `JSpinner` (`SpinnerListModel`) moves to the previous item.
  - [ ] Decrementing a `JSpinner` (`SpinnerDateModel`) moves back by one date unit.
  - [ ] Decrementing a `JSlider` decreases its value by one step.
  - [ ] Decrementing a `JSpinner` (`SpinnerNumberModel`) at its minimum returns an MCP error with `isError: true` (BR-07).
  - [ ] Decrementing a `JSpinner` (`SpinnerDateModel`) at its minimum returns an MCP error with `isError: true` (BR-07).
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Component without decrement support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] Disabled component returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Success returns `null`.
  - [ ] Ref map is cleared after a successful call.
  - [ ] MCP client smoke test.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingDecrementScreenTest` (`testSwing` — JFrame/JDialog coverage per `verification.md` matrix; no tests here that cannot run headless)
  - [ ] Decrementing a `JSpinner` inside `JFrame` decreases its value.
  - [ ] Decrementing a `JSlider` inside `JFrame` decreases its value.
  - [ ] Decrementing a `JSpinner` inside `JDialog` decreases its value.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`decrement` supported):**
`JSpinner`, `JSlider`

**Expected to fail with "Component does not support decrement" error:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
