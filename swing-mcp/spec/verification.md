# Verification

Testing involves running a Swing app. The `javax.accessibility` API works in
headless mode. The screenshot capturing functionality probably requires Xvfb,
and will be tested elsewhere.

In headless mode, tests can't instantiate Window. Therefore,
the window Selection functionality must be overridable in MCPServer,
and must be overriden by tests (which provide a list of JPanels instead).

There are two test source sets:

### `src/test` — Headless tests (default `test` task)

- Pure JUnit 6 tests living in `src/test/java/`, mirroring the main package structure.
- Also uses MCP client library to call MCP tasks.
- Sets `java.awt.headless` to `true` for fast, display-free execution.
- The `MCPServerTest` test class:
    - Starts the MCPServer before all tests, stops it afterwards.
    - A test client is initialized before all tests as well; use the official MCP client with the HTTP Transport and Jackson3.
    - No tools are tested here: each tool has its own separate test class.
- For every tool test class:
    - Remember we are headless.
    - `MCPServer.runInEDT(block)` uses `invokeLater` + `CountDownLatch` and would hang waiting for the EDT in headless mode. Tests override `runInEDT` in `FakeMCPServer` to run the block directly on the calling thread instead.

### `src/testSwing` — Screen-mode tests (`testSwing` task)

- Runs with `java.awt.headless = false`, so actual Swing rendering works.
- Requires a real display (Xvfb is used in CI).
- Registered as the `testSwing` Gradle task in the `verification` group; included in the `check` lifecycle.
- Reports go to `build/reports/testSwing/` and `build/test-results/testSwing/` (separate from headless test reports).
- Base class: `AbstractScreenTest`; inherits from it for screen-dependent tool tests.
- These tests should primarily focus on testing with JFrame/JDialog/Dialog/Window since those Swing components require screen.

## 1. Unit Testing

Every use case must have unit tests before it is considered implemented. See `architecture.md` § Testing for tool setup and which test type to use per view type.

### Coverage Requirements

- Each acceptance criterion should be covered by at least one test
- Business rules must have dedicated tests (especially edge cases like limits, validation, and error handling)
- Tests must pass (`./gradlew test`) before the use case status is set to **Implemented**

### Component Matrix

Every tool must be tested against the standard Swing components listed below. Some
tools will succeed on a given component (e.g., `swing_click` on a `JButton`),
while others will correctly fail (e.g., `swing_click` on a `JLabel`). Both
outcomes are valuable test data.

Note: some components do not receive refs. In order to test those, you
register them to the context under a testing ID (for example 99),
then call the tool with the testing ID. If it's supposed to fail,
check that MCPServerException was thrown. Do **not** simply assert that `context.getRefOf(component)`
fails with an IllegalStateException - that is not testing the tool error checking itself.

**Interactive / Form inputs:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`,
`JRadioButton` (with `ButtonGroup`), `JComboBox`, `JToggleButton`,
`JSpinner`, `JSlider`

**Containers / structural:**
`JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JDesktopPane`

**Display:**
`JLabel`, `JProgressBar`

**Menus:**
`JMenuBar`, `JMenu`, `JMenuItem`
(note: `JMenu` is a structural container, not an interactive target — see **DR-012**.
Tools that apply to menu titles in principle — most notably `swing_click` — must
assert the DR-012 "JMenu does not support click" outcome. `JMenuItem` remains
interactive as usual.)

**Other:**
`JToolBar`, `JList` (simple single-selection), `JTree`

**Internal windows:**
`JInternalFrame`
(note: for headless component-matrix tests, create a `JDesktopPane`, add the `JInternalFrame` to it,
and add the desktop pane to a `JPanel` — no display needed.
For screen-mode tests, host the `JDesktopPane` inside a visible `JFrame`).

**Top-level windows (screen required — `testSwing` sources):**
`JFrame`, `JDialog`, `JOptionPane`
(note: test on the JFrame/JDialog/JOptionPane component itself, not on a component nested in them. For example,
we want to check that the swing_toggle_expand tool fails cleanly on JFrame since it's not a JTree node.
For JOptionPane, create a JOptionPane instance and add it to a JDialog's content pane — do **not** use
`JOptionPane.showXxxDialog()` in tests, as it blocks the calling thread).

Each tool test class should include a test method per component from this list,
verifying the tool's behavior (successful operation or appropriate error).
Tests for `JFrame`, `JDialog`, and `JInternalFrame` (inside a visible `JDesktopPane`)
require a display and must live in the `testSwing` source set
(run via `./gradlew :swing-mcp:testSwing`).

### Naming Conventions

- **Test class**: `[FeatureName]Test.java`
- **Test methods**: descriptive names that map to acceptance criteria or business rules (e.g., `onlyItemsWithFutureEventsAreDisplayed`, `maximumSixItemsPerTransaction`)
- **Structure**: one test class per use case, with individual test methods for each acceptance criterion and business rule edge case

## Always Test

* Always smoke test the tool with an actual MCP client: start MCPServer, call the tool with the most simple
  case, then stop the server.
* Prefer assert against the full contents of the string (even if the string is long);
  avoid `.contains()`, `.startWith()` since those make tests less readable.