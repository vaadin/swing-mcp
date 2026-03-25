# Verification

## 1. Unit Testing

Every use case must have unit tests before it is considered implemented. See `architecture.md` § Testing for tool setup and which test type to use per view type.

### Coverage Requirements

- Each acceptance criterion should be covered by at least one test
- Business rules must have dedicated tests (especially edge cases like limits, validation, and error handling)
- Tests must pass (`./gradlew test`) before the use case status is set to **Implemented**

### Naming Conventions

- **Test class**: `[FeatureName]Test.java` or `[FeatureName].test.tsx` (e.g., `BrowseMoviesTest`, `BuyTickets.test.tsx`)
- **Test methods**: descriptive names that map to acceptance criteria or business rules (e.g., `onlyItemsWithFutureEventsAreDisplayed`, `maximumSixItemsPerTransaction`)
- **Structure**: one test class per use case, with individual test methods for each acceptance criterion and business rule edge case

---

## 3. Per-Use-Case Verification Checklist

### UC-001: Message JSONs

**Use case spec:** [`UC-001`](use-cases/use-case-001-message-jsons.md)
**Verified by:** Claude
**Date:** 2026-03-25

#### Automated Tests

- [x] Test class exists and all tests pass (`./gradlew test --tests MCPProtocolTest`)
- [x] Acceptance criteria covered by tests
- [x] Business rule edge cases tested

#### Result

- **Status:** Pass
- **Notes:** 40+ test methods in MCPProtocolTest covering all MCP message types (initialize, ping, tools, resources, prompts, completion, logging, sampling, roots, notifications), round-trip serialization, toString/equals/hashCode, and edge cases (unknown fields, null field omission).

### UC-002: Skeletal MCP Implementation

**Use case spec:** [`UC-002`](use-cases/use-case-002-skeletal-mcp-server.md)
**Verified by:** [Name/Agent]
**Date:** [YYYY-MM-DD]

#### Automated Tests

- [ ] Test class exists and all tests pass (`./gradlew test --tests ClassName`)
- [ ] Acceptance criteria covered by tests
- [ ] Business rule edge cases tested

#### Result

- **Status:** [Pass / Fail / Partial]
- **Notes:** [Any issues found or follow-up items]
