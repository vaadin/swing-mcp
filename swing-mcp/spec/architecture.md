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

---

## 3. Testing

- Pure JUnit 6 tests
  - Tests live in `src/test/java/`, mirroring the main package structure
  - Also uses MCP client library to call MCP tasks.
- Uses headless mode for fast testing of the 
  - Sets `java.awt.headless` to `true`
  - No Xvfb usage in this subproject: we'll create a dedicated subproject for this later on.
- The `MCPServerTest` test class:
  - It starts the MCPServer before all tests, and stops it afterwards.
  - A test client is initialized before all tests as well; use the official MCP client with the HTTP Transport and Jackson3
  - No tools are tested: we will test each tool in its own separate test class.

