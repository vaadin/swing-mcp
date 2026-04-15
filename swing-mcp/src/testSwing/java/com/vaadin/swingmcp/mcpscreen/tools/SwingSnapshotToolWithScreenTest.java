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

        assertEquals(
                "- JFrame (frame)\n"
                + "  - JMenuBar (menu_bar)\n"
                + "    - JMenu (menu) \"File\" [ref=1] actions: click\n"
                + "      - JMenuItem (menu_item) \"Open\" [ref=2] actions: click\n"
                + "      - JMenuItem (menu_item) \"Save\" [ref=3] actions: click",
                snapshot(frame));
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
}
