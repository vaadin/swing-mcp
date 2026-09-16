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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static com.vaadin.swingmcp.mcp.JdkCapabilities.SLIDER_HAS_ACCESSIBLE_ACTIONS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsIncrement(javax.accessibility.Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsIncrementTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jSpinner_supportsIncrement() {
        assertTrue(SwingUtils.supportsIncrement(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))) >= 0);
    }

    @Test
    void jSlider_supportsIncrement() {
        // JSlider gained AccessibleAction in Java 17 (R_jslider_actions_since_17);
        // on 11 the correct answer is "not supported".
        assertEquals(SLIDER_HAS_ACCESSIBLE_ACTIONS,
                SwingUtils.supportsIncrement(new JSlider(0, 100, 50)) >= 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — not supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportIncrement() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertEquals(-1, SwingUtils.supportsIncrement(rb));
    }

    @Test
    void jComboBox_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JToggleButton("Toggle")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportIncrement() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertEquals(-1, SwingUtils.supportsIncrement(tp));
    }

    @Test
    void jSplitPane_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportIncrement() {
        assertEquals(-1, SwingUtils.supportsIncrement(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportIncrement() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertEquals(-1, SwingUtils.supportsIncrement(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledSpinner_stillReportsIncrementSupport() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        spinner.setEnabled(false);
        assertTrue(SwingUtils.supportsIncrement(spinner) >= 0);
    }

    @Test
    void returnedIndexIsConsistentAcrossCalls() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        int first = SwingUtils.supportsIncrement(spinner);
        int second = SwingUtils.supportsIncrement(spinner);
        assertEquals(first, second);
    }
}
