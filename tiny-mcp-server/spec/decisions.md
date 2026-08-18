# Decisions

Cross-cutting design decisions for `tiny-mcp-server` — **what** was chosen,
**why**, and the **alternatives** that were considered and rejected. The
rejected roads are the most valuable thing in here: `architecture.md` describes
the server as it is, and only this file records the forks — which cheaper-looking
design was tried, and why it lost.

**When to read.** When a use case or an implementation note cites a
`DR-<slug>`, or when you are about to revisit a choice that spans multiple
use cases. Never read the file wholesale — `grep '^## DR-' decisions.md` is
the index, and there is deliberately no table of contents to drift out of
sync with it.

**Scope boundary.** This file owns the MCP core: transports, sessions,
JSON-RPC, the client, and the forwarding proxy. Swing-specific decisions —
snapshot shape, tool semantics, EDT dispatch — live in
[`swing-mcp/spec/decisions.md`](../../swing-mcp/spec/decisions.md). Slugs are
unique across both files, so either file can cite the other's slug (e.g.
`DR-fire-and-forget-dispatch`) without ambiguity.

**Format.** One entry per decision, headed
`## DR-<slug> — <headline> (<decided date>)`. The ID is a slug — `DR-` plus a
1–4-word kebab hint at the subject (`DR-stdio-never-evicts`) — so a citation
carries meaning on its own; there are no `DR-NNN` numbers to look up. The date
is *decided* provenance, not a log position: git owns the edit history, so never
narrate how an entry used to read. Each entry carries a `**Status:**` line —
**Proposed** → **Accepted** → **Implemented**, plus **Deferred** for a design
explored and shelved and **Superseded by DR-\<slug\>** for a tombstone — and an
`**Applies to:**` line naming the classes and use cases the decision binds.
Entries that build on, refine, or amend a neighbour say so in a
`**Refines:**` / `**Amends:**` / `**Builds on:**` line, which is what makes the
chain greppable.

**No entry without a real fork.** If nothing was seriously considered and
rejected, it is not a decision — it is how the thing works, and that belongs in
`architecture.md`. This is the guard against a diary.

**Entries are mutable — edit in place, never append addendums.** Each entry is
the single coherent home for one *live* decision; keep it current as the
decision is refined or extended instead of bolting a dated amendment onto the
end. Two things this does **not** license:

- **The roads not taken stay.** "We chose X, rejected Y because Z" is live
  content of the current decision, not stale history — never edit it away.
- **A reversed *shipped* decision forks a tombstone; it is not overwritten.**
  When a design was built and then thrown out, leave the old entry as the scar,
  set its `**Status:**` to **Superseded by DR-\<slug\>**, and write the
  replacement fresh. A decision that is merely *narrowed* is amended in place
  and says so — worked example: `DR-idle-session-eviction` keeps its entry and
  points forward at `DR-stdio-never-evicts`, which narrowed it to the HTTP
  transport. The line: *refined, narrowed, or extended* → edit in place;
  *reversed after shipping* → tombstone plus a new entry.

**Ordering is chronological, oldest first**, so the refines / amends chains read
forward.

**Grep tripwire.** Every `DR-<slug>` cited anywhere in the repo must exist as a
`^## DR-` heading here or in `swing-mcp/spec/decisions.md`.

---

## DR-localhost-http-no-auth — HTTP transport, localhost binding, no auth (2026-04-14)

**Status:** Accepted (stdio scope amended by DR-stdio-transport)
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
the in-process Swing app owns stdout. DR-stdio-transport reinstates stdio for a
distinct use case — a standalone process spawned by an MCP client where
no other code writes to stdout. The localhost-HTTP-no-auth decision above
is unchanged.

---

## DR-gson-only — GSON only, no official MCP SDK at runtime (2026-04-14)

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

## DR-injected-session-policy — Single-session policy as a constructor-injected predicate (2026-04-14)

**Status:** Accepted (mechanism revised by DR-handler-as-configuration;
conflict-resolution superseded by DR-supersede-sessions)
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
evicted by the idle-cleanup tick (DR-idle-session-eviction) after 30 minutes of no
activity. For the single-session caller, that frees the slot
automatically without restarting the Swing app.

**Why this split.** `MCPHandler` aims to be a reusable minimal MCP
core, and multi-session is the MCP spec default. Baking
single-session into the base class would either leak Swing-specific
concurrency assumptions into tiny-mcp-server or force every reuser to
work around them. A pluggable predicate keeps the base class general
while letting `SwingMCP` express its own constraint in one line.

**History.** Originally implemented as a `protected boolean
acceptNewSession()` subclass hook on `TinyMCPServer`.
DR-handler-as-configuration replaced the subclass hook with a
constructor-injected `IntPredicate` on `MCPHandler`, so callers compose the
policy in rather than extending the transport class.

**Alternatives considered.**
- **Bake single-session into `MCPHandler`.** Rejected — forces
  Swing-specific concurrency semantics onto a general-purpose server;
  tiny-mcp-server's own multi-session tests would have to work around
  it.
