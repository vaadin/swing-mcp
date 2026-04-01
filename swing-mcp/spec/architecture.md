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
- Java — **minimum runtime target: Java 17**. Do not use APIs introduced after Java 17 (e.g. `List.getFirst()` is Java 21+). The library is intended to be dropped into existing Swing apps that may run on Java 17.
- Testing: JUnit 6

---

## 2. Application Structure

```
com.vaadin.swingmcp.mcp
  SwingUtils.java   - any Swing-related utilities we may need; a collection of static utility methods
  MCPServer.java    - starts/stops the MCP server with Swing-MCP-specific tools
com.vaadin.swingmcp.mcp.tools - a package with all offered tools, one class per tool
  Parameters.java   - typed wrapper around the raw Map<String, Object> from MCP requests
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
4. After runInEDT() returns, checks whether the tool produced a `PostVerification` (see below). If so, runs the polling loop on the HTTP thread.

### PostVerification

Some tools need to verify a side-effect that may complete asynchronously after the EDT
phase returns (e.g. `swing_close` checking whether the window actually disappeared).
Because sleeping on the EDT would deadlock, the polling must happen on the HTTP thread —
but each individual check must still run on the EDT via `runInEDT()`.

`AbstractSwingTool` exposes:

```java
// Set by the tool during execute(), if post-EDT verification is needed.
// Null by default — most tools leave this unset.
protected PostVerification postVerification;

class PostVerification {
    int[]            delayScheduleMs; // sleep durations between checks, e.g. {100, 200, 700}
    Callable<Boolean> isDone;         // checked on EDT; true = side-effect has completed
    String           pendingMessage;  // appended to the result if still pending after all delays
}
```

The MCPServer wrapper runs the polling loop on the HTTP thread after `runInEDT()` returns:

```java
PostVerification v = tool.postVerification;
if (v != null) {
    for (int delay : v.delayScheduleMs) {
        Thread.sleep(delay);              // HTTP thread sleeps; EDT is free to process tasks
        if (runInEDT(v.isDone)) return success;
    }
    return success + "\n" + v.pendingMessage;
}
```

`runInEDT()` is the same protected method that tests override to run blocks directly without
`SwingUtilities.invokeAndWait()`. Headless tests therefore work correctly: the tool sets
`postVerification` only when a `Window` is actually being closed, a path that is never reached
in headless tests (no `Window` instances exist, so `supportsClose()` returns false).

### Parameters

A typed wrapper around the raw `Map<String, Object>` that MCP tool functions receive.
Lives in `com.vaadin.swingmcp.mcp.tools.Parameters`. All tools use this instead of
accessing the map directly.

Each getter has a required variant (throws `MCPServerException(INVALID_PARAMS, …)` if
missing or wrong type) and an optional variant (returns `null` if missing, throws on
wrong type).

```java
class Parameters {
    Parameters(Map<String, Object> raw);

    // Required — throws MCPServerException(INVALID_PARAMS) if key is missing or value is not a String
    String getString(String key);
    // Optional — returns null if key is missing; throws if present but not a String
    String getStringOrNull(String key);

