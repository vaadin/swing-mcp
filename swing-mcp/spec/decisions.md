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

For the recurring "unsupported action" error the canonical template is
`<ClassName> does not support <action>. Call swing_snapshot or swing_get_cells to verify the list of actions`
(e.g. `JFrame does not support click. …`). `<ClassName>` is the qualifying
Swing ancestor per BR-11 — standard widget for a custom subclass
(`SearchField extends JTextField` → `JTextField`), and `Component` as a
graceful fallback for non-`Component` accessibles (`JTabbedPane.Page`,
`JList` / `JTree` children) so the sentence still reads naturally. Produced
via `ComponentClassResolver.resolveClassName(accessible)`.

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

**Status:** Superseded by DR-006 on 2026-04-15
**Applies to:** UC-011
**Decided:** 2026-04-01
**Superseded:** 2026-04-15

Originally specified an `invokeAndWait` + `PostVerification` polling model
(delay schedule `{100, 200, 700}` ms) for `swing_close` to detect whether the
window had actually closed and return either `""` or an informational message
to the client. Replaced when DR-006 made fire-and-forget the **universal**
dispatch model for mutations: `swing_close` now posts `WINDOW_CLOSING` (or
`doDefaultCloseAction()` for JInternalFrame) via `SwingUtilities.invokeLater()`
and returns `null` immediately, with no synchronous outcome check. The client
verifies outcome by calling `swing_snapshot` (UC-011 BR-03/BR-04). The
HIDE_ON_CLOSE and DO_NOTHING_ON_CLOSE branches collapse into the same
fire-and-forget path; the snapshot is the source of truth.

**Carryovers still in force** (now documented directly in UC-011, not here):
- Undecorated windows are refused via `supportsClose() == false` (UC-011 BR-05).
- `EXIT_ON_CLOSE` frames are refused via `supportsClose() == false`
  (UC-011 BR-09); stale-ref path is covered by a screen test only because
  `EXIT_ON_CLOSE` frames are `Window` instances and can't be instantiated
  headless.

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
   background labels, status bars). **Supplemental walk for iconified
   frames:** after the accessibility walk, `JDesktopPane.getAllFrames()`
   is checked for any iconified frame (`isIcon() == true`) whose
   `getDesktopIcon()` was not already discovered. Missing icons are
   added to the tree. This is necessary because macOS Aqua L&F nests
   `JDesktopIcon` inside a non-accessible `AquaInternalFramePaneUI$Dock`
   wrapper — `getAccessibleChildrenCount()` returns 0 for the desktop
   pane after iconification. The supplement is harmless on other L&Fs
   (Metal, Windows) where the icon is already an accessible child.

3. **JDesktopIcon is rendered as itself** — not resolved back to its
   JInternalFrame. The node line uses the class name `JDesktopIcon`,
   the role `desktop_icon`, and the name from
   `SwingUtils.getEffectiveAccessibleName()` — a three-step fallback:
   (1) icon's own accessible name, (2) underlying frame's accessible
   name, (3) frame title. In practice this collapses to the frame's
   title, but respects explicit overrides. `JInternalFrame` itself
   also uses `getEffectiveAccessibleName()` (accessible name → title).
   Example: `- JDesktopIcon (desktop_icon) "Doc1" [ref=3] actions: close`.
   **ComponentClassResolver carveout:** `JInternalFrame$JDesktopIcon`
   is a JDK-internal nested class that would normally be stripped to
   `JComponent` by `isJdkInternalNested()`. A narrow carveout preserves
   it so the snapshot renders `JDesktopIcon`, not `JComponent`.

4. **JDesktopIcon's children are hard-excluded.** The button and label
   inside are L&F rendering artifacts, not semantic content.
   Implementation: the snapshot tree-builder skips child-walking for
   JDesktopIcon nodes entirely (children are never constructed as
   `SnapshotNode`s), rather than constructing then dropping them.

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
- **Use `JDesktopPane.getAllFrames()` as the primary walk instead of
  accessible children.** Rejected as the sole strategy — drops non-frame
  children that spaghetti apps add to the desktop pane. Adopted as a
  **supplement** for iconified frames only (see item 2 above).
- **Render JDesktopIcon raw (no title annotation, show L&F children).**
  Rejected — the icon's own accessible name is `null`, so it would
  render as a nameless `JComponent (desktop_icon)` with a button and
  label inside. The AI would have to inspect button text to identify
  the frame. The title annotation is a minimal, honest assist.

---

## DR-009 — Synthetic `ICONIFIED` state for JFrame

**Status:** Accepted
**Applies to:** UC-002 (snapshot states), UC-023 (swing_restore)
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

`frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED)`
restores a minimized JFrame programmatically (UC-023). This clears only
the `ICONIFIED` bit and preserves other extended-state bits (e.g.
`MAXIMIZED_BOTH`), so an iconified-maximized frame is restored to
maximized rather than normal. `setState(Frame.NORMAL)` is not used
because it clears all extended-state bits.

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

---

## DR-010 — Mutation tools echo `Posted <action> on ref=N`

**Status:** Accepted
**Applies to:** every mutation tool (`isMutation() == true`); the dispatch
wrapper in `AbstractSwingTool` / `MCPServer.registerTool`;
`architecture.md § Fire-and-Forget Mutation Dispatch`
**Decided:** 2026-04-15

**Decision.** Mutation tools no longer return `null` (which the MCP layer
renders as `{"content":[]}`). Each successful mutation returns a single
text-content item of the form:

```
Posted <action> on ref=<N>[ to <value>]
```

Examples:

- `Posted click on ref=4`
- `Posted set-text on ref=4 to "hello"`
- `Posted close on ref=4`
- `Posted iconify on ref=4` / `Posted restore on ref=4`
- `Posted clear-selection on ref=7`
- `Posted set-value on ref=4 to 42`
- `Posted set-selection on ref=7 to [2, 5, 8]`
- `Posted select-all on ref=7`
- `Posted increment on ref=4` / `Posted decrement on ref=4`
- `Posted toggle-expand on ref=4` / `Posted toggle-popup on ref=4`

**Format conventions.**

- **Verb:** always `Posted`. The tool dispatches on the EDT and returns
  before the action runs (DR-006). The verb describes what the *tool* did,
  not what the *UI became*.
- **Action name:** kebab-case, derived from the tool name minus the
  `swing_` prefix (`swing_set_text` → `set-text`,
  `swing_clear_selection` → `clear-selection`). 1:1 mapping makes echoes
  greppable in logs and predictable for the LLM.
