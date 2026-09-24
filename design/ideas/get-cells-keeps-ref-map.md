# Let `swing_get_cells` keep the snapshot's refs

Found on 2026-09-24 while adding `all_refs` (`D_opt_in_all_refs`).

## The gap

`swing_get_cells` replaces the whole ref map: the parent becomes `ref=1` and the page's nodes
take 2 onwards. Every ref from the snapshot is gone, so a leaf on a later page of a truncated
`JTree` or `JList` can't be dragged onto anything outside that component. `swing_drag` needs the
source and the target refs in the same map.

Inside the component it works: `virtualChildItemAsSourceResolvesToHostJList` drags one item onto
another from the same page.

## Options

1. **Append instead of replace.** `get_cells` continues numbering after the snapshot's highest
   ref, and leaves the snapshot's refs valid. The parent keeps its snapshot ref, so the
   "parent is `ref=1`" paging shortcut becomes "parent keeps its ref". The ref map grows with
   each page until the next mutation.
2. **Append only under `all_refs`.** Keeps today's behaviour for the common case, but gives the
   same parameter two meanings.
3. **Leave it.** Paging through a huge tree to drag is rare, and a caller can work around it
   with `target_x` / `target_y` on the tree's own ref when the drop target is inside the tree.

## Open questions

- `Q_get_cells_append`: does anything rely on `get_cells` renumbering from 1? The `ref=1` parent
  is in the tool description and in `snapshot-format.md`.
- `Q_page_ref_reuse`: when appending, does calling `get_cells` again for the same page hand out
  new numbers or reuse the ones it gave before?
