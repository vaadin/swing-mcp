# UC-001: MCP Protocol JSON POJO objects

In order to implement a MCP server, we must prepare some groundwork.
This UC implements a set of POJO objects, mapped to JSON via
the GSON library. All possible MCP JSONs must be covered.

**Status:** [Draft | Approved | Implemented]
**Date:** [YYYY-MM-DD]

---

## Main Flow

Create Java POJO objects for every JSON message
present in the MCP specification from 2025-06-18.
Also create unit tests, one test method for every JSON message.

For convenience:

1. MCPProtocol has a static GSON singleton instance, and `fromJson()` and `toJson()` functions, which parse JSON to POJO and serialize given POJO to JSON.
2. A convenience interface MCPProtocol.IsJson which provides `toJson()` function which turns this POJO into a JSON String using `MCPProtocol.toJson()`

---

## Acceptance Criteria

- [ ] MCPProtocol static GSON instance created
- [ ] MCPProtocol fromJson()/toJson() functions created
- [ ] POJO generated for every JSON message
- [ ] Every POJO implements MCPProtocol.IsJson
- [ ] Every POJO overrides `toString()` which simply calls `toJson()`
- [ ] Every POJO is mutable, with getters and setters.
- [ ] Every POJO has equals()/hashCode() implemented, which simply
   consult String returned by toJson()

---

## Tests

> Write unit tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] Write a test for every type of JSON message specified by the MCP protocol, testing JSON serialization and deserialization of an example message.
- [ ] Make sure Initialization request message and response message is tested
