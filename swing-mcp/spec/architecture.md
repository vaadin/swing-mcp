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

## 4. Testing

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

