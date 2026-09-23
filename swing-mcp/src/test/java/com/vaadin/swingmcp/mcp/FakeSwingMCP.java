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
package com.vaadin.swingmcp.mcp;

import java.awt.Component;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@link SwingMCP} that serves whatever components the test passes to
 * {@link #setConsideredComponents(List)}.
 * <p>
 * {@code useEDT} picks what {@link #runInEDT(Callable)} does: {@code false} runs the block on the
 * calling thread, for headless tests, which have no EDT; {@code true} marshals it onto the real
 * EDT, for screen-mode tests.
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
     * Safe to call from any thread; the list is copied.
     *
     * @param components must not be null
     */
    public void setConsideredComponents(List<Component> components) {
        this.consideredComponents = new CopyOnWriteArrayList<>(components);
    }

    @Override
    protected List<Component> getConsideredComponents() {
        return consideredComponents;
    }
}
