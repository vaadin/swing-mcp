# Proposals (Work-in-Progress)

> **Purpose.** Temporary scratchpad for in-flight design ideas that are not yet
> Draft-status use cases. Kept in-tree (rather than in chat) so the reasoning
> survives across conversations and can be grilled / iterated on directly.
>
> **Lifecycle.** When a proposal graduates to an agreed plan, promote it into a
> new or amended use case and remove it from this file. When a proposal is
> rejected, strike it through with a one-line epitaph (what was rejected, why).
> This file should stay small — if it grows large, the refactorings it tracks
> are not shipping fast enough.

---

## P-001: Rename `get_selectable_items` → `get_items`; drop `JTabbedPane` from the enumeration tool

**Date:** 2026-04-13 (re-grilled 2026-04-13 post-`isEffectivelyEnabled` rewrite)
**Status:** Proposed, grilled twice
**Touches:** UC-002, UC-017, UC-018, architecture.md § Selection Action Groups.

### Motivation

1. **`selectable_items` overpromises.** After the 2026-04-13 amendment to UC-017
   BR-03, the tool works on any `JTable` regardless of selection mode —
   including column-selection, cell-selection, and no-selection modes where
   nothing is selectable via `swing_set_selection`. The name no longer tells
   the truth about what the tool does.

2. **Accessibility terminology leaks.** "Selectable items" is
   `AccessibleSelection`'s vocabulary. We have already departed from the
   accessibility model for `JTable` (rows, not cells — UC-017 BR-09). If we are
   departing anyway, the tool name should reflect how the AI and users actually
   think about the components, not how the accessibility API classifies them.

3. **Vaadin / real-world mental model.** In Vaadin and in 99% of real Swing
   code, a table is "a list of items with column projections", not "a 2D grid
   of cells". `TableModel` is almost always backed by `List<T>` where rows are
   records and columns are fields. The 2D-grid use case (spreadsheet-like,
   heterogeneous cell editors) is rare. The API should optimize for the 99%.

