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

import com.github.mvysny.tinymcpserver.ToolDescriptor;
import com.github.mvysny.tinymcpserver.client.TinyMCPClient;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tools.SwingTools;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a live server advertises must equal the {@link SwingTools} manifest
 * (D_shared_tool_manifest). {@link TinyMCPClient}, not the official SDK, so {@code listTools()}
 * deserialises into our own {@link MCPProtocol.Tool} and compares with the same equality as
 * {@link ToolDescriptor}.
 */
class SwingToolsCoherenceTest {

    private static FakeSwingMCP server;
    private static TinyMCPClient client;
    private static MCPProtocol.InitializeResult initResult;

    @BeforeAll
    static void start() throws IOException {
        System.setProperty("java.awt.headless", "true");
        server = new FakeSwingMCP(0, "/mcp", false);
        server.start();
        client = new TinyMCPClient(URI.create(server.getUrl()));
        initResult = client.initialize();
    }

    @AfterAll
    static void stop() throws IOException {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void serverInfoMatchesSwingToolsConstants() {
        assertEquals(SwingTools.SERVER_NAME, initResult.getServerInfo().getName());
        assertEquals(SwingTools.SERVER_VERSION, initResult.getServerInfo().getVersion());
    }

    @Test
    void instructionsMatchSwingToolsConstants() {
        assertEquals(SwingTools.INSTRUCTIONS, initResult.getInstructions());
    }

    @Test
    void listToolsMatchesSwingToolsAllExactly() throws IOException {
        List<MCPProtocol.Tool> live = client.listTools();
        Map<String, ToolDescriptor> liveByName = new HashMap<>();
        for (MCPProtocol.Tool t : live) {
            MCPProtocol.InputSchema schema = t.getInputSchema() != null
                    ? t.getInputSchema()
                    : new MCPProtocol.InputSchema();
            liveByName.put(t.getName(),
                    new ToolDescriptor(t.getName(),
                            t.getDescription() != null ? t.getDescription() : "",
                            schema));
        }
        Map<String, ToolDescriptor> expected = new HashMap<>();
        for (ToolDescriptor d : SwingTools.ALL) {
            expected.put(d.name(), d);
        }

        // Names first — clearer failure when an entry is added/removed.
        assertEquals(expected.keySet(), liveByName.keySet(),
                "registered tool name set must match SwingTools.ALL");
        // Per-tool deep comparison — catches description / schema drift.
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), liveByName.get(name),
                    "tool '" + name + "' descriptor must match SwingTools.SWING_*");
        }
    }
}
