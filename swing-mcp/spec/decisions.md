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
  ref on a JTable. Interactive cell *editors* only appear in the accessibility
  tree while a cell is being actively edited, so `get_cells` cannot reach them
  as a supported discovery workflow either.
- Index-space mismatch. The snapshot speaks in rows (UC-002 SC-6:
  `- row N: Val1 | Val2 | Val3`) and selection tools (UC-014, UC-017 BR-09)
  use the same row-based index space. `get_cells` uses a flat row-major
  cell-index space (`row*cols + col`). If both were advertised, the AI would
  see `... and 45 more rows` in the snapshot and then receive
  `cell_count = 150` from `get_cell_count` on the same table — a confusing
  mismatch, and the two index spaces would not be translatable without the
  AI tracking column count out-of-band.

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

---

## DR-006 — Mutation tools use fire-and-forget dispatch

**Status:** Accepted
**Applies to:** `AbstractSwingTool`, every mutation tool
(`isMutation() == true`)

**Decision.** Mutation tools validate on the EDT inside `runInEDT()`, then
post the action via `SwingUtilities.invokeLater()` and return `null`
immediately. The HTTP thread returns the response to the client; the action
executes on a subsequent EDT turn. The client observes the outcome by
calling `swing_snapshot` or `swing_screenshot`, not by waiting on the action's
return value. **Read-only tools** are unaffected — they do their full work
inside `runInEDT()` and return synchronously.

**Why.** If a mutation's action listener opens a modal dialog, the EDT
enters a secondary event loop (`WaitDispatchSupport`). The secondary loop
still processes `invokeLater` tasks — so the MCP server continues to service
subsequent tool calls — but an `invokeAndWait` dispatch from the HTTP thread
would block indefinitely waiting for the original EDT task to return,
deadlocking the HTTP thread until the modal dialog is dismissed. Fire-and-
forget avoids that trap entirely: the HTTP thread never waits on action
completion.

Model bonus: this mirrors how a real user interacts with a Swing app — click
a button, observe the result — rather than "gluing" the client to the EDT
until paint completes.

**Trade-offs accepted.**
- `AccessibleAction.doAccessibleAction()` returns a boolean indicating
  whether the action was performed. Under fire-and-forget this return value
  is discarded. Accepted because pre-condition failures (unknown ref,
  disabled component, unsupported action) are still caught synchronously
  in the validation phase and returned as MCP errors — and a follow-up
  snapshot gives the AI richer outcome information than a single boolean
  anyway.
- The residual deadlock risk in read-only tools (which still use
  `runInEDT()` synchronously) is mitigated by an `EDT_TIMEOUT_MS` (10 s)
  watchdog on `runInEDT()` that throws with the EDT's stack trace — enough
  to diagnose unexpected blockages without masking them.

**Alternatives considered.**
- **Synchronous `invokeAndWait` dispatch.** Rejected — deadlocks on any
  mutation that opens a modal dialog (or triggers any other blocking EDT
  operation). The deadlock path is the *common* case for dialog-opening
  mutations, not an exception.
- **Hybrid: `invokeAndWait` with a timeout, fall back to fire-and-forget
  on timeout.** Rejected — adds complexity without closing the deadlock
  window; the HTTP thread still sits blocked for the timeout duration on
  every modal-dialog mutation.
- **Return `doAccessibleAction()`'s boolean via best-effort echo.**
  Rejected — the only mechanism to read it is `invokeAndWait`; with that
  off the table, the boolean is unreachable.

---

## DR-007 — Wrapper-level `toolLock` spans the whole tool call

**Status:** Accepted
**Applies to:** `MCPServer.registerTool` wrapper, every Swing tool

**Decision.** The wrapper function registered by `MCPServer.registerTool`
acquires a server-wide `ReentrantLock` (`toolLock`) at entry and holds it for
the **entire tool call** — through `runInEDT()` and, for mutations, the
subsequent `invokeLater()` dispatch. Not just around the EDT turn itself. The
lock is a `ReentrantLock`, not a `synchronized` block.

**Why.** The EDT is already serialised, so a first instinct is "just run on
the EDT, that's the lock." Under fire-and-forget dispatch (DR-006),
`runInEDT()` returns as soon as validation completes and `invokeLater(action)`
has been posted — **before the action has actually run**. Between that
`runInEDT()` return and the HTTP response being sent, a second HTTP thread
could enter, acquire its own `runInEDT()`, and replace the ref map or post a
conflicting mutation before the first tool's action has fired. The
wrapper-level lock closes that HTTP-thread gap.

**Alternatives considered.**
- **No extra lock; rely on `runInEDT()` alone.** Rejected — leaks the
  post-validation / pre-action window described above.
- **`synchronized` block on a monitor object.** Rejected for readability:
  inside a Swing lambda closure it becomes non-obvious which monitor is
  held. An explicit `ReentrantLock` makes the scope visible at the
  lock/unlock call sites.
- **Per-tool locks instead of one server-wide lock.** Rejected —
  concurrent tool calls on *different* tools would still interleave
  ref-map mutations. Swing-MCP is designed for a single AI controller;
  there is no value in permitting cross-tool concurrency.

---

## DR-008 — JDesktopPane and JDesktopIcon snapshot strategy

**Status:** Accepted
**Applies to:** UC-002 (snapshot), UC-011 (close), future minimize/restore UCs
**Decided:** 2026-04-14

**Decision bundle:**

1. **JDesktopPane** is added to `SEMANTIC_ROLES` (`DESKTOP_PANE`). It is
   always retained in the snapshot — even when empty it signals MDI
   semantics to the AI client. It receives no ref (no actions).

2. **JDesktopPane children are walked via the standard accessible-children
   API**, not `getAllFrames()`. This preserves non-frame children that
   spaghetti apps add to the desktop pane (toolbars on palette layers,
   background labels, status bars).

