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

**Decision.** The server supports only HTTP (POST + DELETE, no SSE) bound
to `127.0.0.1`. No STDIO transport. No authentication. Default listen
address is `127.0.0.1:18088/mcp`.

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

## DR-003 — Single-session policy as an opt-in subclass hook

**Status:** Accepted
**Applies to:** TinyMCPServer session handling, swing-mcp swing-mcp `MCPServer`

**Decision.** `TinyMCPServer` itself is multi-session capable: it keeps a
`ConcurrentHashMap<String, MCPSession>` and routes requests by the
`Mcp-Session-Id` header. Before creating a new session, `handleInitialize`
calls the protected hook `acceptNewSession()` (default: always returns
`true`) under the `sessionGuardLock`. Subclasses that want a single-
session policy override the hook to cap at one active session; when it
returns `false`, `initialize` fails with HTTP 409 and JSON-RPC code
`-32002` (`"Another session is already active"`). The blocked client
simply fails — no queuing, no retry.

swing-mcp's `swing-mcp `MCPServer`` applies this override because multiple
concurrent AI agents controlling the same Swing app would cause random
concurrency issues (interleaved clicks, snapshot races) on the
single-threaded EDT. Pure `TinyMCPServer` instances (e.g. its own unit
tests) happily run multiple sessions in parallel.

If an AI agent crashes without sending a DELETE, the session is
evicted by the idle-cleanup tick (DR-006) after 30 minutes of no
activity. For the single-session subclass, that frees the slot
automatically without restarting the Swing app.

**Why this split.** `TinyMCPServer` aims to be a reusable minimal MCP
server, and multi-session is the MCP spec default. Baking
single-session into the base class would either leak Swing-specific
concurrency assumptions into tiny-mcp-server or force every reuser to
work around them. A protected hook on `TinyMCPServer` keeps the base
class general while letting swing-mcp express its own constraint in
one line.

**Alternatives considered.**
- **Bake single-session into `TinyMCPServer`.** Rejected — forces
  Swing-specific concurrency semantics onto a general-purpose server;
  tiny-mcp-server's own multi-session tests would have to work around
  it.
- **Session queuing (block until current session ends).** Rejected —
  adds complexity for a scenario that doesn't arise in normal use. The
  blocked agent would hang indefinitely if the first session is stuck.
- **Idle timeout to auto-release stuck sessions.** Accepted — see
  DR-006. Sessions idle for 30 minutes are evicted by a background
  cleanup tick, so a crashed client no longer blocks the single-session
  slot until the Swing app is restarted.

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

**This layer exists for tools only — enforced by the MCP spec, not by
us.** Per the MCP spec (2025-03-26), `isError` is a field on
`CallToolResult` and only on `CallToolResult`; `ReadResourceResult` and
`GetPromptResult` have no equivalent slot. The rationale is that a tool
can meaningfully "partially fail" — it ran, produced a result, and that
result is a descriptive error the LLM should read and reason about.
Resources and prompts are one-shot content producers: you either get
`contents` / `messages` back, or you don't. So when a resource or prompt
handler fails, the only structured channel back to the client is a
JSON-RPC protocol error (layer 2). `MCPResourceHandler` /
`MCPPromptHandler` wrap non-`MCPServerException` failures from the
handler as `INTERNAL_ERROR`, and `MCPErrorResponseException` from
argument parsing as `INVALID_PARAMS` — see the table above for the
full mapping.

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

**Decision.** Every POST is validated against the session map
(`sessions`, keyed by session id) before dispatch. The validation
splits into two stages so the fast path stays cheap while the slow
path can still return a well-formed JSON-RPC envelope:

1. **Pre-parse:** read the `Mcp-Session-Id` header. If it is present
   and does not name a known session in the map, return **HTTP 404**
   immediately. No body parsing needed — every method is rejected.
2. **Post-parse:** after the JSON-RPC body is parsed, if the method is
   neither `initialize` nor `ping` and the header was absent, return
   **HTTP 400** using the parsed request `id` in the JSON-RPC error.

Both rejections use JSON-RPC error code **-32002** (implementation-defined
server error, -32000..-32099 range), exposed as
`MCPServerException.SERVER_NOT_INITIALIZED`. Server-level methods
(`initialize`, `ping`) are handled by `TinyMCPServer` directly; every
other method is routed to `MCPSession.handlePost` for the matched
session. `initialize` ignores the incoming `Mcp-Session-Id` header
entirely — it always tries to create a new session, subject to
`acceptNewSession()` (DR-003). Full outcome matrix:

