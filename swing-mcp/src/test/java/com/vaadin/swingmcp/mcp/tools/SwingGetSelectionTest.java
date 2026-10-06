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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetSelectionTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getSelection(int ref) throws Exception {
        MCPProtocol.Content result = getSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_singleSelectedItem() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}", json);
    }

    @Test
    void jList_noSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":0,\"selected\":[]}", json);
    }

    @Test
    void jList_staleIndexPastShrunkModel_isCountedButNotListed() throws Exception {
        // R_selection_null_entries: the model shrinks without firing intervalRemoved.
        String[] data = {"A", "B", "C"};
        int[] size = {3};
        JList<String> list = new JList<>(new AbstractListModel<String>() {
            @Override public int getSize() { return size[0]; }
            @Override public String getElementAt(int i) { return data[i]; }
        });
        list.setSelectedIndices(new int[]{0, 2});
        size[0] = 2;
        context.putRef(99, list);
        String json = getSelection(99);
        assertEquals("{\"selectedCount\":2,\"selected\":[{\"index\":0,\"name\":\"A\"}]}", json);
    }

    @Test
    void jList_multipleSelectedItems() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma", "Delta"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(new int[]{0, 2, 3});
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":3,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alpha\"},"
                        + "{\"index\":2,\"name\":\"Gamma\"},"
                        + "{\"index\":3,\"name\":\"Delta\"}"
                        + "]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_selectedTab() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("General", new JPanel());
        tp.addTab("Advanced", new JPanel());
        tp.setSelectedIndex(1);
        snapshot(tp);
        String json = getSelection(context.getRefOf(tp));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Advanced\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComboBox
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jComboBox_selectedItem() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(2);
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Blue\"}]}", json);
    }


    @Test
    void jComboBox_empty() throws Exception {
        JComboBox<String> combo = new JComboBox<>();
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":0,\"selected\":[]}", json);
    }

    @Test
    void editableJComboBox_typedValueOutsideModel_isIndexMinusOne() throws Exception {
        // R_selection_null_entries: Swing counts the value but returns no selected child.
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setEditable(true);
        combo.setSelectedItem("Magenta");
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":-1,\"name\":\"Magenta\"}]}", json);
    }

    @Test
    void jComboBox_modelValueOutsideItems_isIndexMinusOne() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.getModel().setSelectedItem("Magenta");
        context.putRef(99, combo);
        String json = getSelection(99);
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":-1,\"name\":\"Magenta\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection mode
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_singleRowSelected() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(1, 1);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Bob | 25\"}]}",
                json);
    }

    @Test
    void jTable_multipleRowsSelected() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        table.addRowSelectionInterval(2, 2);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":2,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alice | 30\"},"
                        + "{\"index\":2,\"name\":\"Carol | 35\"}"
                        + "]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — unsupported modes
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_columnSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        snapshot(table);
        // No selection action in column mode, so a forced ref reaches the refusal.
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    @Test
    void jTable_cellSelectionMode_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    @Test
    void jTable_noSelectionAllowed_returnsError() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}}, new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        context.putRef(99, table);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("row-selection mode"),
                "Expected JTable-specific error, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRef_returnsMcpError() throws Exception {
        JList<String> list = new JList<>(new String[]{"A"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20132).", ex.getMessage());
    }

    @Test
    void componentWithoutSelectionSupport_returnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"),
                "Expected generic error, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void jTree_returnsError() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        JTree tree = new JTree(root);
        snapshot(tree);
        // No ref: its selection is suppressed (D_no_jtree_selection).
        context.putRef(99, tree);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"),
                "Expected generic error for JTree, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Read-only / ref preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void refMapPreservedAfterGetSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setSelectedIndex(0);
        snapshot(list);
        int ref = context.getRefOf(list);

        String json1 = getSelection(ref);
        String json2 = getSelection(ref);
        assertEquals(json1, json2);
    }

    @Test
    void disabledList_selectionStillReadable() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B"});
        list.setEnabled(false);
        list.setSelectedIndex(1);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"B\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Truncation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void truncation_whenSelectionExceedsMax() throws Exception {
        int count = SwingGetSelectionTool.MAX_SELECTION_ITEMS + 5;
        String[] items = new String[count];
        int[] allIndices = new int[count];
        for (int i = 0; i < count; i++) {
            items[i] = "Item" + i;
            allIndices[i] = i;
        }
        JList<String> list = new JList<>(items);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(allIndices);
        snapshot(list);

        String json = getSelection(context.getRefOf(list));
        assertEquals(expectedTruncated(count, i -> "Item" + i), json);
    }

    @Test
    void jTableTruncation_countsEveryRow() throws Exception {
        int count = SwingGetSelectionTool.MAX_SELECTION_ITEMS + 5;
        Object[][] rows = new Object[count][];
        for (int i = 0; i < count; i++) {
            rows[i] = new Object[]{"Row" + i};
        }
        JTable table = new JTable(new DefaultTableModel(rows, new Object[]{"Name"}));
        table.setRowSelectionInterval(0, count - 1);
        snapshot(table);

        String json = getSelection(context.getRefOf(table));
        assertEquals(expectedTruncated(count, i -> "Row" + i), json);
    }

    /** The JSON for {@code count} selected items, truncated to the first MAX_SELECTION_ITEMS. */
    private static String expectedTruncated(int count, java.util.function.IntFunction<String> name) {
        StringBuilder sb = new StringBuilder("{\"selectedCount\":" + count + ",\"selected\":[");
        for (int i = 0; i < SwingGetSelectionTool.MAX_SELECTION_ITEMS; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"index\":").append(i).append(",\"name\":\"").append(name.apply(i)).append("\"}");
        }
        return sb.append("],\"truncated\":true}").toString();
    }

    @Test
    void noTruncation_whenSelectionWithinMax() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(new int[]{0, 1, 2});
        snapshot(list);

        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":3,\"selected\":["
                + "{\"index\":0,\"name\":\"A\"},{\"index\":1,\"name\":\"B\"},{\"index\":2,\"name\":\"C\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Index identity verification
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_indexMatchesAddAccessibleSelection() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(2);
        snapshot(list);

        AccessibleContext ac = list.getAccessibleContext();
        assertTrue(ac.getAccessibleSelection().isAccessibleChildSelected(2));

        ac.getAccessibleSelection().clearAccessibleSelection();
        ac.getAccessibleSelection().addAccessibleSelection(2);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Gamma\"}]}", json);
    }

    @Test
    void jComboBox_indexMatchesAddAccessibleSelection() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(1);
        snapshot(combo);

        AccessibleContext ac = combo.getAccessibleContext();
        ac.getAccessibleSelection().addAccessibleSelection(0);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"Red\"}]}", json);
    }

    @Test
    void jTabbedPane_indexMatchesAddAccessibleSelection() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab0", new JPanel());
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.setSelectedIndex(0);
        snapshot(tp);

        tp.getAccessibleContext().getAccessibleSelection().addAccessibleSelection(2);
        String json = getSelection(context.getRefOf(tp));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Tab2\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectedIndex(0);
        snapshot(list);
        String json = getSelection(context.getRefOf(list));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"A\"}]}", json);
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("First", new JPanel());
        tp.addTab("Second", new JPanel());
        snapshot(tp);
        String json = getSelection(context.getRefOf(tp));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"First\"}]}", json);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"X", "Y"});
        combo.setSelectedIndex(0);
        snapshot(combo);
        String json = getSelection(context.getRefOf(combo));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"X\"}]}", json);
    }

    @Test
    void componentMatrix_JTable() throws Exception {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"A", "1"}, {"B", "2"}}, new Object[]{"Col1", "Col2"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        snapshot(table);
        String json = getSelection(context.getRefOf(table));
        assertEquals("{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"A | 1\"}]}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calls the tool under a known ref, so a component the snapshot gives no ref is refused by
     * the tool rather than skipped.
     */
    private void assertGetSelectionNotSupported(Component component) throws Exception {
        context.putRef(99, (Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_get_selection. Call swing_snapshot or swing_get_cells to verify the list of actions.",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertGetSelectionNotSupported(new JButton("OK"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertGetSelectionNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertGetSelectionNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertGetSelectionNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertGetSelectionNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertGetSelectionNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertGetSelectionNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertGetSelectionNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertGetSelectionNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertGetSelectionNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertGetSelectionNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertGetSelectionNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertGetSelectionNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertGetSelectionNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        assertGetSelectionNotSupported(menu);
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(item);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton btn = new JButton("Tool");
        tb.add(btn);
        snapshot(tb);
        int ref = context.getRefOf(btn);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getSelection(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_selection"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        root.add(new DefaultMutableTreeNode("A"));
        // Its selection is suppressed (D_no_jtree_selection).
        assertGetSelectionNotSupported(new JTree(root));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertGetSelectionNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertGetSelectionNotSupported(iframe);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetSelectionViaMcpClient() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_get_selection", Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "get_selection should succeed");
        assertFalse(result.getContent().isEmpty(), "Result should have content");
        String json = result.getContent().get(0).getText();
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}",
                json);
    }
}
