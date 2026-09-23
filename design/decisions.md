# Decisions

Why this project is the way it is and not otherwise — FAQ-shaped: each entry is a question and
its current answer. Rewrite the answer when it changes; delete the entry when nobody asks any
more. An entry is earned by what it would cost to reverse, or by research the next person would
otherwise redo (cited as its `R_`). **The roads not taken are the most valuable thing in here**:
the code says how it behaves today, and only this file says which cheaper-looking design was
tried and why it lost — so a rejected road keeps every one of its losing reasons, and an entry
that needs its roads is long and stays long. Not an entry: a message's wording, a constant's
value, the testing library, a version bump — a comment at the site of the choice, or nothing;
nothing about `design/` itself. A rule that binds one tool only is that tool's doc comment.
Cite by slug, `D_<slug>`, never by position;
`grep '^## D_' design/decisions.md` is the index. When you have written an entry, re-read it
against the one above, check it says nothing the doc comments already say, and check that every
rejected road still carries the whole reason it lost.

---

## D_accessibility_snapshot — Why drive Swing through the accessibility tree rather than screenshots and robot clicks?

Before this project the only way an LLM agent could operate a Swing application was to screenshot
it and click coordinates through `java.awt.Robot`. `swing_snapshot` walks `javax.accessibility`
instead and returns an indented text tree in which every interactable node carries a `ref`; every
other tool takes a `ref` and dispatches through the real listener tree, so a click is the click
the application would have received from a mouse.

**Why not screenshots and coordinates.** Two independent reasons, either of which would be
enough. Cost scales with pixels rather than with widget count, so a dense form and an empty
window cost the same to look at, and every re-look pays again. And a coordinate is only valid for
the geometry it was computed on: a resize, a look-and-feel change, a scrolled viewport or a
relaid-out container silently invalidates every coordinate the agent has learned, with no signal
that anything has changed — the agent clicks confidently on the wrong thing.

**Why keep `swing_screenshot` at all.** Some questions are genuinely about pixels: whether a
layout is broken, whether a custom renderer paints what it should, whether something overlaps.
The accessibility tree cannot answer those. But the screenshot is never how a tool is *targeted*
— there is no coordinate-taking path from an image to an action, deliberately, so the two
channels cannot drift into disagreeing about what is clickable.

**Why not both, with coordinates as a fallback for what the tree misses.** Rejected: it would
reintroduce the invalidation problem for exactly the components least likely to be stable, and it
would give the agent two ways to address the same widget, one of which silently stops working.
The fallback for a component the tree cannot see is to fix the probe (`SwingUtils`), not to route
around it.

**The cost we carry.** A component whose `AccessibleContext` is absent, or whose implementation
is wrong, is invisible to us — and we inherit every quirk of Swing's accessibility layer, which
is why `design/research.md` exists at all. `D_mirror_swing_semantics` is the rule that keeps us
honest about that rather than papering over it.

## D_mirror_swing_semantics — Why mirror Swing's semantics, including the broken ones?

Swing MCP is a faithful proxy. If a Swing application technically permits an interaction, this
server advertises it, however ridiculous it looks. No "cleaner" interpretation is layered on top
of what the framework actually does.

**The case that sets the rule.** `Component.setEnabled(false)` on a container does not disable
its descendants (`R_disabled_not_propagated`), so a button inside a greyed-out `JPanel`,
`JScrollPane` or `JToolBar` is still clickable by a human — and `swing_click` accepts it, and the
snapshot does not mark it `[disabled]`.

**Operational consequences.**

- `isEffectivelyEnabled()` does not walk the parent chain for real `Component` instances; it
  trusts each component's own `ENABLED` bit.
- Two places where Swing genuinely does propagate get carveouts: a tab disabled through
  `JTabbedPane.setEnabledAt(i, false)` becomes non-navigable, and virtual `JTable` cells follow
  their host. Both are invisible in the state set, so both are checked against the real API.
- Where Swing's `AccessibleStateSet` disagrees with Swing's own API — `AccessiblePage` still
  reporting `ENABLED` for a disabled tab — **the API wins**, because the API is what the user
  experiences.
- Cross-platform and look-and-feel corner cases are ignored rather than modelled:
  `Window.setEnabled(false)` blocks input at the peer but paints inconsistently, and there is no
  reliable way to represent that.

**Why.** Swing applications in the wild are old and messy. Developers programmatically select
disabled tabs, set non-propagating disabled state on containers, and put interactive widgets in
unconventional places. If the agent cannot reach a control that a human could click, it gets
stuck with no way forward and no explanation. The layer must reflect the *actual* clickability
surface, not an idealized one.

**Why not propagate `disabled` transitively to give the model a cleaner mental model.** Rejected
for two reasons, not one. The snapshot would lie about what the mutation tools accept — it would
show `[disabled]` on a button that `swing_click` will happily click, so the two halves of the
contract would disagree. And it would hide legitimately clickable controls inside
visually-disabled panels, which is the exact failure mode above.

**The two deliberate carveouts.** `D_jmenu_not_clickable` and `D_label_not_readable` each
withdraw something Swing technically allows. Both argue the same justification: the capability is
already delivered through another channel, so the agent ends up no less capable than a person,
which is what this rule is actually protecting. Any third carveout must clear the same bar.

## D_interactable_windows_only — Why do tools see only the windows a user could interact with right now?

`getConsideredComponents()` returns exactly the windows a human could act on at this moment: if a
modal `Dialog` is showing, that one modal and nothing else; otherwise every visible `Window`,
minus redundant heavyweight popup containers. This is the load-bearing contract every tool
inherits, not a default to be refined later — a ref addresses something the user could click, or
it does not exist.

**Why.**

- **Tool safety is sourced from this invariant.** Every mutation assumes its target is reachable
  by the user. A ref addressing a window blocked by a modal would either no-op silently, because
  the framework swallows the event, or bypass the modal's intent through a setter API that skips
  event dispatch. Both are worse than refusing to advertise the ref.
- **The snapshot is a decision surface, not an inventory.** The agent reads it to choose what to
  do next. Windows it cannot act on inflate that surface with non-options and dilute the ref
  numbering; a blocked `JButton` holding `ref=7` is a footgun waiting to misfire.
- **Consistency across tools.** Read and mutation tools see the same roots, so the invariant is
  inherited from one place instead of re-derived per tool.
- **Screenshot honesty.** The PNG shows what is actionable. A full window stack would either
  overlap, hiding the modal, or arrange windows vertically in an order matching no real screen.

**Why not return the modal's owner chain as additional roots.** Rejected — see the decision
surface above, and because refs on owner frames would permit mutations the framework blocks,
producing silent failures the agent cannot diagnose.

**Why not an `include_blocked_windows` parameter.** Rejected twice over: a flag with two modes
where only one is ever correct is bureaucracy, and the one case it would serve is already served
by `D_modal_stack_header`'s metadata, so the flag would reintroduce precisely the footgun the
rule exists to remove.

**Why not return everything always and let the model infer blockage from a `[modal]` state.**
Rejected — it inverts the contract from "tool output is what the user can do" to "tool output is
everything the framework knows about". Every downstream tool would then need its own is-this-ref-
blocked check, and the agent would do ref arithmetic across a soup of unreachable components.

**Why not return every visible modal, supporting `DOCUMENT_MODAL` multi-hierarchy cases.**
Rejected as speculative: topmost-only matches how nearly every Swing application uses modality,
and `D_modal_stack_header`'s per-root design means adding it later is a change in
`getConsideredComponents()` alone. Generality without a real case costs today's clarity for
tomorrow's maybe.

