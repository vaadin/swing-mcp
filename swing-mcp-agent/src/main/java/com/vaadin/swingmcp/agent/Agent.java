package com.vaadin.swingmcp.agent;

import com.vaadin.swingmcp.mcp.MCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;

/**
 * Java Instrumentation Agent that starts the Swing MCP server.
 * <p>
 * Usage: {@code java -javaagent:swing-mcp-agent.jar YourApp}
 * <p>
 * The MCP server is started on a daemon thread so it does not
 * prevent the JVM from shutting down.
 */
public final class Agent {

    private static final Logger LOG = LoggerFactory.getLogger(Agent.class);

    private Agent() {
    }

    /**
     * Entry point called by the JVM before {@code main()}.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        Thread starter = new Thread(() -> {
            try {
                MCPServer server = new MCPServer();
                server.startAndAutoStop();
                LOG.info("Swing MCP agent started");
            } catch (Exception e) {
                LOG.error("Failed to start Swing MCP agent", e);
            }
        }, "swing-mcp-agent-starter");
        starter.setDaemon(true);
        starter.start();
    }
}
