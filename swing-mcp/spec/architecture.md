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

1. Acquires the MCPServer-level lock (see **Concurrency** below).
2. Calls `runInEDT()` with a block that (all on the EDT):
   a. Retrieves a list of considered components.
   b. Calls `AbstractSwingTool.execute()`.
   c. If `isMutation()` is true, clears the ref map in a `finally` block (even on exception).
3. Returns the result.

**Mutation tools use fire-and-forget dispatch** (see **Fire-and-Forget Mutation Dispatch** below):
`execute()` performs validation on the EDT, then posts the action via `SwingUtilities.invokeLater()` and returns `null` immediately.
The action executes on the EDT after `runInEDT()` returns and the HTTP response has been sent.
The client observes the outcome via `swing_snapshot` or `swing_screenshot`.

**Read-only tools** perform their full work inside `runInEDT()` and return the result synchronously.

### Concurrency

**Concurrent tool calls are not supported.** Two clients controlling the same Swing app
simultaneously would produce unpredictable, interleaved UI state.

The wrapper function registered by `MCPServer.registerTool` acquires a `ReentrantLock`
(`toolLock` field on `MCPServer`) for the entire duration of the tool call —
from lock acquisition through the `runInEDT()` call and the `invokeLater()` dispatch (for
mutations). Both run inside `toolLock.lock()` / `toolLock.unlock()`. Rationale (why the lock spans the whole tool call rather than just the EDT turn, and why `ReentrantLock` rather than `synchronized`) is **DR-007**.

### Fire-and-Forget Mutation Dispatch

Mutation tools (those where `isMutation()` returns `true`) use a **fire-and-forget** dispatch model — rationale (modal-dialog deadlock; alternatives rejected) is **DR-006**.

**Dispatch model:**

1. `execute()` runs on the EDT (inside `runInEDT()`).
2. It performs all **validation**: ref lookup, capability check, enabled check.
3. If validation passes it calls `SwingUtilities.invokeLater(action)` from the EDT,
   queuing the action for the next EDT turn, and returns `null` immediately.
4. `runInEDT()` completes; the HTTP thread returns the response to the client.
5. The EDT picks up the `invokeLater` action and executes it.

The client observes the outcome — new dialog appeared, field changed, window closed — by
calling `swing_snapshot` or `swing_screenshot` after the mutation tool returns.

**Deadlock detection:** `runInEDT()` uses `SwingUtilities.invokeLater()` + `CountDownLatch`
rather than `SwingUtilities.invokeAndWait()`. If the EDT does not complete the task within
`EDT_TIMEOUT_MS` (10 seconds), an `MCPErrorResponseException` is thrown with the EDT's
current stack trace. This detects the residual deadlock risk in read-only tools (which do not
use fire-and-forget) and provides a diagnostic for unexpected EDT blockages.

### Parameters

A typed wrapper around the raw `Map<String, Object>` that MCP tool functions receive.
Lives in `com.vaadin.swingmcp.mcp.tools.Parameters`. All tools use this instead of
accessing the map directly.

Each getter has a required variant (throws `MCPServerException(INVALID_PARAMS, …)` if
missing or wrong type) and an optional variant (returns `null` if missing, throws on
wrong type).

**String-to-number coercion.** Numeric getters (`getInt`, `getIntOrNull`, `getNumber`,
`getIntArray`) accept string-encoded numbers (e.g. `"21"` instead of `21`) and coerce
them to the expected type. This is necessary because LLM clients frequently send all
tool arguments as strings, ignoring the `"type": "integer"` declared in the JSON schema.
Non-parseable strings still produce `INVALID_PARAMS` errors.

See `Parameters.java` for the full API. Error messages must name the parameter and the expected type, e.g.:
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
   `swing_get_cells` also replaces the ref map (with a fresh local numbering
   scoped to its output window), even though it is an inspection tool, not an
   interaction tool. Refs from a prior `swing_snapshot` or `swing_get_cells`
   call are no longer valid after a `swing_get_cells` call.

4. **Expected AI workflow.** The AI is expected to follow a
   snapshot → interact → snapshot loop:
   ```
   swing_snapshot          → get refs
   swing_click ref=3       → refs invalidated
   swing_snapshot          → get fresh refs
   swing_set_text ref=1 …  → refs invalidated
   swing_snapshot          → get fresh refs
   ```
   Tool calls must be issued **sequentially**, not in parallel. DR-007's
   `toolLock` serialises concurrent calls on the server side, so state stays
   consistent — but the second of two parallel mutations will always see a
   cleared ref map and fail with a stale-ref error. The `MCPServer`
   `INSTRUCTIONS` blurb surfaces this rule to clients at `initialize` time.

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

Implemented in `SwingUtils.supportsTogglePopup(Accessible)`:

- `AccessibleAction.TOGGLE_POPUP` (`"toggle popup"`) covers any component that uses the constant directly.
- `UIManager.getString("ComboBox.togglePopupText")` covers `JComboBox`, which uses a potentially localized lookup.

### Detecting Click Support

Click detection uses a two-tier approach: first check `AccessibleAction` (the standard path),
then fall back to checking for application-installed `MouseListener`s on the underlying
`Component`. The fallback covers custom "button-like" components (e.g. a `JPanel` with a
`MouseAdapter` for click handling) that do not expose an `AccessibleAction`.

`supportsClick()` returns a `Runnable` that performs the click when invoked, or `null` if the
component does not support clicking. The returned `Runnable` captures everything needed to
execute the click (the `AccessibleAction` + index for Tier 1, or the `Component` for Tier 2),
so callers never need to know which tier was used. The snapshot checks `!= null` to decide
whether to add the `click` action; `swing_click` calls `run()` inside `invokeLater()`.

