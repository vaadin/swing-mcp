# Decisions

Why this server is the way it is and not otherwise — FAQ-shaped: each entry is a question and
its current answer. Rewrite the answer when it changes; delete the entry when nobody asks any
more. An entry is earned by what it would cost to reverse, or by research the next person would
otherwise redo (cited as its `R_`). Not an entry: a method name, the testing library, a version
bump, which logger — a comment at the site of the choice, or nothing; nothing about `design/`
itself. Cite by slug, `D_<slug>`, never by position; `grep '^## D_' design/decisions.md` is the
index. The first entry is the ruler: every later one trims to its length. When you have written
an entry, re-read it against the one above, check it says nothing the doc comments already say,
and cut what is left over.

---

## D_no_framework_deps — Why hand-roll MCP rather than use the official Java SDK?

This server is embedded as a jar into a host application and shares its JVM and its
classloader, so every runtime dependency it carries is one the host may already have at a
different version. `MCPProtocol` maps the wire format through plain GSON-bound POJOs; the
runtime classpath is GSON and its annotations jar, nothing else. Why not
`io.modelcontextprotocol.sdk:mcp`: it brings Reactor and two Jackson lineages at once
(`R_mcp_sdk_deps`) — a clash surface no host should be asked to absorb. Why not isolate it
behind a child classloader instead: that buys dependency safety at the price of a second loader
living inside someone else's application, which is harder to explain and to support than a
hand-written parser. Quarkus MCP and Spring AI lose on footprint too, and additionally want a
bootstrap that a plain `main()` does not have. The SDK stays a `testImplementation`, so the
tests drive this server through a real MCP client. The cost we carry: protocol revisions are
ours to follow by hand.

## D_localhost_no_auth — Why bind the HTTP transport to 127.0.0.1 with no authentication?

The HTTP transport exists for the in-process case: a developer's own machine, a server embedded
in an application that developer is running, reached by a tool on the same host. Binding to
`127.0.0.1` makes the operating system the access control, and the developer who owns the
machine owns its security. Why not bind `0.0.0.0` and add authentication: it converts a
single-machine tool into a network service, which means credentials to store, rotate and
support, for a reach nothing currently needs. There is deliberately no port discovery either —
one host application per machine is the normal case, and a second one changes the port. The
cost we carry: any future remote use case reopens this entry rather than adding a flag, because
a "just bind wider" option with the auth story unwritten is the shape this avoids.

## D_stdio_transport — Why a second transport rather than HTTP for everything?

The two transports serve two lifecycles that cannot borrow each other's. A server embedded in a
host application cannot use stdio, because the host owns stdout. A server an MCP client spawns
as a subprocess strongly prefers stdio, because there is no port for the two sides to agree on
and the pipe closing is a definitive death notice. `StdioMCPServer` reads newline-delimited
JSON-RPC (`R_mcp_stdio_framing`) and is single-session by construction. Why not a separate
server class: the registries, session machinery and dispatch are identical and only the framing
differs, so a second class would either duplicate them or grow a shared base — more churn than
a second transport shell over the same handler. Why not build stdio in the consuming module
instead: it would re-implement framing, routing and registries with GSON already on this
classpath. On entry `runStdio` re-points `System.out` at `System.err` and keeps the real stdout
in a private writer, so a stray print cannot corrupt the wire.

## D_handler_transport_split — Why is protocol dispatch split from transport, with the handler as the configuration object?

`MCPHandler` owns everything transport-agnostic — the registries, the session map, the shared
executor, JSON-RPC dispatch — and is also the only thing a caller configures. A transport is a
shell that takes a configured handler and adds framing: HTTP status codes and
`Mcp-Session-Id` for one, newline-delimited lines for the other. The split is what let stdio be
added without touching dispatch at all. Why not one class with both a `start()` and a
`runStdio()`: every stdio path would thread around header lookups and an HTTP exchange it has
no use for, and stdio's single-session simplicity would be coupled to the HTTP session map's
invariants. Why not have the transport extend the handler: composition — a second transport
composes the same handler rather than inheriting a first transport's surface. Why not keep
`addTool` as a convenience delegate on each transport: duplicate API for one saved method call,
and it hides which object is the configuration. Handler methods therefore return result POJOs
or throw; rendering is the transport's, which is `D_three_error_layers`.

