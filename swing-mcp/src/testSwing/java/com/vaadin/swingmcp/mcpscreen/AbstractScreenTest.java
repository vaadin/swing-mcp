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
package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.FakeSwingMCP;
import com.github.mvysny.tinymcpserver.client.MCPClient;
import com.github.mvysny.tinymcpserver.client.TinyMCPClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.swing.*;
import java.awt.Frame;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractScreenTest {
    @BeforeAll
    public static void assertScreenPresent() {
        assertEquals("false", System.getProperty("java.awt.headless"));
        new JFrame(); // this fails on headless
    }

    protected static FakeSwingMCP mcpServer;
    protected static MCPClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new FakeSwingMCP(0, "/mcp", true);
        mcpServer.start();

        mcpClient = new TinyMCPClient(URI.create(mcpServer.getUrl()));
        mcpClient.initialize();
    }

    @AfterAll
    static void stopMcpServer() throws Exception {
        if (mcpClient != null) {
            mcpClient.close();
        }
        if (mcpServer != null) {
            mcpServer.stop();
        }
    }

    /**
     * Runs {@code block} on the EDT and returns its result, rethrowing what it throws. A
     * no-op block drains the EDT, so a tool's fire-and-forget action has run afterwards:
     *
     * <pre>{@code
     * executeOnEDT(() -> null);
     * }</pre>
     */
    protected static <T> T executeOnEDT(Callable<T> block) throws Exception {
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
     * Polls {@link Frame#getExtendedState()} until {@code (state & mask) == expected}
     * or {@code timeoutMs} elapses. Returns silently on timeout; the caller's next
     * assertion reports it.
     *
     * <p>{@code setExtendedState} only posts a request to the window manager: on X11
     * the reported state lags by tens of milliseconds, and a second request chained
     * before the first settles can be silently dropped.
     */
    protected static void awaitExtendedState(Frame frame, int mask, int expected, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if ((frame.getExtendedState() & mask) == expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