| `Mcp-Session-Id` header | Method | Result |
|---|---|---|
| absent | `initialize` | Create new session, return 200 + `Mcp-Session-Id` — or HTTP 409 if `acceptNewSession()` returns false (DR-003) |
| absent | `ping` | 200 `{}` |
| absent | anything else | HTTP 400 + JSON-RPC error |
| present, matches a session | `initialize` | Same as absent + `initialize` — a fresh session is created; the incoming id is ignored |
| present, matches a session | `ping` | 200 `{}` |
| present, matches a session | anything else | Route to `MCPSession.handlePost` for that session |
| present, no match | any | HTTP 404 + JSON-RPC error |

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

---

## DR-006 — Idle session eviction via a shared scheduled executor

**Status:** Accepted
**Applies to:** `TinyMCPServer`, `MCPSession`
**Supersedes the "Idle timeout" deferral in:** DR-003

**Decision.** `TinyMCPServer` owns a single `ScheduledExecutorService`
(one daemon thread, named `tiny-mcp-server-N`) created in `start()` and
shut down in `stop()`. Once per minute it runs a cleanup tick that
evicts every session whose last access is older than 30 minutes. The
same executor is exposed via `getExecutor()` for tool handlers that need
background work (debouncing, deferred cleanup, short periodic polling)
— reachable from tool code via `MCPSession.getCurrent().getServer()`.
Heavy or long-blocking work must use its own executor so the cleanup
tick cannot be starved.

The cleanup tick and request dispatch synchronise through a single
chokepoint in `MCPSession.runLocked`:

1. `runLocked` acquires the session lock, checks a `closed` flag and
   throws an HTTP 404 `MCPServerException` if the session was already
   evicted, then refreshes `lastAccessNanos = System.nanoTime()` before
   running the request block. Every dispatched POST flows through
   `runLocked`, so the timestamp is authoritative.
2. The cleanup tick iterates the session map, skips sessions whose
   `lastAccessNanos` is within the idle window, and for the rest calls
   `tryClose()`. `tryClose()` uses `ReentrantLock.tryLock()` — if a
   request is in flight, it returns `false` and the session is
   re-evaluated on the next tick, so cleanup never blocks on a live
   request. On success it sets `closed = true` under the lock, so any
   request that acquires the lock afterwards fails fast with 404.
   Removed sessions are reported through `onSessionClosed`, the same
   hook invoked by explicit DELETE.

Constants `IDLE_TIMEOUT_NANOS` (30 minutes) and `CLEANUP_TICK_SECONDS`
(60) are package-private so tests can substitute short values via
reflection and drive the tick synchronously through the
package-private `cleanupIdleSessions()` entry point.

**Why.**

- **Stuck single-session slot.** DR-003 left the single-session
  subclass wedged if an AI client crashed without sending DELETE; the
  only remedy was restarting the Swing app. 30 minutes matches a
  realistic "agent walked away" window without fighting legitimate
  long pauses during a migration session.
- **One executor, many uses.** Tool handlers already wanted a place to
  park background work; running a second executor purely for cleanup
  would double the daemon-thread count and still leave tools with
  nowhere to schedule. Sharing the executor keeps the dependency
  surface minimal (a single `ScheduledExecutorService` field, no new
  runtime libraries per DR-002) and gives tools a predictable,
  already-managed lifecycle tied to `start()` / `stop()`.
- **`runLocked` as the chokepoint.** Refreshing the timestamp at every
  lock acquisition — rather than in `handlePost`'s entry path — means
  test-only callers that run through `runLocked` also count as
  activity, and there is a single place where "this session is alive"
  is recorded. Pairing it with the `closed`-flag check keeps the
  post-eviction 404 consistent with DR-005's "wrong session → 404"
  rule without duplicating map lookups.
- **`tryLock` over `lock`.** A blocking `lock()` in the cleanup thread
  would stall the whole cleanup pass behind any slow request, and
  could in the worst case deadlock with a tool that schedules work on
  the same executor and waits for it. `tryLock()` + retry-next-tick
  keeps cleanup bounded and lock-free from the request path's
  perspective.

**Alternatives considered.**
- **No idle eviction (DR-003 status quo).** Rejected — a crashed
  single-session client requires a full Swing app restart.
- **Separate executor purely for cleanup.** Rejected — doubles the
  background thread count without giving tools anywhere to schedule
  work; the scheduled-executor API is the natural place to host both.
- **Track last-access in `handlePost` only.** Rejected — test
  scenarios and future callers that drive `runLocked` directly would
  silently age out; the chokepoint-based approach keeps the invariant
  in one place.
- **Blocking `lock()` in `tryClose`.** Rejected — would let a single
  slow handler freeze the cleanup pass and open a deadlock window if
  the handler were waiting on the shared executor.
- **Evict from the HTTP thread on each request.** Rejected — sprays
  cleanup cost across every request and does nothing for a server
  that has simply gone idle, which is the exact case we care about.
