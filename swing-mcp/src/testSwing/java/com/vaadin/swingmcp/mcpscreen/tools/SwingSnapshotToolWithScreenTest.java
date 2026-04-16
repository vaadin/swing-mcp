package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSnapshotToolWithScreenTest extends AbstractScreenTest {

    // ── Instance-level setup for direct tool invocation ───────────────────────

    private SwingSnapshotTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        tool = new SwingSnapshotTool();
        context = new SwingToolContext();
    }

    /**
     * Runs a snapshot with the given components as roots and returns the text output.
     */
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
        // A JFrame wraps its content in JRootPane → JLayeredPane → content pane.
        // All of these framework roles must be pruned transparently, leaving only the
        // actual content as direct children of the frame node.
        JFrame frame = new JFrame();
        frame.getContentPane().add(new JButton("Action"));

        assertEquals(
                "- JFrame (frame)\n"
                + "  - JButton (push_button) \"Action\" [ref=1] actions: click",
                snapshot(frame));
    }

    @Test
    void menuBarSetViaSetJMenuBarAppearsInSnapshot() throws Exception {
        // JMenuBar attached via JFrame.setJMenuBar() (rather than added to a JPanel)
        // must appear as a direct child of the frame after framework pruning.
        JFrame frame = new JFrame();
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        menu.add(new JMenuItem("Save"));
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        // DR-012: JMenu has no click action and no ref; only items are clickable.
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
        // DR-012 / HE-5 end-to-end: open a JMenu's popup via the real Swing
        // mechanics (doClick on a visible frame) and verify the snapshot
        // contains each JMenuItem exactly once — no duplicate sibling
        // JPopupMenu node. Reproduces the original feedback report verbatim:
        // before HE-5, "Quit" appeared twice (under JMenu and under a sibling
        // JPopupMenu added to the layered pane).
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

            // Open the popup the way a real user would.
            executeOnEDT(() -> { file.doClick(); return null; });
            executeOnEDT(() -> null); // drain EDT
            assertTrue(file.isPopupMenuVisible(), "Popup should be open for the test");

            String output = snapshot(frame);
            int quitCount = output.split("\"Quit\"", -1).length - 1;
            assertEquals(1, quitCount,
                    "Quit must appear exactly once after HE-5 prune. Snapshot:\n" + output);
            assertFalse(output.contains("JPopupMenu (popup_menu)"),
                    "JMenu's own JPopupMenu must be pruned. Snapshot:\n" + output);
            // JMenu still carries [selected, checked] state — signals the popup is open.
            assertTrue(output.contains("[selected, checked]"),
                    "JMenu must retain [selected, checked] state when popup is open. Snapshot:\n" + output);
        } finally {
            executeOnEDT(() -> { frame.dispose(); return null; });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog tests
    // ══════════════════════════════════════════════════════════════════════════

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
        // Real-world scenario: login form in a modal JDialog.
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
    void swingSnapshotViaMcpClientWithJFrame() {
        JFrame frame = new JFrame();
        frame.getContentPane().add(new JButton("MCP"));
        mcpServer.setConsideredComponents(List.of(frame));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        assertNotNull(result, "result must not be null");
        assertNotEquals(Boolean.TRUE, result.isError(), "result must not be an error");
        assertFalse(result.content().isEmpty(), "content must not be empty");

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertEquals(
                "- JFrame (frame)\n"
                + "  - JButton (push_button) \"MCP\" [ref=1] actions: click",
                textContent.text());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // BR-14 / DR-016 — modal-stack header
    //
    // Modal dialogs that would normally block setVisible() are never actually
    // shown in these tests — the header logic reads Dialog.isModal() and walks
    // Dialog.getOwner(), neither of which requires the root dialog to be mapped.
    // Only ancestors in the owner chain need isVisible() == true to be included,
    // so the test frame (and any visible middle dialog) is shown for real.
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
        // Three-level owner chain: inner modal (not shown, just the rendered
        // root) → middle dialog (shown so the chain-walk includes it) → frame
        // (shown). The middle is non-modal purely because setVisible(true) on
        // a modal blocks the calling thread. Per BR-14 the chain walk does not
        // check ancestor modality, only visibility, so this exercises the same
        // rendering path as a true "modal-over-modal-over-frame" scenario.
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
        // JOptionPane.showMessageDialog(null, ...) shape: a modal dialog
        // owned by Swing's shared hidden frame. SwingUtils.isVisible filters
        // it out, leaving the chain at length 1 → no header.
        JDialog dialog = new JDialog((Frame) null, "Alert", true);
        String output = snapshot(dialog);
        assertFalse(output.startsWith("[modal stack"),
                "No header expected for modal with invisible owner chain. Got:\n" + output);
    }

    @Test
    void dr016_jFrameRoot_emitsNoHeader() throws Exception {
        // Frames have no Dialog.getOwner() semantics for this feature;
        // buildModalStackHeader short-circuits on the !(root instanceof Dialog) check.
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
        // Non-modal dialogs fail the isModal() check even with a visible owner —
        // DR-016 is modal-scope by design.
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

    /** Local subclass for BR-14 Case-B / BR-11 strip-rule verification. */
    private static class LoginDialog extends JDialog {
        LoginDialog(Window owner, String title) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
        }
    }

    @Test
    void dr016_customSubclassModal_usesConcreteSimpleName() throws Exception {
        // Concrete class is preferred — the entry reads "LoginDialog", not
        // "JDialog". BR-11 strip rules are shared via resolveDisplayClass.
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
        // BR-13 shared sanitiser collapses \n to a single space so the
        // header stays on one line.
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
        // BR-13 escapes embedded " as \" so the quoted slot stays parseable.
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
        // Blank / null accessible name → chain entry renders as bare class
        // name with no empty "" slot.
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
        // Under filter_substring the modal-stack header is dropped — the
        // filter produces its own [filter active: ...] notice at line 1.
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
        // Two independent roots — a modal over a frame, and an unrelated
        // JFrame. Only the modal root gets a header; the frame doesn't; the
        // existing --- separator stays intact; refs remain globally sequenced.
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
    // SC-8 / DR-019 — iconified Frame children suppressed
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

    @Test
    void sc8_iconifiedJFrame_childrenSuppressedWithPlaceholder() throws Exception {
        JFrame frame = new JFrame("App");
        frame.getContentPane().add(new JButton("OK"));
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            // Iconify
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
        JFrame frame = new JFrame("App");
        JButton button = new JButton("OK");
        frame.getContentPane().add(button);
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            // Take snapshot while normal — button gets a ref
            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1] actions: close, iconify\n"
                    + "  - JButton (push_button) \"OK\" [ref=2] actions: click",
                    snapshot(frame));
            assertEquals(2, context.getRefOf(button));

            // Iconify
            executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            // Take snapshot while iconified — button ref is not assigned
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
        JFrame frame = new JFrame("App");
        frame.getContentPane().add(new JButton("OK"));
        try {
            executeOnEDT(() -> { frame.setSize(300, 200); frame.setVisible(true); return null; });

            // Iconify
            executeOnEDT(() -> { frame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(frame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"App\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]",
                    snapshot(frame));

            // Restore
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
        JFrame iconifiedFrame = new JFrame("Minimized");
        iconifiedFrame.getContentPane().add(new JButton("Hidden"));
        JFrame normalFrame = new JFrame("Active");
        normalFrame.getContentPane().add(new JButton("Visible"));
        try {
            executeOnEDT(() -> {
                iconifiedFrame.setSize(300, 200);
                iconifiedFrame.setVisible(true);
                normalFrame.setSize(300, 200);
                normalFrame.setVisible(true);
                return null;
            });

            // Iconify only the first frame
            executeOnEDT(() -> { iconifiedFrame.setExtendedState(Frame.ICONIFIED); return null; });
            awaitExtendedState(iconifiedFrame, Frame.ICONIFIED, Frame.ICONIFIED, 2000);

            assertEquals(
                    "- JFrame (frame) \"Minimized\" [ref=1, iconified] actions: close, restore\n"
                    + "  - [Contents hidden — window is iconified. Call swing_restore to interact with this window.]\n"
                    + "---\n"
                    + "- JFrame (frame) \"Active\" [ref=2] actions: close, iconify\n"
                    + "  - JButton (push_button) \"Visible\" [ref=3] actions: click",
                    snapshot(iconifiedFrame, normalFrame));

            // Ref assertions: iconified frame node has ref, its children do not
            assertEquals(1, context.getRefOf(iconifiedFrame));
            assertThrows(IllegalStateException.class,
                    () -> context.getRefOf((JButton) iconifiedFrame.getContentPane().getComponent(0)));

            // Normal frame's button has a ref
            assertEquals(3, context.getRefOf((JButton) normalFrame.getContentPane().getComponent(0)));
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

            // Iconify the internal frame and drain the EDT
            executeOnEDT(() -> { iframe.setIcon(true); return null; });
            executeOnEDT(() -> null);

            assertTrue(iframe.isIcon(), "precondition: internal frame is iconified");

            String text = snapshot(host);

            // SC-5: JDesktopIcon replaces the JInternalFrame; SC-8 placeholder must not appear
            assertTrue(text.startsWith("- JFrame (frame) \"Host\""), "host frame present");
            assertTrue(text.contains("JDesktopIcon (desktop_icon) \"Doc\""),
                    "JDesktopIcon appears (SC-5). Got:\n" + text);
            assertFalse(text.contains("[Contents hidden"),
                    "SC-8 placeholder must not appear for JInternalFrame");
        } finally {
            executeOnEDT(() -> { host.dispose(); return null; });
        }
    }
}
