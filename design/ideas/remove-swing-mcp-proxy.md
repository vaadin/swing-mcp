# Remove `swing-mcp-proxy`; register the in-process HTTP server directly

**Decided 2026-09-23 (owner):** remove the proxy. It never worked well, and the one thing it
still buys is cheap to live without. If losing it bites, we revisit the whole idea rather than
patch around it. This file tracks the removal until it graduates.

## Why

The proxy is a stdio process that Claude Code spawns. It answers `tools/list` from a static
manifest and forwards each `tools/call` to the in-process server over loopback. It was built on
two assumptions: an HTTP server that is down when Claude Code starts is dropped for the session,
and an app restart kills the HTTP session with no way back. An experiment tested both against a
real client. Only the first holds, and for that one `/mcp` → Reconnect is enough.

What the proxy costs:
- **Code and tests:** the module and its 450 KB fat jar. It is the only production user of
  `StdioMCPServer`, `MCPProxy` and `ProxyMessages`.
- **Machinery:** the drift probe, and the `swing-mcp-tool-defs` module, which exists only because
  the proxy must not depend on `swing-mcp`.
- **Bugs:** the stdio self-eviction bug (tiny-mcp-server's idle-cleanup entry, "Why is idle
  cleanup scheduled by the HTTP transport rather than by the handler?").
- **Setup:** a second artifact to build, register and keep in version step.
- **Weight in the host JVM:** the agent fat jar ships `MCPProxy`, `StdioMCPServer` and
  `TinyMCPClient` into the host application's JVM, which works against the "as few runtime
  dependencies as it can" promise.

## Evidence — Claude Code 2.1.280 against direct HTTP, 2026-09-23

Setup: the CRUD testapp (`../swingbridge-emulators/testapps/crud/swing`, `./mvnw -C package`),
launched as `xvfb-run -a java -javaagent:swing-mcp-agent.jar -jar
target/testapp-crud-swing-1.0-SNAPSHOT.jar`. The client was a child `claude -p --mcp-config
<{"mcpServers":{"swing-http":{"type":"http","url":"http://127.0.0.1:18088/mcp"}}}>
--strict-mcp-config` that started and stopped the app itself through Bash in the middle of the
session. The server's JUL log was the ground truth for what the client actually sent.

| Case | Outcome |
|---|---|
| **App restarts mid-session** | Recovers on its own. The next `swing_snapshot` succeeded with no error the model could see. The log shows the old session id rejected with a 404 (`unknown Mcp-Session-Id`), then a fresh `initialize`. |
| **App stops, then starts again** | The tools stay listed. While the app was down each call returned `isError` with `ECONNREFUSED: Unable to connect. Is the computer able to access the url?`. The first call after it came back succeeded. |
| **App not running when Claude Code starts** | The server reports `status: failed` at init and its tools are absent all session. ToolSearch said "configured MCP servers failed to connect … swing-http (ECONNREFUSED)". In 90 s after the app came up the client sent **zero** requests, so it never retries on its own. |
| **Same, interactive, with `/mcp` → Reconnect** (checked by the owner) | Reconnect brought the tools in and `swing_snapshot` worked, with no Claude Code restart. |

So tiny-mcp-server's research entry "Claude Code drops an MCP server that errors on
`tools/list`" is half right. A server that fails at *connect* is dropped for the session, but
`/mcp` Reconnect recovers it. The restart case needs nothing at all.

## Removal checklist

swing-mcp product:
- [ ] Delete `swing-mcp-proxy/`. Remove it from `settings.gradle.kts`; its `configureMavenCentral`
      goes with the module.
- [ ] `README.md`: the module list, and "Registering with Claude Code" → direct HTTP only, plus
      "start the app first, or run `/mcp` → Reconnect once it is up".
- [ ] `AGENTS.md`: drop the "`swing-mcp-proxy` must not depend on `swing-mcp`" invariant and the
      module-map line. Reword the `ToolDescriptor` invariant, whose "so the two transports cannot
      drift" no longer applies. Reword the `tiny-mcp-server` line ("the proxy machinery").
- [ ] `design/architecture.md`: the dependency-direction bullet.
- [ ] **Fold `swing-mcp-tool-defs` back into `swing-mcp`** (owner, 2026-09-23). Its only reason to
      be a separate module was that the proxy must not depend on `swing-mcp`.
      - Move `SwingTools` and `ToolDescriptor` into `swing-mcp`.
      - Drop the module from `settings.gradle.kts`, along with its `configureMavenCentral`, and
        remove the `api(project(":swing-mcp-tool-defs"))` line.
      - Update the module lists in `README.md` and `AGENTS.md`.
      - Keep the binding: the final descriptor-backed accessors on `AbstractSwingTool` and
        `SwingToolsCoherenceTest`.
      - Whether descriptors should then move into each tool is a separate question, not this
        removal's.
- [ ] `design/decisions.md`:
  - rewrite `D_shared_tool_manifest`. Drop "Why a separate module", reword "Why a developer-time
    test as well as the runtime probe" now that no runtime probe exists, and keep what still
    holds: a tool cannot disagree with the manifest;
  - fix `D_java11_floor`'s "Why 11 and not 8" so it stays true. The floor stays at 11 (owner,
    2026-09-23). After the removal, `java.net.http.HttpClient` is used only by `TinyMCPClient`,
    which is test-only in practice but sits in tiny-mcp-server's main source set. Measured
    blockers are in `design/ideas/java-8-floor.md`. Don't link the idea from the entry, since the
    idea is temporary;
  - add a new `D_` entry: why direct HTTP and no stdio proxy. It records what the proxy bought,
    what it cost, and the evidence above, and says to revisit if losing the proxy bites. It
    should also say why Claude Code's silent re-initialize after a restart is safe for us, even
    though tiny-mcp-server's no-auto-retry entry calls exactly that a lie: the restarted app
    starts with an empty ref map. A ref held from before the restart is refused rather than
    landing on the wrong widget, and the model re-snapshots.
