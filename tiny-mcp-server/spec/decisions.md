# Decisions

Cross-cutting design decisions for `tiny-mcp-server`. Each record captures
**what** was chosen, **why**, and the **alternatives** that were considered
and rejected.

> **When to read this file:** when a use case or implementation note references
> `DR-NNN`, or when you're revisiting a choice that spans multiple use cases.
>
> **When to update:** whenever a cross-cutting choice is made, revised, or
> rejected. Keep entries short — the full narrative belongs in the session that
> produced the decision; this file is the durable summary. Status lifecycle:
> **Proposed** → **Accepted** → **Superseded** (link forward to the replacement).

---

## DR-001 — HTTP-only transport, no STDIO

**Status:** Accepted
**Applies to:** TinyMCPServer, all use cases

**Decision.** The server supports only HTTP Streamable transport bound to
`127.0.0.1`. No STDIO transport. No authentication. Default listen address
is `127.0.0.1:18088/mcp`.

**Why.** This MCP server runs in-process within a Swing app. The Swing app
itself uses STDIO for its own purposes — STDIO-based MCP communication
would be polluted by the app's own output. Localhost binding with no auth
is sufficient for the Vaadin migration scenario: the developer owning the
machine is responsible for local security.

**Alternatives considered.**
- **STDIO transport.** Rejected — Swing apps own STDIO; MCP messages
  would be interleaved with application output.
- **Bind to `0.0.0.0` with auth.** Rejected — unnecessary attack surface
  for a single-machine migration tool. Can be revisited if the project
  expands to remote use cases.

---

## DR-002 — GSON only, no official MCP SDK at runtime

**Status:** Accepted
**Applies to:** all use cases, MCPProtocol, TinyMCPServer

**Decision.** The server uses only Google GSON for JSON serialization and
parsing. The official `io.modelcontextprotocol/java-sdk` is used for
**testing only** (as an MCP client), never as a runtime dependency.
JSONs are always produced via POJO class mapping — never via String
concatenation or the GSON element API.

**Why.** This server is embedded as a JAR into customer Swing apps that
have their own dependency trees. Every runtime dependency risks a
transitive version clash. The official MCP Java SDK pulls in Jackson and
requires a servlet container — both are large dependency subtrees.

**Alternatives considered.**
- **Official `io.modelcontextprotocol/java-sdk`.** Rejected — brings
  Jackson and a servlet container as transitive runtime dependencies.
- **Quarkus MCP server.** Rejected — requires Quarkus bootstrap; Swing
  app startup is fixed and cannot be changed.
- **Spring AI MCP.** Rejected — massive dependency footprint.
- **Jackson (standalone).** Rejected — large dependency; GSON is
  sufficient and lighter.

---

## DR-003 — Single-session model, no queuing

**Status:** Accepted
**Applies to:** TinyMCPServer session handling

**Decision.** The server supports at most one active MCP session at a time.
A second initialization request while a session is active is denied with
HTTP 409 Conflict. The blocked client simply fails — no queuing, no retry.

If an AI agent crashes without closing the session, the session remains
locked indefinitely; the Swing app must be restarted to clear it. The
server logs a warning when a connection attempt is blocked by a stuck
session.

**Why.** Multiple concurrent AI agents controlling the same Swing app would
cause random concurrency issues (interleaved clicks, snapshot races),
making automated testing useless. Stuck sessions are rare (<1% of cases)
in the migration scenario; a timeout or manual release can be added if
this becomes problematic.

**Alternatives considered.**
- **Multi-session / concurrent agents.** Rejected — Swing is
  single-threaded (EDT); concurrent tool calls from multiple agents would
  interleave unpredictably.
- **Session queuing (block until current session ends).** Rejected — adds
  complexity for a scenario that doesn't arise in normal use. The blocked
  agent would hang indefinitely if the first session is stuck.
- **Idle timeout to auto-release stuck sessions.** Deferred — acceptable
  tradeoff for now. Revisit if stuck sessions become frequent.

---

## DR-004 — Three-layer error handling model

