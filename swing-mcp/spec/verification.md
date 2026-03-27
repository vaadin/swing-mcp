# Verification

## 1. Unit Testing

Every use case must have unit tests before it is considered implemented. See `architecture.md` § Testing for tool setup and which test type to use per view type.

### Coverage Requirements

- Each acceptance criterion should be covered by at least one test
- Business rules must have dedicated tests (especially edge cases like limits, validation, and error handling)
- Tests must pass (`./gradlew test`) before the use case status is set to **Implemented**

### Component Matrix

Every tool must be tested against a standard set of 20 Swing components. Some
tools will succeed on a given component (e.g., `swing_click` on a `JButton`),
while others will correctly fail (e.g., `swing_click` on a `JLabel`). Both
outcomes are valuable test data.

**Interactive / Form inputs:**
`JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`,
`JRadioButton` (with `ButtonGroup`), `JComboBox`, `JToggleButton`,
`JSpinner`, `JSlider`

**Containers / structural:**
`JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`

**Display:**
`JLabel`, `JProgressBar`

**Menus:**
`JMenuBar`, `JMenu`, `JMenuItem`

**Other:**
`JToolBar`, `JList` (simple single-selection)

Each tool test class should include a test method per component from this list,
verifying the tool's behavior (successful operation or appropriate error).

### Naming Conventions

- **Test class**: `[FeatureName]Test.java`
- **Test methods**: descriptive names that map to acceptance criteria or business rules (e.g., `onlyItemsWithFutureEventsAreDisplayed`, `maximumSixItemsPerTransaction`)
- **Structure**: one test class per use case, with individual test methods for each acceptance criterion and business rule edge case

## Always Test

* Always smoke test the tool with an actual MCP client: start MCPServer, call the tool with the most simple
  case, then stop the server.
* Prefer assert against the full contents of the string (even if the string is long);
  avoid `.contains()`, `.startWith()` since those make tests less readable.