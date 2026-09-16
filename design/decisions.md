# Decisions

Why this project is the way it is and not otherwise — FAQ-shaped: each entry is a question and
its current answer. Rewrite the answer when it changes; delete the entry when nobody asks any
more. An entry is earned by what it would cost to reverse, or by research the next person would
otherwise redo (cited as its `R_`). Not an entry: a message's wording, a constant's value, the
testing library, a version bump — a comment at the site of the choice, or nothing; nothing about
`design/` itself. A decision that binds one tool only stays in that tool's file under
`swing-mcp/spec/tools/`. Cite by slug, `D_<slug>`, never by position;
`grep '^## D_' design/decisions.md` is the index. The first entry is the ruler: every later one
trims to its length. When you have written an entry, re-read it against the one above, check it
says nothing the doc comments already say, and cut what is left over.

---

## D_accessibility_snapshot — Why drive Swing through the accessibility tree rather than screenshots and robot clicks?

Before this project the only way an LLM agent could operate a Swing app was to screenshot it
and click coordinates through `java.awt.Robot`. `swing_snapshot` walks `javax.accessibility`
instead and returns an indented text tree in which every interactable node carries a stable
`ref`; every other tool takes a `ref` and dispatches through the real listener tree, so a click
is the click the app would have received from a mouse. Why not screenshots and coordinates:
tokens scale with pixels rather than with widget count, and a resize, a look-and-feel change or
a scrolled viewport silently invalidates every coordinate the agent has learned.
`swing_screenshot` survives for the questions that are genuinely about pixels — layout and
rendering — but it is never how a tool is targeted. The cost we carry: a component whose
`AccessibleContext` is absent or wrong is invisible to us, and we inherit whatever Swing's
accessibility implementation gets wrong.

## D_mirror_swing_semantics — Why mirror Swing's semantics, including the broken ones?

If a Swing application technically permits an interaction, this server advertises it, however
odd it looks. The concrete case that sets the rule: `setEnabled(false)` on a container does not
disable its children (`R_disabled_not_propagated`), so a button inside a greyed-out panel is
still clickable — and `swing_click` accepts it. Why not propagate `disabled` transitively to
give the model a cleaner mental model: the snapshot would then lie about what the mutation tools
actually accept, and would hide controls a person could click, which is exactly the failure that
leaves an agent stuck with no way forward. Applications in the wild are old and strange; the
layer's job is to reflect the real interaction surface, not an idealized one. Where Swing's own
state set disagrees with Swing's own API — a disabled tab that still reports `ENABLED` — the API
wins, because that is what the user experiences. The two deliberate carveouts from this rule,
`D_jmenu_not_clickable` and `D_label_not_readable`, each argue their own case for leaving the
agent strictly no less capable than a person.

## D_interactable_windows_only — Why do tools see only the windows a user could interact with right now?

`getConsideredComponents()` returns the topmost modal dialog if one is showing, and otherwise
every visible window minus redundant heavyweight popup containers. This is the contract every
tool inherits, not a default to refine: a ref addresses something the user could click, or it
does not exist. Why not return the modal's owner chain as additional roots: a mutation against a
blocked window either no-ops silently while the framework swallows the event, or bypasses the
modal's intent through a setter that skips dispatch — both worse than never offering the ref.
Why not an `include_blocked_windows` flag: a flag with two modes where only one is ever correct
is bureaucracy, and it reintroduces the footgun. Why not return everything and let the model
infer what is blocked from a `[modal]` state: that inverts the contract from "what you can do"
to "what the framework knows", and pushes a blocked-ref check into every tool. The planning
information that this withholds — what am I returning to — comes back as metadata in
`D_modal_stack_header`.

## D_fire_and_forget_dispatch — Why does a mutation tool return before its action has run?

