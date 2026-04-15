package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import javax.accessibility.Accessible;
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
     * The {@link AbstractSwingTool#TOOL_SWING_SNAPSHOT} populates this map by assigning IDs to every accessible
     * that the client MCP can interact with. Stored as {@link Accessible} rather than {@link Component} because
     * virtual accessibility children (e.g. JTable cells, JTree nodes) are {@link Accessible} but not {@link Component}.
     * The map is cleared after every mutation and needs to be re-populated by another call to SwingSnapshotTool.
     */
    private final Map<Integer, Accessible> componentRefs = new HashMap<>();

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

    public void putRef(int ref, Accessible accessible) {
        componentRefs.put(ref, accessible);
    }

    public Accessible getAccessibleByRef(int ref) {
        var result = componentRefs.get(ref);
        if (result == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS, "No component with ref " + ref + ". The ref map is empty or stale — it is cleared after every mutation. Call swing_snapshot to rebuild it.");
        }
        return result;
    }

    /**
     * Returns the ref assigned to the given component. For test use only.
     * <p>
     * Casts the component to {@link Accessible} and performs a reverse lookup
     * in the ref map. Throws {@link IllegalStateException} if the component
     * has no ref assigned (e.g. snapshot was not called, or the component has
     * no actions).
     *
     * @param component the component to look up
     * @return the ref number (always positive)
     * @throws IllegalStateException if no ref is assigned to this component
     */
    public int getRefOf(Component component) {
        Accessible accessible = (Accessible) component;
        for (var entry : componentRefs.entrySet()) {
            if (entry.getValue() == accessible) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("No ref assigned to " + component.getClass().getSimpleName()
                + ". Was swing_snapshot called? Does the component have actions?");
    }
}