**Heavyweight popup exclusion.** When a `JComboBox` or `JMenu` popup opens, `PopupFactory` may
host the `JPopupMenu` in a heavyweight `JWindow`, which then appears in `Window.getWindows()` as
its own root — while the same content is already exposed as accessible children of the invoking
component. Both would mean duplicate subtrees with different refs, and an agent picking the
standalone copy gets refs belonging to the wrong tree. `SwingUtils.isRedundantPopupWindow` filters
them. Context menus, whose invoker is something else, are kept: the popup window is their only
representation. The same duplication is caught one layer down by the `JPopupMenu` prune rule in
`D_jmenu_not_clickable`.

## D_fire_and_forget_dispatch — Why does a mutation tool return before its action has run?

A mutation validates on the EDT inside `runInEDT()` — ref lookup, capability check, enabled check
— then posts the action with `SwingUtilities.invokeLater()` and returns immediately. The HTTP
response goes out before the action executes; the agent observes the outcome with a following
`swing_snapshot` or `swing_screenshot`. Read-only tools are unaffected: they do their whole job
inside `runInEDT()` and return synchronously.

**Why.** If a mutation's action listener opens a modal dialog, the EDT enters a secondary event
loop (`WaitDispatchSupport`). That loop still drains `invokeLater` tasks, so the server keeps
servicing tool calls — but an `invokeAndWait` dispatched from the HTTP thread would block until
the dialog is dismissed, and the only party who was ever going to dismiss it is the agent whose
thread is now blocked. Fire-and-forget never waits on completion, so the trap cannot close. A
secondary benefit: this is how a person uses an application — click, then look — rather than
gluing the client to the EDT until paint completes.

**Why not synchronous `invokeAndWait`.** Rejected — it deadlocks on any mutation that opens a
modal dialog, and for dialog-opening mutations that is the *common* path, not an edge case.

**Why not `invokeAndWait` with a timeout, falling back to fire-and-forget.** Rejected — it adds a
second code path without closing the window: the HTTP thread still sits blocked for the full
timeout on every modal-opening mutation, so the worst case is unchanged and the normal case is
slower.

**Why not return `doAccessibleAction()`'s boolean.** Rejected — the only way to read it is the
synchronous dispatch this rules out. The loss is smaller than it looks: pre-condition failures
(unknown ref, disabled component, unsupported action) are still caught synchronously during
validation and returned as errors, and a follow-up snapshot tells the agent far more than one
boolean would.

**Why not poll for the outcome after dispatching.** This was built, for `swing_close` only: an
`invokeAndWait` plus a `{100, 200, 700}` ms verification schedule that reported whether the
window had actually closed. It was replaced when fire-and-forget became universal. Two reasons it
lost: a poll answers "did it close" for one tool while every other mutation still needed the
general rule, so the special case earned nothing; and the `HIDE_ON_CLOSE` / `DO_NOTHING_ON_CLOSE`
branches collapse into the same answer anyway, which the snapshot gives for free. The carryovers
that were real — refusing undecorated windows and `EXIT_ON_CLOSE` frames — live in
`swing_close`'s own rules, not here.

**Trade-offs accepted.** The residual deadlock risk moves to read-only tools, which still use
`runInEDT()` synchronously; a 10-second watchdog throws with the EDT's stack trace so an
unexpected block names itself instead of hanging. And the tool's return says what was
*dispatched*, never what the application did — which is why `D_dispatched_echo` chooses its verb
as carefully as it does.

## D_tool_call_wide_lock — Why does `toolLock` span the whole tool call rather than the EDT turn?

The wrapper registered by `SwingMCP.registerTool` takes a server-wide `ReentrantLock` on entry
and holds it through `runInEDT()` and, for mutations, the subsequent `invokeLater` dispatch — not
merely around the EDT turn.

**Why.** The first instinct is that the EDT already serializes everything, so the lock is
redundant. Under `D_fire_and_forget_dispatch` it is not: `runInEDT()` returns as soon as
validation completes and the action has been *queued*, before it runs. Between that return and
the HTTP response being written, a second HTTP thread can enter, run its own `runInEDT()`,
replace the ref map, and queue a conflicting mutation ahead of the first one's action. The
wrapper-level lock is what closes that HTTP-thread gap; a lock scoped to the EDT turn would not.

**Why not rely on `runInEDT()` alone, with no extra lock.** Rejected — it leaks exactly the
post-validation, pre-action window described above.

**Why `ReentrantLock` rather than a `synchronized` block.** Rejected on readability: inside a
Swing lambda closure it becomes non-obvious which monitor a `synchronized` block holds, while an
explicit lock/unlock pair shows the scope at both call sites. There is no behavioural difference
being bought — this is a maintainability choice and is recorded as one.

**Why one server-wide lock rather than per-tool locks.** Rejected for two reasons. Concurrent
calls to *different* tools interleave ref-map writes just as destructively as calls to the same
tool, so per-tool locking would not actually protect the thing that needs protecting. And this
server serves one controller by design (`D_single_session`), so cross-tool concurrency has no
user to benefit.

## D_dispatched_echo — Why does a successful mutation echo `Dispatched …` rather than returning nothing?

Every successful mutation returns one text line naming what was dispatched, against which ref,
optionally to what value, and ending with an instruction to verify by snapshot. The exact format
and the value-rendering rules are `AbstractSwingTool`'s helpers and their javadoc; what follows
is why the shape is what it is.

**Why the verb is `Dispatched`.** Under `D_fire_and_forget_dispatch` nothing has run when the
tool returns. `Clicked ref=4` would assert two things the server cannot know: that the action
completed, and that the UI changed as a result. Listeners can veto any event and revert any
setter, so even a genuinely successful dispatch says nothing definitive about the resulting
state. The echo reports what the MCP layer actually knows.

**Why the verification suffix.** `Posted click on ref=4` was tested and read like a success
confirmation, and clients skipped the follow-up snapshot. The inline nudge puts the expected
workflow in the one place every client reliably reads: the response to the call it just made.

**Why one uniform shape across every mutation.** A missing or differently-shaped echo then looks
anomalous, in a log and to a reader. Mixed shapes would create a cliff for both.

**Why not empty content**, which is what this replaced. Rejected on evidence: most models treat
"some content" as a stronger positive signal than "no content", and the empty path tempted
clients into a redundant `swing_snapshot` purely to confirm the call had landed — spending
exactly the tokens the empty response was saving.

**Why not `OK` for every mutation.** Rejected — generic, indistinguishable from noise, echoes
none of the input, and useless when reading logs after the fact.

**Why not a past-tense verb per action** (`Clicked ref=4`, `Closed ref=4`). Rejected — see the
verb argument above; no past-tense verb accurately describes the tool's work at return time, and
past tense additionally implies an outcome verification the server cannot perform.

**Why not two echo shapes** — past tense for direct-API mutations like `setText`, `Dispatched X`
for event dispatches like `WINDOW_CLOSING`. Rejected: under universal fire-and-forget all
mutations are equally dispatched rather than executed by the time `execute()` returns, so the
proposed category split has no referent in the implementation.

**Why not bundle a snapshot into every mutation response.** Rejected for two reasons. Snapshots
are expensive and the agent does not always want one — it may be mid-sequence with three more
fields to fill. And it would couple every mutation's response size to the size of the component
tree, so the cost would scale with the wrong thing.

**Why not structured JSON** such as `{"action":"click","ref":4}`. Rejected — plain text tokenizes
more cheaply, reads directly in logs, and no consumer needs to parse it; the structure would earn
nothing.

