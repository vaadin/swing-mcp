package com.vaadin.swingmcp.mcp;

import java.awt.Component;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for {@link MCPServer} that works in headless mode.
 * <p>
 * Overrides {@link #runInEDT(Callable)} to call the block directly (no EDT
 * dispatch) and allows tests to supply their own component hierarchies via
 * {@link #setConsideredComponents(List)}.
 */
public class FakeMCPServer extends MCPServer {

    private volatile List<Component> consideredComponents = new CopyOnWriteArrayList<>();

    public FakeMCPServer(int port, String contextPath) {
        super(port, contextPath);
    }

    @Override
    protected <T> T runInEDT(Callable<T> block) throws Exception {
        return block.call();
    }

    /**
     * Sets the components returned by {@link #getConsideredComponents()}.
     * Thread-safe: the list is copied into a {@link CopyOnWriteArrayList}
     * and stored in a volatile field.
     *
     * @param components the components to consider; must not be null
     */
    public void setConsideredComponents(List<Component> components) {
        this.consideredComponents = new CopyOnWriteArrayList<>(components);
    }

    @Override
    protected List<Component> getConsideredComponents() {
        return consideredComponents;
    }
}
