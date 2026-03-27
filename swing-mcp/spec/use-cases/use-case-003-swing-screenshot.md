# UC-003: swing_screenshot

---

**As an** AI agent, **I want to** capture a screenshot of the Swing application **so that** I can visually inspect the current state of the UI.

**Status:** Draft
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_screenshot` tool with no parameters.
- The tool determines which components are considered (modal window only, or all visible windows).
- The tool renders each considered component to a `BufferedImage` using `component.paint(g)` on a freshly created `BufferedImage` sized to `component.getWidth()` × `component.getHeight()`. This captures the full Swing-rendered content including menu bar and internal borders, but not OS-managed window decorations (title bar, native border).
- If multiple components are considered, the tool arranges them vertically into a single image: composite width = max of all component widths; each component is horizontally centered; no overlapping.
- The components are ordered by creation order (as returned by `Window.getWindows()`).
- The tool encodes the result as PNG and returns it as `MCPProtocol.Content.image(base64, "image/png")`.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | If a modal window is visible, only that window is captured. |
| BR-02 | If no modal window is visible, all visible windows are captured. |
| BR-03 | Multiple windows are arranged vertically in a single PNG: composite width is the max of all window widths; narrower windows are horizontally centered; no overlapping. |
| BR-04 | The result is always a single PNG image returned as MCP image content (`type=image`, `mimeType=image/png`, base64-encoded). |
| BR-05 | All Swing component access happens on the EDT (via `MCPServer.runInEDT()`). |
| BR-06 | Each component is rendered via `component.paint(g)` (not `printAll`) to capture the visual state as the user sees it. |

---

## Acceptance Criteria

- [ ] Calling `swing_screenshot` returns MCP image content with `mimeType=image/png`.
- [ ] The returned PNG is a valid image decodable by `ImageIO.read()`.
- [ ] A single component produces an image with that component's exact dimensions (`getWidth()` × `getHeight()`).
- [ ] Multiple visible components produce a single image with components stacked vertically, each horizontally centered, composite width = max of component widths.
- [ ] If a modal window is visible, only that window appears in the image.

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

### Headless tests (`src/test`) — `SwingScreenshotTest`

In headless mode, the test overrides `MCPServer.getConsideredComponents()` to return `JPanel` instances
sized via `panel.setSize(w, h)` + `panel.doLayout()` (never shown on screen).
The test overrides `runInEDT()` to run the block inline.

- [ ] A single sized `JPanel` produces a PNG with the expected dimensions.
- [ ] The returned MCP result has `type=image` and `mimeType=image/png`.
- [ ] The returned PNG is decodable by `ImageIO.read()`.
- [ ] Calling `swing_screenshot` via the MCP client returns an image content response.

### Screen-mode tests (`src/testSwing`) — `SwingScreenshotScreenTest`

Uses real `JFrame`/`JDialog` instances on an actual display.

- [ ] A single visible `JFrame` produces a PNG with that frame's dimensions.
- [ ] Multiple visible frames produce a single vertically stacked image with correct composite dimensions.
- [ ] With a modal `JDialog` open, only the dialog is captured.
