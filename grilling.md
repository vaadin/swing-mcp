# Swing MCP Proxy — design grilling checkpoint

Captured 2026-04-29 mid-conversation. Resume from "Open items below"; everything above is locked.

---

## What we're designing

A new **stdio MCP server** that Claude Code launches as a subprocess. It forwards `tools/call` over HTTP to a separately-running in-process `SwingMCP`. Tool list answers from a static manifest because upstream may not be running yet. Friendly `isError` if upstream is down.

Mid-conversation refactor: the proxy machinery (lazy init, drift probe, error taxonomy, per-session state) is **generic** and was promoted from a swing-specific module into `tiny-mcp-server`. swing-mcp-proxy is now just a thin wire-up.

---

## Final shape (locked)

### New subprojects

- **`swing-mcp-tool-defs`** — single source of truth for the Swing tool manifest. Pure data (Java code, no resources). Depends only on `tiny-mcp-server`. No Swing classes.
  - Exports `public static final List<ToolDescriptor> ALL` and per-tool constants like `SwingTools.SWING_CLICK`.
- **`swing-mcp-proxy`** — stdio MCP server. Depends on `tiny-mcp-server` and `swing-mcp-tool-defs`. **Does not depend on `swing-mcp`.** Fat-jar packaged via shadow plugin (already used by `swing-mcp-agent`).

### Promoted into `tiny-mcp-server`

- **`ToolDescriptor`** record — lives in `com.vaadin.swingmcp` (parent package, currently empty). Carries `(name, description, InputSchema)`. Distinct from wire-shape POJO `MCPProtocol.Tool`.
  - Note: package `com.vaadin.swingmcp` will be renamed in the future. Out of scope for now.
- **`MCPHandler.addTool(ToolDescriptor, ToolFunction)`** overload delegating to the existing 4-arg form.
- **`MCPProxy.newHandler(List<ToolDescriptor>, Supplier<MCPClient>, ProxyMessages)`** — static factory returning a fully-wired `MCPHandler`. Caller wraps in `StdioMCPServer`. No `MCPProxy` instance to hold.
- **`ProxyMessages`** record parameterizing the human-readable error text: `(String proxyName, String upstreamName)` with sensible defaults.
- **`MCPHandler` listener refactor**: `acceptNewSession`, `onSessionStarted` (new), `onSessionClosed` move from constructor into setters. **Settable until first session opens; throws `IllegalStateException` on later set.** Sensible no-op defaults so unset listeners still produce a working handler. Supersedes the recent "non-null in ctor" tightening.
- **`MCPClient.callTool` overload** accepting `JsonObject _meta` for proxy passthrough (DR-009 motivation; the existing two-arg form delegates with `null`).
- **`MCPProtocol.InputSchema.equals/hashCode`** — structural deep-equal: `type`, `properties` map (per-property name+type+description+enum+all texts), `required` as a set, any extension fields. Order-insensitive.

### Configuration

