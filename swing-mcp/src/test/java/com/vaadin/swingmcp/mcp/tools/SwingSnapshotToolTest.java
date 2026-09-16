package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.mcp.ClickRecordingPanel;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
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

import static com.vaadin.swingmcp.mcp.JdkCapabilities.SLIDER_HAS_ACCESSIBLE_ACTIONS;
import static org.junit.jupiter.api.Assertions.*;

class SwingSnapshotToolTest extends AbstractHeadlessTest {

    // ── Instance-level setup for direct tool invocation ───────────────────────

    private SwingSnapshotTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        tool = new SwingSnapshotTool();
        context = new SwingToolContext(Runnable::run);
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save\" [ref=1] actions: click\n"
                + "  - JTextField (text) [ref=2] text=\"\" actions: get_text, set_text",
                output);
    }

    @Test
    void refsAreAssignedStartingFromOne() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("First"));
        panel.add(new JButton("Second"));

        String output = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"First\" [ref=1] actions: click\n"
                + "  - JButton (push_button) \"Second\" [ref=2] actions: click",
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
                "- JPanel (panel) \"Outer\"\n"
                + "  - JPanel (panel) \"Inner\"\n"
                + "    - JButton (push_button) \"Go\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Visible\" [ref=1] actions: click",
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
                "- JPanel (panel) \"Root\"\n"
                + "  - JButton (push_button) \"Click\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JPanel (panel) \"Details\"\n"
                + "    - JButton (push_button) \"OK\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JPanel (panel) \"FormSection\"\n"
                + "    - JButton (push_button) \"Submit\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Real\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Action\" [ref=1] actions: click",
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
                "- JPanel (panel) \"Root\"\n"
                + "  - JButton (push_button) \"OK\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JScrollPane (scroll_pane)\n"
                + "    - JList (list) [ref=1] actions: multi-selection\n"
                + "      - (label) \"A\" [ref=2] actions: click\n"
                + "      - (label) \"B\" [ref=3] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=4, vertical] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=5] actions: click\n"
                + "      - JButton (push_button) [ref=6] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=7, horizontal] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=8] actions: click\n"
                + "      - JButton (push_button) [ref=9] actions: click",
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

        // T-002 step 6b / T-020 BR-03: JTable does NOT advertise
        // get_cell_count / get_cells — use swing_get_items instead.
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTable (table) [ref=1] actions: multi-selection\n"
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
        // T-002 step 6b: truncated JList advertises get_cell_count + get_cells.
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
        // T-002 step 6b: truncated JTree advertises get_cell_count + get_cells.
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
                "- JPanel (panel)\n"
                + "  - JTable (table) [ref=1] actions: multi-selection\n"
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
                "- JPanel (panel)\n"
                + "  - JScrollPane (scroll_pane)\n"
                + "    - JTable (table) [ref=1] columns: [ID, Name, City] actions: multi-selection\n"
                + "      - row 0: 1 | Alice | NY\n"
                + "      - row 1: 2 | Bob | LA\n"
                + "    - JScrollBar (scroll_bar) [ref=2, vertical] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=3] actions: click\n"
                + "      - JButton (push_button) [ref=4] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=5, horizontal] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=6] actions: click\n"
                + "      - JButton (push_button) [ref=7] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JTable (table) [ref=1] actions: multi-selection\n"
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
                "- JPanel (panel)\n"
                + "  - JScrollPane (scroll_pane)\n"
                + "    - JTable (table) [ref=1] columns: [] actions: multi-selection\n"
                + "    - JScrollBar (scroll_bar) [ref=2, vertical] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=3] actions: click\n"
                + "      - JButton (push_button) [ref=4] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=5, horizontal] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=6] actions: click\n"
                + "      - JButton (push_button) [ref=7] actions: click",
                output);
    }

    @Test
    void jTableWithRowsButZeroColumns() throws Exception {
        JTable table = new JTable(new DefaultTableModel(3, 0));
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTable (table) [ref=1] actions: multi-selection\n"
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
                "- JPanel (panel)\n"
                + "  - JScrollPane (scroll_pane)\n"
                + "    - JTable (table) [ref=1] columns: [Name] actions: multi-selection\n"
                + "      - row 0: Alice\n"
                + "    - JScrollBar (scroll_bar) [ref=2, vertical] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=3] actions: click\n"
                + "      - JButton (push_button) [ref=4] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=5, horizontal] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=6] actions: click\n"
                + "      - JButton (push_button) [ref=7] actions: click",
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

        // D_jmenu_not_clickable: JMenu has no click action and no ref; items are directly reachable.
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"File\"\n"
                + "      - JMenuItem (menu_item) \"Open\" [ref=1] actions: click\n"
                + "      - JMenuItem (menu_item) \"Save\" [ref=2] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"Tab1\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"Tab1\" [selected]\n"
                + "      - JButton (push_button) \"InTab1\" [ref=2] actions: click\n"
                + "    - (page_tab) 1 \"Tab2\"",
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
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"Tab1\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"Tab1\" [selected]\n"
                + "    - (page_tab) 1 \"Tab2\" [disabled]",
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
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"OnlyTab\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"OnlyTab\" [disabled, selected]\n"
                + "      - JButton (push_button) \"OnDisabled\" [ref=2] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"Gamma\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"Alpha\"\n"
                + "    - (page_tab) 1 \"Beta\" [disabled]\n"
                + "    - (page_tab) 2 \"Gamma\" [selected]\n"
                + "    - (page_tab) 3 \"Delta\" [disabled]",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Normal\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Disabled\" [ref=1, disabled] actions: !click",
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
                "- JPanel (panel)\n"
                + "  - JSlider (slider) [ref=1, disabled, horizontal] value=50 actions: "
                + (SLIDER_HAS_ACCESSIBLE_ACTIONS ? "!increment, !decrement, " : "")
                + "get_value, !set_value",
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
                "- JPanel (panel) [disabled]\n"
                + "  - JButton (push_button) \"Click Me\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Disabled\" [ref=1, disabled] actions: !click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"A\" [ref=1] actions: click\n"
                + "---\n"
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"B\" [ref=2] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jTextFieldAppearsAsEditableText() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JTextField());

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void jPasswordFieldAppearsAsPasswordText() throws Exception {
        // D_password_not_readable: password fields advertise set_text but never get_text.
        JPanel panel = new JPanel();
        panel.add(new JPasswordField());

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JPasswordField (password_text) [ref=1] actions: set_text",
                snapshot(panel));
    }

    @Test
    void nonEditableJPasswordFieldShowsOnlyPrefixedSetText() throws Exception {
        // D_password_not_readable pathological case: a non-editable JPasswordField keeps its ref
        // and shows only "!set_text" — get_text is suppressed, set_text is prefixed
        // with "!" per BR-08 because the EDITABLE state is absent.
        JPanel panel = new JPanel();
        JPasswordField pw = new JPasswordField();
        pw.setEditable(false);
        panel.add(pw);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JPasswordField (password_text) [ref=1, read_only] actions: !set_text",
                snapshot(panel));
    }

    @Test
    void customComponentWithPasswordRoleSuppressesGetText() throws Exception {
        // D_password_not_readable role-based gate: a custom JTextField subclass that claims
        // AccessibleRole.PASSWORD_TEXT (without extending JPasswordField) also
        // has get_text suppressed.
        JPanel panel = new JPanel();
        JTextField pw = new JTextField() {
            @Override
            public javax.accessibility.AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.PASSWORD_TEXT;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        panel.add(pw);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (password_text) [ref=1] actions: set_text",
                snapshot(panel));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BR-12 / D_inline_value_preview — inline text="..." / value=N previews
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void br12_jTextFieldWithContent_rendersInlineTextPreview() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JTextField("admin"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"admin\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void br12_jTextFieldLongerThanCap_truncatesTo14CharsPlusEllipsis() throws Exception {
        // 30 chars — longer than PREVIEW_MAX_LENGTH (15). D_dispatched_echo convention:
        // first 14 chars + U+2026.
        JPanel panel = new JPanel();
        panel.add(new JTextField("Lorem ipsum dolor sit amet ipl"));

        // "Lorem ipsum do" (14 chars) + "…"
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"Lorem ipsum do…\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void br12_jTextFieldAt15Chars_rendersFullValue() throws Exception {
        // Boundary case: exactly 15 chars is emitted in full (no ellipsis).
        JPanel panel = new JPanel();
        panel.add(new JTextField("123456789012345"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"123456789012345\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void br12_emptyJTextField_rendersEmptyStringPreview() throws Exception {
        // Empty JTextField yields text="" — distinct from "no preview" (which
        // would be the case if supportsGetText were false). Matches T-005
        // BR-08's "field exists and is empty" semantics.
        JPanel panel = new JPanel();
        panel.add(new JTextField());

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void br12_jTextAreaWithNewlines_collapsesWhitespaceBeforeTruncation() throws Exception {
        // Newlines and whitespace runs are collapsed to single spaces
        // (matches BR-10's HTML cleanup convention) before the 15-char cap.
        // Source: "line one\nline two" (17 chars) → collapsed: "line one line two"
        // (17 chars after collapse — still 17 because the newline replaced by
        // a single space). Cap: first 14 chars + "…".
        JPanel panel = new JPanel();
        panel.add(new JTextArea("line one\nline two"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextArea (text) [ref=1, multi_line] text=\"line one line…\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void br12_editableJPasswordField_doesNotEmitTextPreview() throws Exception {
        // D_password_not_readable / BR-12 gate parity: supportsGetText() returns false for
        // PASSWORD_TEXT, so no text="..." annotation is emitted even though
        // the field has content. set_text is unaffected.
        JPanel panel = new JPanel();
        JPasswordField pw = new JPasswordField();
        pw.setText("hunter2");
        panel.add(pw);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JPasswordField (password_text) [ref=1] actions: set_text",
                snapshot(panel));
    }

    @Test
    void br12_jSliderWithValue_rendersInlineValuePreview() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JSlider(0, 100, 42));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JSlider (slider) [ref=1, horizontal] value=42 actions: "
                + (SLIDER_HAS_ACCESSIBLE_ACTIONS ? "increment, decrement, " : "")
                + "get_value, set_value",
                snapshot(panel));
    }

    @Test
    void br12_jSpinnerWithFractionalValue_rendersDecimalValue() throws Exception {
        // Fractional numbers render with a decimal point per D_dispatched_echo's number
        // convention. Double(3.5) → "value=3.5" (not "value=3").
        JPanel panel = new JPanel();
        panel.add(new JSpinner(new SpinnerNumberModel(3.5, 0.0, 10.0, 0.5)));

        String out = snapshot(panel);
        assertTrue(out.contains("value=3.5"),
                "Expected fractional value=3.5 in snapshot, got:\n" + out);
    }

    @Test
    void br12_jProgressBarWithMax_rendersCurrentOverMax() throws Exception {
        // PROGRESS_BAR exception: when getMaximumAccessibleValue() is non-null,
        // render as value=current/max.
        JPanel panel = new JPanel();
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(37);
        panel.add(bar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JProgressBar (progress_bar) [ref=1, horizontal] value=37/100 actions: get_value",
                snapshot(panel));
    }

    @Test
    void br12_jProgressBarWithNullMax_rendersBareValue() throws Exception {
        // Fallback for a progress bar that returns null from
        // getMaximumAccessibleValue(): bare value=N. In standard Swing
        // AccessibleJProgressBar always returns the model max, so we have to
        // mock the AccessibleValue to exercise the null-max path.
        JPanel panel = new JPanel();
        JProgressBar bar = new JProgressBar(0, 100) {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJProgressBar() {
                        @Override
                        public AccessibleValue getAccessibleValue() {
                            return new AccessibleValue() {
                                @Override
                                public Number getCurrentAccessibleValue() { return 37; }
                                @Override
                                public boolean setCurrentAccessibleValue(Number n) { return false; }
                                @Override
                                public Number getMinimumAccessibleValue() { return 0; }
                                @Override
                                public Number getMaximumAccessibleValue() { return null; }
                            };
                        }
                    };
                }
                return accessibleContext;
            }
        };
        bar.setValue(37);
        panel.add(bar);

        String out = snapshot(panel);
        assertTrue(out.contains("value=37 actions"),
                "Expected bare value=37 when max is null, got:\n" + out);
        assertFalse(out.contains("value=37/"),
                "Should not render denominator when max is null, got:\n" + out);
    }

    @Test
    void br12_jCheckBox_emitsNoInlinePreview() throws Exception {
        // Neither gate fires for CHECK_BOX (AccessibleValue is suppressed per
        // SUPPRESSED_VALUE_ROLES; no AccessibleText). State [checked]
        // already carries the signal.
        JPanel panel = new JPanel();
        JCheckBox cb = new JCheckBox("Accept");
        cb.setSelected(true);
        panel.add(cb);

        String out = snapshot(panel);
        assertFalse(out.contains(" text="),
                "JCheckBox should not emit inline text=, got:\n" + out);
        assertFalse(out.contains(" value="),
                "JCheckBox should not emit inline value=, got:\n" + out);
    }

    @Test
    void br12_jButton_emitsNoInlinePreview() throws Exception {
        // Neither gate fires for PUSH_BUTTON.
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        String out = snapshot(panel);
        assertFalse(out.contains(" text="),
                "JButton should not emit inline text=, got:\n" + out);
        assertFalse(out.contains(" value="),
                "JButton should not emit inline value=, got:\n" + out);
    }

    @Test
    void br12_defensiveRead_throwingAccessibleText_omitsAnnotationKeepsNode() throws Exception {
        // If the accessible's AccessibleText impl throws at snapshot time, the
        // single text="..." annotation is omitted — but the component still
        // appears in the tree. Exercised via a custom JTextField subclass
        // whose AccessibleText throws on getCharCount().
        JPanel panel = new JPanel();
        JTextField tf = new JTextField("admin") {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public AccessibleText getAccessibleText() {
                            AccessibleText real = super.getAccessibleText();
                            if (real == null) return null;
                            return new AccessibleText() {
                                @Override
                                public int getIndexAtPoint(java.awt.Point p) { return real.getIndexAtPoint(p); }
                                @Override
                                public java.awt.Rectangle getCharacterBounds(int i) { return real.getCharacterBounds(i); }
                                @Override
                                public int getCharCount() { throw new RuntimeException("boom"); }
                                @Override
                                public int getCaretPosition() { return real.getCaretPosition(); }
                                @Override
                                public String getAtIndex(int part, int idx) { return real.getAtIndex(part, idx); }
                                @Override
                                public String getAfterIndex(int part, int idx) { return real.getAfterIndex(part, idx); }
                                @Override
                                public String getBeforeIndex(int part, int idx) { return real.getBeforeIndex(part, idx); }
                                @Override
                                public javax.swing.text.AttributeSet getCharacterAttribute(int i) { return real.getCharacterAttribute(i); }
                                @Override
                                public int getSelectionStart() { return real.getSelectionStart(); }
                                @Override
                                public int getSelectionEnd() { return real.getSelectionEnd(); }
                                @Override
                                public String getSelectedText() { return real.getSelectedText(); }
                            };
                        }
                    };
                }
                return accessibleContext;
            }
        };
        panel.add(tf);

        // The custom AccessibleJTextField still exposes AccessibleEditableText,
        // so supportsGetText is true and get_text/set_text are advertised.
        // The throw happens inside readText → computeInlinePreview catches it
        // and omits the annotation. Component line still renders.
        String out = snapshot(panel);
        assertTrue(out.contains("- JTextField (text) [ref=1]"),
                "Throwing component should still appear in snapshot, got:\n" + out);
        assertFalse(out.contains("text=\""),
                "Throwing AccessibleText should cause text= annotation to be omitted, got:\n" + out);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // End of BR-12 / D_inline_value_preview tests
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // BR-13 / D_quoted_slot_sanitizing — quoted-slot sanitization
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void br13_buttonNameWithNewline_collapsesToSingleSpace() throws Exception {
        // Without sanitization, JButton("Save\nChanges") rendered as three
        // visible lines — the label bleeded into the snapshot tree structure.
        // BR-13 collapses any whitespace run (including \n) to a single space
        // so the one-line-per-node invariant is preserved.
        JPanel panel = new JPanel();
        panel.add(new JButton("Save\nChanges"));

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save Changes\" [ref=1] actions: click",
                out);
    }

    @Test
    void br13_buttonNameWithEmbeddedQuote_escapesAsBackslashQuote() throws Exception {
        // Embedded " was previously emitted verbatim, producing
        // `"Click "here""` which terminates the quoted slot visually. BR-13
        // escapes as \" so the slot remains unambiguous.
        JPanel panel = new JPanel();
        panel.add(new JButton("Click \"here\""));

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Click \\\"here\\\"\" [ref=1] actions: click",
                out);
    }

    @Test
    void br13_buttonNameWithTab_collapsesToSingleSpace() throws Exception {
        // Tabs and other Java \s-matching whitespace collapse alongside \n.
        JPanel panel = new JPanel();
        panel.add(new JButton("Col1\tCol2\tCol3"));

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Col1 Col2 Col3\" [ref=1] actions: click",
                out);
    }

    @Test
    void br13_descriptionWithNewline_collapsesToSingleSpace() throws Exception {
        // Non-HTML accessibleDescription bypasses htmlToPlainText's whitespace
        // collapse. BR-13 ensures the description slot is still sanitized.
        JPanel panel = new JPanel();
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription("line1\nline2\nline3");
        panel.add(button);

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"OK\" \"line1 line2 line3\" [ref=1] actions: click",
                out);
    }

    @Test
    void br13_descriptionWithEmbeddedQuote_escapesAsBackslashQuote() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription("say \"hi\" loudly");
        panel.add(button);

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"OK\" \"say \\\"hi\\\" loudly\" [ref=1] actions: click",
                out);
    }

    @Test
    void br13_textPreviewWithEmbeddedQuote_escapesAsBackslashQuote() throws Exception {
        // The BR-12 text="..." preview also passes through the sanitizer,
        // closing the quote-escape gap that was absent before D_quoted_slot_sanitizing.
        JPanel panel = new JPanel();
        JTextField field = new JTextField();
        field.setText("say \"hi\"");
        panel.add(field);

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"say \\\"hi\\\"\" actions: get_text, set_text",
                out);
    }

    @Test
    void br13_textPreviewWithNewline_collapsesToSingleSpace() throws Exception {
        // Newlines in JTextField content were already collapsed in BR-12's
        // preview whitespace handling. Regression guard that D_quoted_slot_sanitizing's
        // sanitizer keeps the same behaviour while adding quote escaping.
        JPanel panel = new JPanel();
        JTextField field = new JTextField();
        field.setText("line1\nline2");
        panel.add(field);

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1] text=\"line1 line2\" actions: get_text, set_text",
                out);
    }

    @Test
    void br13_longLabelName_rendersFullNameUncapped() throws Exception {
        // D_quoted_slot_sanitizing §2: name is identity, rendered in full regardless of length.
        // Concrete check: a 300-character JLabel name appears verbatim (minus
        // sanitization) in the name slot — no truncation, no … suffix.
        StringBuilder longName = new StringBuilder(300);
        for (int i = 0; i < 300; i++) {
            longName.append('x');
        }

        JPanel panel = new JPanel();
        panel.add(new JLabel(longName.toString()));

        String out = snapshot(panel);

        assertTrue(out.contains("\"" + longName + "\""),
                "300-char name must render uncapped. Got:\n" + out);
        assertFalse(out.contains("…"),
                "Name slot must not emit ellipsis truncation. Got:\n" + out);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D_label_not_readable — LABEL role excluded from get_text
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void dr015_plainJLabel_hasNoRefNoGetTextNoPreview() throws Exception {
        // Plain JLabel: no AccessibleText exposed, so behaviour pre-D_label_not_readable
        // was already "no ref, no get_text". Regression guard.
        JPanel panel = new JPanel();
        panel.add(new JLabel("Status: OK"));

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JLabel (label) \"Status: OK\"",
                out);
    }

    @Test
    void dr015_htmlJLabel_hasNoRefNoGetTextNoPreview() throws Exception {
        // D_label_not_readable behaviour change: a JLabel("<html>...</html>") previously
        // received ref=1, "actions: get_text", and a text="..." preview
        // because AccessibleHTMLTextSupport exposed AccessibleText. After
        // D_label_not_readable the LABEL-role exclusion suppresses all three.
        JPanel panel = new JPanel();
        panel.add(new JLabel("<html>Hello <b>world</b></html>"));

        String out = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JLabel (label) \"Hello world\"",
                out);
    }

    @Test
    void dr015_jListCell_retainsClickRef() throws Exception {
        // Regression guard for D_label_not_readable's role-based gate: JList cells have
        // role LABEL, so the LABEL exclusion applies to them too — but
        // their ref comes from the `click` action (not get_text), so they
        // still receive a ref. Loss of this ref would break every JList
        // interaction.
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("alpha");
        model.addElement("beta");
        JList<String> list = new JList<>(model);
        JPanel panel = new JPanel();
        panel.add(list);

        String out = snapshot(panel);

        assertTrue(out.contains("- (label) \"alpha\" [ref=2] actions: click"),
                "JList cell must retain click ref despite LABEL-role get_text exclusion. Got:\n" + out);
        assertFalse(out.contains("get_text"),
                "JList cell must not advertise get_text after D_label_not_readable. Got:\n" + out);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // End of BR-13 / D_quoted_slot_sanitizing / D_label_not_readable tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTextAreaAppearsAsMultiLineText() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JTextArea());

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextArea (text) [ref=1, multi_line] text=\"\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void nonEditableTextFieldShowsReadOnlyWithoutSetText() throws Exception {
        JPanel panel = new JPanel();
        JTextField tf = new JTextField();
        tf.setEditable(false);
        panel.add(tf);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextField (text) [ref=1, read_only] text=\"\" actions: get_text, !set_text",
                snapshot(panel));
    }

    @Test
    void nonEditableTextAreaShowsReadOnlyWithoutSetText() throws Exception {
        JPanel panel = new JPanel();
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        panel.add(ta);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTextArea (text) [ref=1, read_only, multi_line] text=\"\" actions: get_text, !set_text",
                snapshot(panel));
    }

    @Test
    void jCheckBoxAppearsAsCheckBox() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JCheckBox("Accept"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JCheckBox (check_box) \"Accept\" [ref=1] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JRadioButton (radio_button) \"Option A\" [ref=1] actions: click\n"
                + "  - JRadioButton (radio_button) \"Option B\" [ref=2] actions: click",
                snapshot(panel));
    }

    @Test
    void jComboBoxAppearsAsComboBox() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JComboBox<>(new String[]{"One", "Two", "Three"}));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JComboBox (combo_box) [ref=1, collapsed] actions: toggle_popup, single-selection",
                snapshot(panel));
    }

    @Test
    void jToggleButtonAppearsAsToggleButton() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JToggleButton("Bold"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JToggleButton (toggle_button) \"Bold\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jSpinnerAppearsAsSpinBoxWithIncrementDecrement() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JSpinner(new SpinnerNumberModel(1, 0, 10, 1)));

        // BR-12: JSpinner's AccessibleJSpinner delegates AccessibleText to the
        // inner JFormattedTextField so supportsGetText is true — both text=
        // and value= are emitted on the spinner line. The inner editor gets
        // its own text= preview.
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JSpinner (spin_box) [ref=1] text=\"1\" value=1 actions: increment, decrement, get_text, get_value, set_value\n"
                + "    - JFormattedTextField (text) [ref=2] text=\"1\" actions: get_text, set_text",
                snapshot(panel));
    }

    @Test
    void jSliderAppearsAsSliderWithIncrementDecrement() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JSlider(0, 100, 50));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JSlider (slider) [ref=1, horizontal] value=50 actions: "
                + (SLIDER_HAS_ACCESSIBLE_ACTIONS ? "increment, decrement, " : "")
                + "get_value, set_value",
                snapshot(panel));
    }

    @Test
    void jSplitPaneAppearsAsSplitPane() throws Exception {
        JPanel panel = new JPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JLabel("Left"), new JLabel("Right"));
        panel.add(splitPane);

        // BR-12: unrealized JSplitPane reports -1 from getCurrentAccessibleValue;
        // value preview mirrors that fidelity (T-012 Component Matrix note).
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JSplitPane (split_pane) [ref=1, horizontal] value=-1 actions: get_value, set_value\n"
                + "    - JLabel (label) \"Left\"\n"
                + "    - JLabel (label) \"Right\"",
                snapshot(panel));
    }

    @Test
    void jLabelAppearsAsLabel() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JLabel("Status"));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JLabel (label) \"Status\"",
                snapshot(panel));
    }

    @Test
    void jProgressBarAppearsAsProgressBar() throws Exception {
        JPanel panel = new JPanel();
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(42);
        panel.add(bar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JProgressBar (progress_bar) [ref=1, horizontal] value=42/100 actions: get_value",
                snapshot(panel));
    }

    @Test
    void jToolBarAppearsAsToolBar() throws Exception {
        JPanel panel = new JPanel();
        JToolBar toolBar = new JToolBar();
        toolBar.add(new JButton("Save"));
        panel.add(toolBar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JToolBar (tool_bar)\n"
                + "    - JButton (push_button) \"Save\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jPanelAppearsWhenNamed() throws Exception {
        JPanel root = new JPanel();
        root.getAccessibleContext().setAccessibleName("Form");

        assertEquals(
                "- JPanel (panel) \"Form\"",
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
                "- JPanel (panel)\n"
                + "  - JScrollPane (scroll_pane)\n"
                + "    - JLabel (label) \"Content\"\n"
                + "    - JScrollBar (scroll_bar) [ref=1, vertical] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=2] actions: click\n"
                + "      - JButton (push_button) [ref=3] actions: click\n"
                + "    - JScrollBar (scroll_bar) [ref=4, horizontal] value=0 actions: get_value, set_value\n"
                + "      - JButton (push_button) [ref=5] actions: click\n"
                + "      - JButton (push_button) [ref=6] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"General\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"General\" [selected]\n"
                + "    - (page_tab) 1 \"Advanced\"",
                snapshot(panel));
    }

    @Test
    void jMenuBarAppearsAsMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenu has no click action, no ref.
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        panel.add(menuBar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"File\"",
                snapshot(panel));
    }

    @Test
    void jMenuAppearsAsMenu() throws Exception {
        // D_jmenu_not_clickable: JMenu has no click action, no ref; only the menu item is clickable.
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu editMenu = new JMenu("Edit");
        editMenu.add(new JMenuItem("Cut"));
        menuBar.add(editMenu);
        panel.add(menuBar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"Edit\"\n"
                + "      - JMenuItem (menu_item) \"Cut\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void jMenuItemAppearsAsMenuItem() throws Exception {
        // D_jmenu_not_clickable: JMenu has no click action, no ref; only the menu item is clickable.
        JPanel panel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Actions");
        JMenuItem deleteItem = new JMenuItem("Delete");
        menu.add(deleteItem);
        menuBar.add(menu);
        panel.add(menuBar);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"Actions\"\n"
                + "      - JMenuItem (menu_item) \"Delete\" [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void contextJPopupMenuWithNonJMenuInvokerIsNotPruned() throws Exception {
        // Regression guard for D_jmenu_not_clickable / HE-5: the JPopupMenu prune is keyed on
        // getInvoker() instanceof JMenu. Right-click context menus invoked
        // from a JButton/JTable/etc. must continue to render normally.
        // Headless caveat: we override isVisible() and getInvoker() directly
        // because PopupFactory.getPopup() (the native path) cannot run here.
        JPanel panel = new JPanel();
        JButton button = new JButton("Right-click me");
        panel.add(button);

        @SuppressWarnings("serial")
        JPopupMenu popup = new JPopupMenu() {
            @Override public boolean isVisible() { return true; }
            @Override public Component getInvoker() { return button; }
        };
        popup.add(new JMenuItem("Cut"));
        panel.add(popup);

        String output = snapshot(panel);
        assertTrue(output.contains("JPopupMenu (popup_menu)"),
                "JPopupMenu with JButton invoker must NOT be pruned (HE-5 negative case): " + output);
        assertTrue(output.contains("\"Cut\""),
                "JPopupMenu's items must render when not pruned: " + output);
    }

    @Test
    void jPopupMenuWithJMenuInvokerIsPruned() throws Exception {
        // D_jmenu_not_clickable / HE-5 positive case (headless). The prune rule is keyed on
        // getInvoker() instanceof JMenu. We test the rule in isolation rather
        // than reproduce the full "JMenu's internal popup is showing" scenario,
        // which requires PopupFactory and a real display (covered in the
        // screen test). A standalone JMenu serves as the invoker; the popup
        // contains a plain JMenuItem to confirm its contents are dropped too.
        JPanel panel = new JPanel();
        JMenu invokerMenu = new JMenu("File");

        @SuppressWarnings("serial")
        JPopupMenu popup = new JPopupMenu() {
            @Override public boolean isVisible() { return true; }
            @Override public Component getInvoker() { return invokerMenu; }
        };
        popup.add(new JMenuItem("Quit"));
        panel.add(popup);

        String output = snapshot(panel);
        assertFalse(output.contains("JPopupMenu (popup_menu)"),
                "JPopupMenu with JMenu invoker must be pruned (HE-5): " + output);
        assertFalse(output.contains("\"Quit\""),
                "Pruned popup's items must not appear: " + output);
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
        // The tree itself has no selection (TREE suppressed in T-014) and is
        // not truncated, so it has no actions and no ref.
        // The root "Root" is expanded and has toggle_expand + selection actions.
        // Leaf nodes "A" and "B" are kept because they have accessible names,
        // but they have no actions so carry no ref.
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTree (tree)\n"
                + "    - (label) \"Root\" [ref=1, expanded] actions: toggle_expand, single-selection\n"
                + "      - (label) \"A\" [collapsed]\n"
                + "      - (label) \"B\" [collapsed]",
                output);
    }

    @Test
    void jListAppearsAsList() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JList<>(new String[]{"Alpha", "Beta"}));

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JList (list) [ref=1] actions: multi-selection\n"
                + "    - (label) \"Alpha\" [ref=2] actions: click\n"
                + "    - (label) \"Beta\" [ref=3] actions: click",
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
                "- JPanel (panel)\n"
                + "  - JPanel (panel) \"Login\"\n"
                + "    - JLabel (label) \"Username\"\n"
                + "    - JTextField (text) \"Username\" [ref=1] text=\"\" actions: get_text, set_text\n"
                + "    - JLabel (label) \"Password\"\n"
                + "    - JPasswordField (password_text) \"Password\" [ref=2] actions: set_text\n"
                + "    - JCheckBox (check_box) \"Remember me\" [ref=3] actions: click\n"
                + "    - JButton (push_button) \"Sign In\" [ref=4] actions: click\n"
                + "    - JButton (push_button) \"Cancel\" [ref=5] actions: click",
                snapshot(root));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP integration test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSnapshotViaMcpClientReturnsValidTextResponse() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("MCP"));
        mcpServer.setConsideredComponents(List.of(panel));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_snapshot", Map.of());

        assertNotNull(result, "result must not be null");
        assertNotEquals(Boolean.TRUE, result.getIsError(), "result must not be an error");
        assertFalse(result.getContent().isEmpty(), "content must not be empty");

        MCPProtocol.Content textContent = result.getContent().get(0);
        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"MCP\" [ref=1] actions: click",
                textContent.getText());
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
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save\" [ref=1] actions: click",
                output);
    }

    @Test
    void filterTreeIsCaseInsensitive() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        String output = snapshot("save", panel);

        assertEquals(
                filterHeader("save") + "\n"
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save\" [ref=1] actions: click",
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
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Third\" [ref=3] actions: click",
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
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"A\" [ref=1] actions: click\n"
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"B\" [ref=2] actions: click",
                output);
    }

    @Test
    void filterTreeOmittedReturnsFullSnapshot() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("Save"));

        // Use the no-filter overload
        String output = snapshot(panel);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Save\" [ref=1] actions: click",
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
                + "- JPanel (panel)\n"
                + "  - JButton (push_button) \"Keep\" [ref=1] actions: click",
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
                + "- JPanel (panel)\n"
                + "  - JPanel (panel) \"Toolbar\"\n"
                + "    - JButton (push_button) \"Open\" [ref=1] actions: click\n"
                + "    - JButton (push_button) \"Close\" [ref=2] actions: click",
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
        assertTrue(output.contains("- JTextField (text) [ref=2] text=\"\" actions: get_text, set_text"),
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
        assertTrue(output.contains("- JTextField (text) [ref=2] text=\"\" actions: get_text, set_text"),
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
        assertTrue(output.contains("JTable (table) \"Customers\""),
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
                + "- JPanel (panel)\n"
                + "  - JPanel (panel) \"Right\"\n"
                + "    - JButton (push_button) \"Target\" [ref=2] actions: click",
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
                "- ClickRecordingPanel -> JPanel (panel) [ref=1] actions: click",
                snapshot(panel));
    }

    @Test
    void unnamedPanelWithOnlyFrameworkMouseListener_isPruned() throws Exception {
        // Register ToolTipManager directly as a MouseListener (avoiding setToolTipText
        // which also sets accessibleDescription, making the panel "named").
        JPanel panel = new JPanel();
        panel.addMouseListener(javax.swing.ToolTipManager.sharedInstance());

        assertEquals(
                "- JPanel (panel)",
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
                "- JButton (push_button) \"OK\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void disabledPanelWithAppMouseListener_showsBangClick() throws Exception {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        panel.setEnabled(false);

        assertEquals(
                "- ClickRecordingPanel -> JPanel (panel) [ref=1, disabled] actions: !click",
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
                "- JSlider (slider) [ref=1, horizontal] value=50 actions: "
                        + (SLIDER_HAS_ACCESSIBLE_ACTIONS ? "increment, decrement, " : "")
                        + "get_value, set_value",
                snapshot(slider));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BR-10 — description source resolution and 120-char cap
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void br10_noDescription_tooltipFillsDescriptionSlot() throws Exception {
        JButton button = new JButton("OK");
        button.setToolTipText("Save the document");

        assertEquals(
                "- JButton (push_button) \"OK\" \"Save the document\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void br10_realDescriptionTakesPrecedenceOverTooltip() throws Exception {
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription("Real description");
        button.setToolTipText("Tooltip should be ignored");

        assertEquals(
                "- JButton (push_button) \"OK\" \"Real description\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void br10_htmlTooltip_isCleanedToPlainText() throws Exception {
        JButton button = new JButton("OK");
        button.setToolTipText("<html><b>Save</b><br>Persists changes</html>");

        assertEquals(
                "- JButton (push_button) \"OK\" \"Save Persists changes\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void br10_tooltipLongerThan120Chars_isTruncatedWithEllipsis() throws Exception {
        // 150 'x's → 120 'x's + U+2026; capped description triggers get_description (T-024)
        String longTooltip = "x".repeat(150);
        JButton button = new JButton("OK");
        button.setToolTipText(longTooltip);

        String expectedDesc = "x".repeat(120) + "\u2026";
        assertEquals(
                "- JButton (push_button) \"OK\" \"" + expectedDesc + "\" [ref=1] actions: click, get_description",
                snapshot(button));
    }

    @Test
    void br10_descriptionLongerThan120Chars_isTruncatedWithEllipsis_symmetricCap()
            throws Exception {
        // Symmetric cap: real accessibleDescription is also truncated.
        // Capped description triggers get_description (T-024).
        String longDesc = "y".repeat(150);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(longDesc);

        String expectedDesc = "y".repeat(120) + "\u2026";
        assertEquals(
                "- JButton (push_button) \"OK\" \"" + expectedDesc + "\" [ref=1] actions: click, get_description",
                snapshot(button));
    }

    @Test
    void br10_descriptionExactly120Chars_isNotTruncated() throws Exception {
        // Boundary: a string of exactly MAX_DESCRIPTION_LENGTH chars must
        // pass through unchanged (no trailing ellipsis).
        String exactly120 = "z".repeat(120);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(exactly120);

        assertEquals(
                "- JButton (push_button) \"OK\" \"" + exactly120 + "\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void br10_jTabbedPaneTab_perTabTooltipAppearsOnPageTabLine() throws Exception {
        JPanel panel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("General", new JPanel());
        tabbedPane.setToolTipTextAt(0, "Common settings");
        panel.add(tabbedPane);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"General\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"General\" \"Common settings\" [selected]",
                snapshot(panel));
    }

    @Test
    void br10_tabContentJComponent_usesItsOwnTooltip_notTheTabsTooltip()
            throws Exception {
        // Regression guard for the PAGE_TAB role gate in
        // SwingUtils.getTooltipAsText. The tab-content button shares the
        // JTabbedPane as its accessible parent — without the role check the
        // button would inherit the tab's tooltip.
        JPanel panel = new JPanel();
        JTabbedPane tabbedPane = new JTabbedPane();
        JButton content = new JButton("ContentBtn");
        content.setToolTipText("Content tooltip");
        tabbedPane.addTab("General", content);
        tabbedPane.setToolTipTextAt(0, "Tab tooltip");
        panel.add(tabbedPane);

        assertEquals(
                "- JPanel (panel)\n"
                + "  - JTabbedPane (page_tab_list) \"General\" [ref=1] actions: single-selection\n"
                + "    - (page_tab) 0 \"General\" \"Tab tooltip\" [selected]\n"
                + "      - JButton (push_button) \"ContentBtn\" \"Content tooltip\" [ref=2] actions: click",
                snapshot(panel));
    }

    @Test
    void br10_nonHtmlTooltipWithAngleBrackets_renderedVerbatim() throws Exception {
        // "List<String>" doesn't start with <html> — angle brackets must
        // not be stripped.
        JButton button = new JButton("OK");
        button.setToolTipText("List<String>");

        assertEquals(
                "- JButton (push_button) \"OK\" \"List<String>\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void br10_perCellPerRowPerNodePerItemTooltips_notSurfacedInSnapshot()
            throws Exception {
        // Regression guard (T-002 BR-10): per-cell / per-row / per-node /
        // per-item tooltips on JTable, JList, JTree, JTableHeader are
        // delivered via the MouseEvent-aware overload
        // getToolTipText(MouseEvent). The snapshot walker has no
        // MouseEvent and calls the no-arg getToolTipText() (see
        // SwingUtils#getTooltipAsText), so these tooltips must not appear
        // in the output. A future change that synthesises a fake
        // MouseEvent to probe per-cell tooltips would bloat snapshots
        // with potentially hundreds of strings per data component — this
        // test catches that.
        //
        // Each sentinel is distinct so a failure pinpoints which
        // component type leaked.

        final String tableCellTip = "SENTINEL_TABLE_CELL_TOOLTIP";
        final String listItemTip = "SENTINEL_LIST_ITEM_TOOLTIP";
        final String treeNodeTip = "SENTINEL_TREE_NODE_TOOLTIP";
        final String headerColTip = "SENTINEL_TABLE_HEADER_TOOLTIP";

        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice"}},
                new Object[]{"Name"})) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                return tableCellTip;
            }
        };

        javax.swing.table.JTableHeader header = new javax.swing.table.JTableHeader() {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                return headerColTip;
            }
        };

        JList<String> list = new JList<String>(new String[]{"Item0"}) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                return listItemTip;
            }
        };

        JTree tree = new JTree() {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                return treeNodeTip;
            }
        };

        JPanel root = new JPanel();
        root.add(table);
        root.add(header);
        root.add(list);
        root.add(tree);

        String output = snapshot(root);

        assertFalse(output.contains(tableCellTip),
                "Per-cell JTable tooltip leaked into snapshot. Output:\n" + output);
        assertFalse(output.contains(listItemTip),
                "Per-item JList tooltip leaked into snapshot. Output:\n" + output);
        assertFalse(output.contains(treeNodeTip),
                "Per-node JTree tooltip leaked into snapshot. Output:\n" + output);
        assertFalse(output.contains(headerColTip),
                "Per-column JTableHeader tooltip leaked into snapshot. Output:\n"
                        + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BR-11 — component identity slot (Case A / B / C rendering)
    //
    // End-to-end snapshot-level assertions that pin the three BR-11 rendering
    // shapes in the tree output. Unit-level coverage of the resolver lives in
    // ComponentClassResolverTest.
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void br11_caseA_standardJButton_rendersJClassRole() throws Exception {
        assertEquals(
                "- JButton (push_button) \"Save\" [ref=1] actions: click",
                snapshot(new JButton("Save")));
    }

    @Test
    void br11_caseA_namedJPanel_rendersJClassRoleAndQuotedName() throws Exception {
        JPanel panel = new JPanel();
        panel.getAccessibleContext().setAccessibleName("Details");

        assertEquals("- JPanel (panel) \"Details\"", snapshot(panel));
    }

    @Test
    void br11_caseA_unnamedJScrollPane_hasNoEmptyQuotesAfterIdentitySlot()
            throws Exception {
        // BR-11: an unnamed component must not emit an empty "" segment after
        // the identity slot. Regression guard — see spec line 478.
        JScrollPane scrollPane = new JScrollPane(new JPanel(),
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        String output = snapshot(scrollPane);
        assertTrue(output.startsWith("- JScrollPane (scroll_pane)\n")
                        || output.equals("- JScrollPane (scroll_pane)"),
                "Expected leading '- JScrollPane (scroll_pane)' without empty quotes; got: "
                        + output);
        assertFalse(output.contains("(scroll_pane) \"\""),
                "Unnamed scroll pane must not render empty quotes; got: " + output);
    }

    @Test
    void br11_caseB_userSubclassOfJButton_usesArrowToJButton() throws Exception {
        assertEquals(
                "- FancyButton -> JButton (push_button) \"Fancy\" [ref=1] actions: click",
                snapshot(new FancyButton()));
    }

    @Test
    void br11_caseB_userSubclassOfAbstractButton_usesArrowToAbstractButton()
            throws Exception {
        // BR-11: AbstractButton qualifies (abstract Swing classes do). Role is
        // driven by the subclass's AccessibleContext — BareButtonAccessible
        // reports PUSH_BUTTON so we can assert the full rendered line.
        BareButton btn = new BareButton();
        btn.setAccessibleName("Bare");
        assertEquals(
                "- BareButton -> AbstractButton (push_button) \"Bare\" [ref=1] actions: click",
                snapshot(btn));
    }

    @Test
    void br11_caseB_userSubclassOfPlaf_walksPastPlafToJButton() throws Exception {
        // class MyArrow extends BasicArrowButton → plaf BasicArrowButton is
        // stripped from the qualifying-ancestor walk; nearest qualifying
        // ancestor is JButton. Concrete display class remains MyArrow.
        MyArrow arrow = new MyArrow();
        assertEquals(
                "- MyArrow -> JButton (push_button) [ref=1] actions: click",
                snapshot(arrow));
    }

    @Test
    void br11_anonymousJButtonSubclass_strippedToJButton() throws Exception {
        // Anonymous subclass must be stripped; walk-up lands on JButton.
        JButton anon = new JButton("Anon") { };
        assertTrue(anon.getClass().isAnonymousClass(),
                "precondition: fixture is an anonymous subclass");
        assertEquals(
                "- JButton (push_button) \"Anon\" [ref=1] actions: click",
                snapshot(anon));
    }

    @Test
    void br11_runtimeProxyWithDoubleDollarName_isStrippedToRealSuperclass()
            throws Exception {
        // Simulate a CGLIB / ByteBuddy / Hibernate runtime proxy:
        // top-level class (null enclosingClass) whose name contains "$$".
        // findDisplayClass must strip it and land on FancyButton, yielding
        // a Case B line (FancyButton → JButton).
        Class<? extends FancyButton> proxyClass = new net.bytebuddy.ByteBuddy()
                .subclass(FancyButton.class)
                .name("com.vaadin.swingmcp.test.FancyButton$$EnhancerByCGLIB$$abc123")
                .make()
                .load(getClass().getClassLoader(),
                        net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        // Preconditions: fixture actually trips the isRuntimeProxy heuristic.
        assertTrue(proxyClass.getName().contains("$$"),
                "precondition: proxy class name must contain '$$'");
        assertNull(proxyClass.getEnclosingClass(),
                "precondition: proxy class must be top-level (no enclosing)");

        FancyButton proxy = proxyClass.getDeclaredConstructor().newInstance();

        assertEquals(
                "- FancyButton -> JButton (push_button) \"Fancy\" [ref=1] actions: click",
                snapshot(proxy));
    }

    @Test
    void br11_caseC_jTreeNode_rendersAsParensLabel() throws Exception {
        // JTree.AccessibleJTreeNode is not a Component, so Case C applies —
        // identity slot is just `(label)` with no class prefix.
        javax.swing.tree.DefaultMutableTreeNode root =
                new javax.swing.tree.DefaultMutableTreeNode("Root");
        root.add(new javax.swing.tree.DefaultMutableTreeNode("Leaf"));
        JTree tree = new JTree(root);

        String output = snapshot(tree);
        assertTrue(output.contains("  - (label) \"Root\""),
                "expected Case C '(label) \"Root\"' for JTree node; got:\n" + output);
    }

    @Test
    void br11_caseC_jListItem_rendersAsParensLabel() throws Exception {
        // JList.AccessibleJListChild is not a Component → Case C.
        JList<String> list = new JList<>(new String[]{"Apple", "Banana"});

        String output = snapshot(list);
        assertTrue(output.contains("  - (label) \"Apple\""),
                "expected Case C '(label) \"Apple\"' for JList item; got:\n" + output);
        assertTrue(output.contains("  - (label) \"Banana\""),
                "expected Case C '(label) \"Banana\"' for JList item; got:\n" + output);
    }

    @Test
    void br11_caseC_jTabbedPaneTab_rendersAsParensPageTab() throws Exception {
        // JTabbedPane.Page is not a Component → Case C with 0-based index.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("First", new JPanel());

        String output = snapshot(tabs);
        assertTrue(output.contains("  - (page_tab) 0 \"First\""),
                "expected Case C '(page_tab) 0 \"First\"'; got:\n" + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** BR-11 Case B fixture — user subclass of a concrete Swing widget. */
    public static class FancyButton extends JButton {
        public FancyButton() { super("Fancy"); }
    }

    /**
     * BR-11 Case B fixture — user subclass of an abstract Swing widget.
     * AbstractButton does not declare {@code implements Accessible} itself;
     * concrete subclasses like JButton add it. The fixture adds it explicitly
     * and supplies a minimal AccessibleContext so the role renders as
     * {@code push_button} rather than {@code unknown}.
     */
    public static class BareButton extends AbstractButton implements Accessible {
        public BareButton() {
            setModel(new javax.swing.DefaultButtonModel());
        }

        void setAccessibleName(String name) {
            getAccessibleContext().setAccessibleName(name);
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleAbstractButton();
            }
            return accessibleContext;
        }

        // Inner class mirrors javax.swing.AbstractButton.AccessibleAbstractButton
        // role semantics without depending on JDK-internal accessible classes.
        private class AccessibleAbstractButton extends AccessibleContext
                implements AccessibleAction {
            @Override public AccessibleRole getAccessibleRole() {
                return AccessibleRole.PUSH_BUTTON;
            }
            @Override public AccessibleStateSet getAccessibleStateSet() {
                AccessibleStateSet set = new AccessibleStateSet();
                if (isEnabled()) set.add(AccessibleState.ENABLED);
                if (isVisible()) set.add(AccessibleState.VISIBLE);
                if (isShowing()) set.add(AccessibleState.SHOWING);
                return set;
            }
            @Override public int getAccessibleIndexInParent() { return -1; }
            @Override public int getAccessibleChildrenCount() { return 0; }
            @Override public Accessible getAccessibleChild(int i) { return null; }
            @Override public java.util.Locale getLocale() {
                return java.util.Locale.getDefault();
            }
            @Override public AccessibleAction getAccessibleAction() { return this; }
            @Override public int getAccessibleActionCount() { return 1; }
            @Override public String getAccessibleActionDescription(int i) {
                return i == 0 ? AccessibleAction.CLICK : null;
            }
            @Override public boolean doAccessibleAction(int i) {
                if (i == 0) { doClick(); return true; }
                return false;
            }
        }
    }

    /** BR-11 fixture — user subclass of a javax.swing.plaf.* class. */
    public static class MyArrow extends javax.swing.plaf.basic.BasicArrowButton {
        public MyArrow() { super(javax.swing.plaf.basic.BasicArrowButton.NORTH); }
    }

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
