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

- **`TinyMCPServer`** — server lifecycle, the session map, routing of
  `initialize` / `ping` / `DELETE`, and the single exception-to-response
  seam (`handleRequest`). Registration APIs (`addTool`, `addResource`,
  `addPrompt`) also live here. Two transport entry points:
  - `start()` — bind the HTTP listener and return; request handling
    runs on HTTP-server threads. Default address `127.0.0.1:18088/mcp`.
  - `runStdio(InputStream in, OutputStream out)` — block on a
    newline-delimited JSON-RPC read loop until `in` reaches EOF.
    Single-session by definition (DR-007). Repoints `System.out` to
    `System.err` at entry so stray prints can't corrupt the wire.

  Also owns the shared `ScheduledExecutorService` (daemon threads named
  `tiny-mcp-server-N`) created in `start()` / `runStdio` and shut down
  in `stop()` / on EOF; it runs the once-per-minute idle-session
  cleanup tick and is exposed to tool handlers via `getExecutor()` for
  short background work (see DR-006).
- **`MCPClient` / `TinyMCPClient` / `AutoRetryMCPClient`** — small HTTP
  client speaking the MCP transport from the caller side. Lives in a
  separate package, `com.vaadin.swingmcp.tinymcpclient`, within the same
  Gradle subproject. `MCPClient` is the interface; `TinyMCPClient` is
  the no-retry concrete implementation that throws
  `MCPSessionLostException` on HTTP 404; `AutoRetryMCPClient` is an
  opt-in decorator obtained via the `MCPClient.autoRetry()` default
  method that re-initializes once and replays the failed call.
  Stateless callers opt in; stateful callers (including the swing-mcp
  proxy) deliberately do not. Minimal surface: `initialize`,
  `listTools`, `callTool`, `close`. See DR-008.
- **`MCPSession`** — one per active session; dispatches session-scoped
  methods (`tools/*`, `resources/*`, `prompts/*`) under a per-session
  `ReentrantLock`. Owns session attributes and exposes `getCurrent()` as
  a thread-local for handler code. Every request flows through
  `runLocked`, which refreshes the session's last-access timestamp and
  fails fast with HTTP 404 if the session has already been evicted by
  the cleanup tick; `getServer()` gives tool code access to the owning
  `TinyMCPServer` (and thus its shared executor).
- **`MCPToolHandler` / `MCPResourceHandler` / `MCPPromptHandler`** — the
  three feature handlers. Each owns its registry and implements the
  corresponding `*/list` and `*/call|read|get` methods.
- **`JsonRpcExchange`** — request-scoped wrapper around `HttpExchange`
  with JSON-RPC parse + response helpers.
- **`MCPProtocol`** — all GSON POJOs plus small JSON utilities. Also
  hosts the request records `ToolRequest`, `PromptRequest`, and
  `ResourceRequest` (see DR-009) carrying the request key (name or
  URI), arguments, transport headers, and the JSON-RPC `_meta` object.
  No hand-rolled JSON anywhere else.
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
prompts, then either call `start()` (HTTP) or `runStdio(in, out)`
(stdio). HTTP mode pairs with `stop()`; stdio mode terminates when
`in` reaches EOF. A given instance picks one transport for its
lifetime — there is no `start()`-then-`runStdio` switching. No repeated
start/stop cycles. In HTTP mode the server binds to `127.0.0.1` only;
port `0` is accepted and means "let the OS pick an ephemeral port" —
after `start()`, `getPort()` returns the actual bound port.

By default the server accepts multiple concurrent sessions. Subclasses
can enforce a single-session policy (as swing-mcp does) by overriding
`acceptNewSession()` — returning `false` causes `initialize` to be
rejected with HTTP 409 and JSON-RPC code `-32002`. Sessions idle for
30 minutes are evicted by a background cleanup tick, which invokes the
same `onSessionClosed` hook as an explicit DELETE. See DR-003 / DR-005
/ DR-006 for the full session lifecycle and idle-eviction policy.

#### Tool registration API

Tools are registered via `addTool(...)` on `TinyMCPServer` before
calling `start()`. Resources and prompts have analogous `addResource`
and `addPrompt` methods. The tool registration API accepts:

- **name** — tool name (string); must match `[a-zA-Z_][a-zA-Z0-9_]*`
- **description** — human-readable description (string)
- **inputSchema** — parameter schema built via a fluent builder (see below)
- **function** — a lambda/callback that receives a `ToolRequest`
  (DR-009: name, arguments, transport headers, JSON-RPC `_meta`) and
  returns a result

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
- **Stdio transport tests** drive `runStdio` on a worker thread with
  piped streams; assertions read the response stream and verify
  newline-delimited framing. A separate test asserts that no protocol
  code path writes to the captured `System.out` reference (only to the
  private writer used by the framing layer).
- **`MCPClient` tests** stand up a `TinyMCPServer` HTTP instance with a
  small registered tool, then drive the full client surface
  (`initialize`, `listTools`, `callTool`, `close`) against it. Two
  session-loss scenarios are covered separately:
  - With a plain `TinyMCPClient`, forcing a 404 between two calls (e.g.
    via the package-private session-eviction entry point) must surface
    `MCPSessionLostException` to the caller.
  - With the same client wrapped by `.autoRetry()`, the same scenario
    must succeed transparently on the second call. A second 404 in the
    replay must surface to the caller.
- **Loopback proxy test** stitches the two together: an HTTP
  `TinyMCPServer` (the "real" server) with a registered tool, and a
  stdio `TinyMCPServer` (the "proxy") whose single registered
  forwarding `ToolFunction` uses an `MCPClient` to call upstream by
  the request's `name`. The test drives the stdio server via piped
  streams and verifies the round-trip — proving that DR-007 (stdio),
  DR-008 (client), and DR-009 (request records) compose end-to-end
  without Swing.

