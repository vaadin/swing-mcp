package testapp;

import com.vaadin.swingmcp.mcp.MCPServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testapp.loginapp.LoginApp;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LoginAppMcpTest {

    private MCPServer mcpServer;
    private McpSyncClient mcpClient;

    @BeforeAll
    static void assertNotHeadless() {
        assertEquals("false", System.getProperty("java.awt.headless"), "Test requires a display");
        new JFrame();
    }

    @BeforeEach
    void setUp() throws Exception {
        mcpServer = new MCPServer();
        mcpServer.start();

        Duration timeout = Duration.ofSeconds(10);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(mcpServer.getUrl())
                .openConnectionOnStartup(false)
                .build();
        mcpClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        mcpClient.initialize();

        SwingUtilities.invokeLater(() -> new AppLauncher(List.of(new LoginApp())).show());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mcpClient != null) mcpClient.close();
        if (mcpServer != null) mcpServer.stop();
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    @Test
    void loginAndQuitViaMcp() throws Exception {
        // "Select Application" dialog — Login App is pre-selected, just click Run
        waitForWindow("Select Application", 5_000);
        int runRef = findRef(snapshot(), "push_button\\) \"Run\" \\[ref=(\\d+)");
        call("swing_click", Map.of("ref", runRef));

        // Login dialog appears
        waitForWindow("Login", 5_000);

        String snap = snapshot();
        int usernameRef = findRef(snap, "\\(text\\) \\[ref=(\\d+)");
        call("swing_set_text", Map.of("ref", usernameRef, "text", "admin"));

        snap = snapshot();
        int passwordRef = findRef(snap, "password_text\\) \\[ref=(\\d+)");
        call("swing_set_text", Map.of("ref", passwordRef, "text", "admin"));

        snap = snapshot();
        int loginRef = findRef(snap, "push_button\\) \"Login\" \\[ref=(\\d+)");
        call("swing_click", Map.of("ref", loginRef));

        // Main window appears
        waitForWindow("Login App", 5_000);

        snap = snapshot();
        int quitRef = findRef(snap, "menu_item\\) \"Quit\" \\[ref=(\\d+)");
        call("swing_click", Map.of("ref", quitRef));

        waitForWindowToClose("Login App", 5_000);
    }

    // --- Helpers ---

    private String snapshot() {
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_snapshot", Map.of()));
        assertNotEquals(Boolean.TRUE, result.isError(), "swing_snapshot failed");
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private void call(String tool, Map<String, Object> args) {
        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest(tool, args));
        assertNotEquals(Boolean.TRUE, result.isError(),
                tool + " returned an error: " + result.content());
    }

    private static int findRef(String snapshot, String capturePattern) {
        Matcher m = Pattern.compile(capturePattern).matcher(snapshot);
        assertTrue(m.find(),
                "Component not found. Pattern: " + capturePattern + "\nSnapshot:\n" + snapshot);
        return Integer.parseInt(m.group(1));
    }

    private static void waitForWindow(String title, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (!w.isVisible()) continue;
                String t = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : null;
                if (title.equals(t)) return;
            }
            Thread.sleep(50);
        }
        fail("Window '" + title + "' did not appear within " + timeoutMs + "ms");
    }

    private static void waitForWindowToClose(String title, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean visible = false;
            for (Window w : Window.getWindows()) {
                if (!w.isVisible()) continue;
                String t = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : null;
                if (title.equals(t)) {
                    visible = true;
                    break;
                }
            }
            if (!visible) return;
            Thread.sleep(50);
        }
        fail("Window '" + title + "' did not close within " + timeoutMs + "ms");
    }
}
