package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.FakeSwingMCPHandler;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import javax.swing.*;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractScreenTest {
    @BeforeAll
    public static void assertScreenPresent() {
        assertEquals("false", System.getProperty("java.awt.headless"));
        new JFrame(); // this fails on headless
    }

    protected static FakeSwingMCPHandler mcpServer;
    protected static McpSyncClient mcpClient;

    @BeforeAll
    static void startMcpServer() throws Exception {
        // Port 0 → OS-assigned ephemeral port, so parallel test runs don't collide.
        mcpServer = new FakeSwingMCPHandler(0, "/mcp", true);
        mcpServer.start();

        Duration timeout = Duration.ofSeconds(5);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(mcpServer.getUrl())
                .openConnectionOnStartup(false)
                .build();
        mcpClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        mcpClient.initialize();
    }

    @AfterAll
    static void stopMcpServer() {
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