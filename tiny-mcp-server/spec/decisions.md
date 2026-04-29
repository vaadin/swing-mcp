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

## DR-001 — HTTP transport, localhost binding, no auth

**Status:** Accepted (stdio scope amended by DR-007)
**Applies to:** HttpMCPServer, all use cases

**Decision.** The HTTP transport supports POST + DELETE (no SSE) bound to
`127.0.0.1`. No authentication. Default listen address is
`127.0.0.1:18088/mcp`.

**Why.** Localhost binding with no auth is sufficient for the Vaadin
migration scenario: the developer owning the machine is responsible for
local security.

**Alternatives considered.**
- **Bind to `0.0.0.0` with auth.** Rejected — unnecessary attack surface
  for a single-machine migration tool. Can be revisited if the project
  expands to remote use cases.

**History.** Originally also rejected stdio outright, on the grounds that
the in-process Swing app owns stdout. DR-007 reinstates stdio for a
distinct use case — a standalone process spawned by an MCP client where
no other code writes to stdout. The localhost-HTTP-no-auth decision above
is unchanged.

---

## DR-002 — GSON only, no official MCP SDK at runtime

**Status:** Accepted
**Applies to:** all use cases, MCPProtocol, HttpMCPServer

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

## DR-003 — Single-session policy as a constructor-injected predicate

**Status:** Accepted (mechanism revised by DR-011)
**Applies to:** MCPHandler session handling, swing-mcp `SwingMCP`

**Decision.** `MCPHandler` itself is multi-session capable: it keeps a
`ConcurrentHashMap<String, MCPSession>` and routes requests by the
`Mcp-Session-Id` header. Before creating a new session,
`dispatchInitialize` calls the constructor-injected `IntPredicate
acceptNewSession` (default: always-accept) under the `sessionGuardLock`.
Callers that want a single-session policy supply
`count -> count == 0` to cap at one active session; when it returns
`false`, `initialize` fails with HTTP 409 and JSON-RPC code `-32002`
(`"Another session is already active"`). The blocked client simply
fails — no queuing, no retry.

swing-mcp's `SwingMCP` applies this predicate because multiple
concurrent AI agents controlling the same Swing app would cause random
concurrency issues (interleaved clicks, snapshot races) on the
single-threaded EDT. Pure `MCPHandler` instances (e.g. its own unit
tests) happily run multiple sessions in parallel.

If an AI agent crashes without sending a DELETE, the session is
evicted by the idle-cleanup tick (DR-006) after 30 minutes of no
activity. For the single-session caller, that frees the slot
automatically without restarting the Swing app.

**Why this split.** `MCPHandler` aims to be a reusable minimal MCP
core, and multi-session is the MCP spec default. Baking
single-session into the base class would either leak Swing-specific
concurrency assumptions into tiny-mcp-server or force every reuser to
work around them. A pluggable predicate keeps the base class general
while letting `SwingMCP` express its own constraint in one line.

**History.** Originally implemented as a `protected boolean
acceptNewSession()` subclass hook on `TinyMCPServer`. DR-011 replaced
the subclass hook with a constructor-injected `IntPredicate` on
`MCPHandler`, so callers compose the policy in rather than extending
the transport class.

**Alternatives considered.**
- **Bake single-session into `MCPHandler`.** Rejected — forces
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
**Applies to:** HttpMCPServer, MCPSession, all tool / resource / prompt implementations

**Decision.** Errors surface in one of three layers, chosen by what kind of
failure it is and which handler caught it. All exception-to-response
translation happens in a single seam — `HttpMCPServer.handleRequest` —
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
**Applies to:** `HttpMCPServer.handlePost`

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
(`initialize`, `ping`) are handled by `HttpMCPServer` directly; every
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

**Status:** Accepted (executor ownership moved to `MCPHandler` by DR-010)
**Applies to:** `MCPHandler`, `HttpMCPServer`, `MCPSession`
**Supersedes the "Idle timeout" deferral in:** DR-003

