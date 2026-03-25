# Project Context

This subproject implements a generic minimalistic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built in Java.

The MCP server is minimalistic:

- No support for SSE streams for server-to-client push messages
- No support for auth of any kind
- No support for resources nor prompts, only tools

The MCP server doesn't support STDIO communication, it only supports
HTTP. It listens on localhost interface, on specified port and
path, which default to:

- port: `18088`
- context path: `/mcp`

Contrary to Playwright MCP, the server can not launch more browsers.
Therefore, the server supports at most single session:

- A session is allowed to be opened only if there is no other session ongoing.
- Only after a session is terminated, a new session is allowed to be started.
- If second session is attempted via a MCP initialization request,
  that request is denied. TODO Claude suggest an appropriate HTTP error code and response message.

## 1. Vision

This subproject is an internal dependency of swing-mcp and not meant
to be used elsewhere. Its purpose is to have as few dependencies as possible,
to avoid transitive dependency clashes when embedding into customer
Swing Java apps.

## 2. Users

Internal project: no human users, only the swing-mcp subproject is
the intended user.

## 3. Constraints

- As few runtime dependencies as possible

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Architecture](architecture.md) — technology stack and application structure
- [Use Case Template](use-cases/use-case-template.md) — template for feature specifications
- [Verification](verification.md) — visual verification checklists
