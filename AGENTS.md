# Swing MCP — AGENTS.md

## What this is

An in-process MCP (Model Context Protocol) HTTP server for Java Swing apps,
designed to enable AI-driven inspection and interaction with Swing UIs.
The primary use case is AI-assisted migration of Swing apps to Vaadin.

## Promises

- **An agent drives the app the way a person does.** Everything reachable goes through the real listener tree, and nothing is offered that a user could not do themselves.
- **Cheap to read.** A text snapshot, not a screenshot: what it costs to look at a screen scales with the number of widgets, not the number of pixels.
- **Droppable into an app we do not own.** It ships as a jar for a Swing application built years ago, so it adds as few runtime dependencies as it can and never asks that application to change how it starts.

## Design docs

| File | Owns | Loaded |
|---|---|---|
| `README.md` | the pitch, the Swing component reference, how to install and register it | — |
| `AGENTS.md` (this) | promises, invariants, the module map, conventions, commands | every turn |
| `design/architecture.md` | how the pieces compose — the tool pipeline, the ref lifecycle, threading, the flows; normative | lazy |
| `design/decisions.md` | why this and not that — `D_` entries, FAQ-shaped | lazy |
| `design/research.md` | what `javax.accessibility` and Swing actually do — `R_` entries with provenance | lazy |
| `design/snapshot-format.md` | the exact text the snapshot emits — line grammar, pruning, states; normative | lazy |
| doc comments | what one symbol does and why it is shaped so | at the symbol |

Every fact lives in exactly one of these; the others link to it.

## Invariants

- **Every Swing access happens on the EDT**, through `SwingMCP.runInEDT`; the marshalling seam is in `design/architecture.md`.
- **A mutation validates synchronously and acts asynchronously.** It returns before its action runs, so nothing blocks on a modal dialog. See `D_fire_and_forget_dispatch`.
- **One tool call at a time.** `toolLock` spans the whole call, not just the EDT turn — the gap after dispatch is the one that bites. See `D_tool_call_wide_lock`.
- **Only expose an action a real user could perform.** Advertising a write on a read-only or programmatically-driven component can put the host application into a state it has no code path for.
- **Tools see only windows the user can interact with.** A ref that addresses a blocked window is a mutation that silently does nothing. See `D_interactable_windows_only`.
- **A successful mutation clears the session's ref map; a rejected one leaves it intact** — nothing was dispatched, so the model can retry without re-snapshotting.
- **Every quoted slot in a snapshot line is sanitized before emission.** One embedded newline corrupts the tree structure for every reader below it. See `D_quoted_slot_sanitizing`.
- **A tool never declares its own name, description or schema** — it binds a `ToolDescriptor` from `swing-mcp-tool-defs`, so the two transports cannot drift. See `D_shared_tool_manifest`.
- **`swing-mcp-proxy` must not depend on `swing-mcp`.** It runs in its own JVM; a dependency would drag the whole Swing stack into it.

## Module map

- `tiny-mcp-server` — a separate product vendored here: the MCP protocol, both transports, the proxy machinery. Rules: `tiny-mcp-server/AGENTS.md`
- `swing-mcp-tool-defs` — the shared contract: server identity, the tool manifest, the session-lost message.
- `swing-mcp` — the Swing tools themselves: the snapshot, the screenshot, and every interaction tool.
- `swing-mcp-agent` — a `-javaagent` that starts the server before `main()`, so the host application needs no code change.
- `swing-mcp-proxy` — the stdio process an MCP client spawns, forwarding to the in-process server over loopback.
- `test-apps` — demo Swing applications and the screen-mode integration tests.

## Conventions

- **Java 17 is the floor.** No API introduced later — this drops into applications that have not moved on (`List.getFirst()` is Java 21, and tempting).
- **Tests: JUnit 6, two source sets.** `src/test` runs headless; `src/testSwing` needs a display and owns anything that must instantiate a real `Window`.
- **Every tool test walks the component matrix** — a correct refusal is as much a result as a success; the matrix is in `design/architecture.md` § Testing.
- **Diagnostics go to `java.util.logging`**, never to `System.out`; the LLM reads the error body, the developer reads stderr.
- **Assert against the whole string**, not `contains` / `startsWith` — a snapshot diff is the readable failure.
- **A tool-level failure is `MCPErrorResponseException`** carrying a recovery hint the model can act on, never a bare exception.
- **Component identity is the Swing class name** in everything a person or model reads; the accessibility role appears in the snapshot only. See `D_role_in_snapshot_only`.
- **Pre-1.0: break APIs freely** — every consumer is in this repository.

## Commands

- `./gradlew` — clean, build, all tests. The default task, and what CI runs. It includes `testSwing`, so it needs a display: `xvfb-run -a ./gradlew` where there is none.
- `./gradlew test` — every headless test; `./gradlew :swing-mcp:testSwing` — the screen-mode ones (Xvfb in CI).
- `./gradlew test --tests "com.vaadin.swingmcp.tinymcpserver.TinyMcpServerTest"` — one class; append `.methodName` for one method.
- `design/verify_design_tripwires.sh` and `tiny-mcp-server/design/verify_design_tripwires.sh` — the two doc layers.

## Skills this project follows

- **Component-oriented:** self-sufficient components that reach their data directly, no MVC layers; the `cop` skill has the rules.
- **Javadoc carries the contract, not the narrative:** an example over an algorithm, no history, no referrer lists; the `writing-javadoc` skill has the rules.

## Maintenance of this file

Loaded every turn; cap 34 KB, a module's own `AGENTS.md` 10 KB. Over it, in this order:
delete what has no home — status, history, class lists, what the code already says; trim
each line to its fact plus one clause and send the explanation home — why →
`design/decisions.md`, how across symbols → `design/architecture.md`, how in one symbol →
its doc comment, what upstream does → `design/research.md`; only then a module's own
`AGENTS.md`, peripheral modules first, never the core. Never paraphrase a lazy entry into a
line here. `design/verify_design_tripwires.sh` checks the caps and the cites.
