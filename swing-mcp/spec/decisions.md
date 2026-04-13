# Decisions

Cross-cutting design decisions for `swing-mcp`. Each record captures **what** was
chosen, **why**, and the **alternatives** that were considered and rejected.

> **When to read this file:** when a use case or implementation note references
> `DR-NNN`, or when you're revisiting a choice that spans multiple use cases.
> If a decision is local to one use case, it lives in a sibling
> `use-case-NNN-decisions.md` file instead.
>
> **When to update:** whenever a cross-cutting choice is made, revised, or
> rejected. Keep entries short — the full narrative belongs in the session that
> produced the decision; this file is the durable summary. Status lifecycle:
> **Proposed** → **Accepted** → **Superseded** (link forward to the replacement).

---

## DR-001 — Snapshot uses role parenthetical; everywhere else uses class names

**Status:** Accepted
**Applies to:** UC-002, tool descriptions, error messages
**Decision.** In the snapshot, every node line begins with `JClass (role)` (or
`Concrete -> JClass (role)`, or `(role)` for non-`Component` accessibles). The
role parenthetical is emitted **unconditionally**, even when it looks
tautological (`JButton (push_button)`). In tool descriptions, parameter
documentation, and error messages, refer to components by Swing class name only
(`JButton`, `JTextField`, `JTable`) — never by role.

**Why.**
- **Uniform presence in the snapshot** makes non-standard roles a clean
  attention signal. If we dropped the parenthetical for tautological cases, the
  AI would have to reason "no parenthetical — is the role implied or absent?"
  on every line.
- **Class names are far more familiar to LLMs than accessibility-role terms**
  in tool descriptions and error text. Duplicating both everywhere would
  clutter without adding signal.
- **Portability bonus (not load-bearing):** the project's endgame is migrating
  Swing apps to Vaadin and driving the browser via Playwright MCP, which also
  keys off accessibility roles. Swing roles (`push_button`, `check_box`,
  `page_tab`) map LLM-instinctively to ARIA roles (`button`, `checkbox`,
  `tab`), so a "does every Swing interactable have a Vaadin counterpart"
  verification loop works without a translation layer.

**Alternatives considered.**
- **Drop role when redundant with class name.** Rejected — breaks the attention
  signal and forces per-line reasoning about parenthetical presence.
- **Roles everywhere (also in tool descriptions).** Rejected — unfamiliar
  vocabulary for LLMs; class names already carry the same information where
  the snapshot mapping is not in scope.

---

## DR-002 — `Parameters` accepts string-encoded numbers

**Status:** Accepted
**Applies to:** `Parameters.getInt`, `getIntOrNull`, `getNumber`, `getIntArray`
(and any future numeric accessor)

**Decision.** All numeric `Parameters` accessors coerce string-encoded values
(`"21"` → `21`) to the expected numeric type. Do not add strict type-checking
that would reject string-encoded numbers.

**Why.** Observed in the wild: an AI client's very first `swing_click` sent
`ref = "21"` (string) despite the tool schema declaring `"type": "integer"`.
LLMs generate JSON token-by-token and frequently wrap numbers in quotes. This
is widespread — we cannot rely on clients to respect JSON schema types.

**Alternatives considered.**
- **Strict type enforcement, return a descriptive error.** Rejected — a
  cooperative AI client would still see the error and retry, but every retry
  round-trip wastes a tool call. Silent coercion is invisible to the AI and
  has no downside for well-formed requests.

---

## DR-003 — MCP mirrors Swing semantics, including the weird ones

**Status:** Accepted
**Applies to:** all mutation tools, `SwingUtils.isEffectivelyEnabled`, UC-002
snapshot states

**Decision.** Swing-MCP is a faithful proxy for the AI client. If a Swing app
(however ridiculous) technically permits an interaction, Swing-MCP must report
it as available. Do not add "cleaner" interpretations on top of the underlying
Swing behavior.

