# CLAUDE.md

This is a pure Java/Gradle project implementing an in-process
Model Context Protocol (MCP)
server for a Java Swing app. There are two subprojects:

- `tiny-mcp-server`
- `swing-mcp`

Since this project is intended to be added as a jar file
to an existing Swing app, it must have as few runtime dependencies
as possible, to avoid transitive dependency version clashes.
Do not introduce new runtime dependencies without asking.

`libs.version.toml` and `build.gradle.kts`/`settings.gradle.kts`
are the source of truth for dependencies and versions.
Do not modify these files without asking.

## tiny-mcp-server

This subproject implements a generic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built-in in Java SDK 11+.

The server must not use `io.modelcontextprotocol.sdk` runtime
dependency since it brings the jackson library (a huge dependency)
and requires a servlet container to run (another huge dependency).
The server must also not use Quarkus MCP server (the Swing app
startup is set in stone and is not to be changed to Quarkus way)
nor Spring AI MCP (a huge dependency).

## swing-mcp

Leverages the `javax.accessibility` API to construct an accessibility
tree, which is then served as a YAML snapshot to a MCP client
(Claude Code).

Leverages Swing built-in capability to obtain screenshot of the app by
rendering Swing Windows
(via `window.paint()` to a `BufferedImage` graphics).

Only considers visible Windows of the app. If there is a modal visible window,
only consider that particular window (use `KeyboardFocusManager` to figure out
the current modal window); if there is no modal window, consider all
visible Windows. When creating YAML snapshot, all considered windows will be present.
When creating a screenshot, always create one PNG image: if there are multiple
Windows to be considered, they should be arranged vertically in the PNG image
with no overlapping.

Care must be taken to always access Swing component in EDT.

Testing: testing involves running a Swing app. TODO verify whether `javax.accessibility`
API works in headless mode: if yes, we can test way simpler. However,
the screenshot capturing functionality probably requires Xvfb.

This subproject needs to be specified further and is currently not to be implemented.
