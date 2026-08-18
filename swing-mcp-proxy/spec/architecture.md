# Architecture

`swing-mcp-proxy` is a thin wire-up around the generic `MCPProxy` machinery
in `tiny-mcp-server`. It contains essentially one class (`Main`) plus a
small set of message constants. All of the interesting behaviour — tool
forwarding, drift detection, error taxonomy, per-session state — lives in
`tiny-mcp-server`. See DR-forwarding-proxy / DR-settable-listeners /
DR-structural-schema-equality in `tiny-mcp-server/spec/decisions.md`.

---

## 1. Technology Stack

- Pure Java (Java 17+ runtime target, matching the rest of the
  project).
- Gradle, with the **Shadow plugin** for fat-jar packaging
  (Q15 / Q32b — same pattern already used by
  `swing-mcp-agent`).
- Runtime dependencies:
  - `tiny-mcp-server` — `MCPHandler`, `MCPProxy`,
    `StdioMCPServer`, `TinyMCPClient`, `ProxyMessages`,
    `ToolDescriptor`, `MCPProtocol`.
  - `swing-mcp-tool-defs` — server identity constants
    (`SERVER_NAME`, `SERVER_VERSION`, `INSTRUCTIONS`), the
    `SwingTools.ALL` tool manifest, and the shared
    `SESSION_LOST_MESSAGE`.
- **No dependency on `swing-mcp`** (Q32) — the proxy must not
  drag Swing classes into the standalone JVM.
- Logging: JDK built-in `java.util.logging` (JUL). No log
  framework dependencies.
- Testing: JUnit 6.

---

## 2. Application Structure

```
com.vaadin.swingmcp.proxy
  Main.java               - entry point; owns port resolution, ProxyMessages
                            construction, handler+transport wire-up, shutdown hook
```

That's it. No `tools/` package — the proxy forwards, it does not
implement (Q33d). No standalone protocol code — that all lives in
`tiny-mcp-server`.

### Main lifecycle

```java
public static void main(String[] args) {
    int port = resolvePort();                                 // (1)
    URI upstreamUrl = URI.create(
        "http://127.0.0.1:" + port + "/mcp");                 // (2)
    ProxyMessages messages = buildProxyMessages(upstreamUrl); // (3)
    MCPHandler handler = MCPProxy.newHandler(
        SwingTools.ALL, upstreamUrl, messages);               // (4)
    handler.setServerInfo(SERVER_NAME, SERVER_VERSION);       // (5)
    handler.setInstructions(INSTRUCTIONS);                    // (5)
    StdioMCPServer stdio = new StdioMCPServer(handler);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        // best-effort idempotent close (handler.stop() calls
        // onSessionClosed which closes the upstream client)
    }));

    stdio.runStdio(System.in, System.out);                    // (6) blocks until EOF
}
```

(Names of the `setServerInfo` / `setInstructions` accessors are
illustrative — they may already exist on `MCPHandler` or be added
incidentally with the DR-settable-listeners setter family.)

1. **Port resolution** (Q13). Read system property
   `swing.mcp.port`; if absent, read env var `SWING_MCP_PORT`; if
   absent, default to `18088`. System property wins on conflict.
   Parsing failure throws `NumberFormatException`, which exits
   the JVM with a JUL log to stderr before stdio is touched —
   Claude Code observes a broken pipe and surfaces a clear
   "MCP server failed to start" diagnostic to the user.
2. **Upstream URL.** Hardcoded `http://127.0.0.1:<port>/mcp` —
   loopback only (DR-localhost-http-no-auth), context path matches
   `HttpMCPServer.DEFAULT_CONTEXT_PATH`. URL construction fails
   fast at startup if the resolved port is malformed.
3. **`ProxyMessages` construction.** All four strings are
   pre-formatted at this point; `tiny-mcp-server` does no
   templating. URL interpolation happens here. See "Error
   messages" below.
4. **Handler factory call.** `MCPProxy.newHandler` returns a
   single-session handler with the forwarding lambdas already
   registered, the session-start / session-close listeners
   already wired, and `acceptNewSession = count -> count == 0`.
5. **Server-info + instructions.** Sourced from
   `swing-mcp-tool-defs` so the proxy presents bit-identical
   identity to whatever `SwingMCP` exposes over HTTP.
6. **Run stdio.** `runStdio` blocks the main thread, reads
   newline-delimited JSON-RPC from `stdin`, writes responses to
   `stdout`, and returns when `stdin` reaches EOF (Claude Code
   shut us down) or after a SIGTERM-driven shutdown hook.

### Configuration

| Property / variable | Purpose | Default |
|---|---|---|
| System property `swing.mcp.port` | Upstream `SwingMCP` port | `18088` |
| Env var `SWING_MCP_PORT` | Same, fallback if system property is absent | `18088` |

The system property name matches `swing-mcp-agent`'s convention
so a single `-Dswing.mcp.port=…` flag configures both the agent
(in the Swing JVM) and the proxy (in this JVM, if the user runs
it that way). They live in different JVMs, so there is no clash
even though the property name is identical.

Host is hardcoded to `127.0.0.1` (DR-localhost-http-no-auth). Context path is
hardcoded to `/mcp`. There is no CLI parser (Q13).

### Error messages

