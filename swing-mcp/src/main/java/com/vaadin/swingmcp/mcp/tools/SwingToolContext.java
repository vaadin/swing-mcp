/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;

import javax.accessibility.Accessible;
import java.awt.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * One MCP session's state across tool calls: the ref map that {@code swing_snapshot} and
 * {@code swing_get_cells} fill and every other tool resolves refs against, plus the top-level
 * components the current call may see, set before each call.
 */
public class SwingToolContext {

    private List<Component> consideredComponents = Collections.emptyList();
    /**
     * {@link Accessible} rather than {@link Component}: a virtual child such as a {@code JTree}
     * node can have a ref but is no {@code Component}.
     */
    private final Map<Integer, Accessible> componentRefs = new HashMap<>();
    private final Executor executor;

    /**
     * @param executor runs tool work that must stay off the EDT, such as a {@link java.awt.Robot}
     *                 drag; {@code Runnable::run} will do for a test that never dispatches one
     */
    public SwingToolContext(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void setConsideredComponents(List<Component> consideredComponents) {
        Objects.requireNonNull(consideredComponents, "consideredComponents");
        this.consideredComponents = Collections.unmodifiableList(consideredComponents);
    }

    /**
     * @return the top-level components this call may inspect or act on; none is nested inside
     *         another
     */
    public List<Component> getConsideredComponents() {
        return consideredComponents;
    }

    public void clearRefMap() {
        componentRefs.clear();
    }

    public Executor getExecutor() {
        return executor;
    }

    public void putRef(int ref, Accessible accessible) {
        componentRefs.put(ref, accessible);
    }

    /**
     * @throws MCPErrorResponseException for an unknown ref, telling the model whether the map is
     *         empty (no snapshot yet, or cleared by a mutation) or which refs are valid. Not a
     *         protocol error: the argument is well-formed, it just addresses nothing right now.
     */
    public Accessible getAccessibleByRef(int ref) {
        var result = componentRefs.get(ref);
        if (result == null) {
            if (componentRefs.isEmpty()) {
                throw new MCPErrorResponseException(
                        "Component with ref " + ref + " invalid — the ref map is empty: no swing_snapshot yet, or a successful mutation cleared it. Call swing_snapshot to rebuild it.");
            }
            int min = Collections.min(componentRefs.keySet());
            int max = Collections.max(componentRefs.keySet());
            throw new MCPErrorResponseException(
                    "Component with ref " + ref + " does not exist (valid refs: " + min + "–" + max + ").");
        }
        return result;
    }

    /**
     * The ref assigned to {@code component} — a reverse lookup, for tests only.
     *
     * @param component must be {@link Accessible}
     * @throws IllegalStateException if it has no ref: no snapshot yet, or it has no actions
     */
    public int getRefOf(Component component) {
        Accessible accessible = (Accessible) component;
        for (var entry : componentRefs.entrySet()) {
            if (entry.getValue() == accessible) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("No ref assigned to " + SwingUtils.getComponentClassName(component)
                + ". Was swing_snapshot called? Does the component have actions?");
    }
}
