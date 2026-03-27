package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractScreenTest;
import io.modelcontextprotocol.spec.McpSchema;
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
        return frame;
    }

    private JDialog showDialog(Frame owner, int width, int height) throws InterruptedException {
        JDialog dialog = new JDialog(owner, "Dialog", false);
        dialog.setSize(width, height);
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
        awaitVisibility(dialog, true);
        createdWindows.add(dialog);
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

    private BufferedImage decodeResult(McpSchema.CallToolResult result) throws Exception {
        assertNotEquals(Boolean.TRUE, result.isError(), "unexpected error result");
        McpSchema.ImageContent imageContent = (McpSchema.ImageContent) result.content().get(0);
        byte[] bytes = Base64.getDecoder().decode(imageContent.data());
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void singleVisibleFrameProducesPngWithFrameDimensions() throws Exception {
        JFrame frame = showFrame(400, 300);
        mcpServer.setConsideredComponents(List.of(frame));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        assertEquals(frame.getWidth(), image.getWidth());
        assertEquals(frame.getHeight(), image.getHeight());
    }

    @Test
    void multipleVisibleFramesProduceSingleVerticallyStackedImage() throws Exception {
        JFrame frame1 = showFrame(400, 300);
        JFrame frame2 = showFrame(300, 200);
        mcpServer.setConsideredComponents(List.of(frame1, frame2));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        int expectedWidth = Math.max(frame1.getWidth(), frame2.getWidth());
        int expectedHeight = frame1.getHeight() + frame2.getHeight() + SwingScreenshotTool.WINDOW_GAP;
        assertEquals(expectedWidth, image.getWidth());
        assertEquals(expectedHeight, image.getHeight());
    }

    @Test
    void withModalDialogOpenOnlyDialogIsCaptured() throws Exception {
        JFrame frame = showFrame(400, 300);
        JDialog dialog = showDialog(frame, 200, 150);

        // Simulate what MCPServer.getConsideredComponents() returns when a modal
        // dialog is present: only the dialog is considered.
        mcpServer.setConsideredComponents(List.of(dialog));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        assertEquals(dialog.getWidth(), image.getWidth());
        assertEquals(dialog.getHeight(), image.getHeight());
    }
}
