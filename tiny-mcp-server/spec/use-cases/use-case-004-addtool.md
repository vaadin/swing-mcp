# UC-004: TinyMCPServer.addTool

Implement `TinyMCPServer.addTool()` which accepts the following parameters:

* The tool name: not null, not blank, must match `[a-zA-Z_][a-zA-Z0-9_]*` (same convention as parameter names)
* The tool description: not null, not blank
* `InputSchema`; not null; in Javadoc recommend to use `InputSchemaBuilder`
* A `TinyMCPServer.ToolFunction` (custom `@FunctionalInterface`); not null

`TinyMCPServer.ToolFunction` is a nested functional interface that takes a `Map<String, Object>` and returns `MCPProtocol.Content`. It declares `throws Exception` so callers don't need to wrap checked exceptions. Its Javadoc should describe the map contents and return value contract.

The function has the following properties:

* The function will always be called with a non-null map, even if there are no parameters defined or passed.
* The function may return null: in such case produce an empty JSON array.
* The function will receive Java types as values: `String` for string, `Integer` for integer, `Double` for number, `Boolean` for boolean. It never receives raw JSON objects.
* If the function throws, return `isError`=true with text content set to `exception.toString()` (class name + message, no stacktrace).

`addTool()` constraints:

* Throws `IllegalStateException` if called after `start()`.
* Throws `IllegalStateException` if a tool with the same name is already registered.

`tools/list` must return all registered tools with their name, description, and `InputSchema` passed as-is.

`tools/call` dispatch:

* Tool not found → JSON-RPC error -32601 (Method not found).
* Parameter validation before invoking the function:
  * JSON numbers are deserialized as `Double` by GSON; for `integer` schema parameters, convert whole-number Doubles to `Integer`, reject fractional Doubles with -32602.
  * Missing required parameter → JSON-RPC error -32602 (Invalid params), message: `Invalid parameter '<name>'`.
  * Null parameter value treated as missing.
  * Unknown parameters silently ignored (log a warning).
* Tool invocation is synchronous on the HTTP handler thread.

Add convenient factory methods to `MCPProtocol.Content`:

* `Content.text(String text)`
* `Content.image(String data, String mimeType)` — `data` is base64-encoded
* `Content.audio(String data, String mimeType)` — `data` is base64-encoded
* `Content.resource(ResourceContents resource)`

Prerequisite: UC-003 implemented

**Status:** Implemented
**Date:** 2026-03-26

---

## Acceptance Criteria

- [x] The method is created
- [x] `tools/list` returns all registered tools (name, description, InputSchema passed as-is)
- [x] All tests created and pass

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] Test that null/blank/invalid-pattern tool names are rejected with `IllegalArgumentException`
- [x] Test that `addTool()` after `start()` throws `IllegalStateException`
- [x] Test that `addTool()` with a duplicate name throws `IllegalStateException`
- [x] Test `tools/list` via MCP client returns all registered tools with correct name, description, and InputSchema
- [x] Test function invocation, by running the server and calling the function via the MCP client
  - [x] Parameter passing to the function: test empty map, test all supported types (string, integer, number, boolean)
  - [x] Integer coercion: whole-number Double is converted to Integer; fractional Double returns -32602
  - [x] Missing required parameter returns JSON-RPC error -32602
  - [x] Unknown parameters are silently ignored (warning logged)
  - [x] Null parameter value treated as missing (required → -32602, optional → absent from map)
  - [x] Tool not found returns JSON-RPC error -32601
  - [x] Result handling: null content (empty array), text content, image content, audio content, resource content
  - [x] Exception handling: if the function throws, return isError=true with exception.toString() as text content

