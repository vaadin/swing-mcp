# Architecture

A MCP server which provides concrete MCP tools designed to
manipulate a Swing application.

For testing purposes, we are leveraging the official
`modelcontextprotocol/java-sdk` Java SDK library which provides a MCP
client. We hope that the official MCP client will run in strict mode and
will throw an exception on any malformed MCP JSON message. Don't use the
`mcp-test` module - even though it looks like the perfect fit, it's the
`java-sdk` internal testing tool not meant to be used by other projects.

---

## 1. Technology Stack

- Uses the `tiny-mcp-server`
- Gradle (wrapper included)
- Java
- Testing: JUnit 6

---

## 2. Application Structure

```
com.vaadin.swingmcp.mcp
  SwingUtils.java   - any Swing-related utilities we may need; a collection of static utility methods
  MCPServer.java    - starts/stops the MCP server with Swing-MCP-specific tools
com.vaadin.swingmcp.mcp.tools - a package with all offered tools, one class per tool
```

### MCPServer

Intended lifecycle: create a new instance, start it, then stop it.
Creates a `TinyMCPServer` under the hood and registers all of the Swing-related
MCP tools.
No need to support repeated start/stop cycles.
The server binds to `127.0.0.1` only. Constructor accepts port and path similarly to `TinyMCPServer`.

The MCPServer has a protected method which calculates which Windows are considered. Since tests can't instantiate Windows in headless mode, they will
use JPanel instead => the method should return `List<Component>` instead.
The tests will override the method and will return their own component hierarchy,
whatever suits the test needs.

For upcoming Swing Tools, we create an utility class AbstractSwingTool which:

1. Implements a function similar to TinyMCPServer.ToolFunction, but also receives the list of considered components.
2. Gives assurance that it's run in Swing EDT thread

Every Swing tool must extend that class. When swing tool is registered to
MCPServer, it must register a wrapper ToolFunction which, upon invocation:

1. Calls immediately runInEDT() and runs the remainder of the function there.
2. Retrieves a list of considered components
3. Calls AbstractSwingTool.

---

## 3. Ref Lifecycle

Tools that inspect or interact with the UI use **short numeric refs** to address
components. The ref system is shared across all tools and follows these rules:

1. **Assignment.** `swing_snapshot` assigns refs starting from 1 to every node
   that exposes at least one `AccessibleAction`. Structural nodes (panels, labels,
   scroll panes, etc.) do not receive refs. The ref-to-component map is held by
   `MCPServer` and replaced in its entirety on each `swing_snapshot` call.

2. **Validity window.** Refs are valid from the moment `swing_snapshot` returns
   until the next **interaction tool call** (`swing_click`, `swing_set_text`,
   `swing_set_value`, or any future interaction tool). An interaction may change
   the component tree (e.g., clicking a button may open a dialog, setting text
   may trigger a validator that disables other fields), so stale refs cannot be
   trusted.

3. **Invalidation.** After any interaction tool call, the existing ref map is
   cleared. Subsequent attempts to use an old ref must return an MCP-level error
   (`isError: true`) with a recovery message suggesting the AI call
   `swing_snapshot` to obtain fresh refs.

4. **Expected AI workflow.** The AI is expected to follow a
   snapshot → interact → snapshot loop:
   ```
   swing_snapshot          → get refs
   swing_click ref=3       → refs invalidated
   swing_snapshot          → get fresh refs
   swing_set_text ref=1 …  → refs invalidated
   swing_snapshot          → get fresh refs
   ```

---

## 4. Accessible Actions Reference

The table below documents every `AccessibleAction` implementation in the Java SDK (Java 21),
listing the actions each class provides. This informs which MCP interaction tools make sense
for each Swing component type.

**Action description source types:**
- **Static field** — value of a constant on `AccessibleAction` (e.g. `AccessibleAction.CLICK = "click"`)
- **UIManager** — localized string looked up via `UIManager.getString(key)` at runtime
- **Literal** — hardcoded string in source (AWT legacy)
- **Algorithm** — computed at runtime; cannot be statically determined

### AccessibleAction Interface Constants

