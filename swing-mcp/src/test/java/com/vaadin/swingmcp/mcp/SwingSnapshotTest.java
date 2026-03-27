package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSnapshotTest {

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
        MCPProtocol.Content result = tool.execute(Map.of(), context);
        return result.getText();
    }

    // ── Static setup for the MCP integration test ─────────────────────────────

    private static final int MCP_PORT = 18093;
    private static FakeMCPServer mcpServer;
    private static McpSyncClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        mcpServer = new FakeMCPServer(MCP_PORT, "/mcp");
        mcpServer.start();

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + MCP_PORT + "/mcp")
                .openConnectionOnStartup(false)
                .build();

        mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
        mcpClient.initialize();
    }

    @AfterAll
    static void stopMcpServer() {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (mcpServer != null) {
            mcpServer.stop();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Direct tool invocation tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void simpleHierarchyProducesCorrectRolesNamesAndRefs() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Save");
        JTextField textField = new JTextField();
        panel.add(button);
        panel.add(textField);

        String output = snapshot(panel);

        // Root panel appears
        assertTrue(output.contains("panel"), "root panel should appear");
        // Button with name and ref
        assertTrue(output.contains("push_button"), "button role");
        assertTrue(output.contains("\"Save\""), "button name");
        assertTrue(output.contains("ref=1"), "button gets ref 1");
        assertTrue(output.contains("actions: click"), "button action");
        // Text field with ref
        assertTrue(output.contains("text"), "text field role");
        assertTrue(output.contains("ref=2"), "text field gets ref 2");
        assertTrue(output.contains("actions: type"), "text field action");
    }

    @Test
    void refsAreAssignedStartingFromOne() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JButton("First"));
        panel.add(new JButton("Second"));

        String output = snapshot(panel);

        assertTrue(output.contains("ref=1"), "first ref is 1");
        assertTrue(output.contains("ref=2"), "second ref is 2");
        assertFalse(output.contains("ref=0"), "ref 0 must never appear");
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
        String[] lines = output.split("\n");

        // outer panel at depth 0: "- panel "Outer""
        // inner panel at depth 1: "  - panel "Inner""
        // button at depth 2:      "    - push_button "Go""
        boolean foundOuter = false, foundInner = false, foundButton = false;
        for (String line : lines) {
            if (line.equals("- panel \"Outer\"")) foundOuter = true;
            if (line.equals("  - panel \"Inner\"")) foundInner = true;
            if (line.startsWith("    - push_button \"Go\"")) foundButton = true;
        }
        assertTrue(foundOuter, "outer panel line: " + output);
        assertTrue(foundInner, "inner panel line: " + output);
        assertTrue(foundButton, "button at depth 2: " + output);
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

        assertTrue(output.contains("\"Visible\""), "visible button should appear");
        assertFalse(output.contains("\"Hidden\""), "invisible button must not appear");
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
        String[] lines = output.split("\n");

        // The button should appear directly under root (depth 1), not at depth 2
        boolean buttonAtDepth1 = false;
        for (String line : lines) {
            if (line.startsWith("  - push_button")) {
                buttonAtDepth1 = true;
            }
        }
        assertTrue(buttonAtDepth1, "button should be at depth 1 after unnamed panel is pruned: " + output);
        assertFalse(output.contains("\n    - push_button"), "button must NOT be at depth 2: " + output);
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
        String[] lines = output.split("\n");

        // The titled panel has no accessible name but it has a TitledBorder → must be kept
        // So the button should be at depth 2 (under the titled panel), not depth 1
        boolean buttonAtDepth2 = false;
        for (String line : lines) {
            if (line.startsWith("    - push_button")) {
                buttonAtDepth2 = true;
            }
        }
        assertTrue(buttonAtDepth2, "button under titled panel should be at depth 2: " + output);
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

        // Named panel must appear in output
        assertTrue(output.contains("\"FormSection\""), "named panel should appear: " + output);
        // Button at depth 2
        assertTrue(output.contains("    - push_button"), "button at depth 2: " + output);
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

        assertTrue(output.contains("push_button"), "real button should appear");
        // The CellRendererPane has role PANEL and no children; it should be excluded by HE-2.
        // Count panel lines: only the root panel line and no extra panel from crp.
        long panelLines = Arrays.stream(output.split("\n"))
                .filter(l -> l.trim().startsWith("- panel") || l.trim().startsWith("- panel"))
                .count();
        // Root is a panel; the CellRendererPane-backed accessible should NOT appear
        // (verified indirectly: button is there, and we have at most 1 "panel" line)
        assertEquals(1, panelLines,
                "CellRendererPane must be excluded; only root panel line expected: " + output);
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

        assertFalse(output.contains("root_pane"), "root_pane must be pruned: " + output);
        assertFalse(output.contains("layered_pane"), "layered_pane must be pruned: " + output);
        // Button must still be present
        assertTrue(output.contains("push_button"), "button must survive pruning: " + output);
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

        assertTrue(output.contains("scroll_pane"), "scroll_pane must be kept: " + output);
        assertFalse(output.contains("viewport"), "viewport must be pruned: " + output);
        // list should appear directly under scroll_pane
        int scrollPaneDepth = depthOf(output, "scroll_pane");
        int listDepth = depthOf(output, "list");
        assertEquals(scrollPaneDepth + 1, listDepth,
                "list should be direct child of scroll_pane: " + output);
    }

    @Test
    void largeJTableIsTruncatedWithSummary() throws Exception {
        DefaultTableModel model = new DefaultTableModel(15, 1);
        for (int i = 0; i < 15; i++) {
            model.setValueAt("row" + i, i, 0);
        }
        JTable table = new JTable(model);
        JPanel root = new JPanel();
        root.add(table);

        String output = snapshot(root);

        // Truncation summary must appear
        assertTrue(output.contains("... and 5 more items"),
                "truncation summary missing (expected '... and 5 more items'): " + output);
        // Exactly MAX_DATA_CHILDREN rows shown (count row occurrences by depth)
        long tableChildLines = Arrays.stream(output.split("\n"))
                .filter(l -> l.startsWith("      - ")) // depth 2 = 6 spaces + "- "
                .count();
        assertTrue(tableChildLines <= SwingSnapshotTool.MAX_DATA_CHILDREN,
                "at most " + SwingSnapshotTool.MAX_DATA_CHILDREN + " rows should appear: " + output);
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

        assertTrue(output.contains("menu_bar"), "menu_bar should appear: " + output);
        assertTrue(output.contains("\"File\""), "menu name should appear: " + output);
        assertTrue(output.contains("\"Open\""), "Open item should appear even when menu is closed: " + output);
        assertTrue(output.contains("\"Save\""), "Save item should appear even when menu is closed: " + output);
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

        // page_tab_list (JTabbedPane) must appear
        assertTrue(output.contains("page_tab_list"), "page_tab_list should appear: " + output);
        // The selected tab should be marked with [selected]
        assertTrue(output.contains("selected"), "selected state must appear on selected tab: " + output);
        // Selected tab's content button must appear
        assertTrue(output.contains("\"InTab1\""), "selected tab content must appear: " + output);
        // Non-selected tab's content must NOT appear (not accessible via standard API)
        assertFalse(output.contains("\"InTab2\""), "non-selected tab content must not appear: " + output);
    }

    @Test
    void onlyMeaningfulStatesAreShown() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Normal");
        panel.add(button);

        String output = snapshot(panel);

        // These states must NEVER appear in the output
        assertFalse(output.contains("visible"), "'visible' state must be omitted: " + output);
        assertFalse(output.contains("enabled"), "'enabled' state must be omitted: " + output);
        assertFalse(output.contains("showing"), "'showing' state must be omitted: " + output);
        assertFalse(output.contains("opaque"), "'opaque' state must be omitted: " + output);
    }

    @Test
    void disabledComponentAppearsWithDisabledState() throws Exception {
        JPanel panel = new JPanel();
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        panel.add(button);

        String output = snapshot(panel);

        assertTrue(output.contains("push_button"), "disabled button should appear: " + output);
        assertTrue(output.contains("disabled"), "disabled state must appear: " + output);
    }

    @Test
    void multipleRootsAreSeparatedByDividerAndRefsAreGlobal() throws Exception {
        JPanel root1 = new JPanel();
        root1.add(new JButton("A"));

        JPanel root2 = new JPanel();
        root2.add(new JButton("B"));

        String output = snapshot(root1, root2);

        assertTrue(output.contains("---"), "roots must be separated by '---': " + output);
        // Refs are global: first button gets ref=1, second gets ref=2
        assertTrue(output.contains("ref=1"), "ref 1 in root1: " + output);
        assertTrue(output.contains("ref=2"), "ref 2 in root2 (global numbering): " + output);

        // ref=1 must appear before ref=2 in the output
        int idx1 = output.indexOf("ref=1");
        int idx2 = output.indexOf("ref=2");
        assertTrue(idx1 < idx2, "ref=1 must come before ref=2: " + output);
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

        // Get the text from the first content item
        Object first = result.content().get(0);
        // Content is a polymorphic type; use toString to get a string representation
        // that includes the actual text value
        String contentText = first.toString();
        assertTrue(contentText.contains("push_button") || contentText.contains("panel"),
                "snapshot output must mention UI components: " + contentText);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the indentation depth of the first line in {@code output} that contains {@code keyword}.
     * Depth is the number of leading "  " (two-space) pairs before the "- ".
     */
    private static int depthOf(String output, String keyword) {
        for (String line : output.split("\n")) {
            if (line.contains(keyword)) {
                int indent = 0;
                while (indent * 2 < line.length() && line.charAt(indent * 2) == ' ') {
                    indent++;
                }
                return indent;
            }
        }
        throw new AssertionError("Keyword '" + keyword + "' not found in output:\n" + output);
    }

    /**
     * A CellRendererPane that also implements Accessible so it can appear
     * in the accessibility tree (allowing HE-2 to be tested).
     */
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
