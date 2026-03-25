# CLAUDE.md

This is a pure Java/Gradle project implementing an in-process
Model Context Protocol (MCP)
server for a Java Swing app. There are two subprojects:

- `tiny-mcp-server`
- `swing-mcp`

Since this project is intended to be added as a jar file
to an existing Swing app, it must have as few runtime dependencies
as possible, to avoid transitive dependency version clashes.
Do not introduce new runtime dependencies without asking.
On the other hand, we can use any number of testing dependencies.
We use slf4j for logging since that's the overwhelming default
for all apps and the dependency is tiny. We may replace that
in the future by Java built-in logging, but that remains to be seen.

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
| `use-cases/use-case-template.md` | Template for individual feature specs | Copy per feature as `use-case-NNN-short-name.md` |
| `verification.md` | Verification checklists | During and after implementation |

## Workflow

1. **Define context** — Read `project-context.md` for problem, vision, scope, and constraints.
2. **Outline architecture** — Read `architecture.md` for tech stack and application structure.
3. **Specify features** — Copy `use-cases/use-case-template.md` once per feature.
4. **Implement** — Build each use case, referencing its spec for acceptance criteria.
5. **Verify** — Follow `verification.md` checklists for each implemented use case.
6. **Write Tests** — Write UI tests covering acceptance criteria and business rules. Tests must pass before marking as Implemented.


# Build & Run

```bash
./gradlew                      # clean + build + all tests (default)
./gradlew test                 # run all tests across all subprojects
./gradlew :tiny-mcp-server:test  # run tests for tiny-mcp-server only
./gradlew test --tests "com.vaadin.swingmcp.tinymcpserver.TinyMcpServerTest"  # run a specific test class
./gradlew test --tests "com.vaadin.swingmcp.tinymcpserver.TinyMcpServerTest.myTest"  # run a specific test method
```