**Why not differentiate no-op echoes** such as "ref=4 was already iconified". Rejected — no-op
mutations already route to the error channel with a specific message. The success path means one
thing only: the action was dispatched.

**Cost.** Roughly 15–20 extra tokens per mutation. Accepted: one snapshot saved by the nudge pays
for many suffixes.

**How clients learn the contract.** Two levels, deliberately not three. The per-call suffix is
the primary nudge. The server-level `INSTRUCTIONS`, sent once at `initialize`, name the echo
format and the dispatch-versus-outcome distinction, and also carry the parallel-call and
ref-staleness rules. Per-tool descriptions intentionally do *not* repeat any of this — the two
levels together cover it without duplicating a line across thirteen mutation tools.

## D_role_in_snapshot_only — Why does the snapshot name an accessibility role but everything else name a Swing class?

Every snapshot node line leads with `JClass (role)` — or `Concrete -> JClass (role)` for a custom
subclass, or `(role)` alone for a non-`Component` accessible — and the role parenthetical is
emitted **unconditionally**, even where it reads as tautology (`JButton (push_button)`).
Everywhere a person or a model reads prose — tool descriptions, parameter documentation, error
messages — components are named by Swing class only.

**Why emit the role even when it is redundant.** Uniform presence makes an unusual role a clean
attention signal. If the parenthetical were dropped for tautological cases, the agent would have
to reason about the *absence* of a parenthetical on every single line — is the role implied, or
genuinely missing? — which is a per-line cost paid to save a few characters.

**Why class names rather than roles in prose.** Class names are far more familiar to a language
model than accessibility-role vocabulary, and the snapshot already establishes the mapping.
Carrying both everywhere would be clutter without added signal.

**Why not roles everywhere, for consistency.** Rejected — unfamiliar vocabulary in exactly the
places where the agent needs to act quickly, and the information is already available where the
mapping matters.

**Why not drop the role when redundant with the class name.** Rejected — see the attention-signal
argument; this is the same road from the other direction and loses on the same ground.

**A non-load-bearing bonus.** The project's endgame is migrating Swing applications to Vaadin and
driving the browser through Playwright MCP, which also keys off accessibility roles. Swing roles
(`push_button`, `check_box`, `page_tab`) map instinctively onto ARIA roles (`button`, `checkbox`,
`tab`), so a "does every Swing interactable have a Vaadin counterpart" verification loop works
without a translation layer. This did not drive the decision and must not be used to defend it.

## D_quoted_slot_sanitizing — Why is the name slot uncapped while the description is capped, and why sanitize both?

Every quoted slot in a snapshot line — the name, the description, and the inline `text="…"`
preview — passes through one shared sanitizer before emission. It collapses whitespace runs to a
single space, escapes embedded `"`, strips the ends, and returns null for a blank result. The
name is then emitted in full; the description is capped at 120 characters.

**Why the asymmetry.** Different roles, different policies. A **name is identity** — it answers
"which button is this", and it is the agent's primary disambiguator; truncating a multi-sentence
warning `JLabel` to fit a bound destroys the very thing that made it worth surfacing. A
**description is advice** — supplementary context from a tooltip or `accessibleDescription` that
the agent may read and never needs to act on, so bounding it is cheap insurance against a
pathological tooltip and nothing load-bearing is lost to the `…`.

**Why sanitize rather than reject bad input.** The snapshot's tree structure is carried by
indentation and line breaks, so a single `JLabel("Line 1\nLine 2")`, or a list item populated from
a backend string containing a newline, produces a multi-line node line and silently corrupts the
hierarchy for every reader below it. Every slot reads user-controlled data — `setAccessibleName`,
titles, cell renderers, custom `AccessibleContext` implementations — so defensive sanitization is
the only way to preserve the one-line-per-node invariant. The quote escape follows the same
logic: the slot is quoted, so an embedded quote must not terminate it visually.

**The sanitizer is deliberately not idempotent.** Backslashes are not escaped, so re-running it
on already-sanitized output would double-escape embedded quotes. The render path calls it exactly
once per slot; the discipline lives at the call site, not in the helper.

**Why not uncap both.** Rejected — pathological tooltips are real: accidental 10k-character
strings, localisation bugs, HTML-heavy help text inherited from older applications. They bloat
the snapshot while carrying no load-bearing signal.

**Why not cap both at the same length.** Rejected — it truncates the long warning label the
developer deliberately wrote, which is the name-as-identity argument above.

**Why not full JSON escaping**, escaping `\` to `\\` alongside `"` to `\"`. Rejected for two
reasons. The snapshot is a text format consumed by models, not a parser-driven contract, so the
extra escaping buys no correctness. And it makes paths-as-names ugly (`C:\Users\foo` becomes
`C:\\Users\\foo`) for a rare ambiguity that models tolerate.

**Why not replace `"` with `'`, or with typographic quotes.** Rejected — silent content mutation.
A cell label that genuinely contains `'` becomes indistinguishable from a modified `"`, after
which the agent can no longer reliably pass the value back to a tool such as
`swing_set_selection` by item name. Typographic quotes add the same problem plus characters the
agent may not round-trip.

**Why not sanitize only when an unsafe character is present.** Rejected — a micro-optimisation
that doubles the policy surface: every reader then has to know when it applies and when it does
not. Uniform application is easier to reason about and costs nothing measurable.

**Why not sanitize at the input boundary** instead of at render. Not available — the input
boundary is `javax.accessibility`, owned by the JDK.

**Interaction with the caps.** Sanitization runs *before* truncation in every slot, so the `…`
always follows a printable prefix rather than a trailing escaped quote or a collapsed-whitespace
artefact. A description that gets capped advertises `get_description`, so the full string stays
reachable through `swing_get_description`.

## D_inline_value_preview — Why does the snapshot inline a value preview instead of making the model ask?

A node that can report text carries `text="…"`, capped at 15 characters; a node with a numeric
value carries `value=N` bare — or `value=<current>/<max>` for a progress bar, where the
denominator is the entire point of looking at one. Exactly one of the two may appear. Both read
through the same helpers that `swing_get_text` and `swing_get_value` use, so a preview and a
tool call can never disagree: a `text="abc…"` preview structurally guarantees the tool returns a
string starting `abc`.

**Why.**

- **Chatter reduction.** Observed in the field: clients round-tripped `swing_get_text` or
  `swing_get_value` for every text field, slider and spinner on a form just to learn current
  contents. On a ten-field form that is ten extra calls per verification cycle.
- **Auto-discovery.** The agent *sees* the current value while reading the snapshot, without
  having to decide to ask. That is a different property from cheapness, and the one that matters.
- **Bounded cost.** A 15-character cap puts a 50-field form at roughly 750 characters, well
  inside the noise floor of a snapshot of that size.
- **It matches the round-trip tool.** `text="…"` implies `swing_get_text`; `value=42` implies
  `swing_get_value`. The label documents itself.
- **Closer to Playwright, not further.** The original "no field values" rule cited consistency
  with Playwright MCP; in fact Playwright's snapshot does surface values for textboxes, sliders
  and progressbars.

**Why not a `include_values` flag on `swing_snapshot`.** Rejected — the hidden cost is a per-call
decision. Either the agent always sets it, which is this behaviour with extra ceremony, or it
never does, which is the old behaviour. A flag earns its keep when both modes have real users;
here there is one controller with one preference per session.