- **Session queuing (block until current session ends).** Rejected —
  adds complexity for a scenario that doesn't arise in normal use. The
  blocked agent would hang indefinitely if the first session is stuck.
- **Idle timeout to auto-release stuck sessions.** Accepted — see
  DR-idle-session-eviction. Sessions idle for 30 minutes are evicted by a background
  cleanup tick, so a crashed client no longer blocks the single-session
  slot until the Swing app is restarted.

---

## DR-three-error-layers — Three-layer error handling model (2026-04-14)

**Status:** Accepted
**Applies to:** HttpMCPServer, MCPSession, all tool / resource / prompt
implementations

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
| `SERVER_NOT_INITIALIZED` | -32002 | 400 / 404 / 409 | session lifecycle (DR-session-lifecycle-gate), second-session rejection (DR-injected-session-policy) |
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

## DR-session-lifecycle-gate — Every POST is validated against the session map before dispatch (2026-04-17)

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
`acceptNewSession()` (DR-injected-session-policy). Full outcome matrix:

| `Mcp-Session-Id` header | Method | Result |
|---|---|---|
| absent | `initialize` | Create new session, return 200 + `Mcp-Session-Id` — or HTTP 409 if `acceptNewSession()` returns false (DR-injected-session-policy) |
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

## DR-idle-session-eviction — Idle session eviction via a shared scheduled executor (2026-04-17)

**Status:** Accepted (executor ownership moved to `MCPHandler` by
DR-transport-protocol-seam; scope narrowed to the HTTP transport by
DR-stdio-never-evicts)
**Applies to:** `MCPHandler`, `HttpMCPServer`, `MCPSession`
**Supersedes the "Idle timeout" deferral in:** DR-injected-session-policy
**Amended by:** DR-stdio-never-evicts — the tick is scheduled by
`HttpMCPServer.start()`, not by `MCPHandler.start()`; stdio never schedules
it

**Decision.** `MCPHandler` owns a single `ScheduledExecutorService`
(one daemon thread, named `tiny-mcp-server-N`) created in its
`start()` and shut down in `stop()`. `HttpMCPServer.start()` /
`stop()` (and `StdioMCPServer.runStdio()` start/finish) delegate the
lifecycle calls. Once per minute the executor runs a cleanup tick
that evicts every session whose last access is older than 30 minutes
— but only where a transport asked for one, which per DR-stdio-never-evicts means
HTTP only, via `MCPHandler.scheduleIdleCleanup()`.
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
(60) are package-private, as are `cleanupIdleSessions()` and
`MCPSession.setLastAccessNanos(long)`. Tests never wait and never
shorten the timeout: they backdate a session's last-access stamp and
drive the tick synchronously, so the 30-minute path runs in
milliseconds (`SessionCleanupTest`).

**Why.**

- **Stuck single-session slot.** DR-injected-session-policy left the single-session
  subclass wedged if an AI client crashed without sending DELETE; the
  only remedy was restarting the Swing app. 30 minutes matches a
  realistic "agent walked away" window without fighting legitimate
  long pauses during a migration session.
- **One executor, many uses.** Tool handlers already wanted a place to
  park background work; running a second executor purely for cleanup
  would double the daemon-thread count and still leave tools with
  nowhere to schedule. Sharing the executor keeps the dependency
  surface minimal (a single `ScheduledExecutorService` field, no new
  runtime libraries per DR-gson-only) and gives tools a predictable,
  already-managed lifecycle tied to `start()` / `stop()`.
- **`runLocked` as the chokepoint.** Refreshing the timestamp at every
  lock acquisition — rather than in `handlePost`'s entry path — means
  test-only callers that run through `runLocked` also count as
  activity, and there is a single place where "this session is alive"
  is recorded. Pairing it with the `closed`-flag check keeps the
  post-eviction 404 consistent with DR-session-lifecycle-gate's "wrong session → 404"
  rule without duplicating map lookups.
- **`tryLock` over `lock`.** A blocking `lock()` in the cleanup thread
  would stall the whole cleanup pass behind any slow request, and
  could in the worst case deadlock with a tool that schedules work on
  the same executor and waits for it. `tryLock()` + retry-next-tick
  keeps cleanup bounded and lock-free from the request path's
  perspective.

**Alternatives considered.**
- **No idle eviction (DR-injected-session-policy status quo).** Rejected — a crashed
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

## DR-stdio-transport — Stdio transport as a second transport mode (2026-04-29)

**Status:** Accepted
**Applies to:** HttpMCPServer
**Amends:** DR-localhost-http-no-auth — the "no STDIO" half

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
transport carries no `Mcp-Session-Id` header — there is exactly one implicit
session for the lifetime of the process. Internally, `runStdio` creates one
`MCPSession` up front (after `initialize` is received from the client) and
routes every subsequent message through it. `acceptNewSession()` is
consulted exactly once. The DR-session-lifecycle-gate routing matrix
collapses: there is no "wrong session id → 404" branch, only "`initialize`
first, then any other method".

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
`ScheduledExecutorService` (DR-idle-session-eviction) is started and stopped around the
read loop the same way `start()` / `stop()` do for HTTP. Idle eviction
is **not** scheduled: a stdio session is process-scoped, and evicting
one is unrecoverable rather than merely useless. See DR-stdio-never-evicts, which
corrects the original "the machinery still runs, keeping one code path
simplifies things" reasoning recorded here.

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