- [ ] `design/research.md`: a new `R_` entry holding the evidence table above and the
      reproduction recipe (owner, 2026-09-23). The fact is about Claude Code, and after the removal
      the decision that rests on it is swing-mcp's. tiny-mcp-server's entry on Claude Code and
      `tools/list` goes with the proxy.
- [ ] `SwingTools.SESSION_LOST_MESSAGE`: only the proxy used it. Its javadoc claims the in-process
      server emits it too, but nothing in `swing-mcp/src/main` references it. Delete it.

tiny-mcp-server product (its own design layer; cite across the boundary by path, not slug):
- [ ] **Delete `MCPProxy`, `ProxyMessages` and `MCPProxyTest`** (owner, 2026-09-23). Rebuilding a
      proxy from scratch is cheap, and so is re-measuring the client with the recipe in the
      evidence section, so keeping unused code buys nothing. Pre-1.0 every consumer is in this
      repository, and the code ships into the host JVM through the agent jar.
      - Delete: the forwarding-proxy entry; its research entry on Claude Code and `tools/list`;
        the `ProxyMessages` invariant in `tiny-mcp-server/AGENTS.md`; the "first `tools/call`
        through `MCPProxy`" flow in its `architecture.md`; its `README.md` section.
      - Rewrite: the embedded-client entry, which opens "`MCPProxy` needs to call an upstream MCP
        server". The client stays; its reason becomes being the test client that also runs on
        Java 11. Its "Why HTTP only, with no stdio client" paragraph argues from the proxy's
        stdio-to-HTTP shape, so it needs a new argument or deletion.
      - The new swing-mcp `D_` entry carries the why-not, so the forwarding-proxy entry needs no
        successor note. The reproduction recipe must survive in the new `R_` entry, since
        "re-measure it" is the answer to "should we bring the proxy back?".
- [ ] **Keep `MCPHandler.setOnSessionStarted` and `setOnSessionClosed` for now** (owner,
      2026-09-23). After the proxy goes they have no production caller, but the session-lifecycle
      tests observe through them, so removing them is its own job:
      `design/ideas/remove-session-hooks.md`. For this removal, only make the settable-listeners
      entry ("Why are the session listeners setters that lock…") truthful:
      - its "Why setters at all" is `MCPProxy.newHandler`;
      - its "Why `onSessionStarted` exists" says `MCPProxy` allocates per-session state there.
      Both need to say what is true once the proxy is gone, without linking the idea file.
- [ ] The structural-equality entry ("Why does `InputSchema` implement structural equality…"):
      **keep the code**. `SwingToolsCoherenceTest` compares `ToolDescriptor`s, and so
      `InputSchema`s, with `assertEquals`. Rewrite the entry's "why", which currently cites the
      drift probe.
- [ ] **Keep `StdioMCPServer`** (owner, 2026-09-23). It is a generic transport and table stakes
      for an MCP server library. Rewrite the docs that lean on the proxy as its user: the
      stdio-transport entry ("Why a second transport rather than HTTP for everything?") needs a
      user story without the proxy, and the idle-cleanup entry tells its incident as "a proxy
      process sat idle…". That reasoning holds for any stdio server, so only the wording changes.
- [ ] **Delete `AutoRetryMCPClient`, `MCPClient.autoRetry()` and `AutoRetryMCPClientTest`**
      (owner, 2026-09-23). They have no consumer, and an opt-in silent re-initialize undercuts the
      no-auto-retry entry's own argument. `MCPSessionLostException` stays: `TinyMCPClient` throws
      it and `SessionSupersedeTest` asserts it. Shrink the no-auto-retry entry ("Why does a lost
      session surface as an exception rather than being retried?") to its core, so the client
      throws and never retries. Drop its decorator, default-method and proxy paragraphs, and the
      javadoc links to `autoRetry` in `MCPClient`, `TinyMCPClient` and `MCPSessionLostException`.

Checks: `design/verify_design_tripwires.sh`, `tiny-mcp-server/design/verify_design_tripwires.sh`,
`xvfb-run -a ./gradlew`.

## Open questions

None left. Every question was answered on 2026-09-23, and each answer sits in its checklist item
above. The Java-floor question moved to `design/ideas/java-8-floor.md`.

## Found on the way — not blocking the removal

Direct HTTP makes Claude Code's reconnect the normal path, so these matter more than before:

- **Supersede churn on reconnect.** After a 404, Claude Code seems to reconnect twice at once. The
  log shows `initialize` A, then B superseding A, then a request on A rejected, then C superseding
  B. It settled every time. Still, under `D_single_session`'s "newcomer wins", a tool call could
  land on a just-superseded session and get the superseded message. Worth a look, possibly a
  separate idea.
- **`server/discover`.** Before `initialize`, Claude Code 2.1.280 POSTs `server/discover` with no
  session header. We reject it (`Rejecting 'server/discover': no Mcp-Session-Id header`) and it
  falls back to `initialize`. Harmless today, but it looks like a newer protocol method. Find out
  what it is, and whether "method not found" would be the more correct answer.
