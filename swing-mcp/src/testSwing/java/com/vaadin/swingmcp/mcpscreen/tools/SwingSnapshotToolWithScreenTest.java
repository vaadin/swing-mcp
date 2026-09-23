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
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSnapshotToolWithScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        tool = new SwingSnapshotTool();
        context = new SwingToolContext(Runnable::run);
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(() -> tool.execute(new Parameters(Map.of()), context));
        return result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrameAppearsAsFrame() throws Exception {
        JFrame frame = new JFrame("My App");
        frame.getContentPane().add(new JButton("OK"));

        assertEquals(
                "- JFrame (frame) \"My App\"\n"
                + "  - JButton (push_button) \"OK\" [ref=1] actions: click",
                snapshot(frame));
    }

    @Test
    void frameworkRolesAreTransparentlyPruned() throws Exception {
        // JRootPane, JLayeredPane and the content pane all sit between the frame and the button.
        JFrame frame = new JFrame();
        frame.getContentPane().add(new JButton("Action"));

        assertEquals(
                "- JFrame (frame)\n"
                + "  - JButton (push_button) \"Action\" [ref=1] actions: click",
                snapshot(frame));
    }

    @Test
    void menuBarSetViaSetJMenuBarAppearsInSnapshot() throws Exception {
        // setJMenuBar puts the bar in the layered pane beside the content pane, not inside it.
        JFrame frame = new JFrame();
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        menu.add(new JMenuItem("Save"));
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        // D_jmenu_not_clickable: JMenu has no click action and no ref; only items are clickable.
        assertEquals(
                "- JFrame (frame)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"File\"\n"
                + "      - JMenuItem (menu_item) \"Open\" [ref=1] actions: click\n"
                + "      - JMenuItem (menu_item) \"Save\" [ref=2] actions: click",
                snapshot(frame));
    }

    @Test
    void openJMenuPopup_doesNotDuplicateItems_HE5() throws Exception {
        // D_jmenu_not_clickable: an open JMenu's JPopupMenu lands in the layered pane as a
        // sibling, and would list every item a second time unless pruned.
        JFrame frame = new JFrame("Login App");
        try {
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.add(new JLabel("The app contents", SwingConstants.CENTER), BorderLayout.CENTER);
            JMenuBar mb = new JMenuBar();
            JMenu file = new JMenu("File");
            JMenuItem quit = new JMenuItem("Quit");
            file.add(quit);
            mb.add(file);
            frame.setJMenuBar(mb);
            frame.setSize(400, 300);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            executeOnEDT(() -> { file.doClick(); return null; });
            executeOnEDT(() -> null); // drain EDT
            assertTrue(file.isPopupMenuVisible(), "Popup should be open for the test");

            String output = snapshot(frame);
            int quitCount = output.split("\"Quit\"", -1).length - 1;
            assertEquals(1, quitCount,
                    "Quit must appear exactly once after the JMenu-popup prune. Snapshot:\n" + output);
            assertFalse(output.contains("JPopupMenu (popup_menu)"),
                    "JMenu's own JPopupMenu must be pruned. Snapshot:\n" + output);
            assertTrue(output.contains("[selected, checked]"),
                    "JMenu must retain [selected, checked] state when popup is open. Snapshot:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jInternalFrameInsideDesktopPaneAppearsInSnapshot() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.getContentPane().add(new JButton("OK"));
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        String text = snapshot(host);
        assertTrue(text.contains("JInternalFrame"), "snapshot should contain JInternalFrame");
        assertTrue(text.contains("\"Doc\""), "snapshot should show internal frame title");
        assertTrue(text.contains("\"OK\""), "snapshot should show button inside internal frame");
    }

    @Test
    void desktopPaneWithMultipleInternalFrames() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);

        JInternalFrame iframe1 = new JInternalFrame("Doc1", true, true);
        iframe1.getContentPane().add(new JButton("A"));
        iframe1.setSize(150, 80);
        iframe1.setVisible(true);
        desktop.add(iframe1);

        JInternalFrame iframe2 = new JInternalFrame("Doc2", true, true);
        iframe2.getContentPane().add(new JButton("B"));
        iframe2.setSize(150, 80);
        iframe2.setVisible(true);
        desktop.add(iframe2);

        String text = snapshot(host);
        assertTrue(text.contains("\"Doc1\""), "snapshot should contain first internal frame");
        assertTrue(text.contains("\"Doc2\""), "snapshot should contain second internal frame");
        assertTrue(text.contains("\"A\""), "snapshot should show button in first iframe");
        assertTrue(text.contains("\"B\""), "snapshot should show button in second iframe");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jDialogAppearsAsDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Confirm");
        JPanel content = new JPanel();
        content.add(new JButton("Yes"));
        content.add(new JButton("No"));
        dialog.getContentPane().add(content);

        assertEquals(
                "- JDialog (dialog) \"Confirm\"\n"
                + "  - JButton (push_button) \"Yes\" [ref=1] actions: click\n"
                + "  - JButton (push_button) \"No\" [ref=2] actions: click",
                snapshot(dialog));
    }

    @Test
    void loginDialogSnapshot() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Login");

        JPanel form = new JPanel();
        JLabel usernameLabel = new JLabel("Username");
        JTextField usernameField = new JTextField(20);
        usernameField.getAccessibleContext().setAccessibleName("Username");

        JLabel passwordLabel = new JLabel("Password");
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.getAccessibleContext().setAccessibleName("Password");

        JCheckBox rememberMe = new JCheckBox("Remember me");
        JButton signInButton = new JButton("Sign In");
        JButton cancelButton = new JButton("Cancel");

        form.add(usernameLabel);
        form.add(usernameField);
        form.add(passwordLabel);
        form.add(passwordField);
        form.add(rememberMe);
        form.add(signInButton);
        form.add(cancelButton);
        dialog.getContentPane().add(form);

        assertEquals(
                "- JDialog (dialog) \"Login\"\n"
                + "  - JLabel (label) \"Username\"\n"
                + "  - JTextField (text) \"Username\" [ref=1] text=\"\" actions: get_text, set_text\n"
                + "  - JLabel (label) \"Password\"\n"
                + "  - JPasswordField (password_text) \"Password\" [ref=2] actions: set_text\n"
                + "  - JCheckBox (check_box) \"Remember me\" [ref=3] actions: click\n"
                + "  - JButton (push_button) \"Sign In\" [ref=4] actions: click\n"
                + "  - JButton (push_button) \"Cancel\" [ref=5] actions: click",
                snapshot(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JOptionPane tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jOptionPaneAppearsAsOptionPane() throws Exception {
        JDialog dialog = new JDialog();
        JOptionPane optionPane = new JOptionPane(
                "Test message",
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[]{"OK"},
                "OK");
        dialog.setContentPane(optionPane);

        assertEquals(
                "- JDialog (dialog)\n"
                + "  - JOptionPane (option_pane)\n"
                + "    - JLabel (label) \"Test message\"\n"
                + "    - JButton (push_button) \"OK\" [ref=1] actions: click",
                snapshot(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Multiple windows as separate roots
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void multipleWindowsAreSeparatedByDividerAndRefsAreGlobal() throws Exception {
        JFrame frame = new JFrame("Main");
        frame.getContentPane().add(new JButton("A"));

        JDialog dialog = new JDialog();
        dialog.setTitle("Popup");
        dialog.getContentPane().add(new JButton("B"));

        assertEquals(
                "- JFrame (frame) \"Main\"\n"
                + "  - JButton (push_button) \"A\" [ref=1] actions: click\n"
                + "---\n"
                + "- JDialog (dialog) \"Popup\"\n"
                + "  - JButton (push_button) \"B\" [ref=2] actions: click",
                snapshot(frame, dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP integration test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSnapshotViaMcpClientWithJFrame() throws Exception {
        JFrame frame = new JFrame();
        frame.getContentPane().add(new JButton("MCP"));
        mcpServer.setConsideredComponents(List.of(frame));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_snapshot", Map.of());

        assertNotNull(result, "result must not be null");
        assertNotEquals(Boolean.TRUE, result.getIsError(), "result must not be an error");
        assertFalse(result.getContent().isEmpty(), "content must not be empty");

        MCPProtocol.Content textContent = result.getContent().get(0);
        assertEquals(
                "- JFrame (frame)\n"
                + "  - JButton (push_button) \"MCP\" [ref=1] actions: click",
                textContent.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D_modal_stack_header — modal-stack header
    //
    // The modal root is never shown: setVisible(true) would block, and the header
    // needs only its isModal() and owner chain. Only the ancestors must be visible
    // to count, so those are shown for real.
    // ══════════════════════════════════════════════════════════════════════════

    /** Shows the given frame via the EDT; caller must dispose in a {@code finally}. */
    private JFrame showFrame(String title, int w, int h) throws Exception {
        JFrame frame = new JFrame(title);
        frame.setSize(w, h);
        executeOnEDT(() -> { frame.setVisible(true); return null; });
        return frame;
    }

    @Test
    void dr016_singleModalOverFrame_emitsTwoEntryHeader() throws Exception {
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "X", true);
            String output = snapshot(dialog);
            assertTrue(output.startsWith(
                    "[modal stack (2, topmost first): JDialog \"X\" / JFrame \"Y\"]\n"),
                    "Expected modal-stack header on first line. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_nestedModalsOverFrame_emitsThreeEntryHeader() throws Exception {
        // The middle dialog is non-modal only so that showing it does not block; the
        // chain walk checks ancestor visibility, not modality, so this is the same path
        // as modal-over-modal-over-frame.
        JFrame frame = showFrame("Z", 300, 200);
        JDialog[] middleHolder = new JDialog[1];
        try {
            executeOnEDT(() -> {
                JDialog middle = new JDialog(frame, "Y", false);
                middle.setSize(200, 150);
                middle.setVisible(true);
                middleHolder[0] = middle;
                return null;
            });
            JDialog inner = new JDialog(middleHolder[0], "X", true);
            String output = snapshot(inner);
            assertTrue(output.startsWith(
                    "[modal stack (3, topmost first): JDialog \"X\" / JDialog \"Y\" / JFrame \"Z\"]\n"),
                    "Expected three-entry header. Got:\n" + output);
        } finally {
            executeOnEDT(() -> {
                if (middleHolder[0] != null) middleHolder[0].dispose();
                frame.dispose();
                return null;
            });
        }
    }

    @Test
    void dr016_modalWithHiddenSharedFrameOwner_emitsNoHeader() throws Exception {
        // The JOptionPane.showMessageDialog(null, ...) shape: the owner is Swing's shared
        // hidden frame, which must not count as a stack entry.
        JDialog dialog = new JDialog((Frame) null, "Alert", true);
        String output = snapshot(dialog);
        assertFalse(output.startsWith("[modal stack"),
                "No header expected for modal with invisible owner chain. Got:\n" + output);
    }

    @Test
    void dr016_jFrameRoot_emitsNoHeader() throws Exception {
        JFrame frame = showFrame("Root", 200, 150);
        try {
            String output = snapshot(frame);
            assertFalse(output.startsWith("[modal stack"),
                    "No header expected for JFrame root. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_nonModalDialog_emitsNoHeader() throws Exception {
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "X", false);
            String output = snapshot(dialog);
            assertFalse(output.startsWith("[modal stack"),
                    "No header expected for non-modal dialog. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    private static class LoginDialog extends JDialog {
        LoginDialog(Window owner, String title) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
        }
    }

    @Test
    void dr016_customSubclassModal_usesConcreteSimpleName() throws Exception {
        // The header resolves its class through the same resolveDisplayClass as the body.
        JFrame frame = showFrame("Y", 300, 200);
        try {
            LoginDialog dialog = new LoginDialog(frame, "Sign In");
            String output = snapshot(dialog);
            assertTrue(output.startsWith(
                    "[modal stack (2, topmost first): LoginDialog \"Sign In\" / JFrame \"Y\"]\n"),
                    "Expected concrete class name in chain entry. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_titleWithNewline_collapsedInHeader() throws Exception {
        // D_quoted_slot_sanitizing covers the header's quoted slots too.
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "Line1\nLine2", true);
            String output = snapshot(dialog);
            assertTrue(output.startsWith(
                    "[modal stack (2, topmost first): JDialog \"Line1 Line2\" / JFrame \"Y\"]\n"),
                    "Expected newline collapsed to space. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_titleWithEmbeddedQuote_escaped() throws Exception {
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "say \"hi\"", true);
            String output = snapshot(dialog);
            assertTrue(output.startsWith(
                    "[modal stack (2, topmost first): JDialog \"say \\\"hi\\\"\" / JFrame \"Y\"]\n"),
                    "Expected embedded quotes escaped as \\\". Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_modalWithBlankTitle_omitsQuotedSlot() throws Exception {
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "", true);
            String output = snapshot(dialog);
            assertTrue(output.startsWith(
                    "[modal stack (2, topmost first): JDialog / JFrame \"Y\"]\n"),
                    "Expected bare class name when title blank. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_headerDroppedFromFilteredOutput() throws Exception {
        JFrame frame = showFrame("Y", 300, 200);
        try {
            JDialog dialog = new JDialog(frame, "X", true);
            dialog.getContentPane().add(new JButton("Save"));

            context.setConsideredComponents(List.of(dialog));
            MCPProtocol.Content result = executeOnEDT(() -> tool.execute(
                    new Parameters(Map.of("filter_substring", "Save")), context));
            String output = result.getText();

            assertTrue(output.startsWith("[filter active:"),
                    "Filtered output should start with filter notice. Got:\n" + output);
            assertFalse(output.contains("[modal stack"),
                    "Filtered output should not contain modal-stack header. Got:\n" + output);
            assertTrue(output.contains("\"Save\""),
                    "Filter should match the button. Got:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void dr016_mixedRoots_headerOnlyOnModalRoot() throws Exception {
        JFrame ownerFrame = showFrame("Y", 300, 200);
        JFrame unrelatedFrame = new JFrame("Unrelated");
        try {
            JDialog dialog = new JDialog(ownerFrame, "X", true);
            dialog.getContentPane().add(new JButton("A"));
            unrelatedFrame.getContentPane().add(new JButton("B"));

            String output = snapshot(dialog, unrelatedFrame);
            assertEquals(
                    "[modal stack (2, topmost first): JDialog \"X\" / JFrame \"Y\"]\n"
                    + "- JDialog (dialog) \"X\" [modal]\n"
                    + "  - JButton (push_button) \"A\" [ref=1] actions: click\n"
                    + "---\n"
                    + "- JFrame (frame) \"Unrelated\"\n"
                    + "  - JButton (push_button) \"B\" [ref=2] actions: click",
                    output);
        } finally {
            executeOnEDT(() -> {
                ownerFrame.dispose();
                unrelatedFrame.dispose();
                return null;
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D_iconified_children_hidden — iconified Frame children suppressed
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Polls {@link Frame#getExtendedState()} until {@code (state & mask) == expected}
     * or {@code timeoutMs} elapses.
     */
    private static void awaitExtendedState(Frame frame, int mask, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if ((frame.getExtendedState() & mask) == expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Creates a JFrame that will never receive WM focus, so the snapshot
     * output is deterministic (no WM-dependent {@code [focused]} state).
     */
    private static JFrame newUnfocusableFrame(String title) {
        JFrame f = new JFrame(title);
        f.setFocusableWindowState(false);
        return f;
    }

    @Test
    void sc8_iconifiedJFrame_childrenSuppressedWithPlaceholder() throws Exception {
        JFrame frame = newUnfocusableFrame("App");
        frame.getContentPane().add(new JButton("OK"));
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                    snapshot(frame));
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void sc8_iconifiedJFrame_childRefsNotAssigned() throws Exception {
        JFrame frame = newUnfocusableFrame("App");
        JButton button = new JButton("OK");
        frame.getContentPane().add(button);
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1] actions: close, iconify\n"
                    + "  - JButton (push_button) \"OK\" [ref=2] actions: click",
                    snapshot(frame));
            assertEquals(2, context.getRefOf(button));

            executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                    snapshot(frame));
            assertThrows(IllegalStateException.class, () -> context.getRefOf(button));
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void sc8_restoredJFrame_childrenReappear() throws Exception {
        JFrame frame = newUnfocusableFrame("App");
        frame.getContentPane().add(new JButton("OK"));
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                    snapshot(frame));

            executeOnEDT(() -> {
                frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
                return null;
            });
            awaitExtendedState(frame, Frame.ICONIFIED, 0, 2000);

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1] actions: close, iconify\n"
                    + "  - JButton (push_button) \"OK\" [ref=2] actions: click",
                    snapshot(frame));
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    void sc8_mixedIconifiedAndNormalFrames_refsOnlyOnNormal() throws Exception {
        JFrame iconifiedFrame = newUnfocusableFrame("Minimized");
        JButton hiddenBtn = new JButton("Hidden");
        iconifiedFrame.getContentPane().add(hiddenBtn);
        JFrame normalFrame = newUnfocusableFrame("Active");
        JButton visibleBtn = new JButton("Visible");
        normalFrame.getContentPane().add(visibleBtn);
        try {
            executeOnEDT(() -> {
                iconifiedFrame.setSize(300, 200);
                iconifiedFrame.setVisible(true);
                normalFrame.setSize(300, 200);
                normalFrame.setVisible(true);
                return null;
            });

            executeOnEDT(() -> { iconifiedFrame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(iconifiedFrame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"Minimized\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]\n"
                    + "---\n"
                    + "- JFrame (frame) \"Active\" [ref=2] actions: close, iconify\n"
                    + "  - JButton (push_button) \"Visible\" [ref=3] actions: click",
                    snapshot(iconifiedFrame, normalFrame));

            assertEquals(1, context.getRefOf(iconifiedFrame));
            assertThrows(IllegalStateException.class, () -> context.getRefOf(hiddenBtn));

            assertEquals(3, context.getRefOf(visibleBtn));
        } finally {
            executeOnEDT(() -> {
                iconifiedFrame.dispose();
                normalFrame.dispose();
                return null;
            });
        }
    }

    private String filteredSnapshot(String filter, Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = executeOnEDT(() -> tool.execute(
                new Parameters(Map.of("filter_substring", filter)), context));
        return result.getText();
    }

    /**
     * A filtered snapshot never shows an iconified frame's children, but always lists the frame
     * and its placeholder: what the filter looks for may be hidden there.
     */
    @Test
    void sc8_filteredSnapshot_iconifiedFrameAlwaysListedChildrenNever() throws Exception {
        JFrame iconifiedFrame = newUnfocusableFrame("Minimized");
        iconifiedFrame.getContentPane().add(new JButton("Hidden"));
        JFrame normalFrame = newUnfocusableFrame("Active");
        normalFrame.getContentPane().add(new JButton("Visible"));
        try {
            executeOnEDT(() -> {
                iconifiedFrame.setSize(300, 200);
                iconifiedFrame.setVisible(true);
                normalFrame.setSize(300, 200);
                normalFrame.setVisible(true);
                return null;
            });
            executeOnEDT(() -> { iconifiedFrame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(iconifiedFrame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            String minimized = "- JFrame (frame) \"Minimized\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]";

            // Matches only a hidden child: the child stays hidden.
            assertEquals(
                    "[filter active: only nodes matching \"Hidden\" and their ancestors/descendants are shown]\n"
                    + minimized,
                    filteredSnapshot("Hidden", iconifiedFrame, normalFrame));

            // Matches the iconified frame's own line.
            assertEquals(
                    "[filter active: only nodes matching \"Minimized\" and their ancestors/descendants are shown]\n"
                    + minimized,
                    filteredSnapshot("Minimized", iconifiedFrame, normalFrame));

            // Matches in the normal frame: the iconified one is listed beside it.
            assertEquals(
                    "[filter active: only nodes matching \"Visible\" and their ancestors/descendants are shown]\n"
                    + minimized + "\n"
                    + "- JFrame (frame) \"Active\" [ref=2] actions: close, iconify\n"
                    + "  - JButton (push_button) \"Visible\" [ref=3] actions: click",
                    filteredSnapshot("Visible", iconifiedFrame, normalFrame));

            // Matches nothing: the iconified frame is still listed, not "No lines matched".
            assertEquals(
                    "[filter active: only nodes matching \"nonexistent\" and their ancestors/descendants are shown]\n"
                    + minimized,
                    filteredSnapshot("nonexistent", iconifiedFrame, normalFrame));
        } finally {
            executeOnEDT(() -> {
                iconifiedFrame.dispose();
                normalFrame.dispose();
                return null;
            });
        }
    }

    @Test
    void sc8_iconifiedJInternalFrame_handledBySC5NotSC8() throws Exception {
        // An iconified JInternalFrame renders as its JDesktopIcon (D_desktop_icon_as_itself),
        // not as the iconified-frame placeholder (D_iconified_children_hidden).
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", false, true, false, true);
        iframe.setSize(150, 80);
        desktop.add(iframe);
        try {
            executeOnEDT(() -> {
                host.setSize(400, 300);
                host.setVisible(true);
                iframe.setVisible(true);
                return null;
            });

            executeOnEDT(() -> { iframe.setIcon(true); return null; });
            executeOnEDT(() -> null);

            assertTrue(iframe.isIcon(), "precondition: internal frame is iconified");

            String text = snapshot(host);

            assertTrue(text.startsWith("- JFrame (frame) \"Host\""), "host frame present");
            assertTrue(text.contains("JDesktopIcon (desktop_icon) \"Doc\""),
                    "JDesktopIcon appears. Got:\n" + text);
            assertFalse(text.contains("[Contents hidden"),
                    "the iconified placeholder must not appear for JInternalFrame");
        } finally {
            executeOnEDT(() -> { host.dispose(); return null; });
        }
    }
}