- **`ref=N`:** matches the parameter name. Not `#N`, not `[N]`.
- **Value rendering** (when the tool takes a value parameter):
  - **Strings:** double-quoted; truncated at 15 characters
    (≤15 → full; else first 14 + `…`). No length suffix — the LLM sent
    the input. Example: `"Lorem ipsum d…"`.
  - **Numbers:** bare. `42`, not `"42"`.
  - **Arrays:** JSON-style `[2, 5, 8]`; truncated the same way
    (≤15 chars total → full; else `[2, 5, 8, …]`).
- **Errors are unchanged.** Failures still return through the MCP error
  channel (`isError: true`) with their existing messages. The echo is
  the *success* shape only. Rule: success = echo, failure = error,
  nothing in between.

**Why.**

- **Empty content is a missed affordance.** Most LLMs treat "some
  content" as a stronger positive signal than "no content", and the
  empty path tempts redundant `swing_snapshot` calls just to confirm
  the tool ran.
- **`Posted` is honest about the contract.** Under DR-006 no mutation
  runs synchronously from the tool's perspective — `execute()` posts
  via `invokeLater()` and returns before the action runs. Past-tense
  verbs like `Clicked ref=4` or `Closed ref=4` would imply the action
  completed and the UI changed; both implications overstate what the
  MCP knows. Listeners can revert any setter and veto any event, so
  even *successful* dispatch tells us nothing definitive about UI
  state. The echo must reflect what the MCP knows (dispatch occurred),
  not what it cannot know without auditing the host app.
- **Consistency outranks brevity.** A uniform shape across all
  mutations means missing or differently-shaped echoes look
  anomalous, prompting investigation. Mixed shapes (`Clicked ref=4`
  for clicks but `Posted close on ref=4` for closes) would create a
  cliff for both LLMs and humans reading logs.
- **Cost is trivial.** ~5–10 extra tokens per mutation call.

**Implementation note.** The echo is generated by the dispatch wrapper,
not by each tool's `execute()`. The wrapper has access to the tool name
(→ action) and the call's `Parameters` (→ ref + value), and composes the
echo cross-cuttingly. Individual UCs do not need to specify their echo
string; they inherit it from this DR.

**Dispatch-only contract surfacing.** The dispatch-only contract is
communicated to clients via the server-level `INSTRUCTIONS` blurb in
`MCPServer` (sent at MCP `initialize` time), not via per-tool
descriptions. The blurb explicitly names the echo format and the
dispatch-vs-outcome distinction (listeners can veto, revert, or open a
dialog), then directs clients to `swing_snapshot` for outcome
verification. **Per-tool descriptions intentionally do not repeat this**
— `INSTRUCTIONS` already covers cross-cutting contracts (it also hosts
the parallel-call rule and the ref-staleness rule), and duplicating the
verify-via-snapshot line across 13 mutation tools would be straight
duplication, not defense in depth. If a future MCP client is observed
to drop or de-emphasize `INSTRUCTIONS`, revisit this and consider a
short per-tool suffix as a backstop.

**Trade-offs accepted.**

- The echo names *what the tool did*, not *what happened in the app*.
  Clients must call `swing_snapshot` to verify outcome — same as today
  under DR-006, but now made explicit by the verb choice and the tool
  description language.
- Marginally more verbose than `Clicked ref=4` would be. Chosen for
  honesty + uniformity over brevity.

**Alternatives considered.**

- **Empty content (status quo).** Rejected — see "Why" above.
- **`OK` for every mutation.** Rejected — generic, indistinguishable
  from noise, doesn't echo input parameters, useless in logs.
- **Past-tense action verb (`Clicked ref=4`, `Closed ref=4`).**
  Rejected — under DR-006's universal fire-and-forget every mutation
  is `invokeLater`'d, so no past-tense verb accurately describes the
  tool's actual work at return time. Past tense would also imply
  outcome verification the MCP cannot provide.
- **Two echo shapes — past-tense for direct-API mutations (e.g.
  `setText`), `Posted X` for event dispatches (e.g. `WINDOW_CLOSING`).**
  Rejected — under DR-006 all mutations are equally "posted, not
  executed" by the time `execute()` returns. The category split has
  no referent in the implementation.
- **Bundle a snapshot into every mutation response.** Rejected —
  snapshots are expensive, the LLM may not always want one, and
  forcing them couples mutation response size to tree size. Snapshot
  remains a separate tool the client invokes when it needs to verify.
- **Structured JSON content (e.g. `{"action":"click","ref":4}`).**
  Rejected — plain text is cheaper to tokenize, easier for LLMs to
  parse, and trivially human-readable in logs. No structure earns
  its keep here.
- **Differentiate no-op echoes (e.g. "ref=4 already iconified").**
  Rejected — no-op mutations are already routed to the error channel
  with a specific message (per existing tool behavior, e.g.
  `swing_iconify` on an already-iconified frame). The success path is
  reserved for "we dispatched the action".

---

## DR-011 — Password fields are not readable

**Status:** Accepted
**Applies to:** UC-002 (snapshot action list), UC-005 (`swing_get_text`),
UC-006 (`swing_set_text` BR-12, BR-13 echo asymmetry)
**Decided:** 2026-04-15

**Decision.** Any accessible whose role is `AccessibleRole.PASSWORD_TEXT`
(canonically `JPasswordField` and its subclasses, plus any third-party
component that adopts the same role) is treated as non-readable by the
MCP layer:

1. The snapshot's action-list builder (UC-002 BR-06 step 4) **never emits
   `get_text`** for a password-role accessible. The `set_text` branch is
   unaffected — password fields still advertise `set_text` (editable) or
   `!set_text` (non-editable, per BR-08).
2. `swing_get_text` called on a password-role accessible returns an
   MCP-level error (`isError: true`) with the dedicated message:
   `"JPasswordField content is not readable. Use swing_set_text if you
   need to write a known value."`. This error is distinct from the
   generic `<ClassName> does not support get_text` (per DR-001) so the AI
   can learn the rule rather than assume the capability is simply absent.

**Why.** UC-006 BR-12 already declares the asymmetric posture: the AI may
*write* a known credential into a password field (necessary for
AI-driven login-form filling) but may not *read back* the real value
(would turn the MCP into a credential exfiltration channel). The default
`AccessibleJPasswordField` behavior — returning echo characters
(`••••••`) — undermines that posture on two fronts:

- The AI receives a literal string of echo chars and can reasonably read
  it as "the field contains six bullet characters", giving misleading
  signal about field contents.
- The length of the echo string equals the length of the real password,
  leaking a non-trivial piece of information about the credential.

Aligning the snapshot (no `get_text` advertised) with the tool
(dedicated error) closes the loophole. The AI learns the rule from the
snapshot directly: `set_text` present, `get_text` absent.

