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
