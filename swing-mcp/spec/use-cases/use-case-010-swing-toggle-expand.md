# UC-010: swing_toggle_expand

**Status:** Implemented
**Date:** 2026-04-01

Expands or collapses a `JTree` node so the AI can navigate a hierarchy to find nested items.

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The toggle-expand action is fired by scanning the node's `AccessibleAction` descriptions for `AccessibleAction.TOGGLE_EXPAND` (`"toggleexpand"`) to get action index `i`, then posting `doAccessibleAction(i)` via `SwingUtilities.invokeLater()`. No UIManager lookup is needed — the standard `JTree` implementation uses the static constant directly. See **architecture.md § 2 — Fire-and-Forget Mutation Dispatch**. |
| BR-04 | If the target does not support toggle-expand (i.e. `SwingUtils.supportsToggleExpand(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "<ClassName> does not support toggle_expand. Call swing_snapshot or swing_get_cells to verify the list of actions". This covers leaf nodes (which do not receive the `TOGGLE_EXPAND` action) and all non-tree components. |
| BR-05 | All validation runs on the EDT inside `runInEDT()`. The action is posted via `SwingUtilities.invokeLater()` from within `execute()` and executes asynchronously. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-08 | `swing_toggle_expand` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after successful invocation. A pre-dispatch validation error (`MCPErrorResponseException`) does **not** clear the ref map — the UI state hasn't changed, so existing refs remain valid and the AI can retry without re-snapshotting. |
| BR-09 | The tool toggles the node regardless of its current expanded/collapsed state. If the node is already expanded, calling this tool collapses it; if collapsed, it expands it. The AI can infer the current state from the `EXPANDED` or `COLLAPSED` state in the snapshot. |
| BR-10 | **Verified:** Metal, GTK (SynthTreeUI), and Nimbus all return the static `AccessibleAction.TOGGLE_EXPAND` constant directly from `JTree.AccessibleJTreeNode.getAccessibleActionDescription()`. No JDK L&F overrides `AccessibleJTreeNode`. A third-party L&F could theoretically subclass it, but this is an accepted risk. See **architecture.md § 4 — Action Types Summary**. |
| BR-11 | **Return message.** On success, the dispatch wrapper returns a single text-content item: `Posted toggle-expand on ref=<N>` (see **DR-010**). |

### Algorithm: detecting and invoking the toggle-expand action

`SwingUtils.supportsToggleExpand(Accessible a)` scans `AccessibleAction` descriptions for `AccessibleAction.TOGGLE_EXPAND` and returns the action index or `-1`.

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — fail early if disabled, so the AI gets "disabled" rather than a misleading "unsupported" error (a disabled node may strip its actions).
3. **BR-04** — `int i = SwingUtils.supportsToggleExpand(accessible)` — if `i < 0`, fail.
4. `SwingUtilities.invokeLater(() -> aa.doAccessibleAction(i))` — fire-and-forget; return `null`.

---

## Tests

> See `architecture.md` § Testing for conventions.

- [x] `SwingToggleExpandTest`
  - [x] Toggling a collapsed non-leaf node expands it (verified via `JTree.isExpanded()`).
  - [x] Toggling an expanded non-leaf node collapses it.
  - [x] Invalid ref returns an MCP error with `isError: true`.
  - [x] The error message suggests calling `swing_snapshot` to refresh refs.
  - [x] Leaf node returns an MCP error with `isError: true`.
  - [x] Component without toggle-expand support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [x] Disabled `JTree` node returns an MCP error with `isError: true` explaining the component is disabled.
  - [x] Success returns the DR-010 echo `Posted toggle-expand on ref=N`.
  - [x] Ref map is cleared after a successful call.
  - [x] MCP client smoke test.
  - [x] Each component from the component matrix is tested (dedicated test method per component).

- [x] `SwingToggleExpandScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [x] `swing_toggle_expand` fails on `JFrame` itself (not a tree node).
  - [x] `swing_toggle_expand` fails on `JDialog` itself (not a tree node).
  - [x] `swing_toggle_expand` fails on `JInternalFrame` itself (not a tree node).
  - [x] `swing_toggle_expand` fails on `JDesktopPane` itself (not a tree node).

### Component matrix

`JTree` is the primary target for this tool — the only matrix component that supports `toggle_expand`.

**Succeed (`toggle_expand` supported):** `JTree` non-leaf nodes only.

**Fail with "<ClassName> does not support toggle_expand":** `JTree` leaf nodes; all other matrix components.
