# Claude Code's reconnect quirks against a one-session server

Found while measuring Claude Code 2.1.280 for the proxy removal (2026-09-23). The facts are in
`R_claude_code_http_lifecycle`. Now that direct HTTP is the only path (`D_direct_http_only`), the
client's reconnect is the normal way back after an application restart, so these two are worth a
look.

## Supersede churn on reconnect

After a 404 on a stale session id, the client seems to initialize twice in quick succession. The
server log from one restart:

```
Rejecting request: unknown Mcp-Session-Id 531138cf…
Rejecting 'server/discover': no Mcp-Session-Id header        (×2)
Session superseded: 22193880…                                 ← initialize B evicts A
Rejecting request: unknown Mcp-Session-Id 22193880…           ← something still on A
Rejecting 'server/discover': no Mcp-Session-Id header
Session superseded: ff230f03…                                 ← initialize C evicts B
```

It settled every time, and the tool call succeeded. But under `D_single_session`'s "newcomer
wins", a tool call can land on a session that was just superseded, and the model then gets the
superseded message for a session it never knowingly had.

- `Q_who_initializes` — What are the two initializers: the tool-call path plus a background
  reconnect, or two tool-call retries? Getting it from the client's debug log (`claude --debug`)
  would settle it.
- `Q_grace_window` — If it is inherent, would a short grace window help? The idea: a supersede
  within N ms of the previous session's own `initialize`, with no call made on it yet, evicts
  silently. Or does that just move the race?

## `server/discover`

Before `initialize`, the client POSTs `server/discover` with no session header. We answer it as a
session-less non-`initialize` request (`Rejecting 'server/discover': no Mcp-Session-Id header`),
and the client falls back to `initialize`.

- `Q_discover_spec` — Which specification revision or proposal defines `server/discover`, and what
  should a server that does not support it answer? Probably JSON-RPC "method not found", rather
  than the session error it gets now. Answering that way is tiny-mcp-server's change, and its
  research register is where the finding goes.
