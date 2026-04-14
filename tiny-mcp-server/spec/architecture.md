# Architecture

This server only uses Google GSON library for JSON
serialization and parsing. Do not generate
JSONs via String concatenation nor via the element API:
use a POJO classes mapping instead.

This server must not use `io.modelcontextprotocol.sdk` runtime
dependency since it brings the jackson library (a huge dependency)
and requires a servlet container to run (another huge dependency).
The server must also not use Quarkus MCP server (the Swing app
startup is set in stone and is not to be changed to Quarkus way)
nor Spring AI MCP (a huge dependency), nor Jackson (a huge dependency).

For testing purposes, we are leveraging the official
`modelcontextprotocol/java-sdk` Java SDK library which provides a MCP
client. We hope that the official MCP client will run in strict mode and
will throw an exception on any malformed MCP JSON message. Don't use the
`mcp-test` module - even though it looks like the perfect fit, it's the
`java-sdk` internal testing tool not meant to be used by other projects.
Alternatively we can use `LangChain4j`.

---

## 1. Technology Stack

- Google GSON
- For testing: the official Java MCP SDK
- Gradle (wrapper included)
- Java
- Testing: JUnit 6

---

## 2. Application Structure

```
com.vaadin.swingmcp.tinymcpserver
  TinyMCPServer.java            — The tiny HTTP MCP server implementation itself
  MCPProtocol.java              — All Java POJOs for JSON live here
  InputSchemaBuilder.java       — Fluent builder for tool input schemas
  MCPServerException.java       — Exception carrying a JSON-RPC error code
  MCPErrorResponseException.java — Exception for MCP-level errors (isError: true)
```

### TinyMCPServer

Intended lifecycle: create a new instance, register custom tools, start
the MCP HTTP server, and stop it. No need to support repeated start/stop cycles.
The server binds to `127.0.0.1` only.

#### Tool registration API

Tools are registered via a Java method on the `TinyMCPServer` class before
calling `start()`. The API accepts:

- **name** — tool name (string)
- **description** — human-readable description (string)
- **inputSchema** — parameter schema built via a fluent builder (see below)
- **handler** — a lambda/callback that receives parsed parameters and returns a result

Supported parameter types: `string`, `integer`, `number`, `boolean`, `array`, `object`.
For `integer` parameters, TinyMCPServer accepts a JSON number with no fractional part (e.g. `1.0` is accepted as `1`); a number with a non-zero fractional part is rejected.
For `array` parameters, the handler receives a `List<Object>` (elements follow the same Java type mapping recursively).
For `object` parameters, the handler receives a `Map<String, Object>` (values follow the same Java type mapping recursively).
Supported return types: `string`, `JSON array`, `JSON object`, or `PNG image` (as bytes).

#### Tool parameter schema builder

A fluent Java builder class for defining tool input schemas. The builder
produces MCP-compliant JSON Schema for the tool's `inputSchema` field.

Example usage (illustrative):
```java
server.addTool("swing_click", "Click a UI element",
    new InputSchemaBuilder()
        .requiredInteger("ref", "The element reference number")
        .build(),
    params -> { /* handler */ });
```

The builder supports:
- `.requiredString(name, description)` / `.optionalString(name, description)`
- `.requiredInteger(name, description)` / `.optionalInteger(name, description)`
- `.requiredArray(name, description)` / `.optionalArray(name, description)`
- `.requiredObject(name, description)` / `.optionalObject(name, description)`
- `.build()` — produces the final schema object

### MCPProtocol

Contains all Java POJOs for JSON+GSON serialization purposes.
Also includes any necessary utility functions assisting MCP protocol JSON
serialization, deserialization, message construction etc.

---

## 3. Testing

- Pure JUnit 6 tests, testing the Tiny MCP server itself.
  - Tests live in `src/test/java/`, mirroring the main package structure
- The `TinyMcpServerTest` test class:
  - It starts the TinyMcpServer before all tests, and stops it afterwards.
  - A test client is initialized before all tests as well; use the official MCP client with the HTTP Transport and Jackson3
  - Registers a testing tool, then verifies the tool was called.
  - Test with parameter variations and return values
  - Also test whatever you deem necessary
- MCPProtocolTest: doesn't hurt to test the POJO deserialization as well.
  - Most important tests: test parsing and serialization on an actual real-world MCP JSONs.