**Why not a bulk `get_text` / `get_value` accepting an array of refs.** Rejected *as a
substitute*, kept open as a later addition. It fixes chattiness — one batched call instead of N —
but not discovery: the agent still has to decide whether to batch, and any heuristic for that
leaks. It may still be worth adding for slider-heavy control panels where the untruncated number
matters more than the preview.

**Why not `value=<current> [<min>..<max>]` everywhere.** Rejected — the range is rarely
actionable at a glance, since a sensible range is usually implied by the widget's purpose, and
`swing_get_value` returns bounds when they matter. The denominator survives only for
`JProgressBar`, where progress toward a maximum *is* the meaning of the widget.

**Why not inline previews for `JCheckBox`, `JRadioButton` or `JComboBox` selection.** Rejected —
`[checked]` and `[selected]` already carry the boolean signal for checkboxes and radios, and
combo-box selected-item exposure is a separate design question that belongs with the whole
selection-display story across `JList` / `JTable` / `JComboBox` rather than bolted on here.

**Why not show raw newlines, or JSON-escape them.** Rejected — collapsing to a single space
matches the established text-cleanup convention and keeps the snapshot line-oriented; at 15
characters the fidelity cost is negligible and the full content is one tool call away.

**Trade-offs accepted.** Snapshot size now correlates loosely with form *content*, not just form
structure — bounded by the cap, empirically negligible. Multi-line `JTextArea` previews lose line
structure to whitespace collapsing, which at 15 characters rarely carried meaning, and the
`multi_line` state plus the `get_text` action both signal that full content is available. And for
a field holding sensitive-but-not-password content — a draft message, an API key, a
partially-typed credential — the snapshot now surfaces up to 14 characters of it. Accepted on two
grounds: the agent could already read the whole value with `swing_get_text`, so this reduces the
cost of exfiltration without widening the surface; and the role-based gate
(`D_password_not_readable`) covers the one case Swing itself marks as secret.

**Defensive read.** The preview is wrapped in try/catch. A custom widget whose `AccessibleText`
or `AccessibleValue` throws — document lock contention, a misbehaving implementation — loses its
annotation and nothing else; a `FINE` log line records it. The snapshot must always produce
output, and one bad widget cannot take down the tree.

## D_password_not_readable — Why can a password field be written but not read?

Any accessible whose role is `AccessibleRole.PASSWORD_TEXT` never advertises `get_text`, and
`swing_get_text` on one returns a dedicated error naming the rule rather than the generic
unsupported-action message. The `set_text` branch is untouched: a password field still advertises
`set_text`, or `!set_text` when not editable.

**Why the asymmetry is the right one.** Writing a credential the operator supplied is a real and
necessary use case — AI-driven login-form filling. Reading one back turns the server into an
exfiltration channel and buys nothing the agent needs. The JDK's default behaviour undermines
that posture on two fronts at once: it returns echo characters rather than refusing
(`R_password_echo_chars`), which a model can reasonably read as "this field contains six bullet
characters"; and the echo string's length equals the real password's, which leaks the one
property of a credential that is useful to someone who has everything else.

**Why gate on the role rather than `instanceof JPasswordField`.** The role is Swing's own marker
for "this content is secret", so gating on it covers a third-party or internal `SecretField`
whose `AccessibleContext` reports `PASSWORD_TEXT` — free coverage that a class check would miss.
This is `D_mirror_swing_semantics` applied honestly: we are honouring Swing's own signal.

**Why a dedicated error message rather than the generic one.** The rule is genuinely surprising —
nothing about a text field suggests reading it should fail — so the error teaches it instead of
leaving the agent to infer that the capability is simply absent. Contrast `D_label_not_readable`,
where the rule is visible in the snapshot and a generic error suffices.

**Why not document the echo-character behaviour and change nothing.** Rejected — cheaper in the
short term, but the snapshot would still advertise `get_text` on a password field, luring the
agent into a call that returns `••••••` which it then has to interpret. Two active footguns
survive: the misleading value and the leaked length.

**Why not drop `set_text` as well, for symmetry.** Rejected — it kills a primary use case in
exchange for a symmetry that provides no security benefit. Writing a known value is not
exfiltration.

**Why not return `""` or null instead of an error.** Rejected for two reasons: silent degradation
gives the agent no signal that the call was refused, and `""` is indistinguishable from a
genuinely empty field. An explicit error teaches the rule and is auditable in logs.

**Pathological case, supported deliberately.** A `JPasswordField` with `setEditable(false)`
advertises only `!set_text` — a ref whose sole action is currently unavailable. Nobody should
ship one, but the node keeps its ref and stays visible so the agent can see that a password field
exists and is not currently writable. No special case in the ref-assignment gate.

## D_label_not_readable — Why does a `LABEL`-role node never advertise `get_text`?

`SwingUtils.supportsGetText` returns false for the `LABEL` role regardless of whether
`AccessibleText` is exposed. Everything else follows from that one gate: no `get_text` in the
snapshot, no ref *on account of `get_text` alone*, no inline `text="…"` preview, and a generic
unsupported-action error from the tool.

**Why.** A plain `JLabel` exposes no `AccessibleText`, so it never advertised `get_text`. A
`JLabel` whose string is wrapped in `<html>…</html>` gains an `AccessibleHTMLTextSupport`-backed
`AccessibleText` (`R_html_label_accessible_text`) purely as a side effect of how HTML rendering is
plumbed into accessibility. The result was that two visually identical labels had different tool
surfaces depending on whether a developer happened to type `<html>` — a rule the agent cannot
predict without trying it, inviting wasted calls and confusion.

**Nothing is lost.** The label's content is in the name slot, uncapped per
`D_quoted_slot_sanitizing`. The agent's rule becomes simple: LABEL-role nodes are read from the
name slot, not through a tool.

**Why the gate is role-based, not `instanceof JLabel`.** Free coverage for other LABEL-role
accessibles, which matters more than it sounds: `JList.AccessibleJListChild` carries role `LABEL`
with its content in the name slot and its ref already earned through `click`, and
`JTree.AccessibleJTreeNode` is commonly `LABEL` too. A class check would split the rule
inconsistently and miss custom components.

**Why not keep the accidental `get_text` on HTML labels.** Rejected — the inconsistency is
observable by the agent and unpredictable, which is the whole complaint.

**Why not go the other way and give every `JLabel` a text surface**, for consistency in the other
direction. Rejected for two reasons. Every decorative `"Name:"` and `"Password:"` in every dialog
would receive a ref, inflating the ref sequence that interactive components depend on. And the
name slot already carries the content in full, so the tool would be redundant — a lot of code
changed to produce worse snapshots.

**Why no dedicated error message**, as `D_password_not_readable` has. Rejected — that rule is
non-obvious and worth teaching; this one is visible in the snapshot, because the action simply is
not advertised, so a well-behaved agent never calls the tool. A generic error is sufficient for
the stale-ref recovery case.

**Why not add a `get_description`-style escape valve for very long label content.** Deferred, not
rejected. The name slot is uncapped, so nothing is unreachable today. If profiling ever shows
label content itself bloating snapshots, the answer is a truncation trigger plus a tool, under
its own entry.

**Relationship to the sibling rule.** Both this and `D_password_not_readable` add role-based
exclusions to the same method, but for opposite reasons: password content is **misleading
garbage**, label content is **redundant** with a channel that already carries it. Same mechanism,
different motivation — worth keeping straight, because a future exclusion needs to say which kind
it is.

**Tension with `D_mirror_swing_semantics`.** Swing does permit reading `AccessibleText` on an
HTML label. The carveout holds because this withdraws a *duplicate channel* that exists only as a
JDK implementation detail, not a capability: the agent can still read every label's text, through
one channel instead of two-for-some. The semantics being mirrored is "labels identify other
components", not "labels are editable text".

