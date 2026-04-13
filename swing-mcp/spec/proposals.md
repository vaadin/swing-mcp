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

## P-001: Rename `get_selectable_items` → `get_items`; split `JTabbedPane` into `get_tabs`

**Date:** 2026-04-13
**Status:** Proposed (not yet broken into use cases)
**Touches:** UC-017, UC-018, UC-002 (snapshot action labels — possibly),
architecture.md § Selection Action Groups.

### Motivation

1. **`selectable_items` overpromises.** After the 2026-04-13 amendment to UC-017
   BR-03, the tool works on any `JTable` regardless of selection mode — including
   column-selection, cell-selection, and no-selection modes where nothing is
   selectable via `swing_set_selection`. The name no longer tells the truth
   about what the tool does.
2. **Accessibility terminology leaks.** "Selectable items" is
   `AccessibleSelection`'s vocabulary. We have already departed from the
   accessibility model for `JTable` (rows, not cells — UC-017 BR-09). If we are
   departing anyway, the tool name should reflect how the AI and users actually
   think about the components, not how the accessibility API classifies them.
3. **Vaadin / real-world mental model.** In Vaadin (and in 99% of real Swing
   code), a table is "a list of items with column projections", not "a 2D grid
   of cells". `TableModel` is almost always backed by `List<T>` where rows are
   records and columns are fields. The 2D-grid use case (spreadsheet-like,
   heterogeneous cell editors) is rare. The API should optimize for the 99%.
4. **`JTabbedPane` is a force-fit in the items API.** Three tells:
   - Paging is pure overhead (tabs are bounded by UX; essentially never >20).
   - The `enabled: false` field is `JTabbedPane`-only — a schema wart on an
     otherwise uniform items response.
   - Tabs are **navigation**, not data. "Enumerate the tabs of this pane" is a
     different query from "page through the rows/items of this data component".

### Proposal

#### Rename the general enumeration tool

- `swing_get_selectable_items` → **`swing_get_items`**
- `swing_get_selectable_items_count` → **`swing_get_item_count`**

The index space, algorithm, paging semantics, and JSON shape are unchanged.
Only the names change.

#### Drop `JTabbedPane` from `get_items`; introduce `swing_get_tabs`

`swing_get_tabs` is unpaged and tab-specific. It returns a bare JSON array —
no wrapping object, since there is no sibling field to coexist with (count is
free from array length; no paging means no `offset`/`length` metadata):

```json
[
  {"index": 0, "title": "General", "selected": true},
  {"index": 1, "title": "Advanced"},
  {"index": 2, "title": "Legacy", "enabled": false}
]
```

Schema notes:

