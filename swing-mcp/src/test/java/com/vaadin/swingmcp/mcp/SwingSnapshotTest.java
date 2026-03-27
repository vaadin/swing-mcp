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

        assertEquals(
                "- panel\n"
                + "  - push_button \"Save\" [ref=1] actions: click\n"
                + "  - text [ref=2, editable] actions: type",
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
                + "    - list\n"
                + "      - label \"A\" [ref=1] actions: click\n"
                + "      - label \"B\" [ref=2] actions: click\n"
                + "    - scroll_bar [vertical]\n"
                + "      - push_button [ref=3] actions: click\n"
                + "      - push_button [ref=4] actions: click\n"
                + "    - scroll_bar [horizontal]\n"
                + "      - push_button [ref=5] actions: click\n"
                + "      - push_button [ref=6] actions: click",
                output);
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

        assertEquals(
                "- panel\n"
                + "  - table\n"
                + "    - label \"row0\"\n"
                + "    - label \"row1\"\n"
                + "    - label \"row2\"\n"
                + "    - label \"row3\"\n"
                + "    - label \"row4\"\n"
                + "    - label \"row5\"\n"
                + "    - label \"row6\"\n"
                + "    - label \"row7\"\n"
                + "    - label \"row8\"\n"
                + "    - label \"row9\"\n"
                + "    ... and 5 more items",
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
                + "  - page_tab_list \"Tab1\"\n"
                + "    - page_tab \"Tab1\" [selected]\n"
                + "      - push_button \"InTab1\" [ref=1] actions: click\n"
                + "    - page_tab \"Tab2\"",
                output);
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
                + "  - push_button \"Disabled\" [ref=1, disabled] actions: click",
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
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

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
