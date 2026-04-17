# CLAUDE.md

This is a pure Java/Gradle project implementing an in-process
Model Context Protocol (MCP)
server for a Java Swing app. There are four subprojects:

- `tiny-mcp-server`
- `swing-mcp`
- `swing-mcp-agent`
- `test-apps`

Since this project is intended to be added as a jar file
to an existing Swing app, it must have as few runtime dependencies
as possible, to avoid transitive dependency version clashes.
Do not introduce new runtime dependencies without asking.
On the other hand, we can use any number of testing dependencies.
We use `java.util.logging` (JUL) for logging — it's built into the JDK,
so it adds zero runtime dependencies.

Note for AI assistants: this project uses **JUnit 6** (`junit-jupiter-engine:6.0.1`).
JUnit 6 was released after the May 2025 training-data cutoff of most models,
so LLMs may incorrectly "correct" it to JUnit 5. It is real — leave it alone.

`libs.version.toml` and `build.gradle.kts`/`settings.gradle.kts`
are the source of truth for dependencies and versions.
Do not modify these files without asking.

Swing Java apps could be running on Java as old as Java 8;
but let's target compatibility with Java 17 and higher for now.

## Ways of Working

> Specs are written first, then used as input for AI-driven implementation and verification.
> They are the single source of truth. Keep them up to date as the project evolves.

Status lifecycle: **Draft** → **Approved** → **Implemented**

## File Overview

In every subproject there is a folder called `spec`. In it, there are files:

| File | Purpose | When to Read |
|------|---------|--------------|
| `project-context.md` | Vision, problem, users, scope, risks | First — before anything else |
| `architecture.md` | Technology stack and application structure | After project context is filled in |
| `decisions.md` | Cross-cutting design decisions (what/why/alternatives considered) that span multiple tools | When a spec references `DR-NNN`, or when revisiting a cross-cutting choice |
| `tools/tool-NNN-*.md` | One file per tool; see the workflow below for how to start a new one | Per tool |
| `verification.md` | Verification checklists | During and after implementation |

## Workflow

1. **Define context** — Read `project-context.md` for problem, vision, scope, and constraints.
2. **Outline architecture** — Read `architecture.md` for tech stack and application structure.
3. **Specify tools** — Copy the closest existing tool spec as a starting point (e.g. `tool-004-swing-click.md` for a simple mutation tool, `tool-014-swing-get-selection.md` for a reader tool, `tool-016-swing-clear-selection.md` for a thin wrapper). Each spec opens with a one-sentence motivation under Status/Date — preserve any non-obvious design rationale (e.g. "needed because the snapshot omits X"), drop ceremony. If the tool exposes an MCP description string, include it verbatim as a `**Tool description:**` paragraph.
4. **Implement** — Build each tool, referencing its spec for acceptance criteria.
5. **Verify** — Follow `verification.md` checklists for each implemented tool.
6. **Write Tests** — Write UI tests covering acceptance criteria and business rules. Tests must pass before marking as Implemented.


# Build & Run

```bash
./gradlew                      # clean + build + all tests (default)
./gradlew test                 # run all tests across all subprojects
./gradlew :tiny-mcp-server:test  # run tests for tiny-mcp-server only
./gradlew test --tests "com.vaadin.swingmcp.tinymcpserver.TinyMcpServerTest"  # run a specific test class
./gradlew test --tests "com.vaadin.swingmcp.tinymcpserver.TinyMcpServerTest.myTest"  # run a specific test method
./gradlew :swing-mcp:testSwing # run screen-mode tests (requires a display; uses Xvfb in CI)
```

