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
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingClearSelectionTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingClearSelectionTool clearSelectionTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        clearSelectionTool = new SwingClearSelectionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(java.awt.Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void clearSelection(int ref) throws Exception {
        clearSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
    }

    @Test
    void jList_clearSelection_deselectsItem() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        assertEquals(1, list.getSelectedIndex());

        snapshot(list);
        int ref = context.getRefOf(list);
        clearSelection(ref);

        assertEquals(-1, list.getSelectedIndex());
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    void jList_insideJInternalFrame_clearSelection_deselectsItem() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        iframe.getContentPane().add(list);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        JPanel root = new JPanel();
        root.add(desktop);

        snapshot(root);
        int ref = context.getRefOf(list);
        clearSelection(ref);

        assertEquals(-1, list.getSelectedIndex());
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    void successEchoUsesClearSelectionActionName() throws Exception {
        // the wrapper composes the echo from this tool's MCP-exposed name
        // (clear-selection), not from the delegated SwingSetSelectionTool (set-selection).
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(1);
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPProtocol.Content result = clearSelectionTool.execute(
                new Parameters(Map.of("ref", ref)), context);
        assertEquals("Dispatched clear-selection on ref=" + ref + " — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void clearSelectionViaMcpClient() throws Exception {
        JList<String> list = new JList<>(new String[]{"Alpha", "Beta", "Gamma"});
        list.setSelectedIndex(2);
        mcpServer.setConsideredComponents(List.of(list));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_clear_selection", Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "clear_selection should succeed");
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT
        assertEquals(-1, list.getSelectedIndex());
    }
}