Implemented in `SwingUtils.supportsClick(Accessible)`. `INTERACTIVE_ROLES` (referenced by Tier 2 below) is a constant in the same class.

#### Tier 1 — AccessibleAction

- `AccessibleAction.CLICK` (`"click"`) covers AWT components (`Button`, `MenuItem`, `Menu`,
  `PopupMenu`) which hardcode this literal.
- `UIManager.getString("AbstractButton.clickText")` covers Swing components (`AbstractButton`
  subclasses, `JListChild`) which look up a potentially localized string. Comparing against the
  same UIManager lookup ensures locale-safe matching.
- Components with dynamic/algorithm-derived actions (text components, tree nodes, hyperlinks)
  never produce either string and are therefore excluded automatically.
- The returned `Runnable` captures the `AccessibleAction` and the matched index, calling
  `aa.doAccessibleAction(idx)`.

#### Tier 2 — MouseListener Fallback

Many real-world Swing applications build clickable UI elements from plain containers
(e.g. `JPanel`, `JLabel`) by attaching a `MouseListener`/`MouseAdapter` — these components are
functionally buttons but the accessibility API reports no click action.

**Interactive role exclusion (`INTERACTIVE_ROLES`):** Tier 2 is skipped for components whose
`AccessibleRole` is in the interactive roles set from AI-1 in the snapshot spec. These
components have well-defined accessibility contracts and should use `AccessibleAction` for
click detection — a MouseListener on a `JButton` is L&F plumbing, not application click
behaviour. The excluded roles are: `PUSH_BUTTON`, `TOGGLE_BUTTON`, `CHECK_BOX`,
`RADIO_BUTTON`, `TEXT`, `PASSWORD_TEXT`, `COMBO_BOX`, `LIST`, `TABLE`, `TREE`, `MENU_BAR`,
`MENU`, `MENU_ITEM`, `POPUP_MENU`, `SLIDER`, `SPIN_BOX`, `PROGRESS_BAR`, `SCROLL_BAR`,
`COLOR_CHOOSER`, `FILE_CHOOSER`, `DATE_EDITOR`.

**Filtering out framework listeners:** Swing and AWT install internal `MouseListener`s for
tooltip management (`ToolTipManager`), look-and-feel behaviour, and other plumbing. These are
identified by package prefix and excluded. Only listeners from application packages are
considered evidence of click behaviour. The checked prefixes are:
- `javax.swing.*` — Swing internals (ToolTipManager, BasicXxxUI classes, etc.)
- `java.awt.*` — AWT internals
- `sun.*` — JDK internal implementation classes
- `com.sun.*` — JDK internal implementation classes

The returned `Runnable` synthesizes a mouse click event sequence on the component: three
`MouseEvent`s dispatched via `Component.dispatchEvent()` in order (`MOUSE_PRESSED`,
`MOUSE_RELEASED`, `MOUSE_CLICKED`) with `BUTTON1`, click count 1, coordinates at the center
of the component, and timestamps offset by +0/+1/+2 ms to mimic a real press-release-click
sequence. This goes through the full AWT event pipeline (`processEvent()` →
`processMouseEvent()` → listeners), faithfully emulating a real mouse click including any
`processMouseEvent()` overrides and side effects.

**Virtual accessible children:** Objects like `JList` items, `JTable` cells, and `JTree` nodes
are `Accessible` but not `Component` — they are synthesized by the accessibility API. The
`a instanceof Component` check in Tier 2 automatically excludes them. This is correct: these
virtual children do not have their own MouseListeners; the parent component handles mouse
interaction.

**Third-party look-and-feel libraries** (e.g. FlatLaf, JGoodies) may install their own
`MouseListener`s on components for L&F behaviour. These would not be filtered by the
JDK package-prefix check. The interactive role exclusion guards against false positives: a
`JButton` with a FlatLaf-installed `MouseListener` has role `PUSH_BUTTON`, so Tier 2 is
skipped entirely. For non-interactive roles (e.g. `PANEL`), a third-party L&F MouseListener
could cause a false positive — but this is unlikely in practice (L&F libraries rarely add
MouseListeners to plain panels), and a false-positive `click` action is low-harm (the AI
clicks it, nothing meaningful happens).

**Known limitation — `processMouseEvent()` overrides:** A component that overrides
`processMouseEvent()` directly (without calling `addMouseListener()`) would not be detected by
Tier 2, since `getMouseListeners()` returns an empty array. The `dispatchEvent()` path in the
`Runnable` would correctly reach the override, but detection fails so the `Runnable` is never
created. This is an accepted limitation — revisit if seen in the wild.

**Impact on the snapshot:** A component where `supportsClick()` returns non-null receives the
`click` action in the snapshot and is assigned a ref. This means unnamed panels (TP-5) that
have an application MouseListener are **not** pruned — the AI-3 safety net ("has at least one
action") prevents it.

**Impact on `swing_click`:** The tool calls `supportsClick()`, gets the `Runnable`, and posts
it via `SwingUtilities.invokeLater()`. No branching on which tier was used.

### Effectively Enabled Check

`isEffectivelyEnabled()` does **not** walk the parent chain for real `Component` instances — it trusts each component's own `ENABLED` state-set bit. Rationale (why Swing's non-propagating `setEnabled` must be mirrored rather than "fixed") is **DR-003**.

Two Swing quirks need explicit handling:

1. **`JTabbedPane` tabs disabled via `setEnabledAt(i, false)`** — the `AccessiblePage`
   virtual child does NOT omit `ENABLED` from its state set even though the tab is
   disabled. We consult `JTabbedPane.isEnabledAt(idx)` directly when the accessible's
   parent is a `JTabbedPane`.
