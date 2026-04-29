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
`callTool`, `close`. The `MCPClient` interface is the public type;
`TinyMCPClient` is the no-retry concrete implementation that throws
`MCPSessionLostException` on HTTP 404 — by default the caller is told
clearly when the session is gone, because re-initializing would silently
discard session-bound state (e.g. swing-mcp's component refs).
Stateless callers can opt into transparent recovery via the
`MCPClient.autoRetry()` default method, which wraps the client in an
`AutoRetryMCPClient` decorator. Resources and prompts are not in the
initial client surface — add when a use case asks. See DR-008.

### Session model

`TinyMCPServer` itself is multi-session: it keeps a map of active
sessions and routes incoming requests to the one named by the
`Mcp-Session-Id` header. The subclass used by swing-mcp overrides
`acceptNewSession()` to enforce a single-session policy, because
multiple concurrent AI agents controlling the same Swing app would
cause random concurrency issues (interleaved clicks, snapshot races)
and make automated testing useless. When that subclass rejects a
second `initialize`, the client receives HTTP 409 and JSON-RPC error
`-32002` (`"Another session is already active"`); there is no queuing
or retry.

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
a single seam in `TinyMCPServer.handleRequest`.

See DR-004 for the full mapping of exception types, JSON-RPC codes,
and HTTP statuses.

## 1. Vision

This subproject is an internal dependency of swing-mcp and not meant
to be used elsewhere. Its purpose is to have as few dependencies as possible,
to avoid transitive dependency clashes when embedding into customer
Swing Java apps.

The broader context: swing-mcp is part of a Vaadin migration workflow where
a customer migrates a Java Swing app to Vaadin. An AI agent uses MCP to
inspect and navigate the Swing app, gathering screenshots and accessibility
snapshots to inform the migration.

## 2. Users

Internal project: no human users, only the swing-mcp subproject is
the intended user.

## 3. Constraints

- As few runtime dependencies as possible
- Java 17+ required
- Bind to `127.0.0.1` only — never `0.0.0.0`

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Architecture](architecture.md) — technology stack and application structure
- [Decisions](decisions.md) — cross-cutting design decisions (what/why/alternatives)