`ProxyMessages` is constructed with the strings locked in
grilling Sub-item 1, with the upstream URL interpolated where
indicated. The proxy distinguishes "Swing application" from
"Swing MCP Agent" by error semantics, not by transport — the two
diagnostics point the operator at different things to check.

| Slot | Wording |
|---|---|
| `upstreamDownMessage` | `Cannot reach Swing MCP Agent at <URL> — ask the user to start the Swing application (the MCP agent runs inside it).` |
| `driftMessage` | `Swing-MCP is out of sync with the Swing MCP Agent at <URL> — the tool manifest doesn't match. Tell the user to restart the MCP server or the Swing application so versions match. Do not retry.` |
| `sessionLostMessage` | `SwingTools.SESSION_LOST_MESSAGE` (verbatim — shared with `SwingMCP` so both transports emit identical text) |
| `ioMidCallMessage` | `Lost connection to Swing MCP Agent at <URL> mid-call — the action may or may not have completed; call swing_snapshot to verify.` |

`<URL>` is the upstream URL from step (2) above, e.g.
`http://127.0.0.1:18088/mcp`. Including it verbatim is
deliberate — surfaces "is the agent loaded?" vs. "is the app
running on a different port?" without a second round-trip.

Note that the **session-lost slot is the only string shared
between transports.** The connectivity-flavoured strings live in
this module because they only make sense from the proxy side
(the in-process `SwingMCP` cannot get an `IOException` talking
to itself). Per grilling Sub-item 1 / DR-embedded-mcp-client update, the
in-process `SwingMCP` server adopts `SESSION_LOST_MESSAGE` for
its own session-lost path so both transports emit identical
wording.

### Logging

- JDK `java.util.logging` (JUL). No third-party log framework.
  Per-logger level is the code's call — the spec does not pin a
  default. Adjustable via standard JUL configuration if a
  developer needs more detail.
- Sink: **stderr only.** JUL's default `ConsoleHandler` already
  targets stderr, and `StdioMCPServer` re-points `System.out` to
  `System.err` defensively (DR-stdio-transport), so accidental
  `System.out.println` calls cannot corrupt the wire either.
- **Diagnostic data goes here, not into the LLM-facing
  messages** (Sub-item 1). On drift, log full `ToolDescriptor`
  JSONs (manifest vs. upstream) at WARNING. On `IOException`,
  log the stack trace at WARNING. The LLM sees the short
  remediation; the developer sees the full picture.

### Shutdown

- **stdin EOF** is the normal termination signal — Claude Code
  closes stdin when the MCP session ends. `runStdio` returns
  cleanly, in-flight calls finish first (Q17). After return,
  `handler.stop()` cascades through `onSessionClosed`, which
  closes the upstream `TinyMCPClient`.
- **JVM shutdown hook** covers SIGTERM and `kill` cases. The
  hook closes the same upstream client. `TinyMCPClient.close()`
  is idempotent so running both paths during the same shutdown
  causes no errors.

### Packaging

- Shadow plugin produces a single executable jar:
  `archiveClassifier.set("")`, `mergeServiceFiles()`,
  `tasks.named("build") { dependsOn(tasks.shadowJar) }` — same
  shape as `swing-mcp-agent` (Q32b).
- `Main-Class: com.vaadin.swingmcp.proxy.Main` in the manifest.
- Published to Maven Central via `configureMavenCentral(...)`
  (Q32a) so end-users can `java -jar swing-mcp-proxy-X.Y.Z.jar`
  without building from source.

---

## 3. Testing

Most of the behavioural surface is exercised by tests against
`MCPProxy` in `tiny-mcp-server` (see that module's
`architecture.md` § 3 for the integration-test list). The tests
that live **in this module** focus on the wire-up:

- **Port resolution.** Verify the precedence rule (system
  property wins over env var, env var wins over default) by
  driving the resolution helper with various combinations.
  Includes the malformed-port case (parse failure exits with a
  clear JUL log).
- **`ProxyMessages` construction.** Verify that the four
  produced strings match the locked wordings (above), with the
  URL interpolated correctly. Catches regressions where someone
  edits a message string without realising it's part of the
  contract.
- **Identity passthrough.** Boot the proxy's `Main` against a
  test `HttpMCPServer` running `SwingMCP` (or a mock that
  registers `SwingTools.ALL`); call `initialize` over stdio;
  assert that the returned `serverInfo` and `instructions` match
  the constants in `swing-mcp-tool-defs` exactly. (Same
  assertion runs as a coherence test on the in-process `SwingMCP`
  side; the two together prove identity is bit-identical across
  transports.)
- **End-to-end smoke test.** Drive the full
  `Main` → stdio → upstream HTTP path on piped streams against
  a real `SwingMCP` (or a stand-in handler with the same
  manifest), exercising at least one successful `tools/call`.
  Catches packaging-level breakage that unit tests would miss.

Per Q33c, this module has **no `decisions.md`** — every cross-cutting
decision affecting the proxy is generic and lives in
`tiny-mcp-server/spec/decisions.md` (DR-stdio-transport,
DR-embedded-mcp-client, DR-forwarding-proxy, DR-settable-listeners,
DR-structural-schema-equality). And per Q33d, no `tools/` directory — the
proxy implements zero tools.