**Operational consequences.**
- `Component.setEnabled(false)` does not propagate to children in Swing
  (JDK-4177727, closed won't-fix). Therefore `isEffectivelyEnabled()` does
  not propagate `disabled` through real-`Component` ancestors either. A button
  inside a disabled `JPanel`/`JScrollPane`/`JToolBar` is still clickable in
  Swing — and `swing_click` must accept it.
- Carveouts where Swing genuinely does propagate:
  - `JTabbedPane.setEnabledAt(i, false)` makes the tab header non-navigable
    (but the tab's children, when displayed, remain live).
  - Virtual `JTable` cells inherit the host table's disabled state.
- Check the actual Swing API, not `AccessibleStateSet`. Documented quirks:
  `AccessiblePage` does not drop `ENABLED` for disabled tabs; `JTable` cells
  do not drop `ENABLED` when the host is disabled.
- Cross-platform / L&F-dependent corner cases (e.g. `Window.setEnabled(false)`
  blocking input but painting garbage) are ignored — too unreliable to model.

**Why.** Swing apps in the wild are old and messy. Developers do strange
things — programmatically select disabled tabs, set non-propagating disabled
state on containers, put interactive widgets in unconventional places. If the
AI cannot reach a control that a human user could click, it gets stuck. The
MCP layer must reflect the *actual* clickability surface, not an idealized
one.

**Alternatives considered.**
- **Propagate `disabled` transitively to give the AI a cleaner model.**
  Rejected — would make the snapshot lie about what mutation tools actually
  accept, and would hide legitimately-clickable controls inside visually-
  disabled panels.

---

## DR-004 — JTable does not advertise `get_cells` / `get_cell_count`

**Status:** Accepted
**Applies to:** UC-002 BR-06 step 6b, UC-020, UC-021

**Decision.** `swing_get_cells` and `swing_get_cell_count` are advertised only
for `JList` and `JTree`. JTable is excluded from BR-06 step 6b. Row-level
access to JTable is via `swing_get_items` (always implicit from the
`single-selection`/`multi-selection` group labels).

**Why.**
- JTable cells are stamp-painted via `CellRendererPane` and surface as plain
  text `LABEL`s with no actions — `get_cells` can never return an actionable
  ref on a JTable.
- The snapshot speaks in rows (SC-6: `- row N: Val1 | Val2 | Val3`); cell
  tools speak in flat row-major indices. If both were advertised, the AI
  would see `... and 45 more rows` in the snapshot and then receive
  `cell_count = 150` from `get_cell_count` — a confusing mismatch.

**Follow-up.** If JTable needs bulk row access beyond the snapshot cap, add
row-dumping tools — do not resurrect cell-indexed access.

**Alternatives considered.**
- **Keep cell-based access on JTable for symmetry with JList/JTree.** Rejected
  for the reasons above (no actionable refs; row/cell unit mismatch with
  snapshot).
- **Translate cell indices to row/column on the fly.** Rejected — adds
  translation surface for a tool that, on JTable, can only return
  non-actionable labels.

---

## DR-005 — `swing_close` dispatches WM events then verifies out-of-band

**Status:** Accepted
**Applies to:** UC-011
**Decided:** 2026-04-01

**Decision bundle for `swing_close`:**

1. **DO_NOTHING_ON_CLOSE.** Dispatch `WINDOW_CLOSING` (mirroring what clicking
   the OS X button does — custom `WindowListener`s still fire), then check
   `isShowing()` synchronously in the same `invokeAndWait` block. Return `""`
   if the window closed, an informational message if still showing. No
   PostVerification polling on this path.
2. **Undecorated windows.** `supportsClose()` returns `false`, no exceptions.
3. **HIDE_ON_CLOSE.** No special-case. After dispatch, `isShowing()` returns
   `false`, PostVerification sees `isDone = true`, tool returns `""`.
4. **EXIT_ON_CLOSE stale-ref refusal.** Covered by a screen test only (no
   headless test); `EXIT_ON_CLOSE` frames are `Window` instances and cannot
   be instantiated headless.
5. **PostVerification delay schedule `{100, 200, 700}` ms** (total ~1 s).
   Front-loads short waits for the common fast-dispose path; 700 ms tail
   covers slow/animated close transitions. Revisit only on empirical
   evidence.

**Why (per item).**
- (1) Early spec said "don't dispatch for DO_NOTHING" — changed because the OS
  always dispatches the event and a custom listener might still close the
  window. Skipping PostVerification is safe because `dispatchEvent()` is
  synchronous: any synchronous listener disposal is visible immediately. Async
  disposal via `invokeLater` on a DO_NOTHING window is an unusual pattern;
  accepted tradeoff for simpler code.
- (2) Custom "X" buttons inside undecorated windows (e.g. `new JButton("X")`)
  are already discoverable via the `click` action in the snapshot. There is
  no deterministic way to detect them as "close buttons" — asking the AI to
  click them by ref is simpler than adding fragile heuristics.
- (3) From the tool's perspective, a hidden window is gone from the UI. The
  next `swing_snapshot` won't include it. No extra code needed.
- (5) Gut feel / exponential backoff. 1 s total budget is acceptable.

**Alternatives considered.**
- **Special-case HIDE_ON_CLOSE to report "hidden, not disposed".** Rejected —
  adds code for a distinction the AI doesn't need to act on.
- **Detect "custom close button" inside undecorated windows by label match
  (`"X"`, `"Close"`, `"✕"`).** Rejected as fragile and locale-dependent.
- **Fixed-interval PostVerification polling (e.g. 100 ms × 10).** Rejected —
  wastes budget on the fast path, no additional coverage of slow path.