- Root is a bare array — no `tabs` wrapper. The array *is* the response.
- No `totalCount` — array length is the count. Counting is the caller's job
  (it's free).
- No `offset` / `length` parameters — takes only `ref`.
- `title` replaces the generic `name` field (tabs have titles, not names).
- `selected: true` surfaces the active tab inline. Always exactly one tab has
  it; omitted on the rest.
- `enabled: false` retains the absence-means-enabled convention used today for
  `JTabbedPane` in UC-017 BR-12.

#### Keep the selection family polymorphic (minimal split)

`JTabbedPane` stays in the `single-selection` group for snapshot labeling and
continues to work with `swing_get_selection` / `swing_set_selection` /
`swing_clear_selection`. Only the **enumerate** path moves to `get_tabs`.

This avoids introducing a separate `swing_switch_tab` tool and a `tabs` action
label — the maximal carve-out — which would cost two new tools plus a new group
label for marginal conceptual tidiness. Defer the maximal split unless a
concrete pain point surfaces.

#### Resulting tool matrix

| Component | Enumerate | Count | Read selection | Write selection |
|---|---|---|---|---|
| `JList` | `get_items` | `get_item_count` | `get_selection` | `set_selection` |
| `JComboBox` | `get_items` | `get_item_count` | `get_selection` | `set_selection` |
| `JTable` | `get_items` | `get_item_count` | `get_selection` (row mode) | `set_selection` (row mode) |
| `JTabbedPane` | `get_tabs` | *(n/a — array length)* | `get_selection` | `set_selection` |

### Rejected alternative: `get_rows` / `get_row_count`

Considered and rejected. Main arguments:

- **"Rows" is one component's vocabulary.** `JList` has items, `JComboBox` has
  options, `JTabbedPane` has tabs, `JTable` has rows. Adopting "rows" makes
  three of four components rent a fourth's noun. "Items" is natively at home on
  `JList` and `JComboBox` (Swing's own `getItemCount`/`getItemAt`), reasonable
  on `JTabbedPane` (once the tabs split happens, this is moot), and a mild but
  tolerable stretch on `JTable` rows.
- **Collision with `AccessibleTable` row semantics.** `buildTableRowText`,
  `getAccessibleRowCount`, and "row index" remain crisp JTable-specific terms
  in the spec. Reusing "row" as the generic noun would blur a term that
  currently has a precise meaning.
- **Pair name reads poorly.** `get_rows_count` is awkward English;
  `get_row_count` (singular) would be consistent with natural phrasing but
  diverges in number from `get_rows` (plural), creating pair inconsistency.
- **Preserves `tabular`/`get_rows` as a future namespace.** A hypothetical
  future JTable-specific, structured-per-cell-ref row tool would legitimately
  own the name `get_rows`. Burning it on the general enumerator forecloses that
  option.

### Phasing

Split into two independently shippable waves. Each wave merges and ships on its
own; Wave B does not start until Wave A is done.

1. **Phase 1 — write this proposal.** *(this document, done)*
2. **Phase 2 — grill.** Poke holes. Open questions below are the main ones.

#### Wave A — extract `JTabbedPane` into `swing_get_tabs`

Additive + subtractive. Introduces one new tool, removes `JTabbedPane` from
two existing tools. No renames.

3. **Phase A.1 — spec.**
   - New UC: `swing_get_tabs` (schema, BRs, algorithm, tests).
   - Amend UC-017: remove `JTabbedPane` from the component matrix; remove
     BR-12 (disabled-tab indicator, now lives in `swing_get_tabs`); update
     tool description to drop the `JTabbedPane` mention.
   - Amend UC-018: remove `JTabbedPane` from the component matrix; update
     tool description.
   - UC-002 snapshot: `JTabbedPane` stays labeled `single-selection` (selection
     family still covers it — read/write). Open question Q1 decides whether
     `get_tabs` also gets a snapshot action label.
4. **Phase A.2 — implement.**
   - Add `SwingGetTabsTool` + register it.
   - Update `SwingUtils.supportsGetSelectableItems` /
     `SwingUtils.getSelectableItemsCount` to exclude `JTabbedPane`.
   - Migrate `JTabbedPane` test methods from
     `SwingGetSelectableItemsTest` / `SwingGetSelectableItemsCountTest` into
     new `SwingGetTabsTest` (headless) + `SwingGetTabsScreenTest` (testSwing).
   - Verify the existing tests for `JTabbedPane` under the old tools now
     correctly return an unsupported-component MCP error.
5. **Phase A.3 — wrap up.** Commit, build, ship.

#### Wave B — rename `selectable_items` → `items`

Pure rename. No semantic change. Easier to review because Wave A already
removed the `JTabbedPane`-specific edge cases (BR-12, disabled-tab indicator)
from these tools.

6. **Phase B.1 — spec.**
   - Amend UC-017: rename to `swing_get_items`; file rename
     `use-case-017-swing-get-selectable-items.md` →
     `use-case-017-swing-get-items.md`.
   - Amend UC-018: rename to `swing_get_item_count`; analogous file rename.
   - Update UC-002 and architecture.md § Selection Action Groups to reference
     the new names.
   - Search-and-replace remaining `selectable_items` mentions across
     `use-case-*.md`, `verification.md`, `architecture.md`.
7. **Phase B.2 — implement.** Rename classes
   (`SwingGetSelectableItemsTool` → `SwingGetItemsTool`, same for the count
   tool and their tests). Rename tool IDs. Rename helper method
   `SwingUtils.supportsGetSelectableItems` → `SwingUtils.supportsGetItems` (or
   similar).
8. **Phase B.3 — sweep.** Verify no stale `selectable_items` tokens remain in
   spec or code.

### Open questions

- **Q1: Does `get_tabs` belong in the snapshot action list?** Currently,
  `get_selectable_items` is not listed — its availability is implied by
  `single-selection` / `multi-selection` labels (architecture.md § 6). For
  `get_tabs`, the equivalent implication would be: if the component is a
  `JTabbedPane`, `get_tabs` is available. Options:
  - (a) Do not advertise in the snapshot; document in the tool description (same
    pattern as `get_items` today). Relies on the AI reading the
    tool description, but keeps the snapshot quiet.
  - (b) Add a `tabs` action label to `JTabbedPane` specifically. Costs one label,
    gains direct discoverability.
  - **Leaning:** (a) for consistency with `get_items` and to keep the snapshot
    compact. Flag for grilling.

- **Q2: Does `get_tabs` obviate `get_selection` for `JTabbedPane`?** If
  `get_tabs` already surfaces `selected: true`, the AI can read the active tab
  without calling `get_selection`. That's fine; `get_selection` remains
  available for polymorphic use (AI code written once for any selection
  component still works on tabs). No action needed — just note that `get_tabs`
  is a superset of what `get_selection` tells you for `JTabbedPane`.

- **Q3: Empty tabs.** `JTabbedPane.getTabCount() == 0`. Does `get_tabs` return
  `[]` with no `selected` anywhere? Probably yes (no tab is selected if no
  tabs exist — `getSelectedIndex()` returns -1). Needs a dedicated BR.

- **Q4: Tab `title` vs `name`.** `JTabbedPane.getTitleAt(i)` returns the title.
  This is *different* from the accessible name, which is usually also the
  title but can diverge if the tab has custom accessible properties. Decide
  whether `get_tabs` uses `getTitleAt` (UI-truthful) or
  `getAccessibleContext().getAccessibleName()` (accessibility-truthful). The
  former is simpler and matches what the user sees. Leaning `getTitleAt`.
