# Changelog

User-visible changes to the released `swing-mcp-agent` jar, newest first; the format is
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A change lands under `[Unreleased]`
in the commit that makes it; `RELEASING.md` turns that section into a version.

## [Unreleased]

### Added

- First release: `swing-mcp-agent`, a `-javaagent` fat jar that starts an in-process MCP HTTP
  server at `http://127.0.0.1:18088/mcp` (port from `-Dswing.mcp.port`) before `main()` runs.
- `swing_snapshot`, a text snapshot of every window the user can interact with, and
  `swing_screenshot`.
- Interaction tools that go through the real listener tree: click, set text or value,
  selection, expand / collapse, popups, drag and drop, and window close / iconify / restore.
  The full list and the Swing components each one supports are in the README.
