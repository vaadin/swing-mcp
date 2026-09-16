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
import com.vaadin.swingmcp.tinymcpclient.MCPClient;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.swing.*;
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
     * Runs {@code block} on the EDT via {@link SwingUtilities#invokeAndWait} and returns
     * its result. Use this in place of direct {@code tool.execute()} calls so that Swing
     * state mutations happen on the correct thread, matching production behaviour.
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
}