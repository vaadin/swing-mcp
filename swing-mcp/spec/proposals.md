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

**Date:** 2026-04-13
**Status:** Proposed, grilled
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
   enumerate tools exist for *value collections* (often database-backed,
   potentially large, unbounded by UI layout). They do not exist for *UI
   structure* (layout-bounded by human factors, already visible in the
   snapshot). This test auto-excludes JTabbedPane tabs, JSplitPane panes,
   JMenu children, etc., from ever justifying an enumerate tool. If a future
   counter-example shows up (e.g. a legacy Swing app misusing a structural
   container as data storage), document the use case and revisit.

5. **JTabbedPane tabs are already in the snapshot.** UC-002 SC-2 + the
   `PAGE_TAB` semantic role mean every tab renders as a direct child of the
   `page_tab_list`, with `[selected]` state on the active one. The only
   enumeration gaps are (a) explicit tab index and (b) disabled-tab indicator.
   Both can be closed with a small snapshot rendering change — cheaper than a
   new tool.

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
- **`[disabled]` state on disabled tabs** is believed to emit already, because
  `SwingUtils.isEffectivelyEnabled` reads `AccessibleStateSet.ENABLED` (not
  `Component.isEnabled()`), and `JTabbedPane$Page` correctly reflects
  `isEnabledAt(titleIndex)` in its state set. **To be confirmed by a dedicated
  snapshot test in Wave A.1** — if the assumption fails, root-cause before
  proceeding.

Example rendering after the change:

```
- page_tab_list "General" [ref=1] actions: single-selection
    - page_tab 0 "General" [selected]
      - push_button "InTab1" [ref=2] actions: click
    - page_tab 1 "Advanced"
    - page_tab 2 "Legacy" [disabled]
```

#### Semantics note: `[disabled]` on a `page_tab` is narrower than elsewhere

Empirically verified (2026-04-13 probe):

- On a button / other interactive component, `[disabled]` means the component
  is inert — clicks do nothing, actions are blocked.
- On a `page_tab`, `[disabled]` means **only the tab header is
  non-navigable.** Programmatic selection via `JTabbedPane.setSelectedIndex`
  works; the tab's content, when displayed, is fully live (child buttons fire
  their action listeners normally). UC-015 BR-14 is a **policy** that refuses
  such programmatic selection from our tool, not a Swing-level barrier.

Worth a design note in UC-002 (near SC-2) so future readers don't assume
"disabled tab" means the subtree is inert.

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
| `JTable` | `get_items` | `get_item_count` | `get_selection` (row mode) | `set_selection` (row mode) |
| `JTabbedPane` | *(inline in snapshot — not advertised)* | *(inline in snapshot)* | `get_selection` | `set_selection` |

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

1. **Phase A.1 — verify free behavior.** Add a headless snapshot test
   asserting `[disabled]` already emits on a disabled `page_tab` line.
   Confirms the assumption underpinning §"Enhance snapshot rendering" above.
   If it fails, investigate before proceeding.
2. **Phase A.2 — snapshot enhancement.** Add the `role == PAGE_TAB` branch to
   `SnapshotNode.calculateSelfLine()` that emits the tab index. Add tests
   covering: enabled tab, selected tab, disabled tab, tab with nested
   content, multi-tab pane.
3. **Phase A.3 — spec.**
   - **UC-002**: document the new `- page_tab N "title"` rendering (example
     + the `[disabled]` narrower-semantics note near SC-2).
   - **UC-017**: remove JTabbedPane from the component matrix; remove
     BR-11's JTabbedPane clause; remove BR-12 entirely; remove the
     JTabbedPane algorithm branch and the `JTabbedPane.isEnabledAt` line
     from the accessibility API list; update tool description.
   - **UC-018**: remove JTabbedPane from the component matrix; update tool
     description.
   - **architecture.md § Selection Action Groups**: add a JTabbedPane
     footnote covering (a) enumerate-not-advertised — tabs are in the
     snapshot — and (b) the pre-existing `clear_selection` asymmetry.
4. **Phase A.4 — implement.**
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
  - Downsized Wave A.2 after realizing `SwingUtils.isEffectivelyEnabled` reads
    `AccessibleStateSet.ENABLED` (not `Component.isEnabled()`), meaning
    `[disabled]` likely already emits on tabs with no new machinery needed.
