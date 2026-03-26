# UC-003: swing_screenshot

---

**As an** AI agent, **I want to** capture a screenshot of the Swing application **so that** I can visually inspect the current state of the UI.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_screenshot` tool with no parameters.
- The tool determines which windows are considered (modal window only, or all visible windows).
- The tool renders each considered window to a `BufferedImage` via `window.paint()`.
- If multiple windows are considered, the tool arranges them vertically into a single image with no overlapping.
- The tool returns the result as a PNG image.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | If a modal window is visible, only that window is captured. |
| BR-02 | If no modal window is visible, all visible windows are captured. |
| BR-03 | Multiple windows are arranged vertically in a single PNG with no overlapping. |
| BR-04 | The result is always a single PNG image. |
| BR-05 | All Swing component access happens on the EDT via `SwingUtilities.invokeAndWait()`. |

---

## Acceptance Criteria

- [ ] Calling `swing_screenshot` returns a PNG image.
- [ ] The returned PNG has dimensions matching the rendered window(s).
- [ ] A single window produces an image with that window's dimensions.
- [ ] Multiple visible windows produce a single image with windows stacked vertically.
- [ ] The PNG is a valid image that can be decoded.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [ ] `SwingScreenshotTest`
  - [ ] A single component hierarchy produces a PNG with expected dimensions.
  - [ ] The returned MCP result is a valid PNG image (decodable by `ImageIO.read()`).
  - [ ] Calling `swing_screenshot` via the MCP client returns an image response.
