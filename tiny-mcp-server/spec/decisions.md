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

## DR-004 — Two-layer error handling model

**Status:** Accepted
**Applies to:** TinyMCPServer, all tool implementations

**Decision.** Errors are reported at two layers:

1. **Transport-level (HTTP 4xx/5xx):** For programming errors,
   `RuntimeException`, or when the server cannot process the request at all
   (e.g., malformed JSON-RPC, unknown method).
2. **MCP-level (`isError: true`):** For application-level errors where the
   tool was found and dispatched but the input was bad (e.g., invalid
   component ref, disabled component). These include a helpful recovery
   message following the Playwright MCP pattern (e.g., "Ref not found,
   likely because element was removed. Use swing_snapshot to see what
   elements are currently available.").

JSON-RPC error codes used: `-32601` (tool not found), `-32602` (invalid
params).

**Why.** Separating the layers lets the AI client distinguish "something is
fundamentally broken" (HTTP error — retry won't help) from "my input was
wrong" (MCP error — adjust and retry). The Playwright MCP recovery-message
pattern gives the AI a concrete next step instead of a bare error string.

**Alternatives considered.**
- **All errors as MCP-level `isError: true`.** Rejected — conflates
  infrastructure failures with input validation, making it harder for the
  client to decide whether to retry.
- **All errors as HTTP status codes.** Rejected — MCP clients expect
  `isError` for tool-level failures; HTTP-only errors would break client
  SDK error handling.

---

## DR-005 — Session lifecycle gate

**Status:** Accepted
**Applies to:** `TinyMCPServer.handlePost`, UC-005

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