## D_jmenu_not_clickable — Why doesn't `JMenu` expose `click`?

`SwingUtils.supportsClick` returns null for a `JMenu`, so a menu title carries no `click` action
and no ref. When a menu's popup is open — however it was opened — the resulting `JPopupMenu` node
is pruned whenever its invoker is a `JMenu`. Context popups, invoked from a `JButton` or `JTable`,
are unaffected. The items themselves keep `click`; they are the real targets.

**Why.** A human clicks a menu title to make its items visible. The agent already has them: a
`JMenu`'s accessible children *are* its `JMenuItem`s, rendered under it whether the popup is open
or not. So the click delivers nothing and costs three things:

- **Context.** An open popup duplicates every item — once under the `JMenu`, once under the
  `JPopupMenu`, the same instances reachable by two paths — so a large menu inflates the snapshot
  and forces the agent to choose between identical refs.
- **Round-trips.** `click menu → snapshot → click item` is strictly worse than `click item`, and
  the intervening mutation clears the ref map, so the agent must re-snapshot just to recover the
  item's ref.
- **Semantic clarity.** `doClick()` on a `JMenu` opens the popup but does not close it. An action
  that works once and then silently stops is a worse contract than an absent action.

**Why not keep `click` and make it toggle via `MenuSelectionManager`.** Rejected — it fixes the
asymmetric-close defect and leaves the duplication and the wasted round-trip entirely untouched.
The right question was whether the action should exist, not how to make it symmetric.

**Why not keep `click` for open and refuse it for close.** Rejected — a one-shot "opens but
cannot close" action is worse than no action, and the close path through `MenuSelectionManager` is
trivial enough that refusing it would be arbitrary rather than principled.

**Why not expose `toggle_popup` on `JMenu` instead.** Rejected for the common case, where items
are already visible and the tool would be pure overhead. Deferred for the corner case below.

**Dynamically populated menus — deferred.** An application that adds items only inside
`PopupMenuListener.popupMenuWillBecomeVisible` hides them from the agent entirely under this
rule. Rare enough to defer, and the right answer when it arrives is `toggle_popup` with explicit
open/close intent under its own entry — not `click`, whose toggle semantics are asymmetric.

**Tension with `D_mirror_swing_semantics`.** Humans can click menu titles, so this is a carveout.
It holds because the user-visible *effect* of that click — revealing the items — is already
delivered by the snapshot, so mirroring the human path would give the agent strictly less capable
behaviour than it has. A `JMenu` is closer to an HTML `<details>` element, UI plumbing for
progressive disclosure, than to a button.

## D_modal_stack_header — Why is a modal's owner chain a header line rather than extra roots?

When a snapshot root is a **modal** `Dialog` whose `getOwner()` chain contains at least one
**visible** window, the renderer emits a header line immediately above it:
`[modal stack (N, topmost first): Class0 "Name0" / … ]`. The rendered root is leftmost and
topmost; the outermost live ancestor is last; `topmost first` is stated inline so a reader parsing
the header alone needs no spec knowledge. A chain of fewer than two produces no header.

**Why a header rather than extra roots.**

- **Zero ref pressure.** Including owner windows as sibling roots would either assign refs to
  components the agent cannot currently interact with — which `D_interactable_windows_only`
  exists to prevent — or require inventing a second "hidden ref" state.
- **Unambiguous about liveness.** The rendered root is the only tree the agent can act on. One
  annotated line says "there is a stack, here is what is in it" without implying anything behind
  it is reachable.
- **Ordering is the load-bearing fact.** The agent's real question is "what state do I return to
  when I dismiss this", which is exactly what `getOwner()` answers. A flat inventory without
  ordering would force the agent to infer dismissal order.

**Why modal-only.** A non-modal `JDialog` with a visible owner gets no header: it does not block
its owner, so "return state" is not a concept that applies to it. A `JFrame` root never gets one
either — `getOwner()` is null for frames by construction.

**Why the visibility filter.** `Dialog.getOwner()` can return windows that are not visible, most
notably the shared hidden frame created by `JOptionPane.showMessageDialog(null, …)` or
`JDialog((Frame) null, …)`. The walk skips invisible ancestors; if only the rendered root
survives, no header is emitted, which correctly reads as "nothing to return to".

**Why per-root rather than one global header.** `Dialog.ModalityType.DOCUMENT_MODAL` permits
several modals alive at once in independent owner hierarchies — a multi-document editor with two
frames, each showing its own modal. A global header cannot represent two chains. Under today's
`D_interactable_windows_only` scope only one modal is ever a root, so per-root reduces to "one
header above the single root" and is forward-compatible at zero present cost.

**Why not a flat window inventory** such as `[windows: JOptionPane (active, modal), LoginDialog
(hidden, modal), JFrame "Billing App" (hidden)]`. Rejected — it encodes active/hidden/modal but
not ownership, so the agent must still guess dismissal order for chained modals, which is the one
thing the header exists to state.

**Why not render ancestors as sibling roots with their children stubbed out.** Rejected — see
zero ref pressure; it also doubles the visible surface in the common single-modal case for a
marginal gain and undermines the ref-surface contract.

**Why not place the header after the subtree, as a footer.** Rejected — the agent reads top to
bottom, and this is orientation information for the tree beneath it.

**Why not include the role parenthetical** in chain entries, matching the node lines. Rejected —
every chain entry is a `Window`, so the role is fixed and adds no signal; concrete class names
keep the header short.

**Why `/` rather than an arrow.** The identity slot already uses `->` for the
`Concrete -> JClass` custom-subclass form, so any arrow glyph — `←`, `<-` — invites
miscategorisation in a format the agent is parsing by shape. The slash is visually orthogonal.

**Filter interaction.** Under `filter_substring` the header is dropped along with root
separators: the filter matches content, and a header that can never contain matchable ref-bearing
content would add noise without signal. Revisit if a case emerges for showing it when a
descendant matches.

**Division of labour with `D_interactable_windows_only`.** That entry owns the ref surface — the
owner chain is never reachable. This one owns the informational surface — the planning signal
comes back as metadata. The split is strict, and restoring the ref surface would require
superseding that entry explicitly, not amending this one.

## D_desktop_icon_as_itself — Why is a `JDesktopIcon` rendered as itself rather than resolved back to its frame?

When a `JInternalFrame` in a `JDesktopPane` is iconified, Swing removes it from the component and
accessibility trees entirely and a `JDesktopIcon` takes its place as a child of the desktop pane
(`R_iconified_windows`). The snapshot renders that icon as what it is: class `JDesktopIcon`, role
`desktop_icon`, named through a three-step fallback that lands on the frame's title, with its own
children hard-excluded. `swing_close` on it resolves to the internal frame.

**Why embrace the icon rather than resolve through it.** The first design did resolve the icon
back to its `JInternalFrame` and synthesized an `ICONIFIED` state. It fought the framework at
every step: the resolved frame has `isShowing() == false`, which trips the visibility hard-
exclusion and the `isShowing()` gate inside `supportsClose()`, and its children are
non-interactable. Every one of those needed its own carveout. The icon, by contrast, is a real,
showing component with a natural place in the tree, and `desktop_icon` is a concept a language
model recognises from Swing documentation. The title annotation gives identity continuity between
the open frame and its icon without any tree mangling. `JInternalFrame.JDesktopIcon` is public
and Swing is frozen, so there is no refactoring risk in depending on it.

