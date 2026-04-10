# Swing MCP

An in-process MCP (Model Context Protocol) HTTP server for Java Swing apps,
designed to enable AI-driven inspection and interaction with Swing UIs.
The primary use case is AI-assisted migration of Swing apps to Vaadin.

## Architecture

Three subprojects:

- **`tiny-mcp-server`** — A generic, minimal MCP HTTP server in pure Java (GSON + built-in HttpServer). No external framework dependencies.
- **`swing-mcp`** — Swing-specific MCP tools built on top of `tiny-mcp-server`. Provides accessibility tree snapshots, screenshots, and UI interaction tools.
- **`swing-mcp-agent`** — A Java Instrumentation Agent that starts the MCP server automatically via `-javaagent`. No code changes to the target app required.

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

## Swing Component Reference

How each Swing component appears in `swing_snapshot` output.

### Top-Level Containers

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JFrame` | `FRAME` | No | `- frame "My App" [ref=1] actions: close` |
| `JDialog` | `DIALOG` | No | `- dialog "Confirm" [ref=1, modal] actions: close` |
| `JInternalFrame` | `INTERNAL_FRAME` | No | `- internal_frame "Document" [ref=1] actions: close` |
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
| `JPanel` (named / titled border) | `PANEL` | No | `- panel "Details"` |
| `JScrollPane` | `SCROLL_PANE` | No | `- scroll_pane` |
| `JSplitPane` | `SPLIT_PANE` | No | `- split_pane` |
| `JTabbedPane` | `PAGE_TAB_LIST` | No | `- page_tab_list` |
| *(tab within JTabbedPane)* | `PAGE_TAB` | No | `- page_tab "General" [selected]` |
| `JToolBar` | `TOOL_BAR` | No | `- tool_bar "Main"` |
| `JOptionPane` | `OPTION_PANE` | No | `- option_pane` |

### Buttons

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JButton` | `PUSH_BUTTON` | No | `- push_button "Save" [ref=1] actions: click` |
| `JToggleButton` | `TOGGLE_BUTTON` | No | `- toggle_button "Bold" [ref=1] actions: click` |
| `JCheckBox` | `CHECK_BOX` | No | `- check_box "Remember me" [ref=1, checked] actions: click` |
| `JRadioButton` | `RADIO_BUTTON` | No | `- radio_button "Option A" [ref=1] actions: click` |

### Text Input

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JTextField` | `TEXT` | No | `- text "Name" [ref=1, editable] actions: get_text, set_text` |
| `JPasswordField` | `PASSWORD_TEXT` | No | `- password_text "Password" [ref=1, editable] actions: get_text, set_text` |
| `JTextArea` | `TEXT` | No | `- text "Notes" [ref=1, editable, multi_line] actions: get_text, set_text` |
| `JEditorPane` | `TEXT` | No | `- text "Content" [ref=1, editable, multi_line] actions: get_text, set_text` |

### Selection & Data Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JComboBox` | `COMBO_BOX` | No | `- combo_box "Country" [ref=1] actions: toggle_popup` |
| `JList` | `LIST` | No | `- list [ref=1] actions: get_selection, set_selection, clear_selection, select_all, get_children_count, get_children` |
| *(child of JList)* | `LABEL` | No | `  - label "Item 1" [ref=2] actions: click` |
| `JTree` | `TREE` | No | `- tree [ref=1] actions: get_selection, set_selection, clear_selection, select_all, get_children_count, get_children` |
| *(non-leaf tree node)* | varies | No | `  - label "Folder" [ref=2] actions: toggle_expand, click` |
| `JTable` | `TABLE` | No | `- table [ref=1] actions: get_selection, set_selection, clear_selection, select_all, get_children_count, get_children` |

### Value Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JSlider` | `SLIDER` | No | `- slider "Volume" [ref=1, horizontal] actions: increment, decrement, get_value, set_value` |
| `JSpinner` | `SPIN_BOX` | No | `- spin_box "Quantity" [ref=1] actions: increment, decrement, get_value, set_value` |
| `JProgressBar` | `PROGRESS_BAR` | No | `- progress_bar "Loading" [horizontal] actions: get_value` |

### Display Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JLabel` | `LABEL` | No | `- label "Status: OK"` |
| `JToolTip` | `TOOL_TIP` | No | `- tool_tip "Click to save"` |
| `JSeparator` | `SEPARATOR` | No | `- separator` |
| `JScrollBar` | `SCROLL_BAR` | No | `- scroll_bar [ref=1, vertical] actions: increment, decrement, get_value, set_value` |

### Menu Components

| Swing Component | `AccessibleRole` | Pruned? | Snapshot Example |
|---|---|---|---|
| `JMenuBar` | `MENU_BAR` | No | `- menu_bar` |
| `JMenu` | `MENU` | No | `- menu "File" [ref=1] actions: click` |
| `JMenuItem` | `MENU_ITEM` | No | `- menu_item "Open" [ref=2] actions: click` |
| `JCheckBoxMenuItem` | `CHECK_BOX` | No | `- check_box "Word Wrap" [ref=3, checked] actions: click` |
| `JRadioButtonMenuItem` | `RADIO_BUTTON` | No | `- radio_button "Light Theme" [ref=4] actions: click` |
| `JPopupMenu` | `POPUP_MENU` | No | `- popup_menu` |

**Notes:**
- `JLabel` has no actions and therefore no ref — it appears in the snapshot for context but is not interactable.
- `JProgressBar` exposes `get_value` but not `set_value` (read-only value role).
- `JCheckBoxMenuItem` and `JRadioButtonMenuItem` share roles with their non-menu counterparts (`CHECK_BOX`, `RADIO_BUTTON`).
- `JTextArea` and `JEditorPane` share the `TEXT` role with `JTextField` but include the `multi_line` state.
- Snapshot examples show typical states; actual output depends on the component's runtime configuration.
- See [UC-002 spec](swing-mcp/spec/use-cases/use-case-002-swing-snapshot.md) for full pruning rules and snapshot format details.

## Build

```bash
./gradlew                      # clean + build + all tests (default)
./gradlew test                 # run all tests
```

## Using in Swing Apps

### Option 1: Java Agent (no code changes)

Attach `swing-mcp-agent` as a `-javaagent` when launching your app.
The MCP server starts automatically before `main()` runs:

```bash
java -javaagent:swing-mcp-agent-0.0.1-SNAPSHOT.jar -jar your-app.jar
```

The agent is a fat jar — it bundles all required dependencies, so no
additional classpath entries are needed.

### Option 2: Programmatic startup

Add `swing-mcp` as a dependency and start the `MCPServer` from your code:

```java
public class Application {
    public static void main(String[] args) throws IOException {
        new MCPServer().startAndAutoStop();
        SwingUtilities.invokeLater(() -> runApp());
    }
}
```

---

In both cases the MCP server listens at `http://127.0.0.1:18088/mcp` by default.