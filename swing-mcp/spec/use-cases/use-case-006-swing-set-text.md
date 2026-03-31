# UC-006: swing_set_text

---

**As an** AI agent, **I want to** set the text content of a UI component by ref **so that** I can fill in text fields, text areas, and other editable text components.

**Status:** Draft
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_set_text` with the `ref` parameter identifying the component and a `text` parameter containing the new text.
- The tool looks up the component by ref and replaces its entire text content via the accessibility API.
- The tool returns an empty string on success.
- I call `swing_snapshot` again to get fresh refs reflecting any UI changes.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. The `text` parameter is required and must be a string. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The text is set via `AccessibleContext.getAccessibleEditableText()`. The entire existing text is replaced by first deleting the current content, then inserting the new value. See **Algorithm** section below. |
| BR-04 | If the target does not support `set_text` (i.e. `SwingUtils.supportsSetText(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support set_text. Call swing_snapshot to verify the list of actions". |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be edited. See also **architecture.md § 6** — Tool execution level. |
| BR-07 | If the target is not editable (has `AccessibleEditableText` but the `EDITABLE` state is missing from `AccessibleStateSet`), the tool returns an MCP-level error (`isError: true`) with the message "Component is not editable". This covers `JTextComponent.setEditable(false)`. |
| BR-08 | `swing_set_text` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | Setting an empty string (`text = ""`) is valid — it clears the text content. |

### Algorithm: replacing the text content

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` or `text` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-04** — `SwingUtils.supportsSetText(accessible)` — if `false`, fail with error.
4. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — if `false`, fail with "disabled" error.
5. **BR-07** — Check `AccessibleStateSet` contains `AccessibleState.EDITABLE` — if not, fail with "not editable" error.
6. Obtain `AccessibleEditableText aet = ac.getAccessibleEditableText()`.
7. Replace the entire text: `aet.setTextContents(text)`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleEditableText()` — detection (returns `AccessibleEditableText` or `null`)
- `AccessibleContext.getAccessibleStateSet()` — check for `EDITABLE` state
- `AccessibleEditableText.setTextContents(String s)` — replaces entire text content (primary method)
- `AccessibleState.EDITABLE` — state constant for editability check
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check

---

## Acceptance Criteria

- [ ] Calling `swing_set_text` with a valid ref for a `JTextField` and a `text` parameter replaces the field's text.
- [ ] Calling `swing_set_text` with a valid ref for a `JTextArea` and a multi-line `text` parameter sets the full content.
- [ ] Calling `swing_set_text` with `text = ""` clears the text content.
- [ ] Calling `swing_set_text` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_set_text` on a component that does not support `set_text` (e.g. `JLabel`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_set_text` on a disabled component returns an MCP error explaining the component is disabled.
- [ ] Calling `swing_set_text` on a non-editable text component (`setEditable(false)`) returns an MCP error saying the component is not editable.
- [ ] The ref map is cleared after a successful `swing_set_text` call (mutation tool).
- [ ] The ref map is cleared even after a failed `swing_set_text` call that passed ref lookup (e.g. disabled component).
- [ ] The tool returns an empty string on success.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingSetTextTest`
  - [ ] Setting text on a `JTextField` replaces its content.
  - [ ] Setting text on a `JTextArea` with multi-line content works correctly.
  - [ ] Setting text on a `JPasswordField` updates the password.
  - [ ] Setting an empty string clears the text field.
  - [ ] Setting text with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Setting text on a component without `set_text` support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [ ] Setting text on a disabled `JTextField` returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Setting text on a non-editable `JTextField` (`setEditable(false)`) returns an MCP error with `isError: true` saying the component is not editable.
  - [ ] The ref map is cleared after a successful `swing_set_text` call (verified by attempting to use the same ref again, which should fail).
  - [ ] The ref map is cleared after a failed `swing_set_text` call on a disabled component.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`set_text` supported):**
`JTextField`, `JPasswordField`, `JTextArea`

**Expected to fail with "Component does not support set_text" error:**
`JButton`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JSpinner`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
