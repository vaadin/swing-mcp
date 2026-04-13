# UC-[NNN]: [short-name]

> Copy this template for each feature as `use-case-NNN-short-name.md`.
> Replace all `[bracketed text]` with your content.

**Status:** [Draft | Approved | Implemented]
**Date:** [YYYY-MM-DD]

[One-sentence motivation — what this tool does and why it exists.
Preserve any non-obvious design rationale (e.g. "needed because the snapshot
omits X"), drop ceremony. No heading — this reads as a lead paragraph.]

**Tool description:** "[The exact MCP description string the LLM sees — omit if not applicable]"

---

## Rules

| ID | Rule |
|----|------|
| BR-01 | [Rule — e.g., the `ref` parameter is required and must be an integer.] |
| BR-02 | [Rule — validation / error path.] |
| BR-03 | [Rule — edge case or policy decision.] |

---

## Acceptance Criteria

- [ ] [Criterion 1 — testable statement of expected behaviour]
- [ ] [Criterion 2 — edge case or validation check]
- [ ] [Criterion 3]

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `[FeatureName]Test` (headless)
  - [ ] [What each test covers — map to acceptance criteria and business rules]

- [ ] `[FeatureName]ScreenTest` (`testSwing` — requires display; see `verification.md` § Component Matrix)
  - [ ] Happy-path operation on a component inside `JFrame`.
  - [ ] Happy-path operation on a component inside `JDialog`.
