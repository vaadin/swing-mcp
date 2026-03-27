package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import java.awt.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contextual information passed to {@link AbstractSwingTool#execute} on every
 * invocation. Collects the data that the server resolves <em>before</em> the
 * tool runs (considered components, and in the future potentially more).
 */
public class SwingToolContext {

    private List<Component> consideredComponents;
    /**
     * The SwingSnapshotTool populates this map by assigning IDs to every component
     * that the client MCP can interact with. The map is cleared after every
     * interaction and needs to be re-populated by another call to SwingSnapshotTool.
     */
    private final Map<Integer, Component> componentRefs = new HashMap<>();

    public void setConsideredComponents(List<Component> consideredComponents) {
        this.consideredComponents = consideredComponents == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(consideredComponents);
    }

    /**
     * @return the top-level components to inspect/interact with, never null.
     * The components are independent: no component in this list is nested in another component in this list.
     */
    public List<Component> getConsideredComponents() {
        return consideredComponents;
    }

    public void clearRefMap() {
       componentRefs.clear();
    }

    public Component getComponentByRef(int ref) {
        var result = componentRefs.get(ref);
        if (result == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS, "No component with ref " + ref + ". Maybe the Swing component tree has changed? Call swing_snapshot to obtain the newest component tree snapshot");
        }
        return result;
    }
}
