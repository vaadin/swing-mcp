package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.TinyMCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import com.vaadin.swingmcp.mcp.tools.AbstractSwingTool;
import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP server providing Swing-specific tools for UI inspection and interaction.
 * <p>
 * Wraps a {@link TinyMCPServer} and registers Swing-related MCP tools.
 * Binds to {@code 127.0.0.1} only.
 * <p>
 * Intended lifecycle: create, start (or {@link #startAndAutoStop()}), then let
 * the JVM terminate. No need to support repeated start/stop cycles.
 */
public class MCPServer {

    private static final Logger LOG = LoggerFactory.getLogger(MCPServer.class);

    private final TinyMCPServer server;
    private volatile Thread shutdownHook;

    public MCPServer(int port, String contextPath) {
        this.server = new TinyMCPServer(port, contextPath);
        registerTools();
    }

    public MCPServer() {
        this(TinyMCPServer.DEFAULT_PORT, TinyMCPServer.DEFAULT_CONTEXT_PATH);
    }

    private void registerTools() {
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingScreenshotTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingClickTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetTextTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSetTextTool());
    }

    private final SwingToolContext context = new SwingToolContext();

    /**
     * Registers a Swing tool with the underlying MCP server. The tool is
     * wrapped so that every invocation:
     * <ol>
     *   <li>Marshals onto the EDT via {@link #runInEDT(Callable)}</li>
     *   <li>Retrieves the current considered components</li>
     *   <li>Delegates to {@link AbstractSwingTool#execute}</li>
     * </ol>
     *
     * @param tool the Swing tool to register
     */
    protected void registerTool(AbstractSwingTool tool) {
        server.addTool(tool.getName(), tool.getDescription(), tool.getInputSchema(), params ->
                runInEDT(() -> {
                    context.setConsideredComponents(getConsideredComponents());
                    try {
                        return tool.execute(new Parameters(params), context);
                    } finally {
                        if (tool.isMutation()) {
                            context.clearRefMap();
                        }
                    }
                })
        );
    }

    /**
     * Starts the MCP server.
     */
    public void start() throws IOException {
        server.start();
        LOG.info("Swing MCPServer started");
    }

    /**
     * Stops the MCP server.
     */
    public void stop() {
        server.stop();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // JVM is already shutting down — ignore
            }
            shutdownHook = null;
        }
        LOG.info("Swing MCPServer stopped");
    }

    /**
     * Starts the MCP server and registers a JVM shutdown hook to stop it
     * automatically when the application terminates. This is the recommended
     * method for Swing applications.
     */
    public void startAndAutoStop() throws IOException {
        start();
        shutdownHook = new Thread(() -> {
            server.stop();
            LOG.info("Swing MCPServer stopped via shutdown hook");
        }, "SwingMCPServer-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public int getPort() {
        return server.getPort();
    }

    public String getContextPath() {
        return server.getContextPath();
    }

    /**
     * Executes the given block on the Event Dispatch Thread, waits for it
     * to complete, and returns its result. Tools must use this method for
     * all Swing interactions.
     * <p>
     * Tests override this to run the block directly on the calling thread,
     * since headless mode does not have a functioning EDT.
     *
     * @param <T>   the return type of the block
     * @param block the code to execute on the EDT
     * @return the value returned by the block
     * @throws Exception if the block throws an exception
     */
    protected <T> T runInEDT(Callable<T> block) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(block.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    /**
     * Returns the list of top-level components to consider for snapshots and
     * screenshots. By default this returns the visible Swing windows, respecting
     * modal window rules (see project-context.md &sect;5).
     * <p>
     * Tests override this method to provide their own component hierarchies
     * (e.g. JPanels) since {@link Window} cannot be instantiated in headless mode.
     *
     * @return the components to inspect, never null
     */
    protected List<Component> getConsideredComponents() {
        // If a modal dialog is showing, only consider that one
        Dialog modal = SwingUtils.getTopmostModalDialog();
        if (modal != null) {
            return Collections.singletonList(modal);
        }

        Window[] windows = Window.getWindows();
        if (windows == null || windows.length == 0) {
            return Collections.emptyList();
        }

        List<Component> visible = new ArrayList<>();
        for (Window w : windows) {
            if (w.isVisible()) {
                visible.add(w);
            }
        }
        return visible;
    }
}
