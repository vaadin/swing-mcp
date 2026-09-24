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
- **Select it.** This may already work: the parent node advertises `single-selection`, so
  `swing_set_selection` on the parent with the leaf's index might select it. Unverified.

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

## Open questions

- `Q_ref_means_action`: is "a ref marks an actionable node" an invariant to keep? That decides
  between option 1 and options 2–3.
- `Q_leaf_selection_via_parent`: does `swing_set_selection` on the parent node select a leaf
  today? If so, drag is the only real gap.
- `Q_drag_target_only`: a drop target needs no action of its own. Should `target_ref` and `via`
  accept something that isn't a ref, such as a tree path?