**Decision.** `MCPHandler` owns a single `ScheduledExecutorService`
(one daemon thread, named `tiny-mcp-server-N`) created in its
`start()` and shut down in `stop()`. `HttpMCPServer.start()` /
`stop()` (and `StdioMCPServer.runStdio()` start/finish) delegate the
lifecycle calls. Once per minute the executor runs a cleanup tick
that evicts every session whose last access is older than 30 minutes.
The executor is exposed via `MCPHandler.getExecutor()` for tool
handlers that need background work (debouncing, deferred cleanup,
short periodic polling) — reachable from tool code via
`MCPSession.getCurrent().getHandler().getExecutor()`. Heavy or
long-blocking work must use its own executor so the cleanup tick
cannot be starved.

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

---

## DR-007 — Stdio transport as a second transport mode

**Status:** Accepted
**Applies to:** HttpMCPServer
**Amends:** DR-001 (the "no STDIO" half)

**Decision.** `HttpMCPServer` gains a second transport mode: stdio
(newline-delimited JSON-RPC over `System.in` / `System.out`). HTTP and
stdio are mutually exclusive on a given instance — a server is started
in one mode and stays there for its lifetime. API shape:

- `start()` — unchanged. Binds the HTTP listener and returns immediately;
  request handling runs on HTTP-server threads.
- `runStdio(InputStream in, OutputStream out)` — blocks the calling
  thread, reads newline-delimited JSON-RPC messages from `in`, writes
  responses to `out`, and returns when `in` reaches EOF. Tests pass piped
  streams; production callers pass `System.in` / `System.out`.

Stdio mode is **single-session by definition**. Per the MCP spec, stdio
transport carries no `Mcp-Session-Id` header — there is exactly one
implicit session for the lifetime of the process. Internally,
`runStdio` creates one `MCPSession` up front (after `initialize` is
received from the client) and routes every subsequent message through
it. `acceptNewSession()` is consulted exactly once. The DR-005 routing
matrix collapses: there is no "wrong session id → 404" branch, only
"`initialize` first, then any other method".

**Stdout discipline.** The stdio wire protocol owns `stdout` exclusively;
any `System.out.println` from a tool, library, or accidental debug print
corrupts the framing. `runStdio` defends against this at entry by
re-pointing `System.out` to `System.err` (`System.setOut(System.err)`)
before the read loop starts, and capturing the original stream into a
private writer used only by the protocol layer. JUL's default
`ConsoleHandler` already targets `stderr`, so logging is unaffected. A
unit test asserts that no protocol code path writes to the captured
`System.out` reference, only to the private writer.

**Framing.** Newline-delimited JSON-RPC, UTF-8. One message per line.
No `Content-Length` header (that's the LSP framing variant, not the
MCP stdio framing). Lines longer than the JVM's default buffer are
handled — the reader is `BufferedReader` with the standard size and
relies on `readLine` rather than a fixed-buffer read.

**Lifecycle.** `runStdio` returns when `in` reaches EOF. The shared
`ScheduledExecutorService` (DR-006) is started and stopped around the
read loop the same way `start()` / `stop()` do for HTTP. Idle eviction
is effectively unused (one session, never idle until the process exits)
but the machinery still runs — keeping one code path simplifies things.

