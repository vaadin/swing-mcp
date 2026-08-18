package com.vaadin.swingmcp.mcp;

import com.vaadin.swingmcp.ToolDescriptor;
import com.vaadin.swingmcp.tinymcpclient.TinyMCPClient;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
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
 * Coherence test guarding the contract between {@link SwingTools} (the
 * shared manifest consumed by {@code swing-mcp-proxy}) and the actual
 * tools registered by {@link SwingMCP}. Catches manifest-vs-registration
 * drift at developer-test time so the proxy's runtime drift probe
 * (DR-forwarding-proxy) only ever fires on genuine deployment-version mismatches.
 *
 * <p>Uses {@link TinyMCPClient} (not the official SDK) so the
 * {@code listTools()} response deserialises directly into our
 * {@link MCPProtocol.Tool} POJOs — same equality semantics
 * (DR-structural-schema-equality) as the {@link ToolDescriptor}s in {@link SwingTools#ALL}.
 *
 * <p>Three assertions:
 * <ol>
 *   <li>{@code initialize.serverInfo} matches {@link SwingTools#SERVER_NAME}
 *       and {@link SwingTools#SERVER_VERSION}.</li>
 *   <li>{@code initialize.instructions} equals {@link SwingTools#INSTRUCTIONS}.</li>
 *   <li>{@code listTools()} returns exactly {@link SwingTools#ALL} —
 *       same names, descriptions, and input schemas (structural equality
 *       per DR-structural-schema-equality).</li>
 * </ol>
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