3. **JDesktopIcon is rendered as itself** — not resolved back to its
   JInternalFrame. The node line uses the class name `JDesktopIcon`,
   the role `desktop_icon`, and the **internal frame's title** as the
   accessible name (pulled at render time via
   `desktopIcon.getInternalFrame().getTitle()`, since `JDesktopIcon`'s
   own accessible name is `null`). Example:
   `- JDesktopIcon (desktop_icon) "Doc1" [ref=3] actions: close`

4. **JDesktopIcon's children are pruned** (hard-excluded). The button
   and label inside are L&F rendering artifacts, not semantic content.

5. **`DESKTOP_ICON` is added to `SEMANTIC_ROLES`** so the node is
   always retained regardless of other pruning heuristics.

6. **`swing_close` handles JDesktopIcon**: resolves to the internal
   frame via `getInternalFrame()` and calls `doDefaultCloseAction()`.

7. **JInternalFrame's `AccessibleValue`** (the `JLayeredPane` Z-order
   layer) is suppressed — added to `SUPPRESSED_VALUE_ROLES`. The layer
   is a programmatic concept, not a user-controlled value; exposing
   `set_value` would silently re-layer frames.

**Why — embrace JDesktopIcon rather than resolve to JInternalFrame.**

When a JInternalFrame is iconified, Swing removes it from the
component and accessibility trees entirely (`parent` becomes `null`,
`isShowing()` returns `false`, `ICONIFIED` is **not** set in the
frame's `AccessibleStateSet` — empirically verified, Java 21 OpenJDK,
2026-04-14). A `JDesktopIcon` replaces it as a child of JDesktopPane.

The initial design (pre-brainstorm) resolved JDesktopIcon back to its
JInternalFrame with a synthetic `ICONIFIED` state. This fought the
framework: the resolved frame has `isShowing() == false`, which
triggers the visibility hard-exclusion (HE-1) and the `isShowing()`
gate in `supportsClose()`; its children are non-interactable. Every
one of these problems required a carveout. Embracing JDesktopIcon
avoids all of them — the icon is a real, showing component with a
natural place in the accessibility tree. LLMs are trained on
extensive Swing documentation and code; `desktop_icon` is a
recognizable concept, not an obscure internal class.

The title annotation (`"Doc1"`) provides identity continuity between
the open frame and its icon without tree mangling. `swing_close` on
the icon is trivial — resolve internally, close the frame.

`JInternalFrame.JDesktopIcon` is a public class. Swing stopped
evolving years ago — no risk of refactoring or removal.

**Alternatives considered.**

- **Resolve JDesktopIcon → JInternalFrame with synthetic `ICONIFIED`
  state.** Rejected — fights the framework; the resolved frame is not
  showing, triggers HE-1 pruning and `supportsClose()` rejection,
  children are non-interactable. Every issue requires a special-case
  bypass.
- **Use `JDesktopPane.getAllFrames()` instead of accessible children.**
  Rejected — drops non-frame children that spaghetti apps add to the
  desktop pane.
- **Render JDesktopIcon raw (no title annotation, show L&F children).**
  Rejected — the icon's own accessible name is `null`, so it would
  render as a nameless `JComponent (desktop_icon)` with a button and
  label inside. The AI would have to inspect button text to identify
  the frame. The title annotation is a minimal, honest assist.

---

## DR-009 — Synthetic `ICONIFIED` state for JFrame

**Status:** Accepted
**Applies to:** UC-002 (snapshot states), future restore UC
**Decided:** 2026-04-14

**Decision.** `ICONIFIED` becomes a **synthetic state** in the snapshot,
derived from `frame.getExtendedState() & Frame.ICONIFIED` for JFrame.
It is emitted alongside the existing synthetic states (`DISABLED`,
`READ_ONLY`). The JDK's `AccessibleStateSet` is not used as the source
— it never contains `ICONIFIED` (see "Why" below).

This does **not** apply to JInternalFrame — iconified internal frames
are replaced by `JDesktopIcon` in the accessibility tree (DR-008), so
the AI sees them as a different component type. There is no frame node
to annotate.

A minimized JFrame remains `isShowing() == true` and its children
stay accessible, so the frame and its content still appear in the
snapshot. The `[iconified]` annotation tells the AI the window is
minimized to the OS taskbar.

`frame.setState(Frame.NORMAL)` restores a minimized JFrame
programmatically — a future `swing_restore` tool can use this.

**Why.** `AccessibleJFrame` does not override `getAccessibleStateSet()`
to include `ICONIFIED` based on `getExtendedState()`. Empirically
verified (Java 21 OpenJDK, 2026-04-14): after `setState(Frame.ICONIFIED)`,
`getState()` returns 1, `WindowStateEvent` fires, but
`getAccessibleStateSet().contains(AccessibleState.ICONIFIED)` returns
`false`. This is a JDK bug/omission. Since Swing is frozen, it will
never be fixed. Synthesizing the state follows the same pattern as
`DISABLED` (derived from `isEffectivelyEnabled()` instead of trusting
`AccessibleState.ENABLED`).

**Alternatives considered.**
- **Trust the JDK `AccessibleStateSet`.** Rejected — `ICONIFIED` is
  never present; the AI would never see it.
- **Remove `ICONIFIED` from the displayed states list entirely.**
  Rejected — `ICONIFIED` is valuable for the AI to know when a window
  is minimized, especially for a future `swing_restore` tool.
- **Synthesize `ICONIFIED` for JInternalFrame too (via `isIcon()`).**
  Not needed — iconified internal frames are represented as
  `JDesktopIcon` nodes in the tree (DR-008). No JInternalFrame node
  exists to annotate.