**Role-based gate, not class-based.** The rule is keyed on
`AccessibleRole.PASSWORD_TEXT`, not on `instanceof JPasswordField`. This
is free coverage for third-party or custom subclasses that adopt the
password role (e.g. a company-internal `SecretField` whose
`AccessibleContext` returns `PASSWORD_TEXT`). Consistent with DR-003's
"mirror Swing semantics exactly" principle — the role is Swing's own
marker for "this content is secret", and we honor it.

**Pathological case — non-editable password field.** A `JPasswordField`
with `setEditable(false)` gets `actions: !set_text` — a ref that
advertises only a currently-unavailable mutation. This is spaghetti-app
territory (who ships a read-only password field?), but we support it:
the node retains its ref and remains visible in the snapshot so the AI
can see that a password field exists and is currently not writable. No
special-casing in the ref-assignment gate.

**Error message uses class name, gate uses role.** Per DR-001,
user-facing strings (tool descriptions, error messages) name components
by Swing class. The error says `JPasswordField` because that's the
canonical case; for a rare third-party password-role component the
message is slightly inaccurate but still conveys the rule. No dynamic
class-name lookup needed.

**Alternatives considered.**

- **Document-only — update `swing_get_text`'s tool description to
  mention echo-char behavior, leave snapshot and return value
  unchanged.** Rejected — the snapshot would still advertise `get_text`
  for a password field, luring the AI into a call that returns
  `••••••`, which it then has to interpret. Cheaper short-term but
  leaves two active footguns (misleading value, leaked length).
- **Drop `set_text` on password fields as well.** Rejected — UC-006
  BR-12 explicitly protects the write path for login-form filling.
  Dropping it would kill a primary use case in exchange for symmetry
  that provides no security benefit (writing a known value is not
  exfiltration).
- **Class-based gate (`instanceof JPasswordField`) instead of
  role-based.** Rejected — misses custom subclasses that adopt
  `PASSWORD_TEXT` without extending `JPasswordField`. The role is
  the semantic marker; class is an implementation detail.
- **Return an empty string or `null` on `swing_get_text` instead of
  an error.** Rejected — silent degradation gives the AI no signal
  that the call was refused, and `""` is indistinguishable from an
  empty field. An explicit error teaches the rule and is audit-friendly
  in logs.

---

## DR-012 — `JMenu` does not expose `click`

**Status:** Accepted
**Applies to:** UC-002 (snapshot action list), UC-004 (`swing_click`),
`SwingUtils.supportsClick`
**Decided:** 2026-04-15

**Decision.** `JMenu` is treated as a **structural container**, not an
interactive target:

1. `SwingUtils.supportsClick(JMenu)` returns `null`. The snapshot
   therefore never emits `click` on a `JMenu`, and the node receives no
   ref (unless some other action applies, which at time of writing never
   does).
2. `swing_click` invoked on a `JMenu` ref (possible only from a stale
   ref obtained before this change, or from a race) returns the generic
   `"JMenu does not support click"` error (per DR-001) — no dedicated
   message, since a well-behaved client never sees this.
3. When the menu's popup is open (any origin — the user tabbed in, a
   keyboard accelerator fired, the app opened it programmatically), the
   resulting `JPopupMenu` node is pruned from the snapshot whenever its
   `getInvoker()` is a `JMenu`. See UC-002 BR-06 prune rule HE-5.
   Context popups (right-click menus whose invoker is a `JButton`,
   `JTable`, etc.) are unaffected and render normally.

**Why.** A human clicking a `JMenu` title opens its popup so the items
become visible. The LLM already has those items in the snapshot —
`JMenu`'s accessible children are the `JMenuItem` instances that would
appear in the popup, rendered under the `JMenu` whether the popup is
open or not. So for the LLM, clicking the menu is a no-op that costs:

- **Context.** An open popup duplicates every item in the snapshot
  (once under the `JMenu`, once under the `JPopupMenu` — same
  `JMenuItem` instances reachable via two paths). Menus with many items
  blow up the snapshot and force the LLM to pick between identical refs.
- **Round-trips.** `click menu → snapshot → click item` is strictly
  worse than `click item`. The mutation clears the ref map, so the
  client also has to re-snapshot just to get the item's ref back.
- **Semantic clarity.** `doClick()` (what `AccessibleAction.CLICK`
  invokes) has asymmetric toggle behaviour on a `JMenu`: it opens the
  popup on the first call but does not close it on subsequent calls.
  Exposing `click` and having it stop working mid-sequence is a worse
  API than not exposing it at all.

The items themselves (`JMenuItem`, `JCheckBoxMenuItem`,
`JRadioButtonMenuItem`) retain `click` as usual — they are the real
interactive targets.

**Tension with DR-003 ("mirror Swing semantics exactly").** DR-003 says
we must expose what Swing allows. Humans can click `JMenu` titles, so
technically this is a carveout. Justification: the user-visible *effect*
of clicking a menu title (revealing its items) is already delivered by
the snapshot. Mirroring the human click path would give the LLM strictly
less capable behaviour than it already has. DR-003's intent is to keep
the LLM at least as capable as a human; this carveout preserves that
intent. `JMenu` is closer to an HTML `<details>` element (UI plumbing
for progressive disclosure) than to a button.

**Dynamically populated menus — deferred.** Some apps add items only in
a `PopupMenuListener.popupMenuWillBecomeVisible` handler; with `click`
banned, those items are invisible to the LLM until something else opens
the popup. This is rare enough to defer. The right future answer is
`toggle_popup` on `JMenu` (explicit open/close intent) as a separate
UC — not `click` (toggle with asymmetric semantics). Revisit if the
pattern shows up in real apps.

**Alternatives considered.**

- **Keep `click`, make it toggle via `MenuSelectionManager`.** Rejected
  — fixes the asymmetric-close bug but leaves the duplication,
  context-cost, and round-trip issues in place. The better question is
  whether the action should exist at all.
- **Keep `click` for open, ban for close.** Rejected — one-shot
  "click opens, can't close" is a worse API than no click at all, and
  the MenuSelectionManager close path is trivial to include anyway.
- **Expose `toggle_popup` on `JMenu` instead.** Rejected for the
  common case (items are already visible), deferred for the dynamic-
  menu corner case.

---

## DR-013 — Snapshot includes inline text/value previews

**Status:** Accepted
**Applies to:** UC-002 (BR-03 line format), UC-005 (`swing_get_text`),
UC-012 (`swing_get_value`)
**Decided:** 2026-04-15
**Supersedes:** the "field values are not shown" clause of UC-002 BR-03
as originally written (pre-amendment). BR-03's own "Revisit if the AI
needs field values in future" was the designed escalation point; this
DR takes it.