**Why walk the desktop pane's accessible children rather than `getAllFrames()`.** Rejected as the
*sole* strategy — `getAllFrames()` drops the non-frame children that real applications add to a
desktop pane: toolbars on palette layers, background labels, status bars. It is still consulted
afterwards, as a supplement, purely to recover iconified frames whose icons the accessibility
walk missed — which happens on macOS Aqua, where the icon is nested inside a non-accessible
`AquaInternalFramePaneUI$Dock` wrapper and the desktop pane reports zero accessible children. The
supplement is harmless on Metal and Windows, where the icon is already a child.

**Why hard-exclude the icon's children.** The button and label inside are look-and-feel rendering
artifacts, not semantic content. They are skipped during the walk rather than constructed and
then dropped, so they cost nothing.

**Why not render the icon raw**, with no title annotation and its look-and-feel children visible.
Rejected — the icon's own accessible name is null, so it would render as a nameless
`JComponent (desktop_icon)` containing a button and a label, and the agent would have to inspect
the button's text to work out which document it is. The title annotation is a minimal, honest
assist.

**Two consequences that live here rather than in their own entries.** `JDesktopPane` and
`DESKTOP_ICON` are both in `SEMANTIC_ROLES`, so neither is pruned — an empty desktop pane still
signals MDI semantics to the agent. And `JInternalFrame`'s `AccessibleValue`, which exposes the
`JLayeredPane` Z-order layer, is suppressed: the layer is a programmatic concept, not a
user-controlled value, and `set_value` on it would silently re-layer frames.

## D_synthetic_iconified_state — Why synthesize `ICONIFIED` for a `JFrame` instead of trusting the state set?

The snapshot derives `[iconified]` from `frame.getExtendedState() & Frame.ICONIFIED`, alongside
the other synthetic states. `AccessibleJFrame` never puts `ICONIFIED` in its state set
(`R_iconified_windows`) — a JDK omission that, Swing being frozen, will never be fixed. This is
the same pattern already established by `disabled`, which is derived from `isEffectivelyEnabled()`
rather than trusted from `AccessibleState.ENABLED`.

**Why not trust the JDK state set.** Rejected — `ICONIFIED` is never present, so the agent would
never see it at all. This is not a question of preferring one source; one source is empty.

**Why not drop `ICONIFIED` from the displayed states entirely.** Rejected — knowing a window is
minimized is exactly what tells the agent to restore it before trying to interact, and
`D_iconified_children_hidden` makes that signal actionable.

**A `JInternalFrame` gets it too, from `isIcon()`.** Usually there is no frame node left to
annotate, because its `JDesktopIcon` took its place (`D_desktop_icon_as_itself`). But outside a
`JDesktopPane`, or under a custom `DesktopManager`, the frame is iconified in place and stays
showing (`R_iconified_windows`). It then advertises `restore`, and without `[iconified]` nothing
would explain why. Its children stay listed, unlike a `Frame`'s (`D_iconified_children_hidden`):
they are still on screen and still clickable. A custom manager that hides them by shrinking the
frame leaves them zero-sized, and the visibility check drops them anyway.

**Restoring clears only that bit.** `setExtendedState(getExtendedState() & ~Frame.ICONIFIED)`
preserves other extended-state bits, so an iconified-maximized frame comes back maximized.
`setState(Frame.NORMAL)` was rejected because it clears every bit, silently un-maximizing a window
on the way back — a change the agent did not ask for and would not notice.

## D_iconified_children_hidden — Why does the snapshot hide the children of an iconified frame?

When a snapshot root is a `Frame` whose extended state includes `ICONIFIED`, the frame node
renders normally — `[iconified]`, its ref, its window-level actions — and its children are
neither ref-assigned nor rendered. One placeholder line under it says the contents are hidden and
names `swing_restore`.

**Filtered or not.** A filter never searches the hidden children, and a filtered snapshot always
lists every iconified frame with its placeholder, match or not. Otherwise a filter that hit only
hidden content would leak the children, and one that missed would say "no match" without
pointing at the one window that might hold it.

**Why.** An iconified `JFrame` looks entirely normal through the accessibility API: it stays
`isShowing()`, its children stay accessible, and `printAll()` still renders its full content
(`R_iconified_windows`). So the screenshot showed an ordinary window, the snapshot listed
clickable children with refs, and every click on them silently did nothing. Mixed signals in
three channels at once, and the agent had no way to diagnose it.

**Why not filter iconified frames out of the considered windows entirely.** Rejected — it hides
the frame from both the snapshot and the screenshot, so the agent loses the knowledge that the
window exists *and* the means to restore it. It would be trading one invisible failure for
another.

**Why not render the children but strip their refs.** Rejected — a full component tree the agent
can see but not act on is confusing without being useful, and it still spends the tokens. The
placeholder line is a clearer signal at a fraction of the size.

**Why not render the children with all actions `!`-prefixed**, as disabled. Rejected —
misleading: `!click` means "this component is disabled", not "its window is minimized". The agent
would work through several failed interactions before considering `swing_restore`, which is the
exact failure this entry removes.

**Why `swing_screenshot` is deliberately unchanged.** The screenshot is the raw rendering view —
what Swing can paint — and the snapshot is the semantic view of what the agent can work with. The
two serve different purposes, and the `[iconified]` tag plus child suppression is sufficient to
stop the agent trying. Two rejected ways of changing it: dimming or overlaying the image is
fragile across look-and-feel themes and may not read as intended; and returning text instead of
an image breaks the tool's contract, when a rendered minimized window may still be worth
analysing.

**Scope.** Top-level `Frame` only. `JInternalFrame` is already handled by
`D_desktop_icon_as_itself`. Pruning still runs over the suppressed children, so the tree is
structurally ready if the frame is restored — defensive, and free.

## D_tree_filter_over_line_grep — Why does `filter_substring` return a subtree rather than the matching lines?

A match drags in its whole ancestor path and its whole subtree; only sibling branches that
contain no match are dropped. The output opens with a `[filter active: …]` notice so the agent
knows it is not looking at the whole tree.

**Why.** The first implementation was the obvious one — render everything, keep the lines whose
text matched — and it failed in two ways that a snapshot cannot afford.

- **The agent could not orient itself.** Isolated lines say nothing about where a component sits,
  so the model re-snapshotted with different filters trying to work out the structure, spending
  more calls than an unfiltered snapshot would have cost.
- **Filtering a container silently emptied it.** A filter matching a `JTable`, `JList` or
  `JComboBox` returned the container's own line and dropped every row, item and entry under it —
  structurally complete-looking output describing a table with no rows.

Ancestors fix the first, descendants the second.

**Why not return matching lines plus a separate path string.** Rejected — it invents a second
notation for something the indent already expresses, and still leaves matched containers empty.

**Why keep the notice line.** Sibling branches are gone with no other trace, so without it a
filtered snapshot is indistinguishable from a small application. The notice is what tells the
agent to re-snapshot unfiltered when it needs the whole picture.

## D_select_all_standalone — Why does `swing_select_all` call select-all directly rather than delegating to `swing_set_selection` with every index?

`swing_clear_selection` is a thin delegate to `swing_set_selection` with an empty array, and the
symmetric move here would be to delegate with `[0, itemCount)`. It does not: it calls
`selectAllAccessibleSelection()`, or `JTable.selectAll()` on a table (`R_accessible_selection_writes`).

**Why.** "Everything is selected" is a state a selection model is allowed to represent cheaply,
rather than as a set holding every identifier. Handing in all the indices takes that option away
— it forces the model to materialize a selection the size of the data, which on a large table is
both wasteful and a different outcome from what the component's own select-all would have
produced.

