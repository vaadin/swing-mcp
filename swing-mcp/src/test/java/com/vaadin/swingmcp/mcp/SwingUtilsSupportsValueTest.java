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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsGetValue(javax.accessibility.Accessible)}
 * and {@link SwingUtils#supportsSetValue(javax.accessibility.Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsValueTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportGetValue() {
        // AbstractButton exposes AccessibleValue but it's suppressed
        assertFalse(SwingUtils.supportsGetValue(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportGetValue() {
        // AbstractButton value suppressed
        assertFalse(SwingUtils.supportsGetValue(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportGetValue() {
        // AbstractButton value suppressed
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsGetValue(rb));
    }

    @Test
    void jComboBox_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportGetValue() {
        // AbstractButton value suppressed
        assertFalse(SwingUtils.supportsGetValue(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_supportsGetValue() {
        // JSpinner exposes AccessibleValue via its number editor
        assertTrue(SwingUtils.supportsGetValue(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSpinnerDateModel_doesNotSupportGetValue() {
        // SpinnerDateModel exposes non-null AccessibleValue but getCurrentAccessibleValue() returns null
        JSpinner spinner = new JSpinner(new SpinnerDateModel());
        assertFalse(SwingUtils.supportsGetValue(spinner));
    }

    @Test
    void jSpinnerListModel_doesNotSupportGetValue() {
        // SpinnerListModel exposes non-null AccessibleValue but getCurrentAccessibleValue() returns null
        JSpinner spinner = new JSpinner(new SpinnerListModel(new String[]{"A", "B", "C"}));
        assertFalse(SwingUtils.supportsGetValue(spinner));
    }

    @Test
    void jSlider_supportsGetValue() {
        assertTrue(SwingUtils.supportsGetValue(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural — getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportGetValue() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertFalse(SwingUtils.supportsGetValue(tp));
    }

    @Test
    void jSplitPane_supportsGetValue() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        assertTrue(SwingUtils.supportsGetValue(sp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_supportsGetValue() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertTrue(SwingUtils.supportsGetValue(pb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportGetValue() {
        // Menu AccessibleValue is suppressed
        assertFalse(SwingUtils.supportsGetValue(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportGetValue() {
        // MenuItem AccessibleValue is suppressed
        assertFalse(SwingUtils.supportsGetValue(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other — getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportGetValue() {
        assertFalse(SwingUtils.supportsGetValue(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportGetValue() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsGetValue(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — setValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JButton("OK")));
    }

    @Test
    void jCheckBox_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportSetValue() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsSetValue(rb));
    }

    @Test
    void jToggleButton_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JToggleButton("Toggle")));
    }

    @Test
    void jSlider_supportsSetValue() {
        assertTrue(SwingUtils.supportsSetValue(new JSlider(0, 100, 50)));
    }

    @Test
    void jSpinnerDateModel_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JSpinner(new SpinnerDateModel())));
    }

    @Test
    void jSpinnerListModel_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JSpinner(new SpinnerListModel(new String[]{"A", "B", "C"}))));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — setValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jProgressBar_doesNotSupportSetValue() {
        // ProgressBar has read-only value
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertFalse(SwingUtils.supportsSetValue(pb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers — setValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jSplitPane_supportsSetValue() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        assertTrue(SwingUtils.supportsSetValue(sp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — setValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenu_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportSetValue() {
        assertFalse(SwingUtils.supportsSetValue(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Relationship: setValue implies getValue
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setValueImpliesGetValue_JSlider() {
        JSlider slider = new JSlider(0, 100, 50);
        if (SwingUtils.supportsSetValue(slider)) {
            assertTrue(SwingUtils.supportsGetValue(slider),
                    "If setValue is supported, getValue must also be supported");
        }
    }

    @Test
    void getValueWithoutSetValue_JProgressBar() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertTrue(SwingUtils.supportsGetValue(pb));
        assertFalse(SwingUtils.supportsSetValue(pb));
    }
}