**Decision.** The snapshot line format gains an inline value annotation
for components that carry user-facing content via `AccessibleText` or
`AccessibleValue`. Exactly one of two labels may appear on a line,
never both, selected by the same capability gate that drives the
action list (BR-06 steps 4 and 5):

- **`text="..."`** — emitted when `SwingUtils.supportsGetText(accessible)`
  returns `true` (i.e. the node would advertise `get_text` or
  `set_text`). Content source: `SwingUtils.readText(accessible)` — the
  same helper `swing_get_text` calls, so the two can never disagree.
  Null content is normalised to `""` (matches what the user sees for a
  JTextField whose document is empty). Newlines and runs of whitespace
  are **collapsed to a single space** before truncation (consistent with
  BR-10's HTML cleanup). The result is then capped per the DR-010
  string-truncation convention: `≤15 chars → full; else first 14 + …`.
  Wrapped in double quotes.
- **`value=<number>`** — emitted when
  `SwingUtils.supportsGetValue(accessible)` returns `true`. Content
  source: `SwingUtils.readValue(accessible)` (i.e. `current` from
  `AccessibleValue.getCurrentAccessibleValue()`). Serialised bare per
  DR-010's number convention (whole numbers as integers, fractional as
  floats). Not truncated.
  - **Progress bar exception:** when the role is `PROGRESS_BAR` **and**
    `getMaximumAccessibleValue()` is non-null, render as
    `value=<current>/<max>` instead — the denominator is the whole
    point of looking at a progress bar.

**Placement on the line.** Between the states bracket and the `actions:`
section, in the "additional component-specific info" slot (same slot
used for `columns: […]` on JTable). Order when both JTable `columns:`
and a value annotation are present: `columns:` first, then the value —
though in practice no component triggers both (JTable itself has
neither text nor value). Examples:

```
- JTextField (text) "Name" [ref=3, editable] text="admin" actions: get_text, set_text
- JTextField (text) "Notes" [ref=4, editable] text="Lorem ipsum d…" actions: get_text, set_text
- JSlider (slider) "Volume" [ref=5, horizontal] value=42 actions: increment, decrement, get_value, set_value
- JSpinner (spin_box) "Quantity" [ref=6] value=10 actions: increment, decrement, get_value, set_value
- JProgressBar (progress_bar) "Loading" [horizontal] value=37/100 actions: get_value
- JTextField (text) "Empty" [ref=7, editable] text="" actions: get_text, set_text
```

**Password carveout is free.** `SwingUtils.supportsGetText()` already
returns `false` for `AccessibleRole.PASSWORD_TEXT` (DR-011). Keying
inline `text="..."` off the same gate means password fields never emit
an inline preview — no new code, no new spec carveout. A password
field retains its `set_text` action (or `!set_text` when non-editable)
and simply has no value annotation.

**Shared read helpers.** Implementation factors the read into
`SwingUtils.readText(Accessible) → String` (null → `""`) and
`SwingUtils.readValue(Accessible) → Number` (pre-gated by
`supportsGetValue()`). Both `SwingSnapshotTool` (for the inline
preview) and `SwingGetTextTool` / `SwingGetValueTool` (for the full
response) call these helpers. The snapshot truncates; the tools
return whole. This structurally guarantees that a `text="abc"` preview
implies `swing_get_text` on the same ref will begin with `abc…` — they
are literally the same read.

**Defensive read.** The inline preview is wrapped in try/catch. If a
custom widget's `AccessibleText`/`AccessibleValue` throws (document
lock contention, misbehaving custom impl, etc.), the single
annotation is **omitted** — the rest of the snapshot still renders.
A JUL `FINE` log line records the failure for later diagnosis. The
snapshot must always produce output; one bad widget cannot take down
the whole tree.

**Multi-capability components.** In standard Swing, no component
exposes both `AccessibleText` with content and `AccessibleValue` — the
families are disjoint (text vs. slider/spinner/progress/scroll/split).
A hypothetical custom widget that gates true on both is permitted to
emit both annotations (`text="..." value=42`); no engineered tie-break
is needed. DR-003 ("mirror Swing semantics exactly") covers this — if
the framework reports both, we report both.

**Why.**

- **Chatter reduction.** Observed in the field: AI clients currently
  round-trip `swing_get_text` / `swing_get_value` for every text
  field, slider, and spinner on a form to learn current contents.
  For a 10-field form that is 10 extra MCP calls per verification
  cycle. The inline preview collapses that into one snapshot call.
- **Auto-discovery.** An optional flag (considered, rejected — see
  alternatives) requires the AI to *choose* verbose mode; inline
  preview means the AI *sees* the current value the moment it reads
  the snapshot, without deciding anything.
- **Bounded cost.** A 15-char string cap matches DR-010's existing
  truncation convention. Worst-case overhead for a 50-field form is
  ~750 chars (\~200 tokens) — well within the noise floor of a
  normal snapshot.
- **Playwright alignment.** The original BR-03 rationale cited
  "consistency with Playwright MCP's approach." In fact Playwright's
  accessibility snapshot does surface `value` for textboxes, sliders,
  and progressbars. This DR moves *closer* to Playwright, not away.
- **Matches the round-trip tool.** `text="..."` → `swing_get_text`
  returns the full untruncated string. `value=42` → `swing_get_value`
  returns `{current, min, max}`. The label is its own documentation.

**Trade-offs accepted.**

- Snapshot size now correlates loosely with form content, not just
  form structure. Bounded by the 15-char cap; empirically negligible
  for realistic UIs.
- Multi-line JTextArea / JEditorPane previews lose line structure via
  whitespace collapsing. Acceptable — at 15 chars the structure is
  rarely meaningful, and the `multi_line` state + `get_text` action
  both signal to the AI that the full content is available via
  round-trip.
- For any component (text *or* value) that holds sensitive-but-
  non-password content (draft email, API-key field, partially-typed
  credential, etc.), the snapshot now surfaces up to 14 chars of it.
  Accepted: (a) the AI could already call `swing_get_text` and obtain
  the full value; the inline preview does not widen the exfiltration
  surface, only reduces its cost; (b) the role-based password gate
  (DR-011) covers the one case Swing itself marks as secret; custom
  secret fields that fail to adopt `PASSWORD_TEXT` were already
  leaking via `swing_get_text` and are out of scope here.

**Alternatives considered.**

- **Status quo — no inline value.** Rejected — see "chatter
  reduction" above. BR-03 itself had "Revisit if the AI needs field
  values in future" as a deliberate escalation clause; this DR is
  the escalation.
