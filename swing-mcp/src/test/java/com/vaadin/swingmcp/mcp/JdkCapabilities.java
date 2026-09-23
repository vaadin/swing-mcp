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

import javax.swing.JComboBox;
import javax.swing.JSlider;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

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

    /**
     * Whether a press on a {@link JComboBox} that is not showing leaves its popup alone:
     * {@code false} on Java 11, where opening the popup throws, {@code true} from Java 17.
     * See R_ui_delegate_press_throws.
     */
    public static final boolean COMBO_IGNORES_PRESS_WHILE_NOT_SHOWING =
            comboIgnoresPressWhileNotShowing();

    private static boolean comboIgnoresPressWhileNotShowing() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A"});
        try {
            combo.dispatchEvent(new MouseEvent(combo, MouseEvent.MOUSE_PRESSED, 0,
                    InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON1));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private JdkCapabilities() {
    }
}
