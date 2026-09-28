# Swing MCP

An in-process MCP (Model Context Protocol) HTTP server for Java Swing apps,
designed to enable AI-driven inspection and interaction with Swing UIs.
The primary use case is AI-assisted migration of Swing apps to Vaadin.

## Quick Demo

See it work on the bundled demo app before wiring it into your own. You need a
JDK between 11 and 24 to build.

1. Start the demo app. A "Select Application" dialog opens:
   ```bash
   ./gradlew :test-apps:run
   ```
2. Register the server with Claude Code (once):
   ```bash
   claude mcp add --transport http swing-mcp http://127.0.0.1:18088/mcp
   ```
3. Start `claude` **after** the app is up. Claude Code connects to MCP
   servers only at startup (see
   [below](#when-the-agent-says-the-swing-tools-are-missing)). Then ask:

   > Launch the Login App, try logging in as bob/bob and tell me what
   > happens, then log in as admin/admin and quit via the File menu.

Claude reads each screen as a text snapshot rather than a screenshot. Here is
the one it gets after the failed login:

```
[modal stack (3, topmost first): JDialog "Login Failed" / LoginDialog "Login" / JFrame "Login App"]
- JDialog (dialog) "Login Failed" [ref=1, modal] actions: close
  - JOptionPane (alert)
    - JLabel (label) "Invalid username or password."
    - JLabel (icon)
    - JButton (push_button) "OK" [ref=2, focused] actions: click
```

It then clicks, types and selects through the app's real listeners, one
`ref` at a time. For a migration, try:

> Explore every screen of this app and sketch the equivalent Vaadin views.

## Using in Swing Apps

### Option 1: Java Agent (no code changes)

Download `swing-mcp-agent-<version>.jar` from the
[releases page](https://github.com/vaadin/swing-mcp/releases) and attach it as a
`-javaagent` when launching your app. The MCP server starts automatically before
`main()` runs:

```bash
java -javaagent:swing-mcp-agent-1.0.jar -jar your-app.jar
```

The agent is a fat jar — it bundles all required dependencies, so no
additional classpath entries are needed.

### Option 2: Programmatic startup

> **Not released yet.** Only the agent is published so far; the `swing-mcp`
> library will follow. Until then, use Option 1.

Add `swing-mcp` as a dependency and start `SwingMCP` from your code:

```java
public class Application {
    public static void main(String[] args) {
        new SwingMCP().startAndAutoStop();
        SwingUtilities.invokeLater(() -> runApp());
    }
}
```

---

In both cases the MCP server listens at `http://127.0.0.1:18088/mcp` by default.
The agent takes another port from the `swing.mcp.port` system property
(`java -Dswing.mcp.port=20000 -javaagent:… -jar your-app.jar`); register that
URL instead.

## Registering with Claude Code

Register the in-process server once:

```bash
claude mcp add --transport http swing-mcp http://127.0.0.1:18088/mcp
```

### When the agent says the swing tools are missing

Claude Code connects to MCP servers only when it starts. If the Swing app was
not running then, `swing-mcp` is marked failed (`ECONNREFUSED`) and no
`swing_*` tool exists for the whole session. The agent cannot fix this itself:
`/mcp` is a command only you can run, and the server's own instructions never
reached it. So:

1. Start the Swing app.
2. In Claude Code, run `/mcp` → `swing-mcp` → **Reconnect**.

Tools that are listed but *failing* are a different case: the app went down
mid-session. Restart it and nothing else — Claude Code re-initializes on its
own at the next tool call. A connection refused with
HTTP 409 means another session holds the app — see
[Single session](#single-session).

## Known Limitations

### Blocking modal dialogs

Swing-MCP handles apps that open blocking modal dialogs (e.g.
`JOptionPane.showMessageDialog()`) from action listeners. Mutation tools
dispatch their action via `SwingUtilities.invokeLater()` and return
immediately, so the HTTP response is sent before the action runs on the EDT.
The AI client observes the result on the next `swing_snapshot` or
`swing_screenshot` call.

If the EDT becomes unresponsive for any other reason, Swing-MCP will time out
after 10 seconds and return an error that includes the EDT stack trace.

### Single session

Only one AI agent can control the Swing app at a time. A second connection
attempt receives HTTP 409 Conflict. If an agent disconnects without closing
the session, the session remains locked — restart the Swing app to clear it.

### Localhost only

The MCP server binds to `127.0.0.1` only. No authentication is provided.
The developer is responsible for local machine security.

### Drag and drop

`swing_drag` expects the source and the target to be in the same window. Dragging
between two top-level windows is not supported, and nothing rejects it — it simply
will not do what you meant.

When a display is present and the source component is showing, the drag runs
through `java.awt.Robot`, which exercises the real drag-and-drop pipeline.
Otherwise it falls back to dispatching synthetic `MouseEvent`s: those reach
`MouseListener`, `MouseMotionListener` and `DragGestureRecognizer`, but whether the
native transfer phase (`DropTarget` events) follows is platform-dependent.

## Build

```bash
./gradlew                      # clean + build + all tests (default)
./gradlew test                 # run all tests
```

## Project Layout

Subprojects:

- **`swing-mcp`** — Swing-specific MCP tools built on top of [TinyMCPServer](https://github.com/mvysny/tinymcpserver), a minimal MCP server in pure Java (GSON + built-in HttpServer). Provides accessibility tree snapshots, screenshots, and UI interaction tools.
- **`swing-mcp-agent`** — A Java Instrumentation Agent that starts the MCP server automatically via `-javaagent`. No code changes to the target app required.
- **`test-apps`** — Demo Swing applications and screen-mode integration tests.

## Swing Component Reference

How each Swing component appears in `swing_snapshot` output — which
components are covered and what an agent will see of them. The agent itself
does not need this section: it reads the snapshot and the instructions the
server sends at connect time.

Component-backed nodes use `JClassName (role)` format; non-Component virtual
children (e.g. JList items, JTree nodes) use `(role)` only.

### Top-Level Containers

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JFrame` | `FRAME` | No | `- JFrame (frame) "My App" [ref=1] actions: close` |
| `JDialog` | `DIALOG` | No | `- JDialog (dialog) "Confirm" [ref=1, modal] actions: close` |
| `JInternalFrame` | `INTERNAL_FRAME` | No | `- JInternalFrame (internal_frame) "Document" [ref=1] actions: close` |
| `JDesktopIcon` | `DESKTOP_ICON` | No (children hard-excluded) | `- JDesktopIcon (desktop_icon) "Document" [ref=1] actions: close` |
| `JRootPane` | `ROOT_PANE` | Transparent | *(children promoted to parent)* |

### Framework-Internal (Always Pruned)

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLayeredPane` | `LAYERED_PANE` | Transparent | *(children promoted to parent)* |
| `JViewport` | `VIEWPORT` | Transparent | *(children promoted to parent)* |
| `Box.Filler` / rigid area | `FILLER` | Transparent | *(children promoted to parent)* |
| `CellRendererPane` | *(n/a)* | Hard-excluded | *(dropped with descendants)* |

### Containers & Layout

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JPanel` (unnamed) | `PANEL` | Transparent | *(children promoted to parent)* |
| `JPanel` (named / titled border) | `PANEL` | No | `- JPanel (panel) "Details"` |
| `JScrollPane` | `SCROLL_PANE` | No | `- JScrollPane (scroll_pane)` |
| `JSplitPane` | `SPLIT_PANE` | No | `- JSplitPane (split_pane)` |
| `JTabbedPane` | `PAGE_TAB_LIST` | No | `- JTabbedPane (page_tab_list)` |
| *(tab within JTabbedPane)* | `PAGE_TAB` | No | `- (page_tab) "General" [selected]` |
| `JToolBar` | `TOOL_BAR` | No | `- JToolBar (tool_bar) "Main"` |
| `JOptionPane` | `OPTION_PANE` | No | `- JOptionPane (option_pane)` |

### Buttons

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JButton` | `PUSH_BUTTON` | No | `- JButton (push_button) "Save" [ref=1] actions: click` |
| `JToggleButton` | `TOGGLE_BUTTON` | No | `- JToggleButton (toggle_button) "Bold" [ref=1] actions: click` |
| `JCheckBox` | `CHECK_BOX` | No | `- JCheckBox (check_box) "Remember me" [ref=1, checked] actions: click` |
| `JRadioButton` | `RADIO_BUTTON` | No | `- JRadioButton (radio_button) "Option A" [ref=1] actions: click` |

### Text Input

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JTextField` | `TEXT` | No | `- JTextField (text) "Name" [ref=1, editable] actions: get_text, set_text` |
| `JPasswordField` | `PASSWORD_TEXT` | No | `- JPasswordField (password_text) "Password" [ref=1, editable] actions: get_text, set_text` |
| `JTextArea` | `TEXT` | No | `- JTextArea (text) "Notes" [ref=1, editable, multi_line] actions: get_text, set_text` |
| `JEditorPane` | `TEXT` | No | `- JEditorPane (text) "Content" [ref=1, editable, multi_line] actions: get_text, set_text` |

### Selection & Data Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JComboBox` | `COMBO_BOX` | No | `- JComboBox (combo_box) "Country" [ref=1] actions: toggle_popup` |
| `JList` | `LIST` | No | `- JList (list) [ref=1] actions: multi-selection, get_cell_count, get_cells` |
| *(child of JList)* | `LABEL` | No | `  - (label) "Item 1" [ref=2] actions: click` |
| `JTree` | `TREE` | No | `- JTree (tree) [ref=1] actions: get_cell_count, get_cells` |
| *(non-leaf tree node)* | varies | No | `  - (label) "Folder" [ref=2] actions: toggle_expand, click` |
| `JTable` | `TABLE` | No | `- JTable (table) [ref=1] columns: [ID, Name, City] actions: multi-selection` |
| *(row of JTable)* | — | No | `  - row 0: 1 \| Alice \| NY` |

### Value Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JSlider` | `SLIDER` | No | `- JSlider (slider) "Volume" [ref=1, horizontal] actions: increment, decrement, get_value, set_value` |
| `JSpinner` | `SPIN_BOX` | No | `- JSpinner (spin_box) "Quantity" [ref=1] actions: increment, decrement, get_value, set_value` |
| `JProgressBar` | `PROGRESS_BAR` | No | `- JProgressBar (progress_bar) "Loading" [horizontal] actions: get_value` |

### Display Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLabel` | `LABEL` | No | `- JLabel (label) "Status: OK"` |
| `JToolTip` | `TOOL_TIP` | No | `- JToolTip (tool_tip) "Click to save"` |
| `JSeparator` | `SEPARATOR` | No | `- JSeparator (separator)` |
| `JScrollBar` | `SCROLL_BAR` | No | `- JScrollBar (scroll_bar) [ref=1, vertical] actions: increment, decrement, get_value, set_value` |

### Menu Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JMenuBar` | `MENU_BAR` | No | `- JMenuBar (menu_bar)` |
| `JMenu` | `MENU` | No | `- JMenu (menu) "File" [ref=1] actions: click` |
| `JMenuItem` | `MENU_ITEM` | No | `- JMenuItem (menu_item) "Open" [ref=2] actions: click` |
| `JCheckBoxMenuItem` | `CHECK_BOX` | No | `- JCheckBoxMenuItem (check_box) "Word Wrap" [ref=3, checked] actions: click` |
| `JRadioButtonMenuItem` | `RADIO_BUTTON` | No | `- JRadioButtonMenuItem (radio_button) "Light Theme" [ref=4] actions: click` |
| `JPopupMenu` | `POPUP_MENU` | No | `- JPopupMenu (popup_menu)` |

**Notes:**
- `JLabel` has no actions and therefore no ref — it appears in the snapshot for context but is not interactable.
- `JProgressBar` exposes `get_value` but not `set_value` (read-only value role).
- `JCheckBoxMenuItem` and `JRadioButtonMenuItem` share roles with their non-menu counterparts (`CHECK_BOX`, `RADIO_BUTTON`).
- `JTextArea` and `JEditorPane` share the `TEXT` role with `JTextField` but include the `multi_line` state.
- `JFrame` supports a synthetic `iconified` state: `[iconified]` appears in the bracket when the frame is minimized.
- `JTable` shows `columns: [...]` when inside a `JScrollPane`. Children render as pipe-separated row lines (`row 0: val | val`), not individual cell labels.
- `get_cell_count`/`get_cells` only appear on truncated `JList`/`JTree` (not `JTable`). `JTree` selection is suppressed — selection tools work only on `JList` and `JTable`.
- Snapshot examples show typical states; actual output depends on the component's runtime configuration.
- Mutation actions prefixed with `!` are unavailable because the component is disabled or read-only — a disabled button shows `actions: !click`, a read-only field `actions: get_text, !set_text`.
- A custom subclass keeps its own name alongside the Swing class it extends: `SearchField -> JTextField (text)`.
- The line grammar, the pruning rules behind the `Pruned?` column above, and the full state list are in [`design/snapshot-format.md`](design/snapshot-format.md).

## License

Copyright 2000-2026 Vaadin Ltd.

Licensed under the [Apache License, Version 2.0](LICENSE). Every source file
carries the corresponding header; contributions are accepted under the same
license.