**Why not delegate anyway, for the code sharing.** Rejected — the shared code is a loop that
builds a range, which is not worth losing the component's own semantics. The validation the two
tools genuinely share lives in `AbstractSwingTool`'s `requireSelectable` / `requireMultiSelectable`.

**Why refuse a single-selection component instead of selecting its one item.** Rejected —
"select all" on a component that can hold one item has no meaning the agent could have intended,
and the underlying call does not refuse it either: on a `SINGLE_SELECTION` `JList` it silently
leaves the *last* item selected, which looks like success and is not.

## D_no_jtree_selection — Why do the selection tools refuse a `JTree`?

`JTree` is in `SUPPRESSED_SELECTION_ROLES`, so a tree advertises neither selection group label
and every selection tool rejects it. `get_cells` still enumerates its visible nodes.

**Why.** A tree's own `AccessibleSelection` is non-functional — it reports a selection count of
zero no matter what is selected. The real selection is distributed across the node children, each
reporting which of *its* children are selected (`R_selection_index_spaces`). Every other
selection-bearing component answers at the component level, so supporting trees means a second
implementation — walk the whole node hierarchy, collect per-node answers, and invent an index
space to return them in, since a tree path is not an integer offset into anything.

**Why not advertise selection and let it return empty.** Rejected — the tool would succeed and
report nothing selected while the user is looking at a highlighted row. A refusal naming the
component is recoverable; a confident wrong answer is not.

**Why not expose tree selection through a path-shaped API of its own.** Deferred, not refused —
it is a different index space from the integer offsets every other selection tool speaks, and no
migration has needed it yet. `get_cells` already reaches the visible nodes for interaction.

## D_no_jtable_cells — Why doesn't `JTable` advertise `get_cells` / `get_cell_count`?

Those two tools are advertised on a truncated `JList` or `JTree` only. Row-level access to a
`JTable` is `swing_get_items`, and at runtime the cell tools reject a table with an error
redirecting the agent to the row tools.

**Why.** Two independent reasons.

- **There is nothing actionable to return.** `JTable` cells are stamp-painted through
  `CellRendererPane` and surface as plain `LABEL`s with no actions (`R_jtable_cells_stamped`), so
  `get_cells` could never hand back an actionable ref on a table — which is the entire purpose of
  the tool. Interactive cell *editors* appear only while a cell is being actively edited, so they
  cannot be reached by walking either.
- **The index spaces would collide.** The snapshot speaks in rows, and so do the selection tools.
  `get_cells` counts cells row-major. Advertising both means an agent sees `… and 45 more rows`
  in the snapshot and then `cell_count = 150` for the same table, with no way to reconcile the
  two without tracking the column count out of band.

**Why not keep cell access on `JTable` for symmetry with `JList` and `JTree`.** Rejected on both
counts above — symmetry of API shape is not worth a tool that returns only non-actionable labels
in a unit the rest of the surface does not use.

**Why not translate cell indices to row/column on the fly.** Rejected — it adds translation
surface to a tool that, on a table, can still only return non-actionable labels. It fixes the
smaller of the two problems and none of the larger one.

**Follow-up, if it is ever needed.** Bulk row access beyond the snapshot cap means row-dumping
tools. Do not resurrect cell-indexed access to get there.

## D_shared_tool_manifest — Why does every tool bind its name, description and schema from one manifest class?

`SwingTools` holds the server name and version, the instructions and one `ToolDescriptor` per
tool. `AbstractSwingTool` binds a descriptor at construction and makes its name, description and
schema final, so a tool implementation cannot disagree with the manifest even by accident.

**Why.** Everything the model reads about this server is in one file, and those texts refer to
each other: the instructions name `swing_click` and `swing_set_text`, and most descriptions say
"requires a ref obtained from `swing_snapshot` or `swing_get_cells`". Reviewed in one place, a
rename or a changed contract is visible across all of them at once. Spread across the tool
classes, each text reads fine alone, and the cross-reference that went stale is the one nobody
opens.

**Why bind the descriptor into `AbstractSwingTool` rather than let each tool declare its own
name and schema.** Rejected — declaring them locally is how the drift starts. Making the three
accessors final and descriptor-backed means a tool *cannot* disagree with the manifest, rather
than being expected not to.

**Why a developer-time test as well.** `SwingToolsCoherenceTest` boots the server, calls
`listTools()` and compares the result structurally against `SwingTools.ALL`, and asserts that
`initialize` returns the identity constants. It catches the one drift the final accessors cannot:
a descriptor in `ALL` that no tool registers, or a registered tool missing from `ALL`.

**Why a class in `swing-mcp` rather than a module of its own.** The manifest was a module while a
stdio proxy, running in its own JVM, needed it without the Swing stack. With the proxy gone
(`D_direct_http_only`), a module would only add a build unit.

## D_direct_http_only — Why does the MCP client connect to the in-process server directly rather than through a stdio proxy?

The agent registers `http://127.0.0.1:18088/mcp` with its MCP client, which talks to `SwingMCP`
over loopback. There is no process in between.

**Why.** A proxy was built for two moments when the in-process server is not there: the
application restarting mid-session, and the application not running yet when the client starts.
Claude Code covers both (`R_claude_code_http_lifecycle`). A restart is recovered at the next tool
call without the model noticing. A server that was down at startup comes back with one `/mcp`
Reconnect once the application is up. What a proxy costs does not shrink. It is a second artifact
to build, ship, register and keep in version step with the server, plus a manifest check between
the two. And it is a stdio process that can wedge on its own: one expired its own session after
half an hour idle and stayed broken until the client restarted.

**Why not keep the proxy for the down-at-startup case.** Rejected, 2026-09-23. It saves one
Reconnect per session that started before the application, at the whole price above, and in
practice it never worked well. A proxy is cheap to rebuild and the client cheap to re-measure, so
run the recipe in `R_claude_code_http_lifecycle` before reviving one.

**Why the client's silent re-initialize after a restart is safe here.** tiny-mcp-server's own
client deliberately never re-initializes on a lost session (its no-auto-retry decision, in
`tiny-mcp-server/design/decisions.md`), because a replay against a fresh session can land on
state nobody intended. Claude Code does exactly that. It is safe for this server because the ref
map lives on the session. A restarted application starts with an empty one, so a ref the model
held from before the restart is refused as stale rather than addressing a different widget, and
the refusal tells the model to snapshot again.

**What the model sees while the application is down.** The client's own `ECONNREFUSED` error,
not a message of ours. The server instructions already say the application must be running and
to ask the human to restart it; that is the recovery either way.

## D_single_session — Why does the server serve one agent at a time, and why does a newcomer win?

`SwingMCP` admits one session, and a fresh `initialize` evicts the existing one rather than being
refused. The displaced client's next call returns a message saying it was superseded.

**Why one at a time.** The EDT is single-threaded and the application is shared state. Two agents
would interleave clicks and take snapshots against each other's half-finished mutations, and
`toolLock` (`D_tool_call_wide_lock`) cannot help — it serializes individual calls, but it cannot
make two callers agree about what the UI currently is. Refs make it worse rather than better:
each session has its own ref map, and a mutation clears only the map of the session that made it,
so one agent's click can rebuild the panel another agent holds refs into while that session's map
stays untouched. It never receives the stale-ref error that exists to tell it to re-orient.
Automated testing against such a server is also meaningless, since no run is reproducible.

