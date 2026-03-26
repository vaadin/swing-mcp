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

---

## Acceptance Criteria

- [x] MCPServer has all necessary functionality
- [x] `SwingUtils.getTopmostModalDialog()` returns the topmost visible modal dialog (or null)
- [x] `MCPServer.getConsideredComponents()` delegates modal detection to `SwingUtils.getTopmostModalDialog()`
- [x] Tests are written and pass

---

## Tests

> Write UI tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] MCPServer test tests smoke-test: simply start and stop
  - [x] Additional test will use the official MCP SDK client to ping MCPServer and retrieve the list of tasks; don't assert on the number of tasks since that will change as tasks are implemented, simply assert that the list is not null.

