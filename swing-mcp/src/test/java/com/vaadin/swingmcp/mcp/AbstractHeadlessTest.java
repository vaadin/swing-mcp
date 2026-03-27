package com.vaadin.swingmcp.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.BeforeAll;

import java.time.Duration;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void enableHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    protected static McpSyncClient buildSyncClient(String url, Duration timeout) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(url)
                .openConnectionOnStartup(false)
                .build();
        return McpClient.sync(transport)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
    }
}
