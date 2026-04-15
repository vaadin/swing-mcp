package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsGetText(Accessible)} and
 * {@link SwingUtils#supportsSetText(Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsTextTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JButton("OK")));
    }

    @Test
    void jTextField_supportsGetText() {
        assertTrue(SwingUtils.supportsGetText(new JTextField("text")));
    }

    @Test
    void jPasswordField_supportsGetText() {
        assertTrue(SwingUtils.supportsGetText(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_supportsGetText() {
        assertTrue(SwingUtils.supportsGetText(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportGetText() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsGetText(rb));
    }

    @Test
    void jComboBox_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_supportsGetText() {
        // JSpinner's editor exposes AccessibleText
        assertTrue(SwingUtils.supportsGetText(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportGetText() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertFalse(SwingUtils.supportsGetText(tp));
    }

    @Test
    void jSplitPane_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportGetText() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsGetText(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JButton("OK")));
    }

    @Test
    void jTextField_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JTextField("text")));
    }

    @Test
    void jPasswordField_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportSetText() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsSetText(rb));
    }

    @Test
    void jComboBox_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportSetText() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertFalse(SwingUtils.supportsSetText(tp));
    }

    @Test
    void jSplitPane_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportSetText() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsSetText(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Relationship: setText implies getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setTextImpliesGetText_JTextField() {
        JTextField field = new JTextField("text");
        if (SwingUtils.supportsSetText(field)) {
            assertTrue(SwingUtils.supportsGetText(field),
                    "If setText is supported, getText must also be supported");
        }
    }

    @Test
    void setTextImpliesGetText_JTextArea() {
        JTextArea area = new JTextArea("text");
        if (SwingUtils.supportsSetText(area)) {
            assertTrue(SwingUtils.supportsGetText(area),
                    "If setText is supported, getText must also be supported");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList virtual child — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_virtualChild_doesNotSupportGetText() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertFalse(SwingUtils.supportsGetText(child));
    }

    @Test
    void jList_virtualChild_doesNotSupportSetText() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertFalse(SwingUtils.supportsSetText(child));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // hasPasswordRole (DR-011)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPasswordField_hasPasswordRole() {
        assertTrue(SwingUtils.hasPasswordRole(new JPasswordField("secret")));
    }

    @Test
    void jTextField_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JTextField("text")));
    }

    @Test
    void jTextArea_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JTextArea("text")));
    }

    @Test
    void jButton_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JButton("OK")));
    }

    @Test
    void customComponentWithPasswordRole_hasPasswordRole() {
        // DR-011 gate is role-based, not class-based. A component that is not a
        // JPasswordField but claims AccessibleRole.PASSWORD_TEXT still trips the gate.
        JTextField fake = new JTextField("secret") {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.PASSWORD_TEXT;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        assertTrue(SwingUtils.hasPasswordRole(fake));
    }
}
