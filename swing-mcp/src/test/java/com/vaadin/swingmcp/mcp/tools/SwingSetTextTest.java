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

import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetTextTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSetTextTool setTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        setTextTool = new SwingSetTextTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void setText(int ref, String text) throws Exception {
        setTextTool.execute(new Parameters(Map.of("ref", ref, "text", text)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void settingTextOnJTextFieldReplacesContent() throws Exception {
        JTextField field = new JTextField("old");
        snapshot(field);
        setText(context.getRefOf(field), "new value");
        assertEquals("new value", field.getText());
    }

    @Test
    void settingMultiLineTextOnJTextArea() throws Exception {
        JTextArea area = new JTextArea("old");
        snapshot(area);
        setText(context.getRefOf(area), "line1\nline2\nline3");
        assertEquals("line1\nline2\nline3", area.getText());
    }

    @Test
    void settingEmptyStringClearsTextField() throws Exception {
        JTextField field = new JTextField("something");
        snapshot(field);
        setText(context.getRefOf(field), "");
        assertEquals("", field.getText());
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(999, "value"));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void componentWithoutSetTextSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(slider), "value"));
        assertEquals(
                "JSlider does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void disabledFieldReturnsMcpErrorExplainingDisabled() throws Exception {
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(field), "new"));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void nonEditableFieldReturnsMcpErrorSayingNotEditable() throws Exception {
        JTextField field = new JTextField("read only");
        field.setEditable(false);
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(context.getRefOf(field), "new"));
        // Structurally it has editable text; only the EDITABLE state is missing.
        assertEquals("Component is not editable", ex.getMessage());
    }

    @Test
    void refMapClearedAfterSuccessfulSetText() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        int ref = context.getRefOf(field);
        setText(ref, "new");

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "another"));
        assertEquals("Component with ref " + ref + " invalid \u2014 the ref map is empty: no swing_snapshot yet, or a successful mutation cleared it. Call swing_snapshot to rebuild it.", ex.getMessage());
    }

    @Test
    void refMapPreservedAfterFailedSetTextOnDisabledComponent() throws Exception {
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        snapshot(field);
        int ref = context.getRefOf(field);

        assertThrows(MCPErrorResponseException.class, () ->
                setTextTool.execute(new Parameters(Map.of("ref", ref, "text", "new")), context));

        field.setEnabled(true);
        setText(ref, "another");
        assertEquals("another", field.getText());
    }

    @Test
    void successReturnsDR010Echo() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPProtocol.Content result = setTextTool.execute(
                new Parameters(Map.of("ref", ref, "text", "new")), context);
        assertEquals("Dispatched set-text on ref=" + ref + " to \"new\" — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void successWithLongTextTruncatesValueInEcho() throws Exception {
        // D_dispatched_echo: string values are truncated at 15 chars — first 14 + U+2026.
        JTextField field = new JTextField("old");
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPProtocol.Content result = setTextTool.execute(
                new Parameters(Map.of("ref", ref, "text", "this is a pretty long message")), context);
        assertEquals("Dispatched set-text on ref=" + ref + " to \"this is a pret\u2026\" — call swing_snapshot to verify the outcome",
                result.getText());
    }

    @Test
    void successOnJPasswordFieldReturnsValueEcho() throws Exception {
        // The same echo as a text field: the agent supplied the value, so echoing it leaks nothing.
        JPasswordField field = new JPasswordField();
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPProtocol.Content result = setTextTool.execute(
                new Parameters(Map.of("ref", ref, "text", "hunter2")), context);
        assertEquals("Dispatched set-text on ref=" + ref + " to \"hunter2\" — call swing_snapshot to verify the outcome", result.getText());
    }

    @Test
    void settingTextFiresDocumentListener() throws Exception {
        JTextField field = new JTextField("old");
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { listenerFired.set(true); }
            @Override public void removeUpdate(DocumentEvent e) { listenerFired.set(true); }
            @Override public void changedUpdate(DocumentEvent e) { listenerFired.set(true); }
        });

        snapshot(field);
        setText(context.getRefOf(field), "new");
        assertTrue(listenerFired.get(), "DocumentListener should fire after set_text");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("old");
        snapshot(field);
        setText(context.getRefOf(field), "updated");
        assertEquals("updated", field.getText());
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("old");
        snapshot(field);
        setText(context.getRefOf(field), "newpass");
        // setTextContents writes the real password
        assertArrayEquals("newpass".toCharArray(), field.getPassword());
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("old");
        snapshot(area);
        setText(context.getRefOf(area), "multi\nline");
        assertEquals("multi\nline", area.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertSetTextNotSupported(Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(99, "value"));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertSetTextNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertSetTextNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertSetTextNotSupported(rb);
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertSetTextNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertSetTextNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        assertSetTextNotSupported(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1)));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertSetTextNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertSetTextNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertSetTextNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertSetTextNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertSetTextNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        assertSetTextNotSupported(new JLabel("Hello"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        assertSetTextNotSupported(new JProgressBar(0, 100));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        assertSetTextNotSupported(new JMenuBar());
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        assertSetTextNotSupported(menu);
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        assertSetTextNotSupported(item);
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        assertSetTextNotSupported(button);
    }

    @Test
    void componentMatrix_JList() throws Exception {
        assertSetTextNotSupported(new JList<>(new String[]{"A", "B", "C"}));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertSetTextNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertSetTextNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertSetTextNotSupported(iframe);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSetTextViaMcpClient() throws Exception {
        JTextField field = new JTextField("old");
        mcpServer.setConsideredComponents(List.of(field));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_set_text", Map.of("ref", 1, "text", "via mcp"));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.getIsError(), "set_text should succeed");
        assertEquals("via mcp", field.getText());
    }
}