**Status:** Accepted
**Applies to:** TinyMCPServer, MCPSession, all tool / resource / prompt implementations

**Decision.** Errors surface in one of three layers, chosen by what kind of
failure it is and which handler caught it. All exception-to-response
translation happens in a single seam — `TinyMCPServer.handleRequest` —
which catches `MCPServerException`, `TransportIOException`, and
`RuntimeException` and renders each appropriately.

### Layer 1 — Transport failure (`TransportIOException`)

Raised by `JsonRpcExchange` when a socket write (`sendResponse`,
`sendError`, `sendPlain`) throws `IOException`. The TCP connection is
dead; there is nothing left to write. `handleRequest` logs at WARNING and
abandons — no response is attempted.

### Layer 2 — Protocol error (`MCPServerException` → JSON-RPC error body)

Any `MCPServerException` thrown anywhere in the request path is rendered
as a JSON-RPC error envelope. The exception carries both a JSON-RPC
numeric **code** and an HTTP **status** (default `200`, overridden at the
throw site for transport- or session-level failures). Callers throw,
never inline-write: the single catch in `handleRequest` translates.

Typical codes and their HTTP status:

| Code (constant) | Value | HTTP | Thrown by |
|---|---|---|---|
| `PARSE_ERROR` | -32700 | 400 | `JsonRpcExchange.parsePost` — malformed JSON |
| `INVALID_REQUEST` | -32600 | 400 | `JsonRpcExchange.parsePost` — wrong JSON-RPC shape / batch |
| `METHOD_NOT_FOUND` | -32601 | 200 | `MCPSession` dispatch, handler lookups |
| `INVALID_PARAMS` | -32602 | 200 | tool/resource/prompt input validation |
| `INTERNAL_ERROR` | -32603 | 200 | resource/prompt handler wrapped a generic `Exception` |
| `SERVER_NOT_INITIALIZED` | -32002 | 400 / 404 / 409 | session lifecycle (DR-005), second-session rejection (DR-003) |
| `INTERNAL_ERROR` | -32603 | **500** | catch-all for unexpected `RuntimeException` leaked from server internals (see below) |

### Layer 3 — Tool application error (`isError: true` content, HTTP 200)

When a tool callback throws a non-`MCPServerException` (typically
`MCPErrorResponseException` for clean messages or any other `Exception`
for unexpected tool failures), `MCPToolHandler` catches it and produces a
successful JSON-RPC response whose `CallToolResult.isError` is `true` and
whose text content is either the clean message (for
`MCPErrorResponseException`) or `Throwable.toString()` (class + message).

This layer exists for tools only. Resource and prompt handlers catch
generic exceptions and rewrap them as `MCPServerException(INTERNAL_ERROR)`
— they do not have an `isError` content slot.

