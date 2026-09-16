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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;

class AgentTest {

    @AfterEach
    void tearDown() {
        if (Agent.server != null) {
            Agent.server.stop();
            Agent.server = null;
        }
        System.clearProperty(Agent.PORT_PROPERTY);
    }

    /**
     * Calls {@code premain} directly (no real {@link java.lang.instrument.Instrumentation}
     * needed) and verifies that the MCP server starts accepting HTTP connections.
     * Uses port 0 (OS-assigned ephemeral port) so parallel test runs don't collide.
     */
    @Test
    void premainStartsMcpServer() throws Exception {
        System.setProperty(Agent.PORT_PROPERTY, "0");
        Agent.premain(null, null);

        // The server starts on a background thread; poll until it's visible, then until reachable.
        long deadline = System.currentTimeMillis() + 5_000;
        while (Agent.server == null) {
            if (System.currentTimeMillis() >= deadline) {
                fail("MCP server did not publish itself within 5 seconds");
            }
            Thread.sleep(50);
        }

        int port = Agent.server.getPort();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create("http://127.0.0.1:" + port + "/mcp").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(200);
                conn.setReadTimeout(200);
                conn.getResponseCode();
                // Any HTTP response (even an error) means the server is up.
                return; // success
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                // Server not ready yet — retry.
                Thread.sleep(100);
            }
        }
        fail("MCP server did not start within 5 seconds");
    }
}