2. **Virtual accessible children** (not `Component` instances) — some virtual children
   (notably `JTable` cells) keep `ENABLED` in their state set even when the host
   component is disabled. For any accessible that is not itself a `Component`, we recurse
   into its accessible parent until a `Component` ancestor is found. (`JList` items
   happen to do this correctly already, but the recursion is uniformly safe and costs
   nothing.)

Implemented in `SwingUtils.isEffectivelyEnabled(Accessible)`.

The disabled-`Window` case (an OS-level peer dropping input on `Frame.setEnabled(false)`)
is intentionally **not** handled here — its visual behaviour is platform/L&F-dependent
and unreliable across OSes.

All mutation tools (`swing_click`, `swing_set_text`, `swing_set_value`, etc.) must use
`isEffectivelyEnabled()` rather than checking only the target's own state set. If the check
returns `false`, the tool returns an MCP-level error (`isError: true`) explaining that the
component is disabled.

The `swing_snapshot` tool also uses `isEffectivelyEnabled()` in two ways:
1. **`disabled` state in bracket** — shown when `isEffectivelyEnabled()` returns `false`,
   replacing the previous local `ENABLED` check. Because disabled state does not propagate
   through real-`Component` ancestors, a button inside a disabled `JPanel` is **not**
   marked `disabled` in the snapshot — consistent with the fact that `swing_click` will
   accept it. Only the `JPanel` itself carries `[disabled]`, and only virtual children
   (e.g. `JTable` cells) inherit their host's disabled state. `JTabbedPane` tabs
   disabled via `setEnabledAt` do carry `[disabled]` via the Quirk 1 carveout above.
2. **`!` prefix on mutation actions** — mutation actions (`click`, `toggle_popup`,
   `increment`, `decrement`, `toggle_expand`, `set_text`, `set_value`, `close`) are
   prefixed with `!` when the component is not effectively enabled. Read-only actions
   and selection group labels are never prefixed. See **UC-002 BR-08** for the full rule.

### Detecting Close Support

`supportsClose` is a **synthetic** action — it is not derived from `AccessibleAction` or any
`AccessibleContext` interface. It is exposed for top-level windows (JFrame, JDialog),
`JInternalFrame`, and `JDesktopIcon` (iconified internal frame). The close mechanism differs
by type:

- **Window (JFrame, JDialog):** dispatches `WindowEvent.WINDOW_CLOSING`, which mirrors what
  the OS close button does and allows the app's `WindowListener`s and `defaultCloseOperation`
  to handle the event normally.
- **JInternalFrame:** calls `doDefaultCloseAction()`, which fires
  `InternalFrameEvent.INTERNAL_FRAME_CLOSING` and then executes the frame's
  `defaultCloseOperation` — mirroring what the internal frame's close button does.
- **JDesktopIcon:** resolves to the underlying JInternalFrame via `getInternalFrame()` and
  then follows the JInternalFrame path.

In all cases the tool does **not** bypass `DO_NOTHING_ON_CLOSE` — if the app ignores the
event, the window/frame stays open.

`JOptionPane` is intentionally excluded: it is a `JComponent`, not a window. Its containing
`JDialog` is itself a `Window` and will already expose `close` directly, so the AI can always
dismiss the dialog via the `dialog` node ref.

Implemented in `SwingUtils.supportsClose(Accessible)`:

- Returns `true` for JFrame/JDialog when the window is showing, has decorations, and will not
  terminate the JVM on close.
- Returns `true` for JInternalFrame when the frame is showing, `isClosable()` is true, and
  `defaultCloseOperation` is not `EXIT_ON_CLOSE`.
- Returns `true` for JDesktopIcon when the **icon itself** is showing (the underlying frame
  is detached with `isShowing() == false` — checking the frame would always fail) and its
  underlying JInternalFrame (via `getInternalFrame()`) passes the JInternalFrame rules
  above (`isClosable()`, not `EXIT_ON_CLOSE`).
- Returns `false` for all other component types, including `JOptionPane`.
- Undecorated windows (`setUndecorated(true)`) have no visible close button, so the user
  cannot close them through the normal UI — `supportsClose` returns `false` for those.
- Non-closable JInternalFrames (`isClosable() == false`) have no close button in the title
  bar — `supportsClose` returns `false` (analogous to undecorated windows). This also
  applies to JDesktopIcons whose underlying frame is not closable.
- `JFrame` with `EXIT_ON_CLOSE` is explicitly excluded: dispatching `WINDOW_CLOSING` would
  terminate the JVM, taking the swing-mcp server down with it and dropping the AI client
  connection with no explanation. The `close` action is never advertised for such frames.
- `JInternalFrame` with `EXIT_ON_CLOSE` is also excluded for consistency (and transitively,
  JDesktopIcons whose underlying frame has `EXIT_ON_CLOSE`).
  `JInternalFrame.setDefaultCloseOperation()` silently accepts the invalid value (JDK does
  not validate), but `doDefaultCloseAction()` falls through with no effect. The refusal is
  defensive — undefined behavior should not be exposed to the AI.

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
| `getAccessibleValue()` | Numeric value is readable; writable only if the component is not a known read-only role (see `supportsSetValue()`). **Note:** `getAccessibleValue()` non-null is necessary but not sufficient — `getCurrentAccessibleValue()` must also return non-null (see JSpinner false-positive in § 5). | `get_value`; `set_value` only when `supportsSetValue()` |
| `getAccessibleSelection()` | Selection is readable and writable (no read-only variant in the API) | `get_selection`, `set_selection`, `clear_selection`, `select_all`, `get_children_count`, `get_children` |

### Detection

