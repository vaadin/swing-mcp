# UC-003: swing_screenshot

---

**As an** AI agent, **I want to** capture a screenshot of the Swing application **so that** I can visually inspect the current state of the UI.

**Status:** Implemented
**Date:** 2026-03-26

---

## Main Flow

- I call the `swing_screenshot` tool with no parameters.
- The tool calls `context.getConsideredComponents()` to obtain the list of components to render.
- The tool renders each considered component to a `BufferedImage` using `component.printAll(g)` on a freshly created `BufferedImage` sized to `component.getWidth()` × `component.getHeight()`. This captures the full Swing-rendered content including menu bar and internal borders, but not OS-managed window decorations (title bar, native border).
- If multiple components are considered, the tool arranges them vertically into a single image: composite width = max of all component widths; each component is horizontally centered; a 4 px gap separates consecutive components.
- The tool encodes the result as PNG and returns it as `MCPProtocol.Content.image(base64, "image/png")`.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Use `SwingToolContext.getConsideredComponents()` to determine which components to capture (modal-vs-all selection is handled upstream by `MCPServer`). |
| BR-02 | Multiple windows are arranged vertically in a single PNG: composite width is the max of all window widths; composite height is the sum of all window heights plus gaps between them; a 4 px gap (defined as a `static final int` field) is inserted between consecutive windows; narrower windows are horizontally centered. |
| BR-03 | If `getConsideredComponents()` returns an empty list, return an MCP error (`isError: true`) with a message indicating there are no visible windows and suggesting the caller retry. |
| BR-04 | The result is always a single PNG image returned as MCP image content (`type=image`, `mimeType=image/png`, base64-encoded). |
| BR-05 | All Swing component access happens on the EDT (via `MCPServer.runInEDT()`). |
| BR-06 | Each component is rendered via `component.printAll(g)`. `SwingUtilities.paintComponent()` is not used because it reparents the component via `CellRendererPane`, which is unsafe for top-level windows (`JFrame`/`JDialog`). `printAll` disables `RepaintManager` double buffering before delegating to `paint()` and restores it afterwards, producing the same on-screen visual output without side effects. |
| BR-07 | `BufferedImage` instances are created with type `TYPE_INT_RGB` (windows are assumed opaque). |
| BR-08 | Components with zero width or zero height are silently skipped (they are effectively invisible). If all components are skipped, BR-03 applies. |
| BR-09 | `swing_screenshot` is a read-only tool: `isMutation()` returns `false` and the ref map is not cleared after invocation. |

---

## Acceptance Criteria

- [x] Calling `swing_screenshot` returns MCP image content with `mimeType=image/png`.
- [x] The returned PNG is a valid image decodable by `ImageIO.read()`.
- [x] A single component produces an image with that component's exact dimensions (`getWidth()` × `getHeight()`).
- [x] Multiple visible components produce a single image with components stacked vertically, each horizontally centered, composite width = max of component widths, composite height = sum of component heights + 4 px gap between each pair.
- [x] If a modal window is visible, only that window appears in the image.
- [x] If no components are considered, the tool returns an MCP error response (`isError: true`).

---

## Tests

> Write tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

### Headless tests (`src/test`) — `SwingScreenshotTest`

In headless mode, the test uses `FakeMCPServer.setConsideredComponents()` to supply `JPanel` instances
sized via `panel.setSize(w, h)` + `panel.doLayout()` (never shown on screen).
`FakeMCPServer` already overrides `runInEDT()` to run the block inline.

- [x] A single sized `JPanel` produces a PNG with the expected dimensions.
- [x] The returned MCP result has `type=image` and `mimeType=image/png`.
- [x] The returned PNG is decodable by `ImageIO.read()`.
- [x] Calling `swing_screenshot` via the MCP client returns an image content response.
- [x] With an empty component list, `swing_screenshot` returns an MCP error response (`isError: true`).
- [x] A zero-size `JPanel` mixed with a normal-sized `JPanel` produces a PNG matching only the normal panel's dimensions.
- [x] Two sized `JPanel`s produce a single PNG with composite width = max of the two widths and composite height = sum of heights + 4 px gap.

#### Component matrix

Each component is placed inside a 200×100 `JPanel` (sized via `setSize` + `doLayout`) and rendered.
All components are expected to render successfully to a valid 200×100 PNG.

- [x] `JButton`
- [x] `JTextField`
- [x] `JPasswordField`
- [x] `JTextArea`
- [x] `JCheckBox`
- [x] `JRadioButton` (with `ButtonGroup`)
- [x] `JComboBox`
- [x] `JToggleButton`
- [x] `JSpinner`
- [x] `JSlider`
- [x] `JPanel`
- [x] `JScrollPane`
- [x] `JTabbedPane`
- [x] `JSplitPane`
- [x] `JLabel`
- [x] `JProgressBar`
- [x] `JMenuBar`
- [x] `JMenu`
- [x] `JMenuItem`
- [x] `JToolBar`
- [x] `JList`

### Screen-mode tests (`src/testSwing`) — `SwingScreenshotScreenTest`

Uses real `JFrame`/`JDialog` instances on an actual display.

- [x] A single visible `JFrame` produces a PNG with that frame's dimensions.
- [x] Multiple visible frames produce a single vertically stacked image with correct composite dimensions.
- [x] With a modal `JDialog` open, only the dialog is captured.