| Constant | Value |
|---|---|
| `AccessibleAction.CLICK` | `"click"` |
| `AccessibleAction.INCREMENT` | `"increment"` |
| `AccessibleAction.DECREMENT` | `"decrement"` |
| `AccessibleAction.TOGGLE_EXPAND` | `"toggleexpand"` |
| `AccessibleAction.TOGGLE_POPUP` | `"toggle popup"` |

### Per-Class Action Table

| Class | Enclosing Class | Count | Action Description | Source Type |
|---|---|---|---|---|
| `AccessibleAWTButton` | `Button` | 1 | `"click"` | Literal |
| `AccessibleAWTCheckbox` | `Checkbox` | 0 | *(not implemented)* | — |
| `AccessibleAWTCheckboxMenuItem` | `CheckboxMenuItem` | 0 | *(not implemented)* | — |
| `AccessibleAWTChoice` | `Choice` | 0 | *(not implemented)* | — |
| `AccessibleAWTMenuItem` | `MenuItem` | 1 | `"click"` | Literal |
| `AccessibleAWTMenu` | `Menu` | 1 | `"click"` *(inherited from MenuItem)* | Literal |
| `AccessibleAWTPopupMenu` | `PopupMenu` | 1 | `"click"` *(inherited via Menu)* | Literal |
| `AccessibleAbstractButton` | `AbstractButton` | 1 | `UIManager.getString("AbstractButton.clickText")` | UIManager |
| `AccessibleJButton` | `JButton` | 1 | *(inherited from AbstractButton)* | UIManager |
| `AccessibleJCheckBox` | `JCheckBox` | 1 | *(inherited from AbstractButton)* | UIManager |
| `AccessibleJRadioButton` | `JRadioButton` | 1 | *(inherited from AbstractButton)* | UIManager |
| `AccessibleJToggleButton` | `JToggleButton` | 1 | *(inherited from AbstractButton)* | UIManager |
| `AccessibleJMenuItem` | `JMenuItem` | 1 | *(inherited from AbstractButton)* | UIManager |
| `AccessibleJCheckBoxMenuItem` | `JCheckBoxMenuItem` | 1 | *(inherited via JMenuItem)* | UIManager |
| `AccessibleJRadioButtonMenuItem` | `JRadioButtonMenuItem` | 1 | *(inherited via JMenuItem)* | UIManager |
| `AccessibleJMenu` | `JMenu` | 1 | *(inherited via JMenuItem)* | UIManager |
| `AccessibleJComboBox` | `JComboBox` | 1 | `UIManager.getString("ComboBox.togglePopupText")` | UIManager |
| `AccessibleJSlider` | `JSlider` | 2 | `AccessibleAction.INCREMENT`, `AccessibleAction.DECREMENT` | Static field |
| `AccessibleJSpinner` | `JSpinner` | 2 | `AccessibleAction.INCREMENT`, `AccessibleAction.DECREMENT` | Static field |
| `AccessibleJTextComponent` | `JTextComponent` | *dynamic* | `Action.NAME` from `getActions()` — cannot be statically determined | Algorithm |
| `AccessibleJEditorPane` | `JEditorPane` | *dynamic* | *(inherited from JTextComponent)* | Algorithm |
| `AccessibleJTextArea` | `JTextArea` | *dynamic* | *(inherited from JTextComponent)* | Algorithm |
| `AccessibleJTextField` | `JTextField` | *dynamic* | *(inherited from JTextComponent)* | Algorithm |
| `AccessibleJPasswordField` | `JPasswordField` | *dynamic* | *(inherited via JTextField)* | Algorithm |
| `AccessibleJTreeNode` | `JTree.AccessibleJTree` | *dynamic* | `AccessibleAction.TOGGLE_EXPAND` (index 0, non-leaf only); remaining actions delegated to underlying component's `AccessibleAction` | Static field + Algorithm |
| `AccessibleJListChild` | `JList.AccessibleJList` | 1 | `UIManager.getString("AbstractButton.clickText")` | UIManager |
| `AccessibleHyperlink` | *(abstract base)* | *abstract* | *(abstract — subclass-defined)* | — |
| `HTMLLink` | `JEditorPane.JEditorPaneAccessibleHypertextSupport` | 1 | Anchor text extracted from the HTML document at link position | Algorithm |
| `JEditorPaneAccessibleHypertextSupport` | `JEditorPane` | — | *(hypertext support wrapper, delegates to HTMLLink)* | — |
| `AccessibleJEditorPaneHTML` | `JEditorPane` | *dynamic* | *(inherits from JTextComponent via JEditorPane)* | Algorithm |