## DR-embedded-mcp-client — Embedded MCP client (HTTP, minimal surface, opt-in retry) (2026-04-29)

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
runtime deps per DR-gson-only). No stdio client in this round — the proxy
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
three-layer model from DR-three-error-layers. The proxy then wraps it back into a
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
  Jackson and a servlet container per DR-gson-only. Already used for tests
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

## DR-request-records — Request records carrying name, transport headers, and JSON-RPC `_meta` (2026-04-29)

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

## DR-transport-protocol-seam — Transport-vs-protocol seam: extract `MCPHandler` (2026-04-29)

**Status:** Accepted (final shape set by DR-handler-as-configuration)
**Applies to:** `HttpMCPServer`, `MCPHandler`, `MCPSession`,
`MCPToolHandler`, `MCPResourceHandler`, `MCPPromptHandler`
**Enables:** DR-stdio-transport (stdio transport)
**Refined by:** DR-handler-as-configuration (`MCPHandler` becomes the public
configuration API; transport classes drop their delegating registration
methods and subclass hooks; `TinyMCPServer` renamed to `HttpMCPServer`).

**Decision.** Split `HttpMCPServer` along the HTTP-vs-protocol seam. A
new `MCPHandler` class owns everything transport-agnostic:

- the tool / resource / prompt registries,
- the session map and `sessionGuardLock`,
- the shared `ScheduledExecutorService` (daemon, `tiny-mcp-server-N`),
- the once-per-minute idle-cleanup tick,
- the JSON-RPC dispatch for `initialize` / `ping` and routing to
  `MCPSession`.

`HttpMCPServer` keeps only HTTP-specific concerns: the JDK `HttpServer` and
its executor, request routing (POST / DELETE / 405), `Mcp-Session-Id` header
validation, and JSON-RPC response framing via `JsonRpcExchange`.
`HttpMCPServer.handleRequest` remains the single HTTP-side seam where
`MCPServerException` / `TransportIOException` / unexpected
`RuntimeException` are translated into HTTP responses
(DR-three-error-layers). After DR-handler-as-configuration, configuration
moved entirely onto `MCPHandler` — `HttpMCPServer` keeps only `start` /
`stop` / `getPort` / `getUrl` / `getContextPath` / `getHandler`; the
`addTool` / `addResource` / `addPrompt` delegates and the `acceptNewSession`
/ `onSessionClosed` protected hooks are gone, replaced by direct
registration on the caller-supplied `MCPHandler` and constructor-injected
callbacks on that handler.

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

- **DR-stdio-transport enablement.** Stdio is single-session, has no
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
  DR-handler-as-configuration**: swing-mcp's `MCPServer` was renamed to `SwingMCP`
  and converted from inheritance to composition, removing the
  inheritance constraint that motivated the original rejection.

---

## DR-handler-as-configuration — `MCPHandler` becomes the public configuration API; transports compose (2026-04-29)

**Status:** Accepted
**Applies to:** `MCPHandler`, `HttpMCPServer` (renamed from
`TinyMCPServer`), `StdioMCPServer`, `SwingMCP` (renamed from swing-mcp
`MCPServer`), `ToolFunction`, `ResourceFunction`, `PromptFunction`
**Refines:** DR-injected-session-policy, DR-transport-protocol-seam

**Decision.** Finish the split DR-transport-protocol-seam started: make
`MCPHandler` the public configuration surface, and reduce the transports to
thin shells that take a configured handler. Concretely:

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

**Why.** DR-transport-protocol-seam left an asymmetry: tool registration and the
session-policy hooks lived on the transport, while the registries and
session map already lived on `MCPHandler`. That meant `StdioMCPServer`
duplicated forwarding methods, and any caller who wanted a custom
single-session policy had to subclass the HTTP transport — a poor fit
for stdio, where the transport has no useful surface to override.
After DR-handler-as-configuration, configuration is a single object (`MCPHandler`) and
transports are interchangeable shells.

This is also what made the `acceptNewSession`/`onSessionClosed` constructor
parameters originally rejected in DR-transport-protocol-seam acceptable:
with swing-mcp now composing rather than extending, "force the single-
session caller to extend `MCPHandler`" no longer applies — they inject a
predicate.

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

---

## DR-forwarding-proxy — Generic MCP forwarding-proxy machinery (`MCPProxy`) (2026-04-29)

**Status:** Accepted
**Applies to:** new `MCPProxy` factory in `tiny-mcp-server`, `MCPHandler`,
`MCPSession`, `MCPClient` / `TinyMCPClient`, `ProxyMessages`
**Builds on:** DR-stdio-transport (stdio transport), DR-embedded-mcp-client
(HTTP client), DR-request-records (`ToolRequest._meta`),
DR-transport-protocol-seam / DR-handler-as-configuration (handler is the
configuration object)

**Decision.** `tiny-mcp-server` ships a generic forwarding-proxy
factory:

