# Project Context

Implements the Swing MCP server itself. This subproject builds on top of
`tiny-mcp-server` and adds Swing-specific MCP tools for UI inspection and
interaction. The primary use case is AI-driven migration of Swing apps to Vaadin.

## 1. Accessibility Tree Snapshot

Leverages the `javax.accessibility` API to construct an accessibility tree.

### Snapshot format

The snapshot is served as a **compact indented text tree** (NOT YAML), mimicking
the format used by Playwright MCP. Each node in the tree includes all information
obtainable from `AccessibleContext`:

- Role (e.g., `button`, `text field`, `panel`)
- Name / label
- States (enabled, visible, focused, etc.)
- Available actions
- Current value (for text fields, checkboxes, etc.)

Start verbose — include all available data. If this proves too large for AI
context windows (frequent context window compression), reduce verbosity.

For reference, we can mimic the accessibility tree which is returned by Playwright MCP as a tree text-based representation:
```
- document "Page Title"
  - banner
    - heading "Site Name" [level=1]
    - navigation "Main Nav"
      - link "Home" [ref=1]
      - link "About" [ref=2]
      - link "Contact" [ref=3]
  - main
    - heading "Welcome to the App" [level=2]
    - paragraph "Some descriptive text here."
    - button "Sign In" [ref=4]
    - textbox "Email Address" [ref=5, required]
    - textbox "Password" [ref=6, required]
    - checkbox "Remember me" [ref=7, checked]
    - link "Forgot password?" [ref=8]
  - contentinfo
    - text "© 2026 Example Corp"
```

### Component addressing

Components are identified by **short numeric refs** (starting from 1), assigned
fresh with each snapshot — similar to Playwright MCP's `ref` system. A map of
ref → component is maintained internally.

Refs are valid only until the next action is invoked. After any interaction tool
call, the AI must call `swing_snapshot` to get fresh refs.

## 2. Screenshot Capture

Leverages Swing built-in capability to obtain screenshots of the app by
rendering Swing Windows (via `window.paint()` to a `BufferedImage` graphics).
See §5 for which windows are considered. Always creates one PNG image: if
there are multiple windows to be considered, they are arranged vertically
in the PNG image with no overlapping.

## 3. MCP Tools

Each tool has a single responsibility. Tools do NOT automatically return
snapshots or screenshots — the AI decides when it needs fresh state.

| Tool | Returns | Purpose |
|------|---------|---------|
| `swing_snapshot` | Text (compact tree) | Accessibility tree of visible windows |
| `swing_screenshot` | PNG image | Screenshot of visible windows |
| `swing_click` | Empty string | Click a component by ref |
| `swing_set_text` | Empty string | Set text on a text component by ref |
| `swing_set_value` | Empty string | Set value on a component by ref |

Additional interaction tools may be added as needed (e.g., `swing_select`,
`swing_hover`). Start with the basics above.

## 4. EDT Threading

All Swing component access must happen on the EDT (Event Dispatch Thread).
The MCP HTTP handler runs on `HttpServer`'s thread pool, so every tool call
must marshal onto the EDT via `SwingUtilities.invokeAndWait()`.

### Modal / blocking dialog limitation

**Assumption:** The Swing app has no blocking modal dialogs
(`JOptionPane.showXxxDialog()` and similar). Blocking dialogs block the EDT,
which in turn blocks `invokeAndWait()`, which deadlocks the HTTP request.

This is an intentional limitation: blocking dialogs are also problematic for
the Vaadin migration itself (they block the web UI from rendering). The Swing
app is expected to undergo a preparation phase replacing blocking dialogs with
non-blocking alternatives before using Swing-MCP.

If a blocking dialog is detected, the server should log an error. The EDT will
remain blocked (and Swing-MCP effectively deadlocked) until the dialog is
dismissed manually.

## 5. Window Selection

All tools must only consider the windows the user can interact with.
Consider only visible windows:

- If there is a modal visible window, only consider that window
  (use `KeyboardFocusManager` to determine the current modal window).
- If there is no modal window, consider all visible windows.
- For snapshots: all considered windows appear in the tree.
- For screenshots: all considered windows are arranged vertically in a single
  PNG with no overlapping.

## 6. Testing

Testing involves running a Swing app. The `javax.accessibility` API works in
headless mode. The screenshot capturing functionality probably requires Xvfb,
and will be tested elsewhere.

In headless mode, tests can't instantiate Window. Therefore,
the window Selection functionality must be overridable in MCPServer,
and must be overriden by tests (which provide a list of JPanels instead).

## 7. Constraints

- All Swing access on EDT
- No blocking modal dialogs (documented limitation)
- Depends on `tiny-mcp-server` for MCP protocol handling
