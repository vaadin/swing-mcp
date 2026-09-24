# Problems the 2026-09-23 javadoc pass found

The javadoc proof-read covered every `.java` file, comments only. Wherever a doc and the code
disagreed and the code was clearly right, the doc was fixed. Everything below is what was left
untouched, because the fix belongs in code, a test or a design doc.

Treat each item as a lead, not a verdict. It comes from an agent reading the code, not from a
run or a failing test. Unless an item is marked **checked**, nobody has confirmed it. Line
numbers are from the commit after the pass.

## Possible code bugs — swing-mcp

- **`SwingGetSelectionTool`**: `selectedCount` is `selected.size()`, so it undercounts when the
  result is truncated or holds null entries. It is documented as-is for now.
- **`SwingUtils.createDragAction`** dispatches the whole drag from one `Runnable`, so a listener
  that throws on the press aborts the drags and the release. A real mouse sends each event
  separately, and the EDT carries on after one throws. This only affects the synthetic, headless
  path. **Checked:** `R_ui_delegate_press_throws` has two delegates that do this.

## Tests that prove less than they claim

- **Tests that assert nothing:**
  - `SwingClickToolTest.componentMatrix_JTabbedPane`
  - `componentMatrix_JScrollPane` in `SwingDecrementTest`, `SwingIncrementTest`,
    `SwingToggleExpandTest` and `SwingTogglePopupTest`
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
- **`tiny-mcp-server/design/research.md` candidates**, now kept only in javadoc:
  - MCP 2025-03-26: `content` has no `minItems`, so an empty array is valid (in `ToolFunction`).
  - `MCP-Protocol-Version` is required from 2025-06-18 (in `TinyMCPClient`).
