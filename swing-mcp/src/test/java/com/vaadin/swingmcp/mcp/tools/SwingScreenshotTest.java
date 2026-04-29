package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingScreenshotTest extends AbstractHeadlessTest {

    private SwingScreenshotTool tool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        tool = new SwingScreenshotTool();
        context = new SwingToolContext(Runnable::run);
    }

    private MCPProtocol.Content screenshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        return tool.execute(new Parameters(Map.of()), context);
    }

    private BufferedImage decodeImage(MCPProtocol.Content content) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(content.getData());
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    // ── Acceptance criteria ────────────────────────────────────────────────────

    @Test
    void singlePanelProducesPngWithExpectedDimensions() throws Exception {
        JPanel panel = new JPanel();
        panel.setSize(200, 150);
        panel.doLayout();

        BufferedImage image = decodeImage(screenshot(panel));

        assertEquals(200, image.getWidth());
        assertEquals(150, image.getHeight());
    }

    @Test
    void returnedMcpResultHasTypeImageAndMimeTypePng() throws Exception {
        JPanel panel = new JPanel();
        panel.setSize(100, 100);
        panel.doLayout();

        MCPProtocol.Content content = screenshot(panel);

        assertEquals("image", content.getType());
        assertEquals("image/png", content.getMimeType());
    }

    @Test
    void returnedPngIsDecodableByImageIO() throws Exception {
        JPanel panel = new JPanel();
        panel.setSize(100, 100);
        panel.doLayout();

        assertNotNull(decodeImage(screenshot(panel)));
    }

    @Test
    void swingScreenshotViaMcpClientReturnsImageContentResponse() {
        JPanel panel = new JPanel();
        panel.setSize(100, 100);
        panel.doLayout();
        mcpServer.setConsideredComponents(List.of(panel));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));

        assertNotNull(result);
        assertNotEquals(Boolean.TRUE, result.isError());
        assertFalse(result.content().isEmpty());
        McpSchema.ImageContent imageContent = (McpSchema.ImageContent) result.content().get(0);
        assertEquals("image/png", imageContent.mimeType());
    }

    @Test
    void emptyComponentListReturnsError() {
        mcpServer.setConsideredComponents(List.of());

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_screenshot", Map.of()));

        assertEquals(Boolean.TRUE, result.isError());
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        assertEquals("No visible windows to capture. The application may still be starting up — retry shortly.",
                textContent.text());
    }

    @Test
    void zeroSizePanelIsSkippedAndNormalPanelCaptured() throws Exception {
        JPanel zeroSize = new JPanel(); // getWidth() == 0, getHeight() == 0 by default
        JPanel normal = new JPanel();
        normal.setSize(300, 200);
        normal.doLayout();

        BufferedImage image = decodeImage(screenshot(zeroSize, normal));

        assertEquals(300, image.getWidth());
        assertEquals(200, image.getHeight());
    }

    @Test
    void twoPanelsProduceCorrectCompositeDimensions() throws Exception {
        JPanel first = new JPanel();
        first.setSize(300, 200);
        first.doLayout();
        JPanel second = new JPanel();
        second.setSize(200, 100);
        second.doLayout();

        BufferedImage image = decodeImage(screenshot(first, second));

        assertEquals(300, image.getWidth()); // max(300, 200)
        assertEquals(200 + 100 + SwingScreenshotTool.WINDOW_GAP, image.getHeight());
    }

    // ── Component matrix tests ─────────────────────────────────────────────────

    /** Creates a 200×100 panel containing the given children, laid out and ready to render. */
    private JPanel sizedPanel(Component... children) {
        JPanel panel = new JPanel();
        for (Component c : children) panel.add(c);
        panel.setSize(200, 100);
        panel.doLayout();
        return panel;
    }

    private void assertRendersToValidPng(JPanel panel) throws Exception {
        BufferedImage image = decodeImage(screenshot(panel));
        assertNotNull(image);
        assertEquals(200, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void jButtonRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JButton("Save")));
    }

    @Test
    void jTextFieldRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JTextField("Hello")));
    }

    @Test
    void jPasswordFieldRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JPasswordField("secret")));
    }

    @Test
    void jTextAreaRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JTextArea("text")));
    }

    @Test
    void jCheckBoxRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JCheckBox("Accept")));
    }

    @Test
    void jRadioButtonRendersSuccessfully() throws Exception {
        ButtonGroup group = new ButtonGroup();
        JRadioButton optionA = new JRadioButton("Option A");
        JRadioButton optionB = new JRadioButton("Option B");
        group.add(optionA);
        group.add(optionB);
        assertRendersToValidPng(sizedPanel(optionA, optionB));
    }

    @Test
    void jComboBoxRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JComboBox<>(new String[]{"One", "Two", "Three"})));
    }

    @Test
    void jToggleButtonRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JToggleButton("Bold")));
    }

    @Test
    void jSpinnerRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JSpinner(new SpinnerNumberModel(1, 0, 10, 1))));
    }

    @Test
    void jSliderRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JSlider(0, 100, 50)));
    }

    @Test
    void jPanelRendersSuccessfully() throws Exception {
        JPanel inner = new JPanel();
        inner.getAccessibleContext().setAccessibleName("Inner");
        assertRendersToValidPng(sizedPanel(inner));
    }

    @Test
    void jScrollPaneRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JScrollPane(new JLabel("Content"))));
    }

    @Test
    void jTabbedPaneRendersSuccessfully() throws Exception {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Tab1", new JPanel());
        tabbedPane.addTab("Tab2", new JPanel());
        assertRendersToValidPng(sizedPanel(tabbedPane));
    }

    @Test
    void jSplitPaneRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JLabel("Left"), new JLabel("Right"))));
    }

    @Test
    void jLabelRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JLabel("Status")));
    }

    @Test
    void jProgressBarRendersSuccessfully() throws Exception {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(42);
        assertRendersToValidPng(sizedPanel(bar));
    }

    @Test
    void jMenuBarRendersSuccessfully() throws Exception {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(new JMenu("File"));
        assertRendersToValidPng(sizedPanel(menuBar));
    }

    @Test
    void jMenuRendersSuccessfully() throws Exception {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Edit");
        menu.add(new JMenuItem("Cut"));
        menuBar.add(menu);
        assertRendersToValidPng(sizedPanel(menuBar));
    }

    @Test
    void jMenuItemRendersSuccessfully() throws Exception {
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Actions");
        menu.add(new JMenuItem("Delete"));
        menuBar.add(menu);
        assertRendersToValidPng(sizedPanel(menuBar));
    }

    @Test
    void jToolBarRendersSuccessfully() throws Exception {
        JToolBar toolBar = new JToolBar();
        toolBar.add(new JButton("Save"));
        assertRendersToValidPng(sizedPanel(toolBar));
    }

    @Test
    void jListRendersSuccessfully() throws Exception {
        assertRendersToValidPng(sizedPanel(new JList<>(new String[]{"Alpha", "Beta"})));
    }
}
