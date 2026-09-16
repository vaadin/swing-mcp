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

import javax.swing.JSlider;

/**
 * Accessibility capabilities that differ across the JDK versions this project
 * supports, probed from the running JVM so a test asserts what this JDK can
 * actually do rather than what the newest one can.
 *
 * <pre>{@code
 * assumeTrue(JdkCapabilities.SLIDER_HAS_ACCESSIBLE_ACTIONS,
 *         "JSlider exposes increment/decrement only from Java 17");
 * }</pre>
 *
 * <p>Probes query the JDK directly, never swing-mcp — an expectation derived
 * from the code under test would assert nothing.
 */
public final class JdkCapabilities {

    /**
     * Whether {@link JSlider} advertises {@code increment} / {@code decrement}
     * through {@code AccessibleAction}: {@code false} on Java 11, {@code true}
     * from Java 17. See R_jslider_actions_since_17.
     */
    public static final boolean SLIDER_HAS_ACCESSIBLE_ACTIONS =
            new JSlider().getAccessibleContext().getAccessibleAction() != null;

    private JdkCapabilities() {
    }
}
