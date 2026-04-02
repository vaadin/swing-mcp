# UC-013: swing_set_value

---

**As an** AI agent, **I want to** set the numeric value of a UI component by ref **so that** I can adjust sliders, spinners, and split pane dividers during Swing app migration.

**Status:** Implemented
**Date:** 2026-04-02

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_value` with the ref to learn the current value and the min/max range.
- I call `swing_set_value` with the `ref` parameter identifying the component and a `value` parameter containing the new numeric value.
- The tool validates the ref, capability, enabled state, and range, then fires `setCurrentAccessibleValue()` asynchronously and returns `null` immediately.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

**Tool description:** "Set the numeric value of a UI component by ref. Call swing_get_value first to check the current value and valid range. Call swing_snapshot first to obtain refs."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. The `value` parameter is required and must be a number. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `set_value` (i.e. `SwingUtils.supportsSetValue(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support set_value. Call swing_snapshot to verify the list of actions". |
| BR-04 | All validation runs on the EDT inside `runInEDT()`. The `setCurrentAccessibleValue()` call is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-05 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. The enabled check runs before the range check (BR-07). |
| BR-06 | `swing_set_value` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-07 | **Range validation.** Before setting the value, the tool reads `getMinimumAccessibleValue()` and `getMaximumAccessibleValue()`. If `min` is non-null and `value < min`, the tool returns an MCP-level error (`isError: true`) with the message "Value N is below the minimum (M). Call swing_get_value to check the valid range." If `max` is non-null and `value > max`, the error message is "Value N is above the maximum (M). Call swing_get_value to check the valid range." The comparison uses `doubleValue()` for generality. If `min` or `max` is null (unbounded), that bound is not checked. Min is checked before max. Numbers in error messages are formatted using the same `serializeNumber()` logic as UC-012 BR-10 (integer when whole). |
| BR-08 | **Type preservation.** The incoming `value` parameter arrives as a JSON number (Gson deserializes as `Double`). Before passing it to `setCurrentAccessibleValue()`, the tool reads the component's `getCurrentAccessibleValue()` and converts the incoming value to the same Java numeric type. This is critical: probe testing confirmed that `SpinnerNumberModel.setValue()` stores whatever `Number` type it receives, contaminating the model's value class (e.g. a `BigDecimal` model receiving `Double(60.5)` permanently changes its value class to `Double`). Conversion table: `Integer` → `intValue()`; `Long` → `longValue()`; `Float` → `floatValue()`; `Double` → `doubleValue()`; `BigDecimal` → `BigDecimal.valueOf(doubleValue())`. If the current value's class is none of these, pass the incoming value as-is. |
| BR-11 | **Whole-number validation.** When the target type is `Integer` or `Long` (determined via BR-08), and the incoming value has a fractional part (`doubleValue() % 1 != 0`), the tool returns an MCP-level error (`isError: true`) with the message "Value N cannot be set — this component requires a whole number." This prevents silent truncation (e.g. `42.5` → `42`). The check runs after range validation (BR-07) and before the type conversion (BR-08). |
| BR-09 | The tool returns `null` (empty content array) on success, consistent with other mutation tools (`swing_click`, `swing_set_text`). |
| BR-10 | `Parameters` must provide a `getNumber(String key)` method that returns the raw `Number` value (without converting to `int`). This is a prerequisite infrastructure change. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` or `value` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-03** — `SwingUtils.supportsSetValue(accessible)` — if `false`, fail with error.
4. **BR-05** — `SwingUtils.isEffectivelyEnabled(accessible)` — if `false`, fail with "disabled" error.
5. Obtain `AccessibleValue av = ac.getAccessibleValue()`.
6. **BR-07** — range validation: read `min = av.getMinimumAccessibleValue()`, `max = av.getMaximumAccessibleValue()`. If min is non-null and `value.doubleValue() < min.doubleValue()`, or max is non-null and `value.doubleValue() > max.doubleValue()`, fail with range error.
7. **BR-08** — type preservation: read `current = av.getCurrentAccessibleValue()`, determine target class.
8. **BR-11** — if target class is `Integer` or `Long` and `value.doubleValue() % 1 != 0`, fail with "requires a whole number" error.
9. Convert `value` to the target class per BR-08 conversion table.
10. `SwingUtilities.invokeLater(() -> av.setCurrentAccessibleValue(convertedValue))` — fire-and-forget; return `null`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleValue()` — detection and value access
- `AccessibleValue.getCurrentAccessibleValue()` — for type inference (BR-08)
- `AccessibleValue.getMinimumAccessibleValue()` — lower bound for range check
- `AccessibleValue.getMaximumAccessibleValue()` — upper bound for range check
- `AccessibleValue.setCurrentAccessibleValue(Number n)` — sets the new value
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check

### Design notes

- **`JProgressBar`** — excluded by `supportsSetValue()` via `READ_ONLY_VALUE_ROLES`. The AI cannot set a progress bar's value.
- **`JSplitPane`** — supported. The divider location is set via `setCurrentAccessibleValue()`. The range check (BR-07) naturally prevents invalid values (min=0, max=available space). Setting the value triggers layout recalculation.
- **`JSpinner(SpinnerNumberModel)`** — type preservation (BR-08) is critical. Probe testing confirmed that `SpinnerNumberModel.setValue()` stores whatever `Number` type it receives — a `BigDecimal` model receiving `Double(60.5)` permanently changes its value class to `Double`, which can break downstream code that casts to `BigDecimal`. Supported types: `Integer`, `Long`, `Float`, `Double`, `BigDecimal`. For `BigDecimal`, conversion uses `BigDecimal.valueOf(doubleValue())` which preserves reasonable precision; extreme-precision `BigDecimal` values (beyond double's ~15 significant digits) will lose precision, but this is acceptable for Swing UI use cases.
- **No editable check** — unlike `swing_set_text` (which checks `EDITABLE` state), value components don't have a separate editable/read-only flag. A slider or spinner is either enabled or disabled. The enabled check (BR-05) is sufficient.
- **Model type stability** — type preservation (BR-08) reads the current value's class at call time. If the model's type changes between `get_value` and `set_value` (e.g. external code replaces an `Integer` model with a `Double`), `set_value` still picks the correct type because it reads the *current* value, not a cached one. If the model changes dramatically (e.g. from `SpinnerNumberModel` to `SpinnerDateModel`), `supportsSetValue()` will return `false` and the tool fails with "unsupported". This is an accepted risk — apps almost universally stick to one type per component.
- **`setCurrentAccessibleValue()` return value** — returns `boolean` indicating success. Under fire-and-forget, this return value is discarded. Validation (BR-03, BR-05, BR-07) catches pre-condition errors synchronously; the AI observes outcomes via `swing_snapshot`.
- **`serializeNumber()`** — the number formatting logic (integer when whole) from UC-012 `SwingGetValueTool` must be moved to `SwingUtils` so both `SwingGetValueTool` and `SwingSetValueTool` can share it.

---

## Acceptance Criteria

- [x] Calling `swing_set_value` with a valid ref for a `JSlider` and a value within range changes the slider position.
- [x] Calling `swing_set_value` with a valid ref for a `JSpinner(SpinnerNumberModel)` changes the spinner value.
- [x] Calling `swing_set_value` with a valid ref for a `JSplitPane` changes the divider location.
- [x] Calling `swing_set_value` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_set_value` on a component that does not support `set_value` (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [x] Calling `swing_set_value` on a `JProgressBar` returns an MCP error (read-only value).
- [x] Calling `swing_set_value` on a disabled component returns an MCP error explaining the component is disabled.
- [x] Calling `swing_set_value` with a value below the component's minimum returns a range error.
- [x] Calling `swing_set_value` with a value above the component's maximum returns a range error.
- [x] Calling `swing_set_value` on an unbounded `JSpinner` with any numeric value succeeds (no range error).
- [x] The ref map is cleared after a successful `swing_set_value` call (mutation tool).
- [x] The ref map is cleared even after a failed `swing_set_value` call that passed ref lookup.
- [x] The tool returns `null` (empty content array) on success.
- [x] Type preservation: setting a value on a `JSpinner` whose model holds `Integer` passes an `Integer` to the accessibility API.
- [x] Type preservation: setting a value on a `JSpinner` whose model holds `BigDecimal` passes a `BigDecimal` to the accessibility API.
- [x] Setting a fractional value (e.g. `42.5`) on a component whose model holds `Integer` returns an MCP error saying the component requires a whole number.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `ParametersTest` update
  - [x] `getNumber()` returns the raw `Number` for a numeric value.
  - [x] `getNumber()` throws `MCPServerException` when key is missing.
  - [x] `getNumber()` throws `MCPServerException` when value is not a Number.

- [x] `SwingSetValueTest` (headless)
  - [x] Setting a `JSlider` value within range changes the slider position.
  - [x] Setting a `JSpinner(SpinnerNumberModel)` value within range changes the spinner value.
  - [x] Setting a `JSplitPane` value changes the divider location.
  - [x] Setting with an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Setting on a component without value support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Setting on a `JProgressBar` returns an MCP error (read-only value via `supportsSetValue`).
  - [x] Setting on a disabled `JSlider` returns an MCP error explaining the component is disabled.
  - [x] Setting a value below min returns a range error with `isError: true`.
  - [x] Setting a value above max returns a range error with `isError: true`.
  - [x] Setting a value on an unbounded `JSpinner(SpinnerNumberModel(5, null, null, 1))` succeeds.
  - [x] The ref map is cleared after a successful `swing_set_value` call (verified by attempting to use the same ref again).
  - [x] The ref map is cleared after a failed call on a disabled component.
  - [x] Type preservation: setting a value on a `JSpinner` with `Integer` model preserves `Integer` type (verified by reading back the model's value class).
  - [x] Type preservation: setting a value on a `JSpinner` with `Double` model preserves `Double` type.
  - [x] Type preservation: setting a value on a `JSpinner` with `BigDecimal` model preserves `BigDecimal` type.
  - [x] Setting a fractional value on an `Integer`-model spinner returns an MCP error (BR-11).
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingSetValueScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Setting a `JSlider` value inside `JFrame` changes the slider position.
  - [x] Setting a `JSpinner(SpinnerNumberModel)` value inside `JFrame` changes the spinner value.
  - [x] Setting a `JSlider` value inside `JDialog` changes the slider position.
  - [x] Setting on a disabled `JSlider` inside `JFrame` returns an MCP error.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`set_value` supported):**
`JSlider`, `JSpinner(SpinnerNumberModel)`, `JSplitPane`

**Expected to fail with "Component does not support set_value" error:**
`JProgressBar` (read-only value), `JSpinner(SpinnerDateModel)`, `JSpinner(SpinnerListModel)`, `JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JComboBox`, `JToggleButton`, `JLabel`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
