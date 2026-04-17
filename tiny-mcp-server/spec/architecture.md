# Architecture

This server uses only Google GSON for JSON serialization/parsing and
the JDK built-in `com.sun.net.httpserver.HttpServer` for HTTP. No
other runtime dependencies. Do not generate JSONs via String
concatenation nor via the GSON element API: use POJO class mapping
(see `MCPProtocol`).

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

All classes live in `com.vaadin.swingmcp.tinymcpserver`. The split follows
the natural seams in the protocol:

- **`TinyMCPServer`** — HTTP lifecycle, the session map, routing of
  `initialize` / `ping` / `DELETE`, and the single exception-to-response
  seam (`handleRequest`). Registration APIs (`addTool`, `addResource`,
  `addPrompt`) also live here.
- **`MCPSession`** — one per active session; dispatches session-scoped
  methods (`tools/*`, `resources/*`, `prompts/*`) under a per-session
  `ReentrantLock`. Owns session attributes and exposes `getCurrent()` as
  a thread-local for handler code.
- **`MCPToolHandler` / `MCPResourceHandler` / `MCPPromptHandler`** — the
  three feature handlers. Each owns its registry and implements the
  corresponding `*/list` and `*/call|read|get` methods.
- **`JsonRpcExchange`** — request-scoped wrapper around `HttpExchange`
  with JSON-RPC parse + response helpers.
- **`MCPProtocol`** — all GSON POJOs plus small JSON utilities. No
  hand-rolled JSON anywhere else.
- **`InputSchemaBuilder` / `PromptArgumentsBuilder`** — fluent builders
  for tool input schemas and prompt argument lists.
- **`MCPParameterParser`** — parses and type-coerces incoming tool
  arguments against a declared schema.
- **Exceptions:** `MCPServerException` (JSON-RPC protocol error, carries
  code + HTTP status), `MCPErrorResponseException` (tool-layer
  `isError: true` with a clean message), `TransportIOException` (socket
  is dead). See DR-004.

### TinyMCPServer

Intended lifecycle: create a new instance, register tools / resources /
prompts, call `start()`, later `stop()`. No repeated start/stop cycles.
The server binds to `127.0.0.1` only. Port `0` is accepted and means
"let the OS pick an ephemeral port" — after `start()`, `getPort()`
returns the actual bound port.

By default the server accepts multiple concurrent sessions. Subclasses
can enforce a single-session policy (as swing-mcp does) by overriding
`acceptNewSession()` — returning `false` causes `initialize` to be
rejected with HTTP 409 and JSON-RPC code `-32002`. See DR-003 / DR-005
for the full session lifecycle.

#### Tool registration API

Tools are registered via `addTool(...)` on `TinyMCPServer` before
calling `start()`. Resources and prompts have analogous `addResource`
and `addPrompt` methods. The tool registration API accepts:

- **name** — tool name (string); must match `[a-zA-Z_][a-zA-Z0-9_]*`
- **description** — human-readable description (string)
- **inputSchema** — parameter schema built via a fluent builder (see below)
- **function** — a lambda/callback that receives parsed parameters and returns a result

Supported parameter types: `string`, `integer`, `number`, `boolean`, `array`, `object`.
For `integer` parameters, TinyMCPServer accepts a JSON number with no fractional part (e.g. `1.0` is accepted as `1`); a number with a non-zero fractional part is rejected.
For `array` parameters, the handler receives a `List<Object>` (elements follow the same Java type mapping recursively).
For `object` parameters, the handler receives a `Map<String, Object>` (values follow the same Java type mapping recursively).
Unknown parameters are logged at WARNING and ignored.

The tool function returns a `MCPProtocol.Content` — built via the
`Content.text(...)`, `Content.json(...)`, `Content.image(...)` (from a
base64 string or a `BufferedImage`), `Content.audio(...)`, or
`Content.resource(...)` factories — or `null` for an empty result
(produces `"content": []`).

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
- `.requiredNumber(name, description)` / `.optionalNumber(name, description)`
- `.requiredBoolean(name, description)` / `.optionalBoolean(name, description)`
- `.requiredArray(name, description)` / `.optionalArray(name, description)`
- `.requiredObject(name, description)` / `.optionalObject(name, description)`
- `.build()` — produces the final schema object

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