## D_settable_listeners — Why are the session listeners setters that lock, rather than constructor parameters?

`setAcceptNewSession`, `setOnSessionStarted` and `setOnSessionClosed` are fluent setters with
no-op defaults, settable until the first session is accepted and throwing `IllegalStateException`
after. The shape exists for factories: `MCPProxy.newHandler` builds a handler and then layers
per-session lifecycle onto it, which a fat constructor cannot express without every caller
passing nulls. Why not constructor-only, which is what this replaced: it works for a caller
that knows all its callbacks up front and blocks every caller that does not. Why not leave the
setters open for the handler's whole life: listener semantics changing mid-flight is a footgun
— a session admitted under one policy and closed under another — and the lockdown costs one
boolean while preserving exactly the guarantee the constructor form was reaching for.
Pre-flight reconfiguration is the legitimate case; mid-flight is not.

## D_three_error_layers — Why three error layers rather than one?

A failure is one of three things and each wants a different client reaction. A dead socket
(`TransportIOException`) has nobody left to tell, so it is logged and abandoned. A protocol
error (`MCPServerException`, carrying both a JSON-RPC code and an HTTP status) renders as a
JSON-RPC error envelope the client SDK can parse. A tool that ran and failed semantically
returns a normal result with `isError: true` and a recovery hint the model can act on — and
that layer is tools-only because the specification gives no other result type the slot
(`R_mcp_iserror_tools_only`). Why not render everything as `isError`: it conflates "retry will
never help" with "fix your input", which is the one distinction a client needs. Why not use
HTTP status codes alone: MCP clients read the JSON-RPC envelope, so status-only errors break
their error paths. Why not write errors inline at each throw site: the mapping would scatter
across every handler, each having to remember its own HTTP status. One seam translates, in
`HttpMCPServer.handleRequest`; everything else throws.

## D_session_gate_two_stage — Why validate the session in two stages, around body parsing?

A POST is checked twice. Before parsing: if `Mcp-Session-Id` is present and names no live
session, return 404 immediately — every method is rejected, so the body is never read. After
parsing: if the method is neither `initialize` nor `ping` and the header was absent, return 400
using the parsed request id. The split follows from what the specification demands of each case
(`R_mcp_session_lifecycle`): 404 tells a client its id is stale, 400 tells it the id is
missing, and the two are distinguishable only if the second one can quote the client's own
request id back — which needs the parsed body. Why not one pre-parse check: the
not-initialized case has neither the method nor the id yet. Why not one post-parse check: it
pays full body parsing for requests already doomed by their header. Both use -32002, from the
implementation-defined range, because a session-state violation is a server-state condition and
not malformed input.

## D_session_policy_injected — Why is the session-admission policy injected rather than built in?

`MCPHandler` is multi-session, which is the specification's default, and takes a policy function
that sees the live sessions and returns reject, accept, or accept-and-evict. A caller that must
be single-session says so in one line. Why not bake single-session in: this server is meant to
be reused, and the reason its best-known consumer needs one session at a time — a single-threaded
UI toolkit that two agents would interleave clicks on — is that consumer's constraint, not the
protocol's. Baking it in would either push that assumption onto every reuser or make this
module's own multi-session tests fight their own server. Why not queue a blocked client until
the slot frees: it adds a waiting state for a case that does not arise, and an agent blocked
behind a wedged session waits forever. What replaced the wedging problem is `D_supersede_sessions`.

## D_supersede_sessions — Why does a new session supersede the existing one rather than being rejected?

The single-session policy used to reject a second `initialize` until the first session timed
out. Correct under clean shutdown, brittle in practice: a client process that exits without
sending DELETE locks the slot for the full idle window, and a crashing editor is the normal
case for the workflow this server supports. `AcceptAndEvict` inverts it — a fresh `initialize`
is the new ground truth, the displaced session is tombstoned, and its next call gets a 404
carrying the reason rather than a bare "session not found". Why the tombstone at all: "I am
talking to a stale id", "the server restarted" and "I was kicked" need different recoveries and
only the server knows which happened. Why the message deliberately omits the new session id:
naming it invites the displaced client to reattach, and two clients sharing one session produce
interleaved state changes that read as randomly dropped tool calls. Why not simply shorten the
idle timeout: it treats the symptom and starts evicting legitimately slow sessions. Eviction
runs outside the handler's guard lock, so closing a session can wait for its in-flight request
without pinning the handler.