A mutation validates on the EDT inside `runInEDT()` — ref lookup, capability, enabled — then
posts the action with `invokeLater` and returns immediately; the model observes the outcome by
taking a snapshot. The reason is modal dialogs: if an action listener opens one, the EDT enters
a secondary event loop, and an `invokeAndWait` from the HTTP thread would block until a human
dismisses a dialog that only the agent was ever going to dismiss. The secondary loop still drains
`invokeLater`, so read tools keep working throughout. Why not `invokeAndWait` with a timeout and
a fallback: the HTTP thread still sits blocked for the timeout on every dialog-opening mutation,
which is the common case rather than the exception. Why not report `doAccessibleAction()`'s
boolean: reading it requires the synchronous path this rules out. An earlier design polled for
the outcome after dispatching (`swing_close` checked at 100/200/700 ms); it was replaced because
a poll answers "did it close" for one tool while every other mutation still needed the general
rule, and a snapshot answers it for all of them. What we give up: the tool's return says what was
dispatched, never what the app did — which is why `D_dispatched_echo` words it carefully.

## D_tool_call_wide_lock — Why does `toolLock` span the whole tool call rather than the EDT turn?

The wrapper takes a server-wide `ReentrantLock` on entry and holds it through `runInEDT()` and
the `invokeLater` dispatch. The instinct is that the EDT already serializes everything, so no
lock is needed — but under `D_fire_and_forget_dispatch` `runInEDT()` returns once validation is
done and the action is merely *queued*. In that window a second HTTP thread can enter, replace
the ref map, and queue a conflicting mutation ahead of the first one's action. The lock closes
exactly that gap. Why `ReentrantLock` and not `synchronized`: inside a Swing lambda it stops
being obvious which monitor a `synchronized` block holds, while explicit lock/unlock calls show
the scope at both ends. Why one server-wide lock and not one per tool: concurrent calls to
*different* tools interleave ref-map writes just as badly, and there is only ever one client
(`D_single_session`), so cross-tool concurrency buys nothing worth the reasoning.

## D_dispatched_echo — Why does a successful mutation echo `Dispatched …` rather than returning nothing?

Two words in `AbstractSwingTool.echo` are load-bearing. The verb is `Dispatched` because under
`D_fire_and_forget_dispatch` nothing has run yet — `Clicked ref=4` would claim both that the
action completed and that the UI changed, and a listener can veto or revert either. The trailing
`— call swing_snapshot to verify the outcome` is there because `Posted click on ref=4` tested as
reading like a success confirmation, and clients then skipped the verifying snapshot. Why not
empty content, which is what this replaced: most models read "no content" as a weaker signal than
"some content" and take a redundant snapshot just to confirm the call landed. Why not bundle a
snapshot into the response: snapshots are expensive, the model does not always want one, and it
would couple every mutation's response size to the tree size. Why not structured JSON: plain text
tokenizes cheaper and reads in logs. Why one shape for every mutation rather than a verb per
tool: a uniform echo makes a missing or malformed one obvious in a log, which is why the helpers
are `final` on the base class and no tool writes its own string.

## D_role_in_snapshot_only — Why does the snapshot name an accessibility role but everything else name a Swing class?

Every snapshot line leads with `JClass (role)`, emitted unconditionally even when it reads as
tautology (`JButton (push_button)`). Everywhere a person or a model reads prose — tool
descriptions, parameter docs, error messages — components are named by Swing class only. Why
emit the role even when redundant: dropping it for the tautological cases would make the model
reason about the *absence* of a parenthetical on every line, when the whole point is that an
unusual role should catch the eye. Why not use roles in prose too: class names are far more
familiar to a language model than accessibility vocabulary, and carrying both everywhere is
clutter without signal. A non-load-bearing bonus: Swing roles map instinctively onto ARIA roles,
so once an app is migrated, the same reasoning transfers to a browser-driving tool without a
translation layer.

## D_quoted_slot_sanitizing — Why is the name slot uncapped while the description is capped, and why sanitize both?