```java
public final class MCPProxy {
    public static MCPHandler newHandler(
        List<ToolDescriptor> tools,
        URI upstreamUrl,
        ProxyMessages messages);
}
```

The returned `MCPHandler` is fully wired and ready for a transport
(typically `StdioMCPServer`) to run. There is no `MCPProxy` instance
to hold — all per-session state lives on `MCPSession.attributes`
(the bag already exposed by `MCPSession`).

**`tools/list` is answered from the static `tools` list, always** —
even after a drift failure has been cached, even before upstream has
been contacted. The proxy advertises the same manifest its caller
declared, regardless of upstream availability. The tools' authority
to act is verified per call, not per list.

**Per-session lifecycle.** `MCPProxy.newHandler` configures the
returned handler as follows:

- **`acceptNewSession = count -> count == 0`** — single-session by
  default. Stdio is one-client-per-JVM, so concurrent sessions are
  out of scope. Callers needing a non-stdio multi-session proxy can
  override the predicate via the setter (DR-settable-listeners).
- **`onSessionStarted`** — creates a per-session state object
  (`upstream = new TinyMCPClient(upstreamUrl)`,
  `initialized = false`, `driftFailure = null`) and stashes it on
  `MCPSession.attributes`. No upstream traffic yet.
- **`onSessionClosed`** — best-effort `state.upstream.close()`,
  swallowing `IOException`. Idempotent so a JVM shutdown hook can
  also close the client without double-close errors.
