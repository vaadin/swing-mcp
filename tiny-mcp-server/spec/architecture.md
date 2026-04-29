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

- **`MCPHandler`** — transport-agnostic MCP dispatch core and the
  public configuration object. Owns the tool / resource / prompt
  registries (`addTool` / `addResource` / `addPrompt`), the session
  map (keyed by `Mcp-Session-Id`), the shared
  `ScheduledExecutorService` (daemon threads named
  `tiny-mcp-server-N`) created in `start()` and shut down in
  `stop()`, the once-per-minute idle-session cleanup tick, and the
  JSON-RPC dispatch for `initialize` / `ping` / routing to
  `MCPSession`. Knows nothing about HTTP or stdio framing; both
  transports drive the same handler instance. Constructor is
  0-arg; per DR-013, the session-lifecycle listeners
  (`acceptNewSession` — `IntPredicate` over the current session
  count, `onSessionStarted` and `onSessionClosed` —
  `Consumer<MCPSession>`) are configured via fluent setters
  (`setAcceptNewSession`, `setOnSessionStarted`,
  `setOnSessionClosed`) with sensible no-op defaults
  (always-accept, no-op on start/close). Setters are settable
  until the first session opens, then locked — calling any setter
  later throws `IllegalStateException`. `addTool` has an overload
  taking `ToolDescriptor` (DR-013) for callers that already hold a
  descriptor (e.g. `MCPProxy`, `AbstractSwingTool`). Methods that
  fail throw `MCPServerException` rather than writing a response;
  the transport translates. The shared executor is exposed via
  `getExecutor()` for tool handlers that need short background
  work (see DR-006), reachable from tool code via
  `MCPSession.getCurrent().getHandler().getExecutor()`.

- **`HttpMCPServer`** — HTTP transport (formerly `TinyMCPServer`).
  Owns the JDK `HttpServer`, routes incoming POST / DELETE / 405,
  validates the `Mcp-Session-Id` header, and writes JSON-RPC
  responses to the wire (the single exception-to-response seam,
  `handleRequest`). Constructor takes
  `(port, contextPath, MCPHandler)`; the caller configures the
  handler before passing it in. Public surface is intentionally
  small: `start()` / `stop()` / `getUrl()` / `getPort()` /
  `getContextPath()` / `getHandler()`. `start()` binds the HTTP
  listener on `127.0.0.1` (default `127.0.0.1:18088/mcp`) and calls
  `handler.start()`; `stop()` calls `handler.stop()` and tears down
  the HTTP server.

- **`StdioMCPServer`** — sibling stdio transport (DR-007). Owns the
  newline-delimited JSON-RPC read loop on the caller-supplied
  `InputStream` / `OutputStream`. Constructor takes `(MCPHandler)`.
  `runStdio(in, out)` calls `handler.start()`, blocks the calling
  thread until `in` reaches EOF, then calls `handler.stop()`,
  redirecting `System.out` to `System.err` only when
  `out == System.out` so stray prints can't corrupt the wire while
  leaving piped-stream tests unaffected. Single-session by
  definition: tracks one active `MCPSession` lazily populated on the
  first `initialize`; a re-`initialize` evicts the previous session
  before creating the new one.
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
  `listTools`, `callTool` (two- and three-arg overloads — the
  three-arg form takes a JSON-RPC `_meta` object, DR-013), `close`.
  See DR-008.
- **`MCPProxy`** — generic forwarding-proxy factory (DR-012).
  Static `MCPProxy.newHandler(List<ToolDescriptor>, URI upstreamUrl,
  ProxyMessages)` returns a fully-wired single-session
  `MCPHandler` whose tool functions forward to the upstream MCP
  server at `upstreamUrl` via `TinyMCPClient`. The caller wraps
  the returned handler in a transport (typically `StdioMCPServer`)
  and runs it. There is no `MCPProxy` instance — per-session state
  (lazy-initialized upstream client, drift cache) lives on
  `MCPSession.attributes`. `tools/list` is always answered from
  the supplied `ToolDescriptor` list; drift between that list and
  upstream's actual `listTools()` is detected at first call and
  cached as a permanent error for the session. `ProxyMessages`
  carries four pre-formatted error strings (upstream-down, drift,
  session-lost, IO-mid-call) which `MCPProxy` emits verbatim — no
  templating happens inside `tiny-mcp-server`. See DR-012 for the
  full lifecycle, drift policy, and error taxonomy.
