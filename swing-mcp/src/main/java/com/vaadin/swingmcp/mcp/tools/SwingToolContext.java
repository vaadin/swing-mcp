package com.vaadin.swingmcp.mcp.tools;

import java.awt.Component;
import java.util.Collections;
import java.util.List;

/**
 * Contextual information passed to {@link AbstractSwingTool#execute} on every
 * invocation. Collects the data that the server resolves <em>before</em> the
 * tool runs (considered components, and in the future potentially more).
 */
public class SwingToolContext {

    private final List<Component> consideredComponents;

    public SwingToolContext(List<Component> consideredComponents) {
        this.consideredComponents = consideredComponents == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(consideredComponents);
    }

    /**
     * @return the top-level components to inspect/interact with, never null
     */
    public List<Component> getConsideredComponents() {
        return consideredComponents;
    }
}
