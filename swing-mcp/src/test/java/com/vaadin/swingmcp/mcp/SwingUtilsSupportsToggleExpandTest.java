package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsToggleExpand(Accessible)}.
 * Covers every component in the verification component matrix, plus JTree node variants.
 */
class SwingUtilsSupportsToggleExpandTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTree nodes — primary target
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTreeNonLeafNode_supportsToggleExpand() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        root.add(new DefaultMutableTreeNode("child"));
        JTree tree = new JTree(root);
        // root node is the first accessible child of JTree
        Accessible rootNode = tree.getAccessibleContext().getAccessibleChild(0);
        assertTrue(SwingUtils.supportsToggleExpand(rootNode) >= 0);
    }

    @Test
    void jTreeLeafNode_doesNotSupportToggleExpand() {
        // A root node with no children is a leaf — accessible at index 0 without any expansion
        JTree tree = new JTree(new DefaultMutableTreeNode("leaf-root"));
        Accessible leafNode = tree.getAccessibleContext().getAccessibleChild(0);
        assertNotNull(leafNode, "Root accessible should not be null");
        assertEquals(-1, SwingUtils.supportsToggleExpand(leafNode));
    }

    @Test
    void jTree_doesNotSupportToggleExpand() {
        JTree tree = new JTree(new DefaultMutableTreeNode("Root"));
        assertEquals(-1, SwingUtils.supportsToggleExpand(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JButton("OK")));
    }

    @Test
    void jTextField_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportToggleExpand() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertEquals(-1, SwingUtils.supportsToggleExpand(rb));
    }

    @Test
    void jComboBox_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportToggleExpand() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertEquals(-1, SwingUtils.supportsToggleExpand(tp));
    }

    @Test
    void jSplitPane_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportToggleExpand() {
        assertEquals(-1, SwingUtils.supportsToggleExpand(new JList<>(new String[]{"A", "B"})));
    }
}