## D_idle_eviction — Why evict idle sessions from a shared executor rather than per request?

`MCPHandler` owns one daemon `ScheduledExecutorService`; a tick once a minute evicts sessions
untouched for thirty minutes. Thirty matches "the agent walked away" without fighting a long
legitimate pause. The tick and live requests meet at one chokepoint, `MCPSession.runLocked`,
which refreshes the timestamp and fails fast on an already-closed session. Why the chokepoint
rather than the transport's entry path: anything that drives a session — tests included —
counts as activity, and "this session is alive" is recorded in exactly one place. Why
`tryLock` rather than `lock` in the sweep: a blocking acquire would stall the whole pass behind
one slow request, and could deadlock against a tool waiting on this same executor; skipping and
retrying next tick keeps cleanup bounded. Why share the executor with tool handlers rather than
run a second one: tools need somewhere to park short background work anyway, and a second
daemon thread buys nothing. Why not evict opportunistically on each request: it spreads the cost
over every call and does nothing for a server that has simply gone quiet, which is the whole case.

## D_stdio_never_evicts — Why is idle cleanup scheduled by the HTTP transport rather than by the handler?

`MCPHandler.start()` creates the executor and nothing else; only `HttpMCPServer.start()` asks
for the cleanup tick. Eviction exists for a failure mode stdio does not have — an HTTP server
cannot tell "client died" from "client went quiet", so it times out, whereas over stdio the
client *is* the process holding the pipe and EOF is a definitive death notice. Under stdio the
timer can therefore only destroy a live conversation, and unrecoverably: there is no session id
on the wire and no 404 to observe, so no client has any reason to re-initialize
(`R_mcp_stdio_framing`). A proxy process sat idle for thirty-two minutes, evicted its own stdio
session, and answered every later call with "Session expired" without a byte reaching upstream;
nothing short of restarting the client recovered it. Why not a configurable timeout or an
off switch: it leaves a reachable configuration in which this happens again, and a disabled
state for every later reader to reason about. Moving the call to the transport that needs it
makes the invariant hold by construction, which is what lets `StdioMCPServer.dispatch` assert
on it. Why not have the client ping to stay alive: `ping` is answered without touching the
session, so it never refreshes anything — worth knowing before anyone proposes it again.

## D_embedded_client — Why ship our own MCP client rather than reuse the SDK's?

`MCPProxy` needs to call an upstream MCP server, and the client is a package beside the server
in the same jar: same POJOs, same exception conventions, `java.net.http.HttpClient` underneath,
no new runtime dependency. Using the SDK's client here would undo `D_no_framework_deps` for the
one module whose reason to exist is not having those dependencies. Why a package rather than a
third Gradle module: client and server share `MCPProtocol` wholesale, and splitting them costs
more in coordination than it earns in cohesion — the module keeps the name `tiny-mcp-server`
and hosts both, which is a naming wart rather than a design one. The surface is `initialize`,
`listTools`, `callTool`, `close` and stops there; resources and prompts get added when a caller
asks, and none has. HTTP only — the proxy forwards from stdio to HTTP, so a stdio client would
exist purely to test the stdio server, which the loopback test does better.

## D_no_auto_retry — Why does a lost session surface as an exception rather than being retried?

`TinyMCPClient` throws `MCPSessionLostException` on a 404 and lets the caller decide; retry is
an opt-in decorator reached through `MCPClient.autoRetry()`. The reason is that MCP sessions can
hold state a fresh `initialize` silently discards — a consumer's per-session handle map being
the obvious case, where a replayed call would land on whatever object now holds that key, or on
none. For a stateful caller **failure is information**: a clear "your session is gone" beats a
call that succeeds against state nobody intended. So the proxy deliberately does not opt in; it
catches the exception and hands the model a recovery hint instead. Why a decorator rather than a
subclass or a static factory: any future `MCPClient` gets it for free and it chains with later
wrappers. Why not retry `IOException` too: the caller knows whether the operation is idempotent
and the client does not.

