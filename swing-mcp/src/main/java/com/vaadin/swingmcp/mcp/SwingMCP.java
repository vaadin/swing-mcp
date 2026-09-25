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
package com.vaadin.swingmcp.mcp;

import com.github.mvysny.tinymcpserver.HttpMCPServer;
import com.github.mvysny.tinymcpserver.MCPHandler;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.github.mvysny.tinymcpserver.MCPSession;
import com.github.mvysny.tinymcpserver.SessionDecision;
import com.vaadin.swingmcp.tools.SwingTools;
import javax.swing.SwingUtilities;
import com.vaadin.swingmcp.mcp.tools.AbstractSwingTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import org.jspecify.annotations.Nullable;

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
 * The Swing MCP server: every Swing tool, served over loopback HTTP to one client at a time
 * (D_single_session). Start it once and let the JVM's exit stop it:
 *
 * <pre>{@code
 * new SwingMCP().startAndAutoStop();   // http://127.0.0.1:18088/mcp
 * }</pre>
 *
 * {@code new SwingMCP(0, "/mcp")} takes an ephemeral port instead; read it back with
 * {@link #getUrl()} once started. One start and one stop per instance.
 */
public class SwingMCP {

    private static final Logger LOG = Logger.getLogger(SwingMCP.class.getName());

    /**
     * What an evicted client reads, verbatim, in the 404 body of its next call once a new
     * client connects (D_single_session).
     */
    static final String EVICTION_REASON =
            "This Swing application's MCP server only accepts one client at a time, "
                    + "and a new client just connected — so this session was closed. "
                    + "If you did not expect this, make sure only one MCP client (e.g. a "
                    + "single Claude Code instance) is configured to connect to this app. "
                    + "Multiple clients, or several terminals each launching their own "
                    + "client, will repeatedly evict each other. To resume work here, "
                    + "reconnect from the surviving client; the previous session cannot "
                    + "be recovered.";

    private final MCPHandler handler;
    private final HttpMCPServer server;
    private volatile @Nullable Thread shutdownHook;
    /** Held for a whole tool call, dispatch included (D_tool_call_wide_lock). */
    private final Lock toolLock = new ReentrantLock();

    public SwingMCP(int port, String contextPath) {
        MCPProtocol.Implementation serverInfo = new MCPProtocol.Implementation();
        serverInfo.setName(SwingTools.SERVER_NAME);
        serverInfo.setVersion(SwingTools.SERVER_VERSION);
        // D_single_session: a new initialize evicts the existing session.
        this.handler = new MCPHandler(serverInfo, SwingTools.INSTRUCTIONS)
                .setAcceptNewSession(existing -> new SessionDecision.AcceptAndEvict(existing, EVICTION_REASON));
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
     * Registers {@code tool} behind the shared wrapper — session context, {@code toolLock}, the
     * EDT hop, and the ref-map clear after a mutation that returns normally. The steps and
     * their order are {@code design/architecture.md} § Flows, "A tool call".
     */
    protected void registerTool(AbstractSwingTool tool) {
        handler.addTool(tool.getName(), tool.getDescription(), tool.getInputSchema(), request -> {
            SwingToolContext context = currentSessionContext();
            toolLock.lock();
            try {
                return runInEDT(() -> {
                    context.setConsideredComponents(getConsideredComponents());
                    MCPProtocol.Content result = tool.execute(request.arguments(), context);
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
     * Returns the {@link SwingToolContext} of the dispatching {@link MCPSession}, attaching a
     * fresh one on first use. Call it on the dispatch thread, not the EDT:
     * {@link MCPSession#getCurrent()} is a thread-local bound only there.
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

    public void start() {
        server.start();
        LOG.info("SwingMCP started");
    }

    /**
     * Stops the server and removes the shutdown hook {@link #startAndAutoStop()} registered.
     * For tests; an application lets the JVM's exit stop it.
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

    /** {@link #start()}, plus a JVM shutdown hook that stops the server. */
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

    /** How long {@link #runInEDT} waits before reporting a blocked EDT. */
    static final long EDT_TIMEOUT_MS = 10_000;

    /**
     * Runs {@code block} on the EDT and waits for its result — the one way any Swing state is
     * touched.
     *
     * @throws com.github.mvysny.tinymcpserver.MCPErrorResponseException carrying the EDT's
     *     stack trace, if the block has not finished within {@link #EDT_TIMEOUT_MS}
     * @throws Exception whatever {@code block} throws
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
            throw new com.github.mvysny.tinymcpserver.MCPErrorResponseException(msg.toString());
        }

        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    /**
     * Returns the roots every tool sees: the topmost visible modal dialog alone, or else every
     * visible window except a redundant popup container (D_interactable_windows_only). A
     * headless test overrides it; see {@code design/architecture.md} § Testing.
     */
    protected List<Component> getConsideredComponents() {
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
