package com.vaadin.swingmcp.mcp;

import java.awt.Component;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for {@link SwingMCP} that allows tests to supply their own component
 * hierarchies via {@link #setConsideredComponents(List)}.
 * <p>
 * The {@code useEDT} constructor flag controls {@link #runInEDT(Callable)} behaviour:
 * <ul>
 *   <li>{@code false} — block is called directly on the calling thread; use this for
 *       headless tests where no real EDT is running.</li>
 *   <li>{@code true} — delegates to {@code super.runInEDT}, which marshals the block
 *       onto the EDT via {@link javax.swing.SwingUtilities#invokeAndWait}; use this for
 *       screen-mode tests where a real EDT is running.</li>
 * </ul>
 */
public class FakeSwingMCP extends SwingMCP {

    private final boolean useEDT;
    private volatile List<Component> consideredComponents = new CopyOnWriteArrayList<>();

    public FakeSwingMCP(int port, String contextPath, boolean useEDT) {
        super(port, contextPath);
        this.useEDT = useEDT;
    }

    @Override
    protected synchronized <T> T runInEDT(Callable<T> block) throws Exception {
        if (useEDT) {
            return super.runInEDT(block);
        }
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