Every quoted slot passes through one sanitizer: whitespace runs collapse to a single space
(including U+0085, U+2028 and U+2029, which Java's `\s` misses), embedded `"` is escaped, and a
blank result becomes null. The name is then emitted in full and the description is capped at 120
characters. The asymmetry is deliberate: a name is *identity* — it answers "which button is
this", and a truncated 500-character warning label defeats the reason for surfacing it — while a
description is *advice* the model may read and never needs to act on, so bounding it is cheap
insurance against a pathological tooltip. Sanitizing is not optional because every slot reads
user-controlled data, and one `JLabel("Line 1\nLine 2")` would break the one-line-per-node
invariant that carries the tree structure. Why not escape backslashes too, for full JSON
escaping: the snapshot is read by models, not parsers, and `C:\\Users\\foo` is uglier for no
gain. Why not replace `"` with `'` or a typographic quote: silent content mutation, after which
the model can no longer pass the value back to a tool. Why not sanitize only when an unsafe
character is present: it doubles the policy surface to save nothing measurable.

## D_inline_value_preview — Why does the snapshot inline a value preview instead of making the model ask?

A node that can report text gets `text="…"` capped at 15 characters; a node with a numeric value
gets `value=N` bare, or `value=<current>/<max>` for a progress bar, where the denominator is the
entire meaning of the widget. Both read through the same helpers the `swing_get_text` and
`swing_get_value` tools use, so a preview and a tool call can never disagree. The driver was
observed chatter: clients were round-tripping a read per field to learn a form's current
contents, which is ten extra calls on a ten-field form, every verification cycle. Why not a
`include_values` flag: either the model always sets it, which is this behaviour with extra
ceremony, or it never does, which is the old behaviour — and the per-call decision is itself the
cost. Why not a bulk getter taking an array of refs: it fixes chattiness but not *discovery*, as
the model still has to decide to ask; it stays open as a later addition for control panels where
the untruncated number matters. The read is wrapped in try/catch and the annotation is simply
omitted if a custom widget throws — one bad component must not take down the whole tree.

## D_password_not_readable — Why can a password field be written but not read?

A `PASSWORD_TEXT`-role accessible never advertises `get_text`, and `swing_get_text` on one
returns a dedicated error naming the rule rather than the generic unsupported-action message.
`set_text` is untouched, because filling a login form with a credential the operator supplied is
a real use case. Reading is not the mirror image of writing: the JDK hands back echo characters
rather than nothing (`R_password_echo_chars`), which is misleading — a model can reasonably read
`••••••` as the field's literal contents — and leaks the password's exact length.
Why gate on the role rather than `instanceof JPasswordField`: the role is Swing's own marker for
"this is secret", so a third-party `SecretField` that adopts it is covered for free. Why not just
document the echo-character behaviour and change nothing: the snapshot would still advertise
`get_text`, luring the model into a call whose result it then has to interpret. Why not return
`""` or null instead of an error: silent degradation is indistinguishable from an empty field
and teaches the model nothing.

## D_label_not_readable — Why does a `LABEL`-role node never advertise `get_text`?

`supportsGetText` returns false for the `LABEL` role regardless of whether `AccessibleText` is
present. Without this, a `JLabel` gains `get_text`, a ref and an inline preview precisely when its
developer happened to wrap the string in `<html>`, because that is what plumbs an
`AccessibleText` surface into it (`R_html_label_accessible_text`). Same role, same component to
any user, different tool surface depending on an invisible authoring choice — a rule the model
cannot predict without trying it. Nothing is lost: the label's content is in the name slot,
uncapped per `D_quoted_slot_sanitizing`. Why not go the other way and give every `JLabel` a text
surface: every decorative `"Name:"` in every dialog would take a ref, inflating the numbering that
the interactive components rely on. Why no dedicated error message, as `D_password_not_readable`
has: that rule is genuinely surprising, this one is visible in the snapshot — the action simply
is not offered. This is a carveout from `D_mirror_swing_semantics`, justified because it removes
a duplicate channel rather than a capability.

## D_jmenu_not_clickable — Why doesn't `JMenu` expose `click`?

`supportsClick` returns null for a `JMenu`, so a menu title carries no `click` and no ref, and an
open menu's `JPopupMenu` is pruned when its invoker is a `JMenu`. A person clicks a menu title to
reveal its items; the model already has those items, because a `JMenu`'s accessible children are
its `JMenuItem`s whether the popup is open or not. So the click buys nothing and costs three
things: an open popup duplicates every item under two paths with two different refs, the
`click menu → snapshot → click item` round trip is strictly worse than clicking the item, and
`doClick()` on a `JMenu` opens the popup but does not close it — an action that stops working
halfway through a sequence. Why not keep `click` and make it toggle through `MenuSelectionManager`:
that fixes the asymmetry and leaves the duplication and the wasted round trip. This is the second
carveout from `D_mirror_swing_semantics`, and the intent holds: the model ends up more capable,
not less. Deferred: an app that populates its menu only in `popupMenuWillBecomeVisible` hides
those items from us, and the answer there is `toggle_popup` with explicit intent, not `click`.

## D_modal_stack_header — Why is a modal's owner chain a header line rather than extra roots?

When a root is a modal dialog whose owner chain contains at least one visible window, the
renderer emits `[modal stack (N, topmost first): Class0 "Name0" / …]` above it. The model's real
question is "what do I return to when I dismiss this", which is exactly what `getOwner()`
answers, and ordering is the load-bearing part. Why metadata rather than rendering the owners as
sibling roots: that would either hand out refs to components `D_interactable_windows_only`
guarantees are unreachable, or invent a second "hidden ref" concept — and it doubles the visible
surface in the ordinary single-modal case. Scope is modal-only, because a non-modal dialog does
not block its owner and "return state" is not a thing that applies to it. Invisible owners are
filtered out, notably the shared hidden frame behind `showMessageDialog(null, …)`; if only the
root survives, no header is emitted, which correctly reads as nothing to return to. The separator
is ` / ` rather than any arrow, because `->` already means the custom-subclass form in the
identity slot and reusing arrow glyphs invites miscategorisation.

## D_desktop_icon_as_itself — Why is a `JDesktopIcon` rendered as itself rather than resolved back to its frame?

An iconified `JInternalFrame` leaves both trees entirely and a `JDesktopIcon` takes its place
(`R_iconified_windows`), and we render that icon as what it is — class `JDesktopIcon`, role
`desktop_icon`, named by a fallback that lands on the frame's title — with its own children
hard-excluded as look-and-feel artifacts. The first design resolved the icon back to the frame
and synthesized an `ICONIFIED` state; it fought the framework at every step, because the resolved
frame has `isShowing() == false`, which trips the visibility exclusion and the `supportsClose`
gate and leaves its children non-interactable. Each of those needed its own bypass. The icon, by
contrast, is a real showing component sitting naturally in the tree, and `desktop_icon` is a
concept a language model recognizes. Why walk the desktop pane's accessible children rather than
`getAllFrames()`: the latter drops non-frame children that real applications add to a desktop
pane. `getAllFrames()` is still consulted afterwards, purely to recover icons that macOS Aqua
hides inside a non-accessible wrapper.

## D_synthetic_iconified_state — Why synthesize `ICONIFIED` for a `JFrame` instead of trusting the state set?

The snapshot derives `[iconified]` from `frame.getExtendedState() & Frame.ICONIFIED`, because
`AccessibleJFrame` never puts `ICONIFIED` in its state set (`R_iconified_windows`) — a JDK
omission that will not be fixed, Swing being frozen. This follows the pattern already set by
`disabled`, which is likewise derived rather than trusted. Why not simply drop `ICONIFIED` from
the displayed states: knowing a window is minimized is exactly what tells the model to restore it
before trying to interact. Restoring clears only that bit —
`setExtendedState(getExtendedState() & ~Frame.ICONIFIED)` — rather than `setState(Frame.NORMAL)`,
which would wipe `MAXIMIZED_BOTH` too and silently un-maximize a window on the way back. It does
not apply to `JInternalFrame`, where there is no frame node left to annotate.

## D_iconified_children_hidden — Why does the snapshot hide the children of an iconified frame?

An iconified `JFrame` renders normally through `printAll()` and keeps every child accessible
(`R_iconified_windows`), so without this the screenshot showed an ordinary-looking window, the
snapshot listed clickable children, and every click silently did nothing. The frame node itself
still renders with `[iconified]` and keeps its window-level actions, its children are neither
ref-assigned nor rendered, and one placeholder line says so and names `swing_restore`. Why not
drop the frame from the considered windows entirely: the model would lose both the knowledge that
the window exists and the means to restore it. Why not render the children without refs: a full
tree the model cannot act on is confusing without being useful. Why not mark them all disabled
with the `!` prefix: `!click` means the component is disabled, not that its window is minimized,
so the model would exhaust several failed clicks before thinking of restoring. The screenshot is
deliberately left alone — it is the raw rendering view, and the snapshot is where semantics live.

## D_no_jtable_cells — Why doesn't `JTable` advertise `get_cells` / `get_cell_count`?

Those two tools are offered on a truncated `JList` or `JTree` only; row access to a `JTable` is
`swing_get_items`, and at runtime the cell tools refuse a table and say so. Two reasons.
`JTable` cells are stamp-painted, so they surface as plain labels with no actions and no reachable
editors (`R_jtable_cells_stamped`) — `get_cells` could never return an actionable ref on a table,
which is the entire point of the tool. And the index spaces would collide: the snapshot speaks in
rows and so do the selection tools, while `get_cells` counts cells row-major, so a model that saw
`… and 45 more rows` and then `cell_count = 150` for the same table would have to track the column
count out of band to reconcile them. Why not translate cell indices to row/column on the fly:
translation surface for a tool that on a table can only ever return non-actionable labels. If bulk
row access beyond the snapshot cap is needed, the answer is row-dumping tools, not resurrected
cell indexing.

## D_shared_tool_manifest — Why do both transports source their identity from a third module?

`swing-mcp-tool-defs` holds the server name, version, instructions, the tool descriptors and the
session-lost message, and both `swing-mcp` and `swing-mcp-proxy` import them. `AbstractSwingTool`
binds a descriptor at construction and makes its name, description and schema final, so a tool
implementation cannot drift from the manifest even by accident. The failure being designed out is
a proxy that advertises a tool the in-process server no longer has, or the same tool with a
different schema — which the model discovers as a call that fails in a way nothing explains. Why
a separate module rather than putting the constants in `swing-mcp`: the proxy must not depend on
`swing-mcp`, because that would pull the entire Swing stack into a standalone JVM that never
opens a window. With one source, the remaining drift surface is only "were both modules rebuilt
together", which is what the proxy's runtime probe catches; a unit test compares `listTools()`
against the manifest so the probe only ever fires on a genuine version mismatch.

## D_single_session — Why does the server serve one agent at a time, and why does a newcomer win?

`SwingMCP` admits one session, and a fresh `initialize` evicts the one before it rather than
being refused. The single-session part is forced by the EDT: two agents driving one Swing
application would interleave clicks and snapshot against each other's half-finished mutations,
and `toolLock` (`D_tool_call_wide_lock`) only serializes calls — it cannot make two callers agree
about what the UI currently is. Refs make it worse rather than better: each session gets its own
ref map, and a mutation clears only the map of the session that made it, so one agent's click can
rebuild the panel another agent is holding refs into while that agent's map stays untouched. It
never gets the stale-ref error that exists to tell it to re-orient.
Why the newcomer wins rather than being told to wait: the client here is a developer's editor,
and an editor that crashes without closing its session would otherwise hold the slot until the
idle timeout, locking the developer out of their own application. There is at most one *intended*
client at any moment, so the newest connection is the best available guess at which one that is.
The displaced client gets a message saying it was superseded rather than a bare "session not
found", because being kicked and talking to a restarted server call for different recoveries.

## D_no_batch_fill_form — Why is there no batch form-filling tool?

Filling an N-field form costs `2N+1` calls, and a `fill_form` taking a list of `{ref, value}`
pairs would collapse it to three. It was designed and not built, for three compounding reasons.
Fire-and-forget (`D_fire_and_forget_dispatch`) is sound for a single mutation, where the gap
between dispatch and completion is one EDT turn; across a batch that gap spans the whole run, and
state can change and revert inside it unobserved. Worse, the bail-out is invisible: when a field
goes briefly read-only during an async validation the batch stops, the transient clears, and the
next snapshot shows a half-filled form with every field editable and no evidence anything went
wrong — the model cannot tell "finished" from "bailed" from "still running". Making it
synchronous to return a real result is not available, because a setter that opens a modal would
then block the EDT, the server and the client at once. And the model cannot tell in advance
whether a form is simple enough to batch without exploring it, which is the cost the tool was
meant to avoid. Revisit if telemetry shows form-filling dominating token spend; the entry point
would be a way to report batch outcome, not another pass at the pure fire-and-forget shape.