    // Required — throws MCPServerException(INVALID_PARAMS) if key is missing or value is not a Number.
    // Converts to int via Number.intValue().
    int getInt(String key);
    // Optional — returns null if key is missing; throws if present but not a Number
    Integer getIntOrNull(String key);
}
```

Error messages must name the parameter and the expected type, e.g.:
`"Required parameter 'ref' is missing"`, `"Parameter 'ref' must be an integer"`.

`AbstractSwingTool.execute` receives `Parameters` (constructed by `MCPServer.registerTool`
from the raw map) instead of `Map<String, Object>`.

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
   cleared — even if the tool fails with an exception (use a `finally` block).
   The component tree may be in a partially modified state after a failure, so
   stale refs cannot be trusted. Subsequent attempts to use an old ref must
   return an MCP-level error (`isError: true`) with a recovery message
   suggesting the AI call `swing_snapshot` to obtain fresh refs.
   `swing_get_children` also replaces the ref map (with a fresh local numbering
   scoped to its output window), even though it is an inspection tool, not an
   interaction tool. Refs from a prior `swing_snapshot` or `swing_get_children`
   call are no longer valid after a `swing_get_children` call.

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

All `supports*` methods that detect an `AccessibleAction` return the **action index** (≥ 0)
on success, or **-1** if the action is not found. The caller can pass the returned index
directly to `doAccessibleAction(i)`, avoiding a second scan.

```java
int supportsTogglePopup(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return -1;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return -1;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        String desc = aa.getAccessibleActionDescription(i);
        if (AccessibleAction.TOGGLE_POPUP.equals(desc) ||
            UIManager.getString("ComboBox.togglePopupText").equals(desc))
            return i;
    }
    return -1;
}
```

- `AccessibleAction.TOGGLE_POPUP` (`"toggle popup"`) covers any component that uses the constant directly.
- `UIManager.getString("ComboBox.togglePopupText")` covers `JComboBox`, which uses a potentially localized lookup.

### Detecting Click Support

An `Accessible` supports the click action if any of its accessible action descriptions matches a
known click string. Because Swing localizes the click description via `UIManager` while AWT
uses a hardcoded literal, both must be checked:

```java
int supportsClick(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return -1;
    AccessibleAction aa = ac.getAccessibleAction();
    if (aa == null) return -1;
    for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
        String desc = aa.getAccessibleActionDescription(i);
        if (AccessibleAction.CLICK.equals(desc) ||
            UIManager.getString("AbstractButton.clickText").equals(desc))
            return i;
    }
    return -1;
}
```

- `AccessibleAction.CLICK` (`"click"`) covers AWT components (`Button`, `MenuItem`, `Menu`,
  `PopupMenu`) which hardcode this literal.
- `UIManager.getString("AbstractButton.clickText")` covers Swing components (`AbstractButton`
  subclasses, `JListChild`) which look up a potentially localized string. Comparing against the
  same UIManager lookup ensures locale-safe matching.
- Components with dynamic/algorithm-derived actions (text components, tree nodes, hyperlinks)
  never produce either string and are therefore excluded automatically.

### Effectively Enabled Check

Virtual accessible children (e.g. `JList` items, `JTable` cells) may not propagate the
parent component's disabled state into their own `AccessibleStateSet`. A mutation tool must
therefore walk the accessible parent chain to determine whether the target is *effectively*
enabled:

```java
boolean isEffectivelyEnabled(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null) return false;
    if (!ac.getAccessibleStateSet().contains(AccessibleState.ENABLED)) return false;
    Accessible parent = ac.getAccessibleParent();
    return parent == null || isEffectivelyEnabled(parent);
}
```

- The accessible itself must have `ENABLED` in its state set.
- If it has a parent (`getAccessibleParent()` non-null), the parent must also be effectively
  enabled — recursively up to the root.
- A `null` parent means the root of the accessible hierarchy has been reached; the chain is
  considered enabled.

All mutation tools (`swing_click`, `swing_set_text`, `swing_set_value`, etc.) must use
`isEffectivelyEnabled()` rather than checking only the target's own state set. If the check
returns `false`, the tool returns an MCP-level error (`isError: true`) explaining that the
component is disabled.

### Detecting Close Support

`supportsClose` is a **synthetic** action — it is not derived from `AccessibleAction` or any
`AccessibleContext` interface. It is exposed for top-level windows (JFrame, JDialog) only.
The close is performed by dispatching `WindowEvent.WINDOW_CLOSING` to the window, which mirrors
exactly what the OS close button does and allows the app's `WindowListener`s and
`defaultCloseOperation` to handle the event normally. The tool does **not** bypass
`DO_NOTHING_ON_CLOSE` — if the app ignores the event, the window stays open.

`JOptionPane` is intentionally excluded: it is a `JComponent`, not a window. Its containing
`JDialog` is itself a `Window` and will already expose `close` directly, so the AI can always
dismiss the dialog via the `dialog` node ref.

```java
boolean supportsClose(Accessible a) {
    if (!(a instanceof Window window)) return false;
    if (!window.isShowing()) return false;
    if (window instanceof Frame f && f.isUndecorated()) return false;
    if (window instanceof Dialog d && d.isUndecorated()) return false;
    if (window instanceof JFrame jf &&
            jf.getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
    return true;
}
```

- Returns `true` for JFrame/JDialog when the window is showing, has decorations, and will not
  terminate the JVM on close.
- Returns `false` for all other component types, including `JOptionPane`.
- Undecorated windows (`setUndecorated(true)`) have no visible close button, so the user
  cannot close them through the normal UI — `supportsClose` returns `false` for those.
- `JFrame` with `EXIT_ON_CLOSE` is explicitly excluded: dispatching `WINDOW_CLOSING` would
  terminate the JVM, taking the swing-mcp server down with it and dropping the AI client
  connection with no explanation. The `close` action is never advertised for such frames.

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
boolean supportsGetText(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    return ac != null && ac.getAccessibleText() != null;
}

boolean supportsSetText(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    return ac != null && ac.getAccessibleEditableText() != null;
}

boolean supportsGetValue(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
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

boolean supportsSetValue(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
    if (ac == null || ac.getAccessibleValue() == null) return false;
    return !READ_ONLY_VALUE_ROLES.contains(ac.getAccessibleRole());
}

boolean supportsSelection(Accessible a) {
    AccessibleContext ac = a.getAccessibleContext();
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
| `toggle_expand` | Raw `AccessibleAction` description compare | `AccessibleAction.TOGGLE_EXPAND` static constant | `swing_toggle_expand` | Toggles expanded/collapsed; AI can infer current state from `EXPANDED`/`COLLAPSED` in snapshot. **Known limitation:** The standard JTree implementation uses the constant directly, but it has not been verified that every Look-and-Feel (Nimbus, GTK, Windows, etc.) upholds this. If a L&F localizes the description, detection silently fails and the node loses its ref and action. This is an accepted risk: the algorithm stays deterministic and correct for the standard L&F rather than introducing a non-deterministic fallback for non-standard ones. |
| `get_text` | `supportsGetText()` | `getAccessibleText()` non-null | `swing_get_text` | On `JPasswordField`, returns echo characters (masked), **not** the actual password |
| `set_text` | `supportsSetText()` | `getAccessibleEditableText()` non-null | `swing_set_text` | `supportsSetText()` implies `supportsGetText()` (`AccessibleEditableText extends AccessibleText`) |
| `get_value` | `supportsGetValue()` | `getAccessibleValue()` non-null | `swing_get_value` | All components with `AccessibleValue` expose `get_value`, including read-only ones like `JProgressBar`. |
| `set_value` | `supportsSetValue()` | `getAccessibleValue()` non-null AND role not in `READ_ONLY_VALUE_ROLES` | `swing_set_value` | Only exposed for components a user can actually modify. `JProgressBar` is explicitly excluded. Add other read-only value roles to `READ_ONLY_VALUE_ROLES` as discovered. |
| `get_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_selection` | Returns a list of integer indices of currently selected children. |
| `set_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_set_selection` | Accepts a list of integer indices of children to select. Replaces the current selection. |
| `clear_selection` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_clear_selection` | Clears the current selection. Equivalent to `set_selection` with an empty list. |
| `select_all` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_select_all` | Selects all children. |
| `get_children_count` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_children_count` | Returns the number of accessible children that can potentially be selected. |
| `get_children` | `supportsSelection()` | `getAccessibleSelection()` non-null | `swing_get_children` | Returns a paged accessibility tree dump of the component's accessible children. Parameters: `ref` (integer), `offset` (integer, 0-based), `length` (integer, max children to return). The output format mirrors `swing_snapshot` — the same indented text tree — but rooted at the requested children rather than the full UI. Each child entry explicitly shows its zero-based index so the AI can pass it directly to `set_selection`. **Serves two purposes:** (1) **Selection browsing** — discover which index to pass to `set_selection`; (2) **Content discovery** — find actionable children (e.g. an "Edit" button inside a JTable row). For purpose 2, `get_children` assigns a fresh local ref numbering and **replaces the MCPServer ref map** with only the refs in its output window. This is analogous to scrolling a JTable: children outside the `offset`/`length` window are not interactable. The AI must call `swing_snapshot` again to return to the full-tree ref map. |
| `close` | `supportsClose()` | Synthetic — dispatches `WindowEvent.WINDOW_CLOSING` to the window | `swing_close` | Not from `AccessibleAction`. Exposed for `Window` instances (JFrame, JDialog) only — `JOptionPane` is excluded because its containing JDialog already exposes `close`. Respects the app's `WindowListener`s and `defaultCloseOperation`; does **not** bypass `DO_NOTHING_ON_CLOSE`. `isEffectivelyEnabled()` is **not** checked — closing is a window-level action, not a component-level one. |

---

## 7. Testing

See [verification](verification.md) for a complete list of testing instructions.