- **`ToolDescriptor`** — public record `(name, description,
  InputSchema)` in the parent package `com.vaadin.swingmcp`
  (currently empty, intentional — see DR-013 / Q23). The in-memory
  contract type for a tool, distinct from the wire-shape POJO
  `MCPProtocol.Tool`. Consumed by `MCPProxy.newHandler` as the
  manifest, by `MCPHandler.addTool(ToolDescriptor, ToolFunction)`
  for direct registration, and by `swing-mcp-tool-defs` as the
  shared descriptor list across `swing-mcp` and
  `swing-mcp-proxy`. Equality on `ToolDescriptor` is structural
  via `MCPProtocol.InputSchema`'s structural `equals` / `hashCode`
  (DR-014).
- **`ProxyMessages`** — public record carrying the four
  human-readable error strings emitted by `MCPProxy` when upstream
  is down, drifting, lost the session, or threw an `IOException`
  mid-call. Each field's javadoc carries an example string showing
  the voice and concreteness expected (clear cause, named
  remediation, "do not retry" where appropriate). Defined in the
  same package as `MCPProxy` inside `tiny-mcp-server`.
- **`MCPSession`** — one per active session; dispatches session-scoped
  methods (`tools/*`, `resources/*`, `prompts/*`) under a per-session
  `ReentrantLock`. Owns session attributes and exposes `getCurrent()` as
  a thread-local for handler code. Every request flows through
  `runLocked`, which refreshes the session's last-access timestamp and
  fails fast with HTTP 404 if the session has already been evicted by
  the cleanup tick. `handlePost` returns the JSON-RPC `result` POJO
  rather than writing it; the transport renders the response.
  `getHandler()` gives tool code access to the owning `MCPHandler`
  (and thus its shared executor).
- **`MCPToolHandler` / `MCPResourceHandler` / `MCPPromptHandler`** — the
  three feature handlers. Each owns its registry and implements the
  corresponding `*/list` and `*/call|read|get` methods. Handler
  methods return the result POJO or throw `MCPServerException`; they
  do not touch the transport (DR-010).
- **`JsonRpcExchange`** — HTTP-transport-scoped wrapper around
  `HttpExchange` with JSON-RPC parse + response helpers. Used only by
  `HttpMCPServer`.
- **`MCPProtocol`** — all GSON POJOs plus small JSON utilities. Also
  hosts the request records `ToolRequest`, `PromptRequest`, and
  `ResourceRequest` (see DR-009) carrying the request key (name or
  URI), arguments, transport headers, and the JSON-RPC `_meta` object.
  No hand-rolled JSON anywhere else. `MCPProtocol.InputSchema` (and
  the property descriptors it references) implements deep
  structural `equals` / `hashCode` per DR-014 — used by `MCPProxy`'s
  drift probe and by `ToolDescriptor` equality.
- **`InputSchemaBuilder` / `PromptArgumentsBuilder`** — fluent builders
  for tool input schemas and prompt argument lists.
- **`MCPParameterParser`** — parses and type-coerces incoming tool
  arguments against a declared schema.
- **Exceptions:** `MCPServerException` (JSON-RPC protocol error, carries
  code + HTTP status), `MCPErrorResponseException` (tool-layer
  `isError: true` with a clean message), `TransportIOException` (socket
  is dead). See DR-004.

### HttpMCPServer

Intended lifecycle: build and configure an `MCPHandler` (register
tools / resources / prompts), construct
`new HttpMCPServer(port, contextPath, handler)`, then call `start()`.
`stop()` shuts the HTTP listener and the shared executor (via the
embedded `MCPHandler`). No repeated start/stop cycles. The server
binds to `127.0.0.1` only; port `0` is accepted and means "let the OS
pick an ephemeral port" — after `start()`, `getPort()` returns the
actual bound port.

`StdioMCPServer` is the sibling stdio transport (DR-007) driving its
own `MCPHandler` instance; HTTP and stdio are mutually exclusive per
handler.

### MCPProxy

Intended lifecycle: call
`MCPProxy.newHandler(toolDescriptors, upstreamUri, proxyMessages)` to
get a configured `MCPHandler`, wrap it in a transport (typically
`new StdioMCPServer(handler)`), and run the transport. There is no
`MCPProxy` instance to hold — see DR-012 for the rationale.

Behaviourally:

- `tools/list` is answered locally from `toolDescriptors`. Always.
  Even when upstream is down. Even after drift has been detected.
- The first `tools/call` of each fresh session pays three localhost
  round-trips: upstream `initialize`, `listTools` drift probe, and
  the actual forwarded call. Subsequent calls in the same fresh
  session pay one.