### Action Types Summary

Action display names use lower-case underscore-separated format regardless of the raw
`AccessibleAction` constant value (e.g. `"toggleexpand"` → `toggle_expand`, `"toggle popup"` → `toggle_popup`).

| Display name | Components | Source type |
|---|---|---|
| `click` | AWT: `Button`, `MenuItem`, `Menu`, `PopupMenu` | Literal |
| `click` | Swing: all `AbstractButton` subclasses (`JButton`, `JCheckBox`, `JRadioButton`, `JToggleButton`, `JMenuItem`, `JCheckBoxMenuItem`, `JRadioButtonMenuItem`, `JMenu`), `JListChild` | UIManager |
| `toggle_popup` | `JComboBox` | UIManager |
| `increment`, `decrement` | `JSlider`, `JSpinner` | Static field |
| `toggle_expand` | `JTree` non-leaf nodes | Static field |
| Dynamic (cannot be enumerated statically) | All `JTextComponent` subclasses (`JTextField`, `JPasswordField`, `JTextArea`, `JEditorPane`, `AccessibleJEditorPaneHTML`); `HTMLLink` | Algorithm |
| None (unimplemented stubs) | AWT `Checkbox`, `CheckboxMenuItem`, `Choice` | — |

### Detecting Toggle-Popup Support

```java
boolean supportsTogglePopup(Component component) {
    AccessibleContext ac = component.getAccessibleContext();
    if (ac == null) return false;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return false;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        String desc = aa.getAccessibleActionDescription(i);
        if (AccessibleAction.TOGGLE_POPUP.equals(desc) ||
            UIManager.getString("ComboBox.togglePopupText").equals(desc))
            return true;
    }
    return false;
}
```

- `AccessibleAction.TOGGLE_POPUP` (`"toggle popup"`) covers any component that uses the constant directly.
- `UIManager.getString("ComboBox.togglePopupText")` covers `JComboBox`, which uses a potentially localized lookup.

To invoke, call `doAccessibleAction(i)` on the matching index `i`.

### Detecting Click Support

A component supports the click action if any of its accessible action descriptions matches a
known click string. Because Swing localizes the click description via `UIManager` while AWT
uses a hardcoded literal, both must be checked:

```java
boolean supportsClick(Component component) {
    AccessibleContext ac = component.getAccessibleContext();
    if (ac == null) return false;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return false;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        String desc = aa.getAccessibleActionDescription(i);
        if (AccessibleAction.CLICK.equals(desc) ||
            UIManager.getString("AbstractButton.clickText").equals(desc))
            return true;
    }
    return false;
}
```

- `AccessibleAction.CLICK` (`"click"`) covers AWT components (`Button`, `MenuItem`, `Menu`,
  `PopupMenu`) which hardcode this literal.
- `UIManager.getString("AbstractButton.clickText")` covers Swing components (`AbstractButton`
  subclasses, `JListChild`) which look up a potentially localized string. Comparing against the
  same UIManager lookup ensures locale-safe matching.
- Components with dynamic/algorithm-derived actions (text components, tree nodes, hyperlinks)
  never produce either string and are therefore excluded automatically.

To invoke the click, call `doAccessibleAction(i)` on the matching index `i`.

---

## 5. Additional Actions

Beyond `AccessibleAction`, the accessibility API exposes further interaction capabilities via
dedicated interfaces on `AccessibleContext`. Each interface returning non-null signals that the
corresponding actions are available. Since the snapshot deliberately omits field values (UC-002
BR-03), both read and write actions are needed so the AI can retrieve data it cannot see.

### Capability → Action Mapping

All action names follow lower-case underscore-separated format.

