package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#isEffectivelyEnabled(Accessible)}.
 * Covers every component in the verification component matrix, plus
 * parent-chain and edge-case scenarios.
 */
class SwingUtilsIsEffectivelyEnabledTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — enabled
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_enabledByDefault() {
        JButton button = new JButton("OK");
        assertTrue(SwingUtils.isEffectivelyEnabled(button));
    }

    @Test
    void jButton_disabled() {
        JButton button = new JButton("OK");
        button.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(button));
    }

    @Test
    void jTextField_enabledByDefault() {
        JTextField field = new JTextField("text");
        assertTrue(SwingUtils.isEffectivelyEnabled(field));
    }

    @Test
    void jTextField_disabled() {
        JTextField field = new JTextField("text");
        field.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(field));
    }

    @Test
    void jPasswordField_enabledByDefault() {
        JPasswordField field = new JPasswordField("secret");
        assertTrue(SwingUtils.isEffectivelyEnabled(field));
    }

    @Test
    void jPasswordField_disabled() {
        JPasswordField field = new JPasswordField("secret");
        field.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(field));
    }

    @Test
    void jTextArea_enabledByDefault() {
        JTextArea area = new JTextArea("text");
        assertTrue(SwingUtils.isEffectivelyEnabled(area));
    }

    @Test
    void jTextArea_disabled() {
        JTextArea area = new JTextArea("text");
        area.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(area));
    }

    @Test
    void jCheckBox_enabledByDefault() {
        JCheckBox cb = new JCheckBox("Check");
        assertTrue(SwingUtils.isEffectivelyEnabled(cb));
    }

    @Test
    void jCheckBox_disabled() {
        JCheckBox cb = new JCheckBox("Check");
        cb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(cb));
    }

    @Test
    void jRadioButton_enabledByDefault() {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        assertTrue(SwingUtils.isEffectivelyEnabled(rb));
    }

    @Test
    void jRadioButton_disabled() {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        rb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(rb));
    }

    @Test
    void jComboBox_enabledByDefault() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertTrue(SwingUtils.isEffectivelyEnabled(combo));
    }

    @Test
    void jComboBox_disabled() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        combo.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(combo));
    }

    @Test
    void jToggleButton_enabledByDefault() {
        JToggleButton tb = new JToggleButton("Toggle");
        assertTrue(SwingUtils.isEffectivelyEnabled(tb));
    }

    @Test
    void jToggleButton_disabled() {
        JToggleButton tb = new JToggleButton("Toggle");
        tb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(tb));
    }

    @Test
    void jSpinner_enabledByDefault() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        assertTrue(SwingUtils.isEffectivelyEnabled(spinner));
    }

    @Test
    void jSpinner_disabled() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        spinner.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(spinner));
    }

    @Test
    void jSlider_enabledByDefault() {
        JSlider slider = new JSlider(0, 100, 50);
        assertTrue(SwingUtils.isEffectivelyEnabled(slider));
    }

    @Test
    void jSlider_disabled() {
        JSlider slider = new JSlider(0, 100, 50);
        slider.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(slider));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_enabledByDefault() {
        JPanel panel = new JPanel();
        assertTrue(SwingUtils.isEffectivelyEnabled(panel));
    }

    @Test
    void jPanel_disabled() {
        JPanel panel = new JPanel();
        panel.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(panel));
    }

    @Test
    void jScrollPane_enabledByDefault() {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        assertTrue(SwingUtils.isEffectivelyEnabled(sp));
    }

    @Test
    void jScrollPane_disabled() {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        sp.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(sp));
    }

    @Test
    void jTabbedPane_enabledByDefault() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertTrue(SwingUtils.isEffectivelyEnabled(tp));
    }

    @Test
    void jTabbedPane_disabled() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(tp));
    }

    @Test
    void jSplitPane_enabledByDefault() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        assertTrue(SwingUtils.isEffectivelyEnabled(sp));
    }

    @Test
    void jSplitPane_disabled() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        sp.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(sp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_enabledByDefault() {
        JLabel label = new JLabel("Hello");
        assertTrue(SwingUtils.isEffectivelyEnabled(label));
    }

    @Test
    void jLabel_disabled() {
        JLabel label = new JLabel("Hello");
        label.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(label));
    }

    @Test
    void jProgressBar_enabledByDefault() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertTrue(SwingUtils.isEffectivelyEnabled(pb));
    }

    @Test
    void jProgressBar_disabled() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(pb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_enabledByDefault() {
        JMenuBar mb = new JMenuBar();
        assertTrue(SwingUtils.isEffectivelyEnabled(mb));
    }

    @Test
    void jMenuBar_disabled() {
        JMenuBar mb = new JMenuBar();
        mb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(mb));
    }

    @Test
    void jMenu_enabledByDefault() {
        JMenu menu = new JMenu("File");
        assertTrue(SwingUtils.isEffectivelyEnabled(menu));
    }

    @Test
    void jMenu_disabled() {
        JMenu menu = new JMenu("File");
        menu.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(menu));
    }

    @Test
    void jMenuItem_enabledByDefault() {
        JMenuItem item = new JMenuItem("Open");
        assertTrue(SwingUtils.isEffectivelyEnabled(item));
    }

    @Test
    void jMenuItem_disabled() {
        JMenuItem item = new JMenuItem("Open");
        item.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(item));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_enabledByDefault() {
        JToolBar tb = new JToolBar();
        assertTrue(SwingUtils.isEffectivelyEnabled(tb));
    }

    @Test
    void jToolBar_disabled() {
        JToolBar tb = new JToolBar();
        tb.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(tb));
    }

    @Test
    void jList_enabledByDefault() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertTrue(SwingUtils.isEffectivelyEnabled(list));
    }

    @Test
    void jList_disabled() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(list));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Parent chain — disabled parent makes child effectively disabled
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void enabledButtonInDisabledPanel_isEffectivelyDisabled() {
        JPanel panel = new JPanel();
        JButton button = new JButton("Child");
        panel.add(button);
        panel.setEnabled(false);

        assertTrue(button.isEnabled(), "Button itself is still enabled");
        assertFalse(SwingUtils.isEffectivelyEnabled(button),
                "Button should be effectively disabled because parent is disabled");
    }

    @Test
    void enabledButtonInEnabledPanel_isEffectivelyEnabled() {
        JPanel panel = new JPanel();
        JButton button = new JButton("Child");
        panel.add(button);

        assertTrue(SwingUtils.isEffectivelyEnabled(button));
    }

    @Test
    void disabledGrandparentMakesGrandchildEffectivelyDisabled() {
        JPanel grandparent = new JPanel();
        JPanel parent = new JPanel();
        JButton button = new JButton("Grandchild");
        grandparent.add(parent);
        parent.add(button);
        grandparent.setEnabled(false);

        assertTrue(parent.isEnabled());
        assertTrue(button.isEnabled());
        assertFalse(SwingUtils.isEffectivelyEnabled(button),
                "Button should be effectively disabled because grandparent is disabled");
    }

    @Test
    void jList_virtualChild_enabledWhenListEnabled() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertTrue(SwingUtils.isEffectivelyEnabled(child));
    }

    @Test
    void jList_virtualChild_disabledWhenListDisabled() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        list.setEnabled(false);
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertFalse(SwingUtils.isEffectivelyEnabled(child),
                "JList virtual child should be effectively disabled when list is disabled");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane — individual tabs disabled via setEnabledAt
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPane_disabledTab_isEffectivelyDisabled() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Enabled", new JPanel());
        tp.addTab("Disabled", new JPanel());
        tp.setEnabledAt(1, false);

        AccessibleContext ac = tp.getAccessibleContext();
        Accessible disabledTab = ac.getAccessibleChild(1);
        assertNotNull(disabledTab);
        assertFalse(SwingUtils.isEffectivelyEnabled(disabledTab),
                "Tab disabled via setEnabledAt(1, false) should be effectively disabled");
    }

    @Test
    void jTabbedPane_enabledTab_isEffectivelyEnabled() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Enabled", new JPanel());
        tp.addTab("Disabled", new JPanel());
        tp.setEnabledAt(1, false);

        AccessibleContext ac = tp.getAccessibleContext();
        Accessible enabledTab = ac.getAccessibleChild(0);
        assertNotNull(enabledTab);
        assertTrue(SwingUtils.isEffectivelyEnabled(enabledTab),
                "Tab not disabled via setEnabledAt should be effectively enabled");
    }

    @Test
    void jTabbedPane_disabledPane_makesAllTabsEffectivelyDisabled() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JPanel());
        tp.addTab("B", new JPanel());
        tp.setEnabled(false);

        AccessibleContext ac = tp.getAccessibleContext();
        for (int i = 0; i < ac.getAccessibleChildrenCount(); i++) {
            Accessible tab = ac.getAccessibleChild(i);
            assertNotNull(tab);
            assertFalse(SwingUtils.isEffectivelyEnabled(tab),
                    "Tab " + i + " should be effectively disabled because the JTabbedPane is disabled");
        }
    }

    @Test
    void buttonInDisabledToolBar_isEffectivelyDisabled() {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        tb.setEnabled(false);

        assertTrue(button.isEnabled(), "Button itself is still enabled");
        assertFalse(SwingUtils.isEffectivelyEnabled(button),
                "Button should be effectively disabled because parent toolbar is disabled");
    }
}
