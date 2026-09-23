# Research — `javax.accessibility` as Swing implements it, and the MCP client driving it

What the things we don't own actually do. About *them*, never us: a sentence starting "we chose"
is a `D_`. `## R_<slug> — <title>`, one claim per bullet, one provenance marker per claim —
**[docs]**, **[src]**, **[verified <date>, <version>]**, **[unverified]** (a hypothesis; a design
built on it says so). A claim is earned by its provenance, or by having cost real work to find
out. Checked against Java 21 OpenJDK unless a claim names its own version (the MCP-client
entries always do: a client, unlike Swing, is not frozen); Swing is frozen, so a
finding here is not expected to rot. Cite by slug, `R_<slug>`, never by position;
`grep '^## R_' design/research.md` is the index. The first entry is the ruler: every later one
trims to its length — which is how long this file gets, so keep it short.

---

## R_accessible_action_impls — Which components implement `AccessibleAction`, and how the description is sourced

- The action a component advertises is matched by its **description string**, and that string
  comes from one of four places: an `AccessibleAction` constant, a `UIManager` lookup, a
  hardcoded literal, or a runtime computation. Only the first two are matchable. **[src]**
- AWT (`Button`, `MenuItem`, `Menu`, `PopupMenu`) hardcodes the literal `"click"`. Swing's
  `AbstractButton` family and `JList` children instead use
  `UIManager.getString("AbstractButton.clickText")`, which is **localized** — matching must go
  through the same lookup, not a hardcoded English string. **[src]**
- `JComboBox` uses `UIManager.getString("ComboBox.togglePopupText")`; `JSlider` and `JSpinner`
  use the `INCREMENT` / `DECREMENT` constants directly; `JTree` non-leaf nodes use
  `TOGGLE_EXPAND`. The constants carry no localization, so those are safe to compare raw. **[src]**
- `JTextComponent` and its subclasses compute actions from `getActions()` at runtime, so their
  action set **cannot be enumerated statically** and never matches a click string. **[src]**
- AWT `Checkbox`, `CheckboxMenuItem` and `Choice` declare `AccessibleAction` but implement zero
  actions — the interface is present and empty. **[src]**
- Not covered by any of this: a component whose click behaviour is a plain `MouseListener`
  exposes no `AccessibleAction` at all, which is why click detection needs a second tier. **[src]**

## R_disabled_not_propagated — `setEnabled(false)` does not propagate to children

- `Component.setEnabled(false)` on a container does **not** disable its descendants; a `JButton`
  inside a disabled `JPanel` still receives and acts on clicks. Filed as JDK-4177727 and closed
  won't-fix. **[docs]**
- Two places where Swing *does* propagate, both invisible in the state set: a tab disabled via
  `JTabbedPane.setEnabledAt(i, false)` becomes non-navigable, and virtual `JTable` cells follow
  their host table. **[verified 2026-04-13, Java 21]**
