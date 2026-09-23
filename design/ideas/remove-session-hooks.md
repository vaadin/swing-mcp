# Remove `MCPHandler.setOnSessionStarted` / `setOnSessionClosed`

Split out of `remove-swing-mcp-proxy.md` on 2026-09-23. The proxy removal keeps both hooks; this
idea removes them afterwards.

## Why

`MCPProxy` was their only production caller: it allocated per-session upstream state in
`onSessionStarted` and closed it in `onSessionClosed`. Once the proxy is gone, nothing in `main`
calls either hook. `SwingMCP` uses only `setAcceptNewSession`, for its single-session policy, and
that setter stays. Pre-1.0, an unused hook is a guess at an API, and a guess costs its locking
rule, its exception containment and its doc entry.

## What depends on them, 2026-09-23

Only tests. They use the hooks to observe the session lifecycle, which is why the hooks could not
simply go with the proxy.

| Test | Hook use |
|---|---|
| `SessionCleanupTest` | `RecordingServer` records closed session ids through `onSessionClosed`, and can make it throw |
| `SessionSupersedeTest` | records started and closed ids, to assert who was superseded |
| `StdioMCPServerTest` | `dispatchOnAClosedSessionIsAnInternalErrorNotATombstone` captures the started session through `onSessionStarted` |
| `MCPHandlerDispatchTest` | a throwing `onSessionClosed` must not strand the remaining sessions on `closeAllSessions` |
| `MCPHandlerListenerTest` | tests the hooks themselves |

The last two test hook behaviour, so they go with the hooks. The first three test something else
and use a hook as their window.

## Removal sketch

- Rewrite the three observer tests to see what a real client sees: the wire response (a 404 or
  tombstone for an evicted or superseded session, a working call for a live one) plus the public
  `MCPHandler.getSessionCount()`.
- Delete the two setters, their fields, the exception containment around the calls, and the
  locking rule's mention of them. Delete `MCPHandlerListenerTest` and the throwing-listener case.
- tiny-mcp-server's settable-listeners entry ("Why are the session listeners setters that lock…")
  shrinks to `setAcceptNewSession` and its lock-after-first-session rule, or merges into the
  session-policy entry if nothing else is left. Update `MCPHandler`'s class javadoc, which lists
  all three setters.

## Open questions

- `Q_observer_seam` — Can every observer test assert on the wire plus `getSessionCount()`? Or does
  one need to reach a specific `MCPSession`, as `StdioMCPServerTest` does to close it behind the
  transport's back? If one does, would a package-private accessor be the hook in all but name?
  If so, keep `onSessionClosed` and delete only `onSessionStarted`.
- `Q_cleanup_throw` — `SessionCleanupTest` makes the close listener throw, to prove that a
  failing cleanup does not wedge eviction. With no listener, is there any user code left on the
  close path that could throw? If not, the property is vacuous and the test case goes.
