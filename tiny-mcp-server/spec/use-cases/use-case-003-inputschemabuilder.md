# UC-003: Tool InputSchema Builder

Create a `InputSchemaBuilder` Java class.

Example usage (illustrative):
```java
InputSchema inputSchema = 
    new InputSchemaBuilder()
        .requiredInteger("ref", "The element reference number")
        .build();
```

The builder supports the following types: `string`, `integer`, `number`, `boolean`.

Example methods:
- `.requiredString(name, description)` / `.optionalString(name, description)`
- `.requiredInteger(name, description)` / `.optionalInteger(name, description)`
- `.build()` — produces the final schema object `MCPProtocol.InputSchema`

All build methods return `this`, to allow fluent API.
Both `name` and `description` must not be null nor blank; throw `IllegalArgumentException` if it is.

Parameters appear in the built `InputSchema` and in `toString()` in **insertion order** (the order they were added to the builder). This ordering is part of the contract.

Note: it is not allowed to add a parameter second time: attempting to add a parameter when it already exists
will throw `IllegalStateException`.

Builder's `toString()` method will produce the following succinct output:
```
a: integer, b: integer, ref: string
```
(note that the description is omitted).

Prerequisite: UC-002 implemented

**Status:** Implemented
**Date:** 2026-03-26

---

## Acceptance Criteria

- [x] InputSchemaBuilder Java class created
- [x] All methods created
- [x] All tests created

---

## Tests

> Write unit tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] Write InputSchemaBuilder test and test all types thoroughly. Use `toString()` instead of `build()` to make test methods more succinct.
- [x] Test the fluent API by chaining multiple calls
- [x] Test that insertion order is preserved in both `toString()` and `build()`
- [x] Test that duplicite parameter fails
- [x] Test the `.build()` method.
- [x] Test not-null/not-blank cases

