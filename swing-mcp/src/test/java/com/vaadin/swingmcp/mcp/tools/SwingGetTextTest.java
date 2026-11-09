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
import com.github.mvysny.tinymcpserver.Parameters;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetTextTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetTextTool getTextTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getTextTool = new SwingGetTextTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private String getText(int ref) throws Exception {
        MCPProtocol.Content result = getTextTool.execute(new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readJTextFieldReturnsContent() throws Exception {
        JTextField field = new JTextField("hello world");
        snapshot(field);
        assertEquals("hello world", getText(context.getRefOf(field)));
    }

    @Test
    void readJTextAreaReturnsFullContentWithNewlines() throws Exception {
        JTextArea area = new JTextArea("line1\nline2\nline3");
        snapshot(area);
        assertEquals("line1\nline2\nline3", getText(context.getRefOf(area)));
    }

    @Test
    void readJPasswordFieldReturnsDr011Error() throws Exception {
        JPasswordField field = new JPasswordField("secret");
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    @Test
    void readCustomPasswordRoleComponentReturnsDr011Error() throws Exception {
        // D_password_not_readable gates on the PASSWORD_TEXT role, not the JPasswordField class.
        JTextField field = new JTextField("secret") {
            @Override
            public javax.accessibility.AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.PASSWORD_TEXT;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    @Test
    void readEmptyJTextFieldReturnsEmptyString() throws Exception {
        JTextField field = new JTextField("");
        snapshot(field);
        MCPProtocol.Content result = getTextTool.execute(
                new Parameters(Map.of("ref", context.getRefOf(field))), context);
        assertNotNull(result, "Result should not be null for empty field");
        assertEquals("", result.getText(), "Empty field should return empty string");
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class, () -> getText(999));
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void componentWithoutTextSupportReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(context.getRefOf(slider)));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"),
                "Error should mention get_text, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void refMapPreservedAfterGetText() throws Exception {
        JTextField field = new JTextField("preserved");
        snapshot(field);
        int ref = context.getRefOf(field);

        assertEquals("preserved", getText(ref));
        assertEquals("preserved", getText(ref));
    }

    @Test
    void readDisabledJTextFieldSucceeds() throws Exception {
        JTextField field = new JTextField("disabled content");
        field.setEnabled(false);
        snapshot(field);

        assertEquals("disabled content", getText(context.getRefOf(field)));
    }

    @Test
    void textExceedingMaxLengthIsTruncated() throws Exception {
        String longText = "x".repeat(SwingGetTextTool.MAX_TEXT_LENGTH + 100);
        JTextField field = new JTextField(longText);
        snapshot(field);

        String result = getText(context.getRefOf(field));
        assertNotNull(result);
        assertTrue(result.contains("truncated"), "Result should contain truncation notice");
        assertTrue(result.contains(String.valueOf(SwingGetTextTool.MAX_TEXT_LENGTH + 100)),
                "Result should mention total character count");
        int newlineIdx = result.indexOf('\n');
        assertTrue(newlineIdx > 0);
        assertEquals(SwingGetTextTool.MAX_TEXT_LENGTH, newlineIdx);
    }

    @Test
    void textExactlyAtMaxLengthNotTruncated() throws Exception {
        String exactText = "y".repeat(SwingGetTextTool.MAX_TEXT_LENGTH);
        JTextField field = new JTextField(exactText);
        snapshot(field);

        String result = getText(context.getRefOf(field));
        assertEquals(exactText, result, "Text at exactly MAX_TEXT_LENGTH should not be truncated");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text content");
        snapshot(field);
        assertEquals("text content", getText(context.getRefOf(field)));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        // D_password_not_readable: a dedicated error, not the generic "does not support".
        JPasswordField field = new JPasswordField("pass");
        snapshot(field);
        int ref = context.getRefOf(field);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("multi\nline");
        snapshot(area);
        assertEquals("multi\nline", getText(context.getRefOf(area)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Calls the tool under a known ref, so a component the snapshot gives no ref is refused by
     * the tool rather than skipped.
     */
    private void assertGetTextNotSupported(Component component) throws Exception {
        context.putRef(99, (Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(99));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_get_text. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertGetTextNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertGetTextNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertGetTextNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertGetTextNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertGetTextNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        // AccessibleJSpinner delegates AccessibleText to its editor, so get_text succeeds.
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        String result = getText(context.getRefOf(spinner));
        assertNotNull(result, "JSpinner should return its formatted value as text");
        assertFalse(result.isBlank(), "JSpinner text should not be blank");
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        assertGetTextNotSupported(new JSlider(0, 100, 50));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        assertGetTextNotSupported(new JPanel());
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        assertGetTextNotSupported(new JScrollPane(new JTextArea("content")));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertGetTextNotSupported(tp);
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        assertGetTextNotSupported(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel()));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        // D_label_not_readable.
        assertGetTextNotSupported(new JLabel("Hello"));
    }

    @Test
    void dr015_htmlJLabel_returnsGenericGetTextError() throws Exception {
        // An HTML JLabel does expose AccessibleText (AccessibleHTMLTextSupport); the
        // LABEL-role gate of D_label_not_readable refuses it anyway.
        JLabel html = new JLabel("<html>Hello <b>world</b></html>");
        context.putRef(99, (javax.accessibility.Accessible) html);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(99));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"),
                "D_label_not_readable: HTML JLabel must fail with generic error, got: " + ex.getMessage());
    }

    @Test
    void dr015_jListCell_returnsGenericGetTextError() throws Exception {
        // A list cell has a real ref (from click) and role LABEL, so D_label_not_readable refuses it.
        // Found by role, not identity: getAccessibleChild(i) may return a fresh instance per
        // call, while the ref map holds the one the snapshot walk captured.
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("alpha");
        JList<String> list = new JList<>(model);
        snapshot(list);

        int cellRef = -1;
        for (int candidate = 1; candidate <= 10; candidate++) {
            try {
                javax.accessibility.Accessible a = context.getAccessibleByRef(candidate);
                javax.accessibility.AccessibleContext ctx = a.getAccessibleContext();
                if (ctx != null && javax.accessibility.AccessibleRole.LABEL
                        .equals(ctx.getAccessibleRole())) {
                    cellRef = candidate;
                    break;
                }
            } catch (Exception ignored) {
                // ref not present — keep looking
            }
        }
        assertTrue(cellRef > 0,
                "JList cell with LABEL role should have a ref from its click action");

        final int ref = cellRef;
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"),
                "D_label_not_readable: JList cell must fail with generic error, got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertGetTextNotSupported(pb);
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertGetTextNotSupported(mb);
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        assertGetTextNotSupported(menu);
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
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        snapshot(tb);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertTrue(ex.getMessage().contains("does not support swing_get_text"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        assertGetTextNotSupported(new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root")));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        assertGetTextNotSupported(new JDesktopPane());
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        assertGetTextNotSupported(iframe);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingGetTextViaMcpClient() throws Exception {
        JTextField field = new JTextField("via mcp client");
        mcpServer.setConsideredComponents(List.of(field));

        mcpClient.callTool("swing_snapshot", Map.of());

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_get_text", Map.of("ref", 1));

        assertNotEquals(Boolean.TRUE, result.getIsError(), "get_text should succeed");
        assertFalse(result.getContent().isEmpty(), "Result should have content");
        String text = result.getContent().get(0).getText();
        assertEquals("via mcp client", text);
    }
}
