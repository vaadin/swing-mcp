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

import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetTextTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetTextScreenTest extends AbstractScreenTest {

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
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private String getText(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(() -> getTextTool.execute(new Parameters(Map.of("ref", ref)), context));
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readTextFieldInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JTextField field = new JTextField("frame content");
        frame.getContentPane().add(field);

        snapshot(frame);
        assertEquals("frame content", getText(context.getRefOf(field)));
    }

    @Test
    void readPasswordFieldInsideJFrameReturnsDr011Error() throws Exception {
        JFrame frame = new JFrame("Login");
        JPasswordField password = new JPasswordField("secret");
        frame.getContentPane().add(password);

        snapshot(frame);
        int ref = context.getRefOf(password);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    @Test
    void readTextAreaInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Editor");
        JTextArea area = new JTextArea("line1\nline2");
        frame.getContentPane().add(new JScrollPane(area));

        snapshot(frame);
        assertEquals("line1\nline2", getText(context.getRefOf(area)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readTextFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Input");
        JTextField field = new JTextField("dialog content");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        assertEquals("dialog content", getText(context.getRefOf(field)));
    }

    @Test
    void readPasswordFieldInsideJDialogReturnsDr011Error() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Login");
        JPasswordField password = new JPasswordField("pass");
        dialog.getContentPane().add(password);

        snapshot(dialog);
        int ref = context.getRefOf(password);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    @Test
    void readEmptyTextFieldInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        JTextField field = new JTextField("");
        dialog.getContentPane().add(field);

        snapshot(dialog);
        MCPProtocol.Content result = executeOnEDT(() -> getTextTool.execute(
                new Parameters(Map.of("ref", context.getRefOf(field))), context));
        assertNotNull(result);
        assertEquals("", result.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readTextFieldInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JTextField field = new JTextField("iframe content");
        iframe.getContentPane().add(field);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        assertEquals("iframe content", getText(context.getRefOf(field)));
    }

    @Test
    void readPasswordFieldInsideJInternalFrameReturnsDr011Error() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JPasswordField password = new JPasswordField("secret");
        iframe.getContentPane().add(password);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        int ref = context.getRefOf(password);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getText(ref));
        assertEquals(
                "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.",
                ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — JFrame and JDialog themselves (not their children)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        snapshot(frame);
        // JFrame itself has no get_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(frame));
    }

    @Test
    void componentMatrix_JDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        snapshot(dialog);
        // JDialog itself has no get_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(dialog));
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
                null, new Object[]{"OK"}, "OK");
        dialog.setContentPane(optionPane);
        snapshot(dialog);
        // JOptionPane itself has no get_text action — it has no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(optionPane));
    }
}
