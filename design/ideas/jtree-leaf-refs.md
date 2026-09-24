# Give a JTree leaf a ref

Found on 2026-09-24 while writing `SwingDragScreenTest.nestedJTreeNodeAsSourceDragsFromItsCenter`.

## The gap

`SnapshotNode.assignRefs` numbers only a node that `hasAnyAction()`, and `swing_get_cells` uses
the same numbering. A JTree node's `AccessibleAction` is `toggle_expand` plus whatever the cell
renderer's context offers (`JTree.java:5667` in JDK 17: `isLeaf ? 0 : 1` on top of the
renderer's count). The default renderer is a `JLabel`, which offers nothing. So a leaf has no
actions and no ref:

```
- JTree (tree)
  - (label) "Root" [ref=1, expanded] actions: toggle_expand, single-selection
    - (label) "Parent" [ref=2, expanded] actions: toggle_expand, single-selection
      - (label) "Child" [collapsed]
```

Leaves are most of a tree, and a user can do things to them that an agent can't:

- **Drag.** `swing_drag` takes `source_ref` / `target_ref` and `via` refs, so a leaf can't be the
  source, the drop target or a waypoint. Dragging a file onto a folder in a file tree is the
  obvious case.
- **Point at it.** The JTree itself had no ref either in that snapshot. Only a truncated tree
  gets one, for `get_cells`. So `source_x` / `source_y` on the tree is no workaround.
- **Select it.** Not today: `D_no_jtree_selection` makes every selection tool refuse a `JTree`,
  whatever the node advertises.

JList items don't have this gap, because each one advertises `click` (`R_accessible_action_impls`).

## Options

1. **A ref for every node, or every virtual child of a JTree / JList.** It's the simplest, but the
   ref stops meaning "something you can act on", and the snapshot gets longer.
2. **A ref for a node its host lets you select or drag** — a leaf of a selectable tree, or of one
   with a `TransferHandler` / `setDragEnabled(true)`. Tighter, but "can a user drag this?" is
   hard to answer statically: a plain `MouseMotionListener` drag has no flag.
3. **Advertise `click` on a tree node**, like a JList item, and dispatch a synthetic click at
   the node's centre. `resolveComponentAndPoint` now gets that centre right at any depth. This
   gives the leaf a real action, and hence a ref, without bending what a ref means.
4. **An opt-in `all_refs` boolean on `swing_snapshot`** that numbers every node, actionable or
   not. The agent asks for it when it's about to drag, so a normal snapshot costs nothing extra.
   **The leaning.**

## Option 4 in detail

- **Why it beats option 2.** "Can a user drag this?" can't be answered statically. With the
  flag, the agent says it is about to drag, so nothing has to guess.
- **It covers drop targets, not just leaves.** A drop target is often a plain component with a
  `TransferHandler` and no action. `SwingDragTool` already takes any ref: it resolves the ref and
  refuses only a disabled component. `source_ref`, `target_ref` and `via` need no change.
- **It composes with `filter_substring`.** Refs are assigned before the filter runs, so
  `{filter_substring: "Child", all_refs: true}` is a short output that still carries the leaf's
  ref. The extra snapshot a drag costs can stay small.
- **Per call, not sticky.** The first successful mutation clears the ref map anyway, and the
  next plain snapshot is back to actionable-only refs.
- **Named for its effect, not for drag.** `all_refs`, with the description saying "use before
  `swing_drag`". Pointing at a node through `source_x` / `source_y` has the same need.
- **Refs only, not pruning.** An unnamed layout panel is still pruned, even if it is a drop
  zone, but the window root now takes a ref, so any point stays reachable as root ref + x/y.
  Turning pruning off would lengthen the output a lot for little benefit.
- **`swing_get_cells` takes the same flag.** A tree cut off at `MAX_DATA_ROW_NODES` reaches its
  hidden leaves only there, and it numbers through the same `assignRefs`.
- **It doesn't fix selection.** Clicking a leaf to select it still needs option 3; option 4
  covers drag and pointing only.

**Implementation sketch.** `SnapshotNode.assignRefs(nextRef, context)` takes the flag and
numbers a node when the flag is set or `hasAnyAction()`. `JTableRowSnapshotNode`'s override
keeps `ref = 0`: a row mirrors no accessible, so there is nothing to register. The
`SWING_SNAPSHOT` and `SWING_GET_CELLS` descriptors each gain an optional boolean, and
`snapshot-format.md` § Whole-tree structure gains a sentence on when refs go to every node.

## Answered

- `Q_ref_means_action` — keep "a ref marks an actionable node" as the default; `all_refs`
  widens it for one call. This rules out option 1.
- `Q_leaf_selection_via_parent` — no, see `D_no_jtree_selection`. Drag and pointing are real
  gaps; selection is a separate one.
- `Q_drag_target_only` — with `all_refs`, a drop target has a ref, so `target_ref` and `via`
  need no path-shaped alternative.

## Open questions

- `Q_get_cells_cross_page_drag`: `swing_get_cells` replaces the whole ref map with the parent
  (`ref=1`) and the page, so a leaf on a later page can't be dragged onto anything outside the
  tree. Should `get_cells` add to the snapshot's ref map instead of replacing it, at least under
  `all_refs`?