Implemented in `SwingUtils`: `supportsGetText`, `supportsSetText`, `supportsGetValue`, `supportsSetValue`, `supportsSelection`, and the `SUPPRESSED_VALUE_ROLES` / `READ_ONLY_VALUE_ROLES` / `SUPPRESSED_SELECTION_ROLES` constants. Notable semantics that are not obvious from the getters alone:

- **JSpinner false-positive:** `AccessibleJSpinner` returns `this` from `getAccessibleValue()` for every model type, so `supportsGetValue` also null-checks `getCurrentAccessibleValue()` — this returns `null` for `SpinnerDateModel` / `SpinnerListModel` and correctly suppresses the action.
- **`READ_ONLY_VALUE_ROLES` — `PROGRESS_BAR` only.** A user can drag a `JScrollBar`, so it stays writable. `JProgressBar.setCurrentAccessibleValue()` actually mutates the bar at the JDK level (verified by test), but we suppress `set_value` at the tool level because progress is application-controlled, not user-controlled.
- **`SUPPRESSED_VALUE_ROLES` includes `INTERNAL_FRAME` and `DESKTOP_ICON`.** Both expose `AccessibleValue` for the `JLayeredPane` Z-order layer — a programmatic concept, not a user-controlled value. `set_value` would silently re-layer frames. See DR-008.
- **`supportsSetText` and `supportsGetText` are independent capabilities.** `AccessibleEditableText extends AccessibleText` makes a structural "setText implies getText" implication tempting, but `supportsGetText` answers the domain question *"does reading yield meaningful content?"* and excludes components where the accessibility API returns garbage — today `AccessibleRole.PASSWORD_TEXT` (DR-011), and in principle any write-only input (e.g. filter combo boxes that clear themselves on apply). Callers must gate `get_text` and `set_text` independently. `hasAnyAction` (ref-assignment gate) combines both via `supportsGetText || hasEditableText` so write-only text components still receive refs.

### AccessibleValue — Component Behaviour

Empirically verified on Java 21 OpenJDK (all results reproduced by tests in
`AccessibleValueTypeResearchTest` and `JSpinnerAccessibleResearchTest`).

#### `getCurrentAccessibleValue()` return types

| Component | Return type | Notes |
|---|---|---|
| `JSlider` | `Integer` | Always; mirrors the int-based model |
| `JSpinner(SpinnerNumberModel)` | Mirrors model value type | `Integer`, `Double`, `Long`, etc. — whatever type was passed to the `SpinnerNumberModel` constructor |
| `JSpinner(SpinnerDateModel)` | `null` | Model value is a `Date`, not a `Number` |
| `JSpinner(SpinnerListModel)` | `null` *or* current element | Returns `null` unless the current list element happens to be a `Number`; unreliable — do not treat as a number spinner |
| `JSplitPane` | `Integer` | Divider location |
| `JProgressBar` | `Integer` | Always |

`getMinimumAccessibleValue()` / `getMaximumAccessibleValue()` return the same type as
`getCurrentAccessibleValue()`.

#### `setCurrentAccessibleValue(Number n)` behaviour

| Component | Behaviour |
|---|---|
| `JSlider` | Calls `n.intValue()` — **truncates** to int. `Double(33.7)` → 33, `Float(20.5f)` → 20. Out-of-range values are **clamped** to `[min, max]`. |
| `JSplitPane` | Same — calls `n.intValue()`, truncates. `Double(100.9)` → 100. |
| `JProgressBar` | Returns `true` and mutates the bar (not blocked by the JDK). Suppressed at the tool level via `READ_ONLY_VALUE_ROLES`. |
| `JSpinner(SpinnerNumberModel)` | Calls `model.setValue(n)` — **stores the Number AS-IS** with no type conversion. Passing `Double(7.9)` to an int-typed model stores a `Double`, not an `Integer`. This can corrupt the model. See JSpinner section below. |
| `JSpinner(SpinnerDateModel)` | Returns `false` — `SpinnerDateModel.setValue()` rejects non-Date values with `IllegalArgumentException`. Safe. |
| `JSpinner(SpinnerListModel)` | Returns `false` — same rejection. Safe. |

#### JSpinner — special handling required

`JSpinner.AccessibleJSpinner` always returns `this` from `getAccessibleValue()`, so a
naive non-null check says every spinner supports `get_value` / `set_value`. The actual
capability depends on the model:

| Model | `get_value` | `set_value` | Method |
|---|---|---|---|
| `SpinnerNumberModel` | Yes | Yes — with care (see below) | `setCurrentAccessibleValue(Number)` |
| `SpinnerDateModel` | No | No | — |
| `SpinnerListModel` | No | No | — |

**`SpinnerNumberModel` — type-safety risk with `setCurrentAccessibleValue`:**
The JDK's `JSpinner.AccessibleJSpinner.setCurrentAccessibleValue(n)` delegates directly
to `SpinnerModel.setValue(n)`. `SpinnerNumberModel.setValue()` accepts any `Number`
without type-checking — it stores the value as-is. If the caller passes a `Double` to a
spinner whose model was constructed with `int` literals (`SpinnerNumberModel(5, 0, 10, 1)`),
the model will hold a `Double` from that point on. The spinner's own increment/decrement
logic may then break because it compares the stored `Double` against `Integer` bounds.

**Safe alternative — text path via `DefaultEditor.getTextField()`:**
Each standard spinner editor (`NumberEditor`, `DateEditor`, `ListEditor`) extends
`JSpinner.DefaultEditor` and wraps a `JFormattedTextField`. This text field's
`AccessibleContext` returns a non-null `AccessibleEditableText` (`JTextField.AccessibleJTextField`).
Writing a string value and then committing it causes the text field's own formatter to
parse the string and call `SpinnerModel.setValue()` with the correctly typed result:

