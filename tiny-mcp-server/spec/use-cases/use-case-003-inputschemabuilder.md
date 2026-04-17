# UC-003: Tool InputSchema Builder

Create a `InputSchemaBuilder` Java class.

Example usage (illustrative):
```java
InputSchema inputSchema = 
    new InputSchemaBuilder()
        .requiredInteger("ref", "The element reference number")
        .build();
```

The builder supports the following types: `string`, `integer`, `number`, `boolean`, `array`, `object`.

Example methods:
- `.requiredString(name, description)` / `.optionalString(name, description)`
- `.requiredInteger(name, description)` / `.optionalInteger(name, description)`
- `.requiredArray(name, description)` / `.optionalArray(name, description)`
- `.requiredObject(name, description)` / `.optionalObject(name, description)`
- `.withEnum(String... values)` — adds `enum` constraint to the last added parameter; throws `IllegalArgumentException` if `values` is empty; throws `IllegalStateException` if called before any parameter is added, or if `enum` is already set for that parameter
- `.withMinimum(Number min)` / `.withMaximum(Number max)` — adds `minimum`/`maximum` constraint to the last added parameter; throws `IllegalStateException` if called before any parameter is added, or if already set for that parameter
- `.build()` — produces the final schema object `MCPProtocol.InputSchema`

All build methods return `this`, to allow fluent API.
Both `name` and `description` must not be null nor blank; throw `IllegalArgumentException` if they are.
Parameter `name` must also match the pattern `[a-zA-Z_][a-zA-Z0-9_]*` (starts with a letter or underscore, followed by alphanumeric characters or underscores); throw `IllegalArgumentException` otherwise.

Parameters appear in the built `InputSchema` and in `toString()` in **insertion order** (the order they were added to the builder). This ordering is part of the contract.

Note: it is not allowed to add a parameter second time: attempting to add a parameter when it already exists
will throw `IllegalStateException`.

Builder's `toString()` method will produce the following succinct output:
```
a: integer, b: integer?, ref: string, status: string(active|inactive), page: integer[1,], price: number[0.0,999.99], tags: array, config: object?
```
- Required parameters are shown as `type`, optional as `type?` (Kotlin/TypeScript convention)
- Enum constraints are shown as `type(val1|val2|...)`
- Range constraints are shown as `type[min,max]` — either bound may be empty if not set
- Description is omitted

**Status:** Implemented
**Date:** 2026-03-26

---

## Acceptance Criteria

- [x] InputSchemaBuilder Java class created
- [x] All methods created (string, integer, number, boolean, array, object)
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
- [x] Test that invalid parameter names (spaces, leading digit, special chars) throw `IllegalArgumentException`
- [x] Test `withEnum`, `withMinimum`, `withMaximum` in `toString()` and `build()`
- [x] Test that calling `withEnum`/`withMinimum`/`withMaximum` before any parameter throws `IllegalStateException`
- [x] Test that calling `withEnum`/`withMinimum`/`withMaximum` twice on the same parameter throws `IllegalStateException`
- [x] Test that constraints apply only to the last-added parameter
- [x] Test `requiredArray` and `optionalArray` in `toString()` and `build()`
- [x] Test `requiredObject` and `optionalObject` in `toString()` and `build()`

