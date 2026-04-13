# UC-020: swing_get_cells

---

**As an** AI agent, **I want to** enumerate the accessible children of a large data component (JList, JTree) by ref **so that** I can discover content beyond the snapshot's truncation cap — e.g. find and click a specific button rendered inside a list item that was not included in the snapshot.

**Status:** Implemented (amended 2026-04-13 — JTable removed)
**Date:** 2026-04-08

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I see a large data component (JList or JTree) with `get_cells` in its actions — this means its children were truncated by the snapshot.
- I call `swing_get_cells` with the `ref` identifying the component, plus `offset` and `length` to page through its accessible children.
- The tool returns an indented text tree (same format as `swing_snapshot`) rooted at the requested children. Each actionable child receives a fresh ref.
- The tool **replaces the MCPServer ref map** with only the refs visible in the output. Refs from the prior `swing_snapshot` or `swing_get_cells` call are no longer valid.
- I use the returned refs to interact with the discovered children (e.g. click a button inside a list cell renderer). When I need to return to the full UI, I call `swing_snapshot` again.

**Tool description:** "Enumerate accessible children of a large data component (JList, JTree) by ref, returning refs for any actionable children inside. Returns a paged accessibility tree (same format as swing_snapshot) rooted at the requested children. Parameters: ref (integer), offset (0-based integer), length (integer). Indices are in the accessible children index space (not the selection item index space — use swing_get_selectable_items for selection). Use this when you need to click or otherwise interact with a component nested inside a list item or tree node. For JTable, use swing_get_selectable_items instead — table cells are plain text labels with no actionable children. WARNING: this tool replaces the ref map — refs from prior swing_snapshot or swing_get_cells calls become invalid. The parent component gets ref=1 so you can call get_cells again with a different offset. Call swing_snapshot to restore the full-tree ref map. Requires a ref obtained from swing_snapshot or swing_get_cells."

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. `offset` is required and must be a non-negative integer (0 or greater). `length` is required and must be a non-negative integer (0 or greater). No upper cap is enforced on `length` — the AI client is responsible for managing its own context window. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | If the target does not support `get_cells`, the tool returns an MCP-level error (`isError: true`). JTable targets receive the message *"JTable does not support get_cells. Table cells are plain text labels — use swing_get_selectable_items to page through rows."* All other unsupported targets receive *"Component does not support get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions."* `get_cells` is supported when the component's role is `LIST` or `TREE` (`isGetCellsSupported`). `TABLE` is explicitly excluded — JTable cell renderers paint via `CellRendererPane` (stamp painting), so cell accessible children are always `LABEL` with no actions; `get_cells` can return no actionable refs. For row-based JTable access, `swing_get_selectable_items` is the canonical tool (UC-017 BR-09). **No child count threshold is enforced at runtime** for the supported roles — a JList with 3 items is accepted. The snapshot only *advertises* `get_cells` when `childCount > MAX_DATA_ROW_NODES` (to reduce action noise), but the tool itself works on any supported large data component. This avoids confusing errors when the child count drops between the snapshot and the `get_cells` call (e.g. items deleted from a list model): the AI simply receives fewer cells instead of an error, and does not have to repeat the snapshot → get_cells cycle — preventing unnecessary context window pollution. |
| BR-04 | All Swing component access happens on the EDT via `runInEDT()`. |
| BR-05 | `swing_get_cells` **replaces the ref map**. Even though this is a read-only inspection tool (`isMutation()` returns `false`), it manages the ref map itself inside `execute()`. Unlike `swing_snapshot` (which clears at the very start), `get_cells` clears the ref map **after** the ref lookup and eligibility check succeed (steps 1–3 in the algorithm), then populates via `context.putRef()` during ref assignment. This is necessary because the `ref` parameter refers to the *previous* ref map — clearing before lookup would make the lookup fail. The `isMutation() == false` means the `finally` block in `MCPServer.registerTool` does not additionally clear the map, so the assigned refs survive the tool call. Children outside the `offset`/`length` window are not interactable. The AI must call `swing_snapshot` to restore the full-tree ref map. |
| BR-06 | No enabled check is performed — enumerating children is always allowed, even on disabled components. |
| BR-07 | If `offset` is greater than or equal to the total accessible children count, the tool returns an empty string (not an error). This allows the AI to detect end-of-list. |
| BR-08 | The output format mirrors `swing_snapshot`: a compact indented text tree with role, name, states, and actions per node. The same pruning pipeline (Stages 1–3 from UC-002) and action label algorithm (UC-002 BR-06) are applied to each child's subtree. The **parent component itself** receives ref=1 (always), and child refs start from 2. This allows the AI to call `swing_get_cells` again with the new parent ref to page to a different offset without round-tripping through `swing_snapshot`. The parent is also usable for other tools (e.g. `swing_get_selectable_items`, `swing_get_selection`). |
| BR-09 | Children are enumerated via `AccessibleContext.getAccessibleChild(i)` for `i` in `[offset, min(offset + length, totalChildren))`. Each child becomes a root for the snapshot pipeline (build → prune → assignRefs → render). Each child renders at **depth 0** (no indentation for the top-level child node; its own children indent from there). Multiple children are rendered consecutively with **no separator** between them — they are siblings in a flat list, not independent roots like `swing_snapshot`'s `---`-separated windows. |
| BR-10 | The output includes a header line: `Showing N children from offset O (total T) for ROLE [ref=1]` where N is the number of children actually returned, O is the `offset` parameter, T is the total accessible children count, and ROLE is the component's accessible role name (lowercased). Examples: `Showing 10 children from offset 10 (total 200) for list [ref=1]`, `Showing 0 children from offset 300 (total 200) for list [ref=1]`, `Showing 0 children from offset 0 (total 0) for list [ref=1]`. No singular/plural branching — always "children". The header is always present, even when no children are returned. |
| BR-11 | The ref assignment is global across all returned children and their subtrees. The parent component gets ref=1 (registered before child enumeration). Child refs start from 2 and increment depth-first across all children in the output window. |
| BR-12 | The snapshot advertises `get_cells` only when `childCount > MAX_DATA_ROW_NODES` (UC-002 step 6b). The tool itself does not enforce this threshold — it accepts any large data component (BR-03). |
| BR-13 | If `getAccessibleChild(i)` returns `null` (defensive case), a placeholder line `- null` is emitted in the output — no `SnapshotNode` is created and no ref is assigned. The child still counts toward the N in the header line. This keeps the count consistent with the iteration range and avoids confusing gaps. |