- **Boolean flag on `swing_snapshot` (e.g. `include_values=true`).**
  Rejected — the flag's hidden cost is per-call decision overhead.
  Either the AI always sets it (equivalent to default-on with no
  flag) or never sets it (equivalent to status quo). Flags earn
  their keep when both modes have real users — here the "user" is
  one AI controller with one preference per session. Bureaucratic
  without benefit.
- **Bulk `get_text` / `get_value` accepting an array of refs.**
  Rejected **as a substitute**, kept open as a future follow-up.
  Fixes chattiness (1 batched call vs N) but not *discovery*: the
  AI still has to decide "should I batch-fetch?" and heuristics
  leak. Inline preview is auto-discovery. A bulk getter may still
  be worth adding later for slider-heavy control panels where the
  full numeric value (not truncated preview) is worth the extra
  round-trip.
- **`value=<current> [<min>..<max>]` everywhere.** Rejected — range
  is rarely actionable-at-a-glance for sliders/spinners (a reasonable
  range is usually implied by the widget's purpose) and the AI can
  always call `swing_get_value` when bounds matter. Kept the
  denominator only for `JProgressBar`, where progress toward a
  maximum *is* the meaning of the widget.
- **Inline preview for `JCheckBox` / `JRadioButton` / `JComboBox`
  selection.** Rejected — `[checked]`/`[selected]` state already
  carries the boolean signal for checkboxes/radios; combo-box
  selected-item exposure is a separate design question (the
  selection-display story across JList/JTable/JComboBox is better
  addressed as a coordinated follow-up than as part of this DR).
- **Show raw newlines / JSON-escape `\n`, `\t`.** Rejected —
  collapsing to a single space matches BR-10's established text-
  cleanup convention and keeps the snapshot line-oriented. At
  15 chars the fidelity cost is negligible; the full content is
  always reachable via `swing_get_text`.

---

## DR-014 — Quoted-slot rendering: name uncapped, description capped, always sanitized

**Status:** Accepted
**Applies to:** UC-002 (BR-03 line format, BR-10 description, BR-12 inline
preview), `SnapshotNode.calculateSelfLine`, `SnapshotNode.computeInlinePreview`,
`SwingUtils.sanitizeForQuotedSlot`
**Decided:** 2026-04-15

**Decision.** Every quoted slot in a snapshot line — `"name"`,
`"description"`, and the BR-12 `text="..."` preview — passes through a
shared sanitizer before emission, and each slot's length policy is
locked in independently:

1. **Sanitization (applied unconditionally to every quoted slot).**
   The shared helper `SwingUtils.sanitizeForQuotedSlot(String)`:
   (a) replaces any run of whitespace characters with a single ASCII
   space — covers Java's ASCII `\s` (`\n`, `\r`, `\t`, vertical tab,
   form feed) plus the Unicode line separators U+0085, U+2028, U+2029
   (added explicitly because Java's default `\s` is ASCII-only);
   (b) escapes any embedded `"` as `\"`; (c) strips leading/trailing
   whitespace; (d) returns `null` if the result is empty or blank, so
   callers need only the existing null-check. Input `null` → output
   `null`. Backslashes are **not** escaped — a literal `\` passes
   through. **The sanitizer is not idempotent** — because `\` is not
   escaped, re-running the sanitizer on already-sanitized output would
   double-escape embedded quotes (`say "hi"` → `say \"hi\"` → `say
   \\"hi\\"`). The snapshot render path calls the sanitizer exactly
   once per slot, so double-escape is prevented by call-site
   discipline, not by the helper.

2. **Name slot — uncapped.** The accessible name is the component's
   identity. It answers "which button is this?" — truncating it
   destroys the AI's primary disambiguator. Empirically, names are
   short in realistic apps (`"Save"`, `"Cancel"`, `"Invoice #4532"`);
   when they are long (a multi-sentence `JLabel` used as a warning in
   a dialog), the length itself carries the signal and must be read
   in full. Sanitize and emit verbatim.

3. **Description slot — capped at 120 characters per BR-10.** No
   change from the existing BR-10 behaviour; DR-014 formalises it as a
   deliberate asymmetry rather than an incidental implementation
   choice. Descriptions are supplementary context (tooltips,
   `accessibleDescription`); bounding them keeps a
   pathological-tooltip component from bloating the snapshot. Cap
   order: sanitize first, then apply `capDescription` (truncate + `…`).

4. **Inline `text="..."` preview — capped at 15 chars per DR-013.**
   No change to the existing BR-12 cap. DR-014 adds the missing
   quote-escape step: previously the preview collapsed whitespace but
   did not escape `"`, so a `JTextField` whose document contained
   `say "hi"` rendered as `text="say "hi""` — unparseable. The
   sanitizer closes that gap.

**Why the asymmetry between name (uncapped) and description (capped).**
Different roles, different policies:

- **Name is identity** — a first-class part of "what component is
  this?", equivalent to the class-prefix (BR-11). Identity must be
  readable in full for the AI to take action against it. Truncating
  a 500-char warning `JLabel` just to fit a bound defeats the whole
  purpose of surfacing the label.
- **Description is advice** — auxiliary context the AI *may* read to
  learn what the component does, but never needs to act on. Bounded
  size is cheap insurance against pathological tooltips, and the AI
  loses nothing load-bearing if the last N chars of a multi-paragraph
  tooltip get replaced with `…`.

Spec readers may be tempted to unify the two under one policy; this
DR exists so that choice is documented rather than inferred.

**Why sanitize rather than fail-on-bad-input.** The snapshot's tree
structure is carried by indentation and line breaks. A single
`JLabel("Line 1\nLine 2")` or a list item populated from a backend
string containing `\n` would produce a multi-line snapshot-node
line, silently corrupting the hierarchy downstream readers
(including LLMs) rely on. Since every text slot reads from
user-controlled data (`setAccessibleName`, `setTitle`, cell
renderers, custom `AccessibleContext` implementations), defensive
sanitization is the only way to preserve the one-line-per-node
invariant. The escape for `"` follows the same logic — the slot is
quoted in the line format, so embedded quotes must not terminate the
string visually.

**Long-description retrieval — implemented (UC-024).** When a
description exceeds 120 chars and is capped with `…`, the node
advertises `get_description` in the snapshot action list and
receives a ref (even if `get_description` is its sole action).
`swing_get_description` returns the sanitized but uncapped string
(up to 1000 chars). See UC-024 for full specification.

**Interaction with other rules.** The sanitizer runs *before* the
DR-010 15-char truncation convention and *before* the 120-char
description cap. Truncation is always applied to sanitized content,
so the `…` suffix always follows a printable prefix (never a
trailing escaped quote, never a trailing collapsed-whitespace
artefact). `htmlToPlainText` remains responsible for HTML-specific
cleanup (tag stripping, entity decoding); its output is sanitized
once for the description slot. Because `htmlToPlainText` preserves
plain text verbatim, sanitization is still needed on top of it — a
non-HTML description containing `\n` or `"` passes through
`htmlToPlainText` unchanged and needs the sanitizer to normalise it.

**Alternatives considered.**

- **Uncap both name and description.** Rejected — pathological
  tooltips (accidental 10k-char strings, localisation bugs,
  HTML-heavy help text from older Swing apps) can bloat the
  snapshot without carrying load-bearing signal. Name gets the
  full-render pass because it's identity; description does not.
- **Cap both at the same length.** Rejected — truncating a long
  warning `JLabel` hides the warning the developer wrote. Name-as-ID
  argument above.
- **Escape `\` to `\\` alongside `"` to `\"` (full JSON escaping).**
  Rejected — the snapshot is a text format consumed primarily by
  LLMs, not a JSON-parser-driven contract. `\\` escaping makes
  paths-as-names ugly (`C:\Users\foo` → `C:\\Users\\foo`) for no
  practical gain. LLMs parse `\"` correctly regardless of
  neighbouring backslashes. The rare component name containing both
  `\` and `"` tolerates the ambiguity.
- **Replace embedded `"` with `'` (human-friendly lossy).**
  Rejected — silent content mutation. A cell label literally
  containing `'` becomes indistinguishable from a modified `"`, and
  the AI can no longer reliably pass the value back to a tool
  (e.g. `swing_set_selection` by item name).
- **Emit `…` for quotes (replace `"` with typographic double
  quotes).** Rejected — same lossy-mutation objection, plus adds
  Unicode characters the AI may not round-trip reliably in tool
  arguments.
- **Sanitize only when an unsafe character is present (fast path for
  the common case).** Rejected — cheap micro-optimisation that
  doubles the policy surface (when is it applied, when is it not?).
  Java's regex engine is fast enough that the common case already
  costs nothing worth measuring; uniform application is easier to
  reason about.
- **Sanitize at the input boundary (`setAccessibleName`, title
  setters) instead of the render site.** Not an option — the input
  boundary is `javax.accessibility`, owned by the JDK. Swing-MCP
  cannot enforce sanitization there.

---

## DR-015 — `AccessibleRole.LABEL` never advertises `get_text`

**Status:** Accepted
**Applies to:** UC-002 (BR-06 action-list algorithm, BR-12 inline
preview gate), UC-005 (`swing_get_text`), `SwingUtils.supportsGetText`
**Decided:** 2026-04-15

**Decision.** Any accessible whose role is `AccessibleRole.LABEL` is
excluded from `get_text` unconditionally. `SwingUtils.supportsGetText`
returns `false` for LABEL-role accessibles regardless of whether
`AccessibleText` is exposed. Consequences, all automatic from the
single gate:

1. The snapshot never emits `get_text` on a LABEL node (BR-06 step 4).
2. The node receives no ref *on account of `get_text` alone* — refs
   still arrive via other actions (BR-06: a `JList` cell has `click`,
   a `JTree` node has `toggle_expand` and `click`).
3. No inline `text="..."` preview is emitted (BR-12 keys off
   `supportsGetText`; the gate change propagates for free).
4. `swing_get_text` called on a LABEL-role accessible returns the
   generic "Component does not support get_text" error. A dedicated
   error (parallel to DR-011's password message) would reveal more
   than it teaches — the rule is "labels are read from the snapshot
   name slot, not via a tool", which the AI already learns by
   observing absence of the action.

**Why.** A `JLabel` with plain text exposes no `AccessibleText`, so
`supportsGetText` returned `false` for it naturally. A `JLabel`
wrapped in `<html>…</html>`, however, gains an
`AccessibleHTMLTextSupport`-backed `AccessibleText` surface — a JDK
implementation detail of how HTML rendering is plumbed into
accessibility. The side-effect: HTML-backed labels were getting
`get_text`, a ref, and a `text="..."` preview, while plain labels
were getting none of those. Same role, same user-facing component,
different tool capabilities based on whether the developer typed
`<html>` at the start of the string. That's the wart this DR
workarounds.

After DR-015:

- Every `JLabel` behaves identically regardless of HTML-ness.
- The label's content remains fully readable from the snapshot name
  slot (uncapped per DR-014), so no information is lost.
- The AI's rule is simple: "LABEL-role nodes have no `get_text`;
  read them from the name slot."

**Role-based gate, not class-based.** The check is
`AccessibleRole.LABEL`, not `instanceof JLabel`. This matches
DR-011's pattern and gives free coverage to other LABEL-role
accessibles:

- **`JList.AccessibleJListChild`** — role `LABEL`, content in name
  slot, ref already assigned via `click`. No behavioural change:
  before DR-015, list cells only got `get_text` in the rare case
  where the renderer component exposed `AccessibleText`; after
  DR-015, they never do. The cell label is in the name slot.
- **`JTree.AccessibleJTreeNode`** — role varies but `LABEL` is
  common. Same analysis: ref from `click`/`toggle_expand`, content
  in name slot.
- **Custom components adopting `AccessibleRole.LABEL`** — follow the
  same rule for free.

**Relationship to DR-011.** Both DRs add role-based exclusions to
`SwingUtils.supportsGetText`. DR-011 (PASSWORD_TEXT) excludes because
the surfaced content is **misleading garbage** (echo chars); DR-015
(LABEL) excludes because the surfaced content is **redundant** with
the name slot. Same mechanism, different motivation. Implementation
is a two-line addition to the existing method:

```java
public static boolean supportsGetText(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return false;
    AccessibleRole role = ac.getAccessibleRole();
    if (AccessibleRole.PASSWORD_TEXT.equals(role)) return false;  // DR-011
    if (AccessibleRole.LABEL.equals(role)) return false;          // DR-015
    return ac.getAccessibleText() != null;
}
```

**Tension with DR-003 ("mirror Swing semantics exactly").** DR-003
says expose what Swing allows. Swing (via the HTML accessibility
plumbing) does allow reading `AccessibleText` on an HTML `JLabel`.
Justification for the carveout: the label's *content* is already
exposed via the name slot, so DR-015 is not withdrawing a
capability — it is eliminating a duplicate channel that exists only
because of a JDK implementation detail. The AI's effective
capability is unchanged: it can read every `JLabel`'s text, just
via one channel instead of two-for-some-and-one-for-others. The
underlying Swing semantics we're mirroring is "labels identify
other components" (LABEL role), not "labels are editable text"
(TEXT role).

**Alternatives considered.**

- **Status quo — keep the accidental `get_text` on HTML labels.**
  Rejected. The inconsistency is observable by the AI: identical
  user-visible components with different tool surfaces based on
  invisible implementation choices. The AI has no way to predict
  which `JLabel` accepts `swing_get_text` without trying it,
  inviting wasted tool calls and confusion.
- **Make plain `JLabel` *also* expose `AccessibleText` (enable
  `get_text` everywhere for consistency in the other direction).**
  Rejected. Every `JLabel` in every dialog (including one-word
  decorative labels like `"Name:"`, `"Password:"`) would receive a
  ref, inflating the ref sequence for interactive components
  elsewhere. The name slot already carries the content in full
  (DR-014); a tool is redundant. Changes a lot of code to produce
  worse snapshots.
- **Class-based gate (`instanceof JLabel`) instead of role-based.**
  Rejected — misses custom LABEL-role components, splits the rule
  inconsistently with DR-011's role-based pattern, adds a Swing-
  class-name coupling that role-based gating avoids.
- **Expose a dedicated `swing_get_description`-style tool for labels
  when the name would otherwise need to be long.** Deferred. The
  name slot is uncapped per DR-014, so a tool is not needed today.
  If profiling ever surfaces real cases where a label's content
  itself (not description) bloats snapshots and a truncation-plus-
  tool escape-valve becomes attractive, revisit under its own DR.
  Keyed off the same truncation trigger the deferred
  `swing_get_description` uses.
- **Dedicated error message on `swing_get_text` for LABEL role
  (parallel to DR-011's `"JPasswordField content is not readable"`).**
  Rejected. DR-011's dedicated message exists because the rule is
  non-obvious (why would reading a password field fail?). DR-015's
  rule — "read labels from the snapshot" — is obvious from the
  snapshot itself: the action isn't advertised, so the AI never
  calls the tool in well-behaved sequences. A generic error is
  sufficient for stale-ref recovery cases.

---

## DR-016 — Modal-stack annotation on snapshot roots

**Status:** Accepted
**Applies to:** UC-002 (BR-03 line format, Main Flow, BR-14),
`SnapshotNode` / `SwingSnapshotTool` render path, `SwingUtils`
**Decided:** 2026-04-15

**Decision.** When a root in the snapshot is a **modal** `Dialog`
whose `getOwner()` chain contains at least one **visible** window,
the renderer emits a header line immediately above the root's first
line:

    [modal stack (N, topmost first): Class0 "Name0" / Class1 "Name1" / ... / ClassN-1 "NameN-1"]

- `N` — total chain length including the rendered root. Minimum `N`
  for header emission is **2**; shorter chains produce no header.
- `topmost first` — literal annotation inside the parenthesis to
  state the ordering convention inline, so a reader parsing the
  header on its own does not need spec knowledge to know which
  entry is the active/topmost.
- `Class0` — the rendered root, leftmost, topmost of the stack.
- `ClassN-1` — the outermost *live* ancestor (usually a `JFrame`).
- `Class` — concrete simple class name, resolved via
  `ComponentClassResolver.resolveDisplayClass(accessible).getSimpleName()`
  so anonymous / synthetic / runtime-proxy / `javax.swing.plaf.*` /
  JDK-internal nested classes are stripped the same way they are in
  the snapshot body (BR-11 consistency).
- `"Name"` — `AccessibleContext.getAccessibleName()`, sanitised via
  `SwingUtils.sanitizeForQuotedSlot` (BR-13). The quoted slot is
  omitted entirely when the name is null/blank — the chain entry
  then reads as just `Class`.
- `/` — ASCII forward slash with a space on each side. Deliberately
  not an arrow: the BR-11 identity slot already uses `->` for the
  `Concrete -> JClass` custom-subclass form, and reusing arrow glyphs
  in the same format invites miscategorisation.
- **Per-root.** The header is emitted independently for each
  considered component that satisfies the modal-plus-live-owner
  predicate. Per DR-017 the current implementation returns only the
  topmost modal, so in practice exactly one header appears when a
  modal is up; the per-root design generalises cleanly if DR-017 is
  ever revisited.

**Scope: modal-only.**

- A non-modal `JDialog` with a visible owner frame does **not** get
  the header. The motivation is return-state context — what will be
  revealed when I close this — and a non-modal dialog does not block
  its owner, so "return state" is not a concept that applies.
- A `JFrame` root never gets the header: frames are top-level and
  `getOwner()` is `null` for them by construction.

**Visibility filter.**
`Dialog.getOwner()` can return `Window`s that are not visible — most
notably the **shared hidden frame** created by
`JOptionPane.showMessageDialog(null, ...)` or
`JDialog((Frame) null, ...)`. The chain walk skips any ancestor
where `SwingUtils.isVisible(owner)` returns `false`. After filtering,
if only the rendered root remains (length 1), no header is emitted —
a standalone modal with no live parent reads as "nothing to return
to", which the absence of a header conveys correctly.

**Placement.** The header line is emitted by `SwingSnapshotTool`
just before delegating to `root.render(0, sb)` for each root. The
render call itself is unchanged. No indentation — the header sits at
column 0 like the root line.

**Filter interaction.** Under `filter_substring` (BR-09), root
separators are already dropped. The modal-stack header is likewise
**dropped from filtered output** — the filter is a content-matching
tool, and a header that never contains matchable ref-bearing
content would add noise without signal. If a use case emerges for
surfacing the header in filtered output (e.g. when any descendant
matches), revisit; the current default is "strip".

**Why a header, not extra roots.**

- **Zero ref pressure.** Including owner windows as sibling roots
  would either assign refs to components the AI cannot currently
  interact with (per DR-017 — blocked by the modal) or require a
  new "hidden" ref state. Header-as-metadata sidesteps both.
- **Unambiguous about liveness.** The rendered root is the only
  tree the AI can act on right now. A single annotated line
  communicates "there's a stack, here's what's in it" without
  implying anything behind it is currently interactive.
- **Ownership ordering is the load-bearing fact.** The AI's
  question is "what state do I return to when I close this?" —
  precisely what `Dialog.getOwner()` answers. A flat inventory
  without ordering would force the AI to infer dismissal order.

**Why per-root, not one header for the whole snapshot.**
`Dialog.ModalityType.DOCUMENT_MODAL` permits multiple modals alive
at once in independent owner hierarchies (e.g. a multi-document
editor with two JFrames, each showing its own modal). A global
header cannot represent two chains; a per-root header generalises
cleanly. Under today's DR-017 scope only one modal is ever a root,
so per-root reduces to "header appears above the single root" —
future-compatible at zero present cost.

**Relationship to DR-017.** DR-017 fixes the contract: tools see
only windows the user can interact with, which means the owner
chain behind a modal is never reachable as refs. DR-016 restores
the *planning* signal — "what state do I return to" — as pure
metadata, without reopening DR-017's decision. The division is
strict: DR-017 owns the ref surface; DR-016 owns the informational
surface.

**Alternatives considered.**

- **Top-level flat window inventory — `[windows: JOptionPane
  (active, modal), LoginDialog (hidden, modal), JFrame "Billing
  App" (hidden)]`.** Rejected — encodes `active`/`hidden`/`modal`
  but not ownership, so the AI still has to guess dismissal order
  for chained modals.
- **Include ancestor windows as sibling roots, rendered with
  children replaced by a "[hidden beneath modal]" stub.** Rejected
  — see "zero ref pressure" above. Doubles the visible surface in
  the common single-modal case for a marginal gain, and undermines
  DR-017's ref-surface contract.
- **Single global header spanning all roots.** Rejected — cannot
  represent multi-hierarchy document-modal cases; also misleading
  when a snapshot contains non-modal frames alongside one modal
  dialog (today impossible under DR-017, but the per-root rule is
  forward-compatible).
- **Render the header after the root subtree (footer).** Rejected
  — the AI reads top-to-bottom; header is orientation information
  that belongs before the tree it annotates.
- **Include role parenthetical (`JDialog (dialog) "Login Failed"`)
  in chain entries, matching BR-11.** Rejected — every chain entry
  is a `Window` (Dialog or Frame); the role is fixed and adds no
  signal. Compact concrete-class-only keeps the header short.
- **Emit for non-modal dialogs too, renamed to `[owner chain:
  ...]`.** Rejected (scope) — non-modal dialogs do not block their
  owner, so the "return state" motivation does not apply. If
  surfacing non-modal ownership becomes valuable (palette dialogs
  attached to a document frame, etc.), revisit under a separate DR.
- **Unicode arrow `←` (U+2190) instead of `/`.** Rejected — any
  arrow glyph conflicts semantically with BR-11's `->`. The slash
  is visually orthogonal.
- **ASCII `<-` arrow.** Rejected for the same reason — mirrors
  BR-11's arrow direction, inviting confusion.
- **Comma separator with an `(active)` marker on the first entry
  (`JDialog "Login Failed" (active), JDialog "Login", ...`).**
  Rejected — self-documenting but redundant: the rendered root
  below the header is already the only active thing. Slash is
  terser and the inline `topmost first` annotation in the header
  handles the ordering convention.
- **Preposition `over` (`JDialog "Login Failed" over JDialog
  "Login" over JFrame "Billing App"`).** Rejected — most readable
  English but most verbose; `/` is tighter for the same signal.

---

## DR-017 — Tools consider only user-interactable windows

**Status:** Accepted
**Applies to:** `MCPServer.getConsideredComponents()`, UC-002
(snapshot), UC-003 (screenshot), all future read and mutation tools
that operate against
`SwingToolContext.getConsideredComponents()`
**Decided:** 2026-04-15 (formalising a pre-existing decision
documented as mechanism in project-context.md §5)

**Decision.** `MCPServer.getConsideredComponents()` returns the
windows a human user can interact with *right now*, and nothing
else:

- If a modal `Dialog` is showing, return exactly that one modal.
  Subordinate windows (owner frame, prior modal in the chain) are
  excluded.
- Otherwise, return every visible `Window`.

This is **not** a default to be refined later. It is the
load-bearing contract every tool operates under: a ref can only
address a component the user could click. There is no
`include_blocked_windows` flag, no "return all modals across owner
hierarchies", no exposure of hidden parents as additional roots.
The owner chain of a modal is surfaced as **metadata** (DR-016
modal-stack header), not as interactable roots.

**Why.**

- **Tool safety is sourced from the user-interaction invariant.**
  Every mutation tool (`swing_click`, `swing_set_text`, …) assumes
  the target is reachable by the user. If a ref could address a
  window blocked by a modal, the mutation would either no-op
  silently (framework swallows the event) or bypass the modal's
  intent (setter APIs that skip event dispatch). Both are worse
  than refusing to advertise the ref.
- **The snapshot is a decision surface, not a component
  inventory.** The AI reads the snapshot to decide what to do
  next. Including windows the AI cannot act on inflates the
  decision surface with non-options and dilutes ref numbering — a
  blocked `JButton` that gets `ref=7` is a footgun waiting to
  misfire.
- **Consistency across tools.** Every tool, read or mutation, sees
  the same set of roots. Screenshot, snapshot, and future tools
  all inherit the invariant from `getConsideredComponents()`
  without per-tool carveouts.
- **Screenshot honesty.** The PNG reflects what the user sees as
  actionable — a full stack would either overlap (hiding the
  modal) or arrange windows vertically in a way that does not
  match any real screen state.

**Interaction with DR-016.** DR-016 exposes the owner chain of a
modal root as a `[modal stack ...]` header. This adds
**informational context** for planning (what state returns when
this modal closes) without adding interactable roots. The
separation is deliberate: metadata lives in the header; refs live
in the body.

**Alternatives considered and rejected.**

- **Return every visible modal (support `DOCUMENT_MODAL`
  multi-hierarchy cases).** Rejected — topmost-only matches how
  nearly every Swing app uses modality in practice, and the
  per-root design of DR-016 means adding this later is a
  mechanical change in `getConsideredComponents()` alone. Not
  pre-built because speculative generality without a real-world
  case hurts today's clarity for tomorrow's maybe.
- **Return the modal *and* its owner chain as additional roots.**
  Rejected — see "decision surface" above. Refs on owner frames
  would allow mutations the framework blocks, producing confusing
  silent failures.
- **Expose an `include_blocked_windows` parameter.** Rejected —
  any flag with two modes where only one is ever correct is
  bureaucracy. The blocked case is handled by DR-016's metadata
  header; a flag would reintroduce the footgun it was designed to
  avoid.
- **Return everything always and let the AI figure out what is
  blocked from the `[modal]` state.** Rejected — this inverts the
  contract ("tool output is what the user can do" → "tool output
  is everything the framework knows about"). Every downstream tool
  would need its own "is this ref blocked?" check, and the AI
  would do ref-arithmetic across a soup of unreachable components.

**Why this DR exists even though project-context.md §5 documents
the mechanism.** §5 reads as operational rules that could evolve;
this DR declares the rules as a design commitment that will not
evolve, with the rejected alternatives captured so future sessions
(AI or human) do not re-litigate them. If §5's rules ever change,
this DR must be **superseded explicitly**, not amended silently.