- **Port**: system property `swing.mcp.port` (matches `swing-mcp-agent`'s convention) **or** env var `SWING_MCP_PORT`. System property wins on conflict.
- **Host**: `127.0.0.1`, hardcoded.
- **Context path**: `/mcp`, hardcoded (matches `HttpMCPServer.DEFAULT_CONTEXT_PATH`).
- **Default port**: `18088` (matches `HttpMCPServer.DEFAULT_PORT`).
- **No CLI parser.**

### Lifecycle

```
proxy main:
  handler = MCPProxy.newHandler(SwingTools.ALL, () -> new TinyMCPClient(uri), proxyMessages)
  stdio   = new StdioMCPServer(handler)
  Runtime.getRuntime().addShutdownHook(... close upstream client idempotently ...)
  stdio.runStdio(System.in, System.out)  // blocks until EOF
```

Inside `MCPProxy.newHandler`:

- **`onSessionStarted`** — creates per-session state object (`upstream = clientSupplier.get()`, `initialized = false`, `driftFailure = null`). Stashes on `MCPSession.attributes` (the bag already exists, line 31 of `MCPSession.java`).
- **N forwarding `ToolFunction`s** — one registered per descriptor; all share an implementation that:
  1. Reads per-session state from the session attribute bag.
  2. If `state.driftFailure != null` → return cached drift `isError`. (Permanent for the session.)
  3. If not yet initialized → lazy init upstream:
     - `IOException` → return "Swing app isn't running, start it and retry" `isError`. **Do not** mark permanently dead; next call retries init from scratch.
     - On success → run **drift probe**: `client.listTools()` deep-compared against the descriptor list. Mismatch → cache as `state.driftFailure` and return drift `isError`.
  4. Forward `client.callTool(req.name(), req.arguments(), req.jsonRpcMeta())`.
     - `MCPSessionLostException` → return DR-008 message ("Swing app session was lost — call `swing_snapshot` to re-orient and retry"). **Surface, don't auto re-init.** Reset `state.initialized = false`; next call walks lazy init.
     - `IOException` mid-call → return "Swing app went away mid-call — may or may not have completed; call `swing_snapshot` to verify." Reset `state.initialized = false`.
     - `MCPClientException` → forward upstream's JSON-RPC error message verbatim as `isError`.
     - `CallToolResult.isError == true` → forward `Content` and `isError` flag verbatim.
- **`onSessionClosed`** — best-effort `state.upstream.close()`, swallow `IOException`. Idempotent with the shutdown hook.

### Drift policy

- **Hard-fail on any difference**: name, description text, input schema. Description drift fails too (proxy + swing-mcp ship as a versioned pair).
- **Probe runs at first successful upstream `initialize` per fresh session.** First `tools/call` after each fresh upstream session pays 3 localhost round-trips: initialize + listTools probe + actual forward. Acceptable.
- **Cache shape**: drift result lives on per-session state. Re-init / `MCPSessionLostException` clears the session and supplier produces a new client → new probe.
- **`tools/list` always answered from the static manifest** (Q29 — pending confirm), even after a successful probe. Manifest is the contract; drift detection happens on call, not on list.
- **Drift message** (Q12): names the offending tool + field + remediation:
  ```
  swing-mcp-proxy is out of sync with the running Swing app
  (tool 'swing_click' differs in <field>). The user must restart
  the MCP server with a proxy version matching the Swing app. Do not retry.
  ```
  Unlike session-lost errors, this is **not** recoverable from inside the conversation; "Do not retry" tells the LLM to stop looping.

### Logging

- JUL, WARNING by default. stderr (stdout is reserved for protocol). No config knob in v1.
- Stdout collisions handled the existing `StdioMCPServer` way (DR-007).

### `AbstractSwingTool` refactor

- Constructor takes a `ToolDescriptor` (the matching `SwingTools.SWING_*` constant).
- `getName/getDescription/getInputSchema` become `final`, delegating to the descriptor.
- Subclasses bind to a descriptor at construction; can't drift from the manifest by accident.

---

## Decision log Q1–Q28

| # | Topic | Locked decision |
|---|-------|-----------------|
| Q1 | Manifest location | In `swing-mcp-tool-defs`, Java code, single source of truth (no resources/JSON files). |
| Q2 | Descriptor binding | `SwingToolDescriptor` is a record; `SwingTools` exports per-tool constants; `AbstractSwingTool` ctor takes a descriptor; subclasses pass constants. |
| Q3 | Descriptor type | Record. |
| Q4 | Drift policy | Probe + hard-fail on **any** difference (name, description, schema). |
| Q5 | Drift cache lifetime | Cache for proxy lifetime; **re-probe on each fresh upstream session**. |
| Q6 | Failure manifestation | `tools/call` returns `isError` until proxy process restart. Don't exit JVM (Claude Code couldn't tell why). Tools still listed. |
| Q7 | Schema-equality predicate | Structural deep-equal. Bake `equals/hashCode` into `MCPProtocol.InputSchema`. |
| Q8 | Description strictness | Hard-fail on description drift confirmed. Proxy + swing-mcp ship as a versioned pair. |
| Q9 | Lazy-init flow & error taxonomy | See "Lifecycle" above. **Surface session-lost, don't auto re-init.** Use "may or may not have completed" wording for `IOException` mid-call. |
| Q10 | `_meta` passthrough | Extend `MCPClient.callTool` now to accept `_meta`. Two-arg form delegates with `null`. |
| Q11 | Re-`initialize` from Claude Code | Fresh start: `onSessionClosed` closes upstream; supplier produces new client; refs gone. Consistent with DR-008. |
| Q12 | Drift error wording | Names tool + field + remediation. "Do not retry." See snippet above. |
| Q13 | Port/host config | System property `swing.mcp.port` (or env var `SWING_MCP_PORT`); system property wins. Host `127.0.0.1`. Context path `/mcp`. No CLI parser. |
| Q14 | 3 round-trips on first call | Accepted as the price of the protocol. |
| Q15 | Packaging | Fat jar via shadow plugin. Same approach as `swing-mcp-agent`. |
| Q16 | Logging | WARNING by default, JUL → stderr, no config knob in v1. |
| Q17 | stdin EOF handling | In-flight calls finish naturally before EOF is observed; plus JVM shutdown hook for SIGTERM. Idempotent close on upstream client. |
| Q22 | `MCPHandler` listener API | Settable until first session opens, then locked. Throws `IllegalStateException` on later set. |
| Q23 | `ToolDescriptor` package | `com.vaadin.swingmcp` (parent, currently empty). Renaming the package is a separate future task. |
| Q24 | `MCPProxy` API | Static factory `MCPProxy.newHandler(descriptors, Supplier<MCPClient>, ProxyMessages)` returning configured `MCPHandler`. No instance to hold. State on `MCPSession.attributes`. |
| Q25 | Client construction | `Supplier<MCPClient>` — fresh client per session. Test-friendly. |
| Q26 | Drift message parameterization | `ProxyMessages(String proxyName, String upstreamName)` record with sensible defaults. |
| Q27 | Per-session state lifecycle | Created in `onSessionStarted`, stashed on session attribute bag, used by forwarding lambda, closed in `onSessionClosed`. (Implicitly accepted by Q24+Q25.) |
| Q28 | `InputSchema.equals` semantics | Deep structural; all text fields compared; sets (`required`, `properties` keys) compared as sets; field order insensitive. |

