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
marshals onto the EDT via `MCPServer.runInEDT()`, which uses
`SwingUtilities.invokeLater()` + a `CountDownLatch` with a timeout.

### Mutation tools and blocking dialogs

Mutation tools (click, set_text, close, etc.) use a **fire-and-forget** dispatch model:
validation runs on the EDT synchronously, then the action is posted via
`SwingUtilities.invokeLater()` and the HTTP response is returned immediately.
This means a mutation can open a modal dialog without deadlocking the server —
subsequent read calls (`swing_snapshot`, `swing_screenshot`) are processed by
the secondary event loop that the modal dialog creates.

### Deadlock detection

`runInEDT()` enforces a 10-second timeout. If the EDT does not complete a task in time,
it returns an MCP-level error with the EDT's stack trace, making the root cause immediately
visible. This protects read-only tools, which do not use fire-and-forget and could
theoretically still encounter a blocked EDT.


## 5. Window Selection

All tools must only consider the windows the user can interact with.
Consider only visible windows:

- If there is a modal visible window, only consider that window
  (use `KeyboardFocusManager` to determine the current modal window).
- If there is no modal window, consider all visible windows.
- For snapshots: all considered windows appear in the tree. When a considered
  window is a modal dialog with a visible owner chain, the snapshot emits a
  `[modal stack ...]` header above it so the AI can reason about what state
  will be returned to when the modal is dismissed — see UC-002 BR-14 / DR-016.
- For screenshots: all considered windows are arranged vertically in a single
  PNG with no overlapping.

> **This rule is load-bearing — see DR-017.** The owner chain of a modal and
> any windows blocked by a modal are **not** surfaced as interactable roots;
> they are exposed only as metadata (per DR-016 above). Do not "extend"
> window selection to return blocked windows or owner chains as refs — DR-017
> captures the rejected alternatives and the reasons they will not be
> revisited without explicit supersession.

## 6. Testing

See [verification](verification.md) for a complete list of testing instructions.

## 7. Constraints

- All Swing access on EDT
- Blocking modal dialogs are handled via fire-and-forget dispatch
- Depends on `tiny-mcp-server` for MCP protocol handling
