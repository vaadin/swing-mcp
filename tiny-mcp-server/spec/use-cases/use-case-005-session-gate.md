# UC-005: Session lifecycle gate

Enforce MCP session lifecycle: reject requests that arrive before `initialize`
or that carry a wrong/missing session ID. Needed because the server currently
dispatches every method regardless of session state, which is non-compliant
with the MCP specification (2025-03-26 §Lifecycle, §Session Management).

**Status:** Implemented
**Date:** 2026-04-16

---

## Context

After a `DELETE` terminates the session, tools like `swing_snapshot` still
succeed without re-initializing. The MCP spec requires initialization before
any non-ping method is accepted, and defines specific HTTP status codes for
session violations.

References: `tiny-mcp-server` DR-003 (single-session model), DR-004
(two-layer error handling).

---

## Design

### Session validation in `handlePost`

Add an early guard in `TinyMCPServer.handlePost`, **before JSON-RPC parsing**,
that reads the incoming `Mcp-Session-Id` header and validates it against the
server's `activeSessionId`.

The guard runs immediately after reading the header — before the request body
is parsed — so invalid sessions are rejected cheaply.

#### Decision matrix

| Incoming `Mcp-Session-Id` | `activeSessionId` | Method | Result |
|---|---|---|---|
| absent | null (no session) | `initialize` | OK — create session |
| absent | null | `ping` | OK |
| absent | null | anything else | **HTTP 400** + JSON-RPC error |
| absent | non-null (session active) | `initialize` | 409 — already active (existing behavior) |
| absent | non-null | `ping` | OK |
| absent | non-null | anything else | **HTTP 400** + JSON-RPC error |
| matches `activeSessionId` | non-null | any | OK — normal dispatch |
| doesn't match | non-null | any (including `initialize`, `ping`) | **HTTP 404** + JSON-RPC error |
| doesn't match | null | any (including `initialize`, `ping`) | **HTTP 404** + JSON-RPC error |

Implementation notes:

- **Session ID mismatch check comes first.** If the client sends *any*
  `Mcp-Session-Id` value that does not match `activeSessionId` (whether
  `activeSessionId` is null or non-null), return HTTP 404. This covers
  stale sessions, typos, and sessions terminated by `DELETE`. Per the MCP
  spec: *"the server MUST respond to requests containing that session ID
  with HTTP 404 Not Found"*, and: *"When a client receives HTTP 404 …
  it MUST start a new session by sending a new InitializeRequest without
  a session ID attached."*

- **Missing session ID + no active session + not `initialize`/`ping`.**
  Return HTTP 400. Per the MCP spec: *"Servers that require a session ID
  SHOULD respond to requests without an Mcp-Session-Id header (other
  than initialization) with HTTP 400 Bad Request."*

- **`ping` is always allowed**, even before initialization or without a
  session ID. The MCP spec singles out pings as acceptable pre-init:
  *"The client SHOULD NOT send requests other than pings before the
  server has responded to the initialize request."*

- **The check happens before JSON body parsing.** This means we cannot
  extract the JSON-RPC `id` or `method` from the body to include in the
  error response. The session ID header alone determines the outcome.
  Exception: distinguishing `initialize` and `ping` from other methods
  requires parsing the body. Therefore the actual flow is:

  1. Read `Mcp-Session-Id` header.
  2. If header is present and does not match `activeSessionId` → HTTP 404
     immediately (no body parsing needed — *any* method is rejected).
  3. Parse the JSON-RPC body (existing code).
  4. If method is `initialize` or `ping` → allow (existing dispatch).
  5. If `activeSessionId` is null → HTTP 400 + JSON-RPC error (using the
     parsed `id`).

  This means step 2 is truly pre-parse (cheap rejection for wrong session
  ID), while step 5 is post-parse (we have the request `id` for a proper
  JSON-RPC error).

### Error responses

**HTTP 400 — not initialized:**

```json
{
  "jsonrpc": "2.0",
  "id": <request-id>,
  "error": {
    "code": -32002,
    "message": "Server not initialized. Send 'initialize' first."
  }
}
```

**HTTP 404 — session not found** (pre-parse, no request id available):

```json
{
  "jsonrpc": "2.0",
  "id": null,
  "error": {
    "code": -32002,
    "message": "Session not found."
  }
}
```