| `AccessibleContext` getter | Non-null means | Actions exposed |
|---|---|---|
| `getAccessibleText()` | Text is readable | `get_text` |
| `getAccessibleEditableText()` | Text is readable **and** writable (`AccessibleEditableText` extends `AccessibleText`) | `get_text`, `set_text` |
| `getAccessibleValue()` | Numeric value is readable; writable only if the component is not a known read-only role (see `supportsSetValue()`) | `get_value`; `set_value` only when `supportsSetValue()` |
| `getAccessibleSelection()` | Selection is readable and writable (no read-only variant in the API) | `get_selection`, `set_selection`, `clear_selection`, `select_all`, `get_children_count`, `get_children` |

### Detection

```java
boolean supportsGetText(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac != null && ac.getAccessibleText() != null;
}

boolean supportsSetText(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac != null && ac.getAccessibleEditableText() != null;
}

boolean supportsGetValue(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac != null && ac.getAccessibleValue() != null;
}

// Roles whose AccessibleValue is read-only (value changes programmatically, not by the user).
// The MCP server must only perform actions a real user can perform — exposing set_value on a
// read-only component risks putting the Swing app into an undefined state.
//
// Membership criterion: the component displays a value via AccessibleValue (non-null), but
// a real user fundamentally cannot edit that value. Add roles here as they are discovered.
//
// Components that return null from getAccessibleValue() (e.g. JInternalFrame) are excluded
// automatically by the null-check above — they never reach this set.
//
// Explicitly NOT in this set (intentional):
//   - SCROLL_BAR (JScrollBar): a user can drag the scrollbar, so set_value is a legitimate
//     action even when the scrollbar is inside a JScrollPane.
private static final Set<AccessibleRole> READ_ONLY_VALUE_ROLES = Set.of(
    AccessibleRole.PROGRESS_BAR
);

boolean supportsSetValue(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    if (ac == null || ac.getAccessibleValue() == null) return false;
    return !READ_ONLY_VALUE_ROLES.contains(ac.getAccessibleRole());
}

boolean supportsSelection(Component c) {
    AccessibleContext ac = c.getAccessibleContext();
    return ac != null && ac.getAccessibleSelection() != null;
}
```

Note: `supportsSetText` implies `supportsGetText` (since `AccessibleEditableText` extends
`AccessibleText`), so only one check is needed — check editable first, then fall back to
read-only.

---

## 6. Action Detection Summary

**Imperative: the MCP server must only expose actions a real user can perform.** Exposing write actions on read-only or programmatically-controlled components risks putting the Swing app into an undefined state. When in doubt, prefer fewer actions over more.

This imperative operates at two levels:

1. **Snapshot level (component type).** Mutation actions (`set_text`, `set_value`, `set_selection`, etc.) are listed for components that *generally* allow their value to be changed by a user — e.g. a text field, a slider. Components that are structurally read-only regardless of state (e.g. `JProgressBar`) never receive the corresponding mutation action in the snapshot. The current enabled/disabled state of the component does **not** affect which actions appear in the snapshot.

2. **Tool execution level (runtime state).** When a mutation tool is called, it must check whether the target component is currently enabled. If the component is disabled, the tool must return an MCP-level error (`isError: true`) with an informative message explaining that the component is disabled and therefore the user cannot change its value.

Authoritative mapping between spec action names, their detection mechanism, and the corresponding MCP tool.
Other specs reference this table instead of duplicating detection logic.