Tools use this layer for application-level failures where the tool was
found and dispatched but the input was semantically wrong (e.g., invalid
component ref, disabled component). Following Playwright MCP's pattern,
the message should include a concrete recovery hint (e.g., *"Ref not
found, likely because the element was removed. Use swing_snapshot to see
what elements are currently available."*).

### Catch-all for unexpected `RuntimeException`

Any `RuntimeException` that is **not** `MCPServerException` or
`TransportIOException` and reaches `handleRequest` is a server bug — a
code path threw something unplanned. It is logged at SEVERE and rendered
as **HTTP 500** with JSON-RPC code `INTERNAL_ERROR` and the message
`"Internal error"`. HTTP 500 (not 200) so operators and monitoring
tooling see a genuine server-side failure rather than a protocol-level
error.

Note that handler-layer exceptions from tools, resources, and prompts
never reach this catch-all — each handler wraps them first (layer 2 or 3
as appropriate).

**Why.** The three layers map cleanly to three different client reactions:

- **Transport failure** — client has already disconnected; nothing to tell
  it.
- **Protocol error** — client SDK can parse the JSON-RPC envelope and
  surface a structured error to the caller.
- **Tool application error** — the tool ran to completion, just with a
  negative result; the AI agent can show the recovery hint to the user
  and try a different input.

Centralizing the translation in `handleRequest` means every caller speaks
one idiom (`throw`), and there is exactly one place to audit for how an
exception becomes bytes on the wire.

**Alternatives considered.**
- **All errors as tool-layer `isError: true`.** Rejected — conflates
  infrastructure failures (malformed JSON, dead session) with input
  validation; the client SDK can't distinguish "retry won't help" from
  "fix your input and retry."
- **All errors as HTTP status codes, no JSON-RPC error body.** Rejected —
  MCP clients parse the JSON-RPC envelope for error details; HTTP-only
  errors would break client SDK error handling. JSON-RPC errors
  intentionally ride HTTP 200 in the normal case.
- **Inline `rpc.sendError(...)` at each error site instead of throwing.**
  Rejected — splits the error-to-response mapping across every handler,
  requires each caller to remember the HTTP status for its error class,
  and a transport failure at the error-send site escapes to a second
  catch that repeats the attempt. One seam, one rule.
- **Separate exception types per layer.** Considered. `MCPServerException`
  already covers all protocol errors with a code field; splitting it
  further would force handler code to pick between types without adding
  useful discrimination downstream.

---

## DR-005 — Session lifecycle gate

**Status:** Accepted
**Applies to:** `TinyMCPServer.handlePost`

**Decision.** Every POST is validated against the server's
`activeSessionId` before dispatch. The validation splits into two stages
so the fast path stays cheap while the slow path can still return a
well-formed JSON-RPC envelope:

1. **Pre-parse:** read the `Mcp-Session-Id` header. If it is present and
   does not match `activeSessionId` (whether `activeSessionId` is null or
   non-null), return **HTTP 404** immediately. No body parsing needed —
   every method is rejected.
2. **Post-parse:** after the JSON-RPC body is parsed, if the method is
   neither `initialize` nor `ping` and `activeSessionId` is null, return
   **HTTP 400** using the parsed request `id` in the JSON-RPC error.

Both rejections use JSON-RPC error code **-32002** (implementation-defined
server error, -32000..-32099 range), exposed as
`MCPServerException.SERVER_NOT_INITIALIZED`. Full outcome matrix:

| Incoming `Mcp-Session-Id` | `activeSessionId` | Method | Result |
|---|---|---|---|
| absent | null | `initialize` | OK — create session |
| absent | null | `ping` | OK |
| absent | null | anything else | HTTP 400 + JSON-RPC error |
| absent | non-null | `initialize` | HTTP 409 + JSON-RPC error (DR-003) |
| absent | non-null | `ping` | OK |
| absent | non-null | anything else | HTTP 400 + JSON-RPC error |
| matches `activeSessionId` | non-null | any | OK — normal dispatch |
| doesn't match | non-null | any | HTTP 404 + JSON-RPC error |
| doesn't match | null | any | HTTP 404 + JSON-RPC error |

**Why.** The MCP spec (2025-03-26 §Lifecycle, §Session Management)
requires HTTP 404 for stale/wrong session IDs so the client knows to
re-initialize (*"When a client receives HTTP 404 … it MUST start a new
session"*), HTTP 400 for missing session IDs on non-init methods
(*"Servers that require a session ID SHOULD respond to requests without
an Mcp-Session-Id header (other than initialization) with HTTP 400"*),
and carves out `ping` as acceptable pre-init. Splitting the check around
JSON-body parsing keeps wrong-session rejection free of parse cost while
still producing a proper JSON-RPC error (with the client's request `id`)
for the not-initialized case, which needs the parsed `method` to
distinguish `initialize`/`ping` from regular tool calls.

**Alternatives considered.**
- **Single pre-parse check.** Rejected — the not-initialized case needs
  the parsed method (to allow `initialize`/`ping`) and the request `id`
  to populate the JSON-RPC error envelope.
- **Single post-parse check.** Rejected — wastes body parsing for
  requests rejected on header alone (wrong session ID).
- **Use -32600 (invalid request) or -32602 (invalid params).** Rejected —
  those codes describe malformed input; session-state violations are a
  server-state condition, which is exactly what the -32000..-32099
  implementation-defined range is for.
