# Project Context

Implements the Swing MCP server itself.

Leverages the `javax.accessibility` API to construct an accessibility
tree, which is then served as a YAML snapshot to a MCP client
(Claude Code).

Leverages Swing built-in capability to obtain screenshot of the app by
rendering Swing Windows
(via `window.paint()` to a `BufferedImage` graphics).

Only considers visible Windows of the app. If there is a modal visible window,
only consider that particular window (use `KeyboardFocusManager` to figure out
the current modal window); if there is no modal window, consider all
visible Windows. When creating YAML snapshot, all considered windows will be present.
When creating a screenshot, always create one PNG image: if there are multiple
Windows to be considered, they should be arranged vertically in the PNG image
with no overlapping.

Care must be taken to always access Swing component in EDT.

Testing: testing involves running a Swing app. TODO verify whether `javax.accessibility`
API works in headless mode: if yes, we can test way simpler. However,
the screenshot capturing functionality probably requires Xvfb.

This subproject needs to be specified further and is currently not to be implemented. Ignore this subproject for now.
