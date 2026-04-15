# UC-024: swing_drag

**Status:** Implemented
**Date:** 2026-04-14

Drag-and-drop primitive for palette→canvas drops, node-to-node edge drawing, component-to-component transfers, reordering, and canvas repositioning — needed because the snapshot exposes no drag action and the AI must infer drag capability from context.

**Tool description:** "Drag from one location to another. Dispatches mouse drag events (PRESSED → DRAGGED → RELEASED). Source: provide source_ref (component reference, drags from center) or source_x/source_y (window-relative pixel coordinates, for custom-painted items without refs). Target: provide target_ref (component reference, drops at center) or target_x/target_y (window-relative pixel coordinates). Refs are obtained from swing_snapshot or swing_get_cells."

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | **Source specification.** The caller provides EITHER `source_ref` (integer) OR both `source_x` and `source_y` (integers, window-relative pixel coordinates). If `source_ref` is provided, `source_x`/`source_y` are ignored. If neither `source_ref` nor both `source_x`/`source_y` are provided, return an `INVALID_PARAMS` error. If only one of `source_x`/`source_y` is present (and no `source_ref`), return an `INVALID_PARAMS` error. |
| BR-02 | If the source ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | **Target specification.** The caller provides EITHER `target_ref` (integer) OR both `target_x` and `target_y` (integers, window-relative pixel coordinates). If `target_ref` is provided, `target_x`/`target_y` are ignored. If neither `target_ref` nor both `target_x`/`target_y` are provided, return an `INVALID_PARAMS` error. |
| BR-04 | If `target_ref` is provided and not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-05 | When `source_ref` is used, the source must be effectively enabled (`isEffectivelyEnabled`). If not, return an MCP-level error (`isError: true`) explaining the component is disabled. Skipped when `source_x`/`source_y` is used (no accessible to check). |
| BR-06 | **Virtual accessible child resolution.** If the source ref resolves to a virtual accessible child (e.g., a JList item, JTree node) that is not a `Component`, the tool walks up via `getAccessibleParent()` to the host `Component` and computes the pixel coordinates of the child within it using `child.getAccessibleContext().getAccessibleComponent().getBounds()`. The press point is the center of the child's bounds within the host component. The same resolution applies to `target_ref` when it resolves to a virtual child. If no `Component` ancestor is found, return an MCP-level error. |
| BR-07 | In synthetic mode, all events are dispatched to the **source host component** (not the target). Target coordinates are translated to the source component's local coordinate system using `SwingUtilities.convertPoint()`. For `target_x`/`target_y` (window-relative), the conversion uses the source component's `Window` ancestor as the origin. In Robot mode, all coordinates are converted to screen-absolute. |
| BR-08 | **Synthetic event sequence** (headless / non-showing mode): 7 events: (1) `MOUSE_PRESSED` at the source press point with `BUTTON1_DOWN_MASK` modifier and `BUTTON1` button, click count 0; (2) five `MOUSE_DRAGGED` events linearly interpolated from press point to target position, with `BUTTON1_DOWN_MASK` modifier and `NOBUTTON` button (AWT convention), click count 0; (3) `MOUSE_RELEASED` at the target position with modifier 0 and `BUTTON1` button, click count 0. Timestamps start at `System.currentTimeMillis()` and increment by 16ms per event. |
| BR-09 | `swing_drag` is a **mutation tool** — it clears the ref map in a `finally` block after execution, regardless of success or failure (see **architecture.md §3 rule 3**). |
| BR-10 | All validation runs on the EDT inside `runInEDT()`. The drag action is posted via `SwingUtilities.invokeLater()` (synthetic mode) or a new `Thread` (Robot mode) and the tool returns `null` immediately (fire-and-forget — see **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**). |
| BR-11 | No `drag` action is advertised in the snapshot. The tool is always callable — the AI infers drag capability from context. |
| BR-12 | **Auto-detection of dispatch strategy.** When `source_ref` is used: if a graphical display is available (`!GraphicsEnvironment.isHeadless()`) AND the source component is showing on screen (`source.component.isShowing()`), uses `java.awt.Robot`; otherwise falls back to synthetic `Component.dispatchEvent()`. When `source_x`/`source_y` are used: always uses `java.awt.Robot` (there is no source Component for synthetic dispatch). If headless or no showing window is found, returns an MCP error. |
| BR-13 | **Coordinate-only source requires a display.** When `source_x`/`source_y` are used, a graphical display must be available and at least one considered window must be showing. If headless, return an MCP error: "Coordinate-based drag requires a graphical display but the environment is headless." If no showing window, return an MCP error: "No showing window found for coordinate-based drag." |