- The drift probe compares the supplied `toolDescriptors` set
  against upstream's `listTools()` set, hard-fail symmetric — any
  difference (extra on either side, or same name with different
  fields per DR-014's structural equality) caches a permanent
  `isError` for the session.
- Errors emitted to the LLM are the `ProxyMessages` strings,
  verbatim. Diagnostic detail (full descriptor JSONs on drift,
  exception stack traces on IO failure) goes to JUL WARNING on
  stderr — two audiences, two channels. The proxy module choosing
  the messages decides what to interpolate (URLs, remote names,
  etc.); `tiny-mcp-server` does no templating.

A re-`initialize` from the MCP client closes the current upstream
client and opens a new one; the next `tools/call` walks the
lazy-init + drift-probe path again. Consistent with the
"no hidden state across sessions" rule from DR-008.

By default the handler accepts multiple concurrent sessions. Callers
that need a single-session policy (as swing-mcp does) call
`handler.setAcceptNewSession(count -> count == 0)` before the first
session opens — when the predicate returns `false`, `initialize` is
rejected with HTTP 409 and JSON-RPC code `-32002`. Sessions idle for
30 minutes are evicted by a background cleanup tick, which invokes
the (optional) listener registered via `setOnSessionClosed` the same
way an explicit DELETE does. `setOnSessionStarted` (DR-013) is the
symmetric per-session-init hook — `MCPProxy.newHandler` uses it to
allocate per-session upstream-client state. All three setters lock
once the first session is accepted; later calls throw
`IllegalStateException`. See DR-003 / DR-005 / DR-006 / DR-011 /
DR-013 for the full session lifecycle, listener API, and
idle-eviction policy.

#### Tool registration API

Tools are registered via `addTool(...)` on `MCPHandler` before the
handler is started by a transport. Resources and prompts have
analogous `addResource` and `addPrompt` methods. The tool
registration API accepts:

- **name** — tool name (string); must match `[a-zA-Z_][a-zA-Z0-9_]*`
- **description** — human-readable description (string)
- **inputSchema** — parameter schema built via a fluent builder (see below)
- **function** — a lambda/callback that receives a `ToolRequest`
  (DR-009: name, arguments, transport headers, JSON-RPC `_meta`) and
  returns a result

Supported parameter types: `string`, `integer`, `number`, `boolean`, `array`, `object`.
For `integer` parameters, HttpMCPServer accepts a JSON number with no fractional part (e.g. `1.0` is accepted as `1`); a number with a non-zero fractional part is rejected.
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
- **`MCPClient` tests** stand up a `HttpMCPServer` HTTP instance with a
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
  `HttpMCPServer` (the "real" server) with a registered tool, and a
  stdio `HttpMCPServer` (the "proxy") whose single registered
  forwarding `ToolFunction` uses an `MCPClient` to call upstream by
  the request's `name`. The test drives the stdio server via piped
  streams and verifies the round-trip — proving that DR-007 (stdio),
  DR-008 (client), and DR-009 (request records) compose end-to-end
  without Swing.
- **`MCPProxy` integration tests** (DR-012) — drive the factory
  output through a `StdioMCPServer` against a real upstream
  `HttpMCPServer` over loopback. Required scenarios:
  - **Down-then-up.** Start the proxy without the upstream;
    `tools/list` succeeds (static manifest); `tools/call` returns
    `messages.upstreamDownMessage()`. Start the upstream; the next
    `tools/call` succeeds. Confirms lazy init isn't permanently
    poisoned by an early failure.
  - **Drift symmetric — extra on manifest.** Manifest declares a
    tool the upstream doesn't expose. First `tools/call` returns
    `messages.driftMessage()`; subsequent calls in the same session
    return the same cached message; a re-`initialize` clears the
    cache.
  - **Drift symmetric — extra upstream.** Upstream exposes a tool
    not in the manifest. Same outcome as the previous scenario —
    deployment-version mismatch is the failure mode regardless of
    which side has more.
  - **Drift on field difference.** Same name on both sides, but
    description / schema differ. Drift detected via DR-014's
    structural equality.
  - **Session-lost mid-call.** Upstream evicts the session between
    two `tools/call`s; the second call returns
    `messages.sessionLostMessage()` and resets `state.initialized`
    so a subsequent call walks lazy-init from scratch. No silent
    re-initialize.
  - **`IOException` mid-call.** Upstream is killed mid-call; the
    proxy returns `messages.ioMidCallMessage()`.
  - **`_meta` passthrough.** A `progressToken` set in `_meta` on
    the proxy's incoming `tools/call` reaches the upstream
    handler's `ToolRequest._meta` unchanged (DR-013's three-arg
    `callTool` overload).
  - **First-call round-trip count.** Assert that the first
    `tools/call` of a fresh session triggers exactly three
    upstream HTTP requests (`initialize`, `listTools`, `callTool`)
    and subsequent calls trigger one. Cheap regression guard
    against an accidental probe-per-call.

