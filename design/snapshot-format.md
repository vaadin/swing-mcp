# Snapshot format

The exact text `swing_snapshot` and `swing_get_cells` emit — the wire format between this server
and the model. **Normative: the code conforms.** Change this file first, then the code. About
the shape of the output only; not here: why a slot is shaped so (`decisions.md` — cite the `D_`),
what `javax.accessibility` returns (`research.md` — cite the `R_`), how the four passes compose
(`architecture.md`), one symbol's behaviour (its doc comment). The reader is a model, not a
person: the README's Swing Component Reference is the per-component view of what this produces.

---

## The node line

One node, one line; depth is two spaces of indent under the parent. Slots in this order, each
omitted when empty:

```
- <identity> "name" "description" [ref=N, state, state] <extra> actions: a, !b
```

- **`<identity>`** — always present, one of three forms (below).
- **`"name"`** — `getAccessibleName()`, sanitized, **uncapped** (it is identity —
  `D_quoted_slot_sanitizing`).
- **`"description"`** — resolved description, sanitized, capped at **120 characters** with a
  trailing `…` (U+2026). When the cap bites, the node also advertises `get_description`.
- **`[…]`** — ref and states share one bracket, comma-separated, lowercase. The whole bracket is
  omitted when there is neither.
- **`<extra>`** — component-specific, after the bracket and before `actions:`. Two producers:
  `columns: [ID, Name, City]` on a `JTable` whose header is visible, and the inline value
  preview `text="…"` (15-char cap) or `value=N` (`D_inline_value_preview`). `columns:` comes
  first if both ever co-occur.
- **`actions:`** — comma-separated. A **mutation** action that would fail validation right now
  carries a `!` prefix (`!click`, `!set_text`); read actions and the selection group labels are
  never prefixed.

Every quoted slot passes through `SwingUtils.sanitizeForQuotedSlot` exactly once, before any
cap, so a newline can never break the one-line-per-node rule that the indent structure depends
on (`D_quoted_slot_sanitizing`).

### The identity slot

Three forms, exhaustive. The resolution algorithm is `ComponentClassResolver`'s doc comment; the
role is the `AccessibleRole` field name lowercased, never `toDisplayString()`, which is
localized (`D_role_in_snapshot_only`).

| Form | When | Example |
|---|---|---|
| `JClass (role)` | a standard Swing component | `JButton (push_button)` |
| `Concrete -> JClass (role)` | a custom subclass worth naming | `SearchField -> JTextField (text)` |
| `(role)` | an accessible that is not a `Component` | `(page_tab)`, `(label)` |

The parenthesised role is unconditional, even when it merely lowercases the class
(`JButton (push_button)`), so that a role override (`JButton (button_with_dropdown)`) reads as a
signal rather than as noise.

### States

Shown when present: `focused`, `selected`, `checked`, `expanded`, `collapsed`, `modal`,
`multi_line`, `horizontal`, `vertical`, `busy`, `indeterminate`.

Three are synthesized rather than read from `AccessibleStateSet`:

- `disabled` — from `SwingUtils.isEffectivelyEnabled()`, which does **not** propagate to
  children, mirroring Swing (`R_disabled_not_propagated`, `D_mirror_swing_semantics`). If the
  snapshot shows `[disabled]`, a mutation tool refuses; if it does not, the tool accepts.
- `read_only` — a text component exposing `AccessibleEditableText` but lacking `EDITABLE`. Its
  absence means editable, so no `editable` flag is ever emitted.
- `iconified` — a `Frame` whose extended state has `ICONIFIED`; the JDK never puts it in the
  state set (`R_iconified_windows`, `D_synthetic_iconified_state`).

Never shown, as noise or as always-true for a node that survived pruning: `visible`, `showing`,
`enabled`, `editable`, `opaque`, `resizable`, `armed`, `transient`, `manages_descendants`.

### Which actions appear

Collected in this order, each from the matching `SwingUtils` probe, so what the snapshot
advertises and what a tool accepts cannot diverge:

`click` (never on a `JMenu` — `D_jmenu_not_clickable`) · `toggle_popup` · `increment` /
`decrement` / `toggle_expand`, matched against the `AccessibleAction` constants only · `get_text`
/ `set_text`, neither on a `PASSWORD_TEXT` or `LABEL` role (`D_password_not_readable`,
`D_label_not_readable`) · `get_value` / `set_value` · a selection group label · `get_cells` /
`get_cell_count` · `close` · `get_description`, only when the description hit its cap.

A text component's dynamic `AccessibleAction` descriptions — `cut-to-clipboard`, `select-all`
and the rest — are deliberately ignored: they are derived from `Action.NAME` at runtime, so they
cannot be matched statically, and an agent filling a form wants `set_text`, not the clipboard.

**`single-selection` and `multi-selection` are group labels, not callable actions.** They tell
the model which selection tools apply: `single-selection` means `swing_get_selection`,
`swing_set_selection`, `swing_clear_selection`, `swing_get_items` and `swing_get_item_count`;
`multi-selection` means those plus `swing_select_all`. Those tool names never appear in an
`actions:` slot — the model learns them from the manifest, once, at session start.