**Why the newcomer wins rather than being told to wait.** The client is a developer's editor, and
an editor that exits without closing its session would otherwise hold the slot until the idle
timeout — locking the developer out of their own running application for up to half an hour, with
no obvious remedy but restarting it. There is at most one *intended* client at any moment, so the
newest connection is the best available guess at which one that is.

**Why not queue the second client until the slot frees.** Rejected — it adds a waiting state for
a situation that does not legitimately arise, and an agent queued behind a wedged session waits
forever with no signal.

**Why the displaced client is told it was superseded.** A bare "session not found" cannot be
distinguished from "the server restarted" or "my id was always wrong", and each of those calls
for a different recovery. Only the server knows which happened, so only the server can say.

**Why the message deliberately withholds the new session's id.** Rejected road: naming it would
let the displaced client reattach, producing two clients on one session with arbitrarily
interleaved state changes. In an agent-driven setup that manifests as randomly missing or
duplicated tool calls — hours of debugging the wrong layer. The cure is worse than the disease.

## D_no_batch_fill_form — Why is there no batch form-filling tool?

Filling an N-field form costs `2N+1` calls — snapshot, set, snapshot, set, … , click. A
`swing_fill_form` taking an ordered list of `{ref, value}` pairs would collapse that to three. It
was designed in full and deliberately not built.

**The design that was explored.** Validate every ref first and reject the whole batch if any
pre-check fails; execute sequentially on the EDT with a short inter-field delay so listeners can
run; re-check visibility and enablement before each write and bail immediately on failure;
dispatch `set_text` or `set_value` by component type; fire-and-forget, refs invalidated on return.

**Why deferred — three compounding problems.**

- **Fire-and-forget is architecturally hostile to multi-step operations.**
  `D_fire_and_forget_dispatch` is sound for a single mutation, where the window between dispatch
  and completion is one EDT turn and effectively atomic from the client's view. Across a batch
  that window spans the whole run, and observable state can change and revert inside it without
  the client ever seeing it. This is not a `fill_form` bug; it is a structural consequence of the
  execution model.
- **Bail-outs are invisible.** When the obstacle persists — a modal dialog that stays open — the
  next snapshot reveals it and the agent can infer the batch stopped. When it is *transient* — a
  field briefly read-only during an async fetch, a spinner overlay for 200 ms, a validation
  listener momentarily disabling a dependent field — the batch bails, the transient clears, and
  the agent sees a half-filled form with every field editable and no evidence anything went
  wrong. It cannot distinguish "completed" from "bailed" from "still running".
- **The bootstrap paradox.** The agent cannot tell in advance whether a form is simple enough to
  batch — inter-field dependencies, async validation, conditional visibility are all invisible
  until you interact. Discovering it costs the exploration the tool existed to skip.

**Why not make it synchronous so it can report a real result.** Rejected — if a field's setter
triggers a modal dialog, the EDT blocks, the server blocks, and the client blocks, and nobody is
left to dismiss the dialog. That is exactly the deadlock `D_fire_and_forget_dispatch` was created
to prevent.

**Why not a synchronous batch with a per-field timeout.** Rejected — it does not close the
window. If field K's setter opens a modal, the timeout fires on the server thread while the EDT
is still blocked inside the setter's own event loop; a server thread cannot cancel an in-flight
EDT task.

**Why not keep refs valid after `set_text` / `set_value` and clear only after `click` / `close`,**
distinguishing "lightweight" from "structural" mutations. Rejected before reaching design — any
mutation can trigger structural change through a listener. A selection change can rebuild a
master-detail form; a text change can reveal or hide dependent fields. The distinction does not
exist in Swing's event model.

**Why not store the batch's terminal state in the session and surface it in the next snapshot**
(`"fill_form: completed 3/5, bailed at field 4: modal dialog"`). Viable in principle, and the
right entry point if this is ever revisited. Deferred alongside the tool: it needs context state,
snapshot integration and lifecycle management, which is a lot of machinery for a tool whose
applicability is already narrow.

**Why not accept the ambiguity and document the limitations.** Rejected as insufficient —
documentation helps when a failure is observable. Invisible bail-outs mean the agent cannot detect
the failure at all, let alone recover from it.

**Revisit trigger.** Telemetry showing form-filling as a dominant share of token spend across
real sessions. Start from the outcome-reporting mechanism above, not from another pass at the
pure fire-and-forget shape.

## D_java11_floor — Why does the shipped code target Java 11 rather than 17?

`options.release = 11` on every compile task, tests included; only tiny-mcp-server's
`testOfficial` compiles at 17. The published jars are class-file version 55, so they load into a
Java 11 JVM.

**Why, at all.** This drops into a Swing application built years ago, and those run on old JVMs.
In a corporate environment the JVM is frequently not the developer's to choose: it is what the
image ships with, and a newer one may not be installable at all — so "ask them to upgrade" is not
a workaround, it is the end of the evaluation. The failure is also total rather than partial: a
class-file version error is a hard load failure at startup, not a degraded feature. The reach of
the artifact is the whole argument, and a language floor is the cheapest way to buy it.

**Why 11 and not 8.** `var`, `List.of`/`List.copyOf` and `String.isBlank`/`repeat`/`strip` are
used throughout the shipped code, and `TinyMCPClient` is built on `java.net.http.HttpClient`. The
client ships in tiny-mcp-server's main source set although only tests use it. A `--release 8`
compile of everything shipped fails with 69 errors (2026-09-23): nearly all mechanical, with the
client the one real blocker. Java 8 would mean relocating or rewriting the client and every such
call. That is real work, for a JVM generation nobody has yet shown the migration target needs.

**Why not 17, keeping records and `sealed`.** Rejected on what it costs to reverse. The nine
records and one sealed interface were about forty lines of hand-written boilerplate to undo
(only `ToolDescriptor` carried a real equality contract, and it is now hand-written); an
application stuck on 11 cannot undo its JVM. The asymmetry decides it.

**Why `--release` rather than `sourceCompatibility` / `targetCompatibility`.** Only `--release`
compiles against that JDK's API signatures. `targetCompatibility` alone emits version-55 class
files that happily call a Java 17 method — the build stays green and the failure lands on the
customer as `NoSuchMethodError`. The old setting was 17 and had therefore never verified anything.

**Why the tests are held to the floor too, and run on an 11 VM.** `--release 11` proves no
post-11 API is *called*; it cannot prove the jars load and run on an 11 VM, which a v61 class
pulled in by shadowJar or an API reached reflectively would break. So `testJava11` re-runs the
whole headless suite on a real Java 11 launcher, and CI builds on 11 as well as 17/21/24. That
cost JUnit 6 (17-only) for JUnit 5.14.4, and Gradle 9.5 for 8.14.3 — the last Gradle that runs
on 11, which in turn means the build no longer runs on Java 25.

The leg paid for itself twice on the first run: `R_jslider_actions_since_17`, and a server bug
where a rejection path answered without draining the request body, so `com.sun.net.httpserver`
closed a connection the client had already pooled. Java 11's `HttpClient` does not retry a POST
that dies that way (JDK 12+ does), so only the 11 leg ever saw it.

**Why the official MCP SDK is confined to one source set.** It has no Java 11 build — every
release from 0.7.0 to 2.0.1 is class-file 61 — so it cannot be the thing that drives a suite
which must also run on 11. It stays in `tiny-mcp-server/src/testOfficial`, compiled at 17 and
skipped entirely on an older build JDK. Everything above that module drives the server through
this repository's own `TinyMCPClient`, which is the right layer anyway. Why the SDK is worth
keeping at all is that module's own decision, in `tiny-mcp-server/design/decisions.md`.
