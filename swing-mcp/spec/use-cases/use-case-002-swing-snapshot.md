# UC-002: swing_snapshot

---

**As an** AI agent, **I want to** obtain an accessibility tree snapshot of the Swing application **so that** I can understand the current UI structure and identify components for interaction.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_snapshot` tool with no parameters.
- The tool walks the `javax.accessibility` tree of each considered window.
- The tool returns a compact indented text tree with each node showing role, name, states, available actions, current value, and a numeric ref.
  - Only include accessibility name and description if those are not blank.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Refs are short integers starting from 1, assigned fresh with each snapshot call. |
| BR-02 | Refs from a previous snapshot are invalidated after any interaction tool call. |
| BR-03 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |
| BR-04 | The output format is a compact indented text tree (not YAML), mimicking Playwright MCP. |

---

## Acceptance Criteria

- [ ] Calling `swing_snapshot` returns a text tree containing role, name, states, actions, and value for each accessible node.
- [ ] Each interactive node in the tree has a unique numeric `ref` starting from 1.
- [ ] A panel with a button and a text field produces a tree with the expected structure and refs.
- [ ] Nested component hierarchies are represented with correct indentation.
- [ ] Non-visible components are excluded from the tree.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

Understand that headless mode is on, which means you have to use JPanel instead of Window/Dialog/JFrame for testing.

- [ ] `SwingSnapshotTest`
  - [ ] A simple hierarchy (panel with button and text field) produces a tree with correct roles, names, and refs.
  - [ ] Refs are assigned starting from 1.
  - [ ] Nested containers produce correctly indented output.
  - [ ] Calling `swing_snapshot` via the MCP client returns a valid text response.