**Why.** A new use case has emerged: a **proxy** MCP server that Claude
Code spawns at startup, which forwards `tools/call` to a separately
running in-process Swing MCP. The proxy is a standalone process — there
is no Swing app sharing the JVM, so the original objection (stdout
collision with the Swing app's own output) does not apply. Stdio is the
natural transport for an MCP server that an MCP client launches as a
subprocess, because there is no port to coordinate.

**Alternatives considered.**
- **Add a separate `TinyMCPStdioServer` class.** Rejected — the session
  machinery, registries, and dispatch loop are identical; only the
  framing differs. A second class would either duplicate code or extract
  a shared base, both more churn than a second start method.
- **Keep stdio out of tiny-mcp-server, build it in the proxy module.**
  Rejected — the proxy module would need to re-implement JSON-RPC
  framing, session routing, and the registries. tiny-mcp-server is the
  natural home; gson is already on the classpath.
- **LSP-style `Content-Length` framing.** Rejected — the MCP spec
  specifies newline-delimited JSON-RPC for stdio transport; conforming
  to the spec is non-negotiable.
- **Auto-restore `System.out` on shutdown.** Considered. Rejected for
  now: stdio-mode processes are short-lived (lifetime of an MCP client
  session) and exit when stdin closes. If a host ever needs to share a
  JVM between stdio mode and other code, this can be revisited.

---

## DR-008 — Embedded MCP client (HTTP, minimal surface, opt-in retry)

**Status:** Accepted
**Applies to:** new `com.vaadin.swingmcp.tinymcpclient` package in the
tiny-mcp-server subproject

**Decision.** The tiny-mcp-server subproject gains a small client API
that speaks the MCP HTTP transport from the caller side. Code lives in
a new package `com.vaadin.swingmcp.tinymcpclient` (sibling to
`tinymcpserver`) within the same Gradle subproject — same dependency
tree (gson + JDK only), same JAR, same `MCPProtocol` POJOs.

> Note on naming: the subproject is called `tiny-mcp-server` but now
> hosts both server and client code. The client lives here rather than
> in a new module because it shares all of the server's protocol
> machinery and adds zero new runtime dependencies. Renaming the
> subproject is out of scope.

The API is an interface with a no-retry concrete implementation and an
opt-in retry decorator:

```java
public interface MCPClient extends Closeable {
    InitializeResult initialize() throws IOException;
    List<Tool> listTools() throws IOException;
    CallToolResult callTool(String name, Map<String, Object> arguments) throws IOException;
    @Override void close() throws IOException;

    /** Wraps {@code this} in a one-shot session-loss retry decorator. */
    default MCPClient autoRetry() {
        return new AutoRetryMCPClient(this);
    }
}

public final class TinyMCPClient implements MCPClient {
    public TinyMCPClient(URI serverUrl) { ... }
    // no retry: throws MCPSessionLostException on HTTP 404
}

public final class AutoRetryMCPClient implements MCPClient {
    public AutoRetryMCPClient(MCPClient inner) { ... }
    // catches MCPSessionLostException, calls inner.initialize(), replays once
}
```

`initialize()` performs the JSON-RPC handshake, stores the returned
`Mcp-Session-Id`, and sends the `notifications/initialized` follow-up.
Idempotent: calling again starts a fresh session against the same URL
(used by the retry decorator's recovery path). `listTools` and
`callTool` map to `tools/list` and `tools/call`. `close()` sends
`DELETE` to release the session; the client is unusable afterwards.

Resources and prompts are **not** in the initial client surface. They
can be added when a use case asks for them — none does today.

### No retry by default — session state would be silently lost

When `TinyMCPClient` (no-retry) receives HTTP 404 on a non-`initialize`
call, it throws a typed `MCPSessionLostException` (extends
`MCPClientException`). The caller decides what to do.

Auto-retry is **not** the default because MCP sessions can hold state
that does not survive re-initialization. Examples:

- **swing-mcp's own ref map.** Component refs from `swing_snapshot` are
  scoped to the session that produced them; a new session has no
  knowledge of those refs. Silently re-initializing would mean a
  follow-up `swing_click` lands on whichever component happens to have
  that integer key in the new snapshot — or no component at all.
- **Generic stateful tools.** Any tool that records context across
  calls (cursors, in-progress builders, transaction handles) loses it
  on re-init. The next call would succeed but operate on different
  state from what the caller believes it is operating on.

For these cases, **failure is information** — silent retry replaces a
clear "session lost, re-orient" signal with subtly wrong results. The
proxy use case explicitly **does not** use `autoRetry()`: when the
upstream Swing app restarts mid-session, the proxy catches
`MCPSessionLostException` and surfaces it as a tool-layer error
(`isError: true`) with a recovery hint — *"Swing app session was lost.
Call `swing_snapshot` to re-orient and retry."* — so the LLM can
recover deliberately.

### `autoRetry()` for stateless callers

For callers proxying purely-functional tools (no session-bound state),
re-initialization is harmless and the convenience is worth it. They
opt in via the interface default method:

```java
MCPClient client = new TinyMCPClient(url).autoRetry();
```

`AutoRetryMCPClient` catches `MCPSessionLostException` from the inner
client, calls `inner.initialize()` directly (not `this.initialize()`,
so wrapping order with future decorators stays predictable), and
replays the call exactly once. A second `MCPSessionLostException` in
the replay surfaces to the caller. The decorator does not retry
`IOException` (transport failures) — that is the caller's call.

Implementing `autoRetry()` as a default method on `MCPClient` (rather
than a static factory on `TinyMCPClient`) means any future
`MCPClient` implementation gets retry-wrapping for free, and chaining
with future decorators reads left-to-right:
`new TinyMCPClient(url).withLogging().autoRetry()`.

### Transport

HTTP only, via `java.net.http.HttpClient` (JDK built-in, zero new
runtime deps per DR-002). No stdio client in this round — the proxy
use case forwards from stdio (server side) to HTTP (client side), so
a stdio client would only be useful for testing the stdio server. The
loopback test instead uses the HTTP client wrapped by a stdio server,
which exercises the same code paths in a more realistic shape.

### Errors

Three exception classes, all in the new package:

- `MCPClientException` — JSON-RPC protocol error returned by the
  server. Carries the JSON-RPC code and message. Also used for HTTP
  4xx/5xx responses other than 404 (rendered as synthetic JSON-RPC
  errors to keep one exception path).
- `MCPSessionLostException extends MCPClientException` — HTTP 404 from
  a non-`initialize` call. The 404→typed-exception mapping happens
  inside `TinyMCPClient` (the only place that knows the protocol);
  decorators do not re-derive it from status codes.
- `IOException` — transport failure (connection refused, timeout,
  broken pipe). The caller decides whether to retry; the client itself
  does not retry transport errors.

`CallToolResult.isError = true` is **not** an exception — it is
returned to the caller as a normal result, mirroring the server's
three-layer model from DR-004. The proxy then wraps it back into a
tool-layer error on its own server side, preserving the message
verbatim.

### Why

The proxy needs a forwarding client. Building it inside
tiny-mcp-server keeps the dependency tree single-rooted and lets us
write a self-contained loopback test (stdio `HttpMCPServer` wrapping
HTTP `HttpMCPServer` through `TinyMCPClient`) that validates the
entire transport-and-routing pipeline without involving Swing or any
external SDK.

### Alternatives considered

- **Auto-retry as default behavior.** Rejected — silently replacing a
  stateful session with a fresh one converts a clear failure signal
  into subtly wrong results. See the swing-mcp ref-map example above.
- **Auto-retry as inheritance (subclass overrides one protected
  method).** Rejected — composition over inheritance. A decorator
  composes with future wrappers (logging, telemetry, rate-limiting); a
  subclass forces a single linear hierarchy.
- **`TinyMCPClient.withAutoRetry(URI url)` static factory.** Rejected
  in favor of the `MCPClient.autoRetry()` default method — works on
  any `MCPClient` implementation, chains with future decorators, and
  doesn't bind the convenience to one concrete class.
- **Use the official MCP Java SDK as the client.** Rejected — pulls in
  Jackson and a servlet container per DR-002. Already used for tests
  only; making it a runtime dep contradicts the project's whole reason
  for existing.
- **Build the client in a separate `mcp-client` Gradle subproject.**
  Rejected — the client and server share `MCPProtocol` POJOs and
  exception conventions; splitting them costs more in coordination
  than it saves in cohesion. A new package within tiny-mcp-server is
  the right granularity.
- **Full client surface (resources, prompts, completions, sampling)
  upfront.** Rejected — YAGNI. Add when a caller asks.
- **Client-side retry on transport `IOException`.** Rejected — the
  caller knows the operation's idempotency better than the client
  does.

---

## DR-009 — Request records carrying name, transport headers, and JSON-RPC `_meta`

**Status:** Accepted
**Applies to:** `ToolFunction`, `PromptFunction`, `ResourceFunction`

**Decision.** The three handler SAMs change from positional arguments to
small `record` types that bundle the existing key (tool name, prompt
name, resource URI) with two new fields: transport headers and the
JSON-RPC `_meta` object from the request envelope. Shapes:

```java
public record ToolRequest(
    String name,
    Map<String, Object> arguments,
    Map<String, String> transportHeaders,
    JsonObject jsonRpcMeta
) {}

public record PromptRequest(
    String name,
    Map<String, String> arguments,
    Map<String, String> transportHeaders,
    JsonObject jsonRpcMeta
) {}

public record ResourceRequest(
    String uri,
    Map<String, String> transportHeaders,
    JsonObject jsonRpcMeta
) {}
```

`transportHeaders` carries HTTP request headers in HTTP mode and is
empty in stdio mode (no out-of-band metadata in newline-delimited JSON).
`jsonRpcMeta` is the parsed `params._meta` GSON `JsonObject` if present
in the request, or `null` otherwise. Both maps are unmodifiable.

The SAMs become:

```java
ToolFunction:     MCPProtocol.Content     call(ToolRequest request) throws Exception;
PromptFunction:   MCPProtocol.GetPromptResult call(PromptRequest request) throws Exception;
ResourceFunction: List<MCPProtocol.ResourceContents> call(ResourceRequest request) throws Exception;
```

**Why.** Two near-term reasons and one durability reason.

- **Near-term: forwarding proxies need the tool/prompt name.** A single
  forwarding lambda registered N times (once per upstream tool) needs to
  know which tool was actually invoked so it can forward the right name
  upstream. Today the lambda's identity is the only carrier — workable
  but ugly when multiple tools share an implementation.
- **Near-term: forwarding proxies want to pass `_meta` through.** MCP's
  `_meta` carries cross-cutting fields like `progressToken` that should
  survive a hop through a proxy.
- **Durable: records grow more cheaply than SAMs.** JSON-RPC may add
  more envelope fields over time (it has already grown `_meta`). A
  record absorbs new optional fields without breaking every callback
  signature; a positional SAM does not.

For resources, the `uri` is the closest analogue to `name` — it is the
identity slot in the request — so the record uses `uri`. The shape is
otherwise identical for symmetry.

**Why not just add the name as a second arg.** Considered. Cheaper for
this round but fragile: the next field (and there will be one — `_meta`
is the obvious next addition) breaks every callback signature again.
Records pay the conversion cost once.

**Migration impact.** In-tree, the only consumer is swing-mcp's
`SwingMCP.registerTool(AbstractSwingTool)` adapter, which already
adapts at one seam. The change is a small refactor there. There are no
external consumers (per the project-context: "internal dependency of
swing-mcp and not meant to be used elsewhere").

**Alternatives considered.**
- **Two-arg SAM (`call(String name, Map args)`).** Rejected — see
  "durability" above.
- **Pass `MCPSession` and let handlers reach into it for name / headers
  / meta.** Rejected — couples handlers to internal session state and
  encourages reaching for fields that should be explicit inputs. The
  request record makes the contract explicit at the call site.
- **Put `_meta` in `MCPSession.getCurrent()` as a thread-local.**
  Rejected — same problem, plus thread-locals are a hassle in async
  handlers and bad in tests.
- **Skip the resource record (resources already get the URI).**
  Rejected — symmetry across tools/prompts/resources is more valuable
  than saving one record. A future `_meta` need on resources would
  re-open this question.

---

## DR-010 — Transport-vs-protocol seam: extract `MCPHandler`

**Status:** Accepted (final shape set by DR-011)
**Applies to:** `HttpMCPServer`, `MCPHandler`, `MCPSession`,
`MCPToolHandler`, `MCPResourceHandler`, `MCPPromptHandler`
**Enables:** DR-007 (stdio transport)
**Refined by:** DR-011 (`MCPHandler` becomes the public configuration
API; transport classes drop their delegating registration methods and
subclass hooks; `TinyMCPServer` renamed to `HttpMCPServer`).

**Decision.** Split `HttpMCPServer` along the HTTP-vs-protocol seam. A
new `MCPHandler` class owns everything transport-agnostic:

- the tool / resource / prompt registries,
- the session map and `sessionGuardLock`,
- the shared `ScheduledExecutorService` (daemon, `tiny-mcp-server-N`),
- the once-per-minute idle-cleanup tick,
- the JSON-RPC dispatch for `initialize` / `ping` and routing to
  `MCPSession`.

`HttpMCPServer` keeps only HTTP-specific concerns: the JDK `HttpServer`
and its executor, request routing (POST / DELETE / 405),
`Mcp-Session-Id` header validation, and JSON-RPC response framing via
`JsonRpcExchange`. `HttpMCPServer.handleRequest` remains the single
HTTP-side seam where `MCPServerException` / `TransportIOException` /
unexpected `RuntimeException` are translated into HTTP responses
(DR-004). After DR-011, configuration moved entirely onto `MCPHandler`
— `HttpMCPServer` keeps only `start` / `stop` / `getPort` / `getUrl` /
`getContextPath` / `getHandler`; the `addTool` / `addResource` /
`addPrompt` delegates and the `acceptNewSession` / `onSessionClosed`
protected hooks are gone, replaced by direct registration on the
caller-supplied `MCPHandler` and constructor-injected callbacks on
that handler.

**Error rendering moves above transport.** Handler methods on
`MCPToolHandler` / `MCPResourceHandler` / `MCPPromptHandler` and on
`MCPSession.handlePost` now return result POJOs (or throw
`MCPServerException`) instead of writing through `JsonRpcExchange`.
The transport renders the response. This makes the entire dispatch
core reusable from a future stdio transport that ignores the HTTP-
status hint on `MCPServerException` and renders every error as a
JSON-RPC envelope.

**`MCPSession`'s back-pointer** is retyped from `HttpMCPServer` to
`MCPHandler`, and the accessor renamed `getServer` → `getHandler`.
Tool code that previously reached for the executor via
`MCPSession.getCurrent().getServer().getExecutor()` now uses
`getHandler().getExecutor()` (only in-tree consumer:
`SwingMCP`).

**Why.** Two reasons.

- **DR-007 enablement.** Stdio is single-session, has no
  `Mcp-Session-Id` header, no HTTP status to surface, and frames
  messages as newline-delimited JSON instead of an HTTP response. None
  of those concerns belong in the registries, dispatch, or session
  cleanup. Pulling them apart cleanly means the stdio transport can be
  added without touching `MCPHandler` — it just constructs one,
  drives messages into `dispatchInitialize` / `dispatchPing` /
  `MCPSession.handlePost`, and writes the returned POJOs.
- **Smaller `HttpMCPServer`.** The class previously bundled HTTP
  framing with protocol logic, registries, and lifecycle in one ~440
  LOC file. After the split, `HttpMCPServer` is the HTTP-specific
  half; `MCPHandler` is the protocol-specific half. Each is testable
  in isolation.

**Alternatives considered.**
- **Keep everything in `HttpMCPServer` and add a `runStdio` method
  alongside `start()`.** Rejected — would force every stdio code path
  to thread around HTTP-specific machinery (header lookups,
  `JsonRpcExchange`) it does not need, and would couple stdio's
  single-session simplicity to the HTTP multi-session map invariants.
- **Make `MCPHandler` the public class and have `HttpMCPServer`
  extend it.** Rejected — composition over inheritance. A future
  stdio transport composes the same way; it does not extend
  `HttpMCPServer`.
- **Pass `MCPSession.handlePost` a writer callback so the session
  could write responses itself.** Rejected — leaks transport framing
  into the session, where it doesn't belong, and forces every
  transport to expose an identical writer abstraction. Returning
  POJOs is simpler.
- **Move `acceptNewSession` / `onSessionClosed` onto `MCPHandler`
  directly (drop the protected hooks).** Originally rejected because
  it would force swing-mcp's single-session subclass to extend
  `MCPHandler` instead of the transport, breaking the existing
  `extends TinyMCPServer` pattern. **Subsequently accepted by
  DR-011**: swing-mcp's `MCPServer` was renamed to `SwingMCP`
  and converted from inheritance to composition, removing the
  inheritance constraint that motivated the original rejection.

---

## DR-011 — `MCPHandler` becomes the public configuration API; transports compose

**Status:** Accepted
**Applies to:** `MCPHandler`, `HttpMCPServer` (renamed from
`TinyMCPServer`), `StdioMCPServer`, `SwingMCP` (renamed from
swing-mcp `MCPServer`), `ToolFunction`, `ResourceFunction`,
`PromptFunction`
**Refines:** DR-003 (single-session policy), DR-010 (transport-vs-
protocol seam)

**Decision.** Finish the split DR-010 started: make `MCPHandler` the
public configuration surface, and reduce the transports to thin shells
that take a configured handler. Concretely:

- `MCPHandler` exposes `addTool` / `addResource` / `addPrompt` and
  takes the `acceptNewSession` (`IntPredicate`) and `onSessionClosed`
  (`Consumer<MCPSession>`) callbacks via its constructor (any can be
  `null` for default behaviour — always-accept and no-op).
- `HttpMCPServer` (renamed from `TinyMCPServer`) takes
  `(port, contextPath, MCPHandler)` and drops:
  - the `addTool` / `addResource` / `addPrompt` delegate methods,
  - the protected hooks `acceptNewSession()` and `onSessionClosed()`,
  - the convenience `getExecutor()` (callers reach via
    `getHandler().getExecutor()`).
- `StdioMCPServer` parallels: takes `(MCPHandler)` and drops the same
  delegate methods.
- `ToolFunction` / `ResourceFunction` / `PromptFunction` are promoted
  from nested types on `TinyMCPServer` to top-level interfaces in
  `com.vaadin.swingmcp.tinymcpserver`. Neither transport "owns" them.
- Transport lifecycle still drives the handler's lifecycle:
  `server.start()` calls `handler.start()`, `server.stop()` calls
  `handler.stop()`. A handler is paired with a single transport for
  one lifecycle cycle (no reuse).
- swing-mcp's `MCPServer` class is renamed to `SwingMCP` and
  switches from `extends TinyMCPServer` to composition: it constructs
  an `MCPHandler` (with `count -> count == 0`) and an `HttpMCPServer`
  wrapping it, and exposes the same public surface as before
  (`start()` / `stop()` / `startAndAutoStop()` / `getUrl()` etc.).

**Why.** DR-010 left an asymmetry: tool registration and the
session-policy hooks lived on the transport, while the registries and
session map already lived on `MCPHandler`. That meant `StdioMCPServer`
duplicated forwarding methods, and any caller who wanted a custom
single-session policy had to subclass the HTTP transport — a poor fit
for stdio, where the transport has no useful surface to override.
After DR-011, configuration is a single object (`MCPHandler`) and
transports are interchangeable shells.

This is also what made the `acceptNewSession`/`onSessionClosed`
constructor parameters originally rejected in DR-010 acceptable: with
swing-mcp now composing rather than extending, "force the single-
session caller to extend `MCPHandler`" no longer applies — they
inject a predicate.

**Alternatives considered.**
- **Keep `addTool` / `addResource` / `addPrompt` as convenience
  delegates on the transports.** Rejected — duplicate API surface for
  no benefit; the handler's accessor (`server.getHandler().addTool(…)`)
  is one extra method call and makes the configuration object
  explicit.
- **Keep the protected hook pattern on `HttpMCPServer` for backward
  compatibility.** Rejected — there are no external subclasses to
  preserve; the only in-tree subclass was swing-mcp's `MCPServer`,
  which is being renamed and converted to composition anyway. The
  predicate form is also strictly more flexible (any caller, not just
  subclasses, can configure the policy).
- **Make handler lifecycle caller-driven (`handler.start()` /
  `handler.stop()` outside the transport).** Rejected — adds a
  required call to every callsite for no gain. Transport-driven
  lifecycle matches today's behaviour and pairs naturally with the
  "one handler, one transport" rule.
