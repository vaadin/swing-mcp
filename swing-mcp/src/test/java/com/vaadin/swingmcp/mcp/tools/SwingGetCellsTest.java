/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetCellsTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetCellsTool tool;
    private SwingClickTool clickTool;
    private SwingGetItemsTool getItemsTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetCellsTool();
        clickTool = new SwingClickTool();
        getItemsTool = new SwingGetItemsTool();
        context = new SwingToolContext(Runnable::run);
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    private String getCells(int ref, int offset, int length) throws Exception {
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("ref", ref, "offset", offset, "length", length)),
                context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — refused, with a redirect to swing_get_items (D_no_jtable_cells)
    // ══════════════════════════════════════════════════════════════════════════

    private JTable createTable(int rows, int cols) {
        DefaultTableModel model = new DefaultTableModel(rows, cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                model.setValueAt("r" + r + "c" + c, r, c);
            }
        }
        return new JTable(model);
    }

    @Test
    void jtable_returnsRedirectError() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(table), 0, 5));
        assertEquals(
                "JTable does not support swing_get_cells. Table cells are plain text labels \u2014 "
                        + "use swing_get_items to page through rows.",
                ex.getMessage());
    }

    @Test
    void jtable_redirectErrorDoesNotReplaceRefMap() throws Exception {
        JTable table = createTable(15, 1);
        snapshot(table);
        int ref = context.getRefOf(table);
        assertThrows(MCPErrorResponseException.class, () -> getCells(ref, 0, 5));
        assertSame(table, context.getAccessibleByRef(ref));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList — truncated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncatedJList_returnsChildren() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 5, 3);
        assertEquals(
                "Showing 3 children from offset 5 (total 20) for list [ref=1]\n"
                + "- (label) \"Item-5\" [ref=2] actions: click\n"
                + "- (label) \"Item-6\" [ref=3] actions: click\n"
                + "- (label) \"Item-7\" [ref=4] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTree — truncated
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncatedJTree_returnsTopLevelNodes() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        String output = getCells(context.getRefOf(tree), 5, 3);
        assertTrue(output.startsWith("Showing 3 children from offset 5 (total 10) for tree [ref=1]"),
                "Header should show correct paging info, got: " + output);
        assertTrue(output.contains("\"Node-5\""), "Should contain Node-5, got: " + output);
        assertTrue(output.contains("\"Node-7\""), "Should contain Node-7, got: " + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Parent ref=1 and follow-up calls
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void parentGetsRef1_childRefsStartFrom2() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        // The parent holds ref=1 without being rendered.
        assertTrue(output.contains("[ref=2]"), "First child should have ref=2, got: " + output);
        assertTrue(output.contains("[ref=3]"), "Second child should have ref=3, got: " + output);
        assertTrue(output.contains("[ref=4]"), "Third child should have ref=4, got: " + output);
    }

    @Test
    void getCellsAgainWithRef1_noPriorSnapshot() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        getCells(context.getRefOf(list), 0, 3);
        String output2 = getCells(1, 10, 3);
        assertEquals(
                "Showing 3 children from offset 10 (total 20) for list [ref=1]\n"
                + "- (label) \"Item-10\" [ref=2] actions: click\n"
                + "- (label) \"Item-11\" [ref=3] actions: click\n"
                + "- (label) \"Item-12\" [ref=4] actions: click",
                output2);
    }

    @Test
    void getCells_thenGetItems_withRef1() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        getCells(context.getRefOf(list), 0, 3);
        MCPProtocol.Content result = getItemsTool.execute(
                new Parameters(Map.of("ref", 1, "offset", 0, "length", 3)),
                context);
        String json = result.getText();
        assertTrue(json.contains("\"totalCount\":20"), "Should see all 20 items");
        assertTrue(json.contains("\"Item-0\""), "Should contain Item-0");
    }

    @Test
    void getCells_thenClickChildRef() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        getCells(context.getRefOf(list), 0, 3);
        assertDoesNotThrow(() -> clickTool.execute(new Parameters(Map.of("ref", 2)), context));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ref map replacement
    // ══════════════════════════════════════════════════════════════════════════

    private static JList<String> bigList() {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        return new JList<>(items);
    }

    @Test
    void oldRefsInvalidAfterGetCells() throws Exception {
        JList<String> list = bigList();
        JPanel panel = new JPanel();
        panel.add(new JButton("Btn"));
        panel.add(list);
        snapshot(panel);

        JButton btn = (JButton) panel.getComponent(0);
        int btnRef = context.getRefOf(btn);
        assertEquals(1, btnRef, "Button should be ref=1 in original snapshot");

        getCells(context.getRefOf(list), 0, 3);

        Accessible resolved = context.getAccessibleByRef(1);
        assertNotSame(btn, resolved, "ref=1 should no longer point to the button");
        assertSame(list, resolved, "ref=1 should now point to the JList parent");
    }

    @Test
    void snapshotRestoresFullRefMap() throws Exception {
        JList<String> list = bigList();
        JPanel panel = new JPanel();
        JButton btn = new JButton("Btn");
        panel.add(btn);
        panel.add(list);
        snapshot(panel);

        getCells(context.getRefOf(list), 0, 3);

        snapshot(panel);

        int btnRef = context.getRefOf(btn);
        assertDoesNotThrow(() -> clickTool.execute(new Parameters(Map.of("ref", btnRef)), context));
    }

    @Test
    void refMapReplacedEvenWhenOutputEmpty() throws Exception {
        JList<String> list = bigList();
        JPanel panel = new JPanel();
        panel.add(new JButton("Btn"));
        panel.add(list);
        snapshot(panel);

        int listRef = context.getRefOf(list);

        // Offset past the end: no children, yet the ref map is still replaced.
        getCells(listRef, 100, 5);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> context.getAccessibleByRef(2));
        assertEquals("Component with ref 2 does not exist (valid refs: 1\u20131).", ex.getMessage());
        assertSame(list, context.getAccessibleByRef(1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Non-truncated large data component — succeeds
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonTruncatedJList_succeeds() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 10);
        assertEquals(
                "Showing 3 children from offset 0 (total 3) for list [ref=1]\n"
                + "- (label) \"A\" [ref=2] actions: click\n"
                + "- (label) \"B\" [ref=3] actions: click\n"
                + "- (label) \"C\" [ref=4] actions: click",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Empty large data component
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void emptyJList_succeeds() throws Exception {
        JList<String> list = new JList<>(new String[]{});
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 10);
        assertEquals(
                "Showing 0 children from offset 0 (total 0) for list [ref=1]",
                output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void nonLargeDataComponent_returnsError() throws Exception {
        JButton btn = new JButton("Click");
        snapshot(btn);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(btn), 0, 5));
        assertEquals("JButton does not support swing_get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test
    void invalidRef_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(9999, 0, 5));
        assertEquals("Component with ref 9999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void disabledComponent_succeeds() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        list.setEnabled(false);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "Should succeed on disabled component");
    }

    @Test
    void negativeOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(list), -1, 5));
    }

    @Test
    void negativeLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPErrorResponseException.class,
                () -> getCells(context.getRefOf(list), 0, -1));
    }

    @Test
    void missingOffset_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", context.getRefOf(list), "length", 5)), context));
    }

    @Test
    void missingLength_returnsError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        assertThrows(MCPServerException.class,
                () -> tool.execute(new Parameters(Map.of("ref", context.getRefOf(list), "offset", 0)), context));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Header line
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void headerLineShowsCorrectInfo() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 5, 3);
        String firstLine = output.split("\n")[0];
        assertEquals("Showing 3 children from offset 5 (total 20) for list [ref=1]", firstLine);
    }

    @Test
    void offsetPastTotal_isHeaderAlone() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        assertEquals("Showing 0 children from offset 7 (total 3) for list [ref=1]",
                getCells(context.getRefOf(list), 7, 2));
    }

    @Test
    void nullChild_rendersAsNullInItsPlace() throws Exception {
        JList<String> list = listWithChildAt1(null);
        context.putRef(99, list);
        assertEquals(
                "Showing 3 children from offset 0 (total 3) for list [ref=1]\n"
                + "- (label) \"A\" [ref=2] actions: click\n"
                + "- null\n"
                + "- (label) \"C\" [ref=3] actions: click",
                getCells(99, 0, 3));
    }

    @Test
    void childWithoutActionOrName_isStillListed() throws Exception {
        JList<String> list = listWithChildAt1(new JPanel());
        context.putRef(99, list);
        assertEquals(
                "Showing 3 children from offset 0 (total 3) for list [ref=1]\n"
                + "- (label) \"A\" [ref=2] actions: click\n"
                + "- JPanel (panel)\n"
                + "- (label) \"C\" [ref=3] actions: click",
                getCells(99, 0, 3));
    }

    @Test
    void allRefs_numbersChildWithoutAction() throws Exception {
        JPanel panel = new JPanel();
        JList<String> list = listWithChildAt1(panel);
        context.putRef(99, list);
        assertEquals(
                "Showing 3 children from offset 0 (total 3) for list [ref=1]\n"
                + "- (label) \"A\" [ref=2] actions: click\n"
                + "- JPanel (panel) [ref=3]\n"
                + "- (label) \"C\" [ref=4] actions: click",
                tool.execute(new Parameters(Map.of("ref", 99, "offset", 0, "length", 3,
                        "all_refs", true)), context).getText());
        assertSame(panel, context.getAccessibleByRef(3));
    }

    @Test
    void allRefs_numbersTruncatedJTreeLeaves() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        assertEquals(
                "Showing 2 children from offset 7 (total 10) for tree [ref=1]\n"
                + "- (label) \"Node-7\" [ref=2, collapsed]\n"
                + "- (label) \"Node-8\" [ref=3, collapsed]",
                tool.execute(new Parameters(Map.of("ref", context.getRefOf(tree),
                        "offset", 7, "length", 2, "all_refs", true)), context).getText());
    }

    @Test
    void allRefs_malformed_leavesRefMapIntact() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        int ref = context.getRefOf(list);
        assertThrows(MCPServerException.class, () -> tool.execute(new Parameters(Map.of(
                "ref", ref, "offset", 0, "length", 2, "all_refs", "yes")), context));
        assertSame(list, context.getAccessibleByRef(ref));
    }

    /** A three-item list whose accessible child 1 is {@code child} instead of item "B". */
    private static JList<String> listWithChildAt1(javax.accessibility.Accessible child) {
        return new JList<>(new String[]{"A", "B", "C"}) {
            @Override
            public javax.accessibility.AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJList() {
                        @Override
                        public javax.accessibility.Accessible getAccessibleChild(int i) {
                            return i == 1 ? child : super.getAccessibleChild(i);
                        }
                    };
                }
                return accessibleContext;
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — expected to succeed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        String output = getCells(context.getRefOf(list), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "JList should succeed");
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        for (int i = 0; i < 10; i++) {
            root.add(new DefaultMutableTreeNode("Node-" + i));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        tree.setSize(200, 400);
        tree.expandRow(0);
        snapshot(tree);
        String output = getCells(context.getRefOf(tree), 0, 3);
        assertTrue(output.contains("Showing 3 children"), "JTree should succeed");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — expected to fail
    // ══════════════════════════════════════════════════════════════════════════

    private void assertNotSupported(Component comp) throws Exception {
        snapshot(comp);
        int ref = context.getRefOf(comp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(ref, 0, 5));
        String expectedClass = ComponentClassResolver.resolveClassName((javax.accessibility.Accessible) comp);
        assertEquals(expectedClass + " does not support swing_get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test void componentMatrix_JTable() throws Exception {
        // JTable gets a dedicated redirect error, not the generic one.
        JTable table = createTable(15, 1);
        snapshot(table);
        int ref = context.getRefOf(table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(ref, 0, 5));
        assertEquals(
                "JTable does not support swing_get_cells. Table cells are plain text labels \u2014 "
                        + "use swing_get_items to page through rows.",
                ex.getMessage());
    }
    @Test void componentMatrix_JButton() throws Exception { assertNotSupported(new JButton("B")); }
    @Test void componentMatrix_JCheckBox() throws Exception { assertNotSupported(new JCheckBox("C")); }
    @Test void componentMatrix_JRadioButton() throws Exception { assertNotSupported(new JRadioButton("R")); }
    @Test void componentMatrix_JTextField() throws Exception { assertNotSupported(new JTextField("T")); }
    @Test void componentMatrix_JTextArea() throws Exception { assertNotSupported(new JTextArea("A")); }
    @Test void componentMatrix_JComboBox() throws Exception { assertNotSupported(new JComboBox<>(new String[]{"A", "B"})); }
    @Test void componentMatrix_JToggleButton() throws Exception { assertNotSupported(new JToggleButton("T")); }
    @Test void componentMatrix_JSlider() throws Exception { assertNotSupported(new JSlider(0, 100, 50)); }
    @Test void componentMatrix_JPanel() throws Exception {
        // No ref, so checked through SwingUtils.
        JPanel p = new JPanel();
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isGetCellsSupported(p));
    }
    @Test void componentMatrix_JScrollPane() throws Exception {
        // No ref, so checked through SwingUtils.
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isGetCellsSupported(new JScrollPane(new JTextArea("c"))));
    }
    @Test void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("T1", new JPanel());
        assertNotSupported(tp);
    }
    @Test void componentMatrix_JSplitPane() throws Exception { assertNotSupported(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())); }
    @Test void componentMatrix_JLabel() throws Exception {
        // No ref, so checked through SwingUtils.
        JLabel label = new JLabel("Hello");
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isGetCellsSupported(label));
    }
    @Test void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertNotSupported(pb);
    }
    @Test void componentMatrix_JSpinner() throws Exception { assertNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))); }
    @Test void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("T"));
        // No ref, so checked through SwingUtils.
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isGetCellsSupported(tb));
    }
    @Test void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        // No ref, so checked through SwingUtils.
        assertFalse(com.vaadin.swingmcp.mcp.SwingUtils.isGetCellsSupported(mb));
    }
    @Test void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenu m = new JMenu("File");
        m.add(new JMenuItem("Open"));
        context.putRef(99, (javax.accessibility.Accessible) m);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCells(99, 0, 5));
        assertEquals("JMenu does not support swing_get_cells. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }
    @Test void componentMatrix_JMenuItem() throws Exception { assertNotSupported(new JMenuItem("Open")); }
    @Test void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> getCells(99, 0, 1));
    }
    @Test void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> getCells(99, 0, 1));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetCellsViaMcpClient() throws Exception {
        String[] items = new String[20];
        for (int i = 0; i < 20; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_get_cells",
                        Map.of("ref", 1, "offset", 0, "length", 3));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "get_cells should succeed");
        assertFalse(result.getContent().isEmpty(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("Showing 3 children from offset 0 (total 20) for list [ref=1]"),
                "Should have header, got: " + text);
        assertTrue(text.contains("\"Item-0\""), "Should contain Item-0, got: " + text);
    }
}
