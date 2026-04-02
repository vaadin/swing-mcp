# UC-012: swing_get_value

---

**As an** AI agent, **I want to** read the numeric value of a UI component by ref **so that** I can understand the current value of sliders, spinners, progress bars, and split panes without relying on the snapshot (which omits field values per UC-002 BR-03).

**Status:** Draft
**Date:** 2026-04-02

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_value` with the `ref` parameter identifying the component whose value I want to read.
- The tool looks up the component by ref and reads its numeric value via the accessibility API (`AccessibleValue`).
- The tool returns a JSON object containing `current`, `min`, and `max` as numbers.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_value` (i.e. `SwingUtils.supportsGetValue(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support get_value. Call swing_snapshot to verify the list of actions". |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_value` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | The tool returns a JSON object. `current` (from `getCurrentAccessibleValue()`) is always present. `min` (from `getMinimumAccessibleValue()`) and `max` (from `getMaximumAccessibleValue()`) are included only when non-null (see BR-08). All values are JSON numbers. Example full result: `{"current": 42, "min": 0, "max": 100}`. |
| BR-07 | No enabled check is performed — reading a value is always allowed, even on disabled components. |
| BR-08 | `getMinimumAccessibleValue()` or `getMaximumAccessibleValue()` may return `null` for some components. If either is `null`, the corresponding field is omitted from the JSON object entirely — it is not serialised as `null`. Only `current` is guaranteed to be present. |

### Algorithm

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-03** — `SwingUtils.supportsGetValue(accessible)` — if `false`, fail with error.
3. Obtain `AccessibleValue av = ac.getAccessibleValue()`.
4. Read `Number current = av.getCurrentAccessibleValue()`.
5. Read `Number min = av.getMinimumAccessibleValue()` (may be `null`).
6. Read `Number max = av.getMaximumAccessibleValue()` (may be `null`).
7. Build and return a JSON object: always include `"current"`, include `"min"` and `"max"` only if non-null (**BR-08**).

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleValue()` — detection and value retrieval
- `AccessibleValue.getCurrentAccessibleValue()` — current value
- `AccessibleValue.getMinimumAccessibleValue()` — lower bound (may be `null`)
- `AccessibleValue.getMaximumAccessibleValue()` — upper bound (may be `null`)

---

## Acceptance Criteria

- [ ] Calling `swing_get_value` with a valid ref for a `JSlider` returns the current slider position, min, and max.
- [ ] Calling `swing_get_value` with a valid ref for a `JSpinner(SpinnerNumberModel)` returns the current spinner value, min, and max.
- [ ] Calling `swing_get_value` with a valid ref for a `JProgressBar` returns the current progress value, min, and max.
- [ ] Calling `swing_get_value` with a valid ref for a `JSplitPane` returns the current divider location plus min and max.
- [ ] Calling `swing_get_value` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_get_value` on a component that does not support `get_value` (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] The ref map is **not** cleared after a `swing_get_value` call (read-only tool).
- [ ] Calling `swing_get_value` on a disabled but value-readable component succeeds (no enabled check).
- [ ] If `getMinimumAccessibleValue()` or `getMaximumAccessibleValue()` returns `null`, the corresponding field is absent from the JSON result.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingGetValueTest` (headless)
  - [ ] Reading a `JSlider` returns `current`, `min`, and `max`.
  - [ ] Reading a `JSpinner(SpinnerNumberModel)` returns `current`, `min`, and `max`.
  - [ ] Reading a `JProgressBar` returns `current`, `min`, and `max`.
  - [ ] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Reading a component without value support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] The ref map is preserved after a successful `swing_get_value` call (verified by calling `swing_get_value` twice with the same ref).
  - [ ] Reading a disabled `JSlider` succeeds and returns its value.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingGetValueScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Reading a `JSlider` inside `JFrame` returns its value.
  - [ ] Reading a `JSpinner(SpinnerNumberModel)` inside `JFrame` returns its value.
  - [ ] Reading a `JProgressBar` inside `JFrame` returns its value.
  - [ ] Reading a `JSlider` inside `JDialog` returns its value.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_value` supported):**
`JSlider`, `JSpinner(SpinnerNumberModel)`, `JProgressBar`, `JSplitPane`

**Expected to fail with "Component does not support get_value" error:**
`JSpinner(SpinnerDateModel)`, `JSpinner(SpinnerListModel)`, `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JComboBox`, `JToggleButton`, `JLabel`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
