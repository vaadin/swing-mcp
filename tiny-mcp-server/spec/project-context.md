# Project Context

This subproject implements a generic minimalistic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built in Java.

The MCP server is minimalistic:

- No support for SSE streams for server-to-client push messages
- No support for auth of any kind
- Supports tools, resources, and prompts

### Transports: HTTP and stdio

The MCP server supports two transports, picked once per instance:

- **HTTP** — the in-process embedding case (e.g. inside a Swing app).
  Binds to `127.0.0.1` only (localhost). Default address: port `18088`,
  context path `/mcp`. No authentication — the developer owning the
  machine is responsible for local security. This is sufficient for the
  Vaadin migration scenario; if the project expands to other use cases,
  security can be revisited.
- **Stdio** — newline-delimited JSON-RPC over `System.in` /
  `System.out`. The standalone-process case: an MCP client (e.g. Claude
  Code) spawns a JVM running `runStdio()`, and there is no other code
  in the JVM writing to stdout. Single-session by definition.

The two transports cover two distinct lifecycles. In-process embedding
(HTTP) cannot use stdio because the host application owns stdout;
standalone proxies (stdio) prefer stdio because there is no port to
coordinate. See DR-007 for the full rationale.

There is no port discovery mechanism. Usually there is exactly one Swing app
running per machine (the one being migrated to Vaadin). Multiple Swing apps
on the same machine is a corner case handled by changing the port.

### Embedded MCP client

The tiny-mcp-server subproject also ships a minimal HTTP MCP client in
a sibling package, `com.vaadin.swingmcp.tinymcpclient`, with just enough
surface to run a forwarding proxy: `initialize`, `listTools`,
`callTool` (with an overload that forwards the JSON-RPC `_meta`
object end-to-end, DR-013), `close`. The `MCPClient` interface is the
public type; `TinyMCPClient` is the no-retry concrete implementation
that throws `MCPSessionLostException` on HTTP 404 — by default the
caller is told clearly when the session is gone, because
re-initializing would silently discard session-bound state (e.g.
swing-mcp's component refs). Stateless callers can opt into
transparent recovery via the `MCPClient.autoRetry()` default method,
which wraps the client in an `AutoRetryMCPClient` decorator.
Resources and prompts are not in the initial client surface — add
when a use case asks. See DR-008.

### Forwarding-proxy machinery

Forwarding an MCP server over a different transport is a first-class
capability. `MCPProxy.newHandler(toolDescriptors, upstreamUri,
proxyMessages)` (DR-012) returns a fully-wired `MCPHandler` that
answers `tools/list` from a static `ToolDescriptor` manifest and
forwards every `tools/call` to an upstream MCP server via the
embedded HTTP client. The caller wraps the returned handler in any
transport — typically `StdioMCPServer` — and runs it.

This shape exists because Claude Code (and most MCP clients) launch
their MCP servers as subprocesses, but in-process embedding cases
(e.g. `SwingMCP`) need to live inside the host application's JVM.
The proxy bridges the two: a stdio process Claude Code can spawn,
forwarding to an HTTP server hosted in the running application.

Three properties make the proxy usable in practice:

- **`tools/list` always answers from the static manifest.** Claude
  Code dispatches `tools/list` at MCP-init time and drops any MCP
  that errors. Answering locally lets the proxy stay registered
  even when the upstream isn't running yet — the LLM sees a clear
  `isError` body on the first `tools/call` instead of losing the
  whole MCP server.
- **Drift detection at first call.** When the upstream becomes
  reachable, the proxy compares its static manifest against
  upstream's `listTools()` (set-keyed by tool name, structural
  equality on each entry per DR-014). Hard-fail symmetric on any
  difference — extra on either side is a deployment-version
  mismatch, and so are field-level differences. Subsequent calls
  in the same session return the cached drift error verbatim.
- **`ProxyMessages` is template-free.** The proxy emits four
  pre-formatted strings supplied at construction time
  (upstream-down, drift, session-lost, IO-mid-call). All
  URL/remote-name interpolation happens at the call site;
  `tiny-mcp-server` never templates. Diagnostic detail goes to
  JUL WARNING on stderr.

`swing-mcp-proxy` is the first concrete consumer; the machinery
itself is generic and doesn't depend on Swing.

### Session model

`MCPHandler` itself is multi-session: it keeps a map of active
sessions and routes incoming requests to the one named by the
`Mcp-Session-Id` header (in HTTP mode; stdio is single-session by
definition). swing-mcp's `SwingMCP` constructs its handler
with the `IntPredicate count -> count == 0` to enforce a
single-session policy, because multiple concurrent AI agents
controlling the same Swing app would cause random concurrency issues
(interleaved clicks, snapshot races) and make automated testing
useless. When that predicate rejects a second `initialize`, the
client receives HTTP 409 and JSON-RPC error `-32002` (`"Another
session is already active"`); there is no queuing or retry.

If an AI agent crashes or disconnects without sending a DELETE, the
session is evicted by the idle-cleanup tick after 30 minutes of no
activity, freeing the single-session slot without restarting the
Swing app.

See DR-003, DR-005, and DR-006 for the full session lifecycle, the
per-method routing matrix, and the idle-eviction policy.

### Error handling model

Errors surface in one of three layers — transport failure (socket
dead), JSON-RPC protocol error (parse / invalid request / method not
found / invalid params / session state), and tool-layer `isError: true`
with a recovery hint. All exception-to-response translation happens in
a single seam in `HttpMCPServer.handleRequest`.

See DR-004 for the full mapping of exception types, JSON-RPC codes,
and HTTP statuses.

## 1. Vision

This subproject is an internal dependency of the swing-mcp ecosystem
and not meant to be used elsewhere. Its purpose is to have as few
dependencies as possible, to avoid transitive dependency clashes when
embedding into customer Swing Java apps.

The broader context: swing-mcp is part of a Vaadin migration workflow where
a customer migrates a Java Swing app to Vaadin. An AI agent uses MCP to
inspect and navigate the Swing app, gathering screenshots and accessibility
snapshots to inform the migration.

The protocol primitives (server, client, proxy machinery) are
generic and Swing-agnostic — they live here so any consumer in the
ecosystem can pick the pieces it needs without pulling in Swing.

## 2. Users

Internal project. Two intended consumers today: `swing-mcp` (the
in-process Swing MCP server) and `swing-mcp-proxy` (the stdio
forwarding proxy that Claude Code spawns). Both depend on this
subproject's protocol POJOs, dispatch core, and — for the proxy —
the `MCPProxy` factory and embedded `MCPClient`.

## 3. Constraints

- As few runtime dependencies as possible
- Java 17+ required
- Bind to `127.0.0.1` only — never `0.0.0.0`

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Architecture](architecture.md) — technology stack and application structure
- [Decisions](decisions.md) — cross-cutting design decisions (what/why/alternatives)