4. **`JTabbedPane` is a force-fit in an enumeration tool.** Three tells:
   paging is pure overhead (tabs are UX-bounded, essentially never >20); the
   `enabled: false` field is JTabbedPane-only and a schema wart; and — the
   deeper reason — **tabs are UI structure, not data.**

   **Deciding principle for enumerate tools (worth stating generally):**
   enumerate tools exist for collections whose **cardinality is bounded by
   data, not by UI layout**. JList items, JComboBox items, and JTable rows
   are bounded by data (often database-backed, potentially thousands).
   JTabbedPane tabs, JSplitPane panes, JMenu children, JToolBar items are
   bounded by UI layout (human-factors caps: essentially never >20, because
   a user can't navigate more). The bounded-by-what test is the load-bearing
   property — "UI structure vs. value" invites category arguments, but
   "what caps the count" is crisp. If a future counter-example shows up
   (e.g. a legacy Swing app using a JMenu as a recent-files list with
   hundreds of dynamic items), document the use case and revisit.

5. **JTabbedPane tabs are already in the snapshot.** UC-002 SC-2 + the
   `PAGE_TAB` semantic role mean every tab renders as a direct child of the
   `page_tab_list`, with `[selected]` state on the active one. As of
   commit c5feda3, disabled tabs also render with `[disabled]` (see
   §"Enhance snapshot rendering" for the required carveout). The **only
   remaining enumeration gap is explicit tab index**, closeable with a
   small snapshot rendering change — cheaper than a new tool.

### Proposal

#### Rename the enumeration tool

- `swing_get_selectable_items` → **`swing_get_items`**
- `swing_get_selectable_items_count` → **`swing_get_item_count`**

Algorithm, index space, paging semantics, and JSON shape are unchanged. Only
the names change.

#### Drop `JTabbedPane` from the enumeration tool

After this refactor, `get_items` / `get_item_count` support `JList`,
`JComboBox`, and `JTable` only. `JTabbedPane` is not a data collection — its
tabs are UI structure and are already visible in the snapshot.

#### Enhance snapshot rendering for `JTabbedPane`

- **Tab index inline.** Render each tab as `- page_tab N "title"` where `N` is
  the tab index. Implementation: one branch in
  `SnapshotNode.calculateSelfLine()` keyed on `role == PAGE_TAB`, emitting
  `ctx.getAccessibleIndexInParent()` (verified equivalent to tab index by
  UC-014 probe). Mirrors the JTable row rendering pattern (`- row N: ...`).
- **`[disabled]` state on disabled tabs** is already wired up as of commit
  c5feda3. `AccessiblePage` does **not** omit `ENABLED` from its state set
  when `JTabbedPane.setEnabledAt(i, false)` is called (a Swing quirk — the
  opposite of what the first grill pass assumed), so `SwingUtils.isEffectivelyEnabled`
  carries an explicit `parent instanceof JTabbedPane` carveout that consults
  `tp.isEnabledAt(idx)`. The contract is locked in by the snapshot tests
  added in commit 043a71b (`SwingSnapshotToolTest.tabbedPane_tabDisabledViaSetEnabledAt_marksOnlyThatTabDisabled`
  and `tabbedPane_buttonOnDisabledTab_isStillClickable`). No further work
  needed in this wave beyond tab-index emission.

Example rendering after the change:

```
- page_tab_list "General" [ref=1] actions: single-selection
    - page_tab 0 "General" [selected]
      - push_button "InTab1" [ref=2] actions: click
    - page_tab 1 "Advanced"
    - page_tab 2 "Legacy" [disabled]
```

Weird-but-legal edge case — a tab that is both selected and disabled (if
`setEnabledAt(idx, false)` is called on the currently-selected tab):

```
- page_tab_list "Only" [ref=1] actions: single-selection
    - page_tab 0 "Only" [disabled, selected]
      - push_button "OnDisabled" [ref=2] actions: click
```

The child button has **no** `[disabled]` marker — per issue below, the
snapshot mirrors Swing's non-propagating `setEnabled` semantics exactly.

#### Semantics note: `[disabled]` never propagates to children, by design

After the `isEffectivelyEnabled` rewrite in commit c02f86b, the snapshot
mirrors Swing's non-propagating disabled semantics **uniformly across all
containers**: on a disabled `JPanel`, `JScrollPane`, `JToolBar`, or
`JTabbedPane` tab, the container is marked `[disabled]` but its children
are not. This is not a JTabbedPane-specific quirk — it is Swing's
documented, by-design behavior for `Component.setEnabled(false)`
(JDK-4177727, closed as won't-fix). A button inside any disabled container
is still mechanically clickable, and our `swing_click` will accept it. See
[`feedback_swing_fidelity.md`](../../../.claude/projects/-home-mavi-work-swingai-swing-mcp/memory/feedback_swing_fidelity.md)
— MCP exposes what Swing allows, not what would be cleaner.

Practical consequences specific to `page_tab`:

- **Tab header non-navigable.** A user cannot click a `[disabled]` tab
  header to switch to it. This is what the `[disabled]` marker on the
  `page_tab` line reports.
- **Programmatic selection still works at the Swing level** —
  `JTabbedPane.setSelectedIndex(i)` happily switches to a disabled tab.
  UC-015 BR-14 is a **policy** in our tool that refuses such selection,
  not a Swing-level barrier.
- **Children of a disabled tab are fully live when displayed** — if the
  disabled tab is the selected one (legal but odd), its child buttons fire
  their action listeners normally. The snapshot reflects this faithfully
  by not propagating `[disabled]`.

Worth a design note in UC-002 (near SC-2) so future readers don't assume
"disabled tab" means the subtree is inert. Pre-c5feda3 the snapshot was
silently *wrong* about disabled tabs (missing the `[disabled]` marker
entirely); BR-14 was the only honest signal. Post-c5feda3 snapshot and
BR-14 agree — an AI reading the snapshot can now predict BR-14's refusal
without out-of-band policy knowledge. This tightening is worth calling out
in the architecture.md footnote (see Phase A.3).

#### Keep the selection family polymorphic for `JTabbedPane`

`JTabbedPane` stays in the `single-selection` group for snapshot labeling.
`swing_get_selection` and `swing_set_selection` keep working. Only the
**enumerate** path moves — from "go through `get_selectable_items`" to
"read the snapshot directly". No new `swing_switch_tab` tool; no `tabs`
action label.

**Pre-existing asymmetry worth surfacing in the same doc edit:**
`swing_clear_selection` on a JTabbedPane always errors ("This component does
not allow the selection to be empty" — `SwingSetSelectionTool.java:77-79`).
Not fixed by this refactor — orthogonal policy question — but since we're
touching the Selection Action Groups section of architecture.md anyway, one
JTabbedPane caveat block covering both this asymmetry and the
enumerate-not-advertised footnote keeps the doc honest in a single edit.

### Resulting tool matrix

| Component | Enumerate | Count | Read selection | Write selection |
|---|---|---|---|---|
| `JList` | `get_items` | `get_item_count` | `get_selection` | `set_selection` |
| `JComboBox` | `get_items` | `get_item_count` | `get_selection` | `set_selection` |
| `JTable` | `get_items` [^jt] | `get_item_count` [^jt] | `get_selection` (row mode) | `set_selection` (row mode) |
| `JTabbedPane` | *(inline in snapshot — not advertised)* | *(inline in snapshot)* | `get_selection` | `set_selection` |

[^jt]: `get_items` / `get_item_count` accept **any** `JTable`, regardless of
    selection mode (row / column / cell / none) — per the
    `SwingUtils.supportsGetSelectableItems` gate added in commit 5296004.
    Only the selection read/write columns require row mode.

### Rejected alternatives

- **`get_rows` / `get_row_count`.** Makes JList, JComboBox, and (during the
  grilling, before we dropped it) JTabbedPane rent JTable's vocabulary.
  "Items" is Swing's own term for JComboBox (`getItemCount` / `getItemAt`) and
  idiomatic for JList. "Rows" would also collide with
  `AccessibleTable.getAccessibleRowCount()` and `buildTableRowText()` — terms
  that currently have crisp JTable-specific meanings in the spec. Pair-name
  English is awkward (`get_rows_count` vs `get_row_count` number
  inconsistency).

- **`swing_get_tabs` as a dedicated JTabbedPane enumerate tool.** Rejected
  because the snapshot already carries every tab (UC-002 SC-2 + PAGE_TAB
  semantic role + no truncation on tabs). The only pieces a dedicated tool
  would add — explicit index and disabled state — are both cheaper delivered
  inline in the snapshot rendering. Also fails the structure-vs-value
  principle above.

- **`swing_select_tab(ref, title)` selecting by title.** Briefly considered
  for ergonomics. Rejected (for now) because `JTabbedPane` allows duplicate
  tab titles, making selection-by-title ambiguous. If a future use case
  justifies it under an unambiguous-title assumption, add it as a separate
  proposal.

- **Option X — keep `JTabbedPane` in `get_items`.** Would leave the
  `enabled: false` JTabbedPane-only schema wart. With the new snapshot
  `[disabled]` marker, that wart would be paying for the same information
  twice (snapshot + tool response). Dropping JTabbedPane from `get_items` is
  the clean landing.

### Phasing

Two independently shippable waves. Wave B does not start until Wave A merges.

#### Wave A — snapshot enhancement + drop JTabbedPane from enumeration

Additive (snapshot rendering) + subtractive (JTabbedPane leaves two tools). No
renames.

1. **Phase A.1 — verify free behavior.** ~~Add a headless snapshot test
   asserting `[disabled]` already emits on a disabled `page_tab` line.~~
   **DONE — and the "free behavior" assumption failed.** `AccessiblePage`
   does not omit `ENABLED`; an explicit `JTabbedPane.isEnabledAt` carveout
   in `SwingUtils.isEffectivelyEnabled` was required (commit c5feda3). The
   two snapshot tests in commit 043a71b lock the contract: a disabled tab
   emits `[disabled]`, and a child button on a disabled-but-selected tab
   does NOT inherit `[disabled]` (Swing fidelity — see Semantics note).
2. **Phase A.2 — spec.** Spec the two upcoming code changes in full before
   any code is written (per CLAUDE.md § "Ways of Working" — specs first).
   - **UC-002**: document the new `- page_tab N "title"` rendering (example
     + the Swing-fidelity-everywhere `[disabled]` semantics note near SC-2).
   - **UC-017**: remove JTabbedPane from the component matrix; remove
     BR-11's JTabbedPane clause; remove BR-12 entirely; remove the
     JTabbedPane algorithm branch and the `JTabbedPane.isEnabledAt` line
     from the accessibility API list; update tool description.
   - **UC-018**: remove JTabbedPane from the component matrix; update tool
     description.
   - **architecture.md § Selection Action Groups**: add a JTabbedPane
     footnote covering (a) enumerate-not-advertised — tabs are in the
     snapshot — (b) the pre-existing `clear_selection` asymmetry, and
     (c) the snapshot-now-predicts-BR-14 tightening: post-c5feda3 the
     `[disabled]` marker on a `page_tab` accurately foreshadows UC-015
     BR-14's refusal of disabled-tab selection, so an AI reading the
     snapshot no longer needs out-of-band policy knowledge.
3. **Phase A.3 — snapshot enhancement (code).** Implement the rendering
   spec'd in A.2 (UC-002). Add the `role == PAGE_TAB` branch to
   `SnapshotNode.calculateSelfLine()` that emits
   `ctx.getAccessibleIndexInParent()` as `- page_tab N "title"` (mirrors
   the JTable `- row N: ...` pattern). Add tests covering: enabled tab,
   selected tab, tab with nested content, multi-tab pane. Also update the
   two existing tests from commit 043a71b (which currently assert
   `- page_tab "Tab1"`) to assert the new `- page_tab 0 "Tab1"` form —
   verify `[disabled, selected]` ordering stays stable.
4. **Phase A.4 — drop JTabbedPane from enumerate (code).** Implement the
   removal spec'd in A.2 (UC-017, UC-018).
   - Update `SwingUtils.supportsGetSelectableItems` and
     `SwingUtils.getSelectableItemsCount` to exclude JTabbedPane.
   - Remove the `instanceof JTabbedPane` branch from
     `SwingGetSelectableItemsTool`.
   - Migrate JTabbedPane test methods: delete JTabbedPane-positive tests
     from `SwingGetSelectableItemsTest` / `SwingGetSelectableItemsCountTest`
     (and their screen-test equivalents), replace with positive assertions
     that JTabbedPane now returns the unsupported-component MCP error
     (regression guard).
5. **Phase A.5 — wrap up.** Build, verify, commit, ship.

#### Wave B — rename `selectable_items` → `items`

Pure rename. No semantic change. Easier to review because Wave A already
removed the JTabbedPane-specific edge cases from these tools.

6. **Phase B.1 — spec.**
   - Rename UC-017 file (`use-case-017-swing-get-selectable-items.md` →
     `use-case-017-swing-get-items.md`); rename tool inside.
   - Rename UC-018 file analogously.
   - Search-and-replace `selectable_items` across UC-002, UC-014, UC-015,
     UC-016, UC-017, UC-018, UC-020, UC-021, architecture.md, verification.md.
7. **Phase B.2 — implement.**
   - `SwingGetSelectableItemsTool` → `SwingGetItemsTool`.
   - `SwingGetSelectableItemsCountTool` → `SwingGetItemCountTool`.
   - Corresponding test-class renames.
   - Tool IDs in the registry.
   - `SwingUtils.supportsGetSelectableItems` → `SwingUtils.supportsGetItems`;
     `SwingUtils.getSelectableItemsCount` → `SwingUtils.getItemCount`.
8. **Phase B.3 — sweep.** Verify no stale `selectable_items` or
   `GetSelectableItems` tokens remain in spec or code.

### Open questions

*(None remaining — all four original open questions were specific to the
discarded `swing_get_tabs` tool and were resolved when it was removed from the
proposal.)*

### Grilling log

- **2026-04-13** — full grill pass.
  - Rejected `get_rows` in favor of `get_items` (rows is one component's vocabulary).
  - Rejected `swing_get_tabs` after observing the snapshot already carries tab data.
  - Landed on structure-vs-value as the litmus test for enumerate tools.
  - Verified empirically (probe 2026-04-13) that disabled-tab contents are live;
    established that `[disabled]` on `page_tab` has narrower semantics.
  - ~~Downsized Wave A.2 after realizing `SwingUtils.isEffectivelyEnabled` reads
    `AccessibleStateSet.ENABLED` (not `Component.isEnabled()`), meaning
    `[disabled]` likely already emits on tabs with no new machinery needed.~~
    **Struck through in 2026-04-13 re-grill** — assumption was wrong, see below.

- **2026-04-13 (re-grill, post-`isEffectivelyEnabled` rewrite)** — verified the
  proposal against the now-landed c02f86b / c5feda3 / 043a71b commits.
  - **First-grill assumption corrected.** `AccessiblePage` does NOT omit
    `ENABLED` when `setEnabledAt(i, false)` is called; an explicit
    `JTabbedPane.isEnabledAt` carveout in `SwingUtils.isEffectivelyEnabled`
    was required (commit c5feda3, merged before this re-grill). Before that
    fix, the snapshot was silently *wrong* about disabled tabs — not just
    "missing a nice-to-have marker". The JTabbedPane-drop is downstream of
    a correctness fix, not just aesthetic cleanup.
  - **Phase A.1 re-classified as done** (commit 043a71b tests) and A.2
    narrowed to tab-index emission only.
  - **Reframed the `[disabled]` semantics note** from "JTabbedPane-specific
    narrower semantics" to "Swing-fidelity everywhere": after c02f86b,
    `isEffectivelyEnabled` no longer walks the parent chain for real
    Components, so disabled containers never mark their children — uniform
    across `JPanel`, `JScrollPane`, `JToolBar`, `JTabbedPane`. Cites memory
    entry `feedback_swing_fidelity.md`.
  - Added `[disabled, selected]` edge-case example (covered by commit
    043a71b test `tabbedPane_buttonOnDisabledTab_isStillClickable`).
  - Surfaced the **snapshot-now-predicts-BR-14** tightening — pre-c5feda3
    the `[disabled]` marker was missing so BR-14's refusal was un-predictable
    from the snapshot alone; post-c5feda3 snapshot and BR-14 agree. Added
    as a third bullet in the A.3 architecture.md footnote task.
  - Sharpened the enumerate-tool principle from "UI structure vs. value" to
    "bounded by data vs. bounded by UI layout" — the load-bearing property
    that avoids category arguments.
  - Added a tool-matrix footnote clarifying JTable's `get_items` enumerate
    gate is *selection-mode-independent* (per `supportsGetSelectableItems`,
    commit 5296004), unlike the selection read/write columns.
  - Flagged tooltip-text-per-tab (`setToolTipTextAt`) as out of scope; a
    dedicated tool is planned separately.
