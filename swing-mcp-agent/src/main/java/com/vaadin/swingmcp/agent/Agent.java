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
package com.vaadin.swingmcp.agent;

import com.vaadin.swingmcp.mcp.SwingMCP;

import java.lang.instrument.Instrumentation;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Java Instrumentation Agent that starts the Swing MCP server.
 * <p>
 * Usage: {@code java -javaagent:swing-mcp-agent.jar YourApp}
 * <p>
 * The MCP server is started on a daemon thread so it does not
 * prevent the JVM from shutting down.
 * <p>
 * The port defaults to the built-in MCP server default, but can be overridden via the
 * {@code swing.mcp.port} system property. A value of {@code 0} selects an OS-assigned
 * ephemeral port (useful for tests).
 */
public final class Agent {

    private static final Logger LOG = Logger.getLogger(Agent.class.getName());

    /** System property that overrides the MCP server port. */
    public static final String PORT_PROPERTY = "swing.mcp.port";

    /**
     * The started server instance, or {@code null} if startup has not completed yet
     * (or failed). Package-private for test introspection.
     */
    static volatile @Nullable SwingMCP server;

    private Agent() {
    }

    /**
     * Entry point called by the JVM before {@code main()}.
     */
    public static void premain(@Nullable String agentArgs, Instrumentation inst) {
        Thread starter = new Thread(() -> {
            try {
                SwingMCP s = buildServer();
                s.startAndAutoStop();
                server = s;
                LOG.info("Swing MCP agent started on " + s.getUrl());
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Failed to start Swing MCP agent", e);
            }
        }, "swing-mcp-agent-starter");
        starter.setDaemon(true);
        starter.start();
    }

    private static SwingMCP buildServer() {
        String value = System.getProperty(PORT_PROPERTY);
        if (value == null || value.isBlank()) {
            return new SwingMCP();
        }
        try {
            int port = Integer.parseInt(value.trim());
            return new SwingMCP(port, "/mcp");
        } catch (NumberFormatException e) {
            LOG.warning("Invalid " + PORT_PROPERTY + "=" + value + "; using default port");
            return new SwingMCP();
        }
    }
}
