# UC-001: MCPServer

Let's get necessary infrastructure in place.

**Status:** Implemented
**Date:** 2026-03-26

---

## Main Flow

The Swing application creates the MCPServer upon boot, calls start(), and then
goes on as usual. MCPServer also offers stop() function which stops the server.
However, this will be primarily intended for internal MCPServer testing.
The Swing app simply terminates, killing MCPServer as well. MCPServer could
introduce a JVM shutdown hook to stop itself cleanly: a startAndAutoStop() perhaps - this is the function Swing Apps should use.

To support future tests, let's introduce a FakeMCPServer which extends MCPServer, goes into the test sources, and:

1. Overrides MCPServer.runInEDT() and calls the Callable right away
2. Provides setConsideredComponents() which allows tests to set a `List<Component>` to be returned by any subsequent calls to getConsideredComponents(). Modify `getConsideredComponents()` accordingly.
For thread safety, wrap given List in CopyOnWriteArrayList and store into a volatile private field.

---

## Acceptance Criteria

- [x] MCPServer has all necessary functionality
- [x] `SwingUtils.getTopmostModalDialog()` returns the topmost visible modal dialog (or null)
- [x] `MCPServer.getConsideredComponents()` delegates modal detection to `SwingUtils.getTopmostModalDialog()`
- [x] `MCPServer.runInEDT(Callable<T>)` dispatches to EDT via `SwingUtilities.invokeLater()` + `CountDownLatch`, waits up to `EDT_TIMEOUT_MS` (10 s), and throws `MCPErrorResponseException` with the EDT stack trace if the EDT does not respond in time; protected so tests can override to run directly
- [x] Tests are written and pass

---

## Tests

> Write UI tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] MCPServer test tests smoke-test: simply start and stop
  - [x] Additional test will use the official MCP SDK client to ping MCPServer and retrieve the list of tasks; don't assert on the number of tasks since that will change as tasks are implemented, simply assert that the list is not null.

