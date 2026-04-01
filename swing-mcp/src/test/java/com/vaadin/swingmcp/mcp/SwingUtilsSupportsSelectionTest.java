package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsSelection(javax.accessibility.Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsSelectionTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportSelection() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsSelection(rb));
    }

    @Test
    void jComboBox_supportsSelection() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertTrue(SwingUtils.supportsSelection(combo));
    }

    @Test
    void jToggleButton_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_supportsSelection() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertTrue(SwingUtils.supportsSelection(tp));
    }

    @Test
    void jSplitPane_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — selection is suppressed for menu bar and menu
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportSelection() {
        // Selection is suppressed for menu bars (internal navigation)
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        assertFalse(SwingUtils.supportsSelection(mb));
    }

    @Test
    void jMenu_doesNotSupportSelection() {
        // Selection is suppressed for menus (internal navigation)
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        assertFalse(SwingUtils.supportsSelection(menu));
    }

    @Test
    void jMenuItem_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportSelection() {
        assertFalse(SwingUtils.supportsSelection(new JToolBar()));
    }

    @Test
    void jList_supportsSelection() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertTrue(SwingUtils.supportsSelection(list));
    }

    @Test
    void jTree_supportsSelection() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertTrue(SwingUtils.supportsSelection(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledComboBox_stillReportsSelectionSupport() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setEnabled(false);
        assertTrue(SwingUtils.supportsSelection(combo));
    }

    @Test
    void disabledList_stillReportsSelectionSupport() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        assertTrue(SwingUtils.supportsSelection(list));
    }
}
