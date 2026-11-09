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
import com.github.mvysny.tinymcpserver.Parameters;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetItemCountTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetItemCountTool tool;
    private SwingGetItemsTool itemsTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        tool = new SwingGetItemCountTool();
        itemsTool = new SwingGetItemsTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getCount(int ref) throws Exception {
        MCPProtocol.Content result = tool.execute(
                new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_5items() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma", "Delta", "Echo"});
        snapshot(list);
        assertEquals("5", getCount(context.getRefOf(list)));
    }

    @Test
    void jList_200items() throws Exception {
        String[] items = new String[200];
        for (int i = 0; i < 200; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        assertEquals("200", getCount(context.getRefOf(list)));
    }

    @Test
    void jList_empty() throws Exception {
        JList<String> list = new JList<>();
        snapshot(list);
        assertEquals("0", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane — refused: the snapshot already lists every tab inline
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_isRejected() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("General", new JPanel());
        tp.addTab("Advanced", new JPanel());
        snapshot(tp);
        int ref = context.getRefOf(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_item_count"),
                "Expected not-supported error for JTabbedPane, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBox_3items() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        snapshot(combo);
        assertEquals("3", getCount(context.getRefOf(combo)));
    }

    @Test
    void jComboBox_empty() throws Exception {
        JComboBox<String> combo = new JComboBox<>();
        snapshot(combo);
        assertEquals("0", getCount(context.getRefOf(combo)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_rowSelectionMode() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{
                        {"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"},
                        {"Dave", "40"}, {"Eve", "28"}, {"Frank", "33"},
                        {"Grace", "45"}, {"Hank", "29"}, {"Ivy", "31"}, {"Jack", "27"}
                },
                new Object[]{"Name", "Age"}));
        snapshot(table);
        assertEquals("10", getCount(context.getRefOf(table)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — all selection modes succeed
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_columnSelectionMode_returnsRowCount() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}, {"b"}, {"c"}}, new Object[]{"col"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        context.putRef(99, table);
        assertEquals("3", getCount(99));
    }

    @Test
    void jTable_cellSelectionMode_returnsRowCount() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}, {"b"}, {"c"}, {"d"}}, new Object[]{"col"}));
        table.setCellSelectionEnabled(true);
        context.putRef(99, table);
        assertEquals("4", getCount(99));
    }

    @Test
    void jTable_noSelectionAllowed_returnsRowCount() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}, {"b"}}, new Object[]{"col"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        context.putRef(99, table);
        assertEquals("2", getCount(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void componentWithoutSelectionSupport_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_item_count"));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void jTree_returnsError() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_item_count"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read-only / ref preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapPreservedAfterCall() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        int ref = context.getRefOf(list);
        String count1 = getCount(ref);
        String count2 = getCount(ref);
        assertEquals(count1, count2);
    }

    @Test
    void disabledList_stillReturnsCount() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        snapshot(list);
        assertEquals("3", getCount(context.getRefOf(list)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Consistency with swing_get_items
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void countMatchesGetItems_JList() throws Exception {
        String[] items = new String[50];
        for (int i = 0; i < 50; i++) items[i] = "Item-" + i;
        JList<String> list = new JList<>(items);
        snapshot(list);
        int ref = context.getRefOf(list);
        String count = getCount(ref);
        String itemsJson = itemsTool.execute(
                new Parameters(Map.of("ref", ref, "offset", 0, "length", 0)), context).getText();
        assertTrue(itemsJson.contains("\"totalCount\":" + count));
    }

    @Test
    void countMatchesGetItems_JTable() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}, {"C", "3"}},
                new Object[]{"Name", "Val"}));
        snapshot(table);
        int ref = context.getRefOf(table);
        String count = getCount(ref);
        String itemsJson = itemsTool.execute(
                new Parameters(Map.of("ref", ref, "offset", 0, "length", 0)), context).getText();
        assertTrue(itemsJson.contains("\"totalCount\":" + count));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        snapshot(list);
        assertEquals("2", getCount(context.getRefOf(list)));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        // Refused: the snapshot lists every tab inline.
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("First", new JPanel());
        tp.addTab("Second", new JPanel());
        assertNotSupported(tp);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y"});
        snapshot(combo);
        assertEquals("2", getCount(context.getRefOf(combo)));
    }

    @Test
    void componentMatrix_JTable() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Col1", "Col2"}));
        snapshot(table);
        assertEquals("2", getCount(context.getRefOf(table)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertNotSupported(Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getCount(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_get_item_count. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test void componentMatrix_JButton() throws Exception { assertNotSupported(new JButton("OK")); }
    @Test void componentMatrix_JCheckBox() throws Exception { assertNotSupported(new JCheckBox("Check")); }
    @Test void componentMatrix_JRadioButton() throws Exception { assertNotSupported(new JRadioButton("Option")); }
    @Test void componentMatrix_JTextField() throws Exception { assertNotSupported(new JTextField("text")); }
    @Test void componentMatrix_JTextArea() throws Exception { assertNotSupported(new JTextArea("text")); }
    @Test void componentMatrix_JToggleButton() throws Exception { assertNotSupported(new JToggleButton("Toggle")); }
    @Test void componentMatrix_JSlider() throws Exception { assertNotSupported(new JSlider(0, 100, 50)); }
    @Test void componentMatrix_JPanel() throws Exception { assertNotSupported(new JPanel()); }
    @Test void componentMatrix_JScrollPane() throws Exception { assertNotSupported(new JScrollPane(new JTextArea("c"))); }
    @Test void componentMatrix_JSplitPane() throws Exception { assertNotSupported(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())); }
    @Test void componentMatrix_JLabel() throws Exception { assertNotSupported(new JLabel("Hello")); }
    @Test void componentMatrix_JProgressBar() throws Exception { assertNotSupported(new JProgressBar(0, 100)); }
    @Test void componentMatrix_JSpinner() throws Exception { assertNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))); }
    @Test void componentMatrix_JToolBar() throws Exception { JToolBar tb = new JToolBar(); tb.add(new JButton("T")); assertNotSupported(tb); }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        assertNotSupported(menu);
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        assertNotSupported(item);
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        assertNotSupported(new JTree(root));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertNotSupported(iframe);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetItemCountViaMcpClient() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_get_item_count",
                        Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "get_item_count should succeed");
        assertFalse(result.getContent().isEmpty(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertEquals("3", text);
    }
}
