# Problems the 2026-09-23 javadoc pass found

The javadoc proof-read covered every `.java` file, comments only. Wherever a doc and the code
disagreed and the code was clearly right, the doc was fixed. Everything below is what was left
untouched, because the fix belongs in code, a test or a design doc.

Treat each item as a lead, not a verdict. It comes from an agent reading the code, not from a
run or a failing test. Unless an item is marked **checked**, nobody has confirmed it. Line
numbers are from the commit after the pass.

## Possible code bugs — swing-mcp

- **`SwingUtils.createDragAction`** dispatches the whole drag from one `Runnable`, so a listener
  that throws on the press aborts the drags and the release. A real mouse sends each event
  separately, and the EDT carries on after one throws. This only affects the synthetic, headless
  path. **Checked:** `R_ui_delegate_press_throws` has two delegates that do this.

## Design docs out of step with the code

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