## D_request_records — Why do handler callbacks take a record rather than positional arguments?

`ToolFunction` and its siblings receive a record bundling the identity slot (tool name, prompt
name, resource URI) with the arguments, the transport headers and the JSON-RPC `_meta` object.
Two things needed it at once: one forwarding lambda registered once per upstream tool has to
know which tool was actually called, and `_meta` carries fields like `progressToken` that should
survive a hop through a proxy. Why not just add the name as a second parameter: cheaper now and
fragile immediately — `_meta` was the very next field, and each addition breaks every callback
signature again, whereas a record absorbs an optional field without touching a single call site.
Why not hand the callback the session and let it reach in: it couples handlers to internal
session state and invites reaching for things that ought to be explicit inputs; a thread-local
is the same problem plus trouble in tests. Resources use `uri` as the identity slot for symmetry,
which is worth one extra record.

## D_forwarding_proxy — Why does the proxy answer `tools/list` from a static manifest rather than from upstream?

`MCPProxy.newHandler(tools, upstreamUrl, messages)` returns a handler that answers `tools/list`
from the manifest it was given — always, even before upstream has ever been contacted, even
after a drift failure. The driver is that an MCP client dispatches `tools/list` once at startup
and drops a server that errors there (`R_claude_code_tools_list`), so a proxy that asks upstream
first disappears entirely whenever the host application is not running yet. Answering locally
keeps it registered, and the first `tools/call` returns a readable `isError` explaining what to
start. Once the manifest is static, drift between it and upstream becomes a real failure mode,
so the first call of each session probes `listTools()` and hard-fails symmetrically on any
difference — extra on either side, or the same name with different fields
(`D_structural_schema_equality`) — caching that as a permanent error for the session. Why not
soft-fail and forward anyway: a drifted manifest can mean different argument semantics, which
produces silently wrong results rather than a visible one. Why not an empty list after drift: a
client reads that as a broken server and stops calling. Why a static factory and no instance:
all state is per-session and lives on the session's attribute bag, so an instance would be empty.

## D_structural_schema_equality — Why does `InputSchema` implement structural equality rather than the proxy comparing fields?

Equality is a property of the type: two `InputSchema` values are equal when they describe the
same JSON Schema document modulo property order, with `required` compared as a set because that
is its JSON Schema semantics. `D_forwarding_proxy`'s drift probe is the consumer, and a bug in
the predicate would surface either as drift errors that are not drift, or — far worse — as
missed drift and silently wrong forwarded calls. Why not a comparator inside the proxy: the next
caller writes a second one, and a fix to either does not reach the other. Why not serialize both
and compare strings: GSON's property order is not part of any contract, so two equal schemas can
differ as text on different days. Why not reflective deep-equals from a utility library: it
pulls a dependency for one method and would compare `required` as an ordered list, which is the
one field where order is meaningless.

## D_vendored_namespace — Why does a host-agnostic server live under `com.vaadin.swingmcp`?

Because extraction is deferred, not because the coupling is real. This module is a separate
product — its own README, its own `design/`, its own release cadence — kept in this repository
for now so its consumer does not have to depend on an unproven external artifact. Nothing in the
source depends on the consumer: every mention of the host toolkit is a comment, a javadoc
example or a test fixture string. The package name is the one genuine tie, and `ToolDescriptor`
sitting in the shared parent package `com.vaadin.swingmcp` is the sharpest part of it. Why not
rename now: the rename is a breaking change for both consumers and buys nothing until the split
actually happens, so we pay it once, at the split. Why write it down rather than leave it: an
undocumented namespace mismatch reads as sloppiness and invites someone to "fix" it in the wrong
direction — by moving host-specific code *into* this package. The test that keeps this honest:
`git subtree split --prefix=tiny-mcp-server` must yield a complete repository, and nothing
inside it may point outward.
