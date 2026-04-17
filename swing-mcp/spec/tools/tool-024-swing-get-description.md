# T-024: swing_get_description

**Status:** Draft
**Date:** 2026-04-16

Returns the full, uncapped description of a component. The snapshot caps descriptions at 120 characters (BR-10 / DR-014); when capped, the node advertises `get_description` so the AI can retrieve the complete text on demand. Needed because labels and other components can carry long semantic content (warnings in confirm dialogs, error messages, extended tooltips) that the AI must read in full to make informed decisions.

**Tool description:** "Read the full description of a UI component by ref. Returns the complete text that was truncated in the snapshot's description slot. The description is resolved from the accessibility API (accessibleDescription, or tooltip fallback). Requires a ref obtained from swing_snapshot."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The description is resolved using the **same logic** as the snapshot description slot (T-002 BR-10): (a) `AccessibleContext.getAccessibleDescription()` if non-blank after HTML cleanup, else (b) tooltip via `SwingUtils.getTooltipAsText()` if non-blank. **HTML cleanup** applies to both sources via `SwingUtils.htmlToPlainText()`. The result then passes through `SwingUtils.sanitizeForQuotedSlot()` (whitespace collapse, `"` escaping, leading/trailing trim). The 120-character cap is **not** applied. |
| BR-04 | The tool always succeeds if the ref is valid. If the component has no description (both sources resolve to null/blank after cleanup), the tool returns `MCPProtocol.Content.text("")` — an explicit empty-string text result. There is no `supportsGetDescription()` gate — every component conceptually has a description (possibly empty). |
| BR-05 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-06 | `swing_get_description` is a read-only tool: `isMutation()` returns `false` and the ref map is **not** cleared after invocation. |
| BR-07 | The returned description is capped at `MAX_DESCRIPTION_LENGTH` characters (static final constant, initially **1000**). If the description is longer, only the first `MAX_DESCRIPTION_LENGTH` characters are returned, followed by `"\n... (truncated, N total characters)"`. |
| BR-08 | No enabled check is performed — reading the description is always allowed, even on disabled components. |

### Algorithm

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-05** — run on the EDT via `runInEDT()`.
3. Resolve description using the existing snapshot description resolution logic (BR-03): `AccessibleContext.getAccessibleDescription()` → HTML cleanup → sanitize. If null/blank, fall back to `SwingUtils.getTooltipAsText(accessible)` → HTML cleanup → sanitize.
4. If the resolved description is null or blank, return `Content.text("")` (BR-04).
5. **BR-07** — if the resolved description exceeds `MAX_DESCRIPTION_LENGTH`, truncate and append the notice.
6. Return `Content.text(description)`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleDescription()` — primary description source
- `SwingUtils.getTooltipAsText(Accessible)` — tooltip fallback (resolves from `JComponent.getToolTipText()` or `JTabbedPane.getToolTipTextAt(index)` for `PAGE_TAB` nodes)
- `SwingUtils.htmlToPlainText(String)` — HTML cleanup (tag stripping, entity decoding, whitespace collapse)
- `SwingUtils.sanitizeForQuotedSlot(String)` — whitespace collapse, `"` escaping, trim

### Design notes

- **No `supportsGetDescription()` gate.** Unlike `swing_get_text` (which requires `AccessibleText` and excludes LABEL/PASSWORD roles), every component in Swing has a description slot — it is simply often empty. Gating on "has a description" would require pre-reading the description to check, for no benefit. The tool returns the description if present, or empty string if not. The AI can already see from the snapshot whether a description exists (the `"description"` slot is visible).
- **Relationship to snapshot `get_description` action.** The snapshot's BR-06 action algorithm is extended (see **Snapshot changes** below) to advertise `get_description` only when the description was capped (truncated at 120 chars). However, the tool itself accepts any valid ref — calling it on a non-capped or description-less component simply returns the short/empty description. This mirrors `swing_get_text` which succeeds on any text-supporting component regardless of whether the inline preview was truncated.
- **Action is read-only.** `get_description` is never prefixed with `!` (BR-08 of T-002) — it is always available, like `get_text` and `get_value`.
- **Ref assignment on description-only nodes.** Before this UC, nodes without any action from the BR-06 algorithm received no ref. This UC adds `get_description` as a new action that can be the sole reason a node gets a ref. This is expected to be infrequent (most components have descriptions well under 120 chars) and provides the AI access to semantically important long text that would otherwise be inaccessible.

---

## Snapshot changes

The following changes to T-002 are required:

### Action Label Algorithm (BR-06) — new step 8

Add after step 7 (`supportsClose()` → `close`):

8. **Description retrieval.** If the component's description was capped during Phase 4 (render) — i.e. the resolved description exceeded 120 characters and was truncated with `…` — add `get_description`. This action is added during or after the render phase, since capping occurs at render time. Implementation: the `SnapshotNode` tracks whether its description was capped (e.g. via a boolean flag set during `calculateSelfLine`) and includes `get_description` in the action list when true.

### Ref assignment (BR-01 / BR-07)

No rule change needed. The existing rule "only nodes that expose at least one action under the BR-06/BR-07 algorithm receive a ref" automatically covers `get_description` once it is added to the action list. A node whose sole action is `get_description` will now receive a ref.

### Action ordering

`get_description` is a read-only action. It is placed alongside other read actions (`get_text`, `get_value`, `get_selection`, `get_cell_count`, `get_cells`). No strict ordering within the read-action group is mandated.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [ ] `SwingGetDescriptionTest` (headless)
  - [ ] Reading a component with a long description (>120 chars, capped in snapshot) returns the full uncapped description.
  - [ ] Reading a component with a short description (<=120 chars, not capped) returns that short description.
  - [ ] Reading a component with no description (no `accessibleDescription`, no tooltip) returns an empty string.
  - [ ] Reading with an invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Reading a component that already has a ref from other actions (e.g. a `JButton` with a long tooltip) — `get_description` is added to the existing action list, ref assignment is unchanged.
  - [ ] The ref map is preserved after a successful `swing_get_description` call (verified by calling `swing_get_description` twice with the same ref).
  - [ ] Reading a disabled component succeeds and returns the description.
  - [ ] Description exceeding `MAX_DESCRIPTION_LENGTH` (1000 chars) is truncated with a `... (truncated, N total characters)` suffix.
  - [ ] Description exactly at `MAX_DESCRIPTION_LENGTH` is returned without truncation.
  - [ ] HTML tooltip description is cleaned (tags stripped, entities decoded) before return.
  - [ ] Tooltip fallback: component with no `accessibleDescription` but a long tooltip returns the full cleaned tooltip text.

- [ ] `SwingGetDescriptionSnapshotTest` (headless)
  - [ ] A component with a description >120 chars shows `get_description` in the snapshot action list and the description is capped with `...` (U+2026).
  - [ ] A component with a description <=120 chars does **not** show `get_description` in the snapshot action list.
  - [ ] A `JLabel` with a long tooltip (>120 chars) and no other actions gets a ref solely from `get_description`.
  - [ ] A `JButton` with a long tooltip (>120 chars) has `get_description` added alongside its existing actions (`click`, etc.).
  - [ ] A `JPanel` (normally refless) with a long `accessibleDescription` gets a ref from `get_description` alone.

- [ ] `SwingGetDescriptionScreenTest` (`testSwing` — requires display)
  - [ ] Reading description of a `JLabel` with a long tooltip inside a `JFrame` returns the full tooltip text.
  - [ ] Reading description of a `JButton` with a long `accessibleDescription` inside a `JDialog` returns the full description.
