# UC-005: swing_get_text

---

**As an** AI agent, **I want to** read the text content of a UI component by ref **so that** I can understand the current value of text fields and other text-bearing components without relying on the snapshot (which omits field values per UC-002 BR-03).

**Status:** Draft
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_get_text` with the `ref` parameter identifying the component whose text I want to read.
- The tool looks up the component by ref and reads its text content via the accessibility API.
- The tool returns the text as a plain string (capped at `MAX_TEXT_LENGTH` characters).

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The text is read via the accessibility API. Primary path: `AccessibleEditableText.getTextRange(0, charCount)` for efficient bulk retrieval. Fallback (when only `AccessibleText` is available): character-by-character via `AccessibleText.getAtIndex(CHARACTER, i)`. See **Algorithm** section below. |
| BR-04 | If the target does not support `get_text` (i.e. `SwingUtils.supportsGetText(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support get_text. Call swing_snapshot to verify the list of actions". |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | On `JPasswordField`, the tool returns the echo characters (the masked representation), **not** the actual password. This is the default behavior of `AccessibleText` on `JPasswordField` — `AccessibleJPasswordField` overrides `getTextRange()`, `getAtIndex()`, and all related methods to replace every character with the echo char. No special handling is needed. |
| BR-07 | `swing_get_text` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-08 | If the text content is empty (zero characters), the tool returns `MCPProtocol.Content.text("")` — an explicit empty-string text result, not `null`. A `null` return would produce an empty content array (`"content": []`), which signals "no result"; an empty string signals "the field exists and is empty". |
| BR-09 | The returned text is capped at `MAX_TEXT_LENGTH` characters (static final constant, initially **1000**). If the text is longer, only the first `MAX_TEXT_LENGTH` characters are returned, followed by `"\n... (truncated, N total characters)"`. |
| BR-10 | No enabled check is performed — reading text is always allowed, even on disabled components. |
| BR-11 | `JEditorPane` with HTML content returns the rendered text (stripped of HTML tags), not the raw HTML source. This is the default behavior of `AccessibleText` on `AccessibleJEditorPaneHTML`. Accepted as-is; revisit if the AI needs raw HTML in the future. |

### Algorithm: reading the full text content

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `SwingUtils.supportsGetText(accessible)` — if `false`, fail with error.
3. Obtain `AccessibleText at = ac.getAccessibleText()`.
4. Get the total character count: `int len = at.getCharCount()`.
5. If `len == 0`, return empty string (BR-08).
6. Compute the read length: `int readLen = Math.min(len, MAX_TEXT_LENGTH)`.
7. **Primary path:** try `ac.getAccessibleEditableText()` — if non-null, call `getTextRange(0, readLen)` which returns the text as a single `String`. Available on all `JTextComponent` subclasses (since `AccessibleJTextComponent` implements `AccessibleEditableText`).
8. **Fallback path:** if `getAccessibleEditableText()` returns `null` (read-only `AccessibleText` without editable support), iterate `at.getAtIndex(AccessibleText.CHARACTER, i)` for `i` in `[0, readLen)` and concatenate. This fallback is expected to be rare — kept as defensive code.
9. **BR-09** — If `len > MAX_TEXT_LENGTH`, append `"\n... (truncated, N total characters)"` to the result.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleText()` — detection (returns `AccessibleText` or `null`)
- `AccessibleContext.getAccessibleEditableText()` — detection and text retrieval (returns `AccessibleEditableText` or `null`)
- `AccessibleText.getCharCount()` — total character count
- `AccessibleEditableText.getTextRange(int startIndex, int endIndex)` — efficient bulk text retrieval (primary path)
- `AccessibleText.getAtIndex(int part, int index)` — character-by-character retrieval (fallback path, using `part = AccessibleText.CHARACTER`)

---

## Acceptance Criteria

- [ ] Calling `swing_get_text` with a valid ref for a `JTextField` returns the text field's content.
- [ ] Calling `swing_get_text` with a valid ref for a `JTextArea` returns the text area's full content (including newlines).
- [ ] Calling `swing_get_text` with a valid ref for a `JPasswordField` returns echo characters, not the real password.
- [ ] Calling `swing_get_text` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_get_text` on a component that does not support `get_text` (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_get_text` on an empty text field returns an empty string (not an error).
- [ ] The ref map is **not** cleared after a `swing_get_text` call (read-only tool).
- [ ] Calling `swing_get_text` on a disabled but text-readable component succeeds (no enabled check — reading is always allowed).
- [ ] Text longer than `MAX_TEXT_LENGTH` is truncated with a summary indicating total length.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingGetTextTest`
  - [ ] Reading a `JTextField` with content returns the expected text.
  - [ ] Reading a `JTextArea` with multi-line content returns the full text including newlines.
  - [ ] Reading a `JPasswordField` returns echo characters, not the actual password.
  - [ ] Reading an empty `JTextField` returns an empty string.
  - [ ] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Reading a component without text support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [ ] The ref map is preserved after a successful `swing_get_text` call (verified by calling `swing_get_text` twice with the same ref).
  - [ ] Reading a disabled `JTextField` succeeds and returns the text content.
  - [ ] Text exceeding `MAX_TEXT_LENGTH` is truncated with a `... (truncated, N total characters)` suffix.
  - [ ] Text exactly at `MAX_TEXT_LENGTH` is returned without truncation.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_text` supported):**
`JTextField`, `JPasswordField`, `JTextArea`

**Expected to fail with "Component does not support get_text" error:**
`JButton`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JSpinner`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
