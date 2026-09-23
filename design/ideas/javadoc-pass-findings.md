# Problems the 2026-09-23 javadoc pass found

The javadoc proof-read covered every `.java` file, comments only. Wherever a doc and the code
disagreed and the code was clearly right, the doc was fixed. Everything below is what was left
untouched, because the fix belongs in code, a test or a design doc.

Treat each item as a lead, not a verdict. It comes from an agent reading the code, not from a
run or a failing test. Unless an item is marked **checked**, nobody has confirmed it. Line
numbers are from the commit after the pass.

## Possible code bugs — swing-mcp

- **`SnapshotNode.renderFiltered`** (`SnapshotNode.java:701`): suppose an iconified frame does not
  match the filter. It gets `renderSelfLine` and then recurses into its matching children, which
  emits them without refs. That breaks `D_iconified_children_hidden`, because the unfiltered
  `render` emits the placeholder instead.
  **Checked:** the code does recurse regardless of the iconified state.
  **Still open:** whether the built tree holds an iconified frame's children at all
  (`Q_iconified_filter`). A filtered snapshot test over an iconified frame would settle it.
- **`SwingUtils.isIconified`** (`SwingUtils.java:446`): the `JInternalFrame` branch requires
  `iframe.isShowing() && iframe.isIcon()`. `R_iconified_windows` says an iconified internal
  frame is not showing, so the branch may never return true. The desktop icon covers restore,
  so this could be dead code rather than a bug.
  **Checked:** the code has that shape.
- **`SwingUtils.resolveComponentAndPoint`** (`:867`): it treats a virtual child's `getBounds()` as
  relative to the first `Component` ancestor. For a nested `JTree` node the bounds may be
  relative to the virtual parent instead. Unverified.
- **`SwingGetSelectionTool`**: `selectedCount` is `selected.size()`, so it undercounts when the
  result is truncated or holds null entries. It is documented as-is for now.
- **`SwingToolContext.getAccessibleByRef`** throws `MCPServerException(INVALID_PARAMS)`, not the
  `MCPErrorResponseException` that `AGENTS.md` asks for on a tool-level failure. Either the code
  or the convention is wrong (`Q_unknown_ref_error`). An unknown ref is arguably a
  protocol-level bad parameter.

## Possible code bugs — tiny-mcp-server

- **`HttpMCPServer.stop()`** (`:136`) never runs `onSessionClosed` for the live sessions either.
  This is now documented on `handler.stop()`; decide whether that is intended.
- **`JsonRpcExchange`**: only tests call `sendResponseRaw` (`:84`), and it builds JSON by string
  concatenation, against the POJO mapping everything else uses. Nothing calls
  `sendError(int, String)` (`:89`). Both look like dead code.

## Tests that prove less than they claim

- **Tests that assert nothing:**
  - `SwingClickToolTest.componentMatrix_JTabbedPane`
  - `componentMatrix_JScrollPane` in `SwingDecrementTest`, `SwingIncrementTest`,
    `SwingToggleExpandTest` and `SwingTogglePopupTest`
  - `SwingDragToolTest.jListAsSourceDragsWithoutError` and
    `virtualChildItemAsSourceResolvesToHostJList` pass as long as the drag does not throw
- **Tests that assert `getRefOf` throws.** architecture.md § Testing calls that testing the
  harness, not the tool. They are in many headless tests, and in the JFrame / JDialog /
  JOptionPane matrix rows of the TogglePopup, Increment, Decrement and ToggleExpand screen tests.
  `SwingGetSelectionTest`, `SwingGetTextTest` and `SwingSetValueTest` are done: their
  not-supported helper registers the component under ref 99 and asserts the whole refusal.
- **`SwingUtilsSupportsTextTest.customLabelRoleComponent…`** uses a JLabel subclass, so it does
  not show the gate works for a component that isn't a JLabel.
- **`SessionCloseTest`**: session 2 always gets a fresh context, whatever happened to session 1's
  map, so the test may not prove anything.
- **`SwingIconifyScreenTest`**:
  - `jframeIsIconified` checks `text.contains("actions: ") && text.contains("iconify")`,
    which is weak.
  - `alreadyIconified*` sets ICONIFIED without waiting for the window manager, so it may be flaky.
- **`InputSchemaEqualityTest.propertyEnumOrderMatters`** pins a choice that tiny-mcp-server's
  structural-schema-equality decision does not cover: whether enum order matters.

## Design docs out of step with the code

- **`D_inline_value_preview`** says exactly one of `text=` and `value=` may appear. A JSpinner
  line shows both, and so does a custom widget that passes both gates. Rewrite the entry, or
  fix the code.
- **`snapshot-format.md`**:
  - It says `single-selection` implies `swing_get_items` and `swing_get_item_count`, but a
    JTabbedPane advertises `single-selection` and both tools refuse it.
  - It claims to own `swing_get_cells` output, but the header line
    (`Showing N children from offset O (total T) for <role> [ref=1]`) and the `- null`
    placeholder exist only in code and in the `SwingGetCellsTool` javadoc.
- **The preview-cap rules** live only in `SnapshotNode` comments: the cap is counted after quote
  escaping, with no dangling backslash and no trailing space before `…`. They belong in
  `snapshot-format.md` or `D_inline_value_preview`.
- **`tiny-mcp-server/design/architecture.md`**: step 5 of the stdio flow doesn't mention that
  `closeAllSessions` runs at EOF.
- **`tiny-mcp-server/design/research.md` candidates**, now kept only in javadoc:
  - MCP 2025-03-26: `content` has no `minItems`, so an empty array is valid (in `ToolFunction`).
  - `MCP-Protocol-Version` is required from 2025-06-18 (in `TinyMCPClient`).