Both use JSON-RPC error code **-32002** (implementation-defined server
error in the -32000 to -32099 range). Add a constant
`MCPServerException.SERVER_NOT_INITIALIZED = -32002`.

### Session close hook

Add a **protected no-op** method `onSessionClosed()` in `TinyMCPServer`.
`handleDelete` calls it **before** nulling `activeSessionId`, so the hook
can still see the session state if needed.

`MCPServer` overrides `onSessionClosed()` to clear the component ref map.
The override acquires `toolLock` to avoid racing with an in-flight tool
call, then calls `context.clearRefMap()`.

### Existing behavior preserved

- `handleInitialize` still returns HTTP 409 when `activeSessionId != null`
  (DR-003).
- `ping` works at all times with no session ID requirement.
- `handleDelete` with no active session still returns 200 (idempotent).
- All response helpers still attach the `Mcp-Session-Id` header when
  `activeSessionId != null`.

---

## Acceptance Criteria

- [x] POST with no `Mcp-Session-Id` and no active session, method other than
      `initialize`/`ping` → HTTP 400 + JSON-RPC error (-32002,
      "Server not initialized. Send 'initialize' first.")
- [x] POST with wrong `Mcp-Session-Id` → HTTP 404 + JSON-RPC error (-32002,
      "Session not found.")
- [x] POST with correct `Mcp-Session-Id` → normal dispatch (200)
- [x] POST `ping` without session ID, before init → succeeds (200)
- [x] POST `ping` without session ID, after init → succeeds (200)
- [x] POST `initialize` without session ID → succeeds (creates session)
- [x] After `DELETE`, tools return HTTP 400 (session cleared)
- [x] After `DELETE`, `initialize` succeeds (new session)
- [x] After `DELETE`, component ref map is cleared (MCPServer layer)
- [x] `MCPServerException.SERVER_NOT_INITIALIZED` constant exists (-32002)
- [x] `TinyMCPServer.onSessionClosed()` is protected and no-op by default
- [x] `MCPServer` overrides `onSessionClosed()` to clear ref map under
      `toolLock`

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md`
> § Testing for conventions.

All tests use raw `java.net.http.HttpClient` POST requests (not the MCP SDK
client) so we can control headers precisely and assert HTTP status codes.

### TinyMCPServerTest (tiny-mcp-server)

- [x] `toolsListBeforeInitializeReturns400` — POST `tools/list` with no
      session ID, no prior init → HTTP 400, JSON-RPC error code -32002
- [x] `toolsCallBeforeInitializeReturns400` — POST `tools/call` with no
      session ID, no prior init → HTTP 400, JSON-RPC error code -32002
- [x] `toolsListWithWrongSessionIdReturns404` — initialize, then POST
      `tools/list` with wrong `Mcp-Session-Id` → HTTP 404, JSON-RPC error
      code -32002
- [x] `toolsListWithCorrectSessionIdSucceeds` — initialize, extract session
      ID from response header, POST `tools/list` with that ID → HTTP 200
- [x] `pingBeforeInitializeSucceeds` — POST `ping` with no session ID,
      no prior init → HTTP 200
- [x] `pingAfterInitializeWithoutSessionIdSucceeds` — initialize, then POST
      `ping` with no session ID → HTTP 200
- [x] `initializeAfterDeleteSucceeds` — initialize, DELETE, initialize
      again → HTTP 200, new session ID
- [x] `toolsListAfterDeleteReturns400` — initialize, DELETE, POST
      `tools/list` (no session ID) → HTTP 400
- [x] `toolsCallAfterDeleteWithStaleSessionIdReturns404` — initialize
      (capture session ID), DELETE, POST `tools/call` with old session ID
      → HTTP 404
- [x] `unknownSessionIdWhenNoActiveSessionReturns404` — POST `initialize`
      with unknown session ID, no active session → HTTP 404
- [x] `pingWithWrongSessionIdReturns404` — initialize, POST `ping` with
      wrong session ID → HTTP 404

### SessionCloseTest (swing-mcp)

- [x] `sessionDeleteClearsRefMap` — populate ref map via snapshot, terminate
      session via DELETE, re-init, verify ref lookup fails with stale error
