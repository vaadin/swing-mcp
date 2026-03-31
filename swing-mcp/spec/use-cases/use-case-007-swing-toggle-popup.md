# UC-007: swing_toggle_popup

---

**As an** AI agent, **I want to** open or close the popup of a UI component by ref **so that** I can expand a combo box to reveal its items or collapse it after selection.

**Status:** Implemented
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_toggle_popup` with the `ref` parameter identifying the component.
- The tool looks up the component by ref and invokes the toggle-popup `AccessibleAction`.
- The tool returns `null` (empty content array) on success.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes (e.g. new items visible after popup opens).

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The toggle-popup action is invoked by finding and calling `doAccessibleAction(i)` where `i` is the index returned by `SwingUtils.supportsTogglePopup(accessible)`. See **architecture.md § 4 — Detecting Toggle-Popup Support** for the full detection algorithm. |
| BR-04 | If the target does not support toggle-popup (i.e. `SwingUtils.supportsTogglePopup(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support toggle_popup. Call swing_snapshot to verify the list of actions". |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-07 | If `doAccessibleAction(i)` returns `false`, the tool returns an MCP-level error (`isError: true`) with the message "The action was not performed, no additional information has been provided". |
| BR-08 | `swing_toggle_popup` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). The AI must call `swing_snapshot` after every `swing_toggle_popup` call to obtain fresh refs. This may be relaxed in the future. |
| BR-09 | The tool toggles the popup regardless of its current open/closed state. If the popup is already open, calling this tool closes it; if closed, it opens it. The AI can infer the current state from the snapshot. |
| BR-10 | `doAccessibleAction` on `JComboBox` throws `java.awt.HeadlessException` in headless mode (popup display requires `getScreenSize()`). This is not a concern in production — the MCP server only runs inside a real Swing app with a display. As a consequence, the happy-path test (successful toggle) cannot run headless and must live in `SwingTogglePopupScreenTest`. |
| BR-11 | Both editable (`setEditable(true)`) and non-editable `JComboBox` support `toggle_popup` via the same accessibility action. Both are tested in `SwingTogglePopupScreenTest`. |

### Algorithm: detecting and invoking the toggle-popup action

See **architecture.md § 4 — Detecting Toggle-Popup Support** for the full algorithm.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsTogglePopup(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. **BR-07** — `doAccessibleAction(i)` — if it returns `false`, report failure.

---

## Acceptance Criteria

- [x] Calling `swing_toggle_popup` with a valid ref for a `JComboBox` opens the popup (requires display — verified in `SwingTogglePopupScreenTest`).
- [x] Calling `swing_toggle_popup` again on the same `JComboBox` closes the popup (requires display — verified in `SwingTogglePopupScreenTest`).
- [x] Calling `swing_toggle_popup` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_toggle_popup` on a component that does not support toggle-popup (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [x] Calling `swing_toggle_popup` on a disabled `JComboBox` returns an MCP error explaining the component is disabled.
- [x] The tool returns `null` on success.
- [x] The ref map is cleared after every `swing_toggle_popup` call (mutation tool).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingTogglePopupTest` (headless — error cases only; see BR-10)
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Component without toggle-popup support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Disabled `JComboBox` returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingTogglePopupScreenTest` (`testSwing` — requires display; happy-path tests live here per BR-10)
  - [x] Toggling popup on a non-editable `JComboBox` inside `JFrame` opens it (verified via `isPopupVisible()`).
  - [x] Toggling popup again closes it.
  - [x] Toggling popup on an editable `JComboBox` inside `JFrame` opens it (BR-11).
  - [x] Toggling popup on a `JComboBox` inside `JDialog` opens it.
  - [x] Success returns `null`.
  - [x] Ref map is cleared after a successful call.
  - [x] MCP client smoke test.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`toggle_popup` supported):**
`JComboBox`

**Expected to fail with "Component does not support toggle_popup" error:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`, `JToggleButton`, `JSpinner`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
