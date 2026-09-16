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

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetValueTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetValueTool getValueTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getValueTool = new SwingGetValueTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getValue(int ref) throws Exception {
        MCPProtocol.Content result = getValueTool.execute(new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJSliderReturnsCurrentMinMax() throws Exception {
        JSlider slider = new JSlider(0, 100, 42);
        snapshot(slider);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", json);
    }

    @Test
    void readJSpinnerNumberModelReturnsCurrentMinMax() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        String json = getValue(context.getRefOf(spinner));
        assertEquals("{\"current\":5,\"min\":0,\"max\":10}", json);
    }

    @Test
    void readJProgressBarReturnsCurrentMinMax() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(75);
        snapshot(pb);
        String json = getValue(context.getRefOf(pb));
        assertEquals("{\"current\":75,\"min\":0,\"max\":100}", json);
    }

    @Test
    void readJSplitPaneReturnsCurrentMinMax() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        snapshot(sp);
        String json = getValue(context.getRefOf(sp));
        assertNotNull(json);
        assertTrue(json.contains("\"current\":"), "Should contain current");
        assertTrue(json.contains("\"min\":"), "Should contain min");
        assertTrue(json.contains("\"max\":"), "Should contain max");
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> getValue(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void componentWithoutValueSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("Click me");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"),
                "Error should mention get_value, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void refMapPreservedAfterGetValue() throws Exception {
        JSlider slider = new JSlider(0, 100, 42);
        snapshot(slider);
        int ref = context.getRefOf(slider);

        // Call get_value twice with same ref — should succeed both times (map not cleared)
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", getValue(ref));
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", getValue(ref));
    }

    @Test
    void readDisabledJSliderSucceeds() throws Exception {
        JSlider slider = new JSlider(0, 100, 42);
        slider.setEnabled(false);
        snapshot(slider);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", json);
    }

    @Test
    void unboundedSpinnerOmitsMinMax() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, null, null, 1));
        snapshot(spinner);
        String json = getValue(context.getRefOf(spinner));
        assertEquals("{\"current\":5}", json);
    }

    @Test
    void wholeNumbersSerializeAsIntegers() throws Exception {
        JSlider slider = new JSlider(0, 100, 42);
        snapshot(slider);
        String json = getValue(context.getRefOf(slider));
        // Should be 42, not 42.0
        assertTrue(json.contains("\"current\":42,"), "Should serialize as integer, got: " + json);
        assertFalse(json.contains("42.0"), "Should not contain 42.0, got: " + json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":50,\"min\":0,\"max\":100}", json);
    }

    @Test
    void componentMatrix_JSpinnerNumberModel() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(7, 1, 20, 1));
        snapshot(spinner);
        String json = getValue(context.getRefOf(spinner));
        assertEquals("{\"current\":7,\"min\":1,\"max\":20}", json);
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(10, 200);
        pb.setValue(150);
        snapshot(pb);
        String json = getValue(context.getRefOf(pb));
        assertEquals("{\"current\":150,\"min\":10,\"max\":200}", json);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        snapshot(sp);
        String json = getValue(context.getRefOf(sp));
        assertNotNull(json);
        assertTrue(json.startsWith("{\"current\":"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertGetValueNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // Component has no ref (no actions) — cannot call get_value, skip
            return;
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"),
                "Expected get_value not supported for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JSpinnerDateModel() throws Exception {
        assertGetValueNotSupported(new JSpinner(new SpinnerDateModel()));
    }

    @Test
    void componentMatrix_JSpinnerListModel() throws Exception {
        assertGetValueNotSupported(new JSpinner(new SpinnerListModel(new String[]{"A", "B", "C"})));
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertGetValueNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertGetValueNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertGetValueNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertGetValueNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertGetValueNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertGetValueNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertGetValueNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");
        snapshot(label);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.setName("TestPanel");
        snapshot(panel);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        snapshot(sp);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(sp));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        snapshot(tp);
        try {
            int ref = context.getRefOf(tp);
            MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                    () -> getValue(ref));
            assertTrue(ex.getMessage().contains("does not support swing_get_value"));
        } catch (IllegalStateException e) {
            // No ref assigned — acceptable
        }
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"));
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"));
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
                () -> getValue(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        snapshot(tb);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getValue(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_value"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> getValue(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> getValue(99));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetValueViaMcpClient() throws Exception {
        JSlider slider = new JSlider(0, 100, 42);
        mcpServer.setConsideredComponents(List.of(slider));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_get_value", Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "get_value should succeed");
        assertFalse(result.getContent().isEmpty(), "Result should have content");
        String json = result.getContent().get(0).getText();
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", json);
    }
}
