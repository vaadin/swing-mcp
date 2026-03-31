# UC-010: swing_toggle_expand

---

**As an** AI agent, **I want to** expand or collapse a tree node by ref **so that** I can navigate a `JTree` hierarchy to find and interact with nested items.

**Status:** Draft
**Date:** 2026-03-31

---

## Main Flow

- I first call `swing_snapshot` to obtain refs for the current UI state.
- I call `swing_toggle_expand` with the `ref` parameter identifying the tree node.
- The tool looks up the node by ref and invokes the toggle-expand `AccessibleAction`.
- The tool returns `null` (empty content array) on success.
- I call `swing_snapshot` again to get fresh refs reflecting the updated tree (newly visible children will appear with refs).

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | The `ref` parameter is required and must be an integer. |
| BR-02 | If the ref is not found, the tool returns an MCP-level error (`isError: true`) with a recovery message suggesting to call `swing_snapshot`. |
| BR-03 | The toggle-expand action is invoked by scanning the node's `AccessibleAction` descriptions for `AccessibleAction.TOGGLE_EXPAND` (`"toggleexpand"`) and calling `doAccessibleAction(i)`. No UIManager lookup is needed — the standard `JTree` implementation uses the static constant directly. |
| BR-04 | If the target does not support toggle-expand (i.e. `SwingUtils.supportsToggleExpand(accessible)` returns `-1`), the tool returns an MCP-level error (`isError: true`) with the message "Component does not support toggle_expand. Call swing_snapshot to verify the list of actions". This covers leaf nodes (which do not receive the `TOGGLE_EXPAND` action) and all non-tree components. |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-06 | If the target is not effectively enabled (see **architecture.md § 4 — Effectively Enabled Check**), the tool returns an MCP-level error (`isError: true`) with a message explaining that the component is disabled. |
| BR-07 | If `doAccessibleAction(i)` returns `false`, the tool returns an MCP-level error (`isError: true`) with the message "The action was not performed, no additional information has been provided". |
| BR-08 | `swing_toggle_expand` is a mutation tool: `isMutation()` returns `true` and the ref map is cleared after invocation (even on failure, via `finally`). |
| BR-09 | The tool toggles the node regardless of its current expanded/collapsed state. If the node is already expanded, calling this tool collapses it; if collapsed, it expands it. The AI can infer the current state from the `EXPANDED` or `COLLAPSED` state in the snapshot. |
| BR-10 | **Known limitation:** The standard `JTree` implementation uses `AccessibleAction.TOGGLE_EXPAND` directly, but it has not been verified that every Look-and-Feel (Nimbus, GTK, Windows, etc.) upholds this. If a L&F localizes the description, detection silently fails and the node loses its ref and `toggle_expand` action. This is an accepted risk; the algorithm stays deterministic and correct for the standard L&F. See **architecture.md § 4 — Action Types Summary**. |

### Algorithm: detecting and invoking the toggle-expand action

`SwingUtils.supportsToggleExpand(Accessible a)` — scans `AccessibleAction` descriptions for `AccessibleAction.TOGGLE_EXPAND`:

```java
int supportsToggleExpand(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return -1;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return -1;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        if (AccessibleAction.TOGGLE_EXPAND.equals(aa.getAccessibleActionDescription(i)))
            return i;
    }
    return -1;
}
```

Execution order:
1. **BR-02** — ref lookup (fail fast if ref is invalid).
2. **BR-04** — `int i = SwingUtils.supportsToggleExpand(accessible)` — if `i < 0`, fail before walking the parent chain.
3. **BR-06** — `SwingUtils.isEffectivelyEnabled(accessible)` — only checked when the action exists.
4. **BR-07** — `doAccessibleAction(i)` — if it returns `false`, report failure.

---

## Acceptance Criteria

- [ ] Calling `swing_toggle_expand` on a collapsed `JTree` non-leaf node expands it.
- [ ] Calling `swing_toggle_expand` on an expanded `JTree` non-leaf node collapses it.
- [ ] Calling `swing_toggle_expand` with an invalid ref returns an MCP error with a recovery message.
- [ ] Calling `swing_toggle_expand` on a `JTree` leaf node returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_toggle_expand` on a component that does not support toggle-expand (e.g. `JButton`) returns an MCP error suggesting to call `swing_snapshot`.
- [ ] Calling `swing_toggle_expand` on a disabled `JTree` node returns an MCP error explaining the component is disabled.
- [ ] The tool returns `null` on success.
- [ ] The ref map is cleared after every `swing_toggle_expand` call (mutation tool).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingToggleExpandTest`
  - [ ] Toggling a collapsed non-leaf node expands it (verified via `JTree.isExpanded()`).
  - [ ] Toggling an expanded non-leaf node collapses it.
  - [ ] Invalid ref returns an MCP error with `isError: true`.
  - [ ] The error message suggests calling `swing_snapshot` to refresh refs.
  - [ ] Leaf node returns an MCP error with `isError: true`.
  - [ ] Component without toggle-expand support (e.g. `JButton`) returns an MCP error with `isError: true`.
  - [ ] Disabled `JTree` node returns an MCP error with `isError: true` explaining the component is disabled.
  - [ ] Success returns `null`.
  - [ ] Ref map is cleared after a successful call.
  - [ ] MCP client smoke test.
  - [ ] Each component from the component matrix is tested (dedicated test method per component).

- [ ] `SwingToggleExpandScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Expanding a collapsed `JTree` non-leaf node inside `JFrame` works.
  - [ ] Expanding a collapsed `JTree` non-leaf node inside `JDialog` works.

### Component matrix

`JTree` is not part of the standard 20-component verification matrix (see `verification.md`), but is the primary target for this tool. The standard 20 components all fail.

**Expected to succeed (`toggle_expand` supported):**
`JTree` non-leaf nodes only

**Expected to fail with "Component does not support toggle_expand" error:**
`JTree` leaf nodes, `JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JToggleButton`, `JSpinner`, `JSlider`, `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JLabel`, `JProgressBar`, `JMenuBar`, `JMenu`, `JMenuItem`, `JToolBar`, `JList`