- **One forwarding `ToolFunction` per descriptor**, all sharing one
  implementation that:
  1. Reads the per-session state from `MCPSession.attributes`.
  2. If `state.driftFailure != null` → return the cached drift
     `isError`. Permanent for the session.
  3. If not yet initialized → lazy-init upstream:
     - On `IOException` → return `messages.upstreamDownMessage()`
       as `isError`. **Do not** mark permanently dead: the next
       call retries init from scratch. (Operator just had to start
       the upstream.)
     - On success → run the **drift probe** below. On mismatch,
       cache as `state.driftFailure` and return
       `messages.driftMessage()` as `isError`.
  4. Forward
     `client.callTool(req.name(), req.arguments(), req.jsonRpcMeta())`:
     - `MCPSessionLostException` → return
       `messages.sessionLostMessage()` as `isError`. **Surface,
       don't auto re-initialize.** Reset `state.initialized = false`
       so the next call walks lazy-init again. (Same rationale as
       DR-embedded-mcp-client's "no auto-retry by default": session-bound state
       can't survive a fresh `initialize`.)
     - `IOException` mid-call → return
       `messages.ioMidCallMessage()` as `isError`. Reset
       `state.initialized = false`.
     - `MCPClientException` → forward upstream's JSON-RPC error
       message verbatim as `isError`.
     - `CallToolResult.isError == true` → forward `Content` and
       the `isError` flag verbatim.

**Drift probe.** At first successful upstream `initialize`,
`MCPProxy` calls `client.listTools()` and compares the response
against the supplied `tools` list as a set keyed by tool name.
Comparison is **symmetric, hard-fail**: a tool present in either
side but missing from the other is drift; same name with different
fields (description, schema) is drift. Equality follows DR-structural-schema-equality's
structural rules. Newer agent tools could mean **subtle changes in
shared-tool behavior** even if the extra tools never reach Claude;
treating mismatched versions as drift is the deployment failure
the probe exists to catch.

The probe runs **once per fresh upstream session**. The first
`tools/call` after each fresh upstream session pays three localhost
round-trips (initialize + listTools + the actual forward); this is
accepted (Q14).

**Session lifetime.** A drift failure caches for the lifetime of
the *current* session. A re-`initialize` from the MCP client opens
a fresh session: `onSessionClosed` closes the old upstream client,
`onSessionStarted` creates a new one, the next `tools/call` walks
lazy-init + drift probe again. Consistent with DR-embedded-mcp-client's "no hidden
state across sessions."

**`ProxyMessages` shape.** Four pre-formatted strings, no
templating:

```java
public record ProxyMessages(
    String upstreamDownMessage,
    String driftMessage,
    String sessionLostMessage,
    String ioMidCallMessage) { }
```

`tiny-mcp-server` is template-free — `MCPProxy` emits each string
verbatim as the `Content` of an `isError: true` result. Callers
interpolate any URL / remote-name references at construction time.
Each field's javadoc carries an example string showing the voice
and concreteness expected of a good message (clear cause, named
remediation, "do not retry" where appropriate).

**Server description is load-bearing.** `tools/list` always returns
the static manifest, even when upstream is down. To keep the LLM
oriented in that state, the proxy's MCP server-info description
itself must spell out the model: "this is a proxy to an in-process
MCP server; if the upstream isn't running, every tool call fails
with an error explaining the situation; tell the user to start the
upstream." Treat the server description as a first-class artifact,
not boilerplate. (See Q29.)

**Why.** The new use case (DR-stdio-transport's stdio rationale) is a proxy
spawned by Claude Code as a subprocess that forwards `tools/call`
to a separately-running in-process MCP server (e.g. `SwingMCP`).
Claude Code dispatches `tools/list` at startup and drops any MCP
that errors on it — so the proxy must succeed in answering
`tools/list` even when the upstream isn't yet running. That forces
the manifest to be a static input to the proxy, separate from the
upstream. Once the manifest is static, drift between proxy and
upstream becomes a real failure mode, and the probe is the one
place that detects it.

**Why static factory, not a class instance.** All proxy state lives
on the per-session attribute bag. There is nothing to hold across
sessions; an instance would be empty. The factory shape also makes
the wiring rule visible at the call site — `MCPProxy.newHandler`
returns a configured `MCPHandler`, the caller wraps it in a
transport.

**Alternatives considered.**
- **`tools/list` triggers init + probe.** Rejected — `tools/list`
  fires at MCP-client init time; an error there causes Claude Code
  to drop the MCP entirely. Defeats the whole point of a proxy that
  survives upstream-down.
- **Empty `tools/list` once drift is cached.** Rejected — Claude
  sees an empty list, decides the MCP is broken, stops using it.
  Worse than the "lying-list + clear `isError` body" combination.
- **Auto-retry on `MCPSessionLostException`.** Rejected — silent
  state loss. Same rationale as DR-embedded-mcp-client's no-auto-retry default.
  The proxy's session-lost message tells the LLM exactly how to
  recover; that's better than fake success on a stale session.
- **`Supplier<MCPClient>` parameter instead of `URI`.** Rejected
  (originally accepted, then walked back). Tests already use HTTP
  loopback for the client surface; a supplier abstraction earned
  no mock-injection benefit and added a failure mode (supplier
  throws). Direct `new TinyMCPClient(uri)` is simpler and the URI
  is validated once at startup. If a future non-HTTP transport
  needs proxying, add a supplier overload then.
- **Soft-fail on drift (warn but forward anyway).** Rejected — the
  whole point of the probe is to catch deployment mismatches. A
  drifted manifest can mean different argument semantics or
  different return shapes; forwarding produces silently-wrong
  results.
- **Async drift probe at session start.** Rejected — adds a race
  between the probe and the first `tools/call`. The 3-round-trip
  cost on the first call is the simplest and most predictable
  shape.

---

## DR-settable-listeners — Settable `MCPHandler` listeners; `ToolDescriptor`; `_meta` callTool overload (2026-04-29)

**Status:** Accepted
**Applies to:** `MCPHandler`, new `ToolDescriptor` record in
`com.vaadin.swingmcp` (parent package), `MCPClient.callTool` overload
**Refines:** DR-handler-as-configuration
**Supersedes:** the recent commit tightening `acceptNewSession` /
`onSessionClosed` to non-null constructor parameters (`b128690 — "Require
non-null acceptNewSession / onSessionClosed in MCPHandler"`)

**Decision.** Three coupled API extensions to `tiny-mcp-server` so
`MCPProxy` (DR-forwarding-proxy) can wire a handler post-construction without
forcing every caller through a fat constructor.

### 1. `MCPHandler` listener accessors with one-shot lockdown

`acceptNewSession`, `onSessionStarted` (new), and `onSessionClosed`
are configured via setters, not constructor parameters:

```java
public MCPHandler setAcceptNewSession(IntPredicate accept);
public MCPHandler setOnSessionStarted(Consumer<MCPSession> on);
public MCPHandler setOnSessionClosed(Consumer<MCPSession> on);
```

Each defaults to a sensible no-op:

| Listener | Default |
|---|---|
| `acceptNewSession` | `count -> true` (multi-session by default — MCP spec default) |
| `onSessionStarted` | `s -> {}` |
| `onSessionClosed` | `s -> {}` |

Setters are **settable until the first session opens**; calling
any setter after that throws `IllegalStateException` ("listener
locked once first session has been accepted"). The lockdown
matches the existing "one handler, one transport, one lifecycle
cycle" rule (DR-handler-as-configuration): listener semantics that change mid-flight
would be a footgun, but pre-flight reconfiguration is exactly what
factories like `MCPProxy.newHandler` need.

`MCPHandler`'s constructor returns to its 0-arg form (the original
shape, before the b128690 tightening). The `(IntPredicate,
Consumer<MCPSession>)` ctor introduced by DR-handler-as-configuration is removed —
callers that previously wrote `new MCPHandler(p, c)` now write
`new MCPHandler().setAcceptNewSession(p).setOnSessionClosed(c)`.

`onSessionStarted` is new in this DR. It fires after a session is
created but before the `initialize` response is returned to the
client. Used by `MCPProxy` to allocate per-session state. Both new
and existing transports invoke it from the same dispatch path.

### 2. `ToolDescriptor` record

A new public record in `com.vaadin.swingmcp` (the parent package,
which is currently empty — see DR-forwarding-proxy / Q23):

```java
public record ToolDescriptor(
    String name,
    String description,
    MCPProtocol.InputSchema inputSchema) { }
```

Distinct from the wire-shape POJO `MCPProtocol.Tool` (which carries
the same fields but is structured for GSON serialization).
`ToolDescriptor` is the in-memory contract artifact that gets
passed to `MCPProxy.newHandler` and embedded in
`AbstractSwingTool` constructors (see swing-mcp `architecture.md`).

A new convenience overload registers a tool from a descriptor:

```java
public MCPHandler addTool(ToolDescriptor descriptor, ToolFunction fn);
```

It delegates to the existing 4-arg
`addTool(name, description, schema, fn)`. This keeps the manifest
(in `swing-mcp-tool-defs`) as the single source for both proxy
forwarding and direct registration in the in-process server,
without rewiring all of `SwingMCP.registerTools()` (Q31).

`InputSchema.equals` / `hashCode` are structural per DR-structural-schema-equality, so
two descriptors with the same logical schema compare equal even
across separate parsings.

> Note: package `com.vaadin.swingmcp` will be renamed in a future
> task. Out of scope for this DR.

### 3. `MCPClient.callTool` overload accepting `_meta`

```java
CallToolResult callTool(
    String name,
    Map<String, Object> arguments,
    JsonObject _meta) throws IOException;
```

The existing two-arg form delegates to the three-arg form with
`null`. `MCPProxy`'s forwarding lambda calls the three-arg form
with `request.jsonRpcMeta()` (DR-request-records), so cross-cutting envelope
fields like `progressToken` survive a hop through the proxy.

`AutoRetryMCPClient` (DR-embedded-mcp-client) forwards `_meta` to its inner
client.

**Why.**

- **Setters over ctor params.** DR-handler-as-configuration / b128690 made the listeners
  constructor-only. That works for direct callers (which know all
  their callbacks at construction time) but blocks factories that
  build the handler then layer per-session lifecycle on top.
  `MCPProxy.newHandler` is exactly that pattern. Setters with
  one-shot lockdown preserve the "no semantic change mid-flight"
  invariant the b128690 commit was reaching for, without forcing
  every caller through a fat constructor.
- **`onSessionStarted`.** Today there is no hook for "a session
  was just born." `onSessionClosed` already exists as the
  symmetric end. Adding the start hook makes per-session resource
  setup natural and removes the alternative ("lazy-init inside
  every `ToolFunction`") which would scatter the wiring across N
  registrations.
- **`ToolDescriptor`.** `MCPProtocol.Tool` is the wire POJO,
  shaped for GSON. Reusing it as the in-memory contract type would
  expose serializer concerns to callers (constructors, default
  values, etc.). A small record in `com.vaadin.swingmcp` is the
  honest type for the contract.
- **`_meta` overload.** DR-request-records added `_meta` to incoming
  `ToolRequest`s. A forwarding proxy that drops `_meta` silently
  loses progress tokens, sampling hints, and any future envelope
  fields. The overload restores end-to-end transparency.

**Alternatives considered.**
- **Keep the b128690 non-null ctor enforcement.** Rejected —
  blocks factory wiring (DR-forwarding-proxy). The defaults are sensible, and
  a caller that forgets to override a listener will see the
  resulting behaviour (unlimited sessions, no per-session work)
  in their first integration test.
- **Always-settable listeners (no first-session lockdown).**
  Rejected — listener semantics changing mid-flight is a footgun.
  The lockdown is cheap and matches the one-handler-one-lifecycle
  rule.
- **Reuse `MCPProtocol.Tool` as the descriptor.** Rejected — wire
  POJO leaks GSON shape into call sites.
- **Replace the existing 4-arg `addTool`.** Rejected — too much
  churn for callers who already have `(name, description, schema,
  fn)` in hand. The descriptor overload delegates.
- **Add `_meta` as a third arg on the existing two-arg
  `callTool`.** Rejected — breaks every existing call site for a
  field the proxy is the only current consumer of. Overload
  preserves source compatibility.

---

## DR-structural-schema-equality — `MCPProtocol.InputSchema` structural `equals` / `hashCode` (2026-04-29)

**Status:** Accepted
**Applies to:** `MCPProtocol.InputSchema`
**Consumed by:** DR-forwarding-proxy (drift probe), `ToolDescriptor`
equality (DR-settable-listeners)

**Decision.** `MCPProtocol.InputSchema` (and any nested types it
references — property descriptors, etc.) implements deep
structural `equals` and `hashCode`:

- `type` field — equal as strings.
- `properties` map — same keys, and per-property: same `name`,
  `type`, `description`, `enum` values (compared as ordered list,
  matching JSON Schema semantics), and any other text fields.
- `required` — compared as a **set**, not an ordered list. JSON
  Schema's `required` is set-semantics; serialized order varies.
- Any extension fields the spec adds in future versions of the
  schema POJO must also be compared structurally.
- Field order is **insensitive**: two schemas whose `properties`
  iterate in different orders but contain the same entries compare
  equal.

`hashCode` is consistent with `equals` (same fields, set-hash for
`required`).

**Why.** DR-forwarding-proxy's drift probe compares descriptor lists. Equality
is a property of the type, not a one-off comparator inside
`MCPProxy`. A bug in the predicate would manifest as silent false
positives (drift errors that aren't really drift) or — worse —
silent false negatives (missed drift, with downstream silently-
wrong tool calls). Centralising the predicate on the type means
one place to audit, one place to test, and free reuse for any
future caller that wants to compare schemas (test assertions,
schema-cache deduplication, etc.).

Schemas are produced by `InputSchemaBuilder` and round-tripped
through GSON. Both paths must produce equal results for the same
logical schema, so the equality contract is "two `InputSchema`s
are equal iff they describe the same JSON Schema document modulo
property order."

**Alternatives considered.**
- **Custom comparator at each call site.** Rejected — drift across
  consumers; bug fixes wouldn't propagate.
- **JSON-serialize and string-compare.** Rejected — GSON property
  order is not part of the contract; two equal schemas might
  serialize to different strings on different days. Also slower
  and surprises tests that hold `InputSchema` instances directly.
- **Reflective deep equals (e.g. Apache Commons
  `EqualsBuilder.reflectionEquals`).** Rejected — pulls a
  dependency for one method, and reflection silently treats
  `required` as a list rather than a set.

---

## DR-supersede-sessions — Session conflict resolution: supersede + tombstones (2026-05-06)

**Status:** Accepted
**Applies to:** `MCPHandler`, `MCPSession`, `HttpMCPServer`,
`TinyMCPClient`, `swing-mcp` `SwingMCP`, `MCPProxy`
**Refines:** DR-injected-session-policy (the single-session mechanism),
DR-idle-session-eviction (idle session eviction)

**Decision.** A new `initialize` may evict existing sessions
("supersede"); the displaced client gets a 404 with a tombstone
reason instead of a generic "session not found" message.
Concretely:

- `MCPHandler.setAcceptNewSession` takes a
  `Function<List<MCPSession>, SessionDecision>` (replacing the
  prior `IntPredicate`). The policy receives a snapshot of
  currently active sessions and returns one of:
  - `SessionDecision.Reject` → HTTP 409 (unchanged from DR-injected-session-policy).
  - `SessionDecision.Accept` → accept without eviction.
  - `SessionDecision.AcceptAndEvict(sessions)` → tombstone and
    evict the listed sessions (blocking on each one's in-flight
    request via the new `MCPSession.close()`), then accept.
- `MCPHandler` keeps a 64-entry `BoundedLRUMap<String, String>` of
  recently-removed session ids → reason. Both supersede and idle
  eviction (DR-idle-session-eviction) write a tombstone. `HttpMCPServer`'s 404 sites
  consult the tombstone to populate the JSON-RPC `error.message`
  field.
- `TinyMCPClient` parses the 404 body's `error.message` and
  surfaces it via `MCPSessionLostException.getMessage()`, so
  callers see *why* their session is gone.
- `swing-mcp`'s `SwingMCP` and `MCPProxy.newHandler` flip from the
  prior reject-on-conflict policy to
  `existing -> new AcceptAndEvict(existing)`.

The supersede message intentionally **does not include the new
session id** — surfacing it would tempt the displaced client into
reattaching, ending up with two clients sharing one session, which
in an LLM-driven setup produces silently corrupted state and
hours of debugging the wrong layer.

Eviction always runs **outside** the handler's `sessionGuardLock`:
under the lock we snapshot the existing sessions, run the policy,
write tombstones, and put the new session; we then drop the lock
and call `close()` on each evicted session in turn. This ordering
keeps the global lock short and lets `close()` block on the
session's own lock (waiting for any in-flight request to quiesce)
without pinning the handler.

**Why.** The prior policy ("reject second initialize until the
first session times out") was correct under clean shutdown but
brittle in practice: when a stale client process exits without
sending DELETE, the next legitimate client cannot connect for up
to 30 minutes (the idle timeout). The dev workflow this server
exists to support — one developer, one IDE, occasional crashes
— is the worst case for that policy. Supersede ("new wins") fits
the actual usage: there is at most one *intended* client at a
time, so a fresh `initialize` is always the new ground truth.

The tombstone+message mechanism exists because the surfacing
layer matters: a client that sees `"Session not found"` cannot
distinguish "I'm talking to a stale id" from "the server
restarted" from "I was kicked." Each calls for a different
recovery, and only the server knows which one happened.

The `BoundedLRUMap` cap is 64 — generous for the dev case, where
churn is low (a handful of supersedes per day at most), but
robust against pathological churn (e.g. an idle-cleanup storm
during a flaky network) without unbounded memory growth.

**Alternatives considered.**
- **Keep reject; rely on shorter idle timeout.** Rejected — even
  a 1-minute timeout is jarring, and shorter still risks evicting
  legitimate idle sessions during long tool calls. Treats the
  symptom, not the cause.
- **Carry the new session id in the supersede message so the old
  client can reattach.** Rejected — produces two clients sharing
  one session, with arbitrary interleaving of state mutations.
  In an LLM-driven setup this manifests as randomly missing or
  duplicated tool calls. The cure is worse than the disease.
- **Imperative policy: predicate calls `evict()` itself.**
  Rejected — couples the policy to handler internals (session-map
  removal, listener firing, tombstone writing), and forces the
  predicate to run with side effects under the global lock.
  Declarative `SessionDecision` keeps the policy pure and the
  eviction machinery in one place.
- **Fixed supersede mode flag (single boolean) instead of a rich
  return type.** Rejected — collapses the multi-session case
  (e.g. "evict the LRU one when adding the Nth"). The
  `Function<List<MCPSession>, SessionDecision>` form expresses
  reject, accept, and any subset eviction in a single signature.
- **Tombstone reason carried in a custom HTTP header instead of
  the JSON-RPC body.** Rejected — JSON-RPC `error.message` is the
  natural channel and already round-trips through every existing
  client's error path. A custom header would need parallel
  plumbing on both ends.

---

## DR-stdio-never-evicts — Idle eviction is an HTTP-transport policy; stdio sessions are process-scoped (2026-08-18)

**Status:** Accepted
**Applies to:** `MCPHandler`, `HttpMCPServer`, `StdioMCPServer`,
`MCPSession`
**Amends:** DR-idle-session-eviction (idle session eviction),
DR-stdio-transport (stdio transport)

**Decision.** `MCPHandler.start()` creates the shared executor and
nothing else. Scheduling the once-per-minute idle-cleanup tick is a
separate package-private call, `MCPHandler.scheduleIdleCleanup()`,
made only by `HttpMCPServer.start()`. `StdioMCPServer` never calls it,
so a stdio session lives exactly as long as its process.

`StdioMCPServer.dispatch` asserts that invariant: if `currentSession`
is non-null but closed, it throws `IllegalStateException` rather than
letting `MCPSession.runLocked` surface a tombstone-backed 404. The
throw is an `IllegalStateException`, not an `AssertionError` — `-da`
would disable a bare `assert`, and an `Error` escapes
`handleMessage`'s `catch (RuntimeException)` and kills the read loop
instead of producing one internal-error reply.

**Why.**

- **Eviction exists for a failure mode stdio does not have.** DR-idle-session-eviction
  added the tick so a crashed HTTP client could not wedge the
  single-session slot until the Swing app restarted: the server cannot
  tell "client died" from "client went quiet", so it times out. Over
  stdio the transport answers that question — the client *is* the
  parent process holding the pipe, and stdin EOF is a definitive
  death notice, handled by `runStdio`'s `finally`. There is nothing to
  reclaim, so the timer can only destroy a live conversation.
- **The failure was unrecoverable, not merely wasteful.** Field
  incident (2026-08-18, recorded in `PROXY-SESSION-BUG.md`): a
  `swing-mcp-proxy` process sat idle ~32 minutes, its own downstream
  handler evicted its own stdio session, and every subsequent
  `tools/call` returned `MCPHandler.IDLE_REASON` — "Session expired
  (idle timeout)" — without any traffic ever reaching the Swing app.
  Nothing recovered it short of restarting the MCP client, because the
  MCP stdio transport has no session concept: there is no session id on
  the wire, no 404 for a client to interpret, and therefore no reason
  for any client to re-initialize. `StdioMCPServer` also pins the
  session in its `currentSession` field, so the process stays bricked.
  This is also why the proxy's own session-loss machinery (DR-forwarding-proxy) did
  not help — that handles the *upstream* session dying, and the dead
  session here was the downstream one.
- **Structural beats configurable.** A `setIdleTimeout(Duration)` /
  `setIdleEviction(false)` knob would work, but leaves a reachable
  configuration in which a stdio session gets evicted, and a
  "disabled" state for every future reader to reason about. Moving the
  schedule call to the transport that needs it makes the invariant
  hold by construction, which is what lets `dispatch` assert on it.
- **Pings would not have saved it.** `StdioMCPServer.dispatch` answers
  `ping` from `handler.dispatchPing()` without touching
  `currentSession`, so keep-alive pings never refresh
  `lastAccessNanos`. Worth knowing before anyone proposes "just have
  the client ping" as the fix.

**Alternatives considered.**
- **Configurable/injectable idle timeout on `MCPHandler`.** Rejected
  as the primary fix, for the "structural beats configurable" reason
  above. Also rejected as a *test* seam: `SessionCleanupTest` already
  drives the 30-minute path in milliseconds by backdating
  `setLastAccessNanos` and calling `cleanupIdleSessions()` directly.
- **Make stdio recover instead: re-initialize and replay on a closed
  session.** Rejected as the fix, though it was the obvious defensive
  move. It papers over an eviction that should not happen, and would
  silently discard session-scoped state (DR-forwarding-proxy's per-session upstream
  client) mid-conversation. The `IllegalStateException` guard is the
  same insight expressed as a loud invariant rather than quiet repair.
- **Refresh `lastAccessNanos` on `ping`.** Rejected — it makes the
  brick depend on client keep-alive behaviour we do not control, and
  leaves the 30-minute timer pointed at a session that should never
  expire at all.
- **Distinguish the three 404 wordings (unknown / idle-evicted /
  superseded).** Deferred, not rejected. The incident's message was
  accurate; it simply named a server nobody suspected. Splitting the
  wording is worthwhile diagnostics (see `PROXY-SESSION-BUG.md` §6),
  but it is a separate change and no longer load-bearing for this bug.
