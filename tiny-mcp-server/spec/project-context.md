# Project Context

This subproject implements a generic minimalistic MCP server in pure Java.
It uses the GSON library for JSON creation and parsing, and
runs on the HttpServer built in Java.

## 1. Vision

This subproject is an internal dependency of swing-mcp and not meant
to be used elsewhere. Its purpose is to have as few dependencies as possible,
to avoid transitive dependency clashes when embedding into customer
Swing Java apps.

Only the tools are supported at the moment: no custom resources nor
prompts are supported.

## 2. Users

Internal project: no human users, only the swing-mcp subproject is
the intended user.

## 3. Constraints

- As few runtime dependencies as possible

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Architecture](architecture.md) — technology stack and application structure
- [Verification](verification.md) — visual verification checklists
