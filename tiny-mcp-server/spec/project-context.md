# Project Context

This subproject implements a generic minimalistic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built in Java.

The MCP server is minimalistic:

- No support for SSE streams for server-to-client push messages
- No support for auth of any kind
- Supports tools, resources, and prompts

### Transport: HTTP only

The MCP server doesn't support STDIO communication, it only supports HTTP.
**Rationale:** Since this MCP server runs in-process within a Swing app,
the Swing app itself uses STDIO for its own purposes. STDIO-based MCP
communication would be polluted by the Swing app's own output. HTTP is
the cleaner approach.

The server binds to `127.0.0.1` only (localhost). No authentication is needed —
the developer owning the machine is responsible for local security. This is
sufficient for the Vaadin migration scenario; if the project expands to other
use cases, security can be revisited.

Default listen address:

- port: `18088`
- context path: `/mcp`

There is no port discovery mechanism. Usually there is exactly one Swing app
running per machine (the one being migrated to Vaadin). Multiple Swing apps
on the same machine is a corner case handled by changing the port.

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
session remains open indefinitely. For the single-session subclass,
that means the Swing app must be restarted to clear a stuck session —
acceptable for the migration scenario (<1% of cases); revisit with a
timeout or manual release if it becomes frequent.

See DR-003 and DR-005 for the full session lifecycle and the
per-method routing matrix.

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