| Spec Action Name | Detection Method | Java Mechanism | MCP Tool | Notes |
|---|---|---|---|---|
| `click` | `supportsClick()` | `AccessibleAction.CLICK` (AWT literal) OR `UIManager.getString("AbstractButton.clickText")` (Swing UIManager) | `swing_click` | AWT hardcodes the literal; Swing uses a potentially localized UIManager lookup — both must be checked |
| `toggle_popup` | `supportsTogglePopup()` | `AccessibleAction.TOGGLE_POPUP` OR `UIManager.getString("ComboBox.togglePopupText")` | `swing_toggle_popup` | Toggles open/closed; AI can infer current state from snapshot |
| `increment` | Raw `AccessibleAction` description compare | `AccessibleAction.INCREMENT` static constant | `swing_increment` | Safe to match by raw constant — `JSlider`/`JSpinner` use the static field directly, no UIManager variant exists |
| `decrement` | Raw `AccessibleAction` description compare | `AccessibleAction.DECREMENT` static constant | `swing_decrement` | Same rationale as `increment` |
| `toggle_expand` | Raw `AccessibleAction` description compare | `AccessibleAction.TOGGLE_EXPAND` static constant | `swing_toggle_expand` | Toggles expanded/collapsed; AI can infer current state from `EXPANDED`/`COLLAPSED` in snapshot. **TODO (revisit):** The source type is "Static field" per the per-class table, meaning the standard JTree implementation uses the constant directly. However, it has not been verified that every Look-and-Feel (Nimbus, GTK, Windows, etc.) upholds this. If a L&F localizes the description, detection silently fails and the node loses its ref and action. The chosen approach is to stay deterministic: accept that a localizing L&F would silently suppress `toggle_expand` rather than risk a non-deterministic matching algorithm. Verify against non-default L&Fs when time permits. |
| `get_text` | `supportsGetText()` | `getAccessibleText()` non-null | `swing_get_text` | On `JPasswordField`, returns echo characters (masked), **not** the actual password |
| `set_text` | `supportsSetText()` | `getAccessibleEditableText()` non-null | `swing_set_text` | `supportsSetText()` implies `supportsGetText()` (`AccessibleEditableText extends AccessibleText`) |
| `get_value` | `supportsGetValue()` | `getAccessibleValue()` non-null | `swing_get_value` | All components with `AccessibleValue` expose `get_value`, including read-only ones like `JProgressBar`. |
| `set_value` | `supportsSetValue()` | `getAccessibleValue()` non-null AND role not in `READ_ONLY_VALUE_ROLES` | `swing_set_value` | Only exposed for components a user can actually modify. `JProgressBar` is explicitly excluded. Add other read-only value roles to `READ_ONLY_VALUE_ROLES` as discovered. |
| `get_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_selection` | Returns a list of integer indices of currently selected children. |
| `set_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_set_selection` | Accepts a list of integer indices of children to select. Replaces the current selection. |
| `clear_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_clear_selection` | Clears the current selection. Equivalent to `set_selection` with an empty list. |
| `select_all` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_select_all` | Selects all children. |
| `get_children_count` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_children_count` | Returns the number of accessible children that can potentially be selected. |
| `get_children` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_children` | Returns a paged accessibility tree dump of the component's accessible children. Parameters: `ref` (integer), `offset` (integer, 0-based), `length` (integer, max children to return). The output format mirrors `swing_snapshot` — the same indented text tree — but rooted at the requested children rather than the full UI. Intended for AI agents that need to browse selectable items before issuing `set_selection`. |

---

## 7. Testing

There are two test source sets:

### `src/test` — Headless tests (default `test` task)

- Pure JUnit 6 tests living in `src/test/java/`, mirroring the main package structure.
- Also uses MCP client library to call MCP tasks.
- Sets `java.awt.headless` to `true` for fast, display-free execution.
- The `MCPServerTest` test class:
  - Starts the MCPServer before all tests, stops it afterwards.
  - A test client is initialized before all tests as well; use the official MCP client with the HTTP Transport and Jackson3.
  - No tools are tested here: each tool has its own separate test class.
- For every tool test class:
  - Remember we are headless.
  - `SwingUtilities.invokeAndWait()` will fail — needs to go into protected function `MCPServer.runInEDT(block)`; the tests will override and simply run the block right away. It is thread-unsafe; if the tests fail we will revisit and think of some locking mechanism.

### `src/testSwing` — Screen-mode tests (`testSwing` task)

- Runs with `java.awt.headless = false`, so actual Swing rendering works.
- Requires a real display (Xvfb is used in CI).
- Registered as the `testSwing` Gradle task in the `verification` group; included in the `check` lifecycle.
- Reports go to `build/reports/testSwing/` and `build/test-results/testSwing/` (separate from headless test reports).
- Base class: `AbstractScreenTest`; inherits from it for screen-dependent tool tests.
- These tests should primarily focus on testing with JFrame/JDialog/Dialog/Window since those Swing components require screen.

