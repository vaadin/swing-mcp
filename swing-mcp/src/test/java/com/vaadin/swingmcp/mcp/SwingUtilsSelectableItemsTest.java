package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleTable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#getSelectableItemsCount} and
 * {@link SwingUtils#buildRowName}.
 */
class SwingUtilsSelectableItemsTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getSelectableItemsCount
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_returnsItemCount() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertEquals(3, SwingUtils.getSelectableItemsCount(list));
    }

    @Test
    void jList_empty_returnsZero() {
        JList<String> list = new JList<>();
        assertEquals(0, SwingUtils.getSelectableItemsCount(list));
    }

    @Test
    void jComboBox_returnsItemCount() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        assertEquals(3, SwingUtils.getSelectableItemsCount(combo));
    }

    @Test
    void jComboBox_empty_returnsZero() {
        JComboBox<String> combo = new JComboBox<>();
        assertEquals(0, SwingUtils.getSelectableItemsCount(combo));
    }

    @Test
    void jTabbedPane_returnsTabCount() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.addTab("Tab3", new JPanel());
        assertEquals(3, SwingUtils.getSelectableItemsCount(tp));
    }

    @Test
    void jTabbedPane_empty_returnsZero() {
        JTabbedPane tp = new JTabbedPane();
        assertEquals(0, SwingUtils.getSelectableItemsCount(tp));
    }

    @Test
    void jTable_returnsRowCount() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"a", "1"}, {"b", "2"}, {"c", "3"}},
                new Object[]{"Name", "Value"}));
        assertEquals(3, SwingUtils.getSelectableItemsCount(table));
    }

    @Test
    void jTable_empty_returnsZero() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{},
                new Object[]{"Name", "Value"}));
        assertEquals(0, SwingUtils.getSelectableItemsCount(table));
    }

    @Test
    void jTable_manyRows_returnsCorrectCount() {
        Object[][] data = new Object[200][2];
        for (int i = 0; i < 200; i++) {
            data[i] = new Object[]{"item" + i, i};
        }
        JTable table = new JTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}));
        assertEquals(200, SwingUtils.getSelectableItemsCount(table));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // buildRowName
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void buildRowName_singleColumn() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice"}}, new Object[]{"Name"}));
        AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
        assertEquals("Alice", SwingUtils.buildRowName(at, 0, 1));
    }

    @Test
    void buildRowName_multipleColumns() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice", "30", "NY"}},
                new Object[]{"Name", "Age", "City"}));
        AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
        assertEquals("Alice, 30, NY", SwingUtils.buildRowName(at, 0, 3));
    }

    @Test
    void buildRowName_nullCellValue() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice", null, "NY"}},
                new Object[]{"Name", "Age", "City"}));
        AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
        assertEquals("Alice, null, NY", SwingUtils.buildRowName(at, 0, 3));
    }

    @Test
    void buildRowName_truncatedToMaxColumns() {
        // Create a table with more than MAX_ROW_NAME_COLUMNS columns
        int cols = SwingUtils.MAX_ROW_NAME_COLUMNS + 5;
        Object[] headers = new Object[cols];
        Object[] row = new Object[cols];
        for (int i = 0; i < cols; i++) {
            headers[i] = "col" + i;
            row[i] = "v" + i;
        }
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{row}, headers));
        AccessibleTable at = table.getAccessibleContext().getAccessibleTable();

        String name = SwingUtils.buildRowName(at, 0, cols);
        // Should only include the first MAX_ROW_NAME_COLUMNS values
        String[] parts = name.split(", ");
        assertEquals(SwingUtils.MAX_ROW_NAME_COLUMNS, parts.length);
        assertEquals("v0", parts[0]);
        assertEquals("v" + (SwingUtils.MAX_ROW_NAME_COLUMNS - 1), parts[parts.length - 1]);
    }

    @Test
    void buildRowName_multipleRows_correctRowSelected() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                new Object[]{"Name", "Age"}));
        AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
        assertEquals("Alice, 30", SwingUtils.buildRowName(at, 0, 2));
        assertEquals("Bob, 25", SwingUtils.buildRowName(at, 1, 2));
    }
}
