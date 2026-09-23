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
 * A {@code -javaagent} that starts the Swing MCP server before the application's
 * {@code main()}, so the application needs no code change:
 *
 * <pre>
 * java -Dswing.mcp.port=20000 -javaagent:swing-mcp-agent.jar -jar your-app.jar
 * </pre>
 *
 * Without {@value #PORT_PROPERTY} the server takes the default port; {@code 0} takes an
 * ephemeral one, and an unparsable value logs a warning and falls back to the default. The path
 * is always {@code /mcp}. A failed start is logged, never thrown into the application.
 */
public final class Agent {

    private static final Logger LOG = Logger.getLogger(Agent.class.getName());

    public static final String PORT_PROPERTY = "swing.mcp.port";

    /** The running server; {@code null} until the start completes, and forever if it fails. */
    static volatile @Nullable SwingMCP server;

    private Agent() {
    }

    /**
     * Starts the server on a daemon thread and returns at once, so the start never delays
     * {@code main()}.
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
