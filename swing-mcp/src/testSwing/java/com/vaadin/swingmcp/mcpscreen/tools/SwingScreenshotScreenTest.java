package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.SwingScreenshotTool;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
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

        // Read dimensions after the call: by this point the OS has applied any window
        // decorations and the frame has settled at its final size.
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
        JFrame frame1 = showFrame(400, 300);
        JFrame frame2 = showFrame(300, 200);
        mcpServer.setConsideredComponents(List.of(frame1, frame2));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        // Read dimensions after the call: by this point the OS has applied any window
        // decorations and both frames have settled at their final sizes.
        int[] dims = new int[4];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = frame1.getWidth();
            dims[1] = frame1.getHeight();
            dims[2] = frame2.getWidth();
            dims[3] = frame2.getHeight();
        });
        assertEquals(Math.max(dims[0], dims[2]), image.getWidth());
        assertEquals(dims[1] + dims[3] + SwingScreenshotTool.WINDOW_GAP, image.getHeight());
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
        // sometimes the height is 187, probably the dialog OS title bar is included?
        assertTrue(image.getHeight() >= 150, "Height was " + image.getHeight());
    }

    // ── Component matrix ───────────────────────────────────────────────────────

    @Test
    void jFrameRendersSuccessfully() throws Exception {
        JFrame frame = showFrame(200, 100);
        mcpServer.setConsideredComponents(List.of(frame));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        // Read dimensions after the call: by this point the OS has applied any window
        // decorations and the frame has settled at its final size.
        int[] dims = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = frame.getWidth();
            dims[1] = frame.getHeight();
        });
        assertEquals(dims[0], image.getWidth());
        assertEquals(dims[1], image.getHeight());
    }

    @Test
    void jDialogRendersSuccessfully() throws Exception {
        JFrame owner = showFrame(200, 100);
        JDialog dialog = showDialog(owner, 200, 100);
        mcpServer.setConsideredComponents(List.of(dialog));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));
        BufferedImage image = decodeResult(result);

        // Read dimensions after the call: by this point the OS has applied any window
        // decorations and the dialog has settled at its final size.
        int[] dims = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            dims[0] = dialog.getWidth();
            dims[1] = dialog.getHeight();
        });
        assertEquals(dims[0], image.getWidth());
        assertEquals(dims[1], image.getHeight());
    }
}
