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

import com.github.mvysny.tinymcpserver.client.MCPClient;
import com.github.mvysny.tinymcpserver.client.TinyMCPClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    protected static FakeSwingMCP mcpServer;
    protected static MCPClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new FakeSwingMCP(0, "/mcp", false);
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
}
