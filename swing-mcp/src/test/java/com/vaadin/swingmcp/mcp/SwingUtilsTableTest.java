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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.accessibility.AccessibleTable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingUtilsTableTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    private static JTable createTable(Object[][] data, Object[] columns) {
        return new JTable(new DefaultTableModel(data, columns));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isTableHeaderVisible
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class IsTableHeaderVisible {

        @Test
        void tableInScrollPane_headerVisible() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Col"});
            new JScrollPane(table); // places table inside scroll pane
            assertTrue(SwingUtils.isTableHeaderVisible(table));
        }

        @Test
        void tableNotInScrollPane_headerNotVisible() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Col"});
            // table added directly to a panel, no JScrollPane
            JPanel panel = new JPanel();
            panel.add(table);
            assertFalse(SwingUtils.isTableHeaderVisible(table));
        }

        @Test
        void tableStandalone_headerNotVisible() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Col"});
            // table not added to any container
            assertFalse(SwingUtils.isTableHeaderVisible(table));
        }

        @Test
        void tableHeaderSetToNull_notVisible() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Col"});
            new JScrollPane(table);
            table.setTableHeader(null);
            assertFalse(SwingUtils.isTableHeaderVisible(table));
        }

        @Test
        void tableHeaderHiddenViaSetVisible_notVisible() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Col"});
            new JScrollPane(table);
            table.getTableHeader().setVisible(false);
            assertFalse(SwingUtils.isTableHeaderVisible(table));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getTableColumnNames
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class GetTableColumnNames {

        @Test
        void singleColumn() {
            JTable table = createTable(
                    new Object[][]{{"a"}}, new Object[]{"Name"});
            assertEquals(List.of("Name"), SwingUtils.getTableColumnNames(table));
        }

        @Test
        void multipleColumns() {
            JTable table = createTable(
                    new Object[][]{{"a", "1", "x"}},
                    new Object[]{"Name", "Age", "City"});
            assertEquals(List.of("Name", "Age", "City"),
                    SwingUtils.getTableColumnNames(table));
        }

        @Test
        void nullColumnHeader_usesDefaultTableModelFallback() {
            // DefaultTableModel replaces a null column name with a letter, so the header is never null.
            JTable table = createTable(
                    new Object[][]{{"a", "b"}},
                    new Object[]{"Name", null});
            List<String> names = SwingUtils.getTableColumnNames(table);
            assertEquals("Name", names.get(0));
            assertNotNull(names.get(1));
        }

        @Test
        void explicitNullHeaderValue_showsNull() {
            JTable table = createTable(
                    new Object[][]{{"a", "b"}},
                    new Object[]{"Name", "Value"});
            table.getColumnModel().getColumn(1).setHeaderValue(null);
            assertEquals(List.of("Name", "null"),
                    SwingUtils.getTableColumnNames(table));
        }

        @Test
        void emptyTable_returnsColumnNames() {
            JTable table = createTable(
                    new Object[][]{},
                    new Object[]{"Name", "Value"});
            assertEquals(List.of("Name", "Value"),
                    SwingUtils.getTableColumnNames(table));
        }

        @Test
        void respectsColumnReordering() {
            JTable table = createTable(
                    new Object[][]{{"a", "b", "c"}},
                    new Object[]{"A", "B", "C"});
            table.getColumnModel().moveColumn(2, 0);
            assertEquals(List.of("C", "A", "B"),
                    SwingUtils.getTableColumnNames(table));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // buildTableRowText
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class BuildTableRowText {

        @Test
        void singleColumn() {
            JTable table = createTable(
                    new Object[][]{{"Alice"}}, new Object[]{"Name"});
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
            assertEquals("Alice", SwingUtils.buildTableRowText(at, 0, 1));
        }

        @Test
        void multipleColumns_pipeSeparated() {
            JTable table = createTable(
                    new Object[][]{{"1", "Acme Corp", "Manufacturing"}},
                    new Object[]{"ID", "Name", "Industry"});
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
            assertEquals("1 | Acme Corp | Manufacturing",
                    SwingUtils.buildTableRowText(at, 0, 3));
        }

        @Test
        void nullCellValue() {
            JTable table = createTable(
                    new Object[][]{{"Alice", null, "NY"}},
                    new Object[]{"Name", "Age", "City"});
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
            assertEquals("Alice | null | NY",
                    SwingUtils.buildTableRowText(at, 0, 3));
        }

        @Test
        void multipleRows_correctRowSelected() {
            JTable table = createTable(
                    new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                    new Object[]{"Name", "Age"});
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
            assertEquals("Alice | 30", SwingUtils.buildTableRowText(at, 0, 2));
            assertEquals("Bob | 25", SwingUtils.buildTableRowText(at, 1, 2));
        }

        @Test
        void truncatedToMaxColumns_withEllipsis() {
            int cols = SwingUtils.MAX_ROW_NAME_COLUMNS + 5;
            Object[] headers = new Object[cols];
            Object[] row = new Object[cols];
            for (int i = 0; i < cols; i++) {
                headers[i] = "col" + i;
                row[i] = "v" + i;
            }
            JTable table = createTable(new Object[][]{row}, headers);
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();

            String text = SwingUtils.buildTableRowText(at, 0, cols);
            assertTrue(text.endsWith("| \u2026"),
                    "Should end with ellipsis when columns exceed limit");
            // MAX_ROW_NAME_COLUMNS values plus the ellipsis are joined by MAX_ROW_NAME_COLUMNS pipes.
            long pipeCount = text.chars().filter(c -> c == '|').count();
            assertEquals(SwingUtils.MAX_ROW_NAME_COLUMNS, pipeCount);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // describeTableCell
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class DescribeTableCell {

        @Test
        void nullCell_returnsNull() {
            assertEquals("null", SwingUtils.describeTableCell(null));
        }

        @Test
        void plainLabelCell() {
            JTable table = createTable(
                    new Object[][]{{"Hello"}}, new Object[]{"Col"});
            AccessibleTable at = table.getAccessibleContext().getAccessibleTable();
            javax.accessibility.Accessible cell = at.getAccessibleAt(0, 0);
            assertEquals("Hello", SwingUtils.describeTableCell(cell));
        }
    }
}
