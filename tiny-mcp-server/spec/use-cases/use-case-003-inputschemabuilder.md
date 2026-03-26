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

Builder's `toString()` method will produce the following succinct output:
```
a: integer, b: integer, ref: string
```
(note that the description is omitted).

Prerequisite: UC-002 implemented

**Status:** [Draft | Approved | Implemented]
**Date:** [YYYY-MM-DD]

---

## Acceptance Criteria

- [ ] InputSchemaBuilder Java class created
- [ ] All methods created
- [ ] All tests created

---

## Tests

> Write unit tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] Write InputSchemaBuilder test and test all types thoroughly. Use `toString()` instead of `build()` to make test methods more succinct.
- [ ] Test the fluent API by chaining multiple calls
- [ ] Test the `.build()` method.

