# Swing MCP

An in-process MCP (Model Context Protocol) HTTP server for Java Swing apps,
designed to enable AI-driven inspection and interaction with Swing UIs.
The primary use case is AI-assisted migration of Swing apps to Vaadin.

## Architecture

Two subprojects:

- **`tiny-mcp-server`** — A generic, minimal MCP HTTP server in pure Java (GSON + built-in HttpServer). No external framework dependencies.
- **`swing-mcp`** — Swing-specific MCP tools built on top of `tiny-mcp-server`. Provides accessibility tree snapshots, screenshots, and UI interaction tools.

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

## Build

```bash
./gradlew                      # clean + build + all tests (default)
./gradlew test                 # run all tests
```

## Using in Swing Apps

Simply start the `MCPServer`; it will run by default at `http://127.0.0.1:18088/mcp`:

```
public class Application {
    public static void main(String[] args) throws IOException {
        new MCPServer().startAndAutoStop();
        SwingUtilities.invokeLater(() -> runApp());
    }
}
```