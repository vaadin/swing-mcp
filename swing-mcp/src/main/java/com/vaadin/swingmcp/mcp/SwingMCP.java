package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.tinymcpserver.HttpMCPServer;
import com.vaadin.swingmcp.tinymcpserver.MCPHandler;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPSession;
import javax.swing.SwingUtilities;
import com.vaadin.swingmcp.mcp.tools.AbstractSwingTool;
import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * MCP handler providing Swing-specific tools for UI inspection and interaction.
 * <p>
 * Composes an {@link MCPHandler} (configured with the single-session predicate
 * {@code count == 0} and Swing-specific server info and instructions) with an
 * {@link HttpMCPServer} that exposes it over HTTP, bound to {@code 127.0.0.1}.
 * <p>
 * Intended lifecycle: create, start (or {@link #startAndAutoStop()}), then let
 * the JVM terminate. No need to support repeated start/stop cycles.
 */
public class SwingMCP {

    private static final Logger LOG = Logger.getLogger(SwingMCP.class.getName());

    private static final String SERVER_NAME = "Swing MCP";
    private static final String SERVER_VERSION = "0.0.1";
    private static final String INSTRUCTIONS =
            "This server provides tools to inspect and interact with a running Java Swing application.\n" +
            "The MCP server runs in-process with the Swing application: if the application exits, this\n" +
            "server becomes unreachable. To restore the connection, ask the human operator to restart\n" +
            "the Swing application.\n" +
            "\n" +
            "## Workflow\n" +
            "\n" +
            "1. Call `swing_snapshot` first to get the current UI state as an accessibility tree. Each component has a `ref` ID used by all interaction tools.\n" +
            "2. Use the `ref` values from the snapshot to target specific components for interaction (click, set_text, etc.).\n" +
            "3. After each interaction, call `swing_snapshot` again to verify the UI has updated as expected.\n" +
            "4. Use `swing_screenshot` only when the accessibility tree alone is ambiguous — it returns a PNG image that may consume many tokens.\n" +
            "\n" +
            "## Key behaviors\n" +
            "\n" +
            "- `swing_snapshot` and `swing_screenshot` return the current state immediately.\n" +
            "- Interaction tools (`swing_click`, `swing_set_text`, etc.) dispatch the action to the Swing event thread asynchronously and return a one-line echo of the form `Dispatched <action> on ref=N [to <value>] — call swing_snapshot to verify the outcome`. The echo does NOT mean the UI changed — listeners can veto, revert, or open a dialog.\n" +
            "- `ref` values may change after UI transitions (dialogs opening/closing, navigation). Re-snapshot after significant state changes before using stale refs.\n" +
            "- Do not call mutation tools in parallel — each successful mutation clears the ref map, so the second call will fail with a stale-ref error. Issue tool calls sequentially.\n" +
            "- `swing_close` on a window with unsaved changes may trigger a confirmation dialog — snapshot afterward to detect it.";

    private final MCPHandler handler;
    private final HttpMCPServer server;
    private volatile Thread shutdownHook;
    /** Serialises all tool calls end-to-end (EDT phase + PostVerification polling). */
    private final Lock toolLock = new ReentrantLock();

    public SwingMCP(int port, String contextPath) {
        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName(SERVER_NAME);
        serverInfo.setVersion(SERVER_VERSION);
        // Single-session policy: reject any initialize that would create a
        // second concurrent session.
        this.handler = new MCPHandler(serverInfo, INSTRUCTIONS)
                .setAcceptNewSession(count -> count == 0);
        this.server = new HttpMCPServer(port, contextPath, handler);
        registerTools();
    }

    public SwingMCP() {
        this(HttpMCPServer.DEFAULT_PORT, HttpMCPServer.DEFAULT_CONTEXT_PATH);
    }

    public String getUrl() {
        return server.getUrl();
    }

    private void registerTools() {
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingScreenshotTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingClickTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingTogglePopupTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingIncrementTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingDecrementTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetTextTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetDescriptionTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSetTextTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetValueTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSetValueTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingToggleExpandTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingCloseTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetSelectionTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSetSelectionTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingClearSelectionTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetItemsTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetItemCountTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingSelectAllTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetCellsTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingGetCellCountTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingIconifyTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingRestoreTool());
        registerTool(new com.vaadin.swingmcp.mcp.tools.SwingDragTool());
    }

    /**
     * Registers a Swing tool with the underlying MCP handler. The tool is
     * wrapped so that every invocation:
     * <ol>
     *   <li>Acquires the SwingMCP-level lock, serialising all tool calls</li>
     *   <li>Resolves the per-session {@link SwingToolContext} from the current
     *       {@link MCPSession} (lazily creating one on first use); this must
     *       happen on the dispatch thread where {@link MCPSession#getCurrent()}
     *       is bound, before marshalling onto the EDT</li>
     *   <li>Marshals onto the EDT via {@link #runInEDT(Callable)}</li>
     *   <li>Retrieves the current considered components</li>
     *   <li>Delegates to {@link AbstractSwingTool#execute}</li>
     * </ol>
     *
     * @param tool the Swing tool to register
     */
    protected void registerTool(AbstractSwingTool tool) {
        handler.addTool(tool.getName(), tool.getDescription(), tool.getInputSchema(), request -> {
            SwingToolContext context = currentSessionContext();
            toolLock.lock();
            try {
                return runInEDT(() -> {
                    context.setConsideredComponents(getConsideredComponents());
                    MCPProtocol.Content result = tool.execute(new Parameters(request.arguments()), context);
                    if (tool.isMutation()) {
                        context.clearRefMap();
                    }
                    return result;
                });
            } finally {
                toolLock.unlock();
            }
        });
    }

    /**
     * Returns the {@link SwingToolContext} attached to the currently-dispatching
     * {@link MCPSession}, creating and attaching a fresh one on first access.
     * <p>
     * Must be called on the HTTP dispatch thread (where the session
     * {@code ThreadLocal} is bound), not on the EDT.
     */
    private static SwingToolContext currentSessionContext() {
        MCPSession session = MCPSession.getCurrent();
        SwingToolContext context = session.getAttribute(SwingToolContext.class);
        if (context == null) {
            context = new SwingToolContext(session.getHandler().getExecutor());
            session.setAttribute(SwingToolContext.class, context);
        }
        return context;
    }

    /**
     * Starts the MCP server.
     */
    public void start() {
        server.start();
        LOG.info("SwingMCP started");
    }

    /**
     * Stops the MCP server. Primarily intended for tests; production Swing
     * applications should use {@link #startAndAutoStop()} and let the JVM
     * terminate the server on shutdown.
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
        LOG.info("SwingMCP stopped");
    }

    /**
     * Starts the MCP server and registers a JVM shutdown hook to stop it
     * automatically when the application terminates. This is the recommended
     * method for Swing applications.
     */
    public void startAndAutoStop() {
        start();
        shutdownHook = new Thread(() -> {
            server.stop();
            LOG.info("SwingMCP stopped via shutdown hook");
        }, "SwingMCP-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public int getPort() {
        return server.getPort();
    }

    public String getContextPath() {
        return server.getContextPath();
    }

    /** Timeout for EDT tasks; exceeding it triggers deadlock detection. */
    static final long EDT_TIMEOUT_MS = 10_000;

    /**
     * Executes the given block on the Event Dispatch Thread, waits for it
     * to complete, and returns its result. Tools must use this method for
     * all Swing interactions.
     * <p>
     * If the EDT does not complete the block within {@link #EDT_TIMEOUT_MS}
     * milliseconds, an {@link com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException}
     * is thrown with the EDT's current stack trace. This detects the common
     * deadlock where an action listener shows a modal dialog (entering a
     * secondary event loop), preventing the EDT task from ever returning.
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
        AtomicReference<Thread> edtThreadRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            edtThreadRef.set(Thread.currentThread());
            try {
                result.set(block.call());
            } catch (Exception e) {
                error.set(e);
            } finally {
                done.countDown();
            }
        });

        if (!done.await(EDT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Thread edt = edtThreadRef.get();
            StringBuilder msg = new StringBuilder();
            msg.append("EDT did not complete within ").append(EDT_TIMEOUT_MS).append(" ms — ")
               .append("possible deadlock (e.g. an action listener opened a modal dialog).\n");
            if (edt != null) {
                msg.append("EDT stack trace:\n");
                for (StackTraceElement frame : edt.getStackTrace()) {
                    msg.append("  at ").append(frame).append('\n');
                }
            } else {
                msg.append("EDT thread not yet started.\n");
            }
            throw new com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException(msg.toString());
        }

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
            if (SwingUtils.isVisible(w) && !SwingUtils.isRedundantPopupWindow(w)) {
                visible.add(w);
            }
        }
        return visible;
    }
}
