# T-006: swing_set_text

**Status:** Implemented
**Date:** 2026-03-31

Writes text into text fields, text areas, and other editable text components.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. The `text` parameter is required and must be a string. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The text is set via `AccessibleEditableText.setTextContents(String)`, posted via `SwingUtilities.invokeLater()` (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). The `AccessibleEditableText` reference is captured during the validation phase on the EDT. |
| BR-04 | If the target does not support `set_text` (i.e. `SwingUtils.supportsSetText(accessible)` returns `false`), the tool returns an MCP-level error (`isError: true`) with the message "<ClassName> does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions". |
| BR-05 | All validation runs on the EDT inside `runInEDT()`. The `setTextContents()` call is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled and cannot be edited. See also **architecture.md § 6** — Tool execution level. The enabled check runs before the editable check (BR-07); a disabled non-editable field always reports "disabled". This ordering is intentional: disabled is a runtime state that takes full precedence. |
| BR-07 | If the target is not editable (has `AccessibleEditableText` but the `EDITABLE` state is missing from `AccessibleStateSet`), the tool returns an MCP-level error (`isError: true`) with the message "Component is not editable". This covers `JTextComponent.setEditable(false)`. **This check is essential:** `setTextContents()` bypasses the editable flag — it calls `setText()` which operates directly on the `Document` without checking `isEditable()`. Without BR-07, text would be silently set on non-editable fields, violating the architecture principle that the MCP server must only perform actions a real user can perform. **Note:** The snapshot already excludes `set_text` from actions for non-editable text components (see tool-002 BR-06 step 4), so well-behaved clients should never reach this check. It remains as defense-in-depth against stale snapshots or direct tool calls. |
| BR-08 | `swing_set_text` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after successful invocation. A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map — the UI state hasn't changed, so existing refs remain valid and the AI can retry without re-snapshotting. |
| BR-09 | Setting an empty string (`text = ""`) is valid — it clears the text content. |
| BR-10 | No length limit on the `text` parameter — the AI is not expected to send very large text, and Swing text components can handle arbitrary lengths. |
| BR-11 | `setTextContents()` fires `DocumentListener` events but does **not** fire `ActionEvent` (like pressing Enter in a `JTextField` would). This is the default behavior of the accessibility API — we delegate entirely to `setTextContents()` and trust the API to do the right thing. Practically, the underlying `JTextComponent.setText()` implementation fires a `removeUpdate` followed by an `insertUpdate` (two events, not one atomic replacement); consumers with `DocumentListener`s will see both. The AI filling a field will not automatically submit a form — it must follow up with `swing_click` on the submit button. This is the intended workflow. |
| BR-12 | `JPasswordField` is a supported target for `set_text`. Setting text on a password field updates the actual password (bypassing echo-char masking), which is necessary for AI-driven login-form filling. This is intentionally asymmetric with `swing_get_text`, which **refuses** to read password-role accessibles and returns a dedicated error — see **DR-011** and T-005 BR-06. The asymmetry is acceptable: the AI can write a known credential but cannot read back the real value to exfiltrate it. The snapshot reflects this asymmetry directly — password fields advertise `set_text` but not `get_text` (T-002 BR-06 step 4, DR-011). |
| BR-13 | **Return message.** On success, the tool returns a single text-content item. The echo is `Dispatched set-text on ref=<N> to "<text>" — call swing_snapshot to verify the outcome` — `<text>` double-quoted and truncated at 15 characters (≤15 → full; else first 14 + `…`), per **DR-010**. Password fields use the same echo format as regular text fields: the agent already supplied the value in the request, so echoing it back discloses nothing new and provides a strong confirmation signal that the correct value was written. |

### Algorithm: replacing the text content

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref` or `text` is missing/wrong type).
2. **BR-02** — ref lookup (fail fast if ref is invalid).
3. **BR-04** — `SwingUtils.supportsSetText(accessible)` — if `false`, fail with error.
4. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — if `false`, fail with "disabled" error.
5. **BR-07** — Check `AccessibleStateSet` contains `AccessibleState.EDITABLE` — if not, fail with "not editable" error.
6. Obtain `AccessibleEditableText aet = ac.getAccessibleEditableText()`.
7. `SwingUtilities.invokeLater(() -> aet.setTextContents(text))` — fire-and-forget.
8. Return the DR-010 echo (BR-13): `Dispatched set-text on ref=<N> to "<text>" — call swing_snapshot to verify the outcome`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleEditableText()` — detection (returns `AccessibleEditableText` or `null`)
- `AccessibleContext.getAccessibleStateSet()` — check for `EDITABLE` state
- `AccessibleEditableText.setTextContents(String s)` — replaces entire text content (primary method)
- `AccessibleState.EDITABLE` — state constant for editability check
- `SwingUtils.isEffectivelyEnabled(Accessible)` — parent-chain enabled check

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingSetTextTest`
  - [x] Setting text on a `JTextField` replaces its content.
  - [x] Setting text on a `JTextArea` with multi-line content works correctly.
  - [x] Setting text on a `JPasswordField` updates the password.
  - [x] Setting an empty string clears the text field.
  - [x] Setting text with an invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Setting text on a component without `set_text` support (e.g. `JSlider`) returns an MCP error with `isError: true`.
  - [x] Setting text on a disabled `JTextField` returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Setting text on a non-editable `JTextField` (`setEditable(false)`) returns an MCP error with `isError: true` saying the component is not editable.
  - [x] The ref map is cleared after a successful `swing_set_text` call (verified by attempting to use the same ref again, which should fail).
  - [x] The ref map is preserved after a failed `swing_set_text` call on a disabled component (refs remain valid for retry).
  - [x] Setting text on a `JTextField` fires a `DocumentListener` event.
  - [x] Each component from the component matrix is tested (dedicated test method per component).
  - [x] Success on a `JTextField` returns the DR-010 echo `Dispatched set-text on ref=<N> to "<text>" — call swing_snapshot to verify the outcome` with the text value double-quoted (BR-13).
  - [x] Success with a long text (>15 chars) returns a truncated value per DR-010 (first 14 chars + `…`).
  - [x] Success on a `JPasswordField` returns the same DR-010 echo as `JTextField`: `Dispatched set-text on ref=<N> to "<text>" — call swing_snapshot to verify the outcome` (BR-13).

- [x] `SwingSetTextScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Setting text on a `JTextField` inside `JFrame` replaces its content.
  - [x] Setting text on a `JPasswordField` inside `JFrame` updates the password.
  - [x] Setting text on a `JTextArea` inside `JFrame` sets multi-line content.
  - [x] Setting text on a disabled field inside `JFrame` returns an MCP error.
  - [x] Setting text on a non-editable field inside `JFrame` returns an MCP error.
  - [x] Setting text on a `JTextField` inside `JDialog` replaces its content.
  - [x] Setting text on a `JPasswordField` inside `JDialog` updates the password.
  - [x] Clearing a `JTextField` inside `JDialog` with empty string succeeds.
  - [x] Setting text on a `JTextField` inside `JInternalFrame` (within `JDesktopPane` inside `JFrame`) replaces its content.
  - [x] Setting text on a disabled field inside `JInternalFrame` returns an MCP error.

### Component matrix

Each matrix component from `verification.md` gets a dedicated test method.

**Succeed (`set_text` supported):** `JTextField`, `JPasswordField`, `JTextArea`.

All other matrix components return `<ClassName> does not support swing_set_text`.
