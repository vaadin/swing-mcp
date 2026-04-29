# Project Context

`swing-mcp-proxy` is a **standalone stdio MCP server** that
Claude Code (or any MCP client that launches its servers as
subprocesses) spawns and talks to over `System.in` / `System.out`.
On every `tools/call` it forwards the request to a separately
running, in-process `SwingMCP` HTTP server hosted inside the
customer's Swing application.

## 1. Problem

The Swing MCP project has two opposing transport requirements:

- **`SwingMCP` must run in-process** with the customer's Swing
  application: that's the only way it can read live components
  off the EDT and dispatch `swing_click`-style mutations through
  the real listener tree. Stdio is impossible there — the host
  app already owns `System.out`.
- **Claude Code (and most MCP clients) launch their MCP servers
  as subprocesses** over stdio. They have no first-class way to
  attach to a long-running HTTP MCP server, and even if they did,
  the user would have to coordinate the port at every session.

The proxy is the bridge: a stdio process Claude Code spawns at
will, that forwards every `tools/call` over loopback HTTP to the
embedded `SwingMCP`.

## 2. Vision — the proxy *is* Swing MCP

From Claude's perspective, **`swing-mcp-proxy` is Swing MCP.**
Claude sees one MCP server, named "Swing MCP", with one set of
tools. The word "proxy" never appears in any LLM-facing string.

This is enforced structurally: server identity (`SERVER_NAME`,
`SERVER_VERSION`, `INSTRUCTIONS`) and the tool manifest live in
`swing-mcp-tool-defs`, and **both** the in-process `SwingMCP` and
this proxy consume the same constants. Whatever Claude sees from
either transport is bit-identical.

"Proxy" is internal vocabulary only — it appears in specs, in
code (`MCPProxy`, `swing-mcp-proxy`), in JUL log lines on stderr,
and nowhere else.

User-facing diagnostic strings distinguish the **two failure
diagnostics** the human operator might face:

- "Swing application" — when the operator needs to start or
  restart the Swing app itself (session-lost cases).
- "Swing MCP Agent at `<URL>`" — when the operator needs to
  verify the agent is actually loaded into the running app
  (connectivity cases). The URL is shown verbatim so the
  operator can tell "app running but agent not loaded" apart from
  "app not running at all."

## 3. Users

- **Direct caller:** Claude Code (or any MCP client) launching
  the proxy as a subprocess.
- **Indirect users:** the AI developer doing the Vaadin
  migration, and the human operator running the Swing
  application. The operator's only concern is that the Swing app
  is running with the `swing-mcp-agent` `-javaagent` loaded.

## 4. Scope

- **Tools only.** The proxy forwards `tools/list` and
  `tools/call`. `SwingMCP` registers no resources or prompts
  today, and the proxy does not synthesize any.
- **No tool implementation.** The proxy ships zero
  `ToolFunction` logic of its own; every call goes through the
  generic `MCPProxy` machinery (DR-012) to upstream.
- **No CLI parser.** Configuration is read from one system
  property and one environment variable. See `architecture.md`.
- **No telemetry / metrics hook in v1** (Sub-item 4). JUL on
  stderr is the only observability channel.

## 5. Launch model

Distributed as a self-contained fat jar via the Gradle Shadow
plugin (Q15, Q32b), published to Maven Central alongside the
`swing-mcp` artifact (Q32a). Same packaging story as
`swing-mcp-agent`. The jar's manifest declares
`Main-Class: com.vaadin.swingmcp.proxy.Main`; the user runs it
via `java -jar swing-mcp-proxy.jar` or through Claude Code's
`mcpServers` configuration entry.

The proxy and `swing-mcp` ship as a **versioned pair**. Drift
between them (Q4 / Q8) is a hard error at the first `tools/call`
of each session — see DR-012's drift policy. The user-facing
remediation tells the operator to restart the MCP server or the
Swing application so versions match.

## 6. Risks

- **Cold-start when upstream is down.** Claude dispatches
  `tools/list` at MCP-init time. `tools/list` must succeed even
  if the Swing app isn't running yet, otherwise Claude drops the
  MCP server entirely. Mitigated by `MCPProxy` answering
  `tools/list` from the static `swing-mcp-tool-defs` manifest
  (Q29 / DR-012). The first `tools/call` then returns a clear
  `isError` body explaining the situation.
- **Drift between proxy and upstream.** Mitigated by the
  symmetric drift probe at the first `tools/call` of each fresh
  upstream session (DR-012; equality semantics from DR-014).
  Detected drift caches a permanent `isError` for the session
  with a "do not retry" remediation.
- **Stale sessions / mid-call upstream death.** Mitigated by
  surfacing `MCPSessionLostException` and mid-call `IOException`
  to the LLM verbatim (DR-008's "no auto-retry" rule). The
  shared `SESSION_LOST_MESSAGE` constant ensures both transports
  emit identical wording.
- **Stdout collisions.** Stdio framing owns `System.out`
  (DR-007). The proxy's own logging goes to stderr via JUL.
  Tools the proxy forwards to never run in this JVM, so
  application-side prints can't reach this process's stdout.

## 7. Constraints

- Depends on `tiny-mcp-server` (transport, client, `MCPProxy`)
  and `swing-mcp-tool-defs` (server identity + tool manifest).
  **Does not depend on `swing-mcp`** (Q32) — the proxy must not
  drag Swing classes into the standalone JVM.
- Java 17+ minimum runtime (matches the rest of the project).
- Bind upstream connection to `127.0.0.1` only.

---

# Related Documents

- [Architecture](architecture.md) — Main class, port/env config, fat-jar packaging, logging, shutdown hook
- [`tiny-mcp-server/spec/decisions.md`](../../tiny-mcp-server/spec/decisions.md) — DR-007 (stdio), DR-008 (client), DR-012 (`MCPProxy`), DR-013 (handler / descriptor / `_meta`), DR-014 (schema equality)
- [`swing-mcp-tool-defs/spec/project-context.md`](../../swing-mcp-tool-defs/spec/project-context.md) — shared server identity + tool manifest
