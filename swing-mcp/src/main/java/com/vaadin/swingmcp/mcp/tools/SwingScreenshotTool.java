package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tools.SwingTools;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;


/**
 * MCP tool {@code swing_screenshot}: captures a screenshot of the Swing
 * application and returns it as a single PNG image.
 *
 * <p>Components are obtained from {@link SwingToolContext#getConsideredComponents()}.
 * Zero-size components are silently skipped. If no renderable components remain,
 * an error is returned. Multiple components are arranged vertically with a
 * {@value #WINDOW_GAP} px gap between them.</p>
 */
public class SwingScreenshotTool extends AbstractSwingTool {

    public static final int WINDOW_GAP = 4;

    public SwingScreenshotTool() {
        super(SwingTools.SWING_SCREENSHOT);
    }

    @Override
    public boolean isMutation() {
        return false;
    }

    @Override
    public MCPProtocol.Content execute(Parameters params,
                                       SwingToolContext context) throws Exception {
        // BR-08: filter out zero-size components
        List<Component> renderables = new ArrayList<>();
        for (Component c : context.getConsideredComponents()) {
            if (c.getWidth() > 0 && c.getHeight() > 0) {
                renderables.add(c);
            }
        }

        // BR-03: empty after filtering → error
        if (renderables.isEmpty()) {
            throw new MCPErrorResponseException(
                    "No visible windows to capture. The application may still be starting up — retry shortly.");
        }

        BufferedImage result = renderables.size() == 1
                ? renderComponent(renderables.get(0))
                : renderComposite(renderables);

        return MCPProtocol.Content.image(result);
    }

    private BufferedImage renderComponent(Component c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            c.printAll(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private BufferedImage renderComposite(List<Component> components) {
        // BR-02: composite width = max; composite height = sum of heights + gaps
        int maxWidth = 0;
        int totalHeight = 0;
        for (Component c : components) {
            if (c.getWidth() > maxWidth) {
                maxWidth = c.getWidth();
            }
            totalHeight += c.getHeight();
        }
        totalHeight += (components.size() - 1) * WINDOW_GAP;

        BufferedImage composite = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics g = composite.createGraphics();
        try {
            int y = 0;
            for (int i = 0; i < components.size(); i++) {
                Component c = components.get(i);
                int x = (maxWidth - c.getWidth()) / 2;
                Graphics sub = g.create(x, y, c.getWidth(), c.getHeight());
                try {
                    c.printAll(sub);
                } finally {
                    sub.dispose();
                }
                y += c.getHeight();
                if (i < components.size() - 1) {
                    y += WINDOW_GAP;
                }
            }
        } finally {
            g.dispose();
        }
        return composite;
    }
}
