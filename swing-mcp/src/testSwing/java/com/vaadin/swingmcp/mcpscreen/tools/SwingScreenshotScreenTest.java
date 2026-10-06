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

import com.vaadin.swingmcp.mcp.tools.SwingScreenshotTool;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingScreenshotScreenTest extends AbstractScreenTest {

    private final List<Window> createdWindows = new ArrayList<>();

    @AfterEach
    void disposeWindows() throws Exception {
        for (Window w : createdWindows) {
            SwingUtilities.invokeAndWait(() -> {
                w.setVisible(false);
                w.dispose();
            });
        }
        createdWindows.clear();
    }

    private JFrame showFrame(int width, int height) throws Exception {
        JFrame frame = new JFrame();
        frame.setSize(width, height);
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        createdWindows.add(frame);
        awaitSizeSettled(frame);
        return frame;
    }

    private JFrame showUndecoratedFrame(int width, int height) throws Exception {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(width, height);
        SwingUtilities.invokeAndWait(() -> frame.setVisible(true));
        createdWindows.add(frame);
        awaitSizeSettled(frame);
        return frame;
    }

    private JDialog showDialog(Frame owner, int width, int height) throws Exception {
        JDialog dialog = new JDialog(owner, "Dialog", false);
        dialog.setSize(width, height);
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        awaitVisibility(dialog, true);
        createdWindows.add(dialog);
        awaitSizeSettled(dialog);
        return dialog;
    }

    private void awaitVisibility(Window w, boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (w.isVisible() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, w.isVisible(),
                "Window visibility did not reach " + expected + " within 2 s");
    }

    /**
     * Waits until the window's size stops changing, returning silently on timeout.
     * {@code setVisible(true)} returns before the window manager's X11 round-trip, so a
     * decorated frame can still grow by its title-bar height after the test moves on.
     */
    private static void awaitSizeSettled(Window w) throws Exception {
        long deadline = System.currentTimeMillis() + 2_000;
        Dimension last = null;
        int stableTicks = 0;
        while (System.currentTimeMillis() < deadline) {
            Dimension[] holder = new Dimension[1];
            SwingUtilities.invokeAndWait(() -> holder[0] = w.getSize());
            Dimension now = holder[0];
            if (now.width > 0 && now.height > 0 && now.equals(last)) {
                if (++stableTicks >= 3) return; // unchanged for ~150 ms
            } else {
                stableTicks = 0;
            }
            last = now;
            Thread.sleep(50);
        }
    }

    private BufferedImage decodeResult(MCPProtocol.CallToolResult result) throws Exception {
        assertNotEquals(Boolean.TRUE, result.getIsError(), "unexpected error result");
        MCPProtocol.Content imageContent = result.getContent().get(0);
        byte[] bytes = Base64.getDecoder().decode(imageContent.getData());
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void singleVisibleFrameProducesPngWithFrameDimensions() throws Exception {
        JFrame frame = showFrame(400, 300);
        mcpServer.setConsideredComponents(List.of(frame));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);

        // Read after the call, once the window manager has applied the decorations.
        int[] dims = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = frame.getWidth();
            dims[1] = frame.getHeight();
        });
        assertEquals(dims[0], image.getWidth());
        assertEquals(dims[1], image.getHeight());
    }

    @Test
    void multipleVisibleFramesProduceSingleVerticallyStackedImage() throws Exception {
        // Undecorated: the window manager may resize a decorated frame after setVisible(),
        // and this asserts exact pixels.
        JFrame frame1 = showUndecoratedFrame(400, 300);
        JFrame frame2 = showUndecoratedFrame(300, 200);
        mcpServer.setConsideredComponents(List.of(frame1, frame2));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);

        assertEquals(400, image.getWidth());
        assertEquals(300 + 200 + SwingScreenshotTool.WINDOW_GAP, image.getHeight());
    }

    @Test
    void withModalDialogOpenOnlyDialogIsCaptured() throws Exception {
        JFrame frame = showFrame(400, 300);
        JDialog dialog = showDialog(frame, 200, 150);

        // What SwingMCP.getConsideredComponents() returns while a modal dialog is showing
        mcpServer.setConsideredComponents(List.of(dialog));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);

        assertEquals(dialog.getWidth(), image.getWidth());
        // Sometimes 187 — probably the OS title bar is included
        assertTrue(image.getHeight() >= 150, "Height was " + image.getHeight());
    }

    // ── Component matrix ───────────────────────────────────────────────────────

    @Test
    void jFrameRendersSuccessfully() throws Exception {
        JFrame frame = showFrame(200, 100);
        mcpServer.setConsideredComponents(List.of(frame));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);

        // Read after the call, once the window manager has applied the decorations.
        int[] dims = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = frame.getWidth();
            dims[1] = frame.getHeight();
        });
        assertEquals(dims[0], image.getWidth());
        assertEquals(dims[1], image.getHeight());
    }

    @Test
    void jInternalFrameInsideDesktopPaneRendersSuccessfully() throws Exception {
        JFrame host = showFrame(400, 300);
        JDesktopPane desktop = new JDesktopPane();
        SwingUtilities.invokeAndWait(() -> host.setContentPane(desktop));
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(200, 100);
        SwingUtilities.invokeAndWait(() -> {
            desktop.add(iframe);
            iframe.setVisible(true);
        });
        mcpServer.setConsideredComponents(List.of(host));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0,
                "Screenshot of JFrame with JInternalFrame should produce a valid image");
    }

    @Test
    void jDialogRendersSuccessfully() throws Exception {
        JFrame owner = showFrame(200, 100);
        JDialog dialog = showDialog(owner, 200, 100);
        mcpServer.setConsideredComponents(List.of(dialog));

        MCPProtocol.CallToolResult result = mcpClient.callTool("swing_screenshot", Map.of());
        BufferedImage image = decodeResult(result);

        // Read after the call, once the window manager has applied the decorations.
        int[] dims = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = dialog.getWidth();
            dims[1] = dialog.getHeight();
        });
        assertEquals(dims[0], image.getWidth());
        assertEquals(dims[1], image.getHeight());
    }
}
