package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#getItemCount}.
 */
class SwingUtilsGetItemCountTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getItemCount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_returnsItemCount() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertEquals(3, SwingUtils.getItemCount(list));
    }

    @Test
    void jList_empty_returnsZero() {
        JList<String> list = new JList<>();
        assertEquals(0, SwingUtils.getItemCount(list));
    }

    @Test
    void jComboBox_returnsItemCount() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        assertEquals(3, SwingUtils.getItemCount(combo));
    }

    @Test
    void jComboBox_empty_returnsZero() {
        JComboBox<String> combo = new JComboBox<>();
        assertEquals(0, SwingUtils.getItemCount(combo));
    }

    @Test
    void jTabbedPane_returnsTabCount() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.addTab("Tab3", new JPanel());
        assertEquals(3, SwingUtils.getItemCount(tp));
    }

    @Test
    void jTabbedPane_empty_returnsZero() {
        JTabbedPane tp = new JTabbedPane();
        assertEquals(0, SwingUtils.getItemCount(tp));
    }

    @Test
    void jTable_returnsRowCount() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a", "1"}, {"b", "2"}, {"c", "3"}},
                new Object[]{"Name", "Value"}));
        assertEquals(3, SwingUtils.getItemCount(table));
    }

    @Test
    void jTable_empty_returnsZero() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{},
                new Object[]{"Name", "Value"}));
        assertEquals(0, SwingUtils.getItemCount(table));
    }

    @Test
    void jTable_manyRows_returnsCorrectCount() {
        Object[][] data = new Object[200][2];
        for (int i = 0; i < 200; i++) {
            data[i] = new Object[]{"item" + i, i};
        }
        JTable table = new JTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}));
        assertEquals(200, SwingUtils.getItemCount(table));
    }

}
