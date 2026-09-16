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
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
        // filter out zero-size components
        List<Component> renderables = new ArrayList<>();
        for (Component c : context.getConsideredComponents()) {
            if (c.getWidth() > 0 && c.getHeight() > 0) {
                renderables.add(c);
            }
        }

        // empty after filtering → error
        if (renderables.isEmpty()) {
            throw new MCPErrorResponseException(
                    "No visible windows to capture. The application may still be starting up — retry shortly.");
        }

        BufferedImage result = renderables.size() == 1
                ? renderComponent(renderables.get(0))
                : renderComposite(renderables);

        String savePath = params.getStringOrNull("save_to");
        if (savePath != null) {
            return saveToDisk(result, savePath);
        }
        return MCPProtocol.Content.image(result);
    }

    private MCPProtocol.Content saveToDisk(BufferedImage image, String savePath)
            throws MCPErrorResponseException {
        Path path = Path.of(savePath);
        // Absolute only: the caller cannot see the server's working directory, so a
        // relative path would resolve somewhere neither side can predict.
        if (!path.isAbsolute()) {
            throw new MCPErrorResponseException(
                    "save_to must be an absolute path; got: " + savePath);
        }
        File file = path.toFile();
        try {
            if (!ImageIO.write(image, "png", file)) {
                throw new MCPErrorResponseException(
                        "Failed to write screenshot to " + path + ": no PNG writer available");
            }
        } catch (IOException e) {
            throw new MCPErrorResponseException(
                    "Failed to write screenshot to " + path + ": " + e.getMessage());
        }
        return MCPProtocol.Content.text("Saved PNG screenshot ("
                + image.getWidth() + "x" + image.getHeight() + ") to " + path);
    }

    private BufferedImage renderComponent(Component c) {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            // Not SwingUtilities.paintComponent: it reparents the component into a
            // CellRendererPane, which is unsafe for a JFrame/JDialog. printAll turns off
            // RepaintManager double buffering, delegates to paint(), and restores it.
            c.printAll(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private BufferedImage renderComposite(List<Component> components) {
        // composite width = max; composite height = sum of heights + gaps
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