`get_cells` / `get_cell_count` appear only on a `JList` or `JTree` whose children the snapshot
actually truncated, never on a `JTable` (`D_no_jtable_cells`). They address the accessible-child
index space; `get_items` addresses the selection index space. The two coincide on a `JList` and
diverge on a `JComboBox` (`R_selection_index_spaces`).

## What survives

Three stages, applied while walking `AccessibleContext.getAccessibleChild(i)` — the
accessibility tree, not `Component.getComponents()`, because only the former has virtual
children for table cells, list items and tree nodes.

**Stage 1 — dropped with every descendant**

| Node | Why |
|---|---|
| `isVisible() == false` | not part of the user-facing UI |
| `CellRendererPane` | stamp-painting artifact; the data arrives as virtual children (`R_jtable_cells_stamped`) |
| a `JRootPane` glass pane with zero accessible children | an empty overlay. With children, it is treated as transparent instead |
| `JTableHeader` | redundant with the `columns:` annotation |
| a `JPopupMenu` whose `getInvoker()` is a `JMenu` | the `JMenu` already exposes the same items (`D_jmenu_not_clickable`). A right-click popup, invoked by a button or table, is unaffected |

**Stage 2 — node removed, children promoted to the parent**

Roles `ROOT_PANE`, `LAYERED_PANE`, `VIEWPORT` and `FILLER`, plus a `PANEL` that has no
accessible name, no description and no `TitledBorder`. A named panel is a logical section and
stays.

`SCROLL_PANE` is deliberately **not** pruned even when unnamed — scrollability is semantic and
shapes a migration. Its `VIEWPORT` is, so the tree reads `scroll_pane → content`.

**Stage 3 — the safety net**

Whatever survives is included, and a node is kept regardless of the rules above when it has a
semantic role, an accessible name, at least one action, `AccessibleText` content or an
`AccessibleValue`, or the `FOCUSED` state.

### Before and after

```
- frame "Invoice Editor"              - JFrame (frame) "Invoice Editor"
  - root_pane              ←stage 2     - JToolBar (tool_bar) "Main"
    - layered_pane         ←stage 2       - JButton (push_button) "Save" [ref=1] actions: click
      - panel              ←stage 2     - JSplitPane (split_pane)
        - panel            ←stage 2       - JLabel (label) "Invoices"
          - tool_bar "Main"               - JScrollPane (scroll_pane)
            - push_button "Save"            - JList (list) [ref=2] actions: multi-selection
        - panel            ←stage 2       - JPanel (panel) "Details"
          - split_pane                      - JTextField (text) "Name" [ref=3] text="" actions: get_text, set_text
            - panel        ←stage 2       - JButton (push_button) "Add" [ref=4] actions: click
              - label "Invoices"
              - scroll_pane
                - viewport ←stage 2
                  - list
            - panel        ←stage 2
              - panel "Details"  ←kept: titled border
                - text "Name"
              - panel      ←stage 2
                - push_button "Add"
```

## Whole-tree structure

- **Roots** are separated by a `---` line. Each root is a window the user could interact with
  right now (`D_interactable_windows_only`).
- **Refs** are integers from 1, assigned depth-first across all roots in one sequence, to every
  node carrying at least one action. They are valid until the next successful mutation.
- **A modal root** whose owner chain has a visible ancestor gets one header line above it:
  `[modal stack (N, topmost first): Class0 "Name0" / Class1 "Name1"]`. Suppressed below two
  entries; the separator is ` / ` because `->` already means something in the identity slot
  (`D_modal_stack_header`).
- **A large data component** (`JTable`, `JList`, `JTree`) renders at most `MAX_DATA_ROW_NODES`
  children, then a render-only line `... and N more items` that is never a node and never takes
  a ref. A `JTable` counts **rows**, and says `... and N more rows`.
- **`JTable` children** are pipe-separated rows, `- row 0: 1 | Alice | NY`, 0-based so the index
  goes straight to a selection tool — not one node per cell (`D_no_jtable_cells`).
- **A `JTabbedPane` tab** renders as `- (page_tab) N "title"`, `N` 0-based, so the model can
  pass it to `swing_set_selection` without enumerating first. Only the selected tab's content
  is walked, because that is all Swing exposes.
- **An iconified `Frame`** renders its own line, then one placeholder line in place of every
  child: `[Contents hidden — window is iconified. Call swing_restore to interact with this
  window.]`. The children take no refs (`D_iconified_children_hidden`).

## Filtered output

With `filter_substring`, the node graph is walked and kept down to every node whose rendered
line contains the substring case-insensitively, **plus all their ancestors** (so the model can
see where a match sits) **and all their descendants** (so a matched container keeps its rows).
Non-matching sibling branches are dropped. Output opens with
`[filter active: only nodes matching "<filter>" and their ancestors/descendants are shown]`;
root separators and modal headers are dropped, since neither carries matchable content. Refs are
unaffected — filtering happens after they are assigned. No match returns
`No lines matched filter_substring 'X'`.
