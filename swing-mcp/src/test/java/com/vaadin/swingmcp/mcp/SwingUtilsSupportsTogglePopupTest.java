package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsTogglePopup(javax.accessibility.Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsTogglePopupTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportTogglePopup() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertEquals(-1, SwingUtils.supportsTogglePopup(rb));
    }

    @Test
    void jComboBox_supportsTogglePopup() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertTrue(SwingUtils.supportsTogglePopup(combo) >= 0);
    }

    @Test
    void jToggleButton_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportTogglePopup() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertEquals(-1, SwingUtils.supportsTogglePopup(tp));
    }

    @Test
    void jSplitPane_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportTogglePopup() {
        assertEquals(-1, SwingUtils.supportsTogglePopup(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportTogglePopup() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertEquals(-1, SwingUtils.supportsTogglePopup(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledComboBox_stillReportsTogglePopupSupport() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setEnabled(false);
        assertTrue(SwingUtils.supportsTogglePopup(combo) >= 0);
    }

    @Test
    void returnedIndexIsConsistentAcrossCalls() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        int first = SwingUtils.supportsTogglePopup(combo);
        int second = SwingUtils.supportsTogglePopup(combo);
        assertEquals(first, second);
    }
}
