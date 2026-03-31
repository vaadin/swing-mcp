# UC-008: swing_increment

---

**As an** AI agent, **I want to** increment the value of a UI component by ref **so that** I can increase the value of a spinner or slider one step at a time.

**Status:** Draft
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_increment` with the `ref` parameter identifying the component.
- The tool looks up the component by ref and invokes the increment `AccessibleAction`.
- The tool returns `null` (empty content array) on success.
- I call `swing_snapshot` again to observe the updated value, or use `swing_get_value` to read the new value without invalidating refs.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The increment action is invoked by scanning the component's `AccessibleAction` descriptions for `AccessibleAction.INCREMENT` (`"increment"`) and calling `doAccessibleAction(i)`. No UIManager lookup is needed — `JSlider` and `JSpinner` use the static constant directly. |
| BR-04 | If the target does not support increment (i.e. `SwingUtils.supportsIncrement(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support increment. Call swing_snapshot to verify the list of actions". |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-07 | If `doAccessibleAction(i)` returns `false`, the tool returns an MCP-level error (`isError: true`) with the message "The action was not performed, no additional information has been provided". |
| BR-08 | `swing_increment` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | The increment step size is determined by the component itself (e.g., `JSpinner`'s step size, `JSlider`'s minor tick unit). The tool invokes the action once per call; the AI must call it multiple times to increment by more than one step. |

### Algorithm: detecting and invoking the increment action

`SwingUtils.supportsIncrement(Accessible a)` — scans `AccessibleAction` descriptions for `AccessibleAction.INCREMENT`:

```java
int supportsIncrement(Accessible a) {
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
4. **BR-07** — `doAccessibleAction(i)` — if it returns `false`, report failure.

---

## Acceptance Criteria

- [ ] Calling `swing_increment` on a `JSpinner` increases its value by one step.
- [ ] Calling `swing_increment` on a `JSlider` increases its value by one step.
- [ ] Calling `swing_increment` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_increment` on a component that does not support increment (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_increment` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] The tool returns `null` on success.
- [ ] The ref map is cleared after every `swing_increment` call (mutation tool).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingIncrementTest`
  - [ ] Incrementing a `JSpinner` increases its value by one step.
  - [ ] Incrementing a `JSlider` increases its value by one step.
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Component without increment support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] Disabled component returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Success returns `null`.
  - [ ] Ref map is cleared after a successful call.
  - [ ] MCP client smoke test.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingIncrementScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Incrementing a `JSpinner` inside `JFrame` increases its value.
  - [ ] Incrementing a `JSlider` inside `JFrame` increases its value.
  - [ ] Incrementing a `JSpinner` inside `JDialog` increases its value.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`increment` supported):**
`JSpinner`, `JSlider`

**Expected to fail with "Component does not support increment" error:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