### Algorithm

Execution order:
1. **BR-01** — parameter validation (fail fast if `ref`, `offset`, or `length` is missing/wrong type; reject negative values).
2. **BR-02** — ref lookup (fail fast if ref is invalid). Uses the *existing* ref map from a prior `swing_snapshot` or `swing_get_cells` call.
3. **BR-03** — eligibility check: verify the component's role is `LIST` or `TREE` (`isGetCellsSupported`). If the component is a `JTable`, return the JTable-specific error that redirects to `swing_get_selectable_items`. For any other unsupported target, return the generic error. No child count threshold is checked.
4. **Clear the ref map and register the parent** — `context.clearRefMap()` (BR-05). Done after validation so that the `ref` lookup in step 2 succeeds against the previous ref map. Then immediately register the parent: `context.putRef(1, accessible)` (BR-08, BR-11). From this point on, the old refs are gone and the parent is ref=1.
5. **Compute iteration range:** `int totalChildren = ac.getAccessibleChildrenCount()`. `int end = (int) Math.min((long) offset + length, totalChildren)`. If `offset >= totalChildren`, return empty output with header (BR-07, BR-10).
6. **Enumerate children:** For each `i` in `[offset, end)`:
   a. `Accessible child = ac.getAccessibleChild(i)`.
   b. If `child == null`, emit a placeholder line `- null` in the output (no `SnapshotNode`, no ref). This keeps the returned count consistent with the iteration range and avoids confusing the AI. This case is expected to be extremely rare.
   c. Build a `SnapshotNode` subtree from `child` using the same `build()` logic as `swing_snapshot` (Phase 1), including SC-3 truncation for nested large data components.
   d. Apply the prune pipeline (Phase 2) to the subtree.
7. **Assign refs:** Run Phase 3 (`assignRefs`) across all built subtrees sequentially, starting from ref **2** (ref 1 is the parent). Each ref is registered via `context.putRef()`.
8. **Render:** Run Phase 4 (`render`) across all subtrees. Prepend the header line (BR-10). Return the concatenated text via `Content.text()`.

