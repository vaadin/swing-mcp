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
import com.vaadin.swingmcp.mcp.tools.SwingSetTextTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetTextScreenTest extends AbstractScreenTest {

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
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void setText(int ref, String text) throws Exception {
        executeOnEDT(() -> setTextTool.execute(new Parameters(Map.of("ref", ref, "text", text)), context));
        context.clearRefMap();
        executeOnEDT(() -> null); // drain EDT so fire-and-forget action has run
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextOnJTextFieldInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("old");
        frame.getContentPane().add(field);

        snapshot(frame);
        setText(context.getRefOf(field), "new value");
        assertEquals("new value", field.getText());
    }

    @Test
    void setTextOnJPasswordFieldInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Login");
        JPasswordField password = new JPasswordField("old");
        frame.getContentPane().add(password);

        snapshot(frame);
        setText(context.getRefOf(password), "newpass");
        assertArrayEquals("newpass".toCharArray(), password.getPassword());
    }

    @Test
    void setTextOnJTextAreaInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Editor");
        JTextArea area = new JTextArea("old");
        frame.getContentPane().add(new JScrollPane(area));

        snapshot(frame);
        setText(context.getRefOf(area), "line1\nline2");
        assertEquals("line1\nline2", area.getText());
    }

    @Test
    void setTextOnDisabledFieldInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        frame.getContentPane().add(field);

        snapshot(frame);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "new"));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void setTextOnNonEditableFieldInsideJFrameFails() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("read only");
        field.setEditable(false);
        frame.getContentPane().add(field);

        snapshot(frame);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "new"));
        assertEquals("Component is not editable", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextOnJTextFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Input");
        JTextField field = new JTextField("old");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        setText(context.getRefOf(field), "dialog value");
        assertEquals("dialog value", field.getText());
    }

    @Test
    void setTextOnJPasswordFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Login");
        JPasswordField password = new JPasswordField("old");
        dialog.getContentPane().add(password);

        snapshot(dialog);
        setText(context.getRefOf(password), "secret");
        assertArrayEquals("secret".toCharArray(), password.getPassword());
    }

    @Test
    void setEmptyStringClearsFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        JTextField field = new JTextField("something");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        setText(context.getRefOf(field), "");
        assertEquals("", field.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextOnJTextFieldInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JTextField field = new JTextField("old");
        iframe.getContentPane().add(field);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        setText(context.getRefOf(field), "new value");
        assertEquals("new value", field.getText());
    }

    @Test
    void setTextOnDisabledFieldInsideJInternalFrameFails() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        iframe.getContentPane().add(field);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        int ref = context.getRefOf(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(ref, "new"));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    /** A component with no ref is registered under ref 99, so every row reaches the refusal itself. */
    private void assertSetTextNotSupported(java.awt.Component component) throws Exception {
        context.putRef(99, (javax.accessibility.Accessible) component);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setText(99, "new"));
        assertEquals(component.getClass().getSimpleName()
                        + " does not support swing_set_text. Call swing_snapshot or swing_get_cells to verify the list of actions",
                ex.getMessage());
    }

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        assertSetTextNotSupported(frame);
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        assertSetTextNotSupported(dialog);
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        assertSetTextNotSupported(optionPane);
    }
}
