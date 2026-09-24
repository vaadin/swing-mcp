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
