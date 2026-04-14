# Project Context

This subproject implements a generic minimalistic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built in Java.

The MCP server is minimalistic:

- No support for SSE streams for server-to-client push messages
- No support for auth of any kind
- No support for resources nor prompts, only tools

### Transport: HTTP only

The MCP server doesn't support STDIO communication, it only supports HTTP.
**Rationale:** Since this MCP server runs in-process within a Swing app,
the Swing app itself uses STDIO for its own purposes. STDIO-based MCP
communication would be polluted by the Swing app's own output. HTTP is
the cleaner approach.

The server binds to `127.0.0.1` only (localhost). No authentication is needed —
the developer owning the machine is responsible for local security. This is
sufficient for the Vaadin migration scenario; if the project expands to other
use cases, security can be revisited.

Default listen address:

- port: `18088`
- context path: `/mcp`

There is no port discovery mechanism. Usually there is exactly one Swing app
running per machine (the one being migrated to Vaadin). Multiple Swing apps
on the same machine is a corner case handled by changing the port.

### Single-session model

Since this MCP server runs in-process within a Swing app, there is exactly
one Swing app instance. Multiple concurrent AI agents controlling the same
Swing app would cause random concurrency issues, making testing useless.
Therefore, the server supports at most a single session:

- A session is allowed to be opened only if there is no other session ongoing.
- Only after a session is terminated, a new session is allowed to be started.
- If a second session is attempted via a MCP initialization request,
  that request is denied with HTTP 409 Conflict and response body `"Another session is already active"`.
- The blocked client simply fails — no queuing or retry mechanism.

### Stuck sessions

If an AI agent crashes or disconnects without properly closing the session,
the session remains locked indefinitely. The server logs a warning when
another AI agent attempts to connect while the session is locked. To clear
a stuck session, the Swing app must be restarted. This is acceptable for
the migration scenario where stuck sessions are rare (<1% of cases). If
this becomes problematic, we will revisit (e.g., timeout or manual release).

### Error handling model

Two layers of error reporting:

- **Transport-level errors (HTTP 4xx/5xx):** For programming errors,
  `RuntimeException`, or when the server cannot process the request at all
  (e.g., accessibility tree unreadable).
- **MCP-level errors (`isError: true`):** For application-level errors
  where the tool ran but the input was bad (e.g., invalid component ref).
  Following Playwright MCP's pattern, include a helpful recovery message
  (e.g., "Ref not found, likely because element was removed. Use swing_snapshot
  to see what elements are currently available.").

## 1. Vision

This subproject is an internal dependency of swing-mcp and not meant
to be used elsewhere. Its purpose is to have as few dependencies as possible,
to avoid transitive dependency clashes when embedding into customer
Swing Java apps.

The broader context: swing-mcp is part of a Vaadin migration workflow where
a customer migrates a Java Swing app to Vaadin. An AI agent uses MCP to
inspect and navigate the Swing app, gathering screenshots and accessibility
snapshots to inform the migration.

## 2. Users

Internal project: no human users, only the swing-mcp subproject is
the intended user.

## 3. Constraints

- As few runtime dependencies as possible
- Java 17+ required
- Bind to `127.0.0.1` only — never `0.0.0.0`

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Architecture](architecture.md) — technology stack and application structure
- [Decisions](decisions.md) — cross-cutting design decisions (what/why/alternatives)
- [Verification](verification.md) — visual verification checklists
