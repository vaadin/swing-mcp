package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.tinymcpserver.MCPServerException;

import javax.accessibility.Accessible;
import java.awt.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Contextual information passed to {@link AbstractSwingTool#execute} on every
 * invocation. Collects the data that the server resolves <em>before</em> the
 * tool runs (considered components, and in the future potentially more).
 * <p>
 * <b>Thread safety:</b> this class is not thread-safe. All mutation and
 * inspection must be serialised by the caller. In production
 * ({@code SwingMCP.registerTool}) every access happens on the Swing event
 * dispatch thread (inside {@code runInEDT}), which satisfies this
 * requirement. Tests may access the context off the EDT provided they use
 * a synchronous hand-off such as {@link javax.swing.SwingUtilities#invokeAndWait}
 * to establish happens-before between the test thread and the EDT; no
 * concurrent access from multiple threads is ever permitted.
 */
public class SwingToolContext {

    private List<Component> consideredComponents;
    /**
     * The {@link AbstractSwingTool#TOOL_SWING_SNAPSHOT} populates this map by assigning IDs to every accessible
     * that the client MCP can interact with. Stored as {@link Accessible} rather than {@link Component} because
     * virtual accessibility children (e.g. JTable cells, JTree nodes) are {@link Accessible} but not {@link Component}.
     * The map is cleared after every successful mutation and needs to be re-populated by another call to SwingSnapshotTool.
     * A pre-dispatch validation error does not clear the map.
     */
    private final Map<Integer, Accessible> componentRefs = new HashMap<>();
    /**
     * Executor for background work spawned by tools (e.g. Robot-based drag in
     * {@link SwingDragTool}, which cannot run on the EDT). Supplied at
     * construction; production passes
     * {@link com.vaadin.swingmcp.tinymcpserver.MCPHandler#getExecutor()}.
     * Tests that do not exercise background dispatch may pass
     * {@code Runnable::run}.
     */
    private final Executor executor;

    public SwingToolContext(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

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

    /**
     * Returns the executor supplied at construction. See {@link #executor}.
     */
    public Executor getExecutor() {
        return executor;
    }

    public void putRef(int ref, Accessible accessible) {
        componentRefs.put(ref, accessible);
    }

    public Accessible getAccessibleByRef(int ref) {
        var result = componentRefs.get(ref);
        if (result == null) {
            if (componentRefs.isEmpty()) {
                throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                        "Component with ref " + ref + " invalid — the ref map is stale (empty). It is cleared after every successful mutation. Call swing_snapshot to rebuild it.");
            }
            int min = Collections.min(componentRefs.keySet());
            int max = Collections.max(componentRefs.keySet());
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Component with ref " + ref + " does not exist (valid refs: " + min + "–" + max + ").");
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
