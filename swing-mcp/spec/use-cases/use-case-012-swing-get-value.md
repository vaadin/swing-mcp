# UC-012: swing_get_value

---

**As an** AI agent, **I want to** read the numeric value of a UI component by ref **so that** I can understand the current value of sliders, spinners, progress bars, and split panes without relying on the snapshot (which omits field values per UC-002 BR-03).

**Status:** Implemented
**Date:** 2026-04-02

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_value` with the `ref` parameter identifying the component whose value I want to read.
- The tool looks up the component by ref and reads its numeric value via the accessibility API (`AccessibleValue`).
- The tool returns a JSON object containing `current`, `min`, and `max` as numbers.

**Tool description:** "Read the numeric value of a UI component by ref. Returns JSON with current, min, max. Missing min/max means unbounded. Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_value` (i.e. `SwingUtils.supportsGetValue(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support get_value. Call swing_snapshot to verify the list of actions". `supportsGetValue()` checks: (1) `getAccessibleValue() != null`, (2) role not in `SUPPRESSED_VALUE_ROLES`, and (3) `getCurrentAccessibleValue() != null` — see design notes. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_value` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-06 | The tool returns a JSON object via `Content.json()`. `current` (from `getCurrentAccessibleValue()`) is always present. `min` (from `getMinimumAccessibleValue()`) and `max` (from `getMaximumAccessibleValue()`) are included only when non-null (see BR-08). All values are JSON numbers. Example full result: `{"current": 42, "min": 0, "max": 100}`. |
| BR-07 | No enabled check is performed — reading a value is always allowed, even on disabled components. |
| BR-08 | `getMinimumAccessibleValue()` or `getMaximumAccessibleValue()` may return `null` for some components (e.g. unbounded `SpinnerNumberModel`). If either is `null`, the corresponding field is omitted from the JSON object entirely — it is not serialised as `null`. Only `current` is guaranteed to be present. The tool description should note that a missing `min`/`max` means the value is unbounded in that direction. |
| BR-09 | If `getCurrentAccessibleValue()` returns `null` at runtime, treat it as unsupported: return an MCP-level error (`isError: true`) with the same message as BR-03. This should not happen in practice if `supportsGetValue()` is correct, but acts as a defensive fallback. |
| BR-10 | Number serialization: serialize as an integer (`long`) when the value is a whole number (i.e. `doubleValue() % 1 == 0`), otherwise as a floating-point number. Rationale: brevity for AI readability; and `swing_set_value` must round-trip the Java type (e.g. a `JSpinner` holding `Double(42.0)` must receive `42.0`, not `42`), so the AI can infer the Java type from whether the JSON number has a fractional part. |

### Algorithm

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-03** — `SwingUtils.supportsGetValue(accessible)` — if `false`, fail with error.
3. Obtain `AccessibleValue av = ac.getAccessibleValue()`.
4. Read `Number current = av.getCurrentAccessibleValue()`. If `null`, fail with the BR-03 error (**BR-09**).
5. Read `Number min = av.getMinimumAccessibleValue()` (may be `null`).
6. Read `Number max = av.getMaximumAccessibleValue()` (may be `null`).
7. Serialize each non-null number per **BR-10**: if `value.doubleValue() % 1 == 0`, emit as `long`; otherwise emit as `double`.
8. Build and return a JSON object via `Content.json()`: always include `"current"`, include `"min"` and `"max"` only if non-null (**BR-08**).

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleValue()` — detection and value retrieval
- `AccessibleValue.getCurrentAccessibleValue()` — current value
- `AccessibleValue.getMinimumAccessibleValue()` — lower bound (may be `null`)
- `AccessibleValue.getMaximumAccessibleValue()` — upper bound (may be `null`)

### Design notes

- **`JSplitPane`** — supported (no explicit exclusion), but considered a corner case: resizing the divider rarely reveals new components. No special handling needed. An un-laid-out `JSplitPane` reports `current: -1` with `min: 0`; the AI should not attempt to set `-1` since min is `0`. The future `swing_set_value` tool will enforce a range check (`min ≤ value ≤ max`), naturally preventing invalid values.
- **`JTabbedPane`** — `getAccessibleValue()` returns `null` (role `PAGE_TAB_LIST`); verified by probe test. Naturally fails `supportsGetValue()` without needing role suppression.
- **`JSpinner(SpinnerDateModel)` / `JSpinner(SpinnerListModel)`** — probe test confirmed that `getAccessibleValue()` returns **non-null** but `getCurrentAccessibleValue()` returns **null** for both. Without a fix, `supportsGetValue()` would return a false positive, causing `get_value` to appear in the snapshot and then fail at runtime. **Fix:** `SwingUtils.supportsGetValue()` must additionally check `av.getCurrentAccessibleValue() != null`. This is a general fix (not model-specific), so any future component with the same pattern is automatically handled. Performance cost is negligible — one extra method call per node that already passed the `getAccessibleValue() != null` gate. `supportsSetValue()` must delegate to `supportsGetValue()` as a prerequisite (set implies get), which automatically inherits the `getCurrentAccessibleValue() != null` check.

---

## Acceptance Criteria

- [x] Calling `swing_get_value` with a valid ref for a `JSlider` returns the current slider position, min, and max.
- [x] Calling `swing_get_value` with a valid ref for a `JSpinner(SpinnerNumberModel)` returns the current spinner value, min, and max.
- [x] Calling `swing_get_value` with a valid ref for a `JProgressBar` returns the current progress value, min, and max.
- [x] Calling `swing_get_value` with a valid ref for a `JSplitPane` returns the current divider location plus min and max.
- [x] Calling `swing_get_value` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_get_value` on a component that does not support `get_value` (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [x] The ref map is **not** cleared after a `swing_get_value` call (read-only tool).
- [x] Calling `swing_get_value` on a disabled but value-readable component succeeds (no enabled check).
- [x] If `getMinimumAccessibleValue()` or `getMaximumAccessibleValue()` returns `null`, the corresponding field is absent from the JSON result.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingUtilsSupportsValueTest` updates (headless) — triggered by `supportsGetValue()` fix
  - [x] `JSpinner(SpinnerDateModel)` returns `false` for `supportsGetValue()`.
  - [x] `JSpinner(SpinnerListModel)` returns `false` for `supportsGetValue()`.
  - [x] `JSpinner(SpinnerDateModel)` returns `false` for `supportsSetValue()`.
  - [x] `JSpinner(SpinnerListModel)` returns `false` for `supportsSetValue()`.

- [x] `SwingGetValueTest` (headless)
  - [x] Reading a `JSlider` returns `current`, `min`, and `max`.
  - [x] Reading a `JSpinner(SpinnerNumberModel)` returns `current`, `min`, and `max`.
  - [x] Reading a `JProgressBar` returns `current`, `min`, and `max`.
  - [x] Reading an unbounded `JSpinner(SpinnerNumberModel(5, null, null, 1))` returns only `current` — `min` and `max` are absent from the JSON (BR-08).
  - [x] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Reading a component without value support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] The ref map is preserved after a successful `swing_get_value` call (verified by calling `swing_get_value` twice with the same ref).
  - [x] Reading a disabled `JSlider` succeeds and returns its value.
  - [x] Whole-number values serialize as JSON integers (e.g. `JSlider` at 42 → `"current":42`, not `"current":42.0`) (BR-10).
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingGetValueScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Reading a `JSlider` inside `JFrame` returns its value.
  - [x] Reading a `JSpinner(SpinnerNumberModel)` inside `JFrame` returns its value.
  - [x] Reading a `JProgressBar` inside `JFrame` returns its value.
  - [x] Reading a `JSlider` inside `JDialog` returns its value.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_value` supported):**
`JSlider`, `JSpinner(SpinnerNumberModel)`, `JProgressBar`, `JSplitPane`

**Expected to fail with "Component does not support get_value" error:**
`JSpinner(SpinnerDateModel)`, `JSpinner(SpinnerListModel)`, `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JComboBox`, `JToggleButton`, `JLabel`, `JPanel`, `JScrollPane`, `JTabbedPane` (role `PAGE_TAB_LIST` — `getAccessibleValue()` returns `null`; verified by probe test), `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