**Accessibility API methods used:**
- `AccessibleContext.getAccessibleChildrenCount()` — total children count and eligibility check
- `AccessibleContext.getAccessibleChild(int i)` — child enumeration
- `AccessibleContext.getAccessibleRole()` — role check for `isLargeDataComponent`
- All methods used by the snapshot pipeline (build, prune, assignRefs, render)

### Design notes

- **Ref map replacement rationale.** Unlike other read-only tools that preserve the ref map, `get_cells` replaces it because the discovered children (e.g. buttons inside table cells) need refs to be interactable. Keeping both the snapshot refs and the cells refs would create ambiguous numbering. The replacement model is simple: the AI sees only what `get_cells` returned plus the parent at ref=1, and must call `swing_snapshot` to "zoom back out".
- **Parent ref=1.** The parent component (the JList/JTree itself) always gets ref=1 in the new ref map. This allows the AI to: (a) call `swing_get_cells` again with `ref=1` to page to a different offset, (b) call selection tools (`swing_get_selectable_items`, `swing_get_selection`, etc.) on the parent without round-tripping through `swing_snapshot`.
- **Relationship to `get_selectable_items`.** `get_cells` operates in the **accessible children index space** — the same indices used by `getAccessibleChild(i)`. `get_selectable_items` operates in the **selection item index space** — the same indices used by `addAccessibleSelection(i)`. For JList, the two spaces are identical. For JTree, `get_cells` enumerates top-level visible nodes; `get_selectable_items` is suppressed.
- **Why JTable is excluded.** JTable cells are painted via `CellRendererPane` (stamp painting) — renderer components are never added to the real component hierarchy and never expose actions via the accessibility API. Every JTable cell surfaces as a `LABEL` with no actions, so `get_cells` on a JTable can never return an actionable ref — only text, which `swing_get_selectable_items` already provides more concisely (pipe-separated rows) and in the same index space the snapshot uses (rows). Additionally, `get_cells`'s flat cell-index space (`row*cols + col`) would conflict with the row-based index space of the snapshot (SC-6) and selection tools (UC-014, UC-017 BR-09). Excluding JTable keeps the index spaces consistent. Interactive cell editors only appear in the accessibility tree while a cell is being actively edited — reaching them through `get_cells` is not a supported workflow.
- **Reusing the snapshot pipeline.** The entire 4-phase pipeline (`build`, `pruneChildren`, `assignRefs`, `render`) has been refactored into `SnapshotNode` as instance/static methods. `SwingSnapshotTool` is now a thin orchestrator. `SwingGetCellsTool` should call `SnapshotNode.build()`, `.pruneChildren()`, `.assignRefs()`, and `.render()` directly on the child subtrees — no code duplication needed.
- **Integer overflow.** When computing `offset + length`, use `long` arithmetic: `int end = (int) Math.min((long) offset + length, totalChildren);`.
- **JTree specificity.** JTree's collapsed nodes hide their children from the accessible tree — `get_cells` only reveals the currently visible top-level nodes, not deeply nested ones. A future JTree-specific content discovery mechanism may be needed (see architecture.md TODO-3).
- **Nested truncation.** If a child of the target is itself a large data component (e.g. a JTree inside a cell renderer — unlikely but possible), SC-3 truncation applies to the nested component as well.

---

## Acceptance Criteria