- `AccessiblePage` (a tab's virtual child) keeps `ENABLED` in its state set even when the tab is
  disabled — the state set disagrees with `JTabbedPane.isEnabledAt(idx)`, which is correct. **[verified 2026-04-13, Java 21]**
- `JTable` cells likewise keep `ENABLED` when the host table is disabled. **[verified 2026-04-13, Java 21]**
- `JTabbedPane.setEnabledAt` is the **only** per-item disable in standard Swing; `JList`,
  `JComboBox` and `JTable` have no equivalent — disabling one item there takes a custom renderer
  and a selection-model override. **[docs]**
- `Window.setEnabled(false)` blocks input at the OS peer but paints inconsistently across
  platforms and look-and-feels — too unreliable to model. **[unverified]**

## R_accessible_value_types — `AccessibleValue`: return types and write behaviour

- `getCurrentAccessibleValue()` returns `Integer` for `JSlider`, `JSplitPane` and `JProgressBar`;
  for `JSpinner` it mirrors the model's own value type. `getMinimum` / `getMaximum` return the
  same type as `getCurrent`. **[verified 2026-04-08, Java 21]**
- `setCurrentAccessibleValue(Number)` on `JSlider` and `JSplitPane` calls `intValue()` — it
  **truncates**, and `JSlider` additionally **clamps** to `[min, max]`. **[verified 2026-04-08, Java 21]**
- `JProgressBar` accepts the write and mutates the bar; the JDK does not block it. **[verified 2026-04-08, Java 21]**
- `JSpinner` with a `SpinnerNumberModel` stores the `Number` **as-is, with no conversion** — a
  `Double` written to an int-typed model leaves a `Double` in the model, after which the
  spinner's own increment logic compares `Double` against `Integer` bounds. **[verified 2026-04-08, Java 21]**
- `SpinnerDateModel` and `SpinnerListModel` reject a non-matching value and return `false`. **[verified 2026-04-08, Java 21]**
- `JTabbedPane` (role `PAGE_TAB_LIST`) returns `null` from `getAccessibleValue()`, so it fails a
  value probe on its own without needing to be excluded by role. **[verified 2026-04-08, Java 21]**

## R_jspinner_accessibility — `JSpinner` misreports its own value capability

- `AccessibleJSpinner.getAccessibleValue()` returns `this` for **every** model type, so a
  non-null check says every spinner has a value. `getCurrentAccessibleValue()` is the honest
  probe: it returns `null` for `SpinnerDateModel`, and for `SpinnerListModel` unless the current
  element happens to be a `Number`. **[verified 2026-04-08, Java 21]**
- `AccessibleJSpinner` implements the `AccessibleEditableText` interface but does **not** override
  the `AccessibleContext.getAccessibleEditableText()` factory method, so that factory returns
  `null` and the obvious entry point silently fails. Casting `getAccessibleText()` works, because
  it is the same instance. **[verified 2026-04-08, Java 21]**
- The type-safe write path is the editor's own text field:
  `((JSpinner.DefaultEditor) spinner.getEditor()).getTextField()`, then `setTextContents(str)`
  followed by `commitEdit()` — the formatter parses and calls `setValue` with the right type.
  `commitEdit()` throws `ParseException` on input the formatter rejects. **[verified 2026-04-08, Java 21]**
- `setTextContents` alone changes only the display; without `commitEdit()` the model is untouched. **[verified 2026-04-08, Java 21]**

## R_selection_index_spaces — `AccessibleSelection` mixes three different index spaces

- Three spaces coexist: **selection-relative** (`getAccessibleSelection(i)` — the i-th *selected*
  item), **accessible-children** (`getAccessibleChild(i)`), and **item index** (what
  `addAccessibleSelection(i)` and `isAccessibleChildSelected(i)` expect). Only the last is usable
  for writing a selection. **[docs]**
- For `JList` and `JTabbedPane` all three coincide. **[verified 2026-04-07, Java 21]**
- For `JComboBox` they do not: `getAccessibleChildrenCount()` is **1** — the popup menu, entirely
  unrelated to the combo's items. A selected item's own parent is the internal popup list, not
  the combo box. **[verified 2026-04-07, Java 21]**
- For `JTable` the children are cells in row-major order, so the item index is a cell index and
  a row is `cellIndex / columnCount`. **[verified 2026-04-07, Java 21]**
- `getAccessibleIndexInParent()` on an item returned by `getAccessibleSelection(i)` equals that
  item's index in the `addAccessibleSelection` space, for every component checked — which is what
  makes reading a selection back possible at all. **[verified 2026-04-07, Java 21]**
- A `JComboBox`'s selection API reports the selected item correctly even while the popup is
  closed, so nothing has to be opened to read it. **[verified 2026-04-07, Java 21]**
- `JTree`'s tree-level `AccessibleSelection` is non-functional — `getAccessibleSelectionCount()`
  stays 0 however the tree is selected. The selection lives one level down: each **node** child
  exposes its own `AccessibleSelection` reporting which of *its* children are selected, so
  reading a tree selection means walking the hierarchy rather than asking the tree. **[verified 2026-04-07, Java 21]**

## R_multiselectable_not_reported — `JTable` does not report `MULTISELECTABLE`

- A `JTable` in a multi-selection mode does **not** carry `AccessibleState.MULTISELECTABLE` in
  its state set, so the state set alone cannot tell single from multi. The honest probe is
  `getSelectionModel().getSelectionMode()`. **[verified 2026-04-07, Java 21]**
- `JTable.getAccessibleSelection()` is non-null in **every** mode, including no-selection mode,
  so a null check cannot stand in for a mode check either. **[verified 2026-04-07, Java 21]**
- `JList` reports the state correctly, so the divergence is `JTable`-specific: `SINGLE_SELECTION`
  omits `MULTISELECTABLE`, both interval modes carry it. It does not separate contiguous-only
  from arbitrary selection. **[verified 2026-04-07, Java 21]**

## R_accessible_selection_writes — Clearing and selecting-all are each broken on one component

- `clearAccessibleSelection()` clears as documented on `JList` (both modes), `JComboBox` (to
  `selectedIndex=-1`, a valid blank state) and `JTable` (row modes). On `JTabbedPane` it is a
  silent **no-op** — the selected tab stays selected and the count stays 1. A tabbed pane has no
  empty selection to reach. **[verified 2026-04-07, Java 21]**
- `selectAllAccessibleSelection()` is a complete **no-op on `JTable`**: on a 5×3 table it selects
  0 rows, 0 columns and reports an accessible selection count of 0. `JTable.selectAll()` does the
  real thing — 5 rows, count 15 — and flips neither `rowSelectionAllowed` nor
  `columnSelectionAllowed`. **[verified 2026-04-08, Java 21]**
- The same call works correctly on `JList` in both interval modes, at 0 items and at 200. On a
  `SINGLE_SELECTION` list it neither throws nor selects all: each interval add replaces the last,
  so exactly the final item ends up selected. **[verified 2026-04-08, Java 21]**

## R_jtable_selection_in_invokelater — `JTable`'s accessible selection write is lost inside `invokeLater`

- `AccessibleJTable.addAccessibleSelection(i)` delegates to `JTable.changeSelection()`. Called
  synchronously on the EDT it works; called inside a `SwingUtilities.invokeLater` block it is
  **silently dropped** — no exception, and `getSelectedRows()` comes back empty. **[verified 2026-04-07, Java 21]**
- `JTable.addRowSelectionInterval(row, row)` works in both positions, so the direct API is the
  only reliable way to write a table selection from a posted block. **[verified 2026-04-07, Java 21]**
- Why it happens is not established; `changeSelection()` appears to depend on event-dispatch
  state that a posted block does not have. **[unverified]**

## R_jtable_cells_stamped — `JTable` cells are painted, not present

- Cell renderers are stamp-painted through `CellRendererPane`: the renderer `Component` is never
  added to the component hierarchy, so it never reaches the accessibility tree. **[docs]**
- The accessibility API therefore exposes virtual children whose names come from the cell value's
  `toString()`. A column rendered with a `JButton` still surfaces as a `LABEL` with no click
  action. **[verified 2026-04-13, Java 21]**
- An interactive cell **editor** does appear in the tree, but only while that cell is actively
  being edited — it cannot be reached by walking. **[verified 2026-04-13, Java 21]**

## R_iconified_windows — What iconifying does to the accessibility tree

- `AccessibleJFrame` never reports `ICONIFIED`. After `setState(Frame.ICONIFIED)`, `getState()`
  returns 1 and `WindowStateEvent` fires, but
  `getAccessibleStateSet().contains(AccessibleState.ICONIFIED)` is `false`. **[verified 2026-04-14, Java 21]**
- An iconified `JFrame` stays `isShowing() == true` and its children stay accessible — it looks
  entirely normal through the accessibility API, and `printAll()` still renders its full content. **[verified 2026-04-16, Java 21]**
- An iconified `JInternalFrame` in a `JDesktopPane` behaves oppositely: it is removed from both
  trees (`parent` becomes `null`, `isShowing()` is `false`, and `ICONIFIED` is *not* in its state
  set) and a `JDesktopIcon` takes its place as a child of the desktop pane. **[verified 2026-04-14, Java 21]**
- Outside a `JDesktopPane` (a plain `JLayeredPane` or `JPanel`) the swap never happens:
  `DefaultDesktopManager.iconifyFrame` returns early when `getDesktopPane()` is `null`, so the
  frame stays in place, showing, with its content, `isIcon() == true`, and its icon has no
  parent. A custom `DesktopManager` that skips the swap has the same result. **[src]**
  **[verified 2026-09-23, Temurin 11.0.32 / 17.0.20, Metal, Xvfb]**
- On macOS Aqua the `JDesktopIcon` is nested inside a non-accessible
  `AquaInternalFramePaneUI$Dock` wrapper, so the desktop pane reports
  `getAccessibleChildrenCount() == 0` after iconification. Metal and Windows expose it directly. **[verified 2026-04-14, Java 21]**
- `JInternalFrame.JDesktopIcon` is a public class, and `setDefaultCloseOperation(EXIT_ON_CLOSE)`
  on a `JInternalFrame` is silently accepted while `doDefaultCloseAction()` then does nothing. **[verified 2026-04-14, Java 21]**

## R_html_label_accessible_text — An `<html>` label gains a text surface a plain one lacks

- A `JLabel` with plain text exposes no `AccessibleText`. The same label with its string wrapped
  in `<html>…</html>` gains an `AccessibleHTMLTextSupport`-backed `AccessibleText`, purely as a
  side effect of how HTML rendering is plumbed into accessibility. **[verified 2026-04-15, Java 21]**
- The two labels are indistinguishable to a user and carry the same role, so any capability
  keyed on `getAccessibleText() != null` diverges on an invisible authoring choice. **[verified 2026-04-15, Java 21]**

## R_password_echo_chars — A password field reads back as echo characters, not as nothing

- `AccessibleJPasswordField.getAccessibleText()` returns the **echo characters** — it neither
  refuses nor returns the password, so a caller that only null-checks gets a plausible-looking
  string. **[docs]**
- That string's length equals the real password's, so it leaks the one property of a credential
  that is useful to an attacker who has everything else. **[docs]**

## R_editable_combo_children — An editable `JComboBox` exposes its editor as a child

- With `setEditable(true)`, `getAccessibleChildrenCount()` is 2: child 0 is the
  `BasicComboPopup` (no name, no actions) and child 1 is the editor, a `JTextField` subclass with
  both `AccessibleText` and `AccessibleEditableText`. **[verified 2026-04-08, Java 21]**
- With the default `setEditable(false)` the count is 1 — the popup only, no text child. **[verified 2026-04-08, Java 21]**
- Consequence: a type-to-filter combo box needs no special handling, because its editor is
  already an ordinary text node in the tree. **[verified 2026-04-08, Java 21]**

## R_jslider_actions_since_17 — `JSlider` gained increment/decrement actions after Java 11

- On Java 11 `AccessibleJSlider` is declared `implements Serializable, AccessibleComponent,
  AccessibleExtendedComponent, AccessibleValue`; `getAccessibleAction()` returns `null`. **[docs]**
  **[verified 2026-09-16, Temurin 11.0.32]**
- On Java 17 the same class adds `AccessibleAction`, `ChangeListener` and `EventListener`, and
  advertises exactly two actions: index 0 `increment`, index 1 `decrement`. **[docs]**
  **[verified 2026-09-16, JBR 17.0.9 / Temurin 21.0.11 / Temurin 24.0.2]**
- `AccessibleValue` is present on both, so below 17 a slider still reads and writes its value —
  it loses only the two step actions, not its whole surface. **[verified 2026-09-16, Temurin 11.0.32]**
- Consequence: a capability keyed on `getAccessibleAction()` is JDK-dependent for this one
  component, so a test that hardcodes either answer fails on the other side of the boundary.

## R_claude_code_http_lifecycle — What Claude Code does when an HTTP MCP server comes and goes

- A server unreachable at client startup is marked `failed`, and its tools are absent for the
  whole session. The client never retries it on its own: in 90 s with the server back up, it sent
  zero requests. **[verified 2026-09-23, Claude Code 2.1.280]**
- `/mcp` → the server → **Reconnect** brings that server's tools in without restarting the
  client. **[verified 2026-09-23, Claude Code 2.1.280, interactive]**
- A server restarted mid-session is recovered at the next tool call. The client sends the old
  `Mcp-Session-Id`, gets a 404, re-initializes and succeeds, and the model sees no error.
  **[verified 2026-09-23, Claude Code 2.1.280]**
- While a server is down, its tools stay listed and each call returns `isError` with
  `ECONNREFUSED: Unable to connect. Is the computer able to access the url?`. The first call
  after it is back succeeds. **[verified 2026-09-23, Claude Code 2.1.280]**
- On a reconnect it initializes more than once in quick succession, so a one-session server
  supersedes its own new sessions a couple of times before settling. It settled every time.
  **[verified 2026-09-23, Claude Code 2.1.280]**
- Before `initialize` it POSTs `server/discover` without a session header, and falls back to
  `initialize` when that is rejected. **[verified 2026-09-23, Claude Code 2.1.280]**
- Recipe: register the endpoint in a JSON file (`{"mcpServers":{"swing":{"type":"http","url":…}}}`)
  and run `claude -p --mcp-config <file> --strict-mcp-config --output-format stream-json
  --verbose`, prompting the child to start and stop the application through Bash (allow its
  script with `--allowedTools`) between tool calls. Launch the application under
  `xvfb-run -a java -javaagent:swing-mcp-agent.jar …`; its JUL log (`unknown Mcp-Session-Id`,
  `Session superseded`) shows what the client actually sent.