```java
// Only works for DefaultEditor-based spinners (all standard models)
JFormattedTextField tf = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
AccessibleEditableText aet = tf.getAccessibleContext().getAccessibleEditableText();
aet.setTextContents(valueAsString);   // sets display text only
tf.commitEdit();                       // parses and commits to the model — may throw ParseException
```

`commitEdit()` throws `java.text.ParseException` if the string cannot be parsed by the
formatter (e.g. locale-mismatched date string for a `SpinnerDateModel`). Callers must
handle this.

**`setTextContents` alone is NOT enough** — it changes the display without touching the
model. `commitEdit()` must be called explicitly.

**`getAccessibleEditableText()` factory returns null:** `AccessibleJSpinner` implements
the `AccessibleEditableText` *interface* but does not override the
`AccessibleContext.getAccessibleEditableText()` factory method. As a result the factory
returns `null`, and using `ac.getAccessibleEditableText()` as the entry point silently
fails. The workaround is either:
- Cast `ac.getAccessibleText()` to `AccessibleEditableText` (works because the same
  `AccessibleJSpinner` instance implements both interfaces).
- Or go directly to the inner text field as shown above (preferred — type-safe).

#### What each model type supports for `set_value`

| Model | Recommended approach | Notes |
|---|---|---|
| `SpinnerNumberModel` | Text path: `setTextContents(str)` + `commitEdit()` | Type-safe; uses the model's own formatter. The formatter validates the range and preserves the model's original `Number` subtype. |
| `SpinnerListModel` | Text path: `setTextContents(str)` + `commitEdit()` | Works only when `str` exactly matches an item in the list. `commitEdit()` rejects non-matching strings via `ParseException`. |
| `SpinnerDateModel` | **Not supported** | `setCurrentAccessibleValue` is rejected; text path fails because the format string is locale-dependent and cannot be reliably constructed by the tool. Use `increment`/`decrement` instead. |

---

## 6. Action Detection Summary

**Imperative: the MCP server must only expose actions a real user can perform.** Exposing write actions on read-only or programmatically-controlled components risks putting the Swing app into an undefined state. When in doubt, prefer fewer actions over more.

This imperative operates at two levels:

1. **Snapshot level (component type and state).** Mutation actions (`set_text`, `set_value`, `set_selection`, etc.) are listed for components that *generally* allow their value to be changed by a user — e.g. a text field, a slider. Components that are structurally read-only regardless of state (e.g. `JProgressBar`) never receive the corresponding mutation action in the snapshot. The current enabled/disabled state of the component does **not** affect which actions appear in the snapshot. **Exception: `set_text` and the editable state.** Text components that expose `AccessibleEditableText` but lack the `EDITABLE` state (e.g. `JTextField` with `setEditable(false)`) do **not** list `set_text` in the snapshot — they list only `get_text` and are annotated with `read_only`. This prevents the AI from wasting a tool call on a component that will refuse the mutation, saving context window space.

2. **Tool execution level (runtime state).** When a mutation tool is called, it must check whether the target component is currently enabled. If the component is disabled, the tool must return an MCP-level error (`isError: true`) with an informative message explaining that the component is disabled and therefore the user cannot change its value. The `swing_set_text` tool additionally checks the `EDITABLE` state at runtime as defense-in-depth (see use-case-006 BR-07), since state may change between snapshot and tool call.

Authoritative mapping between spec action names, their detection mechanism, and the corresponding MCP tool.
Other specs reference this table instead of duplicating detection logic.