- [x] Calling `swing_get_cells` on a `JTable` returns an MCP error whose message redirects the AI to `swing_get_selectable_items`.
- [x] Calling `swing_get_cells` with `offset` beyond the total children count returns an empty result (not an error) with a header showing the total count.
- [x] Calling `swing_get_cells` on a truncated `JList` returns the expected children with correct roles and names.
- [x] Calling `swing_get_cells` with `offset: 10, length: 5` on a truncated `JList` returns children 10–14.
- [x] Calling `swing_get_cells` on a truncated `JTree` returns the expected top-level tree nodes.
- [x] The output format matches `swing_snapshot` — same indented text tree with roles, names, states, and actions.
- [x] Pruning rules (Stages 1–3 from UC-002) are applied to each child's subtree.
- [x] The parent component receives ref=1 in the new ref map. Child refs start from 2.
- [x] After `swing_get_cells`, calling `swing_get_cells` again with `ref=1` (the parent) and a different offset works without calling `swing_snapshot` first.
- [x] After `swing_get_cells`, calling `swing_get_selectable_items` with `ref=1` (the parent) succeeds.
- [x] After `swing_get_cells`, the MCPServer ref map contains only the parent ref and the child refs from the output — refs from a prior `swing_snapshot` are no longer valid.
- [x] After `swing_get_cells`, calling `swing_click` with a child ref from the output succeeds (e.g. clicking a button inside a table cell).
- [x] After `swing_get_cells`, calling `swing_snapshot` replaces the ref map with the full-tree refs again.
- [x] The header line shows the parent's new ref (e.g. `table [ref=1]`).
- [x] Calling `swing_get_cells` on a non-truncated large data component (e.g. a `JList` with 3 items) succeeds — returns those 3 children normally (no child count threshold enforced at runtime).
- [x] Calling `swing_get_cells` on an empty large data component (e.g. a `JList` with 0 items, `offset: 0, length: 10`) succeeds — returns empty output with header showing total count 0 and parent ref=1.
- [x] Calling `swing_get_cells` on a non-supported component (e.g. `JButton`, `JPanel`, `JTable`) returns an MCP error.
- [x] Calling `swing_get_cells` with an invalid ref returns an MCP error with a recovery message.
- [x] Calling `swing_get_cells` on a disabled component succeeds (no enabled check).
- [x] The ref map is replaced even if the output window is empty (offset beyond end).
- [x] Negative `offset` returns an MCP error.
- [x] Negative `length` returns an MCP error.
- [x] Missing `offset` or `length` returns an MCP error.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] `SwingGetCellsTest` (headless)
  - [x] Reading a `JTable` returns an MCP error whose message redirects the AI to `swing_get_selectable_items`.
  - [x] Reading with `offset` beyond total children count returns empty output with header.
  - [x] Reading a truncated `JList` returns children with correct roles and names.
  - [x] Reading a truncated `JList` (`offset: 10, length: 5`) returns children 10–14.
  - [x] Reading a truncated `JTree` returns top-level tree nodes.
  - [x] Output format matches `swing_snapshot` — indented text tree with roles, names, states, actions.
  - [x] Pruning rules are applied (e.g. transparent pruning of unnamed panels inside cells).
  - [x] Parent component gets ref=1; child refs start from 2 and increment depth-first.
  - [x] After `swing_get_cells`, calling `swing_get_cells` again with `ref=1` and a different offset succeeds (no `swing_snapshot` needed).
  - [x] After `swing_get_cells`, calling `swing_get_selectable_items` with `ref=1` succeeds.
  - [x] After `swing_get_cells`, using a child ref from the output (e.g. `swing_click`) succeeds.
  - [x] After `swing_get_cells`, old refs from a prior `swing_snapshot` are invalid (MCP error on use).
  - [x] After `swing_get_cells`, calling `swing_snapshot` restores the full-tree ref map.
  - [x] Non-truncated large data component (e.g. `JList` with 3 items) succeeds — returns those children normally.
  - [x] Empty large data component (e.g. empty `JList`, `offset: 0, length: 10`) succeeds — returns empty output with header showing total count 0 and parent ref=1.
  - [x] Non-large-data component (`JButton`) returns an MCP error.
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Disabled truncated `JList` succeeds.
  - [x] Negative `offset` returns an MCP error.
  - [x] Negative `length` returns an MCP error.
  - [x] Missing `offset` or `length` returns an MCP error.
  - [x] Header line shows correct child range and total count.
  - [x] Ref map is replaced even when output is empty (offset beyond end).
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingGetCellsScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] Reading a `JTable` inside `JFrame` returns an MCP error redirecting to `swing_get_selectable_items`.
  - [x] Reading a truncated `JList` inside `JFrame` returns children.
  - [x] Reading a truncated `JList` inside `JDialog` returns children.

### Component matrix

Each component from the verification matrix gets a dedicated test method.

**Expected to succeed (`get_cells` supported — any supported large data component regardless of child count):**
`JList`, `JTree`

**Expected to fail with a JTable-specific error redirecting to `swing_get_selectable_items`:**
`JTable`

**Expected to fail with "Component does not support get_cells" error:**
`JButton`, `JCheckBox`, `JRadioButton`, `JTextField`, `JTextArea`, `JComboBox`, `JToggleButton`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JSpinner`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`
