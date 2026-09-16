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
package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsSelection(javax.accessibility.Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsSelectionTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportSelection() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsSelection(rb));
    }

    @Test
    void jComboBox_supportsSelection() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertTrue(SwingUtils.supportsSelection(combo));
    }

    @Test
    void jToggleButton_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_supportsSelection() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertTrue(SwingUtils.supportsSelection(tp));
    }

    @Test
    void jSplitPane_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — selection is suppressed for menu bar and menu
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportSelection() {
        // Selection is suppressed for menu bars (internal navigation)
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertFalse(SwingUtils.supportsSelection(mb));
    }

    @Test
    void jMenu_doesNotSupportSelection() {
        // Selection is suppressed for menus (internal navigation)
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        assertFalse(SwingUtils.supportsSelection(menu));
    }

    @Test
    void jMenuItem_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JToolBar()));
    }

    @Test
    void jList_supportsSelection() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertTrue(SwingUtils.supportsSelection(list));
    }

    @Test
    void jTree_doesNotSupportSelection() {
        // JTree tree-level AccessibleSelection is non-functional
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsSelection(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTable — row-selection gate
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTable_rowSelectionMode_supportsSelection() {
        // Default: rowSelectionAllowed=true, columnSelectionAllowed=false
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}}, new Object[]{"col"}));
        assertTrue(SwingUtils.supportsSelection(table));
    }

    @Test
    void jTable_columnSelectionMode_doesNotSupportSelection() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}}, new Object[]{"col"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(true);
        assertFalse(SwingUtils.supportsSelection(table));
    }

    @Test
    void jTable_cellSelectionMode_doesNotSupportSelection() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}}, new Object[]{"col"}));
        table.setCellSelectionEnabled(true);
        assertFalse(SwingUtils.supportsSelection(table));
    }

    @Test
    void jTable_noSelectionMode_doesNotSupportSelection() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a"}}, new Object[]{"col"}));
        table.setRowSelectionAllowed(false);
        table.setColumnSelectionAllowed(false);
        assertFalse(SwingUtils.supportsSelection(table));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // isMultiSelectable
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_defaultSelectionMode_isMultiSelectable() {
        // Default selection mode is MULTIPLE_INTERVAL_SELECTION
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertTrue(SwingUtils.isMultiSelectable(list));
    }

    @Test
    void jList_singleSelectionMode_isNotMultiSelectable() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertFalse(SwingUtils.isMultiSelectable(list));
    }

    @Test
    void jList_singleIntervalMode_isMultiSelectable() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        assertTrue(SwingUtils.isMultiSelectable(list));
    }

    @Test
    void jTabbedPane_isNotMultiSelectable() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertFalse(SwingUtils.isMultiSelectable(tp));
    }

    @Test
    void jComboBox_isNotMultiSelectable() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertFalse(SwingUtils.isMultiSelectable(combo));
    }

    @Test
    void jTable_defaultMode_isMultiSelectable() {
        // Default JTable has row selection allowed, multiple interval selection
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        assertTrue(SwingUtils.isMultiSelectable(table));
    }

    @Test
    void jTable_singleSelectionMode_isNotMultiSelectable() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertFalse(SwingUtils.isMultiSelectable(table));
    }

    @Test
    void jTable_singleIntervalMode_isMultiSelectable() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        assertTrue(SwingUtils.isMultiSelectable(table));
    }

    @Test
    void jButton_isNotMultiSelectable() {
        assertFalse(SwingUtils.isMultiSelectable(new JButton("OK")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsSingleSelection / supportsMultiSelection
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_defaultMode_supportsMultiSelection() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertTrue(SwingUtils.supportsMultiSelection(list));
        assertFalse(SwingUtils.supportsSingleSelection(list));
    }

    @Test
    void jList_singleMode_supportsSingleSelection() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertTrue(SwingUtils.supportsSingleSelection(list));
        assertFalse(SwingUtils.supportsMultiSelection(list));
    }

    @Test
    void jComboBox_supportsSingleSelection() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertTrue(SwingUtils.supportsSingleSelection(combo));
        assertFalse(SwingUtils.supportsMultiSelection(combo));
    }

    @Test
    void jTabbedPane_supportsSingleSelection() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertTrue(SwingUtils.supportsSingleSelection(tp));
        assertFalse(SwingUtils.supportsMultiSelection(tp));
    }

    @Test
    void jTable_defaultMode_supportsMultiSelection() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        assertTrue(SwingUtils.supportsMultiSelection(table));
        assertFalse(SwingUtils.supportsSingleSelection(table));
    }

    @Test
    void jTable_singleMode_supportsSingleSelection() {
        JTable table = new JTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertTrue(SwingUtils.supportsSingleSelection(table));
        assertFalse(SwingUtils.supportsMultiSelection(table));
    }

    @Test
    void jButton_supportsNeitherSingleNorMultiSelection() {
        JButton button = new JButton("OK");
        assertFalse(SwingUtils.supportsSingleSelection(button));
        assertFalse(SwingUtils.supportsMultiSelection(button));
    }

    @Test
    void jMenuBar_supportsNeitherSingleNorMultiSelection() {
        // Suppressed from selection entirely
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertFalse(SwingUtils.supportsSingleSelection(mb));
        assertFalse(SwingUtils.supportsMultiSelection(mb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledComboBox_stillReportsSelectionSupport() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setEnabled(false);
        assertTrue(SwingUtils.supportsSelection(combo));
    }

    @Test
    void disabledList_stillReportsSelectionSupport() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        assertTrue(SwingUtils.supportsSelection(list));
    }
}
