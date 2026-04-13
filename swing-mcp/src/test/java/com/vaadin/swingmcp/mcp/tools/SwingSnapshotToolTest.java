package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.mcp.ClickRecordingPanel;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSnapshotToolTest extends AbstractHeadlessTest {

    // ── Instance-level setup for direct tool invocation ───────────────────────

    private SwingSnapshotTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        tool = new SwingSnapshotTool();
        context = new SwingToolContext();
    }

    /**
     * Runs a snapshot with the given components as roots and returns the text output.
     */
    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = tool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    private String snapshot(String filterSubstring, Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("filter_substring", filterSubstring)), context);
        return result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void simpleHierarchyProducesCorrectRolesNamesAndRefs() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Save");
        JTextField textField = new JTextField();
        panel.add(button);
        panel.add(textField);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click\n"
                + "  - text [ref=2] actions: get_text, set_text",
                output);
    }

    @Test
    void refsAreAssignedStartingFromOne() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("First"));
        panel.add(new JButton("Second"));

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"First\" [ref=1] actions: click\n"
                + "  - push_button \"Second\" [ref=2] actions: click",
                output);
    }

    @Test
    void nestedContainersAreCorrectlyIndented() throws Exception {
        // Named outer panel → named inner panel → button
        JPanel outer = new JPanel();
        outer.getAccessibleContext().setAccessibleName("Outer");
        JPanel inner = new JPanel();
        inner.getAccessibleContext().setAccessibleName("Inner");
        JButton button = new JButton("Go");
        inner.add(button);
        outer.add(inner);

        String output = snapshot(outer);

        assertEquals(
                "- panel \"Outer\"\n"
                + "  - panel \"Inner\"\n"
                + "    - push_button \"Go\" [ref=1] actions: click",
                output);
    }

    @Test
    void invisibleComponentsAreExcluded() throws Exception {
        JPanel panel = new JPanel();
        JButton visible = new JButton("Visible");
        JButton invisible = new JButton("Hidden");
        invisible.setVisible(false);
        panel.add(visible);
        panel.add(invisible);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Visible\" [ref=1] actions: click",
                output);
    }

    @Test
    void unnamedPanelsAreTransparentlyPruned() throws Exception {
        // Root (named so it stays), unnamed child panel, button inside unnamed panel
        JPanel root = new JPanel();
        root.getAccessibleContext().setAccessibleName("Root");
        JPanel unnamed = new JPanel(); // no name → transparent
        JButton button = new JButton("Click");
        unnamed.add(button);
        root.add(unnamed);

        String output = snapshot(root);

        // The button should appear directly under root (depth 1), not at depth 2
        assertEquals(
                "- panel \"Root\"\n"
                + "  - push_button \"Click\" [ref=1] actions: click",
                output);
    }

    @Test
    void namedPanelWithTitledBorderIsKept() throws Exception {
        JPanel root = new JPanel();
        JPanel titled = new JPanel();
        titled.setBorder(new TitledBorder("Details"));
        JButton button = new JButton("OK");
        titled.add(button);
        root.add(titled);

        String output = snapshot(root);

        // The titled panel has a TitledBorder → must be kept, button at depth 2
        assertEquals(
                "- panel\n"
                + "  - panel \"Details\"\n"
                + "    - push_button \"OK\" [ref=1] actions: click",
                output);
    }

    @Test
    void namedPanelWithAccessibleNameIsKept() throws Exception {
        JPanel root = new JPanel();
        JPanel named = new JPanel();
        named.getAccessibleContext().setAccessibleName("FormSection");
        JButton button = new JButton("Submit");
        named.add(button);
        root.add(named);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - panel \"FormSection\"\n"
                + "    - push_button \"Submit\" [ref=1] actions: click",
                output);
    }

    @Test
    void cellRendererPaneIsExcluded() throws Exception {
        // Make CellRendererPane appear in the accessibility tree by subclassing it
        // to implement Accessible; then add it as a component to a JPanel so it
        // surfaces as an accessible child.
        JPanel panel = new JPanel();
        JButton button = new JButton("Real");
        AccessibleCellRendererPane crp = new AccessibleCellRendererPane();
        panel.add(button);
        panel.add(crp);

        String output = snapshot(panel);

        // The CellRendererPane should be excluded (HE-2); only the real button appears
        assertEquals(
                "- panel\n"
                + "  - push_button \"Real\" [ref=1] actions: click",
                output);
    }

    @Test
    void frameworkRolesAreTransparentlyPruned() throws Exception {
        // JRootPane introduces root_pane, layered_pane, and optionally glass_pane.
        // Place a JRootPane inside a wrapper panel; its internal structure should disappear.
        JPanel wrapper = new JPanel();
        JRootPane rootPane = new JRootPane();
        JButton button = new JButton("Action");
        rootPane.getContentPane().add(button);
        wrapper.add(rootPane);

        String output = snapshot(wrapper);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Action\" [ref=1] actions: click",
                output);
    }

    @Test
    void fillerIsTransparentlyPruned() throws Exception {
        // Box.createRigidArea() produces a Box.Filler with AccessibleRole.FILLER.
        // It is a pure layout spacer — transparent pruning drops it; its children
        // (none) are promoted, so it simply disappears.
        JPanel root = new JPanel();
        root.getAccessibleContext().setAccessibleName("Root");
        root.add(Box.createRigidArea(new Dimension(10, 10)));
        root.add(new JButton("OK"));

        String output = snapshot(root);

        assertEquals(
                "- panel \"Root\"\n"
                + "  - push_button \"OK\" [ref=1] actions: click",
                output);
    }

    @Test
    void scrollPaneIsKeptViewportIsPruned() throws Exception {
        JPanel root = new JPanel();
        JList<String> list = new JList<>(new String[]{"A", "B"});
        JScrollPane scrollPane = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scrollPane);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - scroll_pane\n"
                + "    - list [ref=1] actions: multi-selection\n"
                + "      - label \"A\" [ref=2] actions: click\n"
                + "      - label \"B\" [ref=3] actions: click\n"
                + "    - scroll_bar [ref=4, vertical] actions: get_value, set_value\n"
                + "      - push_button [ref=5] actions: click\n"
                + "      - push_button [ref=6] actions: click\n"
                + "    - scroll_bar [ref=7, horizontal] actions: get_value, set_value\n"
                + "      - push_button [ref=8] actions: click\n"
                + "      - push_button [ref=9] actions: click",
                output);
    }

    @Test
    void largeJTableIsTruncatedWithRowSummary() throws Exception {
        DefaultTableModel model = new DefaultTableModel(15, 1);
        for (int i = 0; i < 15; i++) {
            model.setValueAt("row" + i, i, 0);
        }
        JTable table = new JTable(model);
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        // UC-002 step 6b / UC-020 BR-03: JTable does NOT advertise
        // get_cell_count / get_cells — use swing_get_items instead.
        assertEquals(
                "- panel\n"
                + "  - table [ref=1] actions: multi-selection\n"
                + "    - row 0: row0\n"
                + "    - row 1: row1\n"
                + "    - row 2: row2\n"
                + "    - row 3: row3\n"
                + "    - row 4: row4\n"
                + "    ... and 10 more rows",
                output);
    }

    @Test
    void largeJListAdvertisesGetCellsAndGetCellCount() throws Exception {
        // UC-002 step 6b: truncated JList advertises get_cell_count + get_cells.
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);

        String output = snapshot(list);

        String firstLine = output.split("\n")[0];
        assertTrue(firstLine.contains("get_cell_count"),
                "Truncated JList should advertise get_cell_count, got: " + firstLine);
        assertTrue(firstLine.contains("get_cells"),
                "Truncated JList should advertise get_cells, got: " + firstLine);
    }

    @Test
    void largeJTreeAdvertisesGetCellsAndGetCellCount() throws Exception {
        // UC-002 step 6b: truncated JTree advertises get_cell_count + get_cells.
        javax.swing.tree.DefaultMutableTreeNode root =
                new javax.swing.tree.DefaultMutableTreeNode("Root");
        for (int i = 0; i < 20; i++) {
            root.add(new javax.swing.tree.DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);

        String output = snapshot(tree);

        String firstLine = output.split("\n")[0];
        assertTrue(firstLine.contains("get_cell_count"),
                "Truncated JTree should advertise get_cell_count, got: " + firstLine);
        assertTrue(firstLine.contains("get_cells"),
                "Truncated JTree should advertise get_cells, got: " + firstLine);
    }

    @Test
    void jTableMultiColumnRendersRowsWithPipeSeparator() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"1", "Alice", "NY"}, {"2", "Bob", "LA"}},
                new Object[]{"ID", "Name", "City"}));
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        // No JScrollPane → no columns: annotation
        assertEquals(
                "- panel\n"
                + "  - table [ref=1] actions: multi-selection\n"
                + "    - row 0: 1 | Alice | NY\n"
                + "    - row 1: 2 | Bob | LA",
                output);
    }

    @Test
    void jTableInScrollPaneShowsColumnHeaders() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"1", "Alice", "NY"}, {"2", "Bob", "LA"}},
                new Object[]{"ID", "Name", "City"}));
        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel root = new JPanel();
        root.add(scrollPane);

        String output = snapshot(root);

        // In JScrollPane → columns: annotation, header suppressed (SC-6/SC-7)
        assertEquals(
                "- panel\n"
                + "  - scroll_pane\n"
                + "    - table [ref=1] columns: [ID, Name, City] actions: multi-selection\n"
                + "      - row 0: 1 | Alice | NY\n"
                + "      - row 1: 2 | Bob | LA\n"
                + "    - scroll_bar [ref=2, vertical] actions: get_value, set_value\n"
                + "      - push_button [ref=3] actions: click\n"
                + "      - push_button [ref=4] actions: click\n"
                + "    - scroll_bar [ref=5, horizontal] actions: get_value, set_value\n"
                + "      - push_button [ref=6] actions: click\n"
                + "      - push_button [ref=7] actions: click",
                output);
    }

    @Test
    void jTableNotInScrollPaneOmitsColumnHeaders() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice"}},
                new Object[]{"Name"}));
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - table [ref=1] actions: multi-selection\n"
                + "    - row 0: Alice",
                output);
    }

    @Test
    void jTableWithZeroColumnsInScrollPane() throws Exception {
        JTable table = new JTable(new DefaultTableModel(0, 0));
        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel root = new JPanel();
        root.add(scrollPane);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - scroll_pane\n"
                + "    - table [ref=1] columns: [] actions: multi-selection\n"
                + "    - scroll_bar [ref=2, vertical] actions: get_value, set_value\n"
                + "      - push_button [ref=3] actions: click\n"
                + "      - push_button [ref=4] actions: click\n"
                + "    - scroll_bar [ref=5, horizontal] actions: get_value, set_value\n"
                + "      - push_button [ref=6] actions: click\n"
                + "      - push_button [ref=7] actions: click",
                output);
    }

    @Test
    void jTableWithRowsButZeroColumns() throws Exception {
        JTable table = new JTable(new DefaultTableModel(3, 0));
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - table [ref=1] actions: multi-selection\n"
                + "    - row 0: \n"
                + "    - row 1: \n"
                + "    - row 2: ",
                output);
    }

    @Test
    void jTableHeaderSuppressedFromSnapshot() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice"}},
                new Object[]{"Name"}));
        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel root = new JPanel();
        root.add(scrollPane);

        String output = snapshot(root);

        // SC-7: JTableHeader suppressed, columns: annotation present instead
        assertEquals(
                "- panel\n"
                + "  - scroll_pane\n"
                + "    - table [ref=1] columns: [Name] actions: multi-selection\n"
                + "      - row 0: Alice\n"
                + "    - scroll_bar [ref=2, vertical] actions: get_value, set_value\n"
                + "      - push_button [ref=3] actions: click\n"
                + "      - push_button [ref=4] actions: click\n"
                + "    - scroll_bar [ref=5, horizontal] actions: get_value, set_value\n"
                + "      - push_button [ref=6] actions: click\n"
                + "      - push_button [ref=7] actions: click",
                output);
    }

    @Test
    void menuItemsAppearEvenWhenMenuIsClosed() throws Exception {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        JMenuItem saveItem = new JMenuItem("Save");
        menu.add(openItem);
        menu.add(saveItem);
        menuBar.add(menu);

        JPanel root = new JPanel();
        root.add(menuBar);

        String output = snapshot(root);

        assertEquals(
                "- panel\n"
                + "  - menu_bar\n"
                + "    - menu \"File\" [ref=1] actions: click\n"
                + "      - menu_item \"Open\" [ref=2] actions: click\n"
                + "      - menu_item \"Save\" [ref=3] actions: click",
                output);
    }

    @Test
    void tabbedPaneShowsSelectedTabMarkedSelected() throws Exception {
        JPanel panel = new JPanel();
        JPanel tab1Content = new JPanel();
        tab1Content.add(new JButton("InTab1"));
        JPanel tab2Content = new JPanel();
        tab2Content.add(new JButton("InTab2"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Tab1", tab1Content);
        tabbedPane.addTab("Tab2", tab2Content);
        tabbedPane.setSelectedIndex(0);
        panel.add(tabbedPane);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - page_tab_list \"Tab1\" [ref=1] actions: single-selection\n"
                + "    - page_tab 0 \"Tab1\" [selected]\n"
                + "      - push_button \"InTab1\" [ref=2] actions: click\n"
                + "    - page_tab 1 \"Tab2\"",
                output);
    }

    @Test
    void tabbedPane_tabDisabledViaSetEnabledAt_marksOnlyThatTabDisabled() throws Exception {
        // setEnabledAt(1, false) disables tab 2 (the AccessiblePage). The tab itself
        // should be marked [disabled] in the snapshot. The other tab and the
        // page_tab_list itself remain enabled.
        JPanel panel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Tab1", new JPanel());
        tabbedPane.addTab("Tab2", new JPanel());
        tabbedPane.setSelectedIndex(0);
        tabbedPane.setEnabledAt(1, false);
        panel.add(tabbedPane);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - page_tab_list \"Tab1\" [ref=1] actions: single-selection\n"
                + "    - page_tab 0 \"Tab1\" [selected]\n"
                + "    - page_tab 1 \"Tab2\" [disabled]",
                output);
    }

    @Test
    void tabbedPane_buttonOnDisabledTab_isStillClickable() throws Exception {
        // The disabled tab is the selected one. Per Swing semantics, buttons on
        // it are still mechanically clickable (setEnabled does not propagate),
        // so the snapshot must report the button as enabled even though the
        // hosting page_tab is [disabled]. Mirrors Swing exactly.
        JPanel panel = new JPanel();
        JPanel tabContent = new JPanel();
        tabContent.add(new JButton("OnDisabled"));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("OnlyTab", tabContent);
        tabbedPane.setSelectedIndex(0);
        tabbedPane.setEnabledAt(0, false);
        panel.add(tabbedPane);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - page_tab_list \"OnlyTab\" [ref=1] actions: single-selection\n"
                + "    - page_tab 0 \"OnlyTab\" [disabled, selected]\n"
                + "      - push_button \"OnDisabled\" [ref=2] actions: click",
                output);
    }

    @Test
    void tabbedPane_fourTabs_indicesAscendFromZero() throws Exception {
        // Regression guard: tab indices are 0-based and strictly ascending
        // across the page_tab_list, with the selected tab carrying [selected]
        // and disabled tabs carrying [disabled]. Mixes states so a regression
        // in ctx.getAccessibleIndexInParent() ordering would surface here.
        JPanel panel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Alpha", new JPanel());
        tabbedPane.addTab("Beta", new JPanel());
        tabbedPane.addTab("Gamma", new JPanel());
        tabbedPane.addTab("Delta", new JPanel());
        tabbedPane.setSelectedIndex(2);
        tabbedPane.setEnabledAt(1, false);
        tabbedPane.setEnabledAt(3, false);
        panel.add(tabbedPane);

        assertEquals(
                "- panel\n"
                + "  - page_tab_list \"Gamma\" [ref=1] actions: single-selection\n"
                + "    - page_tab 0 \"Alpha\"\n"
                + "    - page_tab 1 \"Beta\" [disabled]\n"
                + "    - page_tab 2 \"Gamma\" [selected]\n"
                + "    - page_tab 3 \"Delta\" [disabled]",
                snapshot(panel));
    }

    @Test
    void onlyMeaningfulStatesAreShown() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Normal");
        panel.add(button);

        String output = snapshot(panel);

        // Only ref is shown; visible/enabled/showing/opaque are omitted
        assertEquals(
                "- panel\n"
                + "  - push_button \"Normal\" [ref=1] actions: click",
                output);
    }

    @Test
    void disabledComponentAppearsWithDisabledState() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        panel.add(button);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Disabled\" [ref=1, disabled] actions: !click",
                output);
    }

    @Test
    void disabledSliderShowsPrefixedMutationsAndUnprefixedReadOnly() throws Exception {
        JPanel panel = new JPanel();
        JSlider slider = new JSlider(0, 100, 50);
        slider.setEnabled(false);
        panel.add(slider);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - slider [ref=1, disabled, horizontal] actions: !increment, !decrement, get_value, !set_value",
                output);
    }

    @Test
    void enabledButtonInsideDisabledPanelStaysEnabled() throws Exception {
        // Swing's setEnabled(false) does not propagate to children
        // (Component.setEnabled javadoc; JDK-4177727 closed as won't-fix). The
        // button is mechanically clickable in Swing, so the snapshot must
        // report it as enabled even though its panel is disabled.
        JPanel parent = new JPanel();
        parent.setEnabled(false);
        JButton button = new JButton("Click Me");
        parent.add(button);

        String output = snapshot(parent);

        assertEquals(
                "- panel [disabled]\n"
                + "  - push_button \"Click Me\" [ref=1] actions: click",
                output);
    }

    @Test
    void disabledComponentWithOnlyPrefixedActionsStillReceivesRef() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        panel.add(button);

        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Disabled\" [ref=1, disabled] actions: !click",
                output);
    }

    @Test
    void multipleRootsAreSeparatedByDividerAndRefsAreGlobal() throws Exception {
        JPanel root1 = new JPanel();
        root1.add(new JButton("A"));

        JPanel root2 = new JPanel();
        root2.add(new JButton("B"));

        String output = snapshot(root1, root2);

        // Roots separated by "---"; refs are globally numbered across roots
        assertEquals(
                "- panel\n"
                + "  - push_button \"A\" [ref=1] actions: click\n"
                + "---\n"
                + "- panel\n"
                + "  - push_button \"B\" [ref=2] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButtonAppearsAsPushButton() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        assertEquals(
                "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jTextFieldAppearsAsEditableText() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JTextField());

        assertEquals(
                "- panel\n"
                + "  - text [ref=1] actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void jPasswordFieldAppearsAsPasswordText() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JPasswordField());

        assertEquals(
                "- panel\n"
                + "  - password_text [ref=1] actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void jTextAreaAppearsAsMultiLineText() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JTextArea());

        assertEquals(
                "- panel\n"
                + "  - text [ref=1, multi_line] actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void nonEditableTextFieldShowsReadOnlyWithoutSetText() throws Exception {
        JPanel panel = new JPanel();
        JTextField tf = new JTextField();
        tf.setEditable(false);
        panel.add(tf);

        assertEquals(
                "- panel\n"
                + "  - text [ref=1, read_only] actions: get_text, !set_text",
                snapshot(panel));
    }

    @Test
    void nonEditableTextAreaShowsReadOnlyWithoutSetText() throws Exception {
        JPanel panel = new JPanel();
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        panel.add(ta);

        assertEquals(
                "- panel\n"
                + "  - text [ref=1, read_only, multi_line] actions: get_text, !set_text",
                snapshot(panel));
    }

    @Test
    void jCheckBoxAppearsAsCheckBox() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JCheckBox("Accept"));

        assertEquals(
                "- panel\n"
                + "  - check_box \"Accept\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jRadioButtonAppearsAsRadioButton() throws Exception {
        JPanel panel = new JPanel();
        ButtonGroup group = new ButtonGroup();
        JRadioButton optionA = new JRadioButton("Option A");
        JRadioButton optionB = new JRadioButton("Option B");
        group.add(optionA);
        group.add(optionB);
        panel.add(optionA);
        panel.add(optionB);

        assertEquals(
                "- panel\n"
                + "  - radio_button \"Option A\" [ref=1] actions: click\n"
                + "  - radio_button \"Option B\" [ref=2] actions: click",
                snapshot(panel));
    }

    @Test
    void jComboBoxAppearsAsComboBox() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JComboBox<>(new String[]{"One", "Two", "Three"}));

        assertEquals(
                "- panel\n"
                + "  - combo_box [ref=1, collapsed] actions: toggle_popup, single-selection",
                snapshot(panel));
    }

    @Test
    void jToggleButtonAppearsAsToggleButton() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JToggleButton("Bold"));

        assertEquals(
                "- panel\n"
                + "  - toggle_button \"Bold\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jSpinnerAppearsAsSpinBoxWithIncrementDecrement() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JSpinner(new SpinnerNumberModel(1, 0, 10, 1)));

        assertEquals(
                "- panel\n"
                + "  - spin_box [ref=1] actions: increment, decrement, get_text, get_value, set_value\n"
                + "    - text [ref=2] actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void jSliderAppearsAsSliderWithIncrementDecrement() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JSlider(0, 100, 50));

        assertEquals(
                "- panel\n"
                + "  - slider [ref=1, horizontal] actions: increment, decrement, get_value, set_value",
                snapshot(panel));
    }

    @Test
    void jSplitPaneAppearsAsSplitPane() throws Exception {
        JPanel panel = new JPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JLabel("Left"), new JLabel("Right"));
        panel.add(splitPane);

        assertEquals(
                "- panel\n"
                + "  - split_pane [ref=1, horizontal] actions: get_value, set_value\n"
                + "    - label \"Left\"\n"
                + "    - label \"Right\"",
                snapshot(panel));
    }

    @Test
    void jLabelAppearsAsLabel() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Status"));

        assertEquals(
                "- panel\n"
                + "  - label \"Status\"",
                snapshot(panel));
    }

    @Test
    void jProgressBarAppearsAsProgressBar() throws Exception {
        JPanel panel = new JPanel();
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(42);
        panel.add(bar);

        assertEquals(
                "- panel\n"
                + "  - progress_bar [ref=1, horizontal] actions: get_value",
                snapshot(panel));
    }

    @Test
    void jToolBarAppearsAsToolBar() throws Exception {
        JPanel panel = new JPanel();
        JToolBar toolBar = new JToolBar();
        toolBar.add(new JButton("Save"));
        panel.add(toolBar);

        assertEquals(
                "- panel\n"
                + "  - tool_bar\n"
                + "    - push_button \"Save\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jPanelAppearsWhenNamed() throws Exception {
        JPanel root = new JPanel();
        root.getAccessibleContext().setAccessibleName("Form");

        assertEquals(
                "- panel \"Form\"",
                snapshot(root));
    }

    @Test
    void jScrollPaneAppearsAsScrollPane() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JScrollPane(new JLabel("Content"),
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));

        String output = snapshot(panel);

        // scroll_pane is kept even without a name; label is its direct child (viewport pruned)
        assertEquals(
                "- panel\n"
                + "  - scroll_pane\n"
                + "    - label \"Content\"\n"
                + "    - scroll_bar [ref=1, vertical] actions: get_value, set_value\n"
                + "      - push_button [ref=2] actions: click\n"
                + "      - push_button [ref=3] actions: click\n"
                + "    - scroll_bar [ref=4, horizontal] actions: get_value, set_value\n"
                + "      - push_button [ref=5] actions: click\n"
                + "      - push_button [ref=6] actions: click",
                output);
    }

    @Test
    void jTabbedPaneAppearsAsPageTabList() throws Exception {
        JPanel panel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("General", new JPanel());
        tabbedPane.addTab("Advanced", new JPanel());
        panel.add(tabbedPane);

        assertEquals(
                "- panel\n"
                + "  - page_tab_list \"General\" [ref=1] actions: single-selection\n"
                + "    - page_tab 0 \"General\" [selected]\n"
                + "    - page_tab 1 \"Advanced\"",
                snapshot(panel));
    }

    @Test
    void jMenuBarAppearsAsMenuBar() throws Exception {
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        panel.add(menuBar);

        assertEquals(
                "- panel\n"
                + "  - menu_bar\n"
                + "    - menu \"File\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jMenuAppearsAsMenu() throws Exception {
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu editMenu = new JMenu("Edit");
        editMenu.add(new JMenuItem("Cut"));
        menuBar.add(editMenu);
        panel.add(menuBar);

        assertEquals(
                "- panel\n"
                + "  - menu_bar\n"
                + "    - menu \"Edit\" [ref=1] actions: click\n"
                + "      - menu_item \"Cut\" [ref=2] actions: click",
                snapshot(panel));
    }

    @Test
    void jMenuItemAppearsAsMenuItem() throws Exception {
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Actions");
        JMenuItem deleteItem = new JMenuItem("Delete");
        menu.add(deleteItem);
        menuBar.add(menu);
        panel.add(menuBar);

        assertEquals(
                "- panel\n"
                + "  - menu_bar\n"
                + "    - menu \"Actions\" [ref=1] actions: click\n"
                + "      - menu_item \"Delete\" [ref=2] actions: click",
                snapshot(panel));
    }

    @Test
    void jTreeAppearsAsTree() throws Exception {
        JPanel panel = new JPanel();
        javax.swing.tree.DefaultMutableTreeNode root = new javax.swing.tree.DefaultMutableTreeNode("Root");
        root.add(new javax.swing.tree.DefaultMutableTreeNode("A"));
        root.add(new javax.swing.tree.DefaultMutableTreeNode("B"));
        JTree tree = new JTree(root);
        panel.add(tree);

        String output = snapshot(panel);
        // JTree nodes use AccessibleRole.LABEL (OpenJDK implementation).
        // The tree itself has no selection (TREE suppressed in UC-014) and is
        // not truncated, so it has no actions and no ref.
        // The root "Root" is expanded and has toggle_expand + selection actions.
        // Leaf nodes "A" and "B" are kept because they have accessible names,
        // but they have no actions so carry no ref.
        assertEquals(
                "- panel\n"
                + "  - tree\n"
                + "    - label \"Root\" [ref=1, expanded] actions: toggle_expand, single-selection\n"
                + "      - label \"A\" [collapsed]\n"
                + "      - label \"B\" [collapsed]",
                output);
    }

    @Test
    void jListAppearsAsList() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JList<>(new String[]{"Alpha", "Beta"}));

        assertEquals(
                "- panel\n"
                + "  - list [ref=1] actions: multi-selection\n"
                + "    - label \"Alpha\" [ref=2] actions: click\n"
                + "    - label \"Beta\" [ref=3] actions: click",
                snapshot(panel));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Real-world scenario test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void loginPanelSnapshot() throws Exception {
        // Simulates a typical login form: titled panel containing labelled inputs,
        // a "remember me" checkbox, and action buttons.
        JPanel root = new JPanel();

        JPanel loginPanel = new JPanel();
        loginPanel.setBorder(new TitledBorder("Login"));

        JLabel usernameLabel = new JLabel("Username");
        JTextField usernameField = new JTextField(20);
        usernameField.getAccessibleContext().setAccessibleName("Username");

        JLabel passwordLabel = new JLabel("Password");
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.getAccessibleContext().setAccessibleName("Password");

        JCheckBox rememberMe = new JCheckBox("Remember me");

        JButton signInButton = new JButton("Sign In");
        JButton cancelButton = new JButton("Cancel");

        loginPanel.add(usernameLabel);
        loginPanel.add(usernameField);
        loginPanel.add(passwordLabel);
        loginPanel.add(passwordField);
        loginPanel.add(rememberMe);
        loginPanel.add(signInButton);
        loginPanel.add(cancelButton);
        root.add(loginPanel);

        assertEquals(
                "- panel\n"
                + "  - panel \"Login\"\n"
                + "    - label \"Username\"\n"
                + "    - text \"Username\" [ref=1] actions: get_text, set_text\n"
                + "    - label \"Password\"\n"
                + "    - password_text \"Password\" [ref=2] actions: get_text, set_text\n"
                + "    - check_box \"Remember me\" [ref=3] actions: click\n"
                + "    - push_button \"Sign In\" [ref=4] actions: click\n"
                + "    - push_button \"Cancel\" [ref=5] actions: click",
                snapshot(root));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP integration test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSnapshotViaMcpClientReturnsValidTextResponse() {
        JPanel panel = new JPanel();
        panel.add(new JButton("MCP"));
        mcpServer.setConsideredComponents(List.of(panel));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        assertNotNull(result, "result must not be null");
        assertNotEquals(Boolean.TRUE, result.isError(), "result must not be an error");
        assertFalse(result.content().isEmpty(), "content must not be empty");

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertEquals(
                "- panel\n"
                + "  - push_button \"MCP\" [ref=1] actions: click",
                textContent.text());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // filter_substring tree-filtering tests (BR-09)
    // ══════════════════════════════════════════════════════════════════════════

    private static final String FILTER_HEADER_PREFIX =
            "[filter active: only nodes matching \"";
    private static final String FILTER_HEADER_SUFFIX =
            "\" and their ancestors/descendants are shown]";

    private static String filterHeader(String filter) {
        return FILTER_HEADER_PREFIX + filter + FILTER_HEADER_SUFFIX;
    }

    @Test
    void filterTreeIncludesMatchedNodeAndAncestors() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));
        panel.add(new JButton("Cancel"));
        panel.add(new JTextField());

        String output = snapshot("Save", panel);

        assertEquals(
                filterHeader("Save") + "\n"
                + "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click",
                output);
    }

    @Test
    void filterTreeIsCaseInsensitive() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        String output = snapshot("save", panel);

        assertEquals(
                filterHeader("save") + "\n"
                + "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click",
                output);
    }

    @Test
    void filterTreeNoMatchReturnsMessage() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        String output = snapshot("nonexistent", panel);

        assertEquals("No lines matched filter_substring 'nonexistent'", output);
    }

    @Test
    void filterTreeDoesNotAffectRefNumbering() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("First"));
        panel.add(new JButton("Second"));
        panel.add(new JButton("Third"));

        // "Third" should still be ref=3 even when filtered
        String output = snapshot("Third", panel);

        assertEquals(
                filterHeader("Third") + "\n"
                + "- panel\n"
                + "  - push_button \"Third\" [ref=3] actions: click",
                output);
    }

    @Test
    void filterTreeDropsRootSeparators() throws Exception {
        JPanel root1 = new JPanel();
        root1.add(new JButton("A"));

        JPanel root2 = new JPanel();
        root2.add(new JButton("B"));

        // Both roots contain push_button — both should appear, no "---"
        String output = snapshot("push_button", root1, root2);

        assertEquals(
                filterHeader("push_button") + "\n"
                + "- panel\n"
                + "  - push_button \"A\" [ref=1] actions: click\n"
                + "- panel\n"
                + "  - push_button \"B\" [ref=2] actions: click",
                output);
    }

    @Test
    void filterTreeOmittedReturnsFullSnapshot() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        // Use the no-filter overload
        String output = snapshot(panel);

        assertEquals(
                "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click",
                output);
    }

    @Test
    void filterTreeAncestorsIncludedButNonMatchingSiblingsDropped() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Keep"));
        panel.add(new JButton("Drop"));
        panel.add(new JTextField());

        String output = snapshot("Keep", panel);

        // Panel (ancestor) is included; "Drop" button and text field are not
        assertEquals(
                filterHeader("Keep") + "\n"
                + "- panel\n"
                + "  - push_button \"Keep\" [ref=1] actions: click",
                output);
    }

    @Test
    void filterTreeDescendantsIncludedUnconditionally() throws Exception {
        // A named panel with children — filter on the panel name,
        // all children should appear even though they don't match
        JPanel outer = new JPanel();
        JPanel inner = new JPanel();
        inner.getAccessibleContext().setAccessibleName("Toolbar");
        JButton b1 = new JButton("Open");
        JButton b2 = new JButton("Close");
        inner.add(b1);
        inner.add(b2);
        outer.add(inner);

        String output = snapshot("Toolbar", outer);

        // "Toolbar" matches — its children (Open, Close) must appear
        // even though they don't contain "Toolbar"
        assertEquals(
                filterHeader("Toolbar") + "\n"
                + "- panel\n"
                + "  - panel \"Toolbar\"\n"
                + "    - push_button \"Open\" [ref=1] actions: click\n"
                + "    - push_button \"Close\" [ref=2] actions: click",
                output);
    }

    @Test
    void filterTreeMatchesOnRole() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Go"));
        panel.add(new JTextField());

        // "text" matches the text field role
        String output = snapshot("text", panel);

        // "text" also matches push_button's "set_text" in actions... but let's
        // check that the text field is definitely included with its ancestor
        assertTrue(output.contains("- text [ref=2] actions: get_text, set_text"),
                "Text field should be in output");
        assertTrue(output.startsWith(filterHeader("text")),
                "Output should start with filter header");
    }

    @Test
    void filterTreeMatchesOnActions() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Go"));
        panel.add(new JTextField());

        String output = snapshot("set_text", panel);

        // "set_text" matches the text field's actions line
        assertTrue(output.contains("- text [ref=2] actions: get_text, set_text"),
                "Text field should be in output");
        assertTrue(output.startsWith(filterHeader("set_text")),
                "Output should start with filter header");
    }

    // ── Additional tree-filter integration tests ───────────────────────────────

    @Test
    void filterTreeIncludesTableDescendantsAndTruncation() throws Exception {
        // Build a table with more rows than MAX_DATA_ROW_NODES so truncation kicks in
        Object[][] data = new Object[SnapshotNode.MAX_DATA_ROW_NODES + 3][2];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Object[]{"Name" + i, "Email" + i};
        }
        JTable table = new JTable(data, new Object[]{"Name", "Email"});
        table.getAccessibleContext().setAccessibleName("Customers");
        JScrollPane scroll = new JScrollPane(table);

        JPanel panel = new JPanel();
        panel.add(scroll);
        panel.add(new JButton("Save"));

        String output = snapshot("Customers", panel);

        // Table + all visible rows + truncation summary should be included;
        // "Save" button should be excluded
        assertTrue(output.startsWith(filterHeader("Customers")),
                "Should start with filter header");
        assertTrue(output.contains("table \"Customers\""),
                "Table should be in output");
        assertTrue(output.contains("- row 0:"),
                "First row should be in output");
        assertTrue(output.contains("... and " + 3 + " more rows"),
                "Truncation summary should be in output");
        assertFalse(output.contains("Save"),
                "Non-matching sibling 'Save' should be excluded");
    }

    @Test
    void filterTreeMultipleRootsDropsSeparators() throws Exception {
        JPanel root1 = new JPanel();
        root1.add(new JButton("Alpha"));
        root1.add(new JButton("Shared"));

        JPanel root2 = new JPanel();
        root2.add(new JButton("Gamma"));
        root2.add(new JButton("Shared"));

        String output = snapshot("Shared", root1, root2);

        // Both "Shared" buttons should appear, no "---" separator
        assertFalse(output.contains("---"), "No root separator in filtered output");
        assertTrue(output.contains("\"Shared\" [ref=2]"), "First Shared button");
        assertTrue(output.contains("\"Shared\" [ref=4]"), "Second Shared button");
        assertFalse(output.contains("Alpha"), "Non-matching sibling excluded");
        assertFalse(output.contains("Gamma"), "Non-matching sibling excluded");
    }

    @Test
    void filterTreeDeepNesting() throws Exception {
        // Match a deeply nested node — all ancestors should appear,
        // non-matching sibling branches should be dropped
        JPanel outer = new JPanel();
        JPanel left = new JPanel();
        left.getAccessibleContext().setAccessibleName("Left");
        left.add(new JButton("Alpha"));
        JPanel right = new JPanel();
        right.getAccessibleContext().setAccessibleName("Right");
        right.add(new JButton("Target"));
        right.add(new JButton("Other"));
        outer.add(left);
        outer.add(right);

        String output = snapshot("Target", outer);

        assertEquals(
                filterHeader("Target") + "\n"
                + "- panel\n"
                + "  - panel \"Right\"\n"
                + "    - push_button \"Target\" [ref=2] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * A CellRendererPane that also implements Accessible so it can appear
     * in the accessibility tree (allowing HE-2 to be tested).
     */
    // ══════════════════════════════════════════════════════════════════════════
    // Tier 2 — MouseListener fallback in snapshot
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void unnamedPanelWithAppMouseListener_appearsInSnapshotWithClickAction() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();

        assertEquals(
                "- panel [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void unnamedPanelWithOnlyFrameworkMouseListener_isPruned() throws Exception {
        // Register ToolTipManager directly as a MouseListener (avoiding setToolTipText
        // which also sets accessibleDescription, making the panel "named").
        JPanel panel = new JPanel();
        panel.addMouseListener(javax.swing.ToolTipManager.sharedInstance());

        assertEquals(
                "- panel",
                snapshot(panel));
    }

    @Test
    void buttonWithAdditionalAppMouseListener_showsClickOnce() throws Exception {
        JButton button = new JButton("OK");
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {}
        });

        // Tier 1 (AccessibleAction) wins — click appears exactly once
        assertEquals(
                "- push_button \"OK\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void disabledPanelWithAppMouseListener_showsBangClick() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        panel.setEnabled(false);

        assertEquals(
                "- panel [ref=1, disabled] actions: !click",
                snapshot(panel));
    }

    @Test
    void interactiveRoleWithAppMouseListener_noTier2Click() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        slider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {}
        });

        // Slider has interactive role — Tier 2 skipped. No click action.
        assertEquals(
                "- slider [ref=1, horizontal] actions: increment, decrement, get_value, set_value",
                snapshot(slider));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private static class AccessibleCellRendererPane extends CellRendererPane implements Accessible {
        @Override
        public AccessibleContext getAccessibleContext() {
            return new AccessibleContext() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PANEL;
                }
                @Override
                public AccessibleStateSet getAccessibleStateSet() {
                    return new AccessibleStateSet();
                }
                @Override
                public int getAccessibleIndexInParent() {
                    return -1;
                }
                @Override
                public int getAccessibleChildrenCount() {
                    return 0;
                }
                @Override
                public Accessible getAccessibleChild(int i) {
                    return null;
                }
                @Override
                public java.util.Locale getLocale() {
                    return java.util.Locale.getDefault();
                }
            };
        }
    }
}
