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
package com.vaadin.swingmcp.mcpscreen.tools;

import com.github.mvysny.tinymcpserver.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetSelectionTool;
import com.vaadin.swingmcp.mcp.tools.SwingSelectAllTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSelectAllScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSelectAllTool selectAllTool;
    private SwingGetSelectionTool getSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        selectAllTool = new SwingSelectAllTool();
        getSelectionTool = new SwingGetSelectionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void selectAll(int ref) throws Exception {
        executeOnEDT(() -> {
            selectAllTool.execute(new Parameters(Map.of("ref", ref)), context);
            return null;
        });
        // drain EDT so fire-and-forget action has run
        SwingUtilities.invokeAndWait(() -> {});
    }

    private String getSelection(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(
                () -> getSelectionTool.execute(new Parameters(Map.of("ref", ref)), context));
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void selectAllJListInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        frame.getContentPane().add(list);

        snapshot(frame);
        selectAll(context.getRefOf(list));
        assertArrayEquals(new int[]{0, 1, 2}, list.getSelectedIndices());
    }

    @Test
    void selectAllJTableInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        selectAll(context.getRefOf(table));
        assertArrayEquals(new int[]{0, 1, 2}, table.getSelectedRows());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void selectAllJListInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        iframe.getContentPane().add(list);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        selectAll(context.getRefOf(list));
        assertArrayEquals(new int[]{0, 1, 2}, list.getSelectedIndices());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void selectAllJListInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JList<String> list = new JList<>(new String[]{"X", "Y", "Z"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dialog.getContentPane().add(list);

        snapshot(dialog);
        selectAll(context.getRefOf(list));
        assertArrayEquals(new int[]{0, 1, 2}, list.getSelectedIndices());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Round-trip (JTable — requires screen for accessible selection readback)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void roundTrip_jTable() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}, {"Carol", "35"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        frame.getContentPane().add(table);

        snapshot(frame);
        selectAll(context.getRefOf(table));

        snapshot(frame);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":3,\"selected\":["
                        + "{\"index\":0,\"name\":\"Alice | 30\"},"
                        + "{\"index\":1,\"name\":\"Bob | 25\"},"
                        + "{\"index\":2,\"name\":\"Carol | 35\"}"
                        + "]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — top-level windows (screen required)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        context.putRef(99, frame);
        assertThrows(MCPErrorResponseException.class, () ->
                executeOnEDT(() -> {
                    selectAllTool.execute(new Parameters(Map.of("ref", 99)), context);
                    return null;
                }));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        snapshot(dialog);
        context.putRef(99, dialog);
        assertThrows(MCPErrorResponseException.class, () ->
                executeOnEDT(() -> {
                    selectAllTool.execute(new Parameters(Map.of("ref", 99)), context);
                    return null;
                }));
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"});
        JDialog dialog = new JDialog();
        dialog.getContentPane().add(optionPane);
        snapshot(dialog);
        context.putRef(99, optionPane);
        assertThrows(MCPErrorResponseException.class, () ->
                executeOnEDT(() -> {
                    selectAllTool.execute(new Parameters(Map.of("ref", 99)), context);
                    return null;
                }));
    }
}