---

## Open items (resume here)

### Q29 — `tools/list` provenance

Even after a successful probe, does the proxy still answer `tools/list` from its static manifest?

- **(I) Always manifest** — source of truth is the descriptor list; drift detected at probe, not on list. *My push.*
- (II) Manifest until probed, then upstream — introduces a behavior boundary at probe time.
- (III) Always upstream when reachable, manifest as fallback — defeats the static-manifest design.

### Q30 — listener defaults

With Q22's settable-until-locked accessors, what's the *initial* value before any setter is called?

- **(A) Sensible no-op defaults** (`acceptNewSession = c -> true`, `onSessionStarted = s -> {}`, `onSessionClosed = s -> {}`). *My push.*
- (B) Null until set; first `initialize` throws if any listener is null. Mirrors the recent "non-null in ctor" commit's spirit but annoys callers.

### Q31 — `SwingTools` shape

Constant or method?

- **`public static final List<ToolDescriptor> ALL = List.of(...)`** — *my push.*
- `public static List<ToolDescriptor> all()`.

### Q32 — gradle layout

`settings.gradle.kts` adds `swing-mcp-tool-defs` and `swing-mcp-proxy`. Dependency edges:

- `swing-mcp-tool-defs` → `tiny-mcp-server`
- `swing-mcp` → `swing-mcp-tool-defs`
- `swing-mcp-proxy` → `tiny-mcp-server` + `swing-mcp-tool-defs`. **NOT** `swing-mcp`.

Shadow plugin in `swing-mcp-proxy/build.gradle.kts` (and already in `swing-mcp-agent/build.gradle.kts`).

Confirm.

### Q33 — specs to write (in this order)

1. **`tiny-mcp-server/spec/decisions.md` — new DRs.** Generic machinery is load-bearing; nail it down first.
   - **DR-012** (or next free number): generic `MCPProxy.newHandler` machinery — lazy upstream init, `tools/list` from descriptors only, drift probe with hard-fail, per-session state via attribute bag, `Supplier<MCPClient>`, `ProxyMessages`.
   - **DR-013**: `ToolDescriptor` promoted to `com.vaadin.swingmcp`; `MCPHandler.addTool(ToolDescriptor, ToolFunction)` overload.
   - **DR-014**: `MCPHandler` listener accessors (settable until locked, sensible defaults). Supersedes the recent "non-null in ctor" commit.
   - **DR-015**: `MCPClient.callTool` overload accepting `_meta`.
   - **DR-016**: `MCPProtocol.InputSchema` structural `equals/hashCode`.
2. **`tiny-mcp-server/spec/architecture.md`** updated for `MCPProxy`, new `ToolDescriptor` location, listener accessors.
3. **`tiny-mcp-server/spec/project-context.md`** — proxy use case is now generic, not Swing-specific.
4. **`swing-mcp-tool-defs/spec/project-context.md`** — short. Manifest as single source of truth; consumed by both swing-mcp and swing-mcp-proxy.
   - No `tools/`, no `architecture.md` — pure data.
