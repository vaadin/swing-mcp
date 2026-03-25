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

Swing-MCP assumes the target Swing app **does not use blocking modal dialogs**
(e.g., `JOptionPane.showMessageDialog()`, `JOptionPane.showConfirmDialog()`,
`JFileChooser.showOpenDialog()`, and similar calls that block the EDT).

**Why:** Swing-MCP marshals all UI access onto the EDT via
`SwingUtilities.invokeAndWait()`. If the EDT is blocked by a modal dialog,
the MCP HTTP request will deadlock until the dialog is dismissed manually.

**What to do:** Before using Swing-MCP, replace blocking dialog calls in the
Swing app with non-blocking alternatives. This preparation step is also
beneficial for the Vaadin migration itself, since blocking dialogs similarly
prevent the Vaadin web UI from rendering.

If a blocking dialog is detected at runtime, Swing-MCP logs an error.
The server will remain unresponsive until the dialog is dismissed.

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

TODO: documentation on how to integrate into a customer Swing app and
how to connect from Claude Code.

