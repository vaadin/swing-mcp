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

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwingMCPTest extends AbstractHeadlessTest {

    @Test
    void smokeTestStartAndStop() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        SwingMCP s = new SwingMCP(0, "/mcp");
        s.start();
        s.stop();
    }

    @Test
    void listTools() throws Exception {
        mcpClient.initialize();

        List<MCPProtocol.Tool> tools = mcpClient.listTools();
        assertNotNull(tools);
        assertFalse(tools.isEmpty());
    }
}