5. **`swing-mcp-proxy/spec/project-context.md`** — vision, problem, users, scope (tools-only), risks (drift, cold-start when upstream down), launch model (Claude Code subprocess via fat jar).
6. **`swing-mcp-proxy/spec/architecture.md`** — main, fat-jar, port/env config, shutdown hook, logging.
   - No `decisions.md` for swing-mcp-proxy — all cross-cutting decisions are generic and live in tiny-mcp-server.
7. **`swing-mcp/spec/`** — small note that `AbstractSwingTool` now binds to `ToolDescriptor` from the new module. Probably one paragraph in architecture.md, no new DR.

### Sub-items not yet asked

- **Drift error field-level granularity.** Does the message say `"differs in description"` or include diffs (`"differs in description: 'old' vs 'new'"`)? Probably names + field name only — diff is verbose for a one-line `isError`.
- **Probe symmetry.** Strict equality means **extra tools on either side fails**. Worth pinning explicitly in the spec.
- **`Supplier<MCPClient>.get()` itself throwing.** Almost certainly env misconfig (bad URI). My push: non-recoverable — `RuntimeException` from `main`, JVM exits, Claude Code sees broken pipe.
- **Telemetry hook for the proxy.** Out of scope unless raised.

---

## Codebase facts established during exploration

- `SwingMCP` registers **only tools** (no resources, no prompts). Proxy is tools-only.
- Defaults from `tiny-mcp-server`: port `18088`, context path `/mcp`, host `127.0.0.1`. Surfaced via `HttpMCPServer.DEFAULT_PORT` and `HttpMCPServer.DEFAULT_CONTEXT_PATH`.
- `MCPProtocol.InputSchema` lives in `tiny-mcp-server` — natural dep target for `swing-mcp-tool-defs`.
- `MCPSession.attributes` already exists (line 31 of `MCPSession.java`) with `getAttribute/setAttribute` — proxy state has a natural home, no external session→state map needed.
- `MCPHandler` has 3 ctor overloads currently (0-arg, 2-arg, 4-arg). Adding `onSessionStarted` would push to a 5-arg overload — confirms the listener-refactor instinct.
- Recent commit `b128690` "Require non-null acceptNewSession / onSessionClosed in MCPHandler" — Q22 walks this back; **DR-014 supersedes it**.
- `swing-mcp-agent` is a `-javaagent` premain that uses `swing.mcp.port` system property. Proxy reuses the same property name (different JVM, no clash).
- `AbstractSwingTool` (in `swing-mcp/src/main/java/com/vaadin/swingmcp/mcp/tools/AbstractSwingTool.java`) currently declares `getName/getDescription/getInputSchema` abstractly per-subclass. Refactor: ctor takes `ToolDescriptor`, those methods become `final` delegators. Subclasses are constructed with `super(SwingTools.SWING_CLICK)` etc.
- `SwingMCP.registerTools()` (line 88+) instantiates 24 tool subclasses. Each will need ctor adjustment when refactored.
- The proxy use case is currently documented across DR-007 (stdio), DR-008 (HTTP client), DR-009 (request records) but **not** as its own spec. DR-012 will be its dedicated home.

---

## Resume instructions

If picking up cold:

1. Re-read this file end-to-end.
2. Confirm Q29–Q33 plus the four sub-items above. (Roughly: my pushes are likely all yes; the user may push back on Q33's ordering or want to combine some DRs.)
3. Once locked, write specs in the Q33 order. Spec-first per `CLAUDE.md` workflow.
4. Implementation order then mirrors the spec order: tiny-mcp-server changes first (`InputSchema.equals`, listener accessors, `_meta` overload, `ToolDescriptor`, `MCPProxy`), then `swing-mcp-tool-defs` content, then `swing-mcp` refactor, then `swing-mcp-proxy` wire-up + fat jar.
5. Tests: there's already a "loopback proxy test" pattern in `tiny-mcp-server/spec/architecture.md:232` (stdio server wrapping HTTP client through `TinyMCPClient`). The new `MCPProxy.newHandler` machinery should get its own integration test in `tiny-mcp-server` covering: down-then-up, drift detection, session-lost mid-call, `IOException` mid-call, `_meta` passthrough.
