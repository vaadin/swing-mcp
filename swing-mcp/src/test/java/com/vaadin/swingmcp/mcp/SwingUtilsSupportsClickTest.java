package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsClick(Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsClickTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_supportsClick() {
        JButton button = new JButton("OK");
        assertNotNull(SwingUtils.supportsClick(button));
    }

    @Test
    void jTextField_doesNotSupportClick() {
        JTextField field = new JTextField("text");
        assertNull(SwingUtils.supportsClick(field));
    }

    @Test
    void jPasswordField_doesNotSupportClick() {
        JPasswordField field = new JPasswordField("secret");
        assertNull(SwingUtils.supportsClick(field));
    }

    @Test
    void jTextArea_doesNotSupportClick() {
        JTextArea area = new JTextArea("text");
        assertNull(SwingUtils.supportsClick(area));
    }

    @Test
    void jCheckBox_supportsClick() {
        JCheckBox cb = new JCheckBox("Check");
        assertNotNull(SwingUtils.supportsClick(cb));
    }

    @Test
    void jRadioButton_supportsClick() {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        assertNotNull(SwingUtils.supportsClick(rb));
    }

    @Test
    void jComboBox_doesNotSupportClick() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertNull(SwingUtils.supportsClick(combo));
    }

    @Test
    void jToggleButton_supportsClick() {
        JToggleButton tb = new JToggleButton("Toggle");
        assertNotNull(SwingUtils.supportsClick(tb));
    }

    @Test
    void jSpinner_doesNotSupportClick() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        assertNull(SwingUtils.supportsClick(spinner));
    }

    @Test
    void jSlider_doesNotSupportClick() {
        JSlider slider = new JSlider(0, 100, 50);
        assertNull(SwingUtils.supportsClick(slider));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportClick() {
        JPanel panel = new JPanel();
        assertNull(SwingUtils.supportsClick(panel));
    }

    @Test
    void jScrollPane_doesNotSupportClick() {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        assertNull(SwingUtils.supportsClick(sp));
    }

    @Test
    void jTabbedPane_doesNotSupportClick() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertNull(SwingUtils.supportsClick(tp));
    }

    @Test
    void jSplitPane_doesNotSupportClick() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        assertNull(SwingUtils.supportsClick(sp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportClick() {
        JLabel label = new JLabel("Hello");
        assertNull(SwingUtils.supportsClick(label));
    }

    @Test
    void jProgressBar_doesNotSupportClick() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertNull(SwingUtils.supportsClick(pb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportClick() {
        JMenuBar mb = new JMenuBar();
        assertNull(SwingUtils.supportsClick(mb));
    }

    @Test
    void jMenu_doesNotSupportClick() {
        // D_jmenu_not_clickable: JMenu is a structural container, not a click target.
        // The menu's JMenuItem children are directly clickable via their own refs.
        JMenu menu = new JMenu("File");
        assertNull(SwingUtils.supportsClick(menu));
    }

    @Test
    void jMenuItem_supportsClick() {
        JMenuItem item = new JMenuItem("Open");
        assertNotNull(SwingUtils.supportsClick(item));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportClick() {
        JToolBar tb = new JToolBar();
        assertNull(SwingUtils.supportsClick(tb));
    }

    @Test
    void jList_doesNotSupportClick() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertNull(SwingUtils.supportsClick(list));
    }

    @Test
    void jTree_doesNotSupportClick() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertNull(SwingUtils.supportsClick(tree));
    }

    @Test
    void jList_childAccessible_supportsClick() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child, "JList should have virtual child accessibles");
        assertNotNull(SwingUtils.supportsClick(child),
                "JList virtual child should support click");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledButton_stillReportsClickSupport() {
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        // supportsClick checks capability, not state
        assertNotNull(SwingUtils.supportsClick(button));
    }

    @Test
    void returnedRunnableIsConsistentlyNonNull() {
        JButton button = new JButton("OK");
        assertNotNull(SwingUtils.supportsClick(button));
        assertNotNull(SwingUtils.supportsClick(button));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tier 2 — MouseListener fallback
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void panelWithAppMouseListener_supportsClick() {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        assertNotNull(SwingUtils.supportsClick(panel),
                "JPanel with application MouseListener should support click via Tier 2");
    }

    @Test
    void panelWithoutMouseListener_doesNotSupportClick() {
        JPanel panel = new JPanel();
        assertNull(SwingUtils.supportsClick(panel),
                "JPanel without MouseListener should not support click");
    }

    @Test
    void panelWithOnlyFrameworkMouseListener_doesNotSupportClick() {
        JPanel panel = new JPanel();
        // Setting a tooltip causes ToolTipManager (javax.swing) to register a MouseListener
        panel.setToolTipText("tooltip");
        assertNull(SwingUtils.supportsClick(panel),
                "JPanel with only framework MouseListeners should not support click");
    }

    @Test
    void interactiveRoleWithAppMouseListener_doesNotSupportClickViaTier2() {
        // JSlider has an interactive role — Tier 2 should be skipped
        JSlider slider = new JSlider(0, 100, 50);
        slider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // application listener
            }
        });
        assertNull(SwingUtils.supportsClick(slider),
                "Interactive role with app MouseListener should not get Tier 2 click");
    }

    @Test
    void buttonWithAccessibleActionAndAppMouseListener_usesTier1() {
        JButton button = new JButton("OK");
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // additional app listener
            }
        });
        // Should still return non-null (Tier 1 takes precedence)
        assertNotNull(SwingUtils.supportsClick(button),
                "Button with both AccessibleAction click and MouseListener should support click");
    }

    @Test
    void labelWithAppMouseListener_supportsClick() {
        JLabel label = new JLabel("Click me");
        label.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // clickable label
            }
        });
        assertNotNull(SwingUtils.supportsClick(label),
                "JLabel with application MouseListener should support click via Tier 2");
    }

    @Test
    void disabledPanelWithAppMouseListener_stillReportsClickSupport() {
        ClickRecordingPanel panel = new ClickRecordingPanel();
        panel.setEnabled(false);
        // supportsClick checks capability, not state
        assertNotNull(SwingUtils.supportsClick(panel),
                "Disabled panel with MouseListener should still report click support");
    }
}
