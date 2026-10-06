# Claude Code's reconnect quirks against a one-session server

Found while measuring Claude Code for the proxy removal (2026-09-23), and re-measured on 2.1.281
with the client's debug log (2026-09-24). The facts are in `R_claude_code_http_lifecycle`. Now
that direct HTTP is the only path (`D_direct_http_only`), the client's reconnect is the normal way
back after an application restart.

## Supersede churn on reconnect

The client's side is reported upstream as anthropics/claude-code#96733: one 404 starts two
recoveries at once, each with its own `initialize`, and a third follows a second later. Under
`D_single_session`'s "newcomer wins", the two concurrent ones evict each other, and when the tool
call's retry loses, the model sees `MCP server "swing" is not connected` for an application that
is up. What is left for us is whether the server should absorb the race until the client fixes it.

- `Q_grace_window` — Would a short grace window help? The two initializes arrive 1–40 ms apart,
  and the loser has not yet made a call. The idea: for N ms after a session's own `initialize`,
  a newcomer is admitted *beside* it instead of evicting it, and whichever makes no call is left to
  the idle eviction. Does that reopen the two-agents problem `D_single_session` exists to prevent,
  or is N ms too short for a second agent to matter? And it is moot once #96733 is fixed, so is it
  worth the code?

## `server/discover`

Before each `initialize`, the client POSTs `server/discover` with no session header. We answer it
as a session-less non-`initialize` request (`Rejecting 'server/discover': no Mcp-Session-Id
header`), and the client falls back to `initialize`.

- `Q_discover_spec` — Which specification revision or proposal defines `server/discover`, and what
  should a server that does not support it answer? Probably JSON-RPC "method not found", rather
  than the session error it gets now. Answering that way is tiny-mcp-server's change, and its
  research register is where the finding goes.
