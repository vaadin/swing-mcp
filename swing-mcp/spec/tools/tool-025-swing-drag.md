# T-025: swing_drag

**Status:** Implemented
**Date:** 2026-04-14

Drag-and-drop primitive for palette→canvas drops, node-to-node edge drawing, component-to-component transfers, reordering, and canvas repositioning — needed because the snapshot exposes no drag action and the AI must infer drag capability from context.

**Tool description:** "Drag a UI component to another location. Dispatches mouse drag events (PRESSED → DRAGGED → RELEASED). Source: source_ref identifies the component; by default drags from its center. Provide optional source_x/source_y (component-relative pixel offsets) to start from a specific point within the component (e.g. a painted node on a canvas). Target: target_ref identifies the drop component; by default drops at its center. Provide optional target_x/target_y (component-relative pixel offsets) to drop at a specific point within the target component. Optional via: flat array of [ref, x, y, ...] triplets defining intermediate waypoints the drag passes through (e.g. for self-edges that must exit and re-enter a node). Refs are obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | **Source specification.** `source_ref` is required — identifies the component to drag from. By default the drag starts from the component's center. Optional `source_x`/`source_y` (integers, component-relative pixel offsets) override the start point within the component (e.g. to target a specific painted node on a canvas). If only one of `source_x`/`source_y` is provided, return an `INVALID_PARAMS` error. |
| BR-02 | If the source ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | **Target specification.** `target_ref` is required — identifies the component to drop onto. By default the drop lands at the component's center. Optional `target_x`/`target_y` (integers, component-relative pixel offsets) override the drop point within the target component. If only one of `target_x`/`target_y` is provided, return an `INVALID_PARAMS` error. |
| BR-04 | If the target ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-05 | The source must be effectively enabled (see **D_mirror_swing_semantics**). If not, return an MCP-level error (`isError: true`) explaining the component is disabled and cannot be dragged. See also **`design/architecture.md`** — Tool execution level. |
| BR-06 | **Virtual accessible child resolution.** If the source ref resolves to a virtual accessible child (e.g., a JList item, JTree node) that is not a `Component`, the tool walks up via `getAccessibleParent()` to the host `Component` and computes the pixel coordinates of the child within it using `child.getAccessibleContext().getAccessibleComponent().getBounds()`. The press point is the center of the child's bounds within the host component (overridden by `source_x`/`source_y` if provided). The same resolution applies to `target_ref` when it resolves to a virtual child. If no `Component` ancestor is found, return an MCP-level error. |
| BR-07 | In synthetic mode, all events are dispatched to the **source host component** (not the target). The target point, and all waypoint positions, are translated to the source component's local coordinate system using `SwingUtilities.convertPoint()`. In Robot mode, all points (source, waypoints, target) are converted to screen-absolute using their respective component's `getLocationOnScreen()`. |
| BR-08 | **Synthetic event sequence** (headless / non-showing mode). Without waypoints: 7 events — (1) `MOUSE_PRESSED` at source press point with `BUTTON1_DOWN_MASK` modifier and `BUTTON1` button, click count 0; (2) five `MOUSE_DRAGGED` events linearly interpolated from press to target, with `BUTTON1_DOWN_MASK` modifier and `NOBUTTON` button (AWT convention), click count 0; (3) `MOUSE_RELEASED` at target with modifier 0 and `BUTTON1` button, click count 0. Timestamps start at `System.currentTimeMillis()` and increment by 16ms per event. With waypoints: the drag events are generated per segment (source→wp1, wp1→wp2, ..., wpN→target) with interpolation within each segment; the total count varies. PRESSED and RELEASED events are unchanged. |
| BR-09 | `swing_drag` is a **mutation tool** — `isMutation()` returns `true` and the ref map is cleared after successful invocation (see **`design/architecture.md` § Flows — the ref lifecycle**). A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map — the UI state hasn't changed, so existing refs remain valid and the AI can retry without re-snapshotting. |
| BR-10 | All validation runs on the EDT inside `runInEDT()`. The drag action is posted via `SwingUtilities.invokeLater()` (synthetic mode) or a new `Thread` (Robot mode) and the tool returns the D_dispatched_echo success echo `Dispatched drag on ref=<S> to ref=<T> — call swing_snapshot to verify the outcome` immediately (fire-and-forget — see **D_fire_and_forget_dispatch**). Only the source/target refs appear in the echo; optional offsets and `via` waypoints are omitted to keep the echo short and uniform across mutations. |
| BR-11 | No `drag` action is advertised in the snapshot. The tool is always callable — the AI infers drag capability from context. |
| BR-12 | **Auto-detection of dispatch strategy.** If a graphical display is available (`!GraphicsEnvironment.isHeadless()`) AND the source component is showing on screen (`source.component.isShowing()`), uses `java.awt.Robot` for real OS-level mouse events (compatible with both MouseListener-based drag and Java's DnD framework). Otherwise falls back to synthetic `Component.dispatchEvent()`. The AI caller does not choose the mode — it is selected automatically. |
| BR-13 | **Intermediate waypoints (`via`).** Optional flat integer array of triplets `[ref1, x1, y1, ref2, x2, y2, ...]`. Each triplet defines a waypoint: `ref` identifies the component (resolved the same as source/target refs per BR-06), `x`/`y` are component-relative pixel offsets within that component. The drag path becomes: source press point → waypoint 1 → waypoint 2 → ... → target drop point. The mouse button stays pressed throughout. If `via` length is not divisible by 3, return an `INVALID_PARAMS` error. If a waypoint ref is not found, return an MCP-level error with recovery message. An empty array or omitted `via` means a direct straight-line drag (current behavior). |

### DnD scenarios covered

| Source | Target | Example |
|--------|--------|---------|
| `source_ref` | `target_ref` | Component → component (JTree→JTable, dual JList, reordering) — center to center |
| `source_ref` | `target_ref` + `target_x`/`target_y` | Palette → canvas (drag a shape onto a specific position on a canvas component) |
| `source_ref` + `source_x`/`source_y` | `target_ref` + `target_x`/`target_y` | Canvas edge drawing (both refs point to the canvas; offsets identify the source and target nodes within it) |
| `source_ref` + `source_x`/`source_y` | `target_ref` | Canvas → component (drag from a specific point on a canvas onto an accessible drop zone) |
| `source_ref` + `source_x`/`source_y` + `via` | `target_ref` + `target_x`/`target_y` | Self-edge (drag from a node, exit its bounds via a waypoint, return to the same node; source and target are the same point, `via` passes outside) |

**Excluded:** Cross-window drag (source and target in different top-level windows).

### Known limitations

1. **Synthetic mode — Java DnD API best effort.** Synthetic `MouseEvent`s dispatched via `Component.dispatchEvent()` trigger `MouseListener`/`MouseMotionListener` and `DragGestureRecognizer`, but the subsequent native DnD transfer phase (`DropTarget` events) depends on platform behavior. Robot mode (auto-selected when a display is available) provides full DnD support.

2. **Single-window only.** Both source and target must share a `Window` ancestor (synthetic mode) or be on the same screen (Robot mode).

### Execution order

1. **BR-01** — validate `source_ref` (required) and optional `source_x`/`source_y` pair.
2. **BR-03** — validate `target_ref` (required) and optional `target_x`/`target_y` pair.
3. **BR-13** — validate `via` (if provided): length divisible by 3.
4. **BR-02** — ref lookup for `source_ref`.
5. **BR-06** — resolve source to Component + press point (center by default; overridden by `source_x`/`source_y`).
6. **BR-04** — ref lookup for `target_ref`.
7. **BR-06** — resolve target to Component + drop point (center by default; overridden by `target_x`/`target_y`).
8. **BR-13** — resolve each waypoint triplet: ref lookup + component resolution + offset application.
9. **BR-05** — `isEffectivelyEnabled(accessible)`.
10. **BR-12** — auto-detect dispatch strategy.
11. Fire-and-forget drag dispatch (source → waypoints → target) — return D_dispatched_echo success echo `Dispatched drag on ref=<S> to ref=<T> — call swing_snapshot to verify the outcome`.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingDragToolTest` (headless — verifies synthetic dispatch path and validation)
  - [x] Drag with `source_ref` + `target_ref` — assert `wasDragged()`.
  - [x] Drag with `target_ref` + `target_x`/`target_y` — assert correct release coordinates at target offset.
  - [x] Headless environment uses synthetic dispatch — events arrive at source, not target.
  - [x] `source_x`/`source_y` overrides default center — press event at custom offset.
  - [x] Press event is at source component center when no offsets provided.
  - [x] Missing `source_ref` returns `INVALID_PARAMS`.
  - [x] Invalid `source_ref` returns MCP error with `isError: true` and recovery message.
  - [x] Invalid `target_ref` returns MCP error with `isError: true` and recovery message.
  - [x] Missing `target_ref` returns `INVALID_PARAMS`.
  - [x] `source_x` without `source_y` returns `INVALID_PARAMS`.
  - [x] `source_y` without `source_x` returns `INVALID_PARAMS`.
  - [x] `target_x` without `target_y` returns `INVALID_PARAMS`.
  - [x] `target_y` without `target_x` returns `INVALID_PARAMS`.
  - [x] Disabled source (via `source_ref`) returns MCP error with `isError: true`.
  - [x] Exactly 5 `MOUSE_DRAGGED` events are received.
  - [x] Full 7-event sequence: PRESSED → 5× DRAGGED → RELEASED (BR-08).
  - [x] Event button/modifier values match BR-08.
  - [x] Drag events are linearly interpolated between source and target.
  - [x] Virtual child source (JList component) resolves to host component without error.
  - [x] Virtual child item (JList accessible child) as source resolves to host JList via `getAccessibleParent()` walk-up (BR-06).
  - [x] Snapshot does not advertise a `drag` action (BR-11).
  - [x] Drag returns the D_dispatched_echo `Dispatched drag on ref=<S> to ref=<T> — call swing_snapshot to verify the outcome` echo (BR-10).
  - [x] `isMutation()` returns `true` (BR-09).
  - [x] The ref map is cleared after a successful `swing_drag` call (BR-09).
  - [x] The ref map is preserved after a failed `swing_drag` call on a disabled source (BR-09 — refs remain valid for retry).
  - [x] Each component from the component matrix is tested.
  - [x] MCP client smoke test (start MCPServer, call tool via MCP client, stop server).
  - [x] `via` with valid waypoints — drag events pass through waypoint positions (BR-13).
  - [x] `via` with length not divisible by 3 returns `INVALID_PARAMS` (BR-13).
  - [x] `via` with empty array — valid, behaves like direct drag (BR-13).
  - [x] `via` with invalid ref returns MCP error with recovery message (BR-13).
  - [x] Self-edge: source and target same point, `via` passes outside — produces valid drag sequence.

### `DragRecordingPanel` — Reusable Test Component

A `JPanel` subclass in `src/test` that registers both a `MouseAdapter` and a `MouseMotionAdapter` on itself to record drag event sequences. It verifies internally that events arrive in the correct order (`MOUSE_PRESSED` → one or more `MOUSE_DRAGGED` → `MOUSE_RELEASED`). The test asserts via `wasDragged()` — returns `true` only if the full sequence was received correctly. Lives in `src/test` so it is visible to both headless and `testSwing` source sets.

- [x] `SwingDragScreenTest` (`testSwing` — requires display; see `design/architecture.md` § Testing)
  - [x] Drag between panels inside a `JFrame` using `source_ref` + `target_ref`.
  - [x] Drag with target offset inside a `JFrame` using `target_ref` + `target_x`/`target_y`.
  - [x] Drag between panels inside a `JDialog`.
