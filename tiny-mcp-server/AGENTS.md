# tiny-mcp-server — AGENTS.md

## What this is

A minimal Model Context Protocol server in pure Java, built to be embedded in someone else's
application. HTTP and stdio transports, tools, resources and prompts, plus the `MCPProxy`
forwarding machinery — over the JDK's `HttpServer` and GSON, with no framework underneath.
It implements the protocol and knows nothing about what the tools do.

## Promises

- **Host-agnostic.** Nothing here names Swing, or any other host application.
- **Embeddable without dependency risk.** GSON and the JDK — it shares a classloader with an application we do not control. See `D_no_framework_deps`.

## Design docs

| File | Owns | Loaded |
|---|---|---|
| `README.md` | the pitch, how to use it, what it does not do | — |
| `AGENTS.md` (this) | promises, invariants, the package map, conventions, commands | every turn |
| `design/architecture.md` | how the pieces compose — the transport/protocol seam, the session lifecycle, the flows; normative | lazy |
| `design/decisions.md` | why this and not that — `D_` entries, FAQ-shaped | lazy |
| `design/research.md` | what the MCP specification and the official SDK actually do — `R_` entries with provenance | lazy |
| doc comments | what one symbol does and why it is shaped so | at the symbol |

Every fact lives in exactly one of these; the others link to it.

## Invariants

- **JSON is produced by POJO mapping through GSON**, never by string concatenation or the element API; escaping bugs are the reason.
- **Handler code throws, transport code writes.** A handler method returns a result POJO or throws `MCPServerException`; only the transport turns one into bytes. See `D_three_error_layers`.
- **Every dispatched request passes through `MCPSession.runLocked`** — the one place that refreshes last-access and fails a closed session. See `D_idle_eviction`.
- **Only the framing layer writes to the captured stdout.** Anything else printing there corrupts the stdio wire. See `D_stdio_transport`.
- **Idle cleanup is scheduled by `HttpMCPServer.start()`, never by `MCPHandler.start()`.** A stdio session evicted on a timer bricks its process. See `D_stdio_never_evicts`.
- **`ProxyMessages` strings are emitted verbatim.** No templating happens in this module — interpolation is the caller's, which is what keeps the module host-agnostic.

## Package map

- `com.vaadin.swingmcp.tinymcpserver` — protocol dispatch, the session map, both transports, the `MCPProxy` factory.
- `com.vaadin.swingmcp.tinymcpclient` — the minimal HTTP MCP client the proxy forwards through.
- `com.vaadin.swingmcp` — `ToolDescriptor`, the in-memory tool-contract type. Shared with this repository's other modules; it is the one package name that still records the vendoring. See `D_vendored_namespace`.

## Conventions

- **Java 11, plain classes, no framework.** GSON for JSON, `java.net.http.HttpClient` for the client side, `com.sun.net.httpserver` for the server side.
- **Tests: JUnit 5**, and the tool suite runs twice — through `TinyMCPClient` (also on Java 11) and, from `src/testOfficial`, through the official MCP SDK. See `D_conformance_two_clients`.
- **Diagnostics go to `java.util.logging`**, never to `System.out`; two audiences, two channels — the LLM reads the `isError` body, the operator reads stderr.
- **Transports compose, never inherit.** A transport takes a configured `MCPHandler`; nothing extends a transport to configure it.
- **One handler, one transport, one lifecycle cycle.** No restart, no reuse, no sharing.
- **Pre-1.0: break APIs freely** — the two consumers live in this repository and are rebuilt together.

## Commands

- `./gradlew :tiny-mcp-server:test` — the tests, from the repository root.
- `./gradlew :tiny-mcp-server:testOfficial` — the same conformance suite through the official SDK; needs a 17+ build JDK.
- `tiny-mcp-server/design/verify_design_tripwires.sh` — this module's doc layer; runs from anywhere in the repository.

## Skills this project follows

- **Composition over inheritance:** subclass a framework type to *be* one, never to share code; the `cop` skill has the rules.

## Maintenance of this file

Loaded every turn; cap 10 KB — this module is nested inside another repository, so the nested
cap applies even though it is its own product. Over it, in this order: delete what has no home
— status, history, class lists, what the code already says; trim each line to its fact plus one
clause and send the explanation home — why → `design/decisions.md`, how across symbols →
`design/architecture.md`, how in one symbol → its doc comment, what upstream does →
`design/research.md`. Never paraphrase a lazy entry into a line here.
`design/verify_design_tripwires.sh` checks the caps and the cites.