| Spec Action Name | Detection Method | Java Mechanism | MCP Tool | Notes |
|---|---|---|---|---|
| `click` | `supportsClick()` returns non-null `Runnable` | **Tier 1:** `AccessibleAction.CLICK` (AWT literal) OR `UIManager.getString("AbstractButton.clickText")` (Swing UIManager). **Tier 2 (fallback):** application-installed `MouseListener` on the underlying `Component` (framework listeners filtered by package prefix). See § 4 "Detecting Click Support" for full algorithm. | `swing_click` | `supportsClick()` returns a `Runnable` encapsulating the click action (Tier 1: `doAccessibleAction(i)`, Tier 2: synthetic MouseEvent sequence). Callers just check `!= null` and call `run()`. |
| `toggle_popup` | `supportsTogglePopup()` | `AccessibleAction.TOGGLE_POPUP` OR `UIManager.getString("ComboBox.togglePopupText")` | `swing_toggle_popup` | Toggles open/closed; AI can infer current state from snapshot |
| `increment` | Raw `AccessibleAction` description compare | `AccessibleAction.INCREMENT` static constant | `swing_increment` | Safe to match by raw constant — `JSlider`/`JSpinner` use the static field directly, no UIManager variant exists |
| `decrement` | Raw `AccessibleAction` description compare | `AccessibleAction.DECREMENT` static constant | `swing_decrement` | Same rationale as `increment` |
| `toggle_expand` | Raw `AccessibleAction` description compare | `AccessibleAction.TOGGLE_EXPAND` static constant | `swing_toggle_expand` | Toggles expanded/collapsed; AI can infer current state from `EXPANDED`/`COLLAPSED` in snapshot. **Known limitation:** The standard JTree implementation uses the constant directly, but it has not been verified that every Look-and-Feel (Nimbus, GTK, Windows, etc.) upholds this. If a L&F localizes the description, detection silently fails and the node loses its ref and action. This is an accepted risk: the algorithm stays deterministic and correct for the standard L&F rather than introducing a non-deterministic fallback for non-standard ones. |
| `get_text` | `supportsGetText()` | `getAccessibleText()` non-null | `swing_get_text` | On `JPasswordField`, returns echo characters (masked), **not** the actual password |
| `set_text` | `supportsSetText()` | `getAccessibleEditableText()` non-null | `swing_set_text` | `supportsSetText()` implies `supportsGetText()` (`AccessibleEditableText extends AccessibleText`) |
| `get_value` | `supportsGetValue()` | `getAccessibleValue()` non-null **and** `getCurrentAccessibleValue()` non-null | `swing_get_value` | All value-capable components expose `get_value`, including read-only ones like `JProgressBar`. `JSpinner` with `SpinnerDateModel`/`SpinnerListModel` is excluded because `getCurrentAccessibleValue()` returns `null` for non-Number models. See § 5 "AccessibleValue — Component Behaviour". |
| `set_value` | `supportsSetValue()` | `getAccessibleValue()` non-null AND `getCurrentAccessibleValue()` non-null AND role not in `READ_ONLY_VALUE_ROLES` | `swing_set_value` | Only exposed for components a user can actually modify. `JProgressBar` is explicitly excluded (writable at the JDK level, suppressed by policy). `JSpinner` with non-Number model is excluded by the `getCurrentAccessibleValue()` null-check. For `JSpinner(SpinnerNumberModel)`, prefer the text-path over `setCurrentAccessibleValue` to avoid type corruption — see § 5 "JSpinner — special handling required". |
| `single-selection` | `supportsSingleSelection()` | `supportsSelection()` AND NOT `isMultiSelectable()` | *(group label)* | Snapshot action group label. Signals that selection tools (`swing_get_selection`, `swing_set_selection`, `swing_clear_selection`, `swing_get_items`, `swing_get_item_count`) work on this component, but `swing_select_all` does not. See the [JTabbedPane caveat](#jtabbedpane-caveat) — JTabbedPane carries `single-selection` but the enumerate and `clear_selection` tools are not wired to it. |
| `multi-selection` | `supportsMultiSelection()` | `supportsSelection()` AND `isMultiSelectable()` | *(group label)* | Snapshot action group label. Signals that all selection tools work on this component, including `swing_select_all`. |
| `get_cell_count` | `isGetCellsSupported` (role is LIST or TREE) AND `childCount > MAX_DATA_ROW_NODES` | `getAccessibleChildrenCount()` | `swing_get_cell_count` | Returns the total number of accessible children. Only advertised on JList/JTree when the snapshot truncated the component's children. **JTable is excluded** — use `swing_get_item_count` for row counts. |
| `get_cells` | `isGetCellsSupported` (role is LIST or TREE) AND `childCount > MAX_DATA_ROW_NODES` | `getAccessibleChild(int i)` | `swing_get_cells` | Returns a paged accessibility tree dump of the component's accessible children, with refs for actionable children inside cell renderers. Parameters: `ref` (integer), `offset` (integer, 0-based), `length` (integer, max children to return). The output format mirrors `swing_snapshot` — the same indented text tree — but rooted at the requested children rather than the full UI. Each child receives a ref, and `get_cells` **replaces the MCPServer ref map** with only the refs in its output window. Children outside the `offset`/`length` window are not interactable. The AI must call `swing_snapshot` again to return to the full-tree ref map. Only advertised on JList/JTree when the snapshot truncated the component's children. **JTable is excluded** — table cells are stamp-painted plain text labels with no actionable children; use `swing_get_items` for row access. |
| `close` | `supportsClose()` | Synthetic — **Window:** dispatches `WindowEvent.WINDOW_CLOSING`; **JInternalFrame:** calls `doDefaultCloseAction()`; **JDesktopIcon:** resolves to JInternalFrame, then `doDefaultCloseAction()` | `swing_close` | Not from `AccessibleAction`. Exposed for `Window` instances (JFrame, JDialog), `JInternalFrame`, and `JDesktopIcon` (iconified internal frame) — `JOptionPane` is excluded because its containing JDialog already exposes `close`. Respects the app's close listeners and `defaultCloseOperation`; does **not** bypass `DO_NOTHING_ON_CLOSE`. `isEffectivelyEnabled()` is **not** checked — closing is a window/frame-level action, not a component-level one. |

### Selection Index Spaces

The `AccessibleSelection` API mixes three distinct index spaces. All selection tools
(`swing_get_selection`, `swing_set_selection`) use the **item index space** — the 0-based
index that `addAccessibleSelection(i)` and `isAccessibleChildSelected(i)` expect.

| Index space | API methods | Description |
|---|---|---|
| **Selection-relative** | `getAccessibleSelection(int i)` | The i-th *selected* item. Ranges over `[0, getAccessibleSelectionCount())`. Not usable with `addAccessibleSelection()`. |
| **Accessible children** | `getAccessibleChild(int i)`, `getAccessibleChildrenCount()` | Structural children of the component. For JComboBox, child 0 is the popup menu (`childrenCount=1`) — completely unrelated to combo items. |
| **Item index** | `addAccessibleSelection(int i)`, `removeAccessibleSelection(int i)`, `isAccessibleChildSelected(int i)`, `getAccessibleIndexInParent()` on a selected item | The logical item position (0-based). This is the index space used by all selection MCP tools. |

Empirically verified (Java 21 OpenJDK, 2026-04-07): for all supported components,
`getAccessibleIndexInParent()` on an item returned by `getAccessibleSelection(i)` equals
the item's index in the `addAccessibleSelection()` space.

| Component | Children index = Item index? | Notes |
|---|---|---|
| `JList` | Yes | All three index spaces are identical. |
| `JTabbedPane` | Yes | All three index spaces are identical. |
| `JComboBox` | **No** | `getAccessibleChildrenCount()=1` (popup menu). Item index accessed via `indexInParent` on selected items. Selected item's parent is the internal popup `list`, not the combo box. |
| `JTable` | **No** | Children are cells in row-major order. Item index is the cell index. See below for row aggregation. |

**JTable row index.** `swing_get_selection` returns **row indices** for JTable (not cell
indices), because reporting every cell in a selected row would be too verbose. The row index
is computed as `cellIndex / columnCount`. `swing_set_selection` must perform the reverse
mapping: to select row `r`, call `addAccessibleSelection(r * cols + c)` for each column `c`
in `[0, cols)`. This translation is internal to the tool — the AI always works with row
indices.

### Selection Action Groups

The old step 6 listed six individual actions (`get_selection`, `set_selection`, `clear_selection`, `select_all`, `get_children_count`, `get_children`) gated on `supportsSelection()`. This has been replaced by two **group labels** in the snapshot action list:

- **`single-selection`** — emitted when `supportsSingleSelection()` returns `true`. The AI learns from the tool descriptions that `swing_get_selection`, `swing_set_selection`, `swing_clear_selection`, `swing_get_items`, and `swing_get_item_count` are available. See also the [JTabbedPane caveat](#jtabbedpane-caveat) below — `JTabbedPane` carries `single-selection` but the enumerate and `clear_selection` tools are intentionally not wired to it.
- **`multi-selection`** — emitted when `supportsMultiSelection()` returns `true`. Same tools as single-selection, plus `swing_select_all`.

This replaces the former TODO-1 and TODO-2. The individual selection actions no longer appear in the snapshot — they are documented in the tool descriptions, which are sent once at session start and persist for the entire MCP session.

**`SwingUtils` methods for selection mode:**

| Method | Logic | Purpose |
|---|---|---|
| `supportsSelection(Accessible)` | `getAccessibleSelection()` non-null AND role not in `SUPPRESSED_SELECTION_ROLES` | Base selection capability check (unchanged) |
| `isMultiSelectable(Accessible)` | `AccessibleState.MULTISELECTABLE` in state set, OR (`instanceof JTable` AND `getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION`) | Multi-select capability. JTable fallback needed because JTable does not report `MULTISELECTABLE` in its `AccessibleStateSet` even in multi-selection mode (verified by probe test, 2026-04-07). |
| `supportsSingleSelection(Accessible)` | `supportsSelection()` AND NOT `isMultiSelectable()` | Convenience: single-selection mode |
| `supportsMultiSelection(Accessible)` | `supportsSelection()` AND `isMultiSelectable()` | Convenience: multi-selection mode |

### JTable Snapshot Rendering

JTable receives special rendering in the snapshot (UC-002 SC-6/SC-7) to present data in a row-oriented, human- and AI-readable format instead of a flat list of cell labels.

**`SwingUtils` methods for JTable rendering:**

| Method | Logic | Purpose |
|---|---|---|
| `isTableHeaderVisible(JTable)` | `getTableHeader()` non-null AND `isVisible()` AND table is inside a `JScrollPane` (parent is `JViewport`, grandparent is `JScrollPane`) AND header passes `isVisible(Accessible)` zero-size check | Determines whether column headers should be shown in the snapshot. Swing only renders the table header when the table is inside a JScrollPane. |
| `getTableColumnNames(JTable)` | Iterates `TableColumnModel` in display order, calls `getHeaderValue().toString()` on each column | Returns column names respecting user column reordering. Used for the `columns: [Col1, Col2, …]` annotation on the table node (placed after bracket, before `actions:`). |
| `buildTableRowText(AccessibleTable, row, cols)` | Concatenates cell accessible names with `" \| "` separator, capped at `MAX_ROW_NAME_COLUMNS` (10) with trailing `"…"` | Builds a pipe-separated row summary for snapshot row lines. |
| `describeTableCell(Accessible)` | Returns `cell.getAccessibleContext().getAccessibleName()`, or `"null"` if absent | Cell text description. JTable cells are virtual accessible children — renderers (even JButton renderers) are "rubber stamps" that don't appear in the accessibility tree. |

**Why cells are always plain text:** JTable cell renderers are painted via `CellRendererPane` (a stamp-painting mechanism) — the renderer `Component` is never added to the real component hierarchy. The accessibility API exposes virtual `Accessible` children whose names come from `toString()` of the cell value. Even if a column uses a `JButton` renderer, the accessible child is a `LABEL` with no click action. Interactive cell editors only appear in the accessibility tree while a cell is being actively edited.

### Content Discovery (`get_cells` / `get_cell_count`)

In cases where the `swing_snapshot` tool trims children of a large data component, the AI
may need to enumerate cells of a component when it's searching for a particular actionable
child (e.g. an "Edit" button inside a list cell renderer on the 200th item). That's where
these tools come in handy. They only operate on large data components — there is no need to
support them on e.g. JFrame since JFrame children are discovered via `swing_snapshot`.

`get_cells` and `get_cell_count` are **decoupled from selection** — they operate in the accessible children index space, not the selection item index space. They are advertised in the snapshot only when **all** conditions hold:
This important distinction must be mentioned in tool description, so that the AI client understands the distinction fully.

1. The component's role is `LIST` or `TREE` (`isGetCellsSupported`).
2. The component's accessible children count exceeds `MAX_DATA_ROW_NODES` (i.e. the snapshot actually truncated its children).

This avoids action list noise for small lists where all children are already visible in the snapshot.

**`TABLE` is excluded** — rationale is **DR-004**. At runtime, JTable is unconditionally rejected by `swing_get_cells` / `swing_get_cell_count` with an error that redirects the AI to `swing_get_items` / `swing_get_item_count`.

**`get_items` / `get_item_count`** operate in the **selection item index space** — the same 0-based index that `addAccessibleSelection(i)` expects. They are not listed as snapshot actions; their availability is documented in the tool descriptions and they are callable on `JList`, `JComboBox`, and any `JTable`. For JTable specifically, these are the **canonical row-access tools** (UC-017 BR-09) and replace what `get_cells` would otherwise have offered. Their eligibility gate is `SwingUtils.supportsGetItems`, which accepts `JList` / `JComboBox` / `JTable` only. Two deliberate deviations from `supportsSelection`: (a) **JTable is accepted in any selection mode** (row / column / cell / no-selection) — row enumeration is read-only and does not require a working selection model; the write-path selection tools (`swing_set_selection`, `swing_clear_selection`, `swing_select_all`) keep the strict `supportsSelection` gate. (b) **JTabbedPane is rejected** — see caveat below. Discoverability caveat for JTable: a non-row-selection JTable does not carry the `single-selection` / `multi-selection` group label in the snapshot, so the AI relies on the tool description to learn that these two tools still work on it.

<a name="jtabbedpane-caveat"></a>**JTabbedPane caveat — the polymorphic-selection asymmetry.** `JTabbedPane` surfaces as `single-selection` in the snapshot because `swing_get_selection` and `swing_set_selection` work on it. But the rest of the `single-selection` tool family is **not** wired up to it, and that is deliberate:

1. **`swing_get_items` / `swing_get_item_count` are not advertised for JTabbedPane** (dropped per P-001). Tabs are UI structure, not data — they are already rendered inline in the snapshot as `- (page_tab) N "title"` with their 0-based index, `[selected]`, and `[disabled]` state (UC-002 SC-2). The AI reads them directly from the snapshot and passes the inline index straight to `swing_set_selection` as `[N]`. Paging would be pure overhead (tabs are bounded by UI layout, essentially never >20), and a dedicated tool would duplicate information the snapshot already carries.

2. **`swing_clear_selection` on a non-empty JTabbedPane always errors** with *"This component does not allow the selection to be empty"* — pre-existing behaviour (see UC-015 BR-07), because `clearAccessibleSelection()` is a no-op on a non-empty JTabbedPane (probe-tested 2026-04-07). An empty JTabbedPane (0 tabs) trivially succeeds. This is an orthogonal policy question, not something Wave A of P-001 set out to fix, but it is part of the same polymorphic-selection asymmetry: JTabbedPane carries `single-selection` yet not every single-selection tool applies to it.

3. **Snapshot now predicts UC-015 BR-14's disabled-tab refusal.** Post-commit c5feda3, a tab disabled via `JTabbedPane.setEnabledAt` renders with `[disabled]` on its `page_tab` line (via the Quirk 1 carveout in `isEffectivelyEnabled` — see § 4 above). UC-015 BR-14 refuses `swing_set_selection` to a disabled tab index. Before c5feda3 the snapshot was silently missing the `[disabled]` marker on tabs, so the BR-14 refusal was un-predictable from the snapshot alone and the AI had to remember the policy out-of-band. The two now agree: if the snapshot shows `[disabled]` on a `page_tab`, `set_selection` will refuse that index; if it doesn't, `set_selection` will accept.

### Editable JComboBox

When `JComboBox.setEditable(true)` is set, the combo box exposes its internal editor
(`JTextField`) as an accessible child alongside the popup menu. The snapshot pipeline
discovers it automatically — no special-case code is required.

**Accessibility tree (editable JComboBox, Java 21 OpenJDK, verified 2026-04-08):**

| Child index | Role | Class | `AccessibleText` | `AccessibleEditableText` | Notes |
|---|---|---|---|---|---|
| 0 | `popup menu` | `BasicComboPopup` | null | null | Pruned from snapshot (no actions, no name) |
| 1 | `text` | `MetalComboBoxEditor$1` (extends `JTextField`) | Yes | Yes | Kept — has `editable` state, supports `get_text` / `set_text` |

**Snapshot output:**

```
- combo_box [ref=2, collapsed] actions: toggle_popup, single-selection
  - text [ref=3, editable] actions: get_text, set_text
```

The child `text` node receives its own ref and supports the standard `get_text` / `set_text`
actions. This means the AI client can type filter text into an editable combo box the same
way it types into any other text field — no combo-specific text handling is needed.

A non-editable JComboBox (`setEditable(false)`, the default) has `getAccessibleChildrenCount()=1`
(only the popup menu), so no `text` child appears.

**Filtering pattern.** Many Swing apps use editable JComboBox for type-to-filter dropdowns.
The AI workflow is: `set_text` on the child text ref to enter the filter string, then
`swing_get_items` or `swing_get_selection` on the parent combo ref to inspect
filtered results. No dedicated filtering tool is needed — the existing primitives compose
naturally. If future evidence shows that AI clients struggle to infer this pattern, we may
add guidance to the MCP server instructions.

### TODOs

**TODO-3: JTree content discovery.** JTree is suppressed from `supportsSelection()` (tree-level `AccessibleSelection` is non-functional — see UC-014 design notes). This means JTree only gets `get_cells`/`get_cell_count` for content discovery when truncated. However, JTree's collapsed nodes hide their children from the accessible tree entirely — `get_cells` only reveals the top-level nodes, not deeply nested ones. A future UC should investigate a JTree-specific content discovery mechanism that walks expanded/collapsed state.

**TODO-4: Cell search tool.** `swing_get_cells` requires the AI to page through children to find a specific row or cell (e.g. "the row containing 'Alice'"). Repeated `get_cells` calls for linear search wastes tokens. A dedicated `swing_search_cells` tool that accepts a search query and returns matching children (with refs) would be more efficient. Should specify: search semantics (substring match on accessible name?), result format (same as `get_cells`?), and whether it also replaces the ref map.

---

## 7. Testing

See [verification](verification.md) for a complete list of testing instructions.
