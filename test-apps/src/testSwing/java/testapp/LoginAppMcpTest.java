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
package testapp;

import com.vaadin.swingmcp.mcp.SwingMCP;
import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testapp.loginapp.LoginApp;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LoginAppMcpTest {

    private SwingMCP mcpServer;
    private MCPClient mcpClient;

    @BeforeAll
    static void assertNotHeadless() {
        assertEquals("false", System.getProperty("java.awt.headless"), "Test requires a display");
        new JFrame();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new SwingMCP(0, "/mcp");
        mcpServer.start();

        mcpClient = new TinyMCPClient(URI.create(mcpServer.getUrl()));
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

    private String snapshot() throws Exception {
        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_snapshot", Map.of());
        assertNotEquals(Boolean.TRUE, result.getIsError(), "swing_snapshot failed");
        return result.getContent().get(0).getText();
    }

    private void call(String tool, Map<String, Object> args) throws Exception {
        MCPProtocol.CallToolResult result = mcpClient.callTool(tool, args);
        assertNotEquals(Boolean.TRUE, result.getIsError(),
                tool + " returned an error: " + result.getContent());
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
                String t = w instanceof Frame ? ((Frame) w).getTitle()
                        : w instanceof Dialog ? ((Dialog) w).getTitle() : null;
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
                String t = w instanceof Frame ? ((Frame) w).getTitle()
                        : w instanceof Dialog ? ((Dialog) w).getTitle() : null;
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
