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
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.github.mvysny.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetSelectionScreenTest extends AbstractScreenTest {

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
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
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
    void readJListSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        frame.getContentPane().add(list);

        snapshot(frame);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}",
                json);
    }

    @Test
    void readJTabbedPaneSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        tp.setSelectedIndex(1);
        frame.getContentPane().add(tp);

        snapshot(frame);
        String json = getSelection(context.getRefOf(tp));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Tab2\"}]}",
                json);
    }

    @Test
    void readJComboBoxSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JComboBox<String> combo = new JComboBox<>(new String[]{"Red", "Green", "Blue"});
        combo.setSelectedIndex(2);
        frame.getContentPane().add(combo);

        snapshot(frame);
        String json = getSelection(context.getRefOf(combo));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Blue\"}]}",
                json);
    }

    @Test
    void readJTableRowSelectionInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Alice", "30"}, {"Bob", "25"}},
                new Object[]{"Name", "Age"});
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        frame.getContentPane().add(table);

        snapshot(frame);
        String json = getSelection(context.getRefOf(table));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":0,\"name\":\"Alice | 30\"}]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJListSelectionInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JList<String> list = new JList<>(new String[]{"X", "Y", "Z"});
        list.setSelectedIndex(2);
        dialog.getContentPane().add(list);

        snapshot(dialog);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":2,\"name\":\"Z\"}]}",
                json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJListSelectionInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        iframe.getContentPane().add(list);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        String json = getSelection(context.getRefOf(list));
        assertEquals(
                "{\"selectedCount\":1,\"selected\":[{\"index\":1,\"name\":\"Beta\"}]}",
                json);
    }
}