### DnD scenarios covered

| Source | Target | Example |
|--------|--------|---------|
| `source_ref` | `target_x`/`target_y` | Palette → canvas (drag a shape onto a graph canvas) |
| `source_x`/`source_y` | `target_x`/`target_y` | Canvas → canvas (draw edges, reposition nodes) |
| `source_ref` | `target_ref` | Component → component (JTree→JTable, dual JList) |
| `source_x`/`source_y` | `target_ref` | Canvas → component (drag a drawn object onto a drop zone) |

**Excluded:** Cross-window drag (source and target in different top-level windows).

### Known limitations

1. **Synthetic mode — Java DnD API best effort.** Synthetic `MouseEvent`s dispatched via `Component.dispatchEvent()` trigger `MouseListener`/`MouseMotionListener` and `DragGestureRecognizer`, but the subsequent native DnD transfer phase (`DropTarget` events) depends on platform behavior. Robot mode (auto-selected when a display is available) provides full DnD support.

2. **Single-window only.** Both source and target must share a `Window` ancestor (synthetic mode) or be on the same screen (Robot mode).

3. **Coordinate-only source not available in headless.** When `source_x`/`source_y` are used, Robot is required (no Component for synthetic dispatch). This mode is not available in headless environments.

### Execution order

1. **BR-01** — source specification validation (fail fast if neither ref nor coords provided).
2. **BR-03** — target specification validation.
3. **BR-02** — ref lookup for `source_ref` (if used).
4. **BR-06** — resolve source to Component + press point (if `source_ref`).
5. **BR-05** — `isEffectivelyEnabled(accessible)` — only checked when `source_ref` is used.
6. **BR-12** — auto-detect dispatch strategy.
7. Fire-and-forget drag dispatch — return `null`.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingDragToolTest` (headless — verifies synthetic dispatch path and validation)
  - [x] Drag with `source_ref` + `target_ref` — assert `wasDragged()`.
  - [x] Drag with `source_ref` + `target_x`/`target_y` — assert correct release coordinates.
  - [x] Headless environment uses synthetic dispatch — events arrive at source, not target.
  - [x] `source_ref` takes precedence over `source_x`/`source_y` when both provided.
  - [x] Invalid `source_ref` returns MCP error with `isError: true` and recovery message.
  - [x] Invalid `target_ref` returns MCP error with `isError: true` and recovery message.
  - [x] No source specification returns `INVALID_PARAMS`.
  - [x] `source_x` without `source_y` returns `INVALID_PARAMS`.
  - [x] `source_y` without `source_x` returns `INVALID_PARAMS`.
  - [x] No target specification returns `INVALID_PARAMS`.
  - [x] `target_x` without `target_y` returns `INVALID_PARAMS`.
  - [x] Disabled source (via `source_ref`) returns MCP error with `isError: true`.
  - [x] `source_x`/`source_y` in headless returns MCP error mentioning headless.
  - [x] Exactly 5 `MOUSE_DRAGGED` events are received.
  - [x] Event button/modifier values match BR-08.
  - [x] Drag events are linearly interpolated between source and target.
  - [x] Virtual child source (JList item) resolves to host component without error.
  - [x] Each component from the component matrix is tested.
  - [x] MCP client smoke test (start MCPServer, call tool via MCP client, stop server).

### `DragRecordingPanel` — Reusable Test Component

A `JPanel` subclass in `src/test` that registers both a `MouseAdapter` and a `MouseMotionAdapter` on itself to record drag event sequences. It verifies internally that events arrive in the correct order (`MOUSE_PRESSED` → one or more `MOUSE_DRAGGED` → `MOUSE_RELEASED`). The test asserts via `wasDragged()` — returns `true` only if the full sequence was received correctly. Lives in `src/test` so it is visible to both headless and `testSwing` source sets.

- [x] `SwingDragScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Drag between panels inside a `JFrame` using `source_ref` + `target_ref`.
  - [x] Drag to coordinates inside a `JFrame` using `source_ref` + `target_x`/`target_y`.
  - [x] Drag between panels inside a `JDialog`.
